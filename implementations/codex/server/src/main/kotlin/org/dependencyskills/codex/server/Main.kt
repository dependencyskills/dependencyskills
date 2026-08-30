package org.dependencyskills.codex.server

import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.io.asSink
import kotlinx.io.asSource
import kotlinx.io.buffered
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.CodexLocation
import org.dependencyskills.codex.index.VectorSearch
import java.nio.file.Path

/**
 * The door, over stdio.
 *
 * **The service in `Application.kt` is how this is meant to run.** This is kept beside it for the
 * case a client can only launch a child process: same tools from [codexServer], same [CodexQueries]
 * underneath, different transport. Nothing between here and the store knows which one it is.
 *
 * Scope comes from the working directory here, because the client launches one of these per
 * project. The service cannot do that - it answers for every project on the machine - so it takes
 * the project per request instead.
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
    // Null when nothing has built an index yet, which is the ordinary state until the service
    // exists. Said out loud on stderr, because "answers are worse than they should be" is not
    // something a caller can see.
    val vectors = VectorSearch.openIfBuilt(storeFile)
    System.err.println(
        if (vectors == null) "dependencyskills: no vector index; answering lexically"
        else "dependencyskills: vector index open, encoder ${vectors.encoderName}",
    )
    val queries = CodexQueries(codex, scope, vectors)
    val server = codexServer(queries)

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
    vectors?.close()
    codex.close()
}
