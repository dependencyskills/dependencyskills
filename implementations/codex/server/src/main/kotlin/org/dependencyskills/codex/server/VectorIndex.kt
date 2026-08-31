package org.dependencyskills.codex.server

import org.dependencyskills.codex.index.VectorSearch
import org.slf4j.LoggerFactory
import java.nio.file.Path

/**
 * The vector index, reopened when one appears.
 *
 * **This exists because of a bug worth remembering.** `VectorSearch.openIfBuilt` is a snapshot: it
 * looks for a directory and returns null when there is none. The service called it once at startup,
 * on a machine that had never indexed anything, and held that null for the rest of its life — so it
 * would run a pass, build a perfectly good index, and go on answering lexically until somebody
 * restarted it. Nothing failed. Answers were simply worse than they should have been, for a reason
 * no log mentioned.
 *
 * So the index is held behind something that can be told to look again, and the indexer tells it
 * after every pass.
 */
class VectorIndex(private val store: Path) : AutoCloseable {

    private val logger = LoggerFactory.getLogger(VectorIndex::class.java)

    @Volatile
    private var current: VectorSearch? = VectorSearch.openIfBuilt(store)

    /** The index, or null when nothing has built one yet. Null is ordinary: search goes lexical. */
    fun get(): VectorSearch? = current

    val encoderName: String? get() = current?.encoderName

    /**
     * Looks again, after something may have built or extended the index.
     *
     * Reopened wholesale rather than refreshed in place: a Lucene reader is a point-in-time view,
     * and this runs once per pass rather than once per query, so the cost is irrelevant next to
     * being wrong.
     */
    @Synchronized
    fun refresh() {
        val previous = current
        val opened = runCatching { VectorSearch.openIfBuilt(store) }
            .onFailure { logger.warn("could not open the vector index: {}", it.message) }
            .getOrNull()
        if (opened == null && previous != null) return   // keep what works
        current = opened
        runCatching { previous?.close() }
        if (previous == null && opened != null) {
            logger.info("vector index now open, encoder {} — answers are no longer lexical", opened.encoderName)
        }
    }

    override fun close() {
        runCatching { current?.close() }
        current = null
    }
}
