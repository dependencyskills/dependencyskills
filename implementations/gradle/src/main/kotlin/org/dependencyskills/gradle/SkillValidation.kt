package org.dependencyskills.gradle

import java.io.File

/** One problem found in an authored skill. */
data class SkillProblem(val path: String, val message: String, val fatal: Boolean)

/**
 * Validation rules for authored skills.
 *
 * Two families. **Spec rules** come from the Agent Skills specification -
 * a skill is a directory holding SKILL.md with `name` and `description`
 * frontmatter. **Filesystem rules** exist because a skill name becomes a
 * directory name on a consumer's machine, and a name that is legal here can
 * be unusable there. Getting these wrong is expensive: published artifacts
 * are permanent.
 */
object SkillValidation {

    /** Reserved on Windows even with an extension - `NUL.txt` IS `NUL`. */
    private val WINDOWS_RESERVED = setOf(
        "CON", "PRN", "AUX", "NUL",
        "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
        "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9",
    )

    private val FRONTMATTER = Regex("""^---\r?\n(.*?)\r?\n---""", RegexOption.DOT_MATCHES_ALL)

    /** The Agent Skills spec caps `description`. Over this it is not a valid skill. */
    const val MAX_DESCRIPTION = 1024

    /** Under this a description cannot discriminate between overlapping libraries. */
    const val MIN_DESCRIPTION = 40

    /**
     * Body budget, in estimated tokens. A skill body loads in full once it
     * triggers, so an overlong one spends a consumer's context on prose they
     * did not ask for - and a consumer may load several. Guidance rather than
     * spec, so this warns.
     */
    const val MAX_BODY_TOKENS = 5_000

    /**
     * Windows caps a path at 260 characters unless long-path support is turned
     * on. A consumer's own project path is unknown, so budget for it: assume
     * roughly 120 characters before our tree begins.
     */
    const val PATH_BUDGET = 140

    /** Crude but adequate: English prose runs about four characters per token. */
    private fun estimateTokens(text: String) = (text.length + 3) / 4

    private fun field(block: String, key: String): String? =
        Regex("""^$key:[ \t]*"?(.*?)"?[ \t]*$""", RegexOption.MULTILINE)
            .find(block)?.groupValues?.get(1)?.trim()?.takeIf { it.isNotEmpty() }

    fun validate(skillsDir: File): List<SkillProblem> {
        val problems = mutableListOf<SkillProblem>()

        if (!skillsDir.isDirectory) {
            return listOf(SkillProblem(skillsDir.path, "no such directory", fatal = true))
        }

        val dirs = skillsDir.listFiles()?.filter { it.isDirectory }?.sortedBy { it.name } ?: emptyList()
        if (dirs.isEmpty()) {
            return listOf(SkillProblem(skillsDir.path, "contains no skill directories", fatal = true))
        }

        // Case-insensitive collision: macOS and Windows cannot hold both.
        dirs.groupBy { it.name.lowercase() }
            .filterValues { it.size > 1 }
            .forEach { (_, clashing) ->
                problems += SkillProblem(
                    clashing.joinToString(", ") { it.name },
                    "names differ only by case - they collide on macOS and Windows",
                    fatal = true,
                )
            }

        dirs.forEach { dir -> problems += validateOne(dir) }
        return problems
    }

    private fun validateOne(dir: File): List<SkillProblem> {
        val problems = mutableListOf<SkillProblem>()
        val name = dir.name
        fun fail(msg: String) = problems.add(SkillProblem(name, msg, true))
        fun warn(msg: String) = problems.add(SkillProblem(name, msg, false))

        // --- filesystem rules: this name becomes a directory on someone else's disk
        if (name.endsWith(".") || name.endsWith(" ")) {
            fail("ends with a period or space - Windows silently strips these")
        }
        if (name.substringBefore('.').uppercase() in WINDOWS_RESERVED) {
            fail("'${name.substringBefore('.')}' is a reserved device name on Windows, even with an extension")
        }
        if (name.startsWith("_")) {
            fail("starts with an underscore - Android's default packaging excludes **/_*")
        }
        if (name.any { it in "/\\:*?\"<>|" }) {
            fail("contains a character that is illegal in a path")
        }
        if (name.length > 64) {
            warn("name is ${name.length} characters - long names eat the Windows path budget, " +
                "and every consumer pays for it in their own directory tree")
        }

        // --- spec rules
        val skillFile = File(dir, "SKILL.md")
        if (!skillFile.isFile) {
            fail("has no SKILL.md - a skill is a directory containing one")
            return problems
        }

        val text = skillFile.readText()
        val block = FRONTMATTER.find(text)?.groupValues?.get(1)
        if (block == null) {
            fail("SKILL.md has no YAML frontmatter")
            return problems
        }

        val declaredName = field(block, "name")
        when {
            declaredName == null -> fail("SKILL.md frontmatter has no 'name' - the spec requires it")
            declaredName != name -> fail("frontmatter name '$declaredName' does not match its directory '$name'")
        }

        val description = field(block, "description")
        when {
            description == null ->
                fail("SKILL.md frontmatter has no 'description' - it is what an agent matches on")
            description.startsWith("<") ->
                fail("description is still the scaffold placeholder")
            description.length > MAX_DESCRIPTION ->
                fail("description is ${description.length} characters - the spec caps it at $MAX_DESCRIPTION")
            description.length < MIN_DESCRIPTION ->
                warn("description is only ${description.length} characters - too thin to be discriminative " +
                    "when several libraries overlap")
        }

        // --- length rules: a skill costs a consumer context, and a path on their disk
        val body = text.substring(FRONTMATTER.find(text)?.range?.last?.plus(1) ?: 0)
        val tokens = estimateTokens(body)
        if (tokens > MAX_BODY_TOKENS) {
            warn("SKILL.md body is roughly $tokens tokens - over the $MAX_BODY_TOKENS budget. " +
                "The body loads in full once the skill triggers; move detail into references/ " +
                "so a consumer only pays for what they read")
        }

        val longest = dir.walkTopDown()
            .filter { it.isFile }
            .map { it.relativeTo(dir.parentFile).invariantSeparatorsPath }
            .maxByOrNull { it.length }
        if (longest != null && longest.length > PATH_BUDGET) {
            warn("'$longest' is ${longest.length} characters - deep enough to risk the 260-character " +
                "path limit on Windows once a consumer's own project path is added")
        }

        // A namespaced name matters because the consumer's namespace is flat -
        // every dependency's skills merge into one directory.
        if (!name.contains('.') && !name.contains('-')) {
            warn("'$name' is unprefixed - skill names share a flat namespace across every " +
                "dependency a consumer has, so prefix it with your coordinates")
        }
        return problems
    }

    /** Human-readable report, errors first. */
    fun report(problems: List<SkillProblem>): String {
        val (fatal, warnings) = problems.partition { it.fatal }
        return buildString {
            if (fatal.isNotEmpty()) {
                appendLine("Agent skill validation failed:")
                fatal.forEach { appendLine("  ERROR  ${it.path}: ${it.message}") }
            }
            if (warnings.isNotEmpty()) {
                if (fatal.isEmpty()) appendLine("Agent skill validation passed with warnings:")
                warnings.forEach { appendLine("  WARN   ${it.path}: ${it.message}") }
            }
        }.trimEnd()
    }
}
