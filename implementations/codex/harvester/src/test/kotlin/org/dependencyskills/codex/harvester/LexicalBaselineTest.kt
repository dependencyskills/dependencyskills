package org.dependencyskills.codex.harvester

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * How well lexical search does over real harvested documentation, with no vectors.
 *
 * **This is a measurement, not a judgement.** The criterion is that the numbers exist and are
 * written down, not that they are good — a poor score passes here and becomes the baseline the
 * two-faced index has to beat. Publishing it either way is the point: a retrieval design with
 * no baseline cannot tell an improvement from a change.
 *
 * It lives in this module because it needs both halves — the harvester to build a corpus and
 * the store to query it — and the harvester is the module that can see both.
 *
 * The corpus is 59 pinned coordinates: one real Ktor server project's resolved dependencies,
 * exactly as `experiments/test5/CORPUS-MANIFEST.md` records them. The 17 needs are that
 * experiment's, unchanged, so these numbers sit beside the ones already measured with
 * embeddings against the same corpus rather than beside nothing.
 */
class LexicalBaselineTest {

    private data class Need(val query: String, val target: String, val where: String)

    private fun needs(): List<Need> {
        val file = File(System.getProperty("codex.needs") ?: error("no needs file on the test JVM"))
        // Small and fixed-shape, so a hand-rolled read beats adding a JSON dependency to a
        // module that otherwise needs none.
        val text = file.readText()
        return Regex("""\{[^}]*}""").findAll(text).map { block ->
            fun field(name: String) = Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(block.value)!!.groupValues[1].replace("\\\"", "\"")
            Need(field("query"), field("target"), field("where"))
        }.toList()
    }

    private fun corpus(): List<Path> =
        (System.getProperty("codex.corpus") ?: error("no corpus on the test JVM"))
            .split(File.pathSeparator).filter { it.isNotBlank() }.map { Path.of(it) }

    @Test
    fun `recall over one real project's dependencies, with no vectors`() {
        val jars = corpus()
        assertTrue(jars.size > 40, "the corpus should be the pinned dependency set, not a fragment")
        val questions = needs()
        assertTrue(questions.size >= 10, "the story asks for at least ten needs; found ${questions.size}")

        val store = createTempDirectory("baseline").resolve("codex.db")
        val harvester = SourcesJarHarvester()
        val scope = LinkedHashSet<Coordinate>()
        var entriesWritten = 0
        var sourceless = 0

        val harvestMillis = Codex.open(store).use { codex ->
            timed {
                jars.forEach { jar ->
                    // The coordinate a sources jar came from is not recoverable from the file
                    // name alone once a version has a hyphen in it, so the jar's own name is
                    // used as the coordinate here. The measurement only needs the scope to be
                    // the harvested set; #3 is what produces real coordinates.
                    val coordinate = Coordinate("maven", jar.fileName.toString().removeSuffix("-sources.jar"))
                    scope.add(coordinate)
                    when (val result = codex.harvest(coordinate, jar, harvester)) {
                        is HarvestResult.Harvested -> entriesWritten += result.entries.size
                        is HarvestResult.NoSource -> sourceless++
                        is HarvestResult.Failed -> sourceless++
                    }
                }
            }
        }

        val rows = ArrayList<String>()
        var atOne = 0
        var atTen = 0
        var nearby = 0
        var missingTargets = 0
        val timings = ArrayList<Long>()
        val singleScopeTimings = ArrayList<Long>()

        Codex.open(store).use { codex ->
            val total = codex.entryCount()
            assertTrue(total > 1_000, "expected a real corpus; got $total entries")
            val indexed = scope.flatMap { codex.entriesOf(it) }.mapTo(HashSet()) { it.symbol }

            questions.forEach { need ->
                // Guard, not decoration. A target that was never harvested makes the recall
                // number a measurement of the harvester wearing the search's name, and the two
                // failures look identical from the outside.
                val present = need.target in indexed
                if (!present) missingTargets++

                val started = System.nanoTime()
                val hits = codex.search(need.query, scope, limit = 10).hits
                timings.add((System.nanoTime() - started) / 1_000)

                val single = System.nanoTime()
                codex.search(need.query, setOf(scope.first()), limit = 10)
                singleScopeTimings.add((System.nanoTime() - single) / 1_000)

                val rank = hits.indexOfFirst { it.entry.symbol == need.target } + 1
                if (rank == 1) atOne++
                if (rank in 1..10) atTen++
                // A weaker question, asked because the ranking kept landing next door: did
                // anything from the target's own declaring scope reach the first ten? A caller
                // handed `Mutex.tryLock` when it wanted `Mutex` is in a different position from
                // one handed a test scheduler.
                val neighbourhood = need.target.substringBeforeLast('.')
                if (hits.any { it.entry.symbol.startsWith("$neighbourhood.") || it.entry.symbol == need.target }) nearby++

                rows.add(
                    "| ${if (rank > 0) "$rank" else "—"} | ${need.where} | " +
                        "${if (present) "yes" else "**no**"} | `${need.target}` |"
                )
            }

            publish(
                entries = total,
                coordinates = scope.size,
                sourceless = sourceless,
                harvestMillis = harvestMillis,
                atOne = atOne,
                atTen = atTen,
                nearby = nearby,
                missingTargets = missingTargets,
                questions = questions.size,
                timings = timings,
                singleScopeTimings = singleScopeTimings,
                rows = rows,
            )
        }

        // The only assertions are that the measurement happened and is recorded. A recall of
        // zero is a real finding about lexical search over raw documentation, not a failure of
        // this test — and turning it into one would create a reason not to publish it.
        assertEquals(questions.size, rows.size)
        assertTrue(timings.all { it > 0 })
        assertTrue(entriesWritten > 0)
    }

