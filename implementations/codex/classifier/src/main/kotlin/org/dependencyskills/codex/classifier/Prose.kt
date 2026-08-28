package org.dependencyskills.codex.classifier

/**
 * How a doc comment is broken up before it is scored.
 *
 * **Per sentence, not per comment.** A payload is one sentence in a comment averaging several, so
 * at comment level its signal is diluted by everything around it — measured as a third more false
 * positives at the same catch. Scoring sentences also says *which* sentence, which is the
 * difference between a reviewer having something to look at and having a label.
 */
internal object Sentences {

    /** Sentence-final punctuation followed by space. The same rule the corpus was built with. */
    private val BOUNDARY = Regex("(?<=[.!?])\\s+")

    /**
     * A sentence shorter than this is not a place an instruction hides, and scoring it adds
     * noise: the shortest fragments in real documentation are headings and cross-references.
     */
    const val MINIMUM_WORDS = 4

    fun of(text: String): List<String> =
        BOUNDARY.split(text).filter { it.trim().split(WHITESPACE).count { w -> w.isNotEmpty() } >= MINIMUM_WORDS }

    private val WHITESPACE = Regex("\\s+")
}

/**
 * Character 4- and 5-grams over lowercased text.
 *
 * Chosen over word splitting by measurement rather than taste, and the variation that could have
 * invalidated everything confirmed it: deleting all 34 attack terms — `env`, `secret`, `token`,
 * `credential` and the rest — from both classes, training included, leaves catch unchanged and
 * false positives slightly better. Whatever this detects is not the vocabulary the payloads are
 * built from, which is what separates it from a keyword list.
 */
internal object CharNgrams {
    const val LOW = 4
    const val HIGH = 5

    /** Emits into [into] rather than allocating, because a long comment produces thousands. */
    fun of(text: String, into: MutableMap<String, Int>) {
        val s = text.lowercase()
        for (n in LOW..HIGH) {
            for (i in 0..s.length - n) {
                val gram = s.substring(i, i + n)
                into[gram] = (into[gram] ?: 0) + 1
            }
        }
    }
}
