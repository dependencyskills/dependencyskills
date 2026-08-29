package org.dependencyskills.codex.index

import org.apache.lucene.index.DirectoryReader
import org.apache.lucene.store.FSDirectory
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.inference.Pooling
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The index's structural properties, on synthetic vectors.
 *
 * Synthetic on purpose. Every claim here is about *shape* — two faces, the better of them, the
 * scope boundary, one basis per index — and a real encoder would make each test slow, dependent
 * on a model file, and no more convincing. What the real encoder is for is measuring retrieval,
 * which is a different question and a different test.
 */
class TwoFacedIndexTest {

    private val encoder = "BAAI/bge-small-en-v1.5"
    private val acme = Coordinate("maven", "com.example.acme:acme-core:1.0.0")
    private val other = Coordinate("maven", "com.example.other:other-core:1.0.0")

    private fun index(path: Path = createTempDirectory("index").resolve("vectors"), dim: Int = 2) =
        TwoFacedIndex.open(path, encoder, Pooling.Mean, dim)

    /** A unit vector at [degrees] around the circle, so every cosine here is arithmetic. */
    private fun at(degrees: Double): FloatArray {
        val radians = Math.toRadians(degrees)
        return floatArrayOf(kotlin.math.cos(radians).toFloat(), kotlin.math.sin(radians).toFloat())
    }

    // -- two faces, scored as the better one ---------------------------------------------------

    @Test
    fun `an entry scores as its best-matching face, not the sum of both`() {
        // `near-on-one-face` matches perfectly on its rewrite and not at all on its doc.
        // `middling-on-both` matches each face equally and moderately.
        //
        // The discriminator: summing the two faces puts `middling-on-both` first (0.71 + 0.71),
        // taking the better of them puts `near-on-one-face` first (1.00). Summing would reward an
        // entry for matching mediocrely twice over one that matches well once.
        index().use { index ->
            index.add("near-on-one-face", setOf(acme), docVector = at(90.0), rewriteVector = at(0.0))
            index.add("middling-on-both", setOf(acme), docVector = at(45.0), rewriteVector = at(45.0))
            index.commit()

            val hits = index.search(at(0.0), setOf(acme), k = 2)
            assertEquals(listOf("near-on-one-face", "middling-on-both"), hits.map { it.entryId })
            assertTrue(
                hits[0].score > hits[1].score,
                "the better face must win outright, not by a tie-break",
            )
        }
    }

    @Test
    fun `either face alone can be the one that finds an entry`() {
        index().use { index ->
            index.add("found-by-its-doc", setOf(acme), docVector = at(0.0), rewriteVector = at(180.0))
            index.add("found-by-its-rewrite", setOf(acme), docVector = at(180.0), rewriteVector = at(0.0))
            index.commit()

            val hits = index.search(at(0.0), setOf(acme), k = 2).map { it.entryId }.toSet()
            assertEquals(setOf("found-by-its-doc", "found-by-its-rewrite"), hits)
        }
    }

    @Test
    fun `the two texts are never concatenated into one key`() {
        // Structural, and it is the reason the class has no third field: RAD-0040 measured a
        // fused vector at 10 of 17 against 15 for two kept apart - worse than either face alone.
        // A test that only checked scoring would not notice a fused field being added later.
        val path = createTempDirectory("index").resolve("vectors")
        index(path).use { index ->
            index.add("an-entry", setOf(acme), docVector = at(0.0), rewriteVector = at(90.0))
            index.commit()
        }
        FSDirectory.open(path).use { directory ->
            DirectoryReader.open(directory).use { reader ->
                val vectorFields = reader.leaves()
                    .flatMap { leaf -> leaf.reader().fieldInfos.map { it } }
                    .filter { it.vectorDimension > 0 }
                    .map { it.name }
                    .toSet()
                assertEquals(setOf(TwoFacedIndex.DOC_FACE, TwoFacedIndex.REWRITE_FACE), vectorFields)
            }
        }
    }

    // -- a degraded entry stays findable --------------------------------------------------------

