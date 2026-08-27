package org.dependencyskills.codex.core

import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.sql.Connection
import java.sql.DriverManager
import java.time.Instant

/** Opening a store written by a newer schema. Loud, rather than corrupting it. */
class SchemaVersionException(found: Int, supported: Int) : RuntimeException(
    "This codex was written by schema version $found; this build supports $supported. " +
        "Upgrade, or delete the store — it is derived and rebuilding costs only a re-harvest."
)

/**
 * The codex store.
 *
 * Content-addressed: an entry is identified by a hash of `(symbol, signature, doc)`, and
 * coordinates point *at* it. Two artifacts carrying the same declaration and the same prose
 * collapse to one entry owned by both, with no decision taken at harvest time. That is not
 * an optimisation — deduplicating at harvest is order-dependent under an incremental store,
 * and a project depending only on the artifact that *lost* would silently see nothing.
 *
 * The hash covers the *raw* doc only. The rewrite is derived, so including it would fork
 * every entry the first time the rewriter is re-run.
 */
class Codex private constructor(private val db: Connection) : AutoCloseable {

    companion object {
        const val SCHEMA_VERSION = 1

        fun open(file: Path): Codex {
            Files.createDirectories(file.parent)
            // Registers the driver explicitly. DriverManager discovers drivers through
            // META-INF/services using the thread context classloader, which inside a Gradle
            // plugin is not the loader that has this jar - so auto-discovery finds nothing and
            // reports it as "no suitable driver", which reads like a missing dependency.
            Class.forName("org.sqlite.JDBC")
            val c = DriverManager.getConnection("jdbc:sqlite:${file.toAbsolutePath()}")
            // Pragmas first: SQLite refuses to change journal mode inside a transaction,
            // and turning autoCommit off opens one.
            c.createStatement().use { s ->
                // WAL lets two Gradle builds write at once without corrupting each other;
                // NORMAL is the durability level WAL is designed around.
                s.execute("PRAGMA journal_mode=WAL")
                s.execute("PRAGMA synchronous=NORMAL")
                s.execute("PRAGMA foreign_keys=ON")
                s.execute("PRAGMA busy_timeout=10000")
            }
            // autoCommit stays ON. Writes go through transact(), which issues BEGIN
            // IMMEDIATE: a deferred transaction takes a read lock and then tries to upgrade
            // it, and SQLite answers that with an immediate SQLITE_BUSY rather than invoking
            // the busy handler - so busy_timeout never applies and a second writer just dies.
            return Codex(c).also { it.migrate() }
        }

        fun open(
            env: Map<String, String> = System.getenv(),
            sysProps: Map<String, String> = System.getProperties().entries
                .associate { (k, v) -> k.toString() to v.toString() },
        ): Codex = open(CodexLocation.databaseFile(env, sysProps))

        /** The entry identity. Raw doc participates; the rewrite deliberately does not. */
        fun entryId(symbol: String, signature: String, doc: String): String {
            val md = MessageDigest.getInstance("SHA-256")
            // Length-prefixed so ("ab","c") and ("a","bc") cannot collide.
            listOf(symbol, signature, doc).forEach {
                val b = it.toByteArray(Charsets.UTF_8)
                md.update(b.size.toString().toByteArray(Charsets.UTF_8)); md.update(0)
                md.update(b)
            }
            return md.digest().joinToString("") { "%02x".format(it) }
        }
    }

    /** Runs [body] in a write transaction that waits for the lock instead of failing on it. */
    private fun <T> transact(body: () -> T): T {
        db.createStatement().use { it.execute("BEGIN IMMEDIATE") }
        return try {
            val r = body()
            db.createStatement().use { it.execute("COMMIT") }
            r
        } catch (t: Throwable) {
            runCatching { db.createStatement().use { it.execute("ROLLBACK") } }
            throw t
        }
    }

