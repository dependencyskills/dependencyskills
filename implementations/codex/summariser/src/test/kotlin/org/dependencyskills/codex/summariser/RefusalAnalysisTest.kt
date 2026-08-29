package org.dependencyskills.codex.summariser

import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Re-scores refused candidates against the rules, without calling a model.
 *
 * A pass over one project's dependencies is 24 minutes of generation. Without the refused text
 * kept somewhere, **every question about a rule costs another 24 minutes to ask** — which is why
 * the Python reference kept it and why the harness now writes `refusals.tsv`.
 *
 * It re-scores with the **real** [Verification], deliberately. Analysing this data with a second
 * implementation of the same rules would risk measuring a verifier that is not the one that
 * ships, and mistaking one for the other is a failure this project has already made twice.
 *
 *   ./gradlew :summariser:refusals -Dcodex.refusals=.../refusals.tsv
 */
class RefusalAnalysisTest {

    private data class Refusal(val symbol: String, val signature: String, val rule: String, val raw: String)

    @Test
    fun `what the rules would do differently`() {
        val path = System.getProperty("codex.refusals")
            ?: error("set -Dcodex.refusals to a refusals.tsv; there is nothing to analyse without one")
        val refusals = File(path).readLines().drop(1).mapNotNull { line ->
            val f = line.split("\t")
            if (f.size < 5) null
            else Refusal(f[0], f[1], f[2], f[4].replace("\\n", "\n").replace("\\\\", "\\"))
        }
        assertTrue(refusals.size > 100, "expected a real pass; got ${refusals.size}")

        // -- 1. does a shape rule hide a safety one? ---------------------------------------------
        // `verify` returns on the FIRST rule that fires, so a candidate refused as "too long" may
        // also carry an imperative that was never evaluated. If shape masks safety, then "81% are
        // shape failures" undercounts safety - and recovering the shape ones would let previously
        // masked safety problems through. This has to be known BEFORE anything is loosened.
        val shape = setOf("too long", "more than one sentence", "empty")
        val masked = refusals.filter { it.rule in shape }.mapNotNull { refusal ->
            val firstSentence = firstSentenceOf(refusal.raw)
            val verdict = Verification.verify(firstSentence, refusal.signature)
            (verdict as? Verdict.Refused)?.takeIf { it.rule !in shape }?.let { refusal.rule to it.rule }
        }

        // -- 2. would stopping at the first sentence recover them? -------------------------------
        val recovered = refusals.filter { it.rule in shape }.count { refusal ->
            Verification.verify(firstSentenceOf(refusal.raw), refusal.signature) is Verdict.Accepted
        }
        val shapeTotal = refusals.count { it.rule in shape }

        // -- 3. what would a different word bound cost or buy? -----------------------------------
        val atBound = listOf(30, 40, 50, 60, 80, 100).associateWith { bound ->
            refusals.count { it.rule == "too long" && wordsIn(it.raw) <= bound }
        }

        val report = buildString {
            appendLine("# Re-scoring the refusals")
            appendLine()
            appendLine("${refusals.size} refused candidates, re-judged by the shipped rules. No model was run.")
            appendLine()
            appendLine("## Does a shape rule hide a safety one?")
            appendLine()
            appendLine("Taking the first sentence of every shape refusal and re-verifying it:")
            appendLine()
            appendLine("| | |")
            appendLine("|---|---|")
            appendLine("| shape refusals | $shapeTotal |")
            appendLine("| **hiding a safety rule underneath** | **${masked.size}** |")
            masked.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }
                .forEach { (pair, n) -> appendLine("| `${pair.first}` was hiding `${pair.second}` | $n |") }
            appendLine()
            appendLine("## Would stopping at the first sentence recover them?")
            appendLine()
            appendLine("| | |")
            appendLine("|---|---|")
            appendLine("| shape refusals | $shapeTotal |")
            appendLine("| **accepted once truncated to the first sentence** | **$recovered** |")
            appendLine("| still refused | ${shapeTotal - recovered} |")
            appendLine()
            appendLine("## What a different word bound would admit")
            appendLine()
            appendLine("Of the ${refusals.count { it.rule == "too long" }} refused as too long, how many fall under each bound:")
            appendLine()
            appendLine("| bound | admitted |")
            appendLine("|---:|---:|")
            atBound.forEach { (bound, n) -> appendLine("| $bound words | $n |") }
        }
        val out = Path.of(System.getProperty("codex.reports") ?: ".")
        Files.createDirectories(out)
        Files.writeString(out.resolve("refusal-analysis.md"), report)
        println(report)
    }

    /** The same sentence split `Verification` counts with, so the two cannot disagree. */
    private fun firstSentenceOf(text: String): String =
        Verification.normalise(text).split(Regex("(?<=[.!?])\\s+")).firstOrNull()?.trim().orEmpty()

    private fun wordsIn(text: String): Int =
        Verification.normalise(text).split(Regex("\\s+")).count { it.isNotEmpty() }
}
