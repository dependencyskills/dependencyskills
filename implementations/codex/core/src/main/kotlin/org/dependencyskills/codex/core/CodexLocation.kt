package org.dependencyskills.codex.core

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where the store lives.
 *
 * `~/.gradle/dscodex/`, beside Gradle's own caches rather than inside them. Gradle
 * garbage-collects `caches/` — every `gc.properties` lives under it — and `CACHEDIR.TAG`,
 * which tells backup tools to skip a tree, sits at `caches/`, `jdks/` and `daemon/` rather
 * than at the root. A sibling of `caches/` is neither collected nor excluded from backups,
 * which matters because this store is expensive to rebuild.
 *
 * The precedent is direct: `nodejs`, `yarn` and `binaryen` are third-party plugin caches at
 * that root already, put there by the Kotlin Multiplatform JS plugin.
 *
 * One store per **build system**, not one per machine: the store belongs to whatever
 * resolved the dependencies, so a Maven implementation would write under `~/.m2/`.
 *
 * The version lives in the schema, not the path. `~/.m2` is Maven *2* and Maven 3 and 4
 * still use it; a version in a directory name becomes a lie, and it cannot detect or
 * migrate an old store the way a recorded `schema_version` can.
 */
object CodexLocation {
    const val DIRECTORY = "dscodex"
    const val FILENAME = "codex.db"

    /** Read in preference to everything else, so a shared or unusual layout is not blocked. */
    const val OVERRIDE_PROPERTY = "dependencyskills.codex.dir"
    const val OVERRIDE_ENV = "DEPENDENCYSKILLS_CODEX_DIR"

    private fun props(): Map<String, String> =
        System.getProperties().entries.associate { (k, v) -> k.toString() to v.toString() }

    /**
     * Resolves the store directory. Precedence: explicit override, then `GRADLE_USER_HOME`,
     * then `~/.gradle`. [env] and [sysProps] are parameters so this is testable without
     * touching the real environment.
     */
    fun directory(
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = props(),
    ): Path {
        sysProps[OVERRIDE_PROPERTY]?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        env[OVERRIDE_ENV]?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        val gradleHome = env["GRADLE_USER_HOME"]?.takeIf { it.isNotBlank() }
            ?.let { Paths.get(it) }
            ?: Paths.get(sysProps["user.home"] ?: error("user.home is not set")).resolve(".gradle")
        return gradleHome.resolve(DIRECTORY)
    }

    fun databaseFile(
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = props(),
    ): Path = directory(env, sysProps).resolve(FILENAME)
}
