package org.dependencyskills.codex.core

import org.junit.jupiter.api.io.TempDir
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Opening a store written by an older schema.
 *
 * The store is derived and could be deleted and rebuilt instead — but rebuilding costs a full
 * re-harvest of every dependency on the machine, which is the expensive half. A migration that
 * works is what keeps that from being the answer to every schema change.
 */
class MigrationTest {

    /** A schema-1 store, written the way schema 1 wrote them: no `symbol_text`, no index. */
    private fun writeVersionOne(file: Path) {
        Class.forName("org.sqlite.JDBC")
        DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}").use { db ->
            db.createStatement().use { s ->
                s.executeUpdate("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
                s.executeUpdate(
                    "CREATE TABLE entry (id TEXT PRIMARY KEY, symbol TEXT NOT NULL, " +
                        "signature TEXT NOT NULL, doc TEXT NOT NULL, rewrite TEXT, lang TEXT NOT NULL, " +
                        "doc_format TEXT NOT NULL, state TEXT NOT NULL, extractor TEXT NOT NULL, " +
                        "summariser TEXT, encoder TEXT, pooling TEXT)"
                )
                s.executeUpdate(
                    "CREATE TABLE coordinate (id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                        "ecosystem TEXT NOT NULL, value TEXT NOT NULL, harvest_state TEXT NOT NULL, " +
                        "first_seen INTEGER NOT NULL, last_attempt INTEGER, UNIQUE (ecosystem, value))"
                )
                s.executeUpdate(
                    "CREATE TABLE coordinate_entry (coordinate_id INTEGER NOT NULL REFERENCES coordinate(id) " +
                        "ON DELETE CASCADE, entry_id TEXT NOT NULL REFERENCES entry(id) ON DELETE CASCADE, " +
                        "PRIMARY KEY (coordinate_id, entry_id))"
                )
                s.executeUpdate(
                    "INSERT INTO entry VALUES ('abc', 'io.ktor.server.response.respondRedirect', " +
                        "'fun respondRedirect()', 'Responds with a redirect to the given URL.', NULL, " +
                        "'kotlin', 'kdoc', 'Whole', 'test', NULL, NULL, NULL)"
                )
                s.executeUpdate(
                    "INSERT INTO coordinate VALUES (1, 'maven', 'io.ktor:ktor-server-core:3.5.2', 'Indexed', 0, NULL)"
                )
                s.executeUpdate("INSERT INTO coordinate_entry VALUES (1, 'abc')")
                s.executeUpdate("INSERT INTO meta VALUES ('schema_version', '1')")
            }
        }
    }

    @Test fun `a schema-1 store gains the index without losing what it held`(@TempDir dir: Path) {
        val file = dir.resolve("c.db")
        writeVersionOne(file)

        Codex.open(file).use { codex ->
            assertEquals(2, codex.schemaVersion)
            val ktor = Coordinate("maven", "io.ktor:ktor-server-core:3.5.2")
            assertEquals(1, codex.entriesOf(ktor).size)

            // The entry was written before the index existed, so it can only be found if the
            // migration backfilled the split symbol and rebuilt the index over what was there.
            val hits = codex.search("send whoever asked to a different address", setOf(ktor)).hits
            assertEquals("io.ktor.server.response.respondRedirect", hits.single().entry.symbol)
        }
    }

    @Test fun `entries written after the migration are indexed too`(@TempDir dir: Path) {
        val file = dir.resolve("c.db")
        writeVersionOne(file)
        Codex.open(file).use { codex ->
            val ktor = Coordinate("maven", "io.ktor:ktor-server-core:3.5.2")
            codex.put(ktor, listOf(NewEntry(
                symbol = "io.ktor.server.request.receiveMultipart",
                signature = "fun receiveMultipart()",
                doc = "Receives a multipart form submission, one part at a time.",
                lang = "kotlin", docFormat = "kdoc", provenance = Provenance("test"),
            )))
            val hits = codex.search("a form with an uploaded file in it", setOf(ktor)).hits
            assertTrue(hits.any { it.entry.symbol == "io.ktor.server.request.receiveMultipart" })
        }
    }

    @Test fun `reopening a migrated store does not migrate it again`(@TempDir dir: Path) {
        val file = dir.resolve("c.db")
        writeVersionOne(file)
        Codex.open(file).use { }
        Codex.open(file).use { codex ->
            assertEquals(2, codex.schemaVersion)
            val ktor = Coordinate("maven", "io.ktor:ktor-server-core:3.5.2")
            assertEquals(1, codex.search("redirect", setOf(ktor)).hits.size)
        }
    }
}
