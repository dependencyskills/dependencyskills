package org.dependencyskills.codex.index

import org.apache.lucene.document.Document
import org.apache.lucene.document.Field
import org.apache.lucene.document.KnnFloatVectorField
import org.apache.lucene.document.StringField
import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.index.IndexWriter
import org.apache.lucene.index.IndexWriterConfig
import org.apache.lucene.index.Term
import org.apache.lucene.index.VectorSimilarityFunction
import org.apache.lucene.search.DisjunctionMaxQuery
import org.apache.lucene.search.IndexSearcher
import org.apache.lucene.search.KnnFloatVectorQuery
import org.apache.lucene.search.Query
import org.apache.lucene.search.TermInSetQuery
import org.apache.lucene.store.FSDirectory
import org.apache.lucene.util.BytesRef
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.inference.Pooling
import java.nio.file.Path

/** One vector hit: which entry, and how well its best face matched. */
data class VectorHit(val entryId: String, val score: Float)

/** The index was asked to do something that would corrupt what it means. */
class IndexBasisException(message: String) : RuntimeException(message)

/**
 * Two vectors per entry, never concatenated, scored as the better of the two.
 *
 * Each entry is searchable twice: once on the library's **own documentation** and once on the
 * **rewritten sentence**. Both are keys; only the rewrite is ever displayed, and that is safe
 * because a retrieval key is a list of numbers and nothing reads it. The original text can decide
 * which entry surfaces without ever reaching an agent.
 *
 * [RAD-0040](../../../../../../../../docs/knowledge/research/RAD-0040-does-summarising-improve-retrieval.md)
 * measured why they stay apart, over the same questions, encoder and 220 entries:
 *
 * | index | in the first ten |
 * |---|---|
 * | raw documentation only | 13 of 17 |
 * | rewrite only | 10 of 17 |
 * | **both faces, two vectors** | **15 of 17** |
 * | both texts fused into one vector | 10 of 17 |
 *
 * **Fusing them is worse than either alone**, and the gain needs them kept apart. It wins by not
 * failing badly rather than by being better everywhere: each face fails a different set of
 * questions and the sets barely overlap. So there is no field here holding both texts, and
 * [search] takes the max of the two rather than their sum — a `DisjunctionMaxQuery` with a zero
 * tie-breaker, which is what "scores as its best-matching face" means in Lucene's vocabulary.
 *
 * ## One basis per index
 *
 * The encoder and its pooling are recorded in the index's own commit data and checked on every
 * open. Two vectors from the same encoder under different pooling are not comparable — RAD-0048
 * measured CLS beating mean on one model and collapsing on another — and the failure is silent:
 * the vectors are the right width and the wrong basis, so they index, score and rank, wrongly.
 *
 * Recording it per document would let them mix. Recording it per index makes mixing impossible,
 * which is the difference between a rule and a guarantee. Selective invalidation still works,
 * because the store keeps the same pair per entry in `Provenance` and this index is derived.
 */
