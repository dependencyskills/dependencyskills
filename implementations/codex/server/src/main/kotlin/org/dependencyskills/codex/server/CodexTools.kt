package org.dependencyskills.codex.server

import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What the server calls itself when a client asks. */
const val SERVER_NAME = "dependencyskills"
const val SERVER_VERSION = "0.0.1"

/**
 * The two tools, defined once for every transport that serves them.
 *
 * There are two transports — a long-lived HTTP service and a per-project stdio child — and they
 * must offer the **same** tools with the same descriptions and the same schemas. Written out at
 * each entry point instead, they drift: a description improves in one, a limit changes in the
 * other, and an agent gets different answers depending on how its client happened to connect.
 * Neither transport appears below, which is the point — this layer has no protocol in it.
 *
 * [queries] is passed in rather than held, because scope is not a property of the process. The
 * HTTP service is machine-level and answers for many projects, so it builds one of these per
 * request against that caller's scope; the stdio child builds one for its whole life.
 */
fun codexServer(queries: CodexQueries): Server {
    val server = Server(
        Implementation(name = SERVER_NAME, version = SERVER_VERSION),
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

    return server
}
