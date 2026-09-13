package org.dependencyskills.codex.server

import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.runBlocking
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.NewEntry
import org.dependencyskills.codex.core.Provenance
import org.junit.jupiter.api.AfterEach
import org.koin.core.context.stopKoin
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The HTTP surface, exercised as installed.
 *
 * **These did not exist, and that is the point of adding them.** Every route was a thin render
 * nobody ran — the operator-facing half of the service was unverified while its query layer was
 * heavily tested. A render that compiles and reports the wrong thing is exactly the failure this
 * project keeps re-learning: a green build over a surface nothing looked at.
 */
class RoutesTest {

    private val acme = Coordinate("maven", "com.example.acme:acme-core:1.0.0")

    // Koin installs into a global context, so one test's container would otherwise still be
    // standing when the next one starts.
    @AfterEach fun tearDown() = stopKoin()

    private fun store(): Path = createTempDirectory("routes").resolve("codex.db")

    private fun seeded(store: Path): Path {
        Codex.open(store).use { codex ->
            codex.put(
                acme,
                listOf(
                    NewEntry(
                        symbol = "com.example.acme.run",
                        signature = "fun run(input: String): String",
                        doc = "Runs the documented thing over the input it is given.",
                        lang = "kotlin",
                        docFormat = "kdoc",
                        provenance = Provenance(extractor = "tree-sitter", summariser = "model-1.0"),
                        rewrite = "Runs the documented thing over the input it is given.",
                    )
                ),
            )
        }
        return store
    }

    // -- operator surface --------------------------------------------------------------------

    @Test
    fun `health reports whether answers are ranked or lexical`() = testApplication {
        val store = seeded(store())
        application { codexModule(store, CodexConfig.load(store)) }
        val body = client.get("/health").bodyAsText()
        assertTrue(body.startsWith("ok "), "expected a health line, got: $body")
        // A store with no vector index answers lexically, and says so rather than looking healthy
        // in the same words as one that is fully indexed.
        assertTrue("lexical" in body, body)
        assertTrue("pending=" in body, body)
    }

    @Test
    fun `indexing reports queue depth and whether a pass is running`() = testApplication {
        val store = seeded(store())
        application { codexModule(store, CodexConfig.load(store)) }
        val body = client.get("/indexing").bodyAsText()
        assertTrue("pending=" in body && "running=" in body && "paused=" in body, body)
    }

    @Test
    fun `pausing and resuming indexing is reflected in what the service reports`() = testApplication {
        val store = seeded(store())
        application { codexModule(store, CodexConfig.load(store)) }
        client.post("/indexing/pause")
        assertTrue("paused=true" in client.get("/indexing").bodyAsText())
        client.post("/indexing/resume")
        assertTrue("paused=false" in client.get("/indexing").bodyAsText())
    }

    // -- the usage record (#33) --------------------------------------------------------------

    @Test
    fun `usage reports counts, and never the text of what was asked`() = testApplication {
        val store = seeded(store())
        application { codexModule(store, CodexConfig.load(store)) }

        // Ask something revealing through the query layer the same way a tool call would.
        val secret = "migrating the billing ledger off the vendor SDK"
        val queries = CodexQueries(
            Codex.open(store), ProjectScope.of(acme), vectors = null, use = UseRecord.open(store),
        )
        queries.search(secret)

        val body = client.get("/usage").bodyAsText()
        assertTrue("searches" in body, body)
        assertTrue("after a search" in body, body)
        // The one thing this endpoint must never do. A need in a developer's own words describes
        // what is being built, and it is counted rather than rendered.
        assertFalse(secret in body, "the endpoint rendered the need text")
        assertFalse("billing" in body, "the endpoint rendered part of the need text")
    }

