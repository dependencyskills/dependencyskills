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

        // -- 2b. what the narrowed imperative rule now admits, and what it still refuses --------
        // #22: the rule matched a modal ANYWHERE in a sentence, and modals are ordinary in
        // descriptive prose. This re-judges every candidate the old rule refused, against the
        // rule that ships now, and separates two very different outcomes: one that now passes is
        // a false positive recovered, one still refused by ANOTHER rule was never this rule's to
        // claim in the first place.
        val wasImperative = refusals.filter { it.rule == "imperative" }
        val nowVerdicts = wasImperative.map { it to Verification.verify(it.raw, it.signature) }
        val recoveredImperative = nowVerdicts.count { it.second is Verdict.Accepted }
        val stillRefused = nowVerdicts.mapNotNull { (_, v) -> (v as? Verdict.Refused)?.rule }
            .groupingBy { it }.eachCount()

        // -- 2c. which rule would have caught it if `imperative` did not exist at all? ----------
        // The open question in #22: is another rule already doing this work? Answered by rule
        // rather than by argument.
        val coveredElsewhere = stillRefused.filterKeys { it != "imperative" }.values.sum()

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
            appendLine("## What the narrowed `imperative` rule changes (#22)")
            appendLine()
            appendLine("Every candidate the OLD word-matching rule refused, re-judged by the rule that ships now.")
            appendLine()
            appendLine("| | |")
            appendLine("|---|---:|")
            appendLine("| refused as `imperative` by the old rule | ${wasImperative.size} |")
            appendLine("| **now accepted — false positives recovered** | **$recoveredImperative** |")
            appendLine("| still refused, by any rule | ${wasImperative.size - recoveredImperative} |")
            appendLine("| of those, refused by a rule that is *not* `imperative` | $coveredElsewhere |")
            appendLine()
            appendLine("Which rule now names them:")
            appendLine()
            appendLine("| rule | count |")
            appendLine("|---|---:|")
            stillRefused.entries.sortedByDescending { it.value }
                .forEach { (rule, n) -> appendLine("| `$rule` | $n |") }
            appendLine()
            appendLine("A candidate refused by a rule other than `imperative` was never this rule's")
            appendLine("to claim: something else was already covering it, which is the question #22 asked.")
            appendLine()
            appendLine("## The refusal rate, restated")
            appendLine()
            val total = refusals.size
            val corrected = total - recoveredImperative
            appendLine("| | count | of ${total} refusals |")
            appendLine("|---|---:|---:|")
            appendLine("| refusals as measured with the old rule | $total | 100% |")
            appendLine("| refusals under the rule that ships now | $corrected | " +
                "${"%.1f".format(100.0 * corrected / total)}% |")
            appendLine("| entries handed back to retrieval | $recoveredImperative | " +
                "${"%.1f".format(100.0 * recoveredImperative / total)}% |")
            appendLine()
            appendLine("Every recovered entry was degraded to signature-only, which RAD-0040 measured")
            appendLine("as unfindable. Any claim of the form \"the verifier refuses X% of unsafe output\"")
            appendLine("has to be restated against the corrected number, not the original one.")
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
