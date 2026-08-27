package org.dependencyskills.codex.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SearchTest {

    private fun entry(symbol: String, doc: String, signature: String = "fun ${symbol.substringAfterLast('.')}()") =
        NewEntry(
            symbol = symbol, signature = signature, doc = doc,
            lang = "kotlin", docFormat = "kdoc",
            provenance = Provenance(extractor = "test"),
        )

    private val ktor = Coordinate("maven", "io.ktor:ktor-client-core:3.5.2")
    private val coroutines = Coordinate("maven", "org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0")
    private val unrelated = Coordinate("maven", "com.example:private-library:1.0")

    private fun stocked(dir: Path): Codex = Codex.open(dir.resolve("c.db")).apply {
        put(ktor, listOf(
            entry(
                "io.ktor.client.plugins.HttpRequestRetryConfig.retryIf",
                "Specifies a predicate deciding whether a failed request should be retried, with an exponential backoff between attempts.",
            ),
            entry(
                "io.ktor.server.response.respondRedirect",
                "Responds with a redirect to the given URL, permanently or temporarily.",
            ),
        ))
        put(coroutines, listOf(
            entry(
                "kotlinx.coroutines.sync.Mutex",
                "A mutual exclusion for coroutines, so that only one coroutine at a time can be inside a critical section.",
            ),
        ))
    }

    // -- ranking ------------------------------------------------------------------------------

    @Test fun `a need in plain words returns ranked candidates`(@TempDir dir: Path) {
        stocked(dir).use { codex ->
            val results = codex.search(
                "how do I make a failed network call try again after waiting a bit",
                setOf(ktor, coroutines),
            )
            assertEquals(
                "io.ktor.client.plugins.HttpRequestRetryConfig.retryIf",
                results.hits.first().entry.symbol,
            )
            assertTrue(results.hits.first().score > 0.0, "score reads higher-is-better")
            assertTrue(
                results.hits.zipWithNext().all { (a, b) -> a.score >= b.score },
                "hits come back ranked",
            )
        }
    }

    @Test fun `an identifier is reachable from a need that never spells it`(@TempDir dir: Path) {
        // `respondRedirect` is one token to the tokenizer. Nothing written in prose could match
        // it unless the symbol is split into words before it is indexed.
        stocked(dir).use { codex ->
            val results = codex.search("send whoever asked to a different address", setOf(ktor))
            assertEquals("io.ktor.server.response.respondRedirect", results.hits.first().entry.symbol)
        }
    }

    @Test fun `results carry what is safe to show`(@TempDir dir: Path) {
        stocked(dir).use { codex ->
            val hit = codex.search("retry", setOf(ktor)).hits.first()
            assertTrue(hit.entry.symbol.isNotBlank())
            assertTrue(hit.entry.signature.isNotBlank())
            // The raw documentation is not on the type at all, so it cannot be returned by
            // accident. The rewrite is what may be displayed, and nothing has written one.
            assertEquals(null, hit.entry.rewrite)
        }
    }

    // -- the boundary -------------------------------------------------------------------------

    @Test fun `an entry outside the supplied coordinates can never be returned`(@TempDir dir: Path) {
        // Two projects on one machine. The store is shared; the answers must not be.
        Codex.open(dir.resolve("c.db")).use { codex ->
            codex.put(ktor, listOf(entry("io.ktor.client.HttpClient", "Performs HTTP requests against a server.")))
            codex.put(unrelated, listOf(entry(
                "com.example.secret.HttpTunnel",
                "Performs HTTP requests through the internal proxy using the shared credentials.",
            )))

            val asKtorProject = codex.search("perform an http request", setOf(ktor))
            assertEquals(listOf("io.ktor.client.HttpClient"), asKtorProject.hits.map { it.entry.symbol })

            // The same need, from the project that does depend on it, still finds it - so the
            // entry is genuinely indexed and the absence above is the scope and nothing else.
            val asOtherProject = codex.search("perform an http request", setOf(unrelated))
            assertEquals(listOf("com.example.secret.HttpTunnel"), asOtherProject.hits.map { it.entry.symbol })
        }
    }

    @Test fun `an empty scope returns nothing rather than everything`(@TempDir dir: Path) {
        stocked(dir).use { codex ->
            assertTrue(codex.search("retry", emptySet()).hits.isEmpty())
        }
    }

    @Test fun `a hit does not name coordinates the caller did not ask about`(@TempDir dir: Path) {
        // The same declaration published by two artifacts collapses to one entry owned by both.
        // Reporting both owners would leak, through the answer, what else this machine indexes.
        Codex.open(dir.resolve("c.db")).use { codex ->
            val shared = entry("io.ktor.http.ContentType", "Represents a value of the Content-Type header.")
            codex.put(ktor, listOf(shared))
            codex.put(unrelated, listOf(shared))

            val hit = codex.search("content type header", setOf(ktor)).hits.single()
            assertEquals(setOf(ktor), hit.entry.coordinates)
        }
    }

    @Test fun `the limit is applied after the scope, not before it`(@TempDir dir: Path) {
        // The trap this ordering avoids: filter after limiting and the out-of-scope entries eat
        // the result slots, so a caller sees fewer matches the more its neighbours have indexed.
        Codex.open(dir.resolve("c.db")).use { codex ->
            codex.put(unrelated, (1..50).map { entry("com.example.Noise$it", "A retry of a request that failed.") })
            codex.put(ktor, listOf(entry("io.ktor.Retry", "A retry of a request that failed.")))
            val results = codex.search("retry of a request that failed", setOf(ktor), limit = 5)
            assertEquals(listOf("io.ktor.Retry"), results.hits.map { it.entry.symbol })
        }
    }

    // -- the three outcomes --------------------------------------------------------------------

    @Test fun `nothing matched is distinguishable from nothing indexed`(@TempDir dir: Path) {
        stocked(dir).use { codex ->
            val nothingMatched = codex.search("quantum chromodynamics", setOf(ktor, coroutines))
            assertTrue(nothingMatched.hits.isEmpty())
            assertEquals(setOf(ktor, coroutines), nothingMatched.searched)
            assertTrue(nothingMatched.notHarvested.isEmpty())
            assertTrue(nothingMatched.answerIsComplete, "an empty result here really does mean absent")

            val notIndexed = Coordinate("maven", "com.example:never-seen:1.0")
            val nothingIndexed = codex.search("quantum chromodynamics", setOf(notIndexed))
            assertEquals(setOf(notIndexed), nothingIndexed.notHarvested)
            assertFalse(nothingIndexed.answerIsComplete, "nobody has indexed this; absence proves nothing")
        }
    }

    @Test fun `a coordinate with no source reads differently from one nobody has looked at`(@TempDir dir: Path) {
        stocked(dir).use { codex ->
            val sourceless = Coordinate("maven", "com.example:no-sources:1.0")
            val queued = Coordinate("maven", "com.example:queued:1.0")
            codex.harvestState(sourceless, HarvestState.NoSource)
            codex.seen(queued)

            val results = codex.search("retry", setOf(ktor, sourceless, queued))
            assertEquals(setOf(ktor), results.searched)
            assertEquals(setOf(sourceless), results.noSource)
            assertEquals(setOf(queued), results.notHarvested)
            // Re-asking will help for one of them and never for the other. A caller that cannot
            // tell them apart either retries for ever or gives up on work still to be done.
            assertFalse(results.answerIsComplete)
        }
    }

    @Test fun `a failed harvest counts as not yet harvested`(@TempDir dir: Path) {
        stocked(dir).use { codex ->
            val failed = Coordinate("maven", "com.example:broken:1.0")
            codex.harvestState(failed, HarvestState.Failed)
            assertEquals(setOf(failed), codex.search("retry", setOf(failed)).notHarvested)
        }
    }

    // -- the index itself -----------------------------------------------------------------------

    @Test fun `a need of only punctuation matches nothing rather than failing`(@TempDir dir: Path) {
        stocked(dir).use { codex ->
            assertTrue(codex.search("?? -- **", setOf(ktor)).hits.isEmpty())
        }
    }

    @Test fun `a need containing FTS syntax is searched for, not executed`(@TempDir dir: Path) {
        // Unquoted, `NOT` and `*` are operators and an odd quote is a syntax error. A caller
        // typing an English sentence must never get either.
        stocked(dir).use { codex ->
            listOf("retry NOT redirect", "how do I retry\" OR *", "a AND b OR (c)").forEach {
                codex.search(it, setOf(ktor, coroutines))
            }
        }
    }

    @Test fun `the same entry indexed twice is found once`(@TempDir dir: Path) {
        stocked(dir).use { codex ->
            val duplicate = entry("io.ktor.http.HttpMethod", "An HTTP method, such as GET or POST.")
            codex.put(ktor, listOf(duplicate))
            codex.put(ktor, listOf(duplicate))
            val hits = codex.search("http method such as get", setOf(ktor)).hits
            assertEquals(1, hits.count { it.entry.symbol == "io.ktor.http.HttpMethod" })
        }
    }

    @Test fun `symbolText splits an identifier into words and keeps the original`() {
        val words = symbolText("io.ktor.server.response.respondOutputStream").split(' ')
        assertTrue("respondOutputStream" in words)
        assertTrue("respond" in words)
        assertTrue("Output" in words)
        assertTrue("Stream" in words)
        assertTrue("ktor" in words)
    }

    @Test fun `symbolText keeps an acronym together`() {
        val words = symbolText("io.ktor.http.HTTPStatusCode").split(' ')
        assertTrue("HTTP" in words, words.toString())
        assertTrue("Status" in words)
    }
}
