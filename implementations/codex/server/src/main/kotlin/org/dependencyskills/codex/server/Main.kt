package org.dependencyskills.codex.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.CodexLocation
import java.nio.file.Path

/**
 * The door, over stdio.
 *
 * **stdio because the store is local and machine-level.** An agent's client launches this as a
 * child process for the project it is working in, which is also how it comes to know its scope —
 * the process is per-project even though the store is not. A shared or remote deployment is a
 * different transport over the same [CodexQueries], and nothing above the transport would change;
 * that is why the query layer has no protocol in it.
 *
 * Everything this prints on stdout is protocol. Diagnostics go to stderr, and there is a specific
 * reason to be careful about it: a stray `println` corrupts the JSON-RPC stream and presents as
 * the client failing to start the server, with nothing in the message pointing here.
 */
fun main(args: Array<String>) = runBlocking {
    val options = args.toList()
    fun option(name: String): String? =
        options.indexOf("--$name").takeIf { it >= 0 && it + 1 < options.size }?.let { options[it + 1] }

    val storeFile = option("store")?.let { Path.of(it) } ?: CodexLocation.databaseFile()
    // Beside the PROJECT, not the store. The store is machine-level and shared across every
    // project on it; a scope belongs to one project, so looking for it next to the store would
    // find one project's scope and serve it to all of them. A client launches this with the
    // project as its working directory, which is how it comes to be in the right place.
    val scopeFile = option("scope")?.let { Path.of(it) }
        ?: Path.of("").toAbsolutePath().resolve(ProjectScope.DEFAULT_PATH)

    val scope = ProjectScope.read(scopeFile)
    // stderr, not stdout. Said at all because a server that starts silently with nothing in scope
    // looks identical to one that is working.
    System.err.println("dependencyskills: store $storeFile")
    System.err.println(
        if (scope.isEmpty) "dependencyskills: NOTHING IN SCOPE (${scope.source}) — every answer will be empty"
        else "dependencyskills: ${scope.coordinates.size} coordinates in scope, from ${scope.source}",
    )

    val codex = Codex.open(storeFile)
    val queries = CodexQueries(codex, scope)
    val server = Server(
        Implementation(name = "dependencyskills", version = "0.0.1"),
        ServerOptions(capabilities = ServerCapabilities(tools = ServerCapabilities.Tools(listChanged = true))),
    )

    server.addTool(
        name = "search",
        description =
            "Find a capability in this project's own dependencies, by describing what you need in " +
                "plain words. Returns the libraries that already do it, so you can call one " +
                "instead of writing it. Search before writing anything a library might already " +
                "provide. Results are limited to what this project actually depends on.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("need", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("What you need, in the words you would use to ask a colleague."))
                })
                put("limit", buildJsonObject {
                    put("type", JsonPrimitive("integer"))
                    put("description", JsonPrimitive("How many candidates to return. Default 10, maximum 50."))
                })
            },
            required = listOf("need"),
        ),
    ) { request ->
        val need = request.arguments?.get("need")?.jsonPrimitive?.content.orEmpty()
        val limit = request.arguments?.get("limit")?.jsonPrimitive?.content?.toIntOrNull() ?: 10
        CallToolResult(content = listOf(TextContent(Rendering.render(queries.search(need, limit)))))
    }

    server.addTool(
        name = "get",
        description =
            "Look up one capability by its exact symbol, as returned by search. Use this when you " +
                "already know the name and want its signature.",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("symbol", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("The fully qualified symbol, exactly as search returned it."))
                })
            },
            required = listOf("symbol"),
        ),
    ) { request ->
        val symbol = request.arguments?.get("symbol")?.jsonPrimitive?.content.orEmpty()
        CallToolResult(content = listOf(TextContent(Rendering.render(queries.get(symbol)))))
    }

    val transport = StdioServerTransport(
        System.`in`.asSource().buffered(),
        System.out.asSink().buffered(),
    )
    // Wait on the TRANSPORT closing, which is what happens when the client's stdin reaches EOF.
    //
    // Neither `Server.onClose` nor `ServerSession.onClose` fires here - `Server` outlives any
    // session, and the session's callback did not run on a closed stream either. Measured by
    // hand: with those, the process sat forever after its client went away, which is how a
    // machine collects a dozen orphaned servers nobody can account for. The transport is the
    // thing that actually notices, so it is the thing to wait on.
    val done = Job()
    transport.onClose { done.complete() }
    server.createSession(transport)
    done.join()
    codex.close()
}
