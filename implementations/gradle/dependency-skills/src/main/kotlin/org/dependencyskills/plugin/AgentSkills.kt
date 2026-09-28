package org.dependencyskills.plugin

import groovy.json.JsonOutput
import groovy.json.JsonSlurper
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider
import org.gradle.work.DisableCachingByDefault
import java.io.File
import java.nio.file.Files
import java.security.MessageDigest

/**
 * Writes the agent skills a project asks for into the project, where its agents look for them.
 *
 * Two skills, one per role a project can have, each written only where its block is declared:
 *
 * ```kotlin
 * dependencySkills {
 *     consumer { }    // librarian: check what the dependencies offer before writing code
 *     author { }      // to-library-skill: write the guide this library ships
 * }
 * ```
 *
 * The plugin writes them rather than a separate installer because it is already applied, already
 * trusted, and runs on every build — so a skill always matches the plugin version that wrote it, and a
 * copy cannot go stale the way a hand copy does. ADR-0003 put the librarian here from the start.
 *
 * **Where.** `.agents/skills/<skill>/` at the root of the build, the cross-agent location, always; and
 * `.claude/skills/<skill>/` too, by default wherever the root has a `.claude/` directory, since Claude
 * Code reads only that. Copies, never links: a link is fine in a checkout someone develops in and a
 * mess everywhere else. A `.claude/skills/<skill>` that is already a link is left alone — it points at
 * a copy this writes anyway.
 *
 * **Edits.** Every file written is recorded with its digest in `dependencyskills-lock.json` at the root —
 * committed with the skills, like any lock file, so a fresh clone knows its copies are unedited —
 * the lock file the lightweight codex's installer keeps too, so `dependencyskills uninstall` reverses
 * either. A skill that no longer matches what was recorded has been edited, and [SkillRefresh] decides
 * what happens to it: replaced with a warning by default, or kept.
 */
internal object AgentSkills {

    /** The skills the plugin carries: `consumer { }` asks for the first, `author { }` for the second. */
    const val LIBRARIAN = "librarian"
    const val TO_LIBRARY_SKILL = "to-library-skill"

    /**
     * The lock file, at the root, shared with the lightweight codex's installer: what was written, with
     * the digest of every file. Named on the `*-lock.json` convention, like the `skills` CLI's
     * `skills-lock.json`, without taking that tool's own file.
     */
    const val MANIFEST = "dependencyskills-lock.json"

    private const val BUNDLE = "/org/dependencyskills/plugin/skills"

    /**
     * Registers a task writing [skill] as [spec] says, and runs it before each compile task the plugin
     * watches, so any build that compiles writes it. Registered in every project; it does nothing where
     * the block is not declared, and once per build where several projects declare it.
     */
    fun register(project: Project, taskName: String, skill: String, spec: AgentSkillSpec, pluginEnabled: Provider<Boolean>, claims: Provider<SourcesClaims>): TaskProvider<WriteAgentSkill> =
        project.tasks.register(taskName, WriteAgentSkill::class.java) {
            group = "dependency skills"
            description = "Writes the $skill agent skill into .agents/skills, where the dependencySkills block asks for it."
            this.skill.set(skill)
            // The plugin's master switch off means it writes nothing, this included.
            writing.set(spec.enabled.zip(pluginEnabled) { skill, plugin -> skill && plugin })
            refresh.set(spec.refresh)
            claudeCode.set(spec.claudeCode)
            rootDirectory.set(project.rootDir.absolutePath)
            this.claims.set(claims)
            usesService(claims)
        }

    /** The skill as this plugin version carries it: relative path to content. */
    fun bundled(skill: String): Map<String, ByteArray> {
        val index = javaClass.getResourceAsStream("$BUNDLE/index.txt")?.use { it.readBytes().decodeToString() }
            ?: error("this build of the plugin carries no agent skills")
        return index.lines().filter { it.startsWith("$skill/") }.associate { path ->
            path.removePrefix("$skill/") to (javaClass.getResourceAsStream("$BUNDLE/$path")?.use { it.readBytes() }
                ?: error("the plugin's skill index names $path, which it does not carry"))
        }
    }

