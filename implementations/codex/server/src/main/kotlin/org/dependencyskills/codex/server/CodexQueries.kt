package org.dependencyskills.codex.server

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Entry
import org.dependencyskills.codex.core.EntryState
import org.dependencyskills.codex.index.VectorSearch

/**
 * What the two tools do, with no MCP anywhere in it.
 *
 * Separated from the transport on purpose. This is the code that decides **what crosses the trust
 * boundary**, and that decision should be testable without a protocol, a process or a client — and
 * reusable if the server is ever reached some way other than stdio.
 *
 * ## What crosses, and what cannot
 *
 * Only the **rewrite** and the **signature**. The raw documentation is a retrieval key: the store
 * searches on it and never hands it out, and nothing here calls [Codex.rawDocumentation] — which is
 * the only route to it. That is structural rather than a rule to remember, because [Entry] does not
 * carry the raw text at all; a caller who wanted to leak it would have to go and fetch it
 * deliberately.
 *
 * A **degraded** entry is one whose prose was refused — by the classifier, because the library's
 * own text was suspect, or by verification, because what the summariser produced was. It keeps its
 * place and its retrieval key and returns its signature. It is findable and it displays nothing,
 * which is the safe state rather than a failure.
 */
class CodexQueries(
    private val codex: Codex,
    private val scope: ProjectScope,
    /**
     * The vector index, when one has been built.
     *
     * Null falls back to lexical search rather than returning nothing. A store that has been
     * harvested but never embedded is an ordinary state — the two-faced index is derived, and
     * something has to build it — and answering lexically is what this could always do.
     */
    private val vectors: VectorSearch? = null,
) {

    /**
     * One candidate as a caller sees it.
     *
     * [capability] is null for a degraded entry. That is not an error to be papered over — the
     * absence is the signal, and a caller that wants prose should notice it is not there.
     */
    data class Candidate(
        val symbol: String,
        val signature: String,
        val capability: String?,
        val libraries: List<String>,
        val degraded: Boolean,
    )

    /**
     * What a search found, and what it could not look at.
     *
     * The second half is not decoration. "Nothing matched your words" and "nobody has indexed your
     * dependencies yet" are different answers, and a caller shown only an empty list reports the
     * second as the first.
     */
    data class Answer(
        val candidates: List<Candidate>,
        val searched: Int,
        val notHarvested: Int,
        val noSource: Int,
        val complete: Boolean,
        val note: String? = null,
    )

    fun search(need: String, limit: Int = DEFAULT_LIMIT): Answer {
        if (need.isBlank()) {
            return empty("a search needs something to search for")
        }
        if (scope.isEmpty) {
            return empty(
                "No dependencies are in scope for this project, so there is nothing to search. " +
                    "The scope is read from ${scope.source}, and a build has to record it before " +
                    "this can answer anything.",
            )
        }
        val bounded = limit.coerceIn(1, MAX_LIMIT)
        val results = codex.search(need, scope.coordinates, bounded)
        // The vector index ranks; the store still says what is in scope, what has not been
        // harvested and what has no source. Those three are properties of the store and are the
        // same answer either way, so only the candidate list changes.
        val ranked = vectors?.let { search -> byVector(search, need, bounded) }
        return Answer(
            candidates = ranked ?: results.hits.map { it.entry.toCandidate() },
            searched = results.searched.size,
            notHarvested = results.notHarvested.size,
            noSource = results.noSource.size,
            complete = results.answerIsComplete,
            note = when {
                results.searched.isEmpty() && results.notHarvested.isNotEmpty() ->
                    "None of this project's ${results.notHarvested.size} dependencies has been " +
                        "indexed yet. This is not an answer about what they contain."
                results.hits.isEmpty() && !results.answerIsComplete ->
                    "Nothing matched, and ${results.notHarvested.size} of this project's " +
                        "dependencies have not been indexed — so this is not evidence of absence."
                else -> null
            },
        )
    }

    /**
     * One entry by its exact symbol, within scope.
     *
     * Scoped like everything else: a symbol this project does not depend on is **not found**, not
     * hidden. The distinction matters — reporting "exists but you cannot see it" would leak the
     * existence of entries from other projects on this machine, which is the boundary this is here
     * to hold.
     */
    fun get(symbol: String): Candidate? {
        if (symbol.isBlank() || scope.isEmpty) return null
        return scope.coordinates.asSequence()
            .flatMap { codex.entriesOf(it).asSequence() }
            .firstOrNull { it.symbol == symbol }
            ?.toCandidate()
    }

    /**
     * Ranked by the index, resolved back to entries through the store.
     *
     * Returns null rather than an empty list when the index cannot answer, so a caller falls back
     * to lexical instead of reporting an absence the index never established. An index that is
     * present but empty is indistinguishable from one that is broken, and neither is evidence
     * that a project has nothing.
     */
    private fun byVector(search: VectorSearch, need: String, limit: Int): List<Candidate>? {
        val ids = runCatching { search.search(need, scope.coordinates, limit) }.getOrNull()
        if (ids.isNullOrEmpty()) return null
        return ids.mapNotNull { codex.entry(it) }
            // Scope again, on the way out. The index filters inside the kNN query and this is the
            // belt to that brace: two independent checks on the boundary that matters most.
            .filter { entry -> entry.coordinates.any { it in scope.coordinates } }
            .map { it.toCandidate() }
            .ifEmpty { null }
    }

    private fun empty(note: String) = Answer(emptyList(), 0, 0, 0, complete = false, note = note)

    private fun Entry.toCandidate() = Candidate(
        symbol = symbol,
        signature = signature,
        // Not `rewrite ?: doc`. There is no fallback to the original text, by construction: the
        // absence of prose is what a degraded entry is.
        capability = rewrite,
        libraries = coordinates.map { it.toString() }.sorted(),
        degraded = state == EntryState.Degraded,
    )

    private companion object {
        const val DEFAULT_LIMIT = 10

        /** A ceiling, so a caller cannot ask for the whole store one request at a time. */
        const val MAX_LIMIT = 50
    }
}
