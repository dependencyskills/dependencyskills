package org.dependencyskills.plugin

import org.gradle.api.file.RegularFileProperty
import org.gradle.api.logging.Logging
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
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
 * Now it reports two ways, one per codex. **Over HTTP to the full service**: the build knows its own
 * coordinates and the service's address; the service knows the store. Neither knows anything about
 * the other's layout, which is what lets the store move, and what lets a Maven or npm plugin use the
 * same endpoint without teaching the service anything new. **As a CycloneDX SBOM in the root build
 * directory, for the lightweight codex**, which runs as a stdio MCP server started by the agent's
 * harness, has no port to be told on, and reads the file when an agent asks. That reader does know
 * where a Gradle build writes its output — the cost ADR-0012 v4 removed from the full service, taken
 * back deliberately for the lightweight one, where it buys having no process to keep running.
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

        /**
         * Where the resolved set is also written, as a CycloneDX SBOM — the root build directory.
         *
         * This is the handoff to the lightweight codex, which runs as an MCP server over stdio and
         * has no port to be reported to: it reads this file when an agent asks, rather than being
         * told when a build finishes. A file in the build's own output is the pattern SBOM plugins
         * already use, adds nothing a project must ignore, and is gone after `clean`.
         *
         * It is also the channel that keeps scope out of the agent's reach. Scope is what the build
         * resolved; if it were set through the same MCP interface the agent queries, the agent could
         * widen its own.
         */
        val reportFile: RegularFileProperty

        /**
         * The path of every project in the build. The SBOM keeps what a project resolved in an
         * earlier build until that project resolves again, so it needs to know which projects still
         * exist: one deleted from settings is dropped rather than kept forever.
         */
        val projectPaths: SetProperty<String>

        /**
         * `group:artifact:version` of every library the build's version catalogs declare. One no
         * module resolves goes into the SBOM marked as declared, so its skill is readable before the
         * first build that uses it — which is when an agent decides whether to write its own.
         */
        val declared: SetProperty<String>
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

    /**
     * The same, by the Gradle project that resolved it. A project whose classpath resolved empty is
     * here with an empty set, which is different from not being here: it says the project now has
     * no dependencies, where absence says this build did not look.
     */
    private val resolvedBy = LinkedHashMap<String, MutableSet<Coordinate>>()
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

    /** Configurations already counted, as `projectPath:configuration`. */
    private val counted = HashSet<String>()

    /**
     * Called each time a compile-dependency configuration resolves, with its project's path.
     *
     * A configuration is counted once however often it reports: fetching its dependencies' sources
     * reselects variants from the same graph and fires the same callback again.
     */
    fun record(projectPath: String, configuration: String, coordinates: Collection<Coordinate>) {
        synchronized(lock) {
            if (broken) return
            if (counted.add("$projectPath:$configuration")) resolutions++
            resolved.addAll(coordinates)
            resolvedBy.getOrPut(projectPath) { LinkedHashSet() }.addAll(coordinates)
        }
    }

    override fun close() {
        synchronized(lock) {
            writeReportFile()
            reportToService()
            report()
            announceAdded()
        }
    }

    private var written: String? = null

    /** `group:artifact:version` of every component the SBOM gained over the one it replaced. */
    private var added: List<String> = emptyList()

    /**
     * Writes the resolved set as a CycloneDX 1.6 SBOM, rewriting the file only when it changed.
     *
     * **Merged by project, not replaced.** Each component names the Gradle projects that resolved it.
     * A build replaces the entries of the projects it resolved and keeps the rest from the file it
     * replaces, so compiling one module leaves every other module's dependencies in scope — the file
     * used to hold only what the last build happened to resolve, and building one module of a
     * multi-module project dropped every other module's libraries. A project no longer in the build
     * is dropped; a file from before projects were recorded is replaced whole. A library a version
     * catalog declares and no project resolves is listed too, marked `dependencyskills:declared`.
     *
     * Only when something resolved, for the same reason as the HTTP report: an empty report would
     * erase the last real one. A classpath that resolved and held nothing IS written, as an SBOM
     * with no components — an empty scope, which is true. Unchanged content leaves the file alone,
     * so its modification time says when the dependencies last changed rather than when the build
     * last ran, and a reader checking it does no work between changes. What a rewrite adds over the
     * file it replaces is kept for [announceAdded]. Nothing here may fail a build.
     */
    private fun writeReportFile() {
        if (resolutions == 0) return
        val file = parameters.reportFile.orNull?.asFile ?: return
        runCatching {
            val root = parameters.projectName.orNull?.takeIf { it.isNotBlank() }
                ?: parameters.projectPath.orNull ?: "project"
            val previous = if (file.isFile) file.readText() else null
            val existing = parameters.projectPaths.orNull.orEmpty()

            // coordinate -> the projects that resolve it: this build's, then what the previous file
            // says of every project this build did not resolve and that still exists.
            val byCoordinate = sortedMapOf<String, MutableSet<String>>()
            resolvedBy.forEach { (project, coordinates) ->
                coordinates.filter { it.ecosystem == "maven" && it.value.split(':').size == 3 }
                    .forEach { byCoordinate.getOrPut(it.value) { sortedSetOf() }.add(project) }
            }
            previous?.let(::components)?.forEach { (coordinate, projects) ->
                projects.filter { it !in resolvedBy && it in existing }
                    .forEach { byCoordinate.getOrPut(coordinate) { sortedSetOf() }.add(it) }
            }

            // Declared in a catalog and resolved by no project: listed, marked, and recomputed from the
            // catalogs every build rather than merged, since the catalog is the whole record of it.
            parameters.declared.orNull.orEmpty()
                .filter { it.split(':').size == 3 && it !in byCoordinate }
                .forEach { byCoordinate[it] = sortedSetOf() }

            val components = byCoordinate.map { (coordinate, projects) ->
                val (group, artifact, version) = coordinate.split(':')
                val purl = "pkg:maven/$group/$artifact@$version"
                val properties = if (projects.isEmpty()) """{"name":"$DECLARED_PROPERTY","value":"true"}"""
                else projects.joinToString(",") { """{"name":"$PROJECT_PROPERTY","value":${quote(it)}}""" }
                """    {"type":"library","bom-ref":${quote(purl)},"group":${quote(group)},""" +
                    """"name":${quote(artifact)},"version":${quote(version)},"purl":${quote(purl)},""" +
                    """"properties":[$properties]}"""
            }
            val text = buildString {
                append("{\n")
                append("  \"bomFormat\": \"CycloneDX\",\n")
                append("  \"specVersion\": \"1.6\",\n")
                append("  \"version\": 1,\n")
                append("  \"metadata\": {\n")
                append("    \"tools\": {\"components\": [{\"type\": \"application\", \"name\": \"dependency-skills\"}]},\n")
                append("    \"component\": {\"type\": \"application\", \"bom-ref\": \"root\", \"name\": ${quote(root)}},\n")
                // Which classpath this is, since SBOM plugins usually describe the runtime one. This is
                // the compile classpath: what the project can import, which is what its scope must be.
                append("    \"properties\": [{\"name\": \"dependencyskills:classpath\", \"value\": \"compile\"}]\n")
                append("  },\n")
                append("  \"components\": [\n")
                append(components.joinToString(",\n"))
                if (components.isNotEmpty()) append('\n')
                append("  ]\n")
                append("}\n")
            }
            if (previous == text) {
                written = file.path
                return@runCatching
            }
            // Only against a file that was there: the first build, and the first after `clean`, have
            // nothing to compare with, and calling every dependency new would say nothing.
            if (previous != null) added = (purls(text) - purls(previous)).sorted()
            file.parentFile.mkdirs()
            file.writeText(text)
            written = file.path
        }
    }

    /**
     * `group:artifact:version` to the projects that resolve it, for every component in an SBOM this
     * class wrote — one component per line. A component with no project recorded is left out, so a
     * file from before projects were recorded is replaced rather than merged.
     */
    private fun components(sbom: String): Map<String, List<String>> =
        sbom.lineSequence().mapNotNull { line ->
            val purl = PURL.find(line) ?: return@mapNotNull null
            val projects = PROJECT.findAll(line).map { it.groupValues[1] }.toList()
            if (projects.isEmpty()) return@mapNotNull null
            "${purl.groupValues[1]}:${purl.groupValues[2]}:${purl.groupValues[3]}" to projects
        }.toMap()

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
            // Which of the two handoffs landed. The file is enough for the lightweight codex; only the
            // full service needs to be told, and saying "not recorded" when the file was written
            // would send someone looking for a problem that is not there.
            logger.lifecycle(
                if (written != null) {
                    "dependencyskills: ${resolved.size} ${plural(resolved.size, "coordinate", "coordinates")} " +
                        "written to $written; no codex service at $it, so the full codex was not told"
                } else {
                    "dependencyskills: no codex service at $it, so ${resolved.size} " +
                        "${plural(resolved.size, "coordinate was", "coordinates were")} not recorded"
                },
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

    /**
     * Names the dependencies this build added, so an agent reading the build's output knows to look.
     *
     * An agent that asked the lightweight codex early, got an empty list, then added a library and
     * built, never asked again, and the library's skill was never read. The build's output is the
     * one place every agent reads whatever harness it runs in, and it is read right after the change
     * that made a new skill reachable — so the news goes there, rather than into a hook one harness
     * has and another does not.
     *
     * It cannot say which of them ship a skill: the plugin never reads a sources jar, which is the
     * codex's work. So it names what changed and where to ask. Quiet level, so a build run with `-q`,
     * as agents often run one, still shows it; and only when something was added, so it is not noise.
     */
    private fun announceAdded() {
        if (added.isEmpty()) return
        val shown = added.take(MAX_ANNOUNCED).map { it.substringBeforeLast(':') }
        val more = added.size - shown.size
        logger.quiet(
            "dependencyskills: new since the last build: ${shown.joinToString(", ")}" +
                (if (more > 0) " and $more more" else "") +
                ". Any of them may ship a skill; an agent with the dependency-skills lookup can check " +
                "with list_dependency_skills.",
        )
    }

    /** The `group:artifact:version` of every component in an SBOM this class wrote. */
    private fun purls(sbom: String): Set<String> =
        PURL.findAll(sbom).map { "${it.groupValues[1]}:${it.groupValues[2]}:${it.groupValues[3]}" }.toSet()

    private fun plural(n: Int, one: String, many: String) = if (n == 1) one else many

    private companion object {
        val PURL = Regex(""""purl":"pkg:maven/([^/"]+)/([^@"]+)@([^"]+)"""")

        /** The component property naming a Gradle project that resolves it, by path. */
        const val PROJECT_PROPERTY = "dependencyskills:project"

        /** The component property marking a library a catalog declares and no project resolves yet. */
        const val DECLARED_PROPERTY = "dependencyskills:declared"
        val PROJECT = Regex(""""name":"$PROJECT_PROPERTY","value":"([^"]*)"""")

        /** Enough to recognise a change; a build that added more than this is a new project. */
        const val MAX_ANNOUNCED = 5

        // Short on purpose. A build waiting on a local service that is not running should notice
        // in the time it takes to fail a connection, not in the time it takes a request to expire.
        const val CONNECT_TIMEOUT_MS = 500L
        const val REQUEST_TIMEOUT_MS = 2000L
    }
}