    @Test
    fun `an entry with no rewrite keeps its documentation key and is still found`() {
        // A degraded entry is not a deleted one. It loses the right to display prose and the
        // second key; an entry with no key at all would be indistinguishable from one silently
        // dropped, which is the failure the degraded state exists to prevent.
        index().use { index ->
            index.add("degraded", setOf(acme), docVector = at(0.0), rewriteVector = null)
            index.commit()
            assertEquals(listOf("degraded"), index.search(at(0.0), setOf(acme)).map { it.entryId })
        }
    }

    @Test
    fun `a degraded entry contributes no rewrite face`() {
        val path = createTempDirectory("index").resolve("vectors")
        index(path).use { index ->
            index.add("degraded", setOf(acme), docVector = at(0.0), rewriteVector = null)
            index.commit()
        }
        FSDirectory.open(path).use { directory ->
            DirectoryReader.open(directory).use { reader ->
                val leaf = reader.leaves().single().reader()
                assertEquals(null, leaf.getFloatVectorValues(TwoFacedIndex.REWRITE_FACE))
                assertTrue(leaf.getFloatVectorValues(TwoFacedIndex.DOC_FACE) != null)
            }
        }
    }

    // -- the scope boundary ---------------------------------------------------------------------

    @Test
    fun `the scope filter is inside the search, and a post-filter would have returned nothing`() {
        // Built the way RAD-0047 built it: the globally nearest vectors all belong to a coordinate
        // the asking project does not have, with in-scope matches sitting below them. That is the
        // case a post-filter gets wrong - not by ranking badly, but by returning an empty result
        // that reads as "your dependencies have nothing like that".
        index(dim = 2).use { index ->
            repeat(200) { i ->
                index.add("out-$i", setOf(other), docVector = at(0.5 * i / 200.0))
            }
            repeat(50) { i ->
                index.add("in-$i", setOf(acme), docVector = at(60.0 + i * 0.1))
            }
            index.commit()

            val everything = index.search(at(0.0), setOf(acme, other), k = 10)
            assertTrue(
                everything.none { it.entryId.startsWith("in-") },
                "the fixture is wrong: the globally nearest must all be out of scope",
            )

            val scoped = index.search(at(0.0), setOf(acme), k = 10)
            assertEquals(10, scoped.size, "a post-filter over the unscoped top-10 would return 0")
            assertTrue(scoped.all { it.entryId.startsWith("in-") })
        }
    }

    @Test
    fun `one project's entries cannot appear in another's results at any k`() {
        index().use { index ->
            // Deliberately identical vectors: the other project's entries are not merely ranked
            // lower, they are exactly as good a match and must still be unreachable.
            index.add("acme-entry", setOf(acme), docVector = at(0.0))
            index.add("other-entry", setOf(other), docVector = at(0.0))
            index.commit()

            listOf(1, 2, 5, 10, 100, 1000).forEach { k ->
                val asAcme = index.search(at(0.0), setOf(acme), k).map { it.entryId }
                assertEquals(listOf("acme-entry"), asAcme, "at k=$k")
                val asOther = index.search(at(0.0), setOf(other), k).map { it.entryId }
                assertEquals(listOf("other-entry"), asOther, "at k=$k")
            }
        }
    }

    @Test
    fun `an entry owned by both projects is visible to both`() {
        index().use { index ->
            index.add("shared", setOf(acme, other), docVector = at(0.0))
            index.commit()
            assertEquals(listOf("shared"), index.search(at(0.0), setOf(acme)).map { it.entryId })
            assertEquals(listOf("shared"), index.search(at(0.0), setOf(other)).map { it.entryId })
        }
    }

    @Test
    fun `an empty scope returns nothing rather than everything`() {
        index().use { index ->
            index.add("acme-entry", setOf(acme), docVector = at(0.0))
            index.commit()
            assertContentEquals(emptyList(), index.search(at(0.0), emptySet()))
        }
    }

    // -- one basis per index ---------------------------------------------------------------------

