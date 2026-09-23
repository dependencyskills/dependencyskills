package org.dependencyskills.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Property
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.bundling.Zip
import java.io.File

/**
 * Ships a library's own skill inside its sources jar, filed under the library's coordinates.
 *
 * The author writes one file, `src/main/skills/SKILL.md` — `src/commonMain/skills/SKILL.md` in a
 * multiplatform build — and this puts it in every sources jar at `skills/<name>/SKILL.md`, where
 * `<name>` is the library's coordinate made a legal skill name ([skillName]). That path is what the
 * codex looks for, and the codex takes a skill only from the artifact whose coordinate it encodes
 * (RAD-0076).
 *
 * **The author never types the coordinates.** They are read from the build's own publication, so
 * a skill cannot be filed under a name the jar does not carry — which the codex would refuse as
 * republishing, silently from the author's side. A `references/` directory beside the file travels
 * with it, as the Agent Skills specification allows. A `scripts/` directory does not: a library's
 * skill tells an agent how to use the library, and never gives it something to run.
 *
 * **A directory the build does not declare does not ship.** RAD-0075 measured `src/main/skills`
 * reaching no artifact without a line of build configuration. This is that line, supplied.
 *
 * Nothing happens in a project with no skill, which is every consuming project that applies this
 * plugin for the other half of what it does.
 */
internal object SkillPackaging {

    /** Where an author puts the skill, per source set, relative to the project directory. */
    private const val JVM_SKILL = "src/main/skills"
    private const val MULTIPLATFORM_SKILL = "src/commonMain/skills"

    fun apply(project: Project) = with(project) {
        // After evaluation, because the coordinates come from the publication, and a build script
        // sets artifactId and groupId in its own body - after this plugin was applied.
        afterEvaluate {
            // Which layout is decided by which file exists: the file is what gets packaged, so it is
            // the fact that matters, and it needs no knowledge of which Kotlin plugin is applied.
            val multiplatformSkill = layout.projectDirectory.dir(MULTIPLATFORM_SKILL).asFile
            val multiplatform = File(multiplatformSkill, "SKILL.md").isFile
            val skillDir = if (multiplatform) multiplatformSkill else layout.projectDirectory.dir(JVM_SKILL).asFile
            val skillFile = File(skillDir, "SKILL.md")
            if (!skillFile.isFile) return@afterEvaluate

            val (group, artifact) = coordinates(project)
            if (group.isBlank()) {
                logger.warn(
                    "dependencyskills: ${skillFile.relativeTo(projectDir)} is not packaged, because this " +
                        "project has no group, so the skill has no coordinates to be filed under",
                )
                return@afterEvaluate
            }
            // A multiplatform sources jar prefixes every entry with its source set; following that
            // keeps the skill beside the code it describes, and the codex accepts both shapes.
            val name = skillName(group, artifact)
            val destination = (if (multiplatform) "commonMain/" else "") + "skills/$name"

            val check = tasks.register("checkDependencySkill", CheckDependencySkill::class.java) {
                description = "Checks the library's skill before it is packaged into the sources jar."
                skill.set(skillFile)
                expectedName.set(name)
                expectedVersion.set(version.toString())
                scripts.set(File(skillDir, "scripts").exists())
            }

            // Every sources jar: a JVM library's `sourcesJar`, and a multiplatform library's root and
            // per-target ones, which are the jars a consumer's build resolves.
            //
            // Matched as `Zip`, not `Jar`, and that is load-bearing. The java plugin's sources jar is
            // `org.gradle.api.tasks.bundling.Jar`; Kotlin Multiplatform's are `org.gradle.jvm.tasks.Jar`,
            // which in Gradle 9 extends `Zip` directly and is not a `bundling.Jar` at all. Matching on
            // `Jar` packaged the skill for plain JVM libraries and silently skipped every multiplatform
            // one. Both are a `Zip`.
            tasks.withType(Zip::class.java)
                .matching { it.name == "sourcesJar" || it.name.endsWith("SourcesJar") }
                .configureEach {
                    dependsOn(check)
                    from(skillDir) {
                        include("SKILL.md", "references/**")
                        into(destination)
                    }
                }
        }
    }

