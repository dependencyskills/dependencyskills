package org.dependencyskills.codex.index

import org.dependencyskills.codex.classifier.Decision
import org.dependencyskills.codex.classifier.ProseClassifier
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.EntryState
import org.dependencyskills.codex.harvester.SourcesJarHarvester
import org.dependencyskills.codex.harvester.harvest
import org.dependencyskills.codex.inference.Pooling
import org.dependencyskills.codex.inference.openEncoder
import org.dependencyskills.codex.inference.openGenerator
import org.dependencyskills.codex.summariser.Summariser
import org.dependencyskills.codex.summariser.Summary
import org.dependencyskills.codex.summariser.summarise
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The whole pipeline, over one real project's dependencies, in one pass.
 *
 * Harvest, classify, summarise, index, query — every component this project has built, on the 59
 * pinned coordinates of `experiments/test5/CORPUS-MANIFEST.md` and that experiment's 17 needs.
 *
 * **This exists to answer one question the earlier measurement could not.** `TwoFacedRetrievalTest`
 * scored the two-faced index at 4 of 17 at recall@10, with a rewrite face covering only 397 of
 * 11,155 entries — borrowed from `experiments/summariser/summaries.json` because #7 did not exist
 * yet. RAD-0040 measured 15 of 17 with *both* faces on all 220 of a much smaller corpus. Whether
 * that survives at 11,155 entries with a real rewrite on every one of them is the number the
 * design rests on, and nothing has ever produced it.
 *
 * A measurement, not a pass mark. What it finds is a finding.
 */
class SummarisedRetrievalTest {

    private data class Need(val query: String, val target: String, val where: String)

    private fun property(name: String): String =
        System.getProperty(name) ?: error("set -D$name; a measurement with no input is not a skip")

