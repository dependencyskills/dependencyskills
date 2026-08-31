package org.dependencyskills.codex.server

import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStopped
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.compression.Compression
import io.ktor.server.plugins.compression.deflate
import io.ktor.server.plugins.compression.gzip
import io.ktor.server.plugins.compression.minimumSize
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.request.header
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.modelcontextprotocol.kotlin.sdk.server.mcpStatelessStreamableHttp
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.CodexLocation
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.index.VectorSearch
import org.koin.core.logger.Level
import org.koin.core.parameter.parametersOf
import org.koin.ktor.ext.getKoin
import org.koin.ktor.plugin.Koin
import org.koin.logger.slf4jLogger
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import kotlin.system.exitProcess

/** The port this service listens on when nothing says otherwise. */
const val SERVER_PORT = 8310

/** The header a caller uses to say which project it is asking for. */
const val PROJECT_HEADER = "X-Codex-Project"

private val logger = LoggerFactory.getLogger("org.dependencyskills.codex.server.Application")

/**
 * The door, as a service.
 *
 * **One process per machine, not one per project.** The store is machine-level ([ADR-0012]) and
 * SQLite wants a single writer, so a server per open project means N processes contending over one
 * database and N copies of a 64 MB encoder resident at once. This is the shape the thing actually
 * gets deployed in: a service on a port, started once, that outlives any particular editor window.
 *
 * The stdio entry point in [main] of `Main.kt` is kept beside it. It is the same [codexServer] over
 * a different transport, and nothing between here and the store knows which one it is answering —
 * that is why [CodexQueries] has no protocol in it.
 *
 * Because one service answers for many projects, **scope cannot come from the process** the way it
 * does over stdio, where the client launches a child in the project directory. It comes per
 * request instead, from [PROJECT_HEADER]. A request that does not say which project it is gets an
 * empty scope, which returns nothing and says why — never everything. That default is the whole
 * containment boundary: treating a missing scope as "search the machine" would hand one project's
 * dependency graph to another and look exactly like the tool working.
 */
fun main(args: Array<String>) {
    System.setProperty("java.awt.headless", "true")

    // `--store` ahead of the environment ahead of the default, so an operator can put the store
    // wherever they keep data without touching a build or exporting anything.
    val store = option(args, "store")?.let { Path.of(it) } ?: storeFile()
    reportLegacyStore(store)

    val port = System.getenv("PORT")?.toIntOrNull() ?: SERVER_PORT
    // Loopback unless told otherwise. This answers questions about one machine's dependency
    // graph, and 0.0.0.0 would put that on whatever network the laptop is joined to - a coffee
    // shop as readily as an office. A container deployment sets HOST explicitly, which is a
    // visible act in a compose file rather than a default nobody chose.
    val host = System.getenv("HOST") ?: "127.0.0.1"

    try {
        logger.info("listening on {}:{}", host, port)
        embeddedServer(
            factory = Netty,
            port = port,
            host = host,
            module = { codexModule(store) },
        ).start(wait = true)
    } catch (e: Throwable) {
        // Loudly, and with a non-zero exit. A service that fails to start and lingers is worse
        // than one that dies: whatever supervises it cannot tell the difference from healthy.
        logger.error("CRITICAL: failed to start the codex server on $host:$port", e)
        exitProcess(1)
    }
}

/**
 * Everything the service installs, separate from starting it so a test can run the same
 * application without binding a port.
 */
