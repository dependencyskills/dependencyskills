package org.dependencyskills.plugin

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
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
import org.gradle.work.DisableCachingByDefault
import java.io.File

/**
 * Ships a library's own skill inside its sources jar, filed under the library's coordinate.
 *
 * The author writes an Agent Skill directory in the source tree, named for the library's coordinate
 * ([skillName]) as the specification requires a skill's directory to be named for the skill:
 *
 * ```
 * src/main/skills/<name>/SKILL.md            a JVM library
 * src/commonMain/skills/<name>/SKILL.md      a Kotlin Multiplatform library
 * ```
 *
 * so it is a valid skill where it is written, and `skills-ref validate` accepts it there. This puts
 * that directory in every sources jar at `skills/<name>/` — `commonMain/skills/<name>/` in a
 * multiplatform jar, which prefixes every entry with its source set. That path is what the codex
 * looks for, and the codex takes a skill only from the artifact whose coordinate the name encodes
 * (RAD-0076).
 *
 * **`references/` and `assets/` travel with it**, the specification's directories for material read
 * on demand and for templates and data. **`scripts/` does not**: a library's skill tells an agent how
 * to use the library, and never gives it something to run.
 *
 * **The coordinate comes from the build, never the author.** `dependencySkillName` prints the name
 * and the path the skill belongs at, so an author — or their agent — never computes the encoding. A
 * skill in a wrongly named directory, or the alpha's earlier flat `skills/SKILL.md`, still ships under
 * the right name, and `checkDependencySkill` says where it should move.
 *
 * **A directory the build does not declare does not ship.** RAD-0075 measured `src/main/skills`
 * reaching no artifact without a line of build configuration. This is that line, supplied.
 *
 * Nothing is packaged in a project with no skill, which is every consuming project that applies this
 * plugin for the other half of what it does.
 */
internal object SkillPackaging {

    /** Where an author's skills root is, per source set, relative to the project directory. */
    private const val JVM_SKILLS = "src/main/skills"
    private const val MULTIPLATFORM_SKILLS = "src/commonMain/skills"

    fun apply(project: Project) {
        // Once every project is evaluated, because the coordinates come from the publication, and it
        // is named late. A build script sets artifactId in its own body, after this plugin was
        // applied; a publishing plugin's `coordinates(...)` - vanniktech's - lands later still, in an
        // afterEvaluate of its own, so reading it in this plugin's afterEvaluate took the Gradle
        // project's name: `datetime`, where the library publishes as `format-datetime`, and every
        // skill named for a coordinate nothing resolves.
        project.gradle.projectsEvaluated { configure(project) }
    }