    fun digest(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    /** Every file under [directory], relative to it, with its digest; null when there is no directory. */
    fun present(directory: File): Map<String, String>? =
        if (!directory.isDirectory) null
        else directory.walkTopDown().filter { it.isFile }
            .associate { it.relativeTo(directory).invariantSeparatorsPath to digest(it.readBytes()) }

    /** What happened to one copy of a skill, and whether it now matches what the plugin carries. */
    enum class Outcome { Written, Updated, Current, Replaced, KeptEdited, KeptLink }

    /**
     * Brings the copy at [target] up to [carried], as [refresh] allows, given what was [recorded] the
     * last time something wrote it. Returns what happened; writes nothing when it is kept.
     */
    fun reconcile(target: File, carried: Map<String, ByteArray>, recorded: Map<String, String>?, refresh: SkillRefresh): Outcome {
        if (Files.isSymbolicLink(target.toPath())) return Outcome.KeptLink
        val wanted = carried.mapValues { digest(it.value) }
        val there = present(target)
        val outcome = when {
            there == null -> Outcome.Written
            there == wanted -> return Outcome.Current
            // Not edited: exactly what was written last time, from an earlier version of the plugin.
            recorded != null && there == recorded -> Outcome.Updated
            refresh == SkillRefresh.Always -> Outcome.Replaced
            else -> return Outcome.KeptEdited
        }
        target.deleteRecursively()
        carried.forEach { (path, bytes) ->
            File(target, path).apply { parentFile.mkdirs() }.writeBytes(bytes)
        }
        return outcome
    }

    /** The manifest's content, or an empty one. Unparseable is treated as empty, and rewritten. */
    @Suppress("UNCHECKED_CAST")
    fun readManifest(root: File): MutableMap<String, Any?> {
        val file = File(root, MANIFEST)
        if (!file.isFile) return mutableMapOf("changes" to mutableListOf<Any?>())
        return runCatching { (JsonSlurper().parse(file) as Map<String, Any?>).toMutableMap() }
            .getOrElse { mutableMapOf() }
            .also { if (it["changes"] !is List<*>) it["changes"] = mutableListOf<Any?>() }
    }

    /** The digests the manifest recorded for a skill at [path], or null if it records none. */
    @Suppress("UNCHECKED_CAST")
    fun recorded(manifest: Map<String, Any?>, path: String): Map<String, String>? =
        (manifest["changes"] as List<Map<String, Any?>>)
            .firstOrNull { it["kind"] == "skill" && it["path"] == path }
            ?.let { it["files"] as? Map<String, String> }

    /** Records [files] as what is now at [path], replacing any earlier record of it. */
    @Suppress("UNCHECKED_CAST")
    fun record(manifest: MutableMap<String, Any?>, path: String, files: Map<String, String>) {
        val changes = (manifest["changes"] as List<Map<String, Any?>>).filterNot { it["kind"] == "skill" && it["path"] == path }
        manifest["changes"] = changes + mapOf("kind" to "skill", "path" to path, "files" to files.toSortedMap(), "by" to "gradle-plugin")
    }

    fun writeManifest(root: File, manifest: Map<String, Any?>) {
        File(root, MANIFEST).apply { parentFile.mkdirs() }
            .writeText(JsonOutput.prettyPrint(JsonOutput.toJson(manifest)) + "\n")
    }
}

/**
 * What a project does with a skill it has edited, when the plugin carries a different version.
 *
 * [Always] by default, so a skill stays what the plugin version ships unless a project explicitly
 * says otherwise, in the block that asks for the skill:
 *
 * ```kotlin
 * import org.dependencyskills.plugin.SkillRefresh
 *
 * dependencySkills {
 *     consumer { refresh = SkillRefresh.UnlessEdited }
 * }
 * ```
 *
 * A skill nobody edited is updated either way.
 */
enum class SkillRefresh {
    /**
     * Replace the skill with the version the plugin carries on every build, edited or not, with a
     * warning in the build output whenever edits are overwritten, so a developer never loses them
     * without being told. The default.
     */
    Always,

    /**
     * Keep an edited skill, with a warning in the build output that it was not updated — so nobody keeps
     * an old skill without knowing a newer one exists.
     */
    UnlessEdited,
}

/** One of the agent skills the plugin writes. Declaring its block is what turns it on. */
abstract class AgentSkillSpec {

