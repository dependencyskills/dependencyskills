package org.dependencyskills.gradle

import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property

/**
 * Configuration for validating this project's authored agent skills.
 *
 * The skills are authored as plain directories, each an immediate child of
 * [skillsDir] holding a `SKILL.md`.
 */
abstract class AgentSkillsExtension {

    /**
     * Master switch. Off means the plugin does nothing at all, and its task
     * skips rather than fails, so a build that invokes it explicitly still
     * succeeds.
     *
     * Defaults to the `agentSkills.enabled` Gradle property when set, so CI and
     * downstream builds can turn it off without editing a build script:
     *
     * ```
     * ./gradlew build -PagentSkills.enabled=false
     * ```
     */
    abstract val enabled: Property<Boolean>

    /** Where skills are authored. Each immediate child is one `<name>/SKILL.md`. */
    abstract val skillsDir: DirectoryProperty

    /** Fail the build on validation errors. Off downgrades them to warnings. */
    abstract val strict: Property<Boolean>
}