class TwoFacedIndex private constructor(
    private val directory: FSDirectory,
    /** The encoder that produced every vector in here. */
    val encoder: String,
    /** The pooling every vector in here was produced under. */
    val pooling: Pooling,
    /** The width every vector in here has. */
    val dimensions: Int,
) : AutoCloseable {

    private val writer = IndexWriter(directory, IndexWriterConfig())

    init {
        writer.setLiveCommitData(
            mapOf(ENCODER to encoder, POOLING to pooling.name, DIMENSIONS to dimensions.toString())
                .entries,
        )
    }

    /**
     * Indexes one entry's faces, replacing whatever was there under the same id.
     *
     * [rewriteVector] is null for an entry whose rewrite was rejected. Such an entry **keeps its
     * documentation face** and is still findable; what it loses is the second key and the right
     * to display prose. An entry with no key at all would be indistinguishable from one silently
     * dropped, which is the failure the degraded state exists to avoid.
     */
    fun add(
        entryId: String,
        coordinates: Set<Coordinate>,
        docVector: FloatArray,
        rewriteVector: FloatArray? = null,
    ) {
        require(coordinates.isNotEmpty()) { "an entry with no coordinate can never be in scope" }
        val document = Document()
        document.add(StringField(ID, entryId, Field.Store.YES))
        coordinates.forEach { document.add(StringField(COORDINATE, it.toString(), Field.Store.NO)) }
        document.add(faceOf(DOC_FACE, docVector))
        rewriteVector?.let { document.add(faceOf(REWRITE_FACE, it)) }
        writer.updateDocument(Term(ID, entryId), document)
    }

    private fun faceOf(field: String, vector: FloatArray): KnnFloatVectorField {
        if (vector.size != dimensions) {
            throw IndexBasisException(
                "this index holds $dimensions-dimension vectors; got ${vector.size}",
            )
        }
        // Normalised here rather than by the encoder, so the index owns the decision its
        // similarity function depends on. DOT_PRODUCT over unit vectors is cosine, without
        // Lucene recomputing a magnitude it can require instead.
        return KnnFloatVectorField(field, normalised(vector), VectorSimilarityFunction.DOT_PRODUCT)
    }

    fun commit() = writer.commit()

    /**
     * The needs nearest [queryVector], within [scope], scored as the better of their two faces.
     *
     * **The scope is applied inside the search, never over its results.** A post-filter is wrong
     * twice: the containment boundary becomes something a caller can forget, and a query whose
     * top-k happens to be entirely out of scope returns *nothing* while in-scope matches sit
     * below the cut. RAD-0047 reproduced the second on 15,000 documents — an unfiltered k=10
     * returned 0 in-scope hits with 250 in-scope entries present.
     *
     * An empty [scope] returns nothing, and means it: a project that resolved no dependencies can
     * see no entries. That is not the same as an unscoped search, which this deliberately cannot
     * express.
     */
    fun search(
        queryVector: FloatArray,
        scope: Set<Coordinate>,
        k: Int = 10,
        /**
         * Which faces to consult. Both is the index; one is how the contribution of each was
         * measured, and the measurement is the reason the default is both.
         */
        faces: List<String> = listOf(DOC_FACE, REWRITE_FACE),
    ): List<VectorHit> {
        require(faces.isNotEmpty()) { "a search over no faces is not a search" }
        if (scope.isEmpty()) return emptyList()
        val target = normalised(queryVector)
        val filter: Query = TermInSetQuery(COORDINATE, scope.map { BytesRef(it.toString()) })
        DirectoryReader.open(directory).use { reader ->
            val searcher = IndexSearcher(reader)
            // OVER-FETCH, and it is not a tuning knob. Each face is a separate top-k search whose
            // results are then unioned, so a face asked for exactly k can have a hit that the max
            // would have kept pushed out by the *other* face before the two are ever compared.
            // Measured: at k=10 with no oversampling, a target the rewrite face ranked 5th was
            // absent from the combined result entirely.
            val perFace = k * OVERSAMPLE
            val queries = faces.map { KnnFloatVectorQuery(it, target, perFace, filter) as Query }
            // Zero tie-breaker: the score is the max of the two faces, not their sum. Summing
            // would reward an entry for matching mediocrely twice over one that matches well once.
            val best = DisjunctionMaxQuery(queries, 0.0f)
            val storedFields = searcher.storedFields()
            return searcher.search(best, k).scoreDocs.map {
                VectorHit(storedFields.document(it.doc).get(ID), it.score)
            }
        }
    }

    override fun close() {
        writer.close()
        directory.close()
    }

    companion object {
        const val ID = "id"
        const val COORDINATE = "coordinate"
        const val DOC_FACE = "doc_vec"
        const val REWRITE_FACE = "rewrite_vec"

        /**
         * How much deeper each face searches than the caller asked for.
         *
         * The union of two top-k lists is not the top-k of the union. Ten is enough that a hit
         * either face ranks inside the requested k survives to be compared, and cheap: an HNSW
         * search to 100 costs a fraction of one to 10, not ten times.
         */
        private const val OVERSAMPLE = 10

        private const val ENCODER = "encoder"
        private const val POOLING = "pooling"
        private const val DIMENSIONS = "dimensions"

        /**
         * Opens or creates an index for exactly one (encoder, pooling, width).
         *
         * Reopening an existing index under a different basis throws rather than appending. There
         * is no correct merge: the vectors already in there cannot be compared with the ones the
         * caller is about to produce, and appending would leave an index that works.
         */
        fun open(path: Path, encoder: String, pooling: Pooling, dimensions: Int): TwoFacedIndex {
            require(encoder.isNotBlank()) { "the encoder must be recorded, not left to be guessed" }
            require(pooling != Pooling.None) { "an index of per-token vectors is not an index" }
            require(dimensions > 0) { "dimensions must be positive" }
            val directory = FSDirectory.open(path)
            existingBasis(directory)?.let { (foundEncoder, foundPooling, foundDimensions) ->
                if (foundEncoder != encoder || foundPooling != pooling.name ||
                    foundDimensions != dimensions.toString()
                ) {
                    directory.close()
                    throw IndexBasisException(
                        "this index holds vectors from $foundEncoder/$foundPooling/${foundDimensions}d; " +
                            "opening it as $encoder/${pooling.name}/${dimensions}d would mix two bases",
                    )
                }
            }
            return TwoFacedIndex(directory, encoder, pooling, dimensions)
        }

        private fun existingBasis(directory: FSDirectory): Triple<String?, String?, String?>? =
            runCatching {
                DirectoryReader.open(directory).use { reader ->
                    val data = reader.indexCommit.userData
                    if (data[ENCODER] == null) null
                    else Triple(data[ENCODER], data[POOLING], data[DIMENSIONS])
                }
            }.getOrNull()

        private fun normalised(vector: FloatArray): FloatArray {
            var sum = 0.0
            vector.forEach { sum += it.toDouble() * it }
            val length = kotlin.math.sqrt(sum)
            if (length == 0.0) throw IndexBasisException("a zero vector has no direction to index")
            return FloatArray(vector.size) { (vector[it] / length).toFloat() }
        }
    }
}
