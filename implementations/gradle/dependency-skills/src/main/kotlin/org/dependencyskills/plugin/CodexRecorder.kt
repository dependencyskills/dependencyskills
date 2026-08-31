package org.dependencyskills.plugin

import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Writes down what this build resolved. That is the whole job.
 *
 * **The plugin does not touch the store, and does not know where it is.** It used to open the
 * SQLite database directly, which put 11.4 MB of SQLite on every consuming project's buildscript
 * classpath and made every Gradle daemon on the machine a writer to one file. Then it wrote a text
 * file at a path the service had to know how to find, which left the service — the one component
 * meant to be ecosystem-agnostic — knowing where Gradle keeps a project's directory.
 *
 * Now it reports over HTTP. The build knows its own coordinates and the service's address; the
 * service knows the store. Neither knows anything about the other's layout, which is what lets the
 * store move, and what lets a Maven or npm plugin use the same endpoint without teaching the
 * service anything new.
 *
 * **Nothing here may fail a build.** The index is an aid; a project whose scope cannot be written
 * still compiles, says so once, and stops trying.
 */
abstract class CodexRecorder : BuildService<CodexRecorder.Params>, AutoCloseable {

    interface Params : BuildServiceParameters {
        /** Where the codex service is listening. */
        val serviceUrl: Property<String>

        /** This project's directory — the identity the service files its scope under. */
        val projectPath: Property<String>

        /**
         * The name the scope is grouped by. Defaults to [projectPath], which cannot collide.
         *
         * Scope belongs to the `(project, source set) → coordinate` relation rather than to a
         * coordinate — the same artifact is `api` in one project and `implementation` in another —
         * so only the build knows it. The build reports it and the service keeps it.
         */
        val projectName: Property<String>
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
    private var unreachable: String? = null

    /**
     * Tells the service a build has started, so it can load its model while Gradle downloads.
     *
     * Fired at the start of configuration rather than at the end of the build, which is the whole
     * point: on a cold project the dependency download is minutes and loading a generative model is
     * not instant either, so the two should overlap rather than queue.
     *
     * Nothing is reported here and no pass starts — the service decides whether there is anything
     * worth warming for, and a machine with nothing pending loads nothing.
     *
     * Same rule as everything else in this class: it cannot fail, block or slow a build. It runs on
     * a daemon thread with a short timeout and every outcome is swallowed, so a service that is not
     * running costs a connection refusal on a thread nobody is waiting for.
     */
    fun signalSyncing() {
        val url = parameters.serviceUrl.orNull?.trimEnd('/') ?: return
        val path = parameters.projectPath.orNull ?: return
        Thread {
            runCatching {
                HttpClient.newBuilder().connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS)).build()
                    .send(
                        HttpRequest.newBuilder(URI.create("$url/projects/syncing"))
                            .header("Content-Type", "application/json")
                            .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                            .POST(HttpRequest.BodyPublishers.ofString("""{"path":${quote(path)}}"""))
                            .build(),
                        HttpResponse.BodyHandlers.discarding(),
                    )
            }
        }.apply { isDaemon = true; name = "dependencyskills-warm" }.start()
    }

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
            reportToService()
            report()
        }
    }

    /**
     * Tells the service what this build resolved.
     *
     * **Sent whole, every build, rather than as a difference.** A dependency removed from the build
     * file has to leave the scope; a report that only added would keep answering questions about a
     * library the project no longer has, which is the containment boundary widening quietly rather
     * than a cache going stale.
     *
     * **Nothing here may fail or delay a build.** The timeouts are short and every outcome is
     * swallowed, because a developer who has not started the service — or has stopped it, or is on
     * a machine that never had it — must not have their build fail, hang, or slow down for an
     * index that is an aid. The cost of the service being down is that it learns about this build
     * on the next one, and it says so rather than answering as though it knew.
     */
    private fun reportToService() {
        // Nothing resolved, so this build learned nothing. Reporting an empty set would erase what
        // the last real build reported - and an empty scope means "search nothing".
        if (resolutions == 0) return
        val url = parameters.serviceUrl.orNull?.trimEnd('/') ?: return
        val path = parameters.projectPath.orNull ?: return
        val name = parameters.projectName.orNull?.takeIf { it.isNotBlank() } ?: path
        try {
            val body = buildString {
                append("""{"path":""").append(quote(path))
                append(""","name":""").append(quote(name))
                append(""","ecosystem":"maven","coordinates":[""")
                resolved.map { it.toString() }.sorted().forEachIndexed { i, c ->
                    if (i > 0) append(',')
                    append(quote(c))
                }
                append("]}")
            }
            val response = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(CONNECT_TIMEOUT_MS))
                .build()
                .send(
                    HttpRequest.newBuilder(URI.create("$url/projects"))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofMillis(REQUEST_TIMEOUT_MS))
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build(),
                    HttpResponse.BodyHandlers.discarding(),
                )
            if (response.statusCode() !in 200..299) {
                broken = true
                logger.warn("dependencyskills: the codex service refused this project (HTTP ${response.statusCode()})")
            }
        } catch (t: Throwable) {
            // Including the service simply not being there, which is an ordinary state.
            broken = true
            unreachable = url
        }
    }

    /** Minimal JSON string escaping. A coordinate is not arbitrary text, but it is not ours either. */
    private fun quote(value: String): String = buildString {
        append('"')
        value.forEach { c ->
            when {
                c == '"' -> append("\\\"")
                c == '\\' -> append("\\\\")
                c < ' ' -> append("\\u%04x".format(c.code))
                else -> append(c)
            }
        }
        append('"')
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
        // Not silence. Saying "recorded" would be a lie and saying nothing leaves a developer
        // wondering why their agent knows nothing, so it says what it saw and what became of it.
        unreachable?.let {
            logger.lifecycle(
                "dependencyskills: no codex service at $it, so ${resolved.size} " +
                    "${plural(resolved.size, "coordinate was", "coordinates were")} not recorded",
            )
            return
        }
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

    private companion object {
        // Short on purpose. A build waiting on a local service that is not running should notice
        // in the time it takes to fail a connection, not in the time it takes a request to expire.
        const val CONNECT_TIMEOUT_MS = 500L
        const val REQUEST_TIMEOUT_MS = 2000L
    }
}