    @Test
    fun `usage says recording is off rather than reporting zero`() = testApplication {
        val store = store()
        CodexConfig.file(store).parent.toFile().mkdirs()
        CodexConfig.file(store).writeText("[usage]\nrecord = false\n")
        seeded(store)
        application { codexModule(store, CodexConfig.load(store)) }

        val body = client.get("/usage").bodyAsText()
        // Zeroes would read as "nobody has asked anything", which is a different fact and the
        // one this service keeps having to distinguish.
        assertTrue("recording is off" in body, body)
        assertTrue("config.toml" in body, "the off switch has to be findable from here: $body")
    }

    // -- what a build reports ----------------------------------------------------------------

    @Test
    fun `a project registers its resolved coordinates, and they become searchable`() = testApplication {
        val store = seeded(store())
        application { codexModule(store, CodexConfig.load(store)) }

        val response = client.post("/projects") {
            setBody("""{"path":"/work/mine","ecosystem":"maven","coordinates":["maven:${acme.value}"]}""")
        }
        assertEquals(HttpStatusCode.OK, response.status)

        // Registering is not the point; being answerable afterwards is. A build reports its
        // dependencies so that a query scoped to that project can see them.
        Codex.open(store).use { codex ->
            val scope = ProjectScope.read(codex, "/work/mine")
            assertFalse(scope.isEmpty, "the project should now have a scope")
            assertTrue(CodexQueries(codex, scope).search("documented thing").candidates.isNotEmpty())
        }
    }

    @Test
    fun `a malformed registration is refused, and never reported as accepted`() = testApplication {
        // A build must not be failed by this route, and must not be told a broken report landed.
        // Both halves matter: the second is how a project ends up permanently unsearchable while
        // every build looks green.
        val store = seeded(store())
        application { codexModule(store, CodexConfig.load(store)) }
        assertEquals(HttpStatusCode.BadRequest, client.post("/projects") { setBody("not json") }.status)
        assertEquals(
            HttpStatusCode.BadRequest,
            client.post("/projects") { setBody("""{"path":"  "}""") }.status,
            "a blank path names no project and cannot be recorded",
        )
    }

    // -- the scope header, which is the containment boundary ---------------------------------

    @Test
    fun `the project header decides what a caller can see, and its absence shows nothing`() {
        // PROJECT_HEADER is how a request says whose scope it is asking in, so it is the one
        // header that must not be ambiguous or optional. Named for this project rather than the
        // generic word precisely so it cannot collide with another tool's header and hand one
        // project's dependency graph to a client asking about a different one.
        testApplication {
            val store = seeded(store())
            Codex.open(store).use { it.recordProject("/work/mine", "mine", "maven", listOf(acme)) }
            application { codexModule(store, CodexConfig.load(store)) }

            fun call(header: String?) = runBlocking {
                client.post("/mcp") {
                    // The MCP transport validates Host as a DNS-rebinding guard, so a request
                    // without one is refused before scope is even considered.
                    headers.append("Host", "127.0.0.1")
                    headers.append("Content-Type", "application/json")
                    headers.append("Accept", "application/json, text/event-stream")
                    header?.let { headers.append(PROJECT_HEADER, it) }
                    setBody(
                        """{"jsonrpc":"2.0","id":1,"method":"tools/call","params":""" +
                            """{"name":"search","arguments":{"need":"documented thing"}}}"""
                    )
                }.bodyAsText()
            }

            // With the header, the project's own entry is reachable.
            val scoped = call("/work/mine")
            assertTrue("com.example.acme.run" in scoped, "scoped search should answer: ${scoped.take(300)}")

            // Without it, nothing — and the reason is said rather than being an empty list that
            // reads as "this project has nothing".
            val anonymous = call(null)
            assertFalse("com.example.acme.run" in anonymous, "an unscoped request must see nothing")
            assertTrue(PROJECT_HEADER in anonymous, "and must say which header was missing")

            // A project nobody registered is not answered from somebody else's scope.
            assertFalse("com.example.acme.run" in call("/work/never-registered"))
        }
    }

    @Test
    fun `an unknown route is not found rather than an error`() = testApplication {
        val store = seeded(store())
        application { codexModule(store, CodexConfig.load(store)) }
        assertEquals(HttpStatusCode.NotFound, client.get("/nothing-here").status)
    }
}
