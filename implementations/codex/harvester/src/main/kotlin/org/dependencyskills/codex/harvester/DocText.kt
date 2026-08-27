package org.dependencyskills.codex.harvester

/**
 * Reduces a doc comment to the prose inside it, or null when there is not enough left to be
 * worth a retrieval key.
 *
 * This is the raw documentation the store holds: a key it searches on and never hands out. What
 * a reader is shown is the rewrite, which a later pass produces. So the job here is not to
 * present the comment well — it is to leave the words a query would match on and drop the
 * markup a query never contains.
 *
 * Kept deliberately identical to the extraction the corpus experiment measured retrieval
 * against, over 1,798 libraries: changing it would invalidate every number this project has
 * about how well retrieval works, and none of those numbers would announce that they had gone
 * stale.
 */
internal fun cleanDoc(raw: String): String? {
    val inner = raw.removePrefix("/**").removeSuffix("*/")
    val prose = FENCE.replace(LEADING_STAR.replace(inner, ""), " ")
    val kept = prose.lineSequence()
        .map { it.trim() }
        // A line that is only a `@param`/`@return` tag is structure, not description. The tag
        // vocabulary is the same in every library, so it matches every query equally and
        // separates nothing.
        .filter { it.isNotEmpty() && !TAG_ONLY.containsMatchIn(it) }
        .joinToString(" ")
    val text = WHITESPACE.replace(kept, " ").trim()
    return if (text.split(' ').size >= MINIMUM_WORDS) text else null
}

/** Fenced code and inline javadoc tags: markup a natural-language query will never contain. */
private val FENCE = Regex("```.*?```|\\{@\\w+[^}]*\\}", RegexOption.DOT_MATCHES_ALL)
private val LEADING_STAR = Regex("^\\s*\\*", RegexOption.MULTILINE)
private val TAG_ONLY = Regex("^\\s*@\\w+")
private val WHITESPACE = Regex("\\s+")

/**
 * Below this a comment is a label rather than a description — "The name." — and it
 * retrieves no better than the symbol it sits on, which the store already has.
 */
private const val MINIMUM_WORDS = 4
