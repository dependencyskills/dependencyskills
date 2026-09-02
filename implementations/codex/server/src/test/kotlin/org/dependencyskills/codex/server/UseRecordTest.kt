package org.dependencyskills.codex.server

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.NewEntry
import org.dependencyskills.codex.core.Provenance
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * What agents actually ask for, recorded (#33).
 *
 * Two things are being protected at once and they pull opposite ways: the record has to be
 * informative enough to answer the questions it exists for, and it must never cost a caller an
 * answer. Most of what is asserted below is the second.
 */
class UseRecordTest {

    private val acme = Coordinate("maven", "com.example.acme:acme-core:1.0.0")
    private val other = Coordinate("maven", "com.example.other:other-core:1.0.0")

    private fun store() = createTempDirectory("use").resolve("codex.db")

    private fun entry(symbol: String) = NewEntry(
        symbol = "com.example.acme.$symbol",
        signature = "fun $symbol(input: String): String",
        doc = "Runs the documented thing over the input it is given.",
        lang = "kotlin",
        docFormat = "kdoc",
        provenance = Provenance(extractor = "tree-sitter", summariser = "model-1.0"),
        rewrite = "Runs the documented thing over the input it is given.",
    )

    // -- what it records -------------------------------------------------------------------------

    @Test
    fun `a search and a get are both recorded, and the get is linked to the search`() {
        // The link is the only usage signal available. The codex cannot see whether the agent
        // wrote the code, kept it, or compiled it — but coming back for the whole entry after a
        // search is observable, and it is the closest thing to "the answer was used".
        val store = store()
        val record = assertNotNull(UseRecord.open(store))
        record.use { r ->
            r.search("/work/mine", "substitute variables", 3, candidates = 2, notHarvested = 0,
                ranking = UseRecord.Ranking.Vector, coordinates = listOf(acme))
            r.get("/work/mine", "com.example.acme.run", found = true, coordinates = listOf(acme))

            val summary = r.summary()
            assertEquals(1, summary.searches)
            assertEquals(1, summary.gets)
            assertEquals(1, summary.getsAfterSearch, "the get followed a search in the same project")
            assertEquals(listOf(acme.value to 2), summary.askedAbout)
        }
    }

    @Test
    fun `a get with no search before it is not attributed to one`() {
        // An agent that goes straight to a symbol did not use a search result, and recording it as
        // though it had would inflate the one number this is here to produce.
        val record = assertNotNull(UseRecord.open(store()))
        record.use { r ->
            r.get("/work/mine", "com.example.acme.run", found = true, coordinates = listOf(acme))
            assertEquals(1, r.summary().gets)
            assertEquals(0, r.summary().getsAfterSearch)
        }
    }

    @Test
    fun `a search that matched nothing is distinct from one that could not look`() {
        // The distinction the whole record exists for. "Nothing matched your words" says something
        // about the corpus; "none of your dependencies is indexed" says nothing about it at all,
        // and counting them together would make the corpus look worse than it is.
        val record = assertNotNull(UseRecord.open(store()))
        record.use { r ->
            r.search("/work/mine", "matched nothing", 5, candidates = 0, notHarvested = 0,
                ranking = UseRecord.Ranking.Vector, coordinates = emptyList())
            r.search("/work/mine", "could not look", 5, candidates = 0, notHarvested = 5,
                ranking = UseRecord.Ranking.Lexical, coordinates = emptyList())

            val summary = r.summary()
            assertEquals(2, summary.searches)
            assertEquals(1, summary.answeredNothing, "one search looked and found nothing")
            assertEquals(1, summary.nothingIndexed, "the other could not look")
            assertEquals(1, summary.lexicalFallback)
        }
    }

    // -- what it must not cost -------------------------------------------------------------------

    @Test
    fun `recording never fails a query`() {
        // The answer is the product; the record is a measurement about it. A locked file or a full
        // disk must lose the measurement, not the answer.
        val store = store()
        val record = assertNotNull(UseRecord.open(store))
        record.close()                                   // every write from here on will throw
        record.search("/work/mine", "after close", 1, 1, 0, UseRecord.Ranking.Vector, listOf(acme))
        record.get("/work/mine", "com.example.acme.run", found = true, coordinates = listOf(acme))
        // Reaching this line is the assertion: neither call propagated.
    }

    @Test
    fun `a query still answers when the record cannot be opened`() {
        // Injected as null, which is what the container does when recording is off or the file
        // could not be opened. The query door must not have a second behaviour for that.
        Codex.open(store()).use { codex ->
            codex.put(acme, listOf(entry("run")))
            val queries = CodexQueries(codex, ProjectScope.of(acme), vectors = null, use = null)
            assertTrue(queries.search("documented thing").candidates.isNotEmpty())
            assertNotNull(queries.get("com.example.acme.run"))
        }
    }

    @Test
    fun `the record is bounded`() {
        // A machine that builds all day would otherwise grow this for ever. The bound is policy,
        // not an accident of how much disk somebody had.
        val record = assertNotNull(UseRecord.open(store(), keep = 20, trimEvery = 10))
        record.use { r ->
            repeat(200) {
                r.search("/work/mine", "need $it", 1, 1, 0, UseRecord.Ranking.Vector, listOf(acme))
            }
            val searches = r.summary().searches
            assertTrue(searches <= 30, "expected the record to be trimmed, held $searches rows")
            assertTrue(searches > 0, "and not emptied entirely")
        }
    }

    // -- what it must not leak ---------------------------------------------------------------------

    @Test
    fun `the need text is stored but never comes back through a tool`() {
        // A need in a developer's own words describes what is being built, which is more revealing
        // than the dependency graph it is asked against. It is recorded for counting and is not
        // reachable through either tool.
        val store = store()
        val record = assertNotNull(UseRecord.open(store))
        Codex.open(store).use { codex ->
            codex.put(acme, listOf(entry("run")))
            val queries = CodexQueries(codex, ProjectScope.of(acme), vectors = null, use = record)
            val secret = "migrating the billing ledger off the vendor SDK"
            queries.search(secret)

            // It is in the record...
            assertTrue(Files.exists(UseRecord.file(store)))
            assertTrue(record.summary().searches > 0)
            // ...and nowhere a caller can reach.
            val answer = queries.search("documented thing")
            assertFalse(answer.candidates.any { secret in it.signature || secret in (it.capability ?: "") })
            assertFalse(secret in (answer.note ?: ""))
            val got = assertNotNull(queries.get("com.example.acme.run"))
            assertFalse(secret in got.signature || secret in (got.capability ?: ""))
        }
        record.close()
    }

    @Test
    fun `one project's queries are not recorded against another`() {
        // The containment boundary the whole service holds, applied to the record too: a `get`
        // must not be attributed to a search another project made.
        val record = assertNotNull(UseRecord.open(store()))
        record.use { r ->
            r.search("/work/theirs", "their need", 1, 1, 0, UseRecord.Ranking.Vector, listOf(other))
            r.get("/work/mine", "com.example.acme.run", found = true, coordinates = listOf(acme))
            assertEquals(0, r.summary().getsAfterSearch, "the get belongs to a different project")
        }
    }

    @Test
    fun `the record sits beside the store, wherever that is`() {
        // It follows the store rather than deciding for itself, so moving the store with --store
        // takes the record with it instead of leaving one behind for somebody else to find.
        val store = store()
        assertEquals(store.parent, UseRecord.file(store).parent)
        assertEquals(UseRecord.FILE_NAME, UseRecord.file(store).fileName.toString())
    }
}
