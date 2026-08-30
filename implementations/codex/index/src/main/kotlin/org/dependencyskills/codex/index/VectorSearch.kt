package org.dependencyskills.codex.index

import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.inference.TextEncoder
import org.dependencyskills.codex.inference.openEncoder
import java.nio.file.Files
import java.nio.file.Path

/**
 * The vector index and the encoder that has to agree with it, opened together.
 *
 * They are one object because they are one decision. An index is only searchable by an encoder
 * producing vectors in the same basis — same model, same pooling, same width — and holding them
 * apart is how a caller ends up embedding a query with one encoder and searching an index built
 * by another. The vectors would be the right width and the wrong basis, and nothing would fail.
 *
 * [openIfBuilt] returns null when there is no index yet, and that is an ordinary state rather
 * than an error: a store that has been harvested but not embedded answers lexically, which is
 * what it could always do.
 */
class VectorSearch private constructor(
    private val index: TwoFacedIndex,
    private val encoder: TextEncoder,
    /** Which model produced the vectors in here, for anything that reports what it used. */
    val encoderName: String,
) : AutoCloseable {

    /** Entry ids, best first, scored as each entry's better face. */
    fun search(need: String, scope: Set<Coordinate>, limit: Int): List<String> =
        index.search(encoder.embed(need), scope, limit).map { it.entryId }

    override fun close() {
        runCatching { index.close() }
        runCatching { encoder.close() }
    }

    companion object {
        /** Beside the store, because it is derived from it and is rebuilt when the store changes. */
        const val DIRECTORY = "vectors"

        /**
         * Opens the index beside [storeFile], or returns null when nothing has built one.
         *
         * Null is not a failure to report. Nothing builds an index yet — that is the service's
         * job — so this returning null is the ordinary case today and the caller falls back to
         * lexical search, which is what it did before this existed.
         */
        fun openIfBuilt(storeFile: Path): VectorSearch? {
            val directory = storeFile.parent?.resolve(DIRECTORY) ?: return null
            if (!Files.isDirectory(directory)) return null
            val packaged = PackagedEncoder.unpack() ?: return null
            val encoder = runCatching { openEncoder(packaged.model.toString(), packaged.pooling) }
                .getOrElse { return null }
            // The index refuses to open under a basis it was not built with, so a mismatch is
            // caught here rather than becoming a quietly wrong ranking.
            val index = runCatching {
                TwoFacedIndex.open(directory, packaged.name, packaged.pooling, packaged.dimensions)
            }.getOrElse {
                encoder.close()
                return null
            }
            return VectorSearch(index, encoder, packaged.name)
        }
    }
}
