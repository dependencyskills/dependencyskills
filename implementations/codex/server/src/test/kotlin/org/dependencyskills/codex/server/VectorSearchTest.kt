package org.dependencyskills.codex.server

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.NewEntry
import org.dependencyskills.codex.core.Provenance
import org.dependencyskills.codex.index.PackagedEncoder
import org.dependencyskills.codex.index.TwoFacedIndex
import org.dependencyskills.codex.index.VectorSearch
import org.dependencyskills.codex.inference.openEncoder
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The server answering from vectors rather than from FTS5.
 *
 * Until this existed the vector path was wired and unexercised: every other test runs with no
 * index, so they all take the lexical fallback and would pass just as happily against a `search`
 * that ignored the index entirely.
 */
class VectorSearchTest {

    private val acme = Coordinate("maven", "com.example.acme:acme-core:1.0.0")

    private fun entry(symbol: String, doc: String, rewrite: String) = NewEntry(
        symbol = "com.example.acme.$symbol",
        signature = "fun $symbol(input: String): String",
        doc = doc, lang = "kotlin", docFormat = "kdoc",
        provenance = Provenance(extractor = "tree-sitter", summariser = "model-1.0"),
        rewrite = rewrite,
    )

    /** A store with an index built beside it, the way the service will leave things. */
    private fun seeded(): java.nio.file.Path {
        val dir = createTempDirectory("vectors")
        val store = dir.resolve("codex.db")
        val entries = listOf(
            entry("stream", "Writes bytes to the response body as they become available.",
                "Writes bytes to the response body as they become available."),
            entry("parseDate", "Reads a calendar date from text in several accepted layouts.",
                "Reads a calendar date from text in several accepted layouts."),
            entry("retry", "Runs an operation again after a failure, waiting longer each time.",
                "Runs an operation again after a failure, waiting longer each time."),
        )
        Codex.open(store).use { it.put(acme, entries) }

        val packaged = assertNotNull(PackagedEncoder.unpack(), "no encoder artifact on the classpath")
        openEncoder(packaged.model.toString(), packaged.pooling).use { encoder ->
            TwoFacedIndex.open(
                dir.resolve(VectorSearch.DIRECTORY), packaged.name, packaged.pooling, packaged.dimensions,
            ).use { index ->
                Codex.open(store).use { codex ->
                    codex.entriesOf(acme).forEach { e ->
                        val doc = codex.rawDocumentation(e.id)!!
                        index.add(e.id, e.coordinates, encoder.embed(doc), e.rewrite?.let { encoder.embed(it) })
                    }
                }
                index.commit()
            }
        }
        return store
    }

    @Test
    fun `a need in plain words is answered from the index`() {
        val store = seeded()
        val vectors = assertNotNull(VectorSearch.openIfBuilt(store), "the index was not opened")
        vectors.use {
            Codex.open(store).use { codex ->
                val queries = CodexQueries(codex, ProjectScope.of(acme), vectors)
                // Deliberately shares no distinctive word with the target: "waiting longer each
                // time" against "backoff". Lexical search cannot answer this, which is the point.
                val found = queries.search("retry something with backoff after it fails")
                assertTrue(found.candidates.isNotEmpty(), "the index returned nothing")
                assertEquals("com.example.acme.retry", found.candidates.first().symbol)
            }
        }
    }

    @Test
    fun `scope still holds when the index is answering`() {
        val store = seeded()
        val other = Coordinate("maven", "com.example.other:other:1.0.0")
        VectorSearch.openIfBuilt(store)!!.use { vectors ->
            Codex.open(store).use { codex ->
                val queries = CodexQueries(codex, ProjectScope.of(other), vectors)
                assertTrue(queries.search("retry something with backoff after it fails").candidates.isEmpty())
            }
        }
    }

    @Test
    fun `no index falls back to lexical rather than answering nothing`() {
        val store = createTempDirectory("no-vectors").resolve("codex.db")
        Codex.open(store).use {
            it.put(acme, listOf(entry("stream", "Writes bytes to the response body.", "Writes bytes.")))
        }
        assertNull(VectorSearch.openIfBuilt(store), "there is no index to open")
        Codex.open(store).use { codex ->
            val queries = CodexQueries(codex, ProjectScope.of(acme), vectors = null)
            assertTrue(queries.search("writes bytes to the response body").candidates.isNotEmpty())
        }
    }
}