    private fun <T> timed(body: () -> T): Long {
        val started = System.nanoTime()
        body()
        return (System.nanoTime() - started) / 1_000_000
    }

    private fun publish(
        entries: Int,
        coordinates: Int,
        sourceless: Int,
        harvestMillis: Long,
        atOne: Int,
        atTen: Int,
        nearby: Int,
        missingTargets: Int,
        questions: Int,
        timings: List<Long>,
        singleScopeTimings: List<Long>,
        rows: List<String>,
    ) {
        val sorted = timings.sorted()
        val median = sorted[sorted.size / 2]
        val singleSorted = singleScopeTimings.sorted()
        val report = buildString {
            appendLine("# Lexical baseline")
            appendLine()
            appendLine("SQLite FTS5 over raw harvested documentation. No vectors, no rewriting, no classifier.")
            appendLine()
            appendLine("| | |")
            appendLine("|---|---|")
            appendLine("| coordinates harvested | $coordinates |")
            appendLine("| with no sources jar | $sourceless |")
            appendLine("| entries indexed | $entries |")
            appendLine("| harvest | ${harvestMillis} ms |")
            appendLine("| targets absent from the index | $missingTargets |")
            appendLine("| **recall@1** | **$atOne of $questions** |")
            appendLine("| **recall@10** | **$atTen of $questions** |")
            appendLine("| something from the target's declaring scope, in the first ten | $nearby of $questions |")
            appendLine("| query over $coordinates coordinates, median | ${median} µs |")
            appendLine("| query over $coordinates coordinates, slowest | ${sorted.last()} µs |")
            appendLine("| query over 1 coordinate, median | ${singleSorted[singleSorted.size / 2]} µs |")
            appendLine()
            appendLine("`targets absent` is the guard on the rest: a target that was never harvested would")
            appendLine("make this a measurement of the harvester under the search's name.")
            appendLine()
            appendLine("| rank | where | in the index | target |")
            appendLine("|---:|---|---|---|")
            rows.forEach { appendLine(it) }
        }
        val directory = Path.of(System.getProperty("codex.reports") ?: ".")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("lexical-baseline.md"), report)
        println(report)
    }
}
