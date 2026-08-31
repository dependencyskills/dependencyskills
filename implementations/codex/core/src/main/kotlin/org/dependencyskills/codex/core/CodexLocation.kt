package org.dependencyskills.codex.core

import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where the store lives.
 *
 * `~/.dscodex/`. Developer tools live at `~/.<name>` — `.gradle`, `.m2`, `.aws`, `.cargo` — and
 * this is one of those. The name is `dscodex` because that is already the prefix the codebase uses
 * everywhere else: `libdscodex`, `DSCODEX_TEST_MODEL`, `dscodex.native.dir`. `~/.codex` was
 * rejected rather than overlooked; an unrelated CLI already claims it.
 *
 * **It used to be `~/.gradle/dscodex/`**, on the reasoning that the store belonged to whatever
 * resolved the dependencies. That stopped being true when the Gradle plugin lost its dependency on
 * this module: the plugin now watches compile classpaths and reports what it resolved, and the
 * service owns the store. Nothing about the store is Gradle's business any more, so it does not
 * live in Gradle's directory.
 *
 * **`GRADLE_USER_HOME` is deliberately not consulted.** It was, and it is now a hazard rather than
 * a convenience: the service is a long-lived process started from a shell or a launch agent, and it
 * never sees the per-build or per-CI value a build does. Honouring it would let a build's idea of
 * where the store is diverge from the service's, with nothing detecting the split — the store would
 * simply look empty.
 *
 * One store per **machine**, not per build system. Coordinates carry their ecosystem
 * (`maven:group:artifact:version`), so one store holds every ecosystem without collision, and a
 * library resolved by two different build systems is harvested and summarised once rather than
 * twice — summarising being the expensive half.
 *
 * The version lives in the schema, not the path. `~/.m2` is Maven *2* and Maven 3 and 4 still use
 * it; a version in a directory name becomes a lie, and it cannot detect or migrate an old store the
 * way a recorded `schema_version` can.
 */
object CodexLocation {
    const val DIRECTORY = ".dscodex"
    const val FILENAME = "codex.db"

    /** Read in preference to everything else, so a shared or unusual layout is not blocked. */
    const val OVERRIDE_PROPERTY = "dependencyskills.codex.dir"
    const val OVERRIDE_ENV = "DEPENDENCYSKILLS_CODEX_DIR"

    /** Where the store used to live, so its presence can be reported rather than ignored. */
    const val LEGACY_DIRECTORY = "dscodex"

    private fun props(): Map<String, String> =
        System.getProperties().entries.associate { (k, v) -> k.toString() to v.toString() }

    /**
     * Resolves the store directory. Precedence: explicit override, then `~/.dscodex`.
     *
     * [env] and [sysProps] are parameters so this is testable without touching the real
     * environment.
     */
    fun directory(
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = props(),
    ): Path {
        sysProps[OVERRIDE_PROPERTY]?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        env[OVERRIDE_ENV]?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        return home(sysProps).resolve(DIRECTORY)
    }

    fun databaseFile(
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = props(),
    ): Path = directory(env, sysProps).resolve(FILENAME)

    /**
     * Where a store written before the move would be, or null when this machine never had one.
     *
     * Exists so a service starting against an empty store can say "there is one over there" rather
     * than starting silently and looking like it is working. Nothing migrates it: the store is
     * reproducible, and moving a database on someone's behalf is a bigger promise than reporting it.
     *
     * `GRADLE_USER_HOME` **is** honoured here, unlike in [directory], because it is part of what the
     * old path meant.
     */
    fun legacyDatabaseFile(
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = props(),
    ): Path {
        val gradleHome = env["GRADLE_USER_HOME"]?.takeIf { it.isNotBlank() }
            ?.let { Paths.get(it) }
            ?: home(sysProps).resolve(".gradle")
        return gradleHome.resolve(LEGACY_DIRECTORY).resolve(FILENAME)
    }

    private fun home(sysProps: Map<String, String>): Path =
        Paths.get(sysProps["user.home"] ?: error("user.home is not set"))
}