    private fun needs(): List<Need> =
        Regex("""\{[^}]*}""").findAll(File(property("codex.needs")).readText()).map { block ->
            fun field(name: String) = Regex(""""$name"\s*:\s*"((?:[^"\\]|\\.)*)"""")
                .find(block.value)!!.groupValues[1].replace("\\\"", "\"")
            Need(field("query"), field("target"), field("where"))
        }.toList()

    /** Identical to `test5/embed_corpus.py`'s `key_text`, so these numbers sit beside the others. */
    private fun keyText(symbol: String, text: String): String =
        symbol.substringAfterLast('.') + ". " + text.take(MAX_CHARS)

    @Test
    fun `harvest, classify, summarise, index and query, end to end`() {
        val encoderModel = property("codex.encoder.model")
        val summariserModel = property("codex.summariser.model")
        val jars = property("codex.corpus").split(File.pathSeparator)
            .filter { it.isNotBlank() }.map { Path.of(it) }
        assertTrue(jars.size > 40, "the corpus should be the pinned dependency set, not a fragment")
        val questions = needs()

        // Persisted, not temporary. Summarising this corpus costs 26 minutes, and a question
        // about how the vectors are COMBINED should not cost that again to ask. Harvest is
        // content-addressed and `summarise` is idempotent per model, so a re-run is minutes.
        val work = Path.of(System.getProperty("codex.reports") ?: ".").resolveSibling("end-to-end")
        Files.createDirectories(work)
        val store = work.resolve("codex.db")
        val scope = LinkedHashSet<Coordinate>()

        // -- harvest ---------------------------------------------------------------------------
        val harvestMs = timed {
            Codex.open(store).use { codex ->
                val harvester = SourcesJarHarvester()
                jars.forEach { jar ->
                    val coordinate = Coordinate("maven", jar.fileName.toString().removeSuffix("-sources.jar"))
                    scope.add(coordinate)
                    codex.harvest(coordinate, jar, harvester)
                }
            }
        }

        // -- classify: degrade suspect prose BEFORE anything paraphrases it ----------------------
        // Order matters. An entry the classifier flags must reach the summariser already degraded,
        // so its rewrite is withheld rather than stored - paraphrasing suspect prose well does not
        // make it less suspect.
        var flagged = 0
        val classifyMs = timed {
            val classifier = ProseClassifier()
            Codex.open(store).use { codex ->
                val seen = HashSet<String>()
                scope.forEach { coordinate ->
                    codex.entriesOf(coordinate).forEach { entry ->
                        if (!seen.add(entry.id)) return@forEach
                        if (entry.docFormat !in classifier.calibratedFor()) return@forEach
                        val doc = codex.rawDocumentation(entry.id) ?: return@forEach
                        if (classifier.classify(doc, entry.docFormat).decision == Decision.Suspect) {
                            codex.setEntryState(entry.id, EntryState.Degraded)
                            flagged++
                        }
                    }
                }
            }
        }

        // -- summarise -------------------------------------------------------------------------
        var stored = 0
        var degraded = 0
        var withheld = 0
        val byRule = LinkedHashMap<String, Int>()
        val refusals = StringBuilder("symbol\tsignature\trule\tdetail\trefused\n")
        val summariseMs = timed {
            openGenerator(summariserModel, contextTokens = 2048).use { generator ->
                val summariser = Summariser(generator, model = File(summariserModel).name)
                Codex.open(store).use { codex ->
                    scope.forEach { coordinate ->
                        val report = codex.summarise(coordinate, summariser) { entry, summary ->
                            // Every refusal, written where a rule change can be re-scored against
                            // it without paying for the model again. Tab-separated and escaped so
                            // one refusal is one line whatever the model produced.
                            if (summary is Summary.Degraded) {
                                fun flat(text: String?) = (text ?: "")
                                    .replace("\\", "\\\\").replace("\t", " ").replace("\n", "\\n")
                                refusals.appendLine(
                                    listOf(entry.symbol, entry.signature, summary.rule, summary.detail, summary.raw)
                                        .joinToString("\t") { flat(it) },
                                )
                            }
                        }
                        stored += report.stored
                        degraded += report.degraded
                        withheld += report.withheld
                        report.byRule.forEach { (rule, n) -> byRule.merge(rule, n, Int::plus) }
                    }
                }
            }
        }

        // -- index and query ---------------------------------------------------------------------
        val vectors = work.resolve("vectors")
        vectors.toFile().deleteRecursively()
        var entries = 0
        var rewriteFaces = 0
        val rows = ArrayList<String>()
        val exactRanks = ArrayList<Int>()
        val arms = linkedMapOf(
            "documentation face only" to listOf(TwoFacedIndex.DOC_FACE),
            "rewrite face only" to listOf(TwoFacedIndex.REWRITE_FACE),
            "both faces, two vectors" to listOf(TwoFacedIndex.DOC_FACE, TwoFacedIndex.REWRITE_FACE),
        )
        val vectorHits = arms.mapValues { intArrayOf(0, 0) }
        val lexicalHits = intArrayOf(0, 0)
        // Held so the combination can be scored EXACTLY - every entry by the better of its two
        // faces - rather than as a union of two approximate top-k lists. 11,155 pairs of 384
        // floats is 34 MB, which is nothing beside what the model calls already cost.
        val docVectors = LinkedHashMap<String, FloatArray>()
        val rewriteVectors = LinkedHashMap<String, FloatArray>()
        val symbolOfId = LinkedHashMap<String, String>()
        val exactHits = intArrayOf(0, 0)
        val unionHits = intArrayOf(0, 0)
        val fusedHits = intArrayOf(0, 0)

        val indexMs = timed {
            openEncoder(encoderModel, Pooling.Mean).use { encoder ->
                TwoFacedIndex.open(vectors, File(encoderModel).name, encoder.pooling, encoder.dimensions)
                    .use { index ->
                        Codex.open(store).use { codex ->
                            val seen = HashSet<String>()
                            scope.forEach { coordinate ->
                                codex.entriesOf(coordinate).forEach { entry ->
                                    if (!seen.add(entry.id)) return@forEach
                                    val doc = codex.rawDocumentation(entry.id) ?: return@forEach
                                    entries++
                                    val docVector = encoder.embed(keyText(entry.symbol, doc))
                                    val rewriteVector = entry.rewrite?.let {
                                        rewriteFaces++
                                        encoder.embed(keyText(entry.symbol, it))
                                    }
                                    index.add(entry.id, entry.coordinates, docVector, rewriteVector)
                                    docVectors[entry.id] = docVector
                                    rewriteVector?.let { rewriteVectors[entry.id] = it }
                                    symbolOfId[entry.id] = entry.symbol
                                }
                            }
                        }
                        index.commit()

                        Codex.open(store).use { codex ->
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
                                // Exact: every entry scored by the better of its two faces, with
                                // no top-k in between. This is what "scores as its best-matching
                                // face" means literally, and what the Lucene arm approximates.
                                val exact = docVectors.keys
                                    .sortedByDescending { id ->
                                        maxOf(
                                            cosine(query, docVectors.getValue(id)),
                                            rewriteVectors[id]?.let { cosine(query, it) } ?: -1.0,
                                        )
                                    }
                                    .indexOfFirst { symbolOfId[it] == need.target }
                                if (exact == 0) exactHits[0]++
                                if (exact in 0..9) exactHits[1]++

                                // The ceiling the combiner is measured against: what you would get
                                // by taking each face's own top ten and keeping everything in
                                // either. Not a design - a bound on what combining could recover.
                                val union = listOf(TwoFacedIndex.DOC_FACE, TwoFacedIndex.REWRITE_FACE)
                                    .flatMap { face -> index.search(query, scope, k = 10, faces = listOf(face)) }
                                    .any { symbolOfId[it.entryId] == need.target }
                                if (union) unionHits[1]++

                                // Reciprocal rank fusion: combine by RANK, not by score. The
                                // exact-max arm shows max is faithfully implemented and still
                                // loses entries either face found, which can only mean the two
                                // faces' score distributions are not comparable - a doc-face 0.72
                                // and a rewrite-face 0.72 do not mean the same thing. Rank fusion
                                // does not care. Note this is fusing RANKINGS, which RAD-0040 did
                                // not test; what it measured worse was fusing the VECTORS.
                                val byDoc = docVectors.keys
                                    .sortedByDescending { cosine(query, docVectors.getValue(it)) }
                                val byRewrite = rewriteVectors.keys
                                    .sortedByDescending { cosine(query, rewriteVectors.getValue(it)) }
                                val rrf = HashMap<String, Double>()
                                listOf(byDoc, byRewrite).forEach { ranked ->
                                    ranked.take(RRF_DEPTH).forEachIndexed { i, id ->
                                        rrf.merge(id, 1.0 / (RRF_K + i + 1), Double::plus)
                                    }
                                }
                                val fused = rrf.entries.sortedByDescending { it.value }
                                    .indexOfFirst { symbolOfId[it.key] == need.target }
                                if (fused == 0) fusedHits[0]++
                                if (fused in 0..9) fusedHits[1]++

                                val lexical = codex.search(need.query, scope, limit = 10)
                                    .hits.indexOfFirst { it.entry.symbol == need.target }
                                if (lexical == 0) lexicalHits[0]++
                                if (lexical in 0..9) lexicalHits[1]++

                                fun show(rank: Int) = if (rank < 0) "—" else "${rank + 1}"
                                exactRanks.add(exact)
                                rows.add(
                                    "| ${show(lexical)} | ${show(ranks.getValue("documentation face only"))} " +
                                        "| ${show(ranks.getValue("rewrite face only"))} " +
                                        "| ${show(ranks.getValue("both faces, two vectors"))} " +
                                        "| ${need.where} | `${need.target}` |",
                                )
                            }
                        }
                    }
            }
        }

        val n = questions.size
        val report = buildString {
            appendLine("# The whole pipeline, end to end")
            appendLine()
            appendLine("59 coordinates, $n needs, one pass. Every component this project has built.")
            appendLine()
            appendLine("| stage | | |")
            appendLine("|---|---|---|")
            appendLine("| harvest | $entries entries | ${harvestMs / 1000} s |")
            appendLine("| classify | $flagged flagged (${"%.3f".format(100.0 * flagged / entries)}%) | ${classifyMs / 1000} s |")
            appendLine("| summarise | $stored stored, $degraded degraded, $withheld withheld | ${summariseMs / 1000} s |")
            appendLine("| index and query | $rewriteFaces rewrite faces of $entries | ${indexMs / 1000} s |")
            appendLine()
            appendLine("Summariser: `${File(summariserModel).name}`. Encoder: `${File(encoderModel).name}`, mean pooling.")
            appendLine()
            appendLine("## Retrieval")
            appendLine()
            appendLine("| arm | searches | recall@1 | recall@10 |")
            appendLine("|---|---:|---|---|")
            appendLine("| lexical (#4's baseline) | $entries | ${lexicalHits[0]} of $n | ${lexicalHits[1]} of $n |")
            arms.forEach { (arm, faces) ->
                val population = if (faces == listOf(TwoFacedIndex.REWRITE_FACE)) rewriteFaces else entries
                appendLine("| $arm | $population | ${vectorHits.getValue(arm)[0]} of $n | ${vectorHits.getValue(arm)[1]} of $n |")
            }
            appendLine("| **both, scored exactly** | $entries | **${exactHits[0]} of $n** | **${exactHits[1]} of $n** |")
            appendLine("| **both, reciprocal rank fusion** | $entries | **${fusedHits[0]} of $n** | **${fusedHits[1]} of $n** |")
            appendLine("| *either face's own top ten (a bound, not a design)* | $entries | — | *${unionHits[1]} of $n* |")
            appendLine()
            appendLine("The last two rows are the combiner under test. `both faces` unions two")
            appendLine("approximate top-k lists through Lucene; `scored exactly` ranks every entry by")
            appendLine("the better of its two faces with no top-k in between. Where they differ, the")
            appendLine("difference is the combiner rather than the index.")
            appendLine()
            appendLine("## Why entries were refused")
            appendLine()
            appendLine("| rule | count |")
            appendLine("|---|---:|")
            byRule.entries.sortedByDescending { it.value }.forEach { appendLine("| ${it.key} | ${it.value} |") }
            appendLine()
            appendLine("## Rank of the target in the first ten, by arm")
            appendLine()
            appendLine("| lexical | doc face | rewrite face | both | where | target |")
            appendLine("|---|---|---|---|---|---|")
            rows.forEach { appendLine(it) }
        }
        val directory = Path.of(System.getProperty("codex.reports") ?: ".")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("end-to-end.md"), report)
        Files.writeString(directory.resolve("refusals.tsv"), refusals.toString())
        println(report)

