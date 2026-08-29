package org.dependencyskills.codex.core

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Where a model a developer installed themselves lives.
 *
 * The default encoder is not here — it travels inside the `encoder` artifact and arrives by
 * dependency resolution like anything else, which is the whole point of picking one small enough
 * to do that. This is the other case: a model too large to publish, or one a particular developer
 * wants instead of the default.
 *
 * `<store>/models/`, a sibling of the database, so the two things that are expensive to obtain sit
 * together and a developer who knows where one is can find the other. It inherits the store's
 * override chain, and adds its own, because someone with a shared model directory across projects
 * should not have to move the store to use it.
 *
 * **Nothing downloads into here.** A missing model is reported with [instructionsFor], never
 * fetched: a build that quietly pulls 2.3 GB the first time somebody runs it is a build that has
 * done something surprising, and doing it silently is worse than not doing it at all.
 */
object ModelLocation {
    const val DIRECTORY = "models"

    const val OVERRIDE_PROPERTY = "dependencyskills.models.dir"
    const val OVERRIDE_ENV = "DEPENDENCYSKILLS_MODELS_DIR"

    private fun props(): Map<String, String> =
        System.getProperties().entries.associate { (k, v) -> k.toString() to v.toString() }

    /** Precedence: this directory's own override, then the store's, then the store's default. */
    fun directory(
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = props(),
    ): Path {
        sysProps[OVERRIDE_PROPERTY]?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        env[OVERRIDE_ENV]?.takeIf { it.isNotBlank() }?.let { return Paths.get(it) }
        return CodexLocation.directory(env, sysProps).resolve(DIRECTORY)
    }

    /**
     * The installed model of that file name, or null when it is not there.
     *
     * Null rather than an exception because absence is an ordinary state here — most developers
     * will never install one — and the caller's job is to say so and carry on with the default,
     * not to fail. What a caller must not do is treat null as "no model was wanted".
     */
    fun find(
        fileName: String,
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = props(),
    ): Path? = directory(env, sysProps).resolve(fileName).takeIf { Files.isRegularFile(it) }

    /**
     * What to tell a developer who asked for a model that is not installed.
     *
     * The instructions live here rather than only in a README because this is where the absence is
     * discovered. A message that names the exact path and the exact file is one a person can act
     * on without going to look for documentation they do not know exists.
     */
    fun instructionsFor(
        fileName: String,
        env: Map<String, String> = System.getenv(),
        sysProps: Map<String, String> = props(),
    ): String {
        val target = directory(env, sysProps)
        return """
            No model named '$fileName' is installed.

            Put it here, creating the directory if it does not exist:
              ${target.resolve(fileName)}

            Or point at a directory you already keep models in:
              -D$OVERRIDE_PROPERTY=/path/to/models
              $OVERRIDE_ENV=/path/to/models

            Nothing downloads it for you. It is a large file and fetching it behind your back
            during a build is not a thing this should do without being asked.
        """.trimIndent()
    }
}
