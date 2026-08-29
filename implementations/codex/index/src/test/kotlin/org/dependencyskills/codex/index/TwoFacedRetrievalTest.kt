package org.dependencyskills.codex.index

import com.google.gson.JsonParser
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.harvester.SourcesJarHarvester
import org.dependencyskills.codex.harvester.harvest
import org.dependencyskills.codex.inference.Pooling
import org.dependencyskills.codex.inference.openEncoder
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Does the two-faced index beat the lexical baseline, on the same corpus and the same needs?
 *
 * **A measurement, not a pass mark** — the same stance `LexicalBaselineTest` takes. Both arms run
 * in this one test rather than one arm here and a number quoted from elsewhere, because a
 * comparison assembled from two runs is a comparison that can quietly stop being one.
 *
 * The corpus is the 59 pinned coordinates of `experiments/test5/CORPUS-MANIFEST.md`, read from
 * that manifest by both this module's build and the harvester's. The 17 needs are that
 * experiment's, unchanged.
 *
 * **The rewrites are borrowed, and that is the honest part of this.** Producing them is #7 and is
 * out of scope here; #6 says in as many words that the index can be tested with hand-written
 * rewrites first. So the second face comes from `experiments/summariser/summaries.json` — 220
 * real rewrites over this corpus, 214 kept and 6 degraded — and the other ~10,900 entries carry a
 * documentation face only. That is a *partially* summarised store, which is what a real one looks
 * like at any moment anyway, and it means the rewrite face is measured on 2% coverage rather than
 * on the whole corpus. Every number below should be read against that.
 */
class TwoFacedRetrievalTest {

    private data class Need(val query: String, val target: String, val where: String)

    private fun property(name: String): String =
        System.getProperty(name) ?: error("set -D$name; a measurement with no input is not a skip")

    private fun needs(): List<Need> =
        Regex("""\{[^}]*}""").findAll(File(property("codex.needs")).readText()).map { block ->
            fun field(name: String) = Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(block.value)!!.groupValues[1].replace("\\\"", "\"")
            Need(field("query"), field("target"), field("where"))
        }.toList()

    /** symbol -> the rewritten sentence, absent for an entry whose rewrite was rejected. */
    private fun rewrites(): Map<String, String> =
        JsonParser.parseReader(File(property("codex.rewrites")).bufferedReader()).asJsonArray
            .map { it.asJsonObject }
            .filter { it.get("capability")?.isJsonNull == false }
            .associate { it.get("symbol").asString to it.get("capability").asString }

    /**
     * The text that is embedded, identical to `test5/embed_corpus.py`'s `key_text`.
     *
     * Kept the same so these numbers sit beside the ones already measured against this corpus
     * rather than beside nothing — which is the same reason the needs and the corpus are pinned.
     */
    private fun keyText(symbol: String, doc: String): String =
        symbol.substringAfterLast('.') + ". " + doc.take(MAX_CHARS)

