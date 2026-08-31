package org.dependencyskills.codex.indexer

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.HarvestState
import org.dependencyskills.codex.inference.Pooling
import org.dependencyskills.codex.inference.TextEncoder
import org.dependencyskills.codex.inference.TextGenerator
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * Where a coordinate ends up when the pipeline does not run to completion.
 *
 * The states are not bookkeeping. `NoSource` says *never ask again* and is the set [#28] will index
 * from bytecode; `Failed` says *ask again later*. Confusing them either re-queues a library that
 * will never have sources on every pass for ever, or writes off one that had a bad afternoon.
 *
 * No model is needed for any of this, which is the point of testing it separately: these paths
 * decide what happens to most coordinates on a machine that has not downloaded many sources.
 */
class IndexerRoutingTest {

    /** Never called on these paths. Present because the pipeline holds them for the whole pass. */
    private class Unused : TextGenerator, TextEncoder {
        override fun generate(prompt: String, maxTokens: Int): String =
            error("the generator must not be reached when there is nothing to summarise")
        override val dimensions = 4
        override val pooling = Pooling.Mean
        override fun embed(text: String): FloatArray =
            error("the encoder must not be reached when there is nothing to index")
        override fun close() = Unit
    }

    /** Neither the cache nor the network has anything, which is what these paths are about. */
    private fun indexer(work: Path): Indexer {
        val unused = Unused()
        return Indexer(
            store = work.resolve("codex.db"),
            generator = unused, generatorName = "unused",
            encoder = unused, encoderName = "unused",
            vectors = work.resolve("vectors"),
            sources = SourcesSupplier(
                staging = work.resolve("staging"),
                cache = { null },
                download = { _, _ -> false },
            ),
        )
    }

    @Test
    fun `a coordinate with no sources in the cache is NoSource, and stays that way`() {
        val work = createTempDirectory("routing")
        val coordinate = Coordinate("maven", "com.example:absent:1.0")
        Codex.open(work.resolve("codex.db")).use { it.seen(coordinate) }

        // Nothing on this machine has these sources - stated by the seam, not by luck.
        val outcome = indexer(work).run().single()

        assertEquals(HarvestState.NoSource, outcome.state)
        assertTrue("sources" in (outcome.detail ?: ""), outcome.detail ?: "")
        Codex.open(work.resolve("codex.db")).use {
            assertEquals(HarvestState.NoSource, assertNotNull(it.coordinate(coordinate)).state)
        }
    }

    @Test
    fun `a NoSource coordinate is not picked up by the next pass`() {
        // Otherwise every pass re-attempts every library that publishes no sources, for ever -
        // which on a machine with a cold cache is most of them.
        val work = createTempDirectory("routing")
        val coordinate = Coordinate("maven", "com.example:absent:1.0")
        Codex.open(work.resolve("codex.db")).use { it.seen(coordinate) }

        val first = indexer(work).run()
        assertEquals(1, first.size)

        val second = indexer(work).run()
        assertTrue(second.isEmpty(), "a settled coordinate came back: $second")
    }

    @Test
    fun `nothing pending is not an error, and reports nothing`() {
        val work = createTempDirectory("routing")
        Codex.open(work.resolve("codex.db")).use { }
        assertTrue(indexer(work).run().isEmpty())
    }

    @Test
    fun `each coordinate is reported as it completes, not at the end`() {
        // A pass is minutes of model calls. A service that says nothing until it finishes cannot
        // be told from one that has hung.
        val work = createTempDirectory("routing")
        val store = work.resolve("codex.db")
        Codex.open(store).use {
            it.seen(Coordinate("maven", "com.example:one:1.0"))
            it.seen(Coordinate("maven", "com.example:two:1.0"))
        }
        val seen = mutableListOf<Indexer.Outcome>()
        val all = indexer(work).run { seen += it }
        assertEquals(2, seen.size, "expected one report per coordinate")
        assertEquals(all.size, seen.size)
    }
}