    /**
     * Whether the skill is written. Declaring the block turns it on, so a project that never declares it
     * gets nothing; set it false to turn the skill off without deleting the block.
     */
    abstract val enabled: Property<Boolean>

    /** What happens to an edited copy of the skill; see [SkillRefresh]. Defaults to [SkillRefresh.Always]. */
    abstract val refresh: Property<SkillRefresh>

    /**
     * Whether to write a copy for Claude Code too, in `.claude/skills/`, which is the only place it reads
     * a project's skills. Defaults to whether the root of the build has a `.claude/` directory.
     */
    abstract val claudeCode: Property<Boolean>
}

/**
 * Writes one agent skill into the root of the build. Nothing here may fail a build: a skill is an aid,
 * and a project must still compile without it.
 */
@DisableCachingByDefault(because = "It writes into the source tree, and deciding what to write is the work")
abstract class WriteAgentSkill : DefaultTask() {

    @get:Internal abstract val skill: Property<String>
    @get:Internal abstract val writing: Property<Boolean>
    @get:Internal abstract val refresh: Property<SkillRefresh>
    @get:Internal abstract val claudeCode: Property<Boolean>
    @get:Internal abstract val rootDirectory: Property<String>
    @get:Internal abstract val claims: Property<SourcesClaims>

    @TaskAction
    fun write() {
        if (!writing.getOrElse(false)) return
        val name = skill.get()
        // Once per build, whichever project runs first; the manifest is shared, so under the lock.
        if (!claims.get().claim("agent-skill:$name")) return
        runCatching {
            claims.get().exclusively { writeUnderLock(name) }
        }.onFailure { logger.warn("dependencyskills: the $name skill was not written: ${it.message}") }
    }

    private fun writeUnderLock(name: String) {
        val root = File(rootDirectory.get())
        val carried = AgentSkills.bundled(name)
        val wanted = carried.mapValues { AgentSkills.digest(it.value) }
        val manifest = AgentSkills.readManifest(root)
        val block = if (name == AgentSkills.LIBRARIAN) "consumer" else "author"
        val targets = listOf(".agents/skills/$name") +
            (if (claudeCode.getOrElse(false)) listOf(".claude/skills/$name") else emptyList())
        var changed = false
        for (path in targets) {
            val recorded = AgentSkills.recorded(manifest, path)
            when (AgentSkills.reconcile(File(root, path), carried, recorded, refresh.getOrElse(SkillRefresh.Always))) {
                AgentSkills.Outcome.Written -> logger.lifecycle(
                    "dependencyskills: wrote the $name skill to $path/ — commit it together with dependencyskills-lock.json, which records it",
                )
                AgentSkills.Outcome.Updated ->
                    logger.lifecycle("dependencyskills: updated the $name skill in $path/ to the version this plugin carries")
                AgentSkills.Outcome.Replaced -> logger.warn(
                    "dependencyskills: OVERWROTE local edits to the $name skill in $path/ with the version this plugin " +
                        "carries; if the edits were committed, version control still has them. To keep edits, set " +
                        "dependencySkills { $block { refresh = SkillRefresh.UnlessEdited } }.",
                )
                // Already what the plugin carries: only a copy nothing recorded yet needs recording.
                AgentSkills.Outcome.Current -> if (recorded == wanted) continue
                AgentSkills.Outcome.KeptEdited -> {
                    // An edit to the version the plugin already carries holds nothing back, so it is not worth a warning.
                    if (recorded == wanted) logger.info("dependencyskills: $path/ has local edits, kept")
                    else logger.warn(
                        "dependencyskills: $path/ has local edits, so it was NOT updated to the version this plugin " +
                            "carries, because refresh is UnlessEdited. To take the new version, delete that directory and " +
                            "build again, or remove the refresh setting from dependencySkills { $block { } }.",
                    )
                    continue
                }
                AgentSkills.Outcome.KeptLink -> {
                    logger.info("dependencyskills: $path is a link, left alone")
                    continue
                }
            }
            AgentSkills.record(manifest, path, wanted)
            changed = true
        }
        if (changed) AgentSkills.writeManifest(root, manifest)
    }
}
