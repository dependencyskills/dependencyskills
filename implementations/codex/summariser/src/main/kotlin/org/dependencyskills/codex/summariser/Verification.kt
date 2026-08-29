package org.dependencyskills.codex.summariser

/**
 * Why a candidate sentence was refused, or that it was not.
 *
 * A reason rather than a boolean, because the rate alone is uninformative: RAD-0040 traced 11 of
 * 16 degradations to a single over-broad rule, and that was only visible because each rejection
 * said which rule fired.
 */
sealed interface Verdict {
    data class Accepted(val sentence: String) : Verdict
    data class Refused(val rule: String, val detail: String) : Verdict
}

/**
 * Is a candidate sentence safe to publish as an index entry? Conservative by design.
 *
 * **Verification is not trusted to be right — only to be conservative**, and its failure lands on
 * a state already measured as safe. That is the posture the whole component rests on: assume
 * something gets through, and make the fallback somewhere you were willing to be anyway.
 *
 * Every rule below is a **shape** an instruction needs and a capability description does not.
 * None of them reads meaning, and that is deliberate: a rule that had to understand the sentence
 * would have to be right, which is the property this design spent so much effort not needing.
 *
 * The cost of an over-broad rule is not zero and must not be treated as a free safety margin.
 * Every refusal degrades an entry to signature-only, and RAD-0040 measured a signature-only entry
 * as **unfindable** — it carries no prose and a query is prose. So a rule that rejects an
 * ordinary English sentence is not cautious, it is a silent deletion.
 */
object Verification {

    /** A capability line, not a paragraph. */
    const val MAX_WORDS = 40

    fun verify(candidate: String, signature: String): Verdict {
        val text = normalise(candidate)
        if (text.isEmpty()) return Verdict.Refused("empty", "nothing was produced")

        val words = text.split(WHITESPACE).filter { it.isNotEmpty() }
        if (words.size > MAX_WORDS) {
            return Verdict.Refused("too long", "${words.size} words")
        }
        val sentences = text.split(SENTENCE_END).count { it.isNotBlank() }
        if (sentences > 1) return Verdict.Refused("more than one sentence", "$sentences")

        IMPERATIVE.find(text)?.let { return Verdict.Refused("imperative", it.value) }
        SECOND_PERSON.find(text)?.let { return Verdict.Refused("addresses a reader", it.value) }
        if (SPELLED.containsMatchIn(text)) {
            return Verdict.Refused("spelled-out punctuation", "a dot or slash written as a word")
        }
        CODEISH.find(text)?.let { return Verdict.Refused("contains code or markup", it.value) }
        EXTERNAL_SYMBOL.find(text)?.let { return Verdict.Refused("names a path or host", it.value) }
        // Only outside the signature. A capability whose own signature says `token` may say so
        // too; one that introduces the word from nowhere may not.
        EXTERNAL_WORD.find(text)?.let { match ->
            if (!signature.contains(match.value, ignoreCase = true)) {
                return Verdict.Refused("names something outside the signature", match.value)
            }
        }
        return Verdict.Accepted(text)
    }

    /**
     * Strips the markdown a model put around a name, before anything judges the sentence.
     *
     * **Not the same act as rejecting it.** Rejecting on a backtick cost 8 of 60 entries on
     * gemma-3-270m, 36 of 60 on Qwen2.5-0.5B and 2 of 220 on a 30B model — a spread that tracks
     * each model's prose style rather than its size, which is why measuring the rule only on the
     * largest model made it look free. In every case the sentence inside the backticks was the
     * sentence outside them.
     *
     * Normalising rather than rejecting also keeps verification honest about what it approved:
     * the normalised sentence is what gets published, so nothing reaches the index that
     * verification did not see.
     *
     * A backtick barely appears in the documentation being summarised either. It is Kotlin's
     * escaped-identifier syntax, used essentially only in test names, not on a public API surface.
     */
    fun normalise(candidate: String): String =
        BACKTICKED.replace(candidate, "$1").replace("`", "").trim()

    private val WHITESPACE = Regex("\\s+")
    private val SENTENCE_END = Regex("(?<=[.!?])\\s+")

    /** Bounded and single-line, so an unterminated backtick cannot swallow a sentence. */
    private val BACKTICKED = Regex("`([^`\\n]{1,80})`")

    private val IMPERATIVE = Regex(
        "\\b(must|should|shall|need to|have to|required|ensure|make sure|" +
            "remember to|be sure|always|never|do not|don't)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val SECOND_PERSON = Regex("\\b(you|your|yours|we|our|us)\\b", RegexOption.IGNORE_CASE)

    /**
     * Two patterns, because one cannot do both.
     *
     * A leading `\b` requires a word character before the match, so `\b~/` and `\b/etc/` can never
     * fire after a space — which meant every path pattern in the first version was dead. The
     * self-test caught it and nothing else would have, which is the entire argument for the
     * self-test existing.
     */
    private val EXTERNAL_WORD = Regex(
        "\\b(environment|env|credential|credentials|secret|secrets|token|" +
            "password|localhost|hostname)\\b",
        RegexOption.IGNORE_CASE,
    )

    private val EXTERNAL_SYMBOL = Regex(
        "\\.env\\b|~/|/etc/|/tmp/|/var/|/usr/|\\.ssh|id_rsa|https?://|127\\.0\\.0\\.1|\\bfile://",
        RegexOption.IGNORE_CASE,
    )

    private val SPELLED = Regex("\\b\\w+\\s+(dot|slash)\\s+\\w+\\b", RegexOption.IGNORE_CASE)

    /**
     * Code and markup, matching a **declaration** rather than a word.
     *
     * Narrowed twice, and both narrowings were paid for. The first version matched the bare words
     * `fun` and `class`, which rejects "Returns the class of the serializer" — an ordinary English
     * sentence, and 11 of 16 of RAD-0040's degradations. The second removed a bare backtick from
     * the character class; see [normalise].
     */
    private val CODEISH = Regex("[{}<>|]|=>|;|\\bfun\\s+\\w+\\s*\\(|\\bclass\\s+[A-Z]\\w*")
}
