package org.dependencyskills.codex.indexer

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.EntryState
import org.dependencyskills.codex.core.HarvestState
import org.dependencyskills.codex.inference.Pooling
import org.dependencyskills.codex.inference.TextEncoder
import org.dependencyskills.codex.inference.TextGenerator
import java.io.File
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * A library that publishes no sources, indexed from its compiled jar instead (#28).
 *
 * The routing is the point. A coordinate with sources must never come down this path, and one
 * without them must not be written off as `NoSource` while its classes sit in the build's cache.
 */
class BytecodeFallbackTest {

    private val acme = Coordinate("maven", "org.slf4j:slf4j-api:2.0.17")

    private val classesJar: Path =
        (System.getProperty("codex.indexer.fixtures")
            // Not a skip. A test that quietly passes when its input is missing is the failure
            // this project keeps re-learning, one level up.
            ?: error("no fixtures on the test JVM - the build did not resolve them"))
            .split(File.pathSeparator).map { Path.of(it) }
            .first { it.fileName.toString() == "slf4j-api-2.0.17.jar" }

    /**
     * The generator must never be reached on this path, and says so by failing.
     *
     * That is the acceptance criterion tested literally: pointing a summariser at a signature
     * would hand attacker-controlled text to a generative model with nothing to rewrite. The
     * encoder IS reached — a degraded entry is embedded on its signature so it stays findable.
     */
    private class NeverSummarises : TextGenerator, TextEncoder {
        var embedded = 0
        override fun generate(prompt: String, maxTokens: Int): String =
            error("the summariser must never be called on a signature")
        override val dimensions = 4
        override val pooling = Pooling.Mean
        override fun embed(text: String): FloatArray {
            embedded++
            return FloatArray(4) { text.length.toFloat() / (it + 1) }
        }
        override fun close() = Unit
    }

    private val sourcesJar: Path =
        (System.getProperty("codex.indexer.fixtures") ?: error("no fixtures"))
            .split(File.pathSeparator).map { Path.of(it) }
            .first { it.fileName.toString() == "slf4j-api-2.0.17-sources.jar" }

    private fun indexer(work: Path, models: NeverSummarises, classes: (Coordinate) -> Path?) =
        Indexer(
            store = work.resolve("codex.db"),
            generator = models, generatorName = "unused",
            encoder = models, encoderName = "test-encoder",
            vectors = work.resolve("vectors"),
            // No sources anywhere: neither the build's cache nor the network has them, which is
            // exactly the population this path exists for.
            sources = SourcesSupplier(
                staging = work.resolve("staging"),
                cache = { null },
                download = { _, _ -> false },
            ),
            classes = classes,
        )

    @Test
    fun `a coordinate with no sources is indexed from its compiled jar, not written off`() {
        val work = createTempDirectory("fallback")
        val models = NeverSummarises()
        Codex.open(work.resolve("codex.db")).use { it.seen(acme) }

        val outcomes = mutableListOf<Indexer.Outcome>()
        indexer(work, models) { classesJar }.run { outcomes.add(it) }

        val outcome = assertNotNull(outcomes.firstOrNull { it.coordinate == acme })
        assertEquals(HarvestState.Indexed, outcome.state)
        assertTrue(outcome.entries > 0, "the compiled jar should have yielded entries")
        assertTrue(outcome.indexed > 0, "and they should have been embedded")
        assertEquals(outcome.entries, models.embedded, "every entry is embedded on its signature")

        Codex.open(work.resolve("codex.db")).use { codex ->
            val entries = codex.entriesOf(acme)
            assertTrue(entries.isNotEmpty())
            // Degraded by construction: a signature, and no prose invented to sit beside it.
            assertEquals(setOf(EntryState.Degraded), entries.map { it.state }.toSet())
            assertTrue(entries.all { it.rewrite == null })
            assertEquals(setOf("asm-classes-jar/1"), entries.map { it.provenance.extractor }.toSet())

            // Findable by its exact symbol, and it returns the declaration a caller needs.
            val logger = assertNotNull(entries.firstOrNull { it.symbol == "org.slf4j.ILoggerFactory.getLogger" })
            assertEquals("public abstract org.slf4j.Logger getLogger(java.lang.String)", logger.signature)
        }
    }

    @Test
    fun `nothing is fetched - the jar comes from the cache the build already filled`() {
        // The premise of the whole ticket: a private repository's artifacts are already on disk
        // because the project compiled against them, and no credentials live here. So the path
        // must work with a supplier that refuses to download anything, which is what these use.
        val work = createTempDirectory("nofetch")
        val models = NeverSummarises()
        Codex.open(work.resolve("codex.db")).use { it.seen(acme) }
        var asked = 0
        indexer(work, models) { asked++; classesJar }.run { }
        assertEquals(1, asked, "the compiled jar is resolved from the cache, once")
    }

    @Test
    fun `a library that has sources never falls through to bytecode`() {
        // Bytecode is a fallback, never a supplement. If both paths ran, one coordinate would
        // carry a summarised entry and a bare signature for the same symbol, and a search would
        // return the library twice saying two different things about it.
        //
        // The sources pipeline itself fails here, because its summariser is the one that refuses
        // to be called — that is incidental. What is asserted is which path was taken.
        val work = createTempDirectory("hassources")
        val models = NeverSummarises()
        Codex.open(work.resolve("codex.db")).use { it.seen(acme) }
        var askedForClasses = 0

        Indexer(
            store = work.resolve("codex.db"),
            generator = models, generatorName = "unused",
            encoder = models, encoderName = "test-encoder",
            vectors = work.resolve("vectors"),
            sources = SourcesSupplier(
                staging = work.resolve("staging"),
                cache = { sourcesJar },
                download = { _, _ -> false },
            ),
            classes = { askedForClasses++; classesJar },
        ).run { }

        assertEquals(0, askedForClasses, "a library with sources must not be read from bytecode")
        Codex.open(work.resolve("codex.db")).use { codex ->
            assertTrue(
                codex.entriesOf(acme).none { it.provenance.extractor == "asm-classes-jar/1" },
                "no bytecode entry may exist for a coordinate that publishes sources",
            )
        }
    }

    @Test
    fun `no sources and no classes stays NoSource, so it is not retried for ever`() {
        val work = createTempDirectory("neither")
        val models = NeverSummarises()
        Codex.open(work.resolve("codex.db")).use { it.seen(acme) }

        val outcomes = mutableListOf<Indexer.Outcome>()
        indexer(work, models) { null }.run { outcomes.add(it) }

        val outcome = assertNotNull(outcomes.firstOrNull { it.coordinate == acme })
        assertEquals(HarvestState.NoSource, outcome.state)
        assertEquals(0, outcome.entries)
        assertEquals(0, models.embedded)
    }
}
