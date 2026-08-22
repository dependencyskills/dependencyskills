package org.dependencyskills.test0

import java.nio.file.Path

/**
 * One arm of the bake-off: turns source into codex entries.
 *
 * Implementations:
 *  - `KotlinPsiParser`   — raw KDoc via kotlin-compiler-embeddable PSI (no native deps).
 *  - `EnrichedPsiParser` — enriched: inherited docs + @sample expansion, resolved within
 *                          the source set (stands in for Dokka; real-Dokka fidelity later).
 *  - `TreeSitterParser`  — cross-language, added for test1 (jtreesitter + grammars).
 *
 * The gap between what each returns for the same source *is* the finding.
 */
interface Parser {
    /** Display name, used to label assertions and results. */
    val name: String

    /** Parse the given source files into codex entries. */
    fun parse(sources: List<Path>): List<Entry>
}
