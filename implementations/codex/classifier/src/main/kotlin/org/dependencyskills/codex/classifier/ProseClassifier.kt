package org.dependencyskills.codex.classifier

/** What the classifier decided about one comment. */
enum class Decision {
    /** Nothing in it scored over the threshold for its documentation convention. */
    Clean,

    /**
     * At least one sentence did. The entry keeps its place and its retrieval key; what it loses
     * is the right to have a rewrite produced or displayed for it.
     */
    Suspect,
}

/**
 * One sentence and what the model made of it.
 *
 * [score] is a margin, not a probability, and it is only meaningful next to the threshold it was
 * compared against — which is why the threshold travels with the verdict rather than being looked
 * up again by whoever reads it.
 */
data class SentenceVerdict(
    val sentence: String,
    val score: Double,
    val flagged: Boolean,
)

/**
 * The classifier's answer about one doc comment.
 *
 * [register] is the shape of instruction the attribution model recognised — `precondition`,
 * `deprecation`, `policy` and so on. It is **advisory and frequently absent**: attribution is a
 * second model and a weaker one, and two registers are close to invisible to it. A null register
 * on a suspect comment means the decision stands and the label does not; it never means clean.
 */
data class ProseVerdict(
    val decision: Decision,
    val docFormat: String,
    val threshold: Double,
    val sentences: List<SentenceVerdict>,
    val register: String? = null,
) {
    val isSuspect: Boolean get() = decision == Decision.Suspect

    /** The sentence that decided it — the highest-scoring one, flagged or not. */
    val worst: SentenceVerdict? get() = sentences.maxByOrNull { it.score }

    /**
     * A line a reviewer can act on: which sentence, how far over, and under what threshold.
     * Rejecting something without saying what was rejected is how a filter becomes unreviewable.
     */
    fun explain(): String = worst?.let {
        "${decision.name.lowercase()} [$docFormat, threshold %.4f]%s: %.4f — \"%s\""
            .format(threshold, register?.let { r -> " $r" } ?: "", it.score, it.sentence.take(160))
    } ?: "${decision.name.lowercase()} [$docFormat]: nothing long enough to score"
}

/**
 * Scores harvested documentation for an instruction hiding inside it.
 *
 * **This is not a gate, and the distinction is the whole design.** RAD-0021 rejected admission
 * control — refusing content at harvest — and called silent discard a correctness hazard; what it
 * argued for instead was down-weighting. So nothing here removes anything. A suspect comment
 * keeps its entry, keeps its retrieval key, and stays findable; the classifier decides only
 * whether a rewrite may be produced for it, and the rewrite is the only thing an agent ever sees.
 *
 * **It is also not a fortress, and must never be written up as one.** It catches casual and
 * accidental injection. Rewording the same instruction costs it about ten points, and whether
 * text it misses would have been obeyed is unmeasured and stays open.
 *
 * Loading is deferred until first use: the model is a few megabytes, and a harvest that never
 * classifies anything should not pay for it.
 */
class ProseClassifier internal constructor(
    private val model: Lazy<ProseModel>,
    private val registers: Lazy<RegisterModel?>,
) {

    constructor() : this(lazy { ProseModel.shipped() }, lazy { RegisterModel.shipped() })

    /**
     * Classifies one comment.
     *
     * [docFormat] selects the operating point. The model generalises across documentation
     * conventions — catch was 100% in all nine train/test pairs — but the false-positive rate
     * varies seventeen-fold by direction, so the threshold is per-convention state and a
     * convention nobody calibrated is a convention this cannot answer for.
     *
     * [threshold] overrides the shipped default, because where the operating point sits is the
     * operator's call and not this project's.
     */
    fun classify(doc: String, docFormat: String, threshold: Double? = null): ProseVerdict {
        val cut = threshold ?: thresholdFor(docFormat)
        val verdicts = Sentences.of(doc).map {
            val score = model.value.score(it)
            SentenceVerdict(it, score, score > cut)
        }
        val suspect = verdicts.any { it.flagged }
        return ProseVerdict(
            decision = if (suspect) Decision.Suspect else Decision.Clean,
            docFormat = docFormat,
            threshold = cut,
            sentences = verdicts,
            register = if (suspect) registerOf(verdicts) else null,
        )
    }

    /**
     * The shipped operating point for a convention.
     *
     * An unknown convention is refused rather than given a neighbour's number. A threshold that
     * silently borrows from another convention is exactly the 1.4% mistake test19 measured, and
     * it would present as the classifier being noisy rather than as being misconfigured.
     */
    fun thresholdFor(docFormat: String): Double =
        model.value.thresholds[docFormat]?.toDouble()
            ?: throw NoCalibrationException(docFormat, model.value.thresholds.keys)

    /** The conventions this build has an operating point for. */
    fun calibratedFor(): Set<String> = model.value.thresholds.keys

    private fun registerOf(verdicts: List<SentenceVerdict>): String? {
        val worst = verdicts.filter { it.flagged }.maxByOrNull { it.score } ?: return null
        return registers.value?.registerOf(worst.sentence)
    }
}

/**
 * Asking for a verdict under a documentation convention nobody calibrated a threshold for.
 *
 * Loud rather than approximate. The alternative is to reuse another convention's number, which
 * measured a seventeen-fold difference in false positives depending on the direction.
 */
class NoCalibrationException(docFormat: String, known: Set<String>) : RuntimeException(
    "no operating point for '$docFormat'; this model is calibrated for ${known.sorted()}. " +
        "Pass a threshold explicitly, or fit one against a sample of that convention."
)