    private fun migrate() {
        db.createStatement().use { s ->
            s.executeUpdate("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        }
        val found = db.prepareStatement("SELECT value FROM meta WHERE key='schema_version'").use { p ->
            p.executeQuery().use { if (it.next()) it.getString(1).toInt() else null }
        }
        if (found != null && found > SCHEMA_VERSION) throw SchemaVersionException(found, SCHEMA_VERSION)
        if (found == null) transact {
            db.createStatement().use { s ->
                s.executeUpdate("""
                    CREATE TABLE entry (
                      id         TEXT PRIMARY KEY,
                      symbol     TEXT NOT NULL,
                      signature  TEXT NOT NULL,
                      doc        TEXT NOT NULL,
                      rewrite    TEXT,
                      lang       TEXT NOT NULL,
                      doc_format TEXT NOT NULL,
                      state      TEXT NOT NULL,
                      extractor  TEXT NOT NULL,
                      summariser TEXT,
                      encoder    TEXT,
                      pooling    TEXT
                    )""".trimIndent())
                s.executeUpdate("""
                    CREATE TABLE coordinate (
                      id            INTEGER PRIMARY KEY AUTOINCREMENT,
                      ecosystem     TEXT NOT NULL,
                      value         TEXT NOT NULL,
                      harvest_state TEXT NOT NULL,
                      first_seen    INTEGER NOT NULL,
                      last_attempt  INTEGER,
                      UNIQUE (ecosystem, value)
                    )""".trimIndent())
                s.executeUpdate("""
                    CREATE TABLE coordinate_entry (
                      coordinate_id INTEGER NOT NULL REFERENCES coordinate(id) ON DELETE CASCADE,
                      entry_id      TEXT    NOT NULL REFERENCES entry(id)      ON DELETE CASCADE,
                      PRIMARY KEY (coordinate_id, entry_id)
                    )""".trimIndent())
                s.executeUpdate("CREATE INDEX idx_ce_entry ON coordinate_entry(entry_id)")
                s.executeUpdate("CREATE INDEX idx_coord_state ON coordinate(harvest_state)")
                s.executeUpdate("INSERT OR IGNORE INTO meta(key,value) VALUES('schema_version','$SCHEMA_VERSION')")
            }
        }
    }

    val schemaVersion: Int
        get() = db.prepareStatement("SELECT value FROM meta WHERE key='schema_version'").use { p ->
            p.executeQuery().use { it.next(); it.getString(1).toInt() } }

    // ---- coordinates ---------------------------------------------------------------

    /** Records a coordinate, or returns the existing row. Idempotent: seeing it twice is one row. */
    fun seen(c: Coordinate, state: HarvestState = HarvestState.Pending): Long = transact {
        db.prepareStatement(
            "INSERT INTO coordinate(ecosystem,value,harvest_state,first_seen) VALUES(?,?,?,?) " +
                "ON CONFLICT(ecosystem,value) DO NOTHING"
        ).use { p ->
            p.setString(1, c.ecosystem); p.setString(2, c.value)
            p.setString(3, state.name); p.setLong(4, Instant.now().epochSecond)
            p.executeUpdate()
        }
        idOf(c) ?: error("coordinate vanished after insert: $c")
    }

    /**
     * Records a batch of coordinates as seen, and answers which of them were new.
     *
     * One transaction rather than one per coordinate: a consuming project's compile classpath
     * is hundreds of coordinates and every build resolves it again, so the overwhelmingly
     * common case is a few hundred rows that all already exist.
     *
     * The return value is the point. A caller that has to ask afterwards which ones it added
     * has to re-read them, and between the write and the read another build may have added
     * more — so it would report someone else's work as its own.
     */
    fun seenAll(coordinates: Collection<Coordinate>): List<Coordinate> {
        if (coordinates.isEmpty()) return emptyList()
        val distinct = coordinates.distinct()
        return transact {
            val known = HashSet<Coordinate>()
            db.prepareStatement("SELECT 1 FROM coordinate WHERE ecosystem=? AND value=?").use { p ->
                distinct.forEach { c ->
                    p.setString(1, c.ecosystem); p.setString(2, c.value)
                    p.executeQuery().use { if (it.next()) known.add(c) }
                }
            }
            val fresh = distinct.filterNot { it in known }
            if (fresh.isNotEmpty()) {
                val now = Instant.now().epochSecond
                db.prepareStatement(
                    "INSERT INTO coordinate(ecosystem,value,harvest_state,first_seen) VALUES(?,?,?,?) " +
                        "ON CONFLICT(ecosystem,value) DO NOTHING"
                ).use { p ->
                    fresh.forEach { c ->
                        p.setString(1, c.ecosystem); p.setString(2, c.value)
                        p.setString(3, HarvestState.Pending.name); p.setLong(4, now)
                        p.addBatch()
                    }
                    p.executeBatch()
                }
            }
            fresh
        }
    }

    private fun idOf(c: Coordinate): Long? =
        db.prepareStatement("SELECT id FROM coordinate WHERE ecosystem=? AND value=?").use { p ->
            p.setString(1, c.ecosystem); p.setString(2, c.value)
            p.executeQuery().use { if (it.next()) it.getLong(1) else null }
        }

    /** Moves a coordinate's harvest state and stamps the attempt. */
    fun harvestState(c: Coordinate, state: HarvestState) {
        seen(c)
        transact { db.prepareStatement("UPDATE coordinate SET harvest_state=?, last_attempt=? WHERE ecosystem=? AND value=?").use { p ->
            p.setString(1, state.name); p.setLong(2, Instant.now().epochSecond)
            p.setString(3, c.ecosystem); p.setString(4, c.value)
            p.executeUpdate()
        } }
    }

    /**
     * What the store knows about a coordinate, or null if it has never been seen.
     *
     * A coordinate recorded [HarvestState.NoSource] returns a record with no entries, which
     * is emphatically not the same as null — without that distinction a sources-less library
     * is re-queued on every build and re-fails forever.
     */
    fun coordinate(c: Coordinate): CoordinateRecord? =
        db.prepareStatement(
            "SELECT harvest_state, first_seen, last_attempt FROM coordinate WHERE ecosystem=? AND value=?"
        ).use { p ->
            p.setString(1, c.ecosystem); p.setString(2, c.value)
            p.executeQuery().use {
                if (!it.next()) null else CoordinateRecord(
                    coordinate = c,
                    state = HarvestState.valueOf(it.getString(1)),
                    firstSeen = Instant.ofEpochSecond(it.getLong(2)),
                    lastAttempt = it.getLong(3).takeIf { v -> !it.wasNull() }?.let(Instant::ofEpochSecond),
                )
            }
        }

    /** Coordinates in a given state, oldest attempt first — so re-checking by age is a query. */
    fun coordinatesIn(state: HarvestState, attemptedBefore: Instant? = null): List<CoordinateRecord> {
        val sql = StringBuilder("SELECT ecosystem,value,harvest_state,first_seen,last_attempt FROM coordinate WHERE harvest_state=?")
        if (attemptedBefore != null) sql.append(" AND (last_attempt IS NULL OR last_attempt < ?)")
        sql.append(" ORDER BY COALESCE(last_attempt, 0), id")
        return db.prepareStatement(sql.toString()).use { p ->
            p.setString(1, state.name)
            if (attemptedBefore != null) p.setLong(2, attemptedBefore.epochSecond)
            p.executeQuery().use { rs ->
                buildList {
                    while (rs.next()) add(CoordinateRecord(
                        Coordinate(rs.getString(1), rs.getString(2)),
                        HarvestState.valueOf(rs.getString(3)),
                        Instant.ofEpochSecond(rs.getLong(4)),
                        rs.getLong(5).takeIf { !rs.wasNull() }?.let(Instant::ofEpochSecond),
                    ))
                }
            }
        }
    }

    // ---- entries -------------------------------------------------------------------

    /**
     * Writes entries for a coordinate and marks it [HarvestState.Indexed].
     *
     * Entries collapse on content: writing the same declaration and prose under a second
     * coordinate adds an ownership row, not a second entry.
     */
    fun put(c: Coordinate, entries: List<NewEntry>) {
        val coordId = seen(c)
        transact {
        db.prepareStatement(
            "INSERT INTO entry(id,symbol,signature,doc,rewrite,lang,doc_format,state,extractor,summariser,encoder,pooling) " +
                "VALUES(?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO NOTHING"
        ).use { p ->
            entries.forEach { e ->
                p.setString(1, entryId(e.symbol, e.signature, e.doc))
                p.setString(2, e.symbol); p.setString(3, e.signature); p.setString(4, e.doc)
                p.setString(5, e.rewrite); p.setString(6, e.lang); p.setString(7, e.docFormat)
                p.setString(8, e.state.name); p.setString(9, e.provenance.extractor)
                p.setString(10, e.provenance.summariser); p.setString(11, e.provenance.encoder)
                p.setString(12, e.provenance.pooling)
                p.addBatch()
            }
            p.executeBatch()
        }
        db.prepareStatement(
            "INSERT INTO coordinate_entry(coordinate_id,entry_id) VALUES(?,?) ON CONFLICT DO NOTHING"
        ).use { p ->
            entries.forEach { e ->
                p.setLong(1, coordId); p.setString(2, entryId(e.symbol, e.signature, e.doc)); p.addBatch()
            }
            p.executeBatch()
        }
        db.prepareStatement("UPDATE coordinate SET harvest_state=?, last_attempt=? WHERE id=?").use { p ->
            p.setString(1, HarvestState.Indexed.name); p.setLong(2, Instant.now().epochSecond); p.setLong(3, coordId)
            p.executeUpdate()
        }
        }
    }

    /** Entries owned by a coordinate. The raw documentation is not among them. */
    fun entriesOf(c: Coordinate): List<Entry> =
        db.prepareStatement("""
            SELECT e.id, e.symbol, e.signature, e.rewrite, e.lang, e.doc_format, e.state,
                   e.extractor, e.summariser, e.encoder, e.pooling
              FROM entry e
              JOIN coordinate_entry ce ON ce.entry_id = e.id
              JOIN coordinate co       ON co.id = ce.coordinate_id
             WHERE co.ecosystem = ? AND co.value = ?
             ORDER BY e.symbol
        """.trimIndent()).use { p ->
            p.setString(1, c.ecosystem); p.setString(2, c.value)
            p.executeQuery().use { rs -> buildList { while (rs.next()) add(readEntry(rs)) } }
        }

    fun entry(id: String): Entry? =
        db.prepareStatement("""
            SELECT id,symbol,signature,rewrite,lang,doc_format,state,extractor,summariser,encoder,pooling
              FROM entry WHERE id = ?
        """.trimIndent()).use { p ->
            p.setString(1, id)
            p.executeQuery().use { if (it.next()) readEntry(it) else null }
        }

    private fun readEntry(rs: java.sql.ResultSet): Entry {
        val id = rs.getString("id")
        return Entry(
            id = id,
            symbol = rs.getString("symbol"),
            signature = rs.getString("signature"),
            rewrite = rs.getString("rewrite"),
            lang = rs.getString("lang"),
            docFormat = rs.getString("doc_format"),
            state = EntryState.valueOf(rs.getString("state")),
            provenance = Provenance(
                rs.getString("extractor"), rs.getString("summariser"),
                rs.getString("encoder"), rs.getString("pooling"),
            ),
            coordinates = ownersOf(id),
        )
    }

    private fun ownersOf(entryId: String): Set<Coordinate> =
        db.prepareStatement("""
            SELECT co.ecosystem, co.value FROM coordinate co
              JOIN coordinate_entry ce ON ce.coordinate_id = co.id
             WHERE ce.entry_id = ? ORDER BY co.ecosystem, co.value
        """.trimIndent()).use { p ->
            p.setString(1, entryId)
            p.executeQuery().use { rs ->
                buildSet { while (rs.next()) add(Coordinate(rs.getString(1), rs.getString(2))) }
            }
        }

    fun entryCount(): Int = db.createStatement().use { s ->
        s.executeQuery("SELECT COUNT(*) FROM entry").use { it.next(); it.getInt(1) } }

    override fun close() = db.close()
}

data class CoordinateRecord(
    val coordinate: Coordinate,
    val state: HarvestState,
    val firstSeen: Instant,
    val lastAttempt: Instant?,
)
