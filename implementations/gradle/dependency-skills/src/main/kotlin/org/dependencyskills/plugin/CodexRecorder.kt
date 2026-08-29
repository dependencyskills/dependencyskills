package org.dependencyskills.plugin

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.CodexLocation
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.HarvestState
import org.gradle.api.provider.Property
import org.gradle.api.logging.Logging
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Files
import java.nio.file.Path

/**
 * Holds the store open for the length of one build, and records what the build resolved.
 *
 * A build service because there is exactly one store and several configurations resolve into
 * it, possibly on different threads: this is the one place that opens it, the one place that
 * counts, and the one place that closes it.
 *
 * **Nothing here may fail a build.** The index is an aid; a project whose store is corrupt, or
 * whose disk is full, or which is running against a schema from a newer version, must still
 * compile. Every entry point swallows its failure, says so once, and stops trying.
 */
abstract class CodexRecorder : BuildService<CodexRecorder.Params>, AutoCloseable {

    interface Params : BuildServiceParameters {
        /**
         * Where the store is. Unset means wherever [CodexLocation] resolves it to.
         *
         * A path as text rather than a `DirectoryProperty` on purpose. A directory property is
         * a file-system input, and the configuration cache fingerprints it — so pointing this
         * at a real store would invalidate the cache on every build, because the build just
         * wrote to it.
         */
        val storeDirectory: Property<String>

        /**
         * Where to write this project's scope — the coordinates it resolved.
         *
         * The store is machine-level and holds entries from every project on this machine; it
         * deliberately records no project-to-coordinate edge, because scope belongs to the
         * `(project, source set) → coordinate` relation rather than to a coordinate. **Only the
         * build knows.** So the build writes it down, and the MCP server reads it.
         *
         * Text rather than a `DirectoryProperty`, for the same configuration-cache reason as
         * [storeDirectory]: this build writes the file, so fingerprinting it would invalidate
         * the cache on every build.
         */
        val scopeFile: Property<String>
    }

    private val logger = Logging.getLogger(CodexRecorder::class.java)

    private val lock = Any()
    private var codex: Codex? = null
    private var broken = false

    /** Coordinates this build has already handed to the store, so it hands them over once. */
    private val seen = LinkedHashSet<Coordinate>()
    private val recorded = LinkedHashSet<Coordinate>()
    private var resolutions = 0

    /**
     * Records everything in [coordinates] the store has never seen.
     *
     * Called once per compile-dependency configuration that resolved. The union across them is
     * formed here rather than computed anywhere: the store is a set, and a coordinate arriving
     * from three compilations is one row.
     */
    fun record(coordinates: Collection<Coordinate>) {
        synchronized(lock) {
            if (broken) return
            resolutions++
            val fresh = coordinates.filter { seen.add(it) }
            if (fresh.isEmpty()) return
            try {
                recorded.addAll(store().seenAll(fresh))
            } catch (t: Throwable) {
                fail(t)
            }
        }
    }

    private fun store(): Codex = codex ?: openStore().also { codex = it }

    private fun openStore(): Codex {
        val configured: Path? = parameters.storeDirectory.orNull?.let { Path.of(it) }
        return if (configured != null) Codex.open(configured.resolve(CodexLocation.FILENAME))
        else Codex.open()
    }

    private fun fail(t: Throwable) {
        broken = true
        // Once. A warning per configuration would be several identical lines for one fault.
        logger.warn(
            "dependency-skills: the codex is unavailable, so nothing was recorded this build " +
                "(${t.javaClass.simpleName}: ${t.message}). The build is unaffected."
        )
        runCatching { codex?.close() }
        codex = null
    }

    override fun close() {
        synchronized(lock) {
            if (!broken) {
                writeScope()
                report()
            }
            runCatching { codex?.close() }
            codex = null
        }
    }

    /**
     * Writes the coordinates this build resolved, for the MCP server to read.
     *
     * **Written whole, every build, rather than appended.** A dependency removed from the build
     * file has to leave the scope, and a scope that only grows would keep answering questions
     * about a library the project no longer has — which is the containment boundary quietly
     * widening rather than a stale cache.
     *
     * Nothing here may fail a build: a project whose scope cannot be written still compiles, and
     * says so once.
     */
    private fun writeScope() {
        val target = parameters.scopeFile.orNull?.let { Path.of(it) } ?: return
        if (resolutions == 0) return   // nothing resolved; last build's scope is better than none
        try {
            Files.createDirectories(target.parent)
            val lines = buildList {
                add("# Written by the dependencyskills Gradle plugin. Do not edit.")
                add("# The coordinates this project resolved, which is what its agent may search.")
                addAll(seen.map { "${it.ecosystem}:${it.value}" }.sorted())
            }
            Files.write(target, lines)
        } catch (t: Throwable) {
            logger.warn("dependencyskills: could not write the agent scope to $target: ${t.message}")
        }
    }

    /**
     * Says what happened, including when nothing did.
     *
     * The distinction the last line exists for: a build where every dependency was already
     * known and a build where the plugin never saw a compile classpath both record nothing,
     * and an index that quietly indexes nothing is the failure this project keeps re-learning.
     */
    private fun report() {
        if (resolutions == 0) {
            logger.lifecycle(
                "dependency-skills: no compile classpath resolved this build — nothing was checked"
            )
            return
        }
        val pending = try {
            store().coordinatesIn(HarvestState.Pending).size
        } catch (t: Throwable) {
            fail(t)
            return
        }
        logger.lifecycle(
            "dependency-skills: $resolutions compile ${plural(resolutions, "classpath", "classpaths")}, " +
                "${seen.size} ${plural(seen.size, "coordinate", "coordinates")}, " +
                "${recorded.size} new — $pending awaiting harvest"
        )
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    /** What was recorded this build. For tests and for anything that wants to say more. */
    internal fun newCoordinates(): List<Coordinate> = synchronized(lock) { recorded.toList() }
}