    @Test
    fun `an index built under one pooling refuses to be reopened under another`() {
        // The failure this prevents is silent: vectors under a different pooling are the right
        // width and the wrong basis, so they index, score and rank - wrongly. RAD-0048 measured
        // CLS beating mean on one model and collapsing on another, which is why pooling travels
        // with the encoder rather than being a global setting.
        val path = createTempDirectory("index").resolve("vectors")
        TwoFacedIndex.open(path, encoder, Pooling.Mean, 2).use {
            it.add("an-entry", setOf(acme), docVector = at(0.0)); it.commit()
        }
        val refused = assertFailsWith<IndexBasisException> {
            TwoFacedIndex.open(path, encoder, Pooling.Cls, 2)
        }
        assertTrue(refused.message!!.contains("Mean"))
        assertTrue(refused.message!!.contains("Cls"))
    }

    @Test
    fun `an index built with one encoder refuses to be reopened with another`() {
        val path = createTempDirectory("index").resolve("vectors")
        TwoFacedIndex.open(path, encoder, Pooling.Mean, 2).use {
            it.add("an-entry", setOf(acme), docVector = at(0.0)); it.commit()
        }
        assertFailsWith<IndexBasisException> {
            TwoFacedIndex.open(path, "BAAI/bge-m3", Pooling.Mean, 2)
        }
    }

    @Test
    fun `an index refuses a vector of the wrong width`() {
        index(dim = 2).use { index ->
            assertFailsWith<IndexBasisException> {
                index.add("wrong", setOf(acme), docVector = floatArrayOf(1f, 0f, 0f))
            }
        }
    }

    @Test
    fun `reopening under the same basis is fine and keeps what was there`() {
        val path = createTempDirectory("index").resolve("vectors")
        TwoFacedIndex.open(path, encoder, Pooling.Mean, 2).use {
            it.add("first", setOf(acme), docVector = at(0.0)); it.commit()
        }
        TwoFacedIndex.open(path, encoder, Pooling.Mean, 2).use {
            it.add("second", setOf(acme), docVector = at(1.0)); it.commit()
            assertEquals(2, it.search(at(0.0), setOf(acme), k = 10).size)
        }
    }

    @Test
    fun `an index of per-token vectors is refused at the door`() {
        assertFailsWith<IllegalArgumentException> {
            TwoFacedIndex.open(createTempDirectory("i").resolve("v"), encoder, Pooling.None, 2)
        }
    }

    @Test
    fun `the encoder must be named, not left blank`() {
        assertFailsWith<IllegalArgumentException> {
            TwoFacedIndex.open(createTempDirectory("i").resolve("v"), "  ", Pooling.Mean, 2)
        }
    }

    @Test
    fun `a zero vector has no direction and is refused`() {
        index().use { index ->
            assertFailsWith<IndexBasisException> {
                index.add("zero", setOf(acme), docVector = floatArrayOf(0f, 0f))
            }
        }
    }

    @Test
    fun `an entry with no coordinate is refused, because it could never be in scope`() {
        index().use { index ->
            assertFailsWith<IllegalArgumentException> {
                index.add("orphan", emptySet(), docVector = at(0.0))
            }
        }
    }

    @Test
    fun `pooling codes are written down, not taken from the enum's order`() {
        // Reordering the enum must not silently repool an index. The codes are llama.cpp's.
        assertEquals(0, Pooling.None.code)
        assertEquals(1, Pooling.Mean.code)
        assertEquals(2, Pooling.Cls.code)
        assertEquals(3, Pooling.Last.code)
        assertEquals(Pooling.Mean, Pooling.ofCode(1))
        assertEquals(null, Pooling.ofCode(99))
    }

    @Test
    fun `vectors are unit length once indexed, whatever the encoder produced`() {
        // The encoder deliberately does not normalise; the index does, because its similarity
        // function depends on it. A caller handing over an unnormalised vector must still get
        // cosine, not a magnitude-weighted score.
        index().use { index ->
            index.add("long", setOf(acme), docVector = floatArrayOf(300f, 0f))
            index.add("short", setOf(acme), docVector = floatArrayOf(0.003f, 0f))
            index.commit()
            val hits = index.search(floatArrayOf(7f, 0f), setOf(acme), k = 2)
            assertEquals(2, hits.size)
            assertEquals(
                hits[0].score, hits[1].score, 1e-5f,
                "magnitude must not decide which of two identical directions wins",
            )
        }
    }
}
