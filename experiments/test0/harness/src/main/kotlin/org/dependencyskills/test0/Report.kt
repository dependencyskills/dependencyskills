package org.dependencyskills.test0

import java.nio.file.Files
import java.nio.file.Path

/**
 * Runs both arms over the whole fixture and renders a comparison — the experiment's
 * actionable output. Both arms parse the same source; the difference is the finding.
 */
object Report {

    fun generate(fixturesDir: Path): String {
        val root = fixturesDir.resolve("test0")
        val files = mutableListOf<Path>()
        Files.walk(root).use { s ->
            s.filter { it.toString().endsWith(".kt") }.forEach { files.add(it) }
        }
        val raw = KotlinPsiParser().parse(files).associateBy { it.symbol }
        val enriched = EnrichedPsiParser().parse(files).associateBy { it.symbol }
        val symbols = (raw.keys + enriched.keys).toSortedSet()

        val b = StringBuilder()
        b.appendLine("# test0 bake-off — raw (PSI) vs enriched\n")
        b.appendLine("Both arms parse the same `fixtures/test0` source. The difference is the finding.\n")

        b.appendLine("## Entries\n")
        b.appendLine("| symbol | arm | capability | trig | category | since | sample | tier | source |")
        b.appendLine("|---|---|---|--:|---|---|---|---|---|")
        for (sym in symbols) {
            for ((label, e) in listOf("raw" to raw[sym], "enriched" to enriched[sym])) {
                if (e == null) continue
                b.appendLine(
                    "| `${short(sym)}` | $label | ${cap(e.capability)} | ${e.triggers.size} | " +
                        "${e.category ?: "—"} | ${e.since ?: "—"} | ${sampleCell(e.sample)} | " +
                        "${e.tier} | ${e.source} |",
                )
            }
        }

        b.appendLine("\n## Deltas — where enrichment changed the entry\n")
        var deltas = 0
        for (sym in symbols) {
            val r = raw[sym] ?: continue
            val en = enriched[sym] ?: continue
            if (r.capability != en.capability) {
                b.appendLine("- `${short(sym)}` — capability: ${cap(r.capability)} → ${cap(en.capability)}")
                deltas++
            }
            if (r.sample != en.sample) {
                b.appendLine("- `${short(sym)}` — sample: ${sampleCell(r.sample)} → ${sampleCell(en.sample)}")
                deltas++
            }
        }
        if (deltas == 0) b.appendLine("_(none)_")

        val rawCap = raw.values.count { it.capability != null }
        val enCap = enriched.values.count { it.capability != null }
        val expanded = enriched.values.count { it.sample.isBody() }
        b.appendLine("\n## Summary\n")
        b.appendLine("- entries: ${symbols.size}")
        b.appendLine("- capability coverage: raw $rawCap/${raw.size}, enriched $enCap/${enriched.size} " +
            "(enrichment recovered ${enCap - rawCap})")
        b.appendLine("- designed-tier entries (custom tags): ${enriched.values.count { it.tier == Tier.Designed }}")
        b.appendLine("- @sample expanded (enriched): $expanded")
        return b.toString()
    }

    private fun short(sym: String) = sym.removePrefix("test0.")

    private fun cap(c: String?): String =
        if (c == null) "—" else "\"" + c.take(44).replace("|", "\\|") + (if (c.length > 44) "…" else "") + "\""

    private fun String?.isBody() = this != null && (contains("\n") || startsWith("{"))

    private fun sampleCell(s: String?): String = when {
        s == null -> "—"
        s.isBody() -> "«body»"
        else -> "`$s`"
    }
}