    /**
     * The skill's `name`, and its directory: the library's coordinate, made a legal skill name.
     *
     * The name is the coordinate so that it is unique per library — an artifactId alone is not, since
     * two groups can publish a `core` — and so that it needs no author to type it. The Agent Skills
     * specification allows only lowercase letters, digits and single hyphens, at most 64 characters,
     * so the coordinate cannot be used verbatim. Three steps:
     *
     * 1. `group:artifact`, lowercased, every run of anything else one hyphen:
     *    `com.example.acme:acme-text` is `com-example-acme-acme-text`. About 98% of libraries stop here.
     * 2. Too long: each group segment shrinks to its first and last letter and the artifact stays
     *    whole — `cm-ge-ad-as-cn-tg-ay-fk-accessibility-test-framework`. First and last rather than
     *    initials, because initials merged sibling groups (`android.arch`/`androidx.arch`).
     * 3. Still too long: cut to 55 characters, ending in eight hex digits of a SHA-256 of
     *    `group:artifact`.
     *
     * One-way, and nothing decodes it: the codex knows each jar's real coordinate, encodes it and
     * compares. Must stay identical to `skill_name` in the lightweight codex; both are tested against
     * the same vectors. Chosen by measurement in RAD-0075.
     */
    fun skillName(group: String, artifact: String): String {
        val coordinate = "$group:$artifact"
        val full = legal(coordinate)
        if (full.length <= MAX_NAME) return full
        val compact = group.lowercase().split(NOT_LEGAL).filter { it.isNotEmpty() }
            .joinToString("-") { if (it.length < 2) it else "${it.first()}${it.last()}" }
        val shortened = "$compact-${legal(artifact)}".trim('-')
        if (shortened.length <= MAX_NAME) return shortened
        val hash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(coordinate.toByteArray()).joinToString("") { "%02x".format(it) }.take(8)
        return shortened.take(MAX_NAME - 9).trimEnd('-') + "-" + hash
    }

    private val NOT_LEGAL = Regex("[^a-z0-9]+")

    private fun legal(text: String) = text.lowercase().replace(NOT_LEGAL, "-").trim('-')

    /** The specification's limit on a skill's name. */
    private const val MAX_NAME = 64

    /**
     * The library's group and artifact, as its publication will name them.
     *
     * A multiplatform build's root publication carries the library's own name; its per-target
     * publications add a suffix the codex strips again. A plain build takes the publication it has.
     * With no publication at all, the project's own group and name — which is what a publication
     * would have defaulted to.
     */
    private fun coordinates(project: Project): Pair<String, String> {
        val publications = project.extensions.findByType(PublishingExtension::class.java)
            ?.publications?.withType(MavenPublication::class.java)
        val publication = publications?.findByName("kotlinMultiplatform")
            ?: publications?.findByName("maven")
            ?: publications?.firstOrNull()
        return (publication?.groupId ?: project.group.toString()) to
            (publication?.artifactId ?: project.name)
    }
}

/**
 * Warns about a skill that would ship but not work.
 *
 * Warnings rather than failures, for the alpha: the checks are what a library author most likely
 * gets wrong, and a publish failing on a new file's frontmatter is a worse first experience than a
 * line in the build output.
 */
abstract class CheckDependencySkill : DefaultTask() {

    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val skill: RegularFileProperty

    /** The library's coordinate made a legal skill name, which `name` must match. */
    @get:Input
    abstract val expectedName: Property<String>

    /** The version being built, which `metadata.version` should state (`spec/content.md`). */
    @get:Input
    abstract val expectedVersion: Property<String>

    @get:Input
    @get:Optional
    abstract val scripts: Property<Boolean>

    @TaskAction
    fun check() {
        val text = skill.get().asFile.readText()
        val frontmatter = FRONTMATTER.find(text)?.groupValues?.get(1)
        val expected = expectedName.get()
        if (frontmatter == null) {
            logger.warn(
                "dependencyskills: SKILL.md has no frontmatter. An agent decides whether to read a skill " +
                    "from its `name` and `description`, so without them it will rarely be read.",
            )
        } else {
            val name = NAME.find(frontmatter)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
            val description = DESCRIPTION.find(frontmatter)?.groupValues?.get(1)?.trim()
            if (name != expected) {
                logger.warn(
                    "dependencyskills: SKILL.md `name` is ${name?.let { "'$it'" } ?: "missing"}; it should be " +
                        "'$expected', the library's coordinate as a skill name",
                )
            }
            if (description.isNullOrBlank()) {
                logger.warn("dependencyskills: SKILL.md has no `description`, which is what tells an agent when to read it")
            }
            // Version-matched guidance is the point of shipping a skill with the artifact, so the
            // version it claims to describe should be the one being built.
            val version = VERSION.find(frontmatter)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
            val building = expectedVersion.get()
            when {
                version == null -> logger.warn(
                    "dependencyskills: SKILL.md has no `metadata.version`; state the library version it " +
                        "describes ('$building'), so an agent can tell it is current",
                )
                version != building -> logger.warn(
                    "dependencyskills: SKILL.md says it describes version '$version', but '$building' is " +
                        "being built. Check the skill still holds, then update `metadata.version`.",
                )
            }
        }
        if (scripts.getOrElse(false)) {
            logger.warn(
                "dependencyskills: the skill's scripts/ directory is not packaged. A library's skill tells an " +
                    "agent how to use the library; it never gives the agent something to run.",
            )
        }
    }

    private companion object {
        val FRONTMATTER = Regex("""\A---\r?\n(.*?)\r?\n---""", RegexOption.DOT_MATCHES_ALL)
        val NAME = Regex("""(?m)^name:\s*(.+)$""")
        val DESCRIPTION = Regex("""(?m)^description:\s*(.+)$""")
        /** `version:` indented under `metadata:`, which is where the specification puts it. */
        val VERSION = Regex("""(?m)^metadata:\s*\r?\n(?:[ \t]+.*\r?\n)*?[ \t]+version:\s*(.+)$""")
    }
}
