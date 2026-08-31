package org.dependencyskills.codex.server

import com.sksamuel.hoplite.ConfigLoaderBuilder
import com.sksamuel.hoplite.ExperimentalHoplite
import com.sksamuel.hoplite.addFileSource
import org.dependencyskills.codex.core.CodexLocation
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path

/**
 * What the operator gets to decide, read from `~/.dscodex/config.toml`.
 *
 * **A file rather than only flags, because the interesting settings are about the machine.** How
 * much memory this may hold and whether it holds it between passes is a property of where the
 * service is installed — a laptop, a workstation with memory to spare, a shared box serving a team
 * — and those answers outlive any one invocation. Putting them on a command line means they live in
 * whatever launch agent or unit file happens to start the process, where nobody finds them.
 *
 * Beside the store, because they are the same decision: this is the directory that belongs to this
 * tool on this machine, and a developer who knows where one is can find the other.
 *
 * **Absent is not an error.** A machine with no config file gets the defaults, and the defaults are
 * the conservative ones — one coordinate at a time, model unloaded when idle. Someone who wants the
 * service to hold a model resident is making a deliberate choice about their machine, and should
 * have to write it down.
 */
data class CodexConfig(
    val server: ServerSettings = ServerSettings(),
    val indexing: IndexingSettings = IndexingSettings(),
) {
    companion object {
        const val FILE_NAME = "config.toml"

        private val logger = LoggerFactory.getLogger(CodexConfig::class.java)

        /** Where the file lives, following the store rather than deciding for itself. */
        fun file(store: Path): Path =
            (store.parent ?: CodexLocation.directory()).resolve(FILE_NAME)

        /**
         * Reads the config beside [store], or returns defaults when there is none.
         *
         * A malformed file is **reported and refused**, not silently replaced with defaults. Config
         * that quietly does not apply is worse than config that fails: a developer who capped this
         * to one worker and finds it running eight has no way to discover their typo.
         */
        @OptIn(ExperimentalHoplite::class)
        fun load(store: Path): CodexConfig {
            val path = file(store)
            if (!Files.isRegularFile(path)) {
                logger.info("no config at {}; using defaults", path)
                return CodexConfig()
            }
            return ConfigLoaderBuilder.default()
                .addFileSource(path.toFile())
                .build()
                .loadConfigOrThrow<CodexConfig>()
                .also { logger.info("config from {}", path) }
        }
    }
}

data class ServerSettings(
    /**
     * Loopback by default. This answers questions about one machine's dependency graph, and
     * `0.0.0.0` would put that on whatever network the machine is joined to. A service deliberately
     * shared across machines sets this, which is a line in a file somebody wrote rather than a
     * default nobody chose.
     */
    val host: String = "127.0.0.1",
    val port: Int = SERVER_PORT,
)

data class IndexingSettings(
    /**
     * How many coordinates are summarised at once.
     *
     * One by default. This is in flight, not model loads — the model stays in memory across every
     * coordinate in a pass regardless of this number.
     */
    val concurrency: Int = 1,

    /**
     * Whether the generative model stays in memory between passes.
     *
     * False by default, so an idle service costs a JVM and an open database rather than a model.
     * That is the right default for a laptop and the wrong one for a workstation with memory to
     * spare or a service dedicated to many machines, where paying the load on every pass is worse
     * than holding the weights — which is exactly why it is a setting rather than a rule.
     */
    val keepModelResident: Boolean = false,

    /**
     * How long an idle service waits before unloading, when [keepModelResident] is false.
     *
     * Not zero. A sync of several projects arrives as a burst, and unloading between them would pay
     * the load repeatedly for work that was about to continue.
     */
    val unloadAfterIdleSeconds: Long = 300,

    /**
     * The generative model used to summarise, as a path.
     *
     * No default, and null means **nothing is summarised**. The model is large, is not committed
     * and is not downloaded by anything here — a service that quietly fetched two gigabytes on
     * first use would have done something surprising. Absent is reported at the start of a pass
     * rather than discovered as an empty index.
     */
    val model: String? = null,
)