    @Test
    fun `two faces against the lexical baseline, same corpus and same needs`() {
        val model = property("codex.encoder.model")
        val jars = property("codex.corpus").split(File.pathSeparator)
            .filter { it.isNotBlank() }.map { Path.of(it) }
        assertTrue(jars.size > 40, "the corpus should be the pinned dependency set, not a fragment")
        val questions = needs()
        assertTrue(questions.size >= 10, "the story asks for at least ten needs; found ${questions.size}")
        val rewrites = rewrites()

        val store = createTempDirectory("two-faced").resolve("codex.db")
        val scope = LinkedHashSet<Coordinate>()
        Codex.open(store).use { codex ->
            val harvester = SourcesJarHarvester()
            jars.forEach { jar ->
                val coordinate = Coordinate("maven", jar.fileName.toString().removeSuffix("-sources.jar"))
                scope.add(coordinate)
                codex.harvest(coordinate, jar, harvester)
            }
        }

        val vectors = createTempDirectory("two-faced").resolve("vectors")
        var docFaces = 0
        var rewriteFaces = 0
        var entries = 0
        val encoderName = File(model).name

        openEncoder(model, Pooling.Mean).use { encoder ->
            assertEquals(Pooling.Mean, encoder.pooling, "the runtime must confirm the pooling asked for")
            TwoFacedIndex.open(vectors, encoderName, encoder.pooling, encoder.dimensions).use { index ->
                Codex.open(store).use { codex ->
                    val seen = HashSet<String>()
                    scope.forEach { coordinate ->
                        codex.entriesOf(coordinate).forEach { entry ->
                            if (!seen.add(entry.id)) return@forEach
                            val doc = codex.rawDocumentation(entry.id) ?: return@forEach
                            entries++
                            val rewrite = rewrites[entry.symbol]
                            index.add(
                                entryId = entry.id,
                                coordinates = entry.coordinates,
                                docVector = encoder.embed(keyText(entry.symbol, doc)),
                                rewriteVector = rewrite?.let {
                                    rewriteFaces++
                                    encoder.embed(keyText(entry.symbol, it))
                                },
                            )
                            docFaces++
                        }
                    }
                }
                index.commit()

                val arms = linkedMapOf(
                    "documentation face only" to listOf(TwoFacedIndex.DOC_FACE),
                    "rewrite face only" to listOf(TwoFacedIndex.REWRITE_FACE),
                    "both faces, two vectors" to listOf(TwoFacedIndex.DOC_FACE, TwoFacedIndex.REWRITE_FACE),
                )
                val vectorHits = arms.mapValues { intArrayOf(0, 0) }
                val lexicalHits = intArrayOf(0, 0)
                val rows = ArrayList<String>()

                Codex.open(store).use { codex ->
                    // The symbol a hit belongs to. The index deals in entry ids; the needs name
                    // symbols, and resolving one to the other is the store's job, not the index's.
                    fun symbolOf(id: String) = codex.entry(id)?.symbol

                    questions.forEach { need ->
                        val query = encoder.embed(need.query)
                        val ranks = arms.mapValues { (_, faces) ->
                            index.search(query, scope, k = 10, faces = faces)
                                .indexOfFirst { symbolOf(it.entryId) == need.target }
                        }
                        arms.keys.forEach { arm ->
                            val rank = ranks.getValue(arm)
                            if (rank == 0) vectorHits.getValue(arm)[0]++
                            if (rank in 0..9) vectorHits.getValue(arm)[1]++
                        }
                        val lexical = codex.search(need.query, scope, limit = 10)
                            .hits.indexOfFirst { it.entry.symbol == need.target }
                        if (lexical == 0) lexicalHits[0]++
                        if (lexical in 0..9) lexicalHits[1]++

                        fun show(rank: Int) = if (rank < 0) "—" else "${rank + 1}"
                        rows.add(
                            "| ${show(lexical)} | ${show(ranks.getValue("documentation face only"))} " +
                                "| ${show(ranks.getValue("rewrite face only"))} " +
                                "| ${show(ranks.getValue("both faces, two vectors"))} " +
                                "| ${need.where} | `${need.target}` |",
                        )
                    }
                }

                val n = questions.size
                val report = buildString {
                    appendLine("# The two-faced index against the lexical baseline")
                    appendLine()
                    appendLine("Same 59 coordinates, same $n needs, one run. The rewrite face covers")
                    appendLine("$rewriteFaces of $entries entries — #7 produces the rest.")
                    appendLine()
                    appendLine("| | |")
                    appendLine("|---|---|")
                    appendLine("| entries indexed | $entries |")
                    appendLine("| documentation faces | $docFaces |")
                    appendLine("| rewrite faces | $rewriteFaces |")
                    appendLine("| encoder | $encoderName, ${encoder.pooling} pooling, ${encoder.dimensions}d |")
                    appendLine()
                    appendLine("| arm | searches | recall@1 | recall@10 |")
                    appendLine("|---|---:|---|---|")
                    appendLine("| lexical (#4's baseline) | $entries | ${lexicalHits[0]} of $n | ${lexicalHits[1]} of $n |")
                    arms.forEach { (arm, faces) ->
                        // The population each arm actually searches. Without it the rewrite-only
                        // row reads as a result rather than as a different, much smaller haystack.
                        val population = if (faces == listOf(TwoFacedIndex.REWRITE_FACE)) rewriteFaces else entries
                        appendLine("| $arm | $population | ${vectorHits.getValue(arm)[0]} of $n | ${vectorHits.getValue(arm)[1]} of $n |")
                    }
                    appendLine()
                    appendLine("**The rewrite-only row is not comparable to the others.** It searches only the")
                    appendLine("$rewriteFaces entries that carry a rewrite, and those were summarised *because* they")
                    appendLine("were this evaluation's sample - so all $n targets are inside a haystack 28x smaller")
                    appendLine("than the one every other arm searches. It is here to show the face works, not to")
                    appendLine("be read as a score.")
                    appendLine()
                    appendLine("Rank of the target in the first ten, by arm.")
                    appendLine()
                    appendLine("| lexical | doc face | rewrite face | both | where | target |")
                    appendLine("|---|---|---|---|---|---|")
                    rows.forEach { appendLine(it) }
                }
                val directory = Path.of(System.getProperty("codex.reports") ?: ".")
                Files.createDirectories(directory)
                Files.writeString(directory.resolve("two-faced-retrieval.md"), report)
                println(report)

                // The measurement has to have happened, and the fixture has to be the real one.
                // What it found is a finding; the story reads it and decides.
                assertTrue(entries > 1_000, "expected a real corpus; indexed $entries")
                assertEquals(entries, docFaces, "every entry must carry a documentation face")
                assertTrue(rewriteFaces in 100..1_000, "expected the borrowed rewrites; got $rewriteFaces")

                // The one thing here that IS a pass mark, because the story states it as one:
                // "beats #4's lexical baseline on the same needs". Strict, not >=, because that is
                // what the acceptance criterion claims - and if an encoder or pooling change ever
                // makes it a tie, that is precisely the moment someone should have to look rather
                // than watch a number drift under a passing test.
                val both = vectorHits.getValue("both faces, two vectors")
                assertTrue(
                    both[1] > lexicalHits[1],
                    "the two-faced index must beat the lexical baseline at recall@10: " +
                        "two-faced ${both[1]}, lexical ${lexicalHits[1]} of $n",
                )
            }
        }
    }

    private companion object {
        /** `test5/embed_corpus.py`'s truncation, kept identical so the numbers stay comparable. */
        const val MAX_CHARS = 900
    }
}
