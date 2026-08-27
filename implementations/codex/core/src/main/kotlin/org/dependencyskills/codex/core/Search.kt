package org.dependencyskills.codex.core

/**
 * One candidate, and how well it matched.
 *
 * [score] is higher-is-better. SQLite's `bm25` returns a negative number where more negative
 * is a better match, which is a fine convention for an ORDER BY and a terrible one for anyone
 * reading a result, so it is negated here once rather than misread repeatedly.
 */
data class Hit(val entry: Entry, val score: Double)

/**
 * What a search found, and what it could not look at.
 *
 * The three states are separate fields rather than an empty list, because collapsing them is
 * indistinguishable from the tool being broken. "Nothing matched your words" and "nobody has
 * indexed your dependencies yet" are different answers to the same query, and a caller that
 * sees only an empty list will report the second as the first.
 */
data class SearchResults(
    val hits: List<Hit>,
    /** In scope, harvested, and searched. These are the coordinates the answer came from. */
    val searched: Set<Coordinate>,
    /**
     * In scope, but with nothing to search yet — never seen, queued, or last attempt failed.
     * A non-empty set here means an empty [hits] is not evidence of absence.
     */
    val notHarvested: Set<Coordinate>,
    /** In scope, harvested, and there was no source to index. Re-asking will not help. */
    val noSource: Set<Coordinate>,
) {
    /** Whether an empty result can be read as "there is nothing like that in your dependencies". */
    val answerIsComplete: Boolean get() = notHarvested.isEmpty()
}

/**
 * The words of a symbol, so an identifier can be reached by a query written in prose.
 *
 * `io.ktor.server.response.respondOutputStream` becomes
 * `io ktor server response respondOutputStream respond Output Stream`. The original is kept
 * alongside the split, so someone searching for the exact identifier still finds it.
 *
 * Internal rather than private: the migration needs it to backfill an existing store, and a
 * test needs it to state what the rule is.
 */
internal fun symbolText(symbol: String): String {
    val words = LinkedHashSet<String>()
    symbol.split('.', '$').filter { it.isNotBlank() }.forEach { part ->
        words.add(part)
        CAMEL.split(part).filter { it.length > 1 }.forEach { words.add(it) }
    }
    return words.joinToString(" ")
}

/** Splits on a lower-to-upper boundary and on an acronym running into a word. */
private val CAMEL = Regex("(?<=[a-z0-9])(?=[A-Z])|(?<=[A-Z])(?=[A-Z][a-z])|[_\\-]+")

/**
 * Turns a need written in plain words into an FTS5 query.
 *
 * Every term is double-quoted, which in FTS5 makes it a literal string: a need containing
 * `NOT`, `*` or an unbalanced quote is a search for those characters rather than a syntax
 * error or an operator the caller did not intend.
 *
 * The terms are joined with OR and left to `bm25` to rank. AND would be the obvious choice and
 * the wrong one — a need is a sentence, and requiring every word of a sentence to appear in one
 * doc comment returns nothing for almost every question worth asking.
 */
internal fun ftsQuery(need: String): String? {
    val terms = need.lowercase()
        .split(Regex("[^\\p{L}\\p{N}]+"))
        .filter { it.length > 1 }
        .distinct()
    return if (terms.isEmpty()) null else terms.joinToString(" OR ") { "\"$it\"" }
}
