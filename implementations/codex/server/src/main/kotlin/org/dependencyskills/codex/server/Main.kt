package org.dependencyskills.codex.server

import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import kotlin.system.exitProcess

/**
 * The door, over stdio — as a relay to the service, not as a second implementation of it.
 *
 * **This opens no store and answers no question.** It reads a JSON-RPC message from stdin, hands it
 * to the service over HTTP, and writes the reply to stdout. That is the whole program.
 *
 * It exists because some clients can only launch a child process. What it must not become is a
 * second way to reach the codex: when this held its own [Codex] and its own scope, there were two
 * components that knew where the store lived and two that knew how a project's scope was found, and
 * they could disagree without anything noticing. Now exactly one does.
 *
 * The cost is real and was accepted deliberately: **stdio stops working when the service is down.**
 * It used to work regardless, because it read the store itself. If that matters more than having
 * one source of truth, the answer is to drop this transport rather than to give it back its own
 * copy of everything.
 *
 * Scope comes from the working directory the client launched this in, sent as [PROJECT_HEADER] on
 * every forwarded request — the same header an HTTP client sends for itself. The service resolves
 * it the same way either way.
 *
 * Everything this prints on stdout is protocol. Diagnostics go to stderr, and there is a specific
 * reason to be careful: a stray `println` corrupts the JSON-RPC stream and presents as the client
 * failing to start the server, with nothing in the message pointing here.
 */
fun main(args: Array<String>) {
    fun option(name: String): String? =
        args.indexOf("--$name").takeIf { it >= 0 && it + 1 < args.size }?.let { args[it + 1] }

    val service = option("service")
        ?: System.getenv("DSCODEX_SERVICE")
        ?: "http://127.0.0.1:$SERVER_PORT"
    val project = option("project")
        ?: Path.of("").toAbsolutePath().toString()

    val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()

    // Checked before a client is told the server started. A relay that accepts a session and only
    // then discovers it has nothing to relay to reports the failure as a broken tool call, which
    // says nothing about the actual problem.
    val health = runCatching {
        http.send(
            HttpRequest.newBuilder(URI.create("$service/health")).GET()
                .timeout(Duration.ofSeconds(5)).build(),
            HttpResponse.BodyHandlers.ofString(),
        ).statusCode()
    }.getOrNull()
    if (health != 200) {
        System.err.println("dependencyskills: no codex service at $service")
        System.err.println("dependencyskills: start it, or pass --service <url>")
        exitProcess(1)
    }
    System.err.println("dependencyskills: relaying to $service, as project $project")

    val out = System.out.bufferedWriter()
    System.`in`.bufferedReader().forEachLine { line ->
        if (line.isBlank()) return@forEachLine
        val reply = runCatching {
            http.send(
                HttpRequest.newBuilder(URI.create("$service/mcp"))
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json, text/event-stream")
                    .header(PROJECT_HEADER, project)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.ofString(line))
                    .build(),
                HttpResponse.BodyHandlers.ofString(),
            ).body()
        }.getOrElse { failure ->
            System.err.println("dependencyskills: ${failure.message}")
            // A notification has no id and expects no reply; inventing one would corrupt the
            // stream. Anything else gets an error it can act on rather than silence.
            if ("\"id\"" !in line) "" else
                """{"jsonrpc":"2.0","id":null,"error":{"code":-32603,"message":"the codex service is unreachable"}}"""
        }
        if (reply.isNotBlank()) {
            out.write(reply)
            out.newLine()
            out.flush()
        }
    }
}
