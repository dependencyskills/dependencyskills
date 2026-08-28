package org.dependencyskills.codex.classifier

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.EntryState

/**
 * One degraded entry, with enough to review the decision without re-running anything.
 *
 * A control nobody can audit is a control nobody can correct. The sentence is here because the
 * decision was made about the sentence, and a reviewer handed only a symbol and a score has to
 * go and find what was actually objected to.
 */
data class Rejection(
    val entryId: String,
    val symbol: String,
    val docFormat: String,
    val register: String?,
    val score: Double,
    val threshold: Double,
    val sentence: String,
) {
    override fun toString(): String =
        "degraded $symbol [$docFormat${register?.let { ", $it" } ?: ""}] " +
            "%.4f > %.4f — \"%s\"".format(score, threshold, sentence.take(160))
}

/**
 * What a classification pass did.
 *
 * [uncalibrated] is the field that stops this being a vacuous pass. A documentation convention
 * with no operating point is skipped, and skipping quietly would make "nothing suspect here"
 * indistinguishable from "nothing was looked at" — the failure this project keeps re-learning.
 */
data class ClassificationReport(
    val examined: Int,
    val degraded: List<Rejection>,
    /** Entries a previous pass degraded that this one, under its threshold, does not. */
    val restored: Int,
    /** Conventions present in the entries and absent from the model's calibration. */
    val uncalibrated: Map<String, Int>,
) {
    val looked: Boolean get() = examined > 0
}

/**
 * Scores every entry a coordinate owns and marks the suspect ones.
 *
 * Nothing is removed and nothing loses its retrieval key. A degraded entry stays in the store,
 * stays in the search index, and still answers with its symbol and signature; what it loses is
 * the right to have a rewrite made for it, and the rewrite is the only thing that ever crosses
 * to an agent.
 *
 * The pass is **idempotent and reversible**: it sets the state each entry's current score
 * implies, so re-running under a lower threshold restores entries a stricter one degraded. A
 * threshold is an operator's setting, and a control that can only ratchet one way turns a
 * setting into a decision nobody can take back.
 *
 * [onRejection] is called for each degraded entry as it happens, so a long pass can report
 * rather than only return.
 */
fun Codex.classifyEntries(
    coordinate: Coordinate,
    classifier: ProseClassifier = ProseClassifier(),
    thresholds: Map<String, Double> = emptyMap(),
    onRejection: (Rejection) -> Unit = {},
): ClassificationReport {
    val calibrated = classifier.calibratedFor()
    val degraded = ArrayList<Rejection>()
    val uncalibrated = HashMap<String, Int>()
    var examined = 0
    var restored = 0

    entriesOf(coordinate).forEach { entry ->
        val threshold = thresholds[entry.docFormat]
            ?: if (entry.docFormat in calibrated) classifier.thresholdFor(entry.docFormat) else null
        if (threshold == null) {
            uncalibrated[entry.docFormat] = (uncalibrated[entry.docFormat] ?: 0) + 1
            return@forEach
        }
        // The raw text is a retrieval key the store does not hand out, so reading it is a
        // separate call with its own name. This is one of the two callers that has a reason to.
        val doc = rawDocumentation(entry.id) ?: return@forEach
        examined++
        val verdict = classifier.classify(doc, entry.docFormat, threshold)
        if (verdict.isSuspect) {
            val worst = verdict.sentences.filter { it.flagged }.maxByOrNull { it.score }!!
            val rejection = Rejection(
                entryId = entry.id, symbol = entry.symbol, docFormat = entry.docFormat,
                register = verdict.register, score = worst.score, threshold = threshold,
                sentence = worst.sentence,
            )
            degraded.add(rejection)
            onRejection(rejection)
            setEntryState(entry.id, EntryState.Degraded)
        } else if (entry.state == EntryState.Degraded) {
            setEntryState(entry.id, EntryState.Whole)
            restored++
        }
    }
    return ClassificationReport(examined, degraded, restored, uncalibrated)
}
