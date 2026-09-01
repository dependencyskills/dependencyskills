package org.dependencyskills.codex.indexer

import org.dependencyskills.codex.core.Coordinate
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile
import kotlin.io.path.name

/**
 * Finds a `-sources.jar` the build system has already downloaded.
 *
 * **Most of them are already there.** Measured on one real machine, 3,829 of 6,892 cached jars had
 * their sources beside them — 56% — because a Gradle sync fetches sources by default and IDEs ask
 * for them. Fetching a coordinate whose sources are already on disk would be paying twice for the
 * same bytes and filling a second directory with them.
 *
 * **This is not the coupling [ADR-0012] removed.** The store's own location stopped being Gradle's
 * business when the plugin stopped opening it. This is different: the ecosystem is named in the
 * coordinate, and where that ecosystem keeps its artifacts is a fact about somebody else's tool,
 * at a layout it owns and does not change. Reading it is not an agreement anyone has to maintain.
 *
 * Nothing is written here, ever. A jar found in this cache is read in place and never deleted —
 * it belongs to the build.
 */
object SourcesInCache {

    /** Where Gradle keeps resolved artifacts, under `GRADLE_USER_HOME` or `~/.gradle`. */
    private const val GRADLE_ARTIFACTS = "caches/modules-2/files-2.1"

    /**
     * The sources jar for [coordinate], or null when the cache does not have one.
     *
     * Null does not mean the library publishes none — only that this machine has not downloaded it.
     * The caller decides what to do about that; it is not a finding.
     */
    fun find(
        coordinate: Coordinate,
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = System.getProperties()
            .entries.associate { (k, v) -> k.toString() to v.toString() },
    ): Path? {
        if (!coordinate.ecosystem.equals("maven", ignoreCase = true)) return null
        // `group:artifact:version`, and the group keeps its dots: Gradle's cache uses one directory
        // per group rather than Maven's nested layout, so no translation is wanted here.
        val parts = coordinate.value.split(':')
        if (parts.size != 3) return null
        val (group, artifact, version) = parts
        if (group.isBlank() || artifact.isBlank() || version.isBlank()) return null

        // Named exactly, rather than "any jar with sources in the name", so a
        // `-sources-shaded.jar` or similar cannot be mistaken for the real one.
        return inCache(group, artifact, version, "$artifact-$version-sources.jar", env, sysProps)
    }

    /**
     * The COMPILED artifact for [coordinate], which is what says who can reach what (#30).
     *
     * A different file in the same version directory, and a different hash directory under it —
     * the classes jar and the sources jar are separate artifacts and Gradle stores each under its
     * own checksum. Null when this machine has only the sources, which is ordinary: the harvest
     * then runs without a visibility rule and reports that it did.
     */
    fun classes(
        coordinate: Coordinate,
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = System.getProperties()
            .entries.associate { (k, v) -> k.toString() to v.toString() },
    ): Path? {
        if (!coordinate.ecosystem.equals("maven", ignoreCase = true)) return null
        val parts = coordinate.value.split(':')
        if (parts.size != 3) return null
        val (group, artifact, version) = parts
        if (group.isBlank() || artifact.isBlank() || version.isBlank()) return null
        return inCache(group, artifact, version, "$artifact-$version.jar", env, sysProps)
    }

    /**
     * One directory per artifact hash, so the file is one level down and the hash is not
     * something we can predict — hence the scan rather than a resolve.
     */
    private fun inCache(
        group: String,
        artifact: String,
        version: String,
        wanted: String,
        env: Map<String, String>,
        sysProps: Map<String, String>,
    ): Path? {
        val versionDirectory = gradleHome(env, sysProps)
            .resolve(GRADLE_ARTIFACTS).resolve(group).resolve(artifact).resolve(version)
        if (!Files.isDirectory(versionDirectory)) return null
        return Files.newDirectoryStream(versionDirectory).use { hashes ->
            hashes.mapNotNull { it.resolve(wanted).takeIf(Path::isRegularFile) }.firstOrNull()
        }
    }

    /**
     * `GRADLE_USER_HOME` is honoured here, unlike for the store.
     *
     * The store must not follow it — the service never sees a build's value, so honouring it would
     * let the two disagree about where the store is. This is the opposite case: it is Gradle's own
     * directory, so Gradle's own variable is precisely the right answer.
     */
    private fun gradleHome(env: Map<String, String>, sysProps: Map<String, String>): Path =
        env["GRADLE_USER_HOME"]?.takeIf { it.isNotBlank() }?.let { Paths.get(it) }
            ?: Paths.get(sysProps["user.home"] ?: error("user.home is not set")).resolve(".gradle")
}
