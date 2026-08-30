package org.dependencyskills.plugin

import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.nio.file.Files
import java.nio.file.Path

/**
 * Writes down what this build resolved. That is the whole job.
 *
 * **The plugin does not touch the store.** It used to: it opened the SQLite database, diffed the
 * resolved set against it, and recorded new coordinates as pending. That was written when the
 * build was the only thing that could do it, and it cost two things worth more than the
 * convenience.
 *
 * It put **11.4 MB of SQLite on every consuming project's buildscript classpath**, for a job that
 * is watching a list and writing a file. And it made every Gradle daemon on the machine a writer
 * to one database — N projects times M daemons, concurrently, against a store the service is
 * meant to own. A single writer is worth more than a shortcut.
 *
 * So this writes the coordinates and stops. **The service has the rest of the information** —
 * what is harvested, what has no sources, what failed, how far behind the index is — and it is
 * the thing that should say so.
 *
 * **Nothing here may fail a build.** The index is an aid; a project whose scope cannot be written
 * still compiles, says so once, and stops trying.
 */
abstract class CodexRecorder : BuildService<CodexRecorder.Params>, AutoCloseable {

    interface Params : BuildServiceParameters {
        /**
         * Where to write this project's scope — the coordinates it resolved.
         *
         * The store is machine-level and records no project-to-coordinate edge, because scope
         * belongs to the `(project, source set) → coordinate` relation rather than to a
         * coordinate: the same artifact is `api` in one project and `implementation` in another.
         * **Only the build knows.** So the build writes it down and the service reads it.
         *
         * Text rather than a `RegularFileProperty`: this build writes the file, so fingerprinting
         * it as an input would invalidate the configuration cache on every build.
         */
        val scopeFile: Property<String>
    }

    private val logger = Logging.getLogger(CodexRecorder::class.java)
    private val lock = Any()

    /**
     * Every coordinate this build resolved, across every compilation.
     *
     * A set, because a library reached from three compilations is one library — the union is
     * formed here rather than computed anywhere later.
     */
    private val resolved = LinkedHashSet<Coordinate>()
    private var resolutions = 0
    private var broken = false

    /** Called once per compile-dependency configuration that resolved. */
    fun record(coordinates: Collection<Coordinate>) {
        synchronized(lock) {
            if (broken) return
            resolutions++
            resolved.addAll(coordinates)
        }
    }

    override fun close() {
        synchronized(lock) {
            writeScope()
            report()
        }
    }

    /**
     * Writes the coordinates this build resolved, for the service to read.
     *
     * **Written whole, every build, rather than appended.** A dependency removed from the build
     * file has to leave the scope; a scope that only grew would keep answering questions about a
     * library the project no longer has, which is the containment boundary widening quietly
     * rather than a cache going stale.
     */
    private fun writeScope() {
        val target = parameters.scopeFile.orNull?.let { Path.of(it) } ?: return
        // Nothing resolved, so this build learned nothing. The previous scope is better than an
        // empty one - and an empty scope means "search nothing", not "search everything".
        if (resolutions == 0) return
        try {
            Files.createDirectories(target.parent)
            Files.write(
                target,
                buildList {
                    add("# Written by the dependencyskills Gradle plugin. Do not edit.")
                    add("# The coordinates this project resolved, which is what its agent may search.")
                    addAll(resolved.map { it.toString() }.sorted())
                },
            )
        } catch (t: Throwable) {
            broken = true
            logger.warn("dependencyskills: could not write the agent scope to $target: ${t.message}")
        }
    }

    /**
     * Says what happened, including when nothing did.
     *
     * That last case is what this exists for. A build that never saw a compile classpath and a
     * build where everything was already known both leave the same file behind, and an indexer
     * that quietly indexes nothing is the failure this project keeps re-learning.
     *
     * It reports what it **wrote**, never what is indexed. How far behind the index is belongs to
     * the service, which is the only thing that knows.
     */
    private fun report() {
        if (broken) return
        if (resolutions == 0) {
            logger.lifecycle(
                "dependencyskills: no compile classpath resolved, so nothing was recorded. If " +
                    "that is a surprise, the plugin may be applied to a project with no sources.",
            )
            return
        }
        logger.lifecycle(
            "dependencyskills: $resolutions " +
                "${plural(resolutions, "compile classpath", "compile classpaths")}, " +
                "${resolved.size} ${plural(resolved.size, "coordinate", "coordinates")} recorded",
        )
    }

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many
}
