package org.dependencyskills.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project

/**
 * Validates a library's authored agent skills against the Agent Skills format
 * and the filesystem-naming rules a consumer's machine imposes.
 *
 * This is a skeleton — deliberately a placeholder for a larger tool. The
 * project's research recommends harvesting a library's documentation from the
 * artifacts that already ship (`-sources.jar`, KDoc) rather than publishing a
 * bespoke sidecar, and the shape of the consumer-side tooling is still being
 * worked out. What is stable enough to check today — that an authored skill
 * conforms to the adopted format and will not break on a consumer's disk — is
 * checked here. The rest grows as the spec settles. See
 * `docs/knowledge/research/`.
 */
class AgentSkillsPlugin : Plugin<Project> {

    companion object {
        const val VALIDATE_TASK = "validateAgentSkills"
        const val GROUP = "agent skills"
    }

    override fun apply(project: Project): Unit = with(project) {
        val ext = extensions.create("agentSkills", AgentSkillsExtension::class.java).apply {
            // -PagentSkills.enabled=false switches it off without a build-script edit.
            enabled.convention(
                providers.gradleProperty("agentSkills.enabled")
                    .map(String::toBoolean)
                    .orElse(true),
            )
            skillsDir.convention(layout.projectDirectory.dir("src/agent-skills"))
            strict.convention(true)
        }

        tasks.register(VALIDATE_TASK, ValidateAgentSkillsTask::class.java) {
            group = GROUP
            description = "Checks authored agent skills against the format and filesystem rules."
            skillsDir.set(ext.skillsDir)
            strict.set(ext.strict)
            reportFile.set(layout.buildDirectory.file("reports/agent-skills/validation.txt"))
            onlyIf { ext.enabled.get() }
        }
    }
}
