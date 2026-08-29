package org.dependencyskills.codex.server

/**
 * Turning an answer into the text a model reads.
 *
 * Plain prose rather than JSON, deliberately. The consumer is a language model, and the thing it
 * is best at reading is the thing people read. A schema would buy parseability nobody needs and
 * cost tokens on punctuation.
 *
 * Nothing here reaches for a field that is not already in a [CodexQueries.Candidate]. That type is
 * the trust boundary made concrete, and rendering is not the place to widen it.
 */
object Rendering {

    fun render(answer: CodexQueries.Answer): String = buildString {
        if (answer.candidates.isEmpty()) {
            appendLine(answer.note ?: "Nothing in this project's dependencies matches that.")
            if (answer.note == null && answer.complete) {
                appendLine()
                appendLine(
                    "All ${answer.searched} of this project's indexed dependencies were searched, " +
                        "so this is a real absence rather than a gap in what has been read.",
                )
            }
            return@buildString
        }

        appendLine(
            "${answer.candidates.size} ${plural(answer.candidates.size, "capability", "capabilities")} " +
                "from ${answer.searched} indexed ${plural(answer.searched, "dependency", "dependencies")}.",
        )
        answer.candidates.forEach { candidate ->
            appendLine()
            appendLine(candidate.symbol)
            appendLine("  ${candidate.signature}")
            appendLine(
                "  " + (candidate.capability
                    // Said out loud. A caller that sees a blank line assumes the tool is broken;
                    // one that reads this knows the entry is real and its prose was withheld.
                    ?: "(no description — this entry's prose was withheld, and the signature is what it offers)"),
            )
            appendLine("  ${candidate.libraries.joinToString(", ")}")
        }

        answer.note?.let {
            appendLine()
            appendLine(it)
        }
        if (!answer.complete && answer.note == null) {
            appendLine()
            appendLine(
                "${answer.notHarvested} of this project's dependencies have not been indexed yet, " +
                    "so there may be more than this.",
            )
        }
    }.trim()

    fun render(candidate: CodexQueries.Candidate?): String = when (candidate) {
        // Not "no such symbol" - a symbol out of scope and a symbol that does not exist are the
        // same answer here, on purpose. Distinguishing them would report the existence of entries
        // belonging to other projects on this machine.
        null -> "No such capability in this project's dependencies."
        else -> buildString {
            appendLine(candidate.symbol)
            appendLine("  ${candidate.signature}")
            appendLine(
                "  " + (candidate.capability
                    ?: "(no description — this entry's prose was withheld, and the signature is what it offers)"),
            )
            appendLine("  ${candidate.libraries.joinToString(", ")}")
        }.trim()
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
}