fun Application.codexModule(store: Path = storeFile()) {
    install(Koin) {
        slf4jLogger(level = Level.ERROR)
        modules(codexRuntimeModule(store))
    }

    // Resolved here rather than per request. `createdAtStart` already opened them; this is what
    // makes a failure to open land on whoever started the service, and it is where the service
    // says what it came up with - a server that starts silently with no index looks exactly like
    // one that is working.
    val codex = getKoin().get<Codex>()
    val vectors = getKoin().getOrNull<VectorSearch>()
    logger.info("store {}", store)
    logger.info(
        if (vectors == null) "no vector index; answering lexically"
        else "vector index open, encoder ${vectors.encoderName}",
    )

    // Released when the server stops, rather than in a JVM shutdown hook. A hook fires on the way
    // out of the process and says nothing about whether this application is still serving.
    monitor.subscribe(ApplicationStopped) {
        runCatching { vectors?.close() }
        runCatching { codex.close() }
    }

    install(Compression) {
        gzip { priority = 1.0 }
        deflate {
            priority = 10.0
            minimumSize(1024)
        }
    }

    install(StatusPages) {
        exception<Throwable> { call: ApplicationCall, cause: Throwable ->
            logger.error("unhandled exception", cause)
            // The message, not the exception. A stack trace or a store internal in a response
            // tells a caller about this machine and tells a developer nothing they asked for -
            // the same rule the query layer already holds for answers.
            call.respondText(
                text = "500: the codex server failed to answer that",
                status = HttpStatusCode.InternalServerError,
            )
        }
    }

    routing {
        // Enough to tell a supervisor the process is up and the store is open, and nothing about
        // what is in it.
        get("/health") {
            call.respondText("ok ${if (vectors == null) "lexical" else "vector"}")
        }

        // Where a build says what it resolved. The build knows its own coordinates and nothing
        // else; this knows the store and nothing about build systems. That is the whole contract,
        // and it is the same one a Maven or npm plugin would use without changing anything here.
        post("/projects") {
            val body = runCatching { Json.decodeFromString<Registration>(call.receiveText()) }
                .getOrElse {
                    // A build must never be failed by this, but it must also never be told a
                    // malformed report was accepted.
                    logger.warn("rejected a malformed project registration: {}", it.message)
                    call.respondText("400: malformed registration", status = HttpStatusCode.BadRequest)
                    return@post
                }
            if (body.path.isBlank()) {
                call.respondText("400: path is required", status = HttpStatusCode.BadRequest)
                return@post
            }
            // The name defaults to the path, which cannot collide. Anything else - a root project
            // name, a directory name - would merge two unrelated projects that happen to share it,
            // making one project's entries reachable from another that never depended on them.
            val name = body.name?.trim()?.takeIf { it.isNotEmpty() } ?: body.path
            val coordinates = body.coordinates.mapNotNull { it.toCoordinate() }
            codex.recordProject(body.path, name, body.ecosystem, coordinates)
            logger.info(
                "registered {} as '{}' with {} coordinates", body.path, name, coordinates.size,
            )
            call.respondText("ok ${coordinates.size}")
        }
    }

    // Stateless: every request carries its own scope, so there is no session worth keeping. It
    // also means no per-session cleanup to get wrong, which is what stranded the stdio server
    // when neither Server.onClose nor ServerSession.onClose fired.
    mcpStatelessStreamableHttp(path = "/mcp") {
        // A fresh CodexQueries from the container for this request, carrying this caller's scope
        // and nobody else's.
        val scope = scopeFor(codex, call.request.header(PROJECT_HEADER))
        codexServer(getKoin().get<CodexQueries> { parametersOf(scope) })
    }
}

/** `--name value`, or null. */
private fun option(args: Array<String>, name: String): String? =
    args.indexOf("--$name").takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }

/**
 * Says so when a store exists where the old one used to live.
 *
 * The store moved out of `~/.gradle/` when the plugin stopped owning it. Nothing is migrated —
 * the store is reproducible, and moving somebody's database for them is a bigger promise than
 * saying where it is. But starting silently against an empty store when a populated one is sitting
 * a directory away is indistinguishable from working, which is the failure this project keeps
 * re-learning.
 */
private fun reportLegacyStore(store: Path) {
    if (Files.exists(store)) return
    val legacy = runCatching { CodexLocation.legacyDatabaseFile() }.getOrNull() ?: return
    if (!Files.exists(legacy)) return
    logger.warn("a store exists at the old location and is NOT being used: {}", legacy)
    logger.warn("this service is using {} - move the old one there, or delete it", store)
}

/** What a build reports. The plugin sends this and nothing else. */
@Serializable
internal data class Registration(
    @SerialName("path") val path: String,
    @SerialName("name") val name: String? = null,
    @SerialName("ecosystem") val ecosystem: String = "maven",
    @SerialName("coordinates") val coordinates: List<String> = emptyList(),
) {
    companion object {
        /** `maven:group:artifact:version` — the ecosystem, then the coordinate as that ecosystem writes it. */
        internal fun String.toCoordinate(): Coordinate? {
            val ecosystem = substringBefore(':', "")
            val value = substringAfter(':', "")
            return if (ecosystem.isBlank() || value.isBlank()) null else Coordinate(ecosystem, value)
        }
    }
}

private fun String.toCoordinate(): Coordinate? = with(Registration) { this@toCoordinate.toCoordinate() }

/**
 * The scope for one request, from the project the caller named.
 *
 * A caller that names no project gets an **empty** scope rather than an open one, and it says which
 * it was so the answer can explain itself instead of looking like a genuine miss.
 *
 * This trusts the caller to name its own project. That is worth stating plainly rather than
 * implying: a local agent that names another project's directory gets that project's scope. It is
 * a boundary against accident and misconfiguration, not against a hostile process on this machine
 * — which could ask this service the same question directly.
 */
internal fun scopeFor(codex: Codex, project: String?): ProjectScope {
    if (project.isNullOrBlank()) {
        return ProjectScope(emptySet(), "no $PROJECT_HEADER header on the request")
    }
    return ProjectScope.read(codex, project)
}