        assertTrue(entries > 1_000, "expected a real corpus; indexed $entries")
        // Not `stored + degraded + withheld == entries`: a resumed run does no work, because
        // `summarise` skips entries already carrying this model's output. The invariant that
        // holds either way is that nothing was left unconsidered, which is a property of the
        // STORE rather than of this pass.
        val unsummarised = Codex.open(store).use { codex ->
            scope.flatMap { codex.entriesOf(it) }.distinctBy { it.id }
                .count { it.provenance.summariser == null }
        }
        assertEquals(0, unsummarised, "entries the summariser never reached")
    }

    private fun cosine(a: FloatArray, b: FloatArray): Double {
        var dot = 0.0; var na = 0.0; var nb = 0.0
        for (i in a.indices) { dot += a[i] * b[i]; na += a[i] * a[i]; nb += b[i] * b[i] }
        return dot / (kotlin.math.sqrt(na) * kotlin.math.sqrt(nb))
    }

    private fun timed(body: () -> Unit): Long {
        val started = System.currentTimeMillis()
        body()
        return System.currentTimeMillis() - started
    }

    private companion object {
        const val MAX_CHARS = 900

        /** The conventional RRF constant, and the depth each face is ranked to. */
        const val RRF_K = 60
        const val RRF_DEPTH = 100
    }
}
