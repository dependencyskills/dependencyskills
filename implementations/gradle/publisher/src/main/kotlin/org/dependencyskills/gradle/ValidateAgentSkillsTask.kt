package org.dependencyskills.gradle

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.*

/**
 * Checks authored skills before they can be packed.
 *
 * This runs before publish rather than after, because a published artifact is
 * permanent - a bad skill name on Central cannot be withdrawn.
 */
@CacheableTask
abstract class ValidateAgentSkillsTask : DefaultTask() {

    @get:InputDirectory
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val skillsDir: DirectoryProperty

    @get:Input
    abstract val strict: Property<Boolean>

    /** Marker so the task is cacheable and up-to-date checks work. */
    @get:OutputFile
    abstract val reportFile: org.gradle.api.file.RegularFileProperty

    @TaskAction
    fun run() {
        val problems = SkillValidation.validate(skillsDir.get().asFile)
        val report = SkillValidation.report(problems)

        reportFile.get().asFile.apply {
            parentFile.mkdirs()
            writeText(if (report.isEmpty()) "ok\n" else report + "\n")
        }

        if (problems.isEmpty()) {
            logger.lifecycle("Agent skills: ${skillCount()} valid.")
            return
        }

        val fatal = problems.count { it.fatal }
        if (fatal > 0 && strict.get()) throw GradleException(report)
        logger.warn(report)
    }

    private fun skillCount(): Int =
        skillsDir.get().asFile.listFiles()?.count { it.isDirectory } ?: 0
}