    /** Registers the name and check tasks, and packages the skill; called once the coordinates are final. */
    private fun configure(project: Project) = with(project) {
        run {
            val (libraryGroup, artifact) = coordinates(project)
            val name = if (libraryGroup.isBlank()) "" else skillName(libraryGroup, artifact)
            // Where a skill is found decides the layout; with none yet, the Kotlin plugin applied
            // decides where one should go.
            val existing = listOf(MULTIPLATFORM_SKILLS, JVM_SKILLS)
                .map { layout.projectDirectory.dir(it).asFile }
                .firstOrNull { it.isDirectory }
            val multiplatform = existing?.path?.endsWith(MULTIPLATFORM_SKILLS.replace('/', File.separatorChar))
                ?: pluginManager.hasPlugin("org.jetbrains.kotlin.multiplatform")
            val root = existing ?: layout.projectDirectory.dir(if (multiplatform) MULTIPLATFORM_SKILLS else JVM_SKILLS).asFile

            tasks.register("dependencySkillName", DependencySkillName::class.java) {
                this.group = "dependency skills"
                description = "Prints the name this library's skill must carry, and the directory it belongs in."
                skillName.set(name)
                skillPath.set(if (name.isBlank()) "" else "${root.relativeTo(projectDir).invariantSeparatorsPath}/$name/SKILL.md")
            }

            // The skill directory: the correctly named one, else the only one, else the flat file the
            // alpha first asked for. More than one candidate is ambiguous, and the check says so.
            val candidates = root.listFiles { f -> f.isDirectory && File(f, "SKILL.md").isFile }.orEmpty().toList()
            val skillDir = when {
                name.isNotBlank() && File(root, "$name/SKILL.md").isFile -> File(root, name)
                candidates.size == 1 -> candidates.single()
                File(root, "SKILL.md").isFile -> root
                else -> null
            }
            if (skillDir == null && candidates.isEmpty()) return@run

            val check = tasks.register("checkDependencySkill", CheckDependencySkill::class.java) {
                description = "Checks the library's skill before it is packaged into the sources jar."
                expectedName.set(name)
                expectedVersion.set(version.toString())
                expectedPath.set("${root.relativeTo(projectDir).invariantSeparatorsPath}/$name/SKILL.md")
                if (skillDir != null) {
                    skill.set(File(skillDir, "SKILL.md"))
                    directoryName.set(if (skillDir == root) "" else skillDir.name)
                    scripts.set(File(skillDir, "scripts").exists())
                }
                ambiguous.set(if (skillDir == null) candidates.map { it.name }.sorted() else emptyList())
            }
            if (skillDir == null || name.isBlank()) return@run

            // Always into the right name, whatever the source directory is called, so a misplaced
            // skill still ships where a consumer looks for it; the check tells the author to move it.
            val destination = (if (multiplatform) "commonMain/" else "") + "skills/$name"

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
                        include("SKILL.md", "references/**", "assets/**")
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
 * Prints the skill's name and the directory it belongs in, so neither an author nor their agent
 * ever computes the coordinate encoding by hand.
 */
@DisableCachingByDefault(because = "It only prints; there is no output to cache")
abstract class DependencySkillName : DefaultTask() {

    @get:Input
    abstract val skillName: Property<String>

    @get:Input
    abstract val skillPath: Property<String>

    @TaskAction
    fun print() {
        if (skillName.get().isBlank()) {
            logger.quiet("This project has no group, so its skill has no coordinate to be named for.")
            return
        }
        logger.quiet("name: ${skillName.get()}")
        logger.quiet("path: ${skillPath.get()}")
    }
}

/**
 * Warns about a skill that would ship but not work, or not be a valid Agent Skill where it is written.
 *
 * Warnings rather than failures, for the alpha: the checks are what a library author most likely
 * gets wrong, and a publish failing on a new file's frontmatter is a worse first experience than a
 * line in the build output. `spec/content.md` says a consumer rejects the naming ones outright, and
 * the lookup refuses a `description` over [MAX_DESCRIPTION] characters.
 */
@DisableCachingByDefault(because = "It only warns; there is no output to cache")
abstract class CheckDependencySkill : DefaultTask() {

    @get:InputFile
    @get:Optional
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val skill: RegularFileProperty

    /** The library's coordinate made a legal skill name, which `name` and the directory must match. */
    @get:Input
    abstract val expectedName: Property<String>

    /** The version being built, which `metadata.version` should state (`spec/content.md`). */
    @get:Input
    abstract val expectedVersion: Property<String>

    /** Where the skill belongs, relative to the project, for a message that says where to move it. */
    @get:Input
    abstract val expectedPath: Property<String>

    /** The directory the skill was found in; empty when it is the flat file directly under `skills/`. */
    @get:Input
    @get:Optional
    abstract val directoryName: Property<String>

    @get:Input
    @get:Optional
    abstract val scripts: Property<Boolean>

    /** More than one candidate directory and none correctly named: nothing is packaged. */
    @get:Input
    abstract val ambiguous: ListProperty<String>

    @TaskAction
    fun check() {
        val expected = expectedName.get()
        val path = expectedPath.get()
        if (ambiguous.get().isNotEmpty()) {
            logger.warn(
                "dependencyskills: no skill packaged: found ${ambiguous.get().joinToString()} under skills/, " +
                    "and none is named '$expected'. A library ships one skill, at $path",
            )
            return
        }
        if (!skill.isPresent) return
        when (val directory = directoryName.getOrElse("")) {
            expected -> Unit
            "" -> logger.warn(
                "dependencyskills: SKILL.md is not in a directory named for the skill, so it is not a valid " +
                    "Agent Skill where it is written. It was packaged under the right name; move it to $path",
            )
            else -> logger.warn(
                "dependencyskills: the skill's directory is '$directory'; the Agent Skills specification requires " +
                    "it to match the skill's name, '$expected'. It was packaged under the right name; move it to $path",
            )
        }
        val text = skill.get().asFile.readText()
        val frontmatter = FRONTMATTER.find(text)?.groupValues?.get(1)
        if (frontmatter == null) {
            logger.warn(
                "dependencyskills: SKILL.md has no frontmatter. An agent decides whether to read a skill " +
                    "from its `name` and `description`, so without them it will rarely be read.",
            )
        } else {
            val name = NAME.find(frontmatter)?.groupValues?.get(1)?.trim()?.trim('"', '\'')
            val description = skillDescription(frontmatter)
            if (name != expected) {
                logger.warn(
                    "dependencyskills: SKILL.md `name` is ${name?.let { "'$it'" } ?: "missing"}; it should be " +
                        "'$expected', the library's coordinate as a skill name",
                )
            }
            if (description.isNullOrBlank()) {
                logger.warn("dependencyskills: SKILL.md has no `description`, which is what tells an agent when to read it")
            } else if (description.length > MAX_DESCRIPTION) {
                logger.warn(
                    "dependencyskills: SKILL.md `description` is ${description.length} characters; the Agent Skills " +
                        "specification allows $MAX_DESCRIPTION, and a consumer's lookup does not serve a skill over " +
                        "it. Shorten it.",
                )
            }
            // The specification allows six top-level fields and the reference validator rejects any
            // other; anything more belongs under `metadata`.
            val unknown = TOP_LEVEL.findAll(frontmatter).map { it.groupValues[1] }.filterNot { it in FIELDS }.toList()
            if (unknown.isNotEmpty()) {
                logger.warn(
                    "dependencyskills: SKILL.md has fields the Agent Skills specification does not allow: " +
                        "${unknown.joinToString()}. Only ${FIELDS.sorted().joinToString()} are allowed; put anything " +
                        "else under `metadata`.",
                )
            }
            if (ALLOWED_TOOLS.containsMatchIn(frontmatter)) {
                logger.warn(
                    "dependencyskills: SKILL.md declares `allowed-tools`. A dependency skill may not grant an " +
                        "agent tools; consumers treat it as a finding (spec/content.md).",
                )
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
        val ALLOWED_TOOLS = Regex("""(?m)^allowed-tools:""")
        val TOP_LEVEL = Regex("""(?m)^([A-Za-z][\w-]*):""")
        val FIELDS = setOf("name", "description", "license", "compatibility", "metadata", "allowed-tools")
        /** `version:` indented under `metadata:`, which is where the specification puts it. */
        val VERSION = Regex("""(?m)^metadata:\s*\r?\n(?:[ \t]+.*\r?\n)*?[ \t]+version:\s*(.+)$""")
    }
}

/** The longest `description` the Agent Skills specification allows; the reference validator rejects a longer one. */
internal const val MAX_DESCRIPTION = 1024

private val DESCRIPTION_LINE = Regex("""description:[ \t]*(.*)""")

/**
 * A skill's `description` as the specification writes it: a plain or quoted scalar, or a folded (`>`)
 * or literal (`|`) block — the folded form being the one the author skill's template uses. The same
 * reading as the lookup's, so the length the build checks is the length a consumer measures. Null when
 * there is no `description`.
 */
internal fun skillDescription(frontmatter: String): String? {
    val lines = frontmatter.lines()
    val at = lines.indexOfFirst { DESCRIPTION_LINE.matches(it) }
    if (at < 0) return null
    val value = DESCRIPTION_LINE.matchEntire(lines[at])!!.groupValues[1].trim()
    val block = lines.drop(at + 1).takeWhile { it.startsWith(" ") || it.startsWith("\t") || it.isBlank() }
    return when (value.firstOrNull()) {
        '>' -> block.map { it.trim() }.filter { it.isNotEmpty() }.joinToString(" ")
        '|' -> block.joinToString("\n") { it.trim() }.trim('\n')
        else -> value.removeSurrounding("\"").removeSurrounding("'")
    }
}
