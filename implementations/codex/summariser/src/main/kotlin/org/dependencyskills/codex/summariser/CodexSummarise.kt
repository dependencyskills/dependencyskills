package org.dependencyskills.codex.summariser

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.SummaryOutcome

/**
 * What a pass over a coordinate did.
 *
 * Counted by outcome rather than as a rate, and the refusals are counted **by rule**. RAD-0040
 * found 11 of 16 degradations coming from a single over-broad rule, and that was only visible
 * because each refusal named itself. A pass that reported "24% degraded" would have hidden it,
 * and the fix — narrowing one pattern — would never have been found.
 */
data class SummaryReport(
    val stored: Int,
    val degraded: Int,
    val withheld: Int,
    /** Entries with no raw documentation to summarise. Not a failure, and not a success either. */
    val nothingToRead: Int,
    /** Refusal rule to how often it fired, including `generator failed`. */
    val byRule: Map<String, Int>,
) {
    val considered: Int get() = stored + degraded + withheld + nothingToRead
}

/**
 * Summarises every entry a coordinate owns, out of band.
 *
 * **Never call this from a build.** It is a model call per documented declaration — one small
 * project yields about 5,400 — and the machine-level store is what makes that affordable at all,
 * since a library summarised once is summarised for every project on the machine. The Gradle
 * plugin depends on `core` and cannot reach this module, which is the structural half of that
 * rule; this comment is the other half.
 *
 * Entries already carrying a summary from the same model are **not** re-summarised. A second pass
 * over a coordinate is therefore cheap and idempotent, which matters because the first one may
 * well be interrupted.
 */
fun Codex.summarise(coordinate: Coordinate, summariser: Summariser): SummaryReport {
    var stored = 0
    var degraded = 0
    var withheld = 0
    var nothingToRead = 0
    val byRule = LinkedHashMap<String, Int>()

    entriesOf(coordinate).forEach { entry ->
        if (entry.provenance.summariser == summariser.model) return@forEach
        val doc = rawDocumentation(entry.id)
        if (doc.isNullOrBlank()) {
            nothingToRead++
            return@forEach
        }
        val summary = summariser.summarise(entry.symbol, entry.signature, doc)
        val rewrite = (summary as? Summary.Rewritten)?.sentence
        if (summary is Summary.Degraded) byRule.merge(summary.rule, 1, Int::plus)

        when (recordSummary(entry.id, rewrite, summary.model)) {
            SummaryOutcome.Stored -> stored++
            SummaryOutcome.Degraded -> degraded++
            SummaryOutcome.Withheld -> withheld++
            // The entry was read from this store a moment ago, so it cannot have gone; if it has,
            // something else is writing concurrently and that is worth not swallowing.
            SummaryOutcome.Unknown -> error("entry ${entry.id} vanished mid-pass")
        }
    }
    return SummaryReport(stored, degraded, withheld, nothingToRead, byRule)
}
