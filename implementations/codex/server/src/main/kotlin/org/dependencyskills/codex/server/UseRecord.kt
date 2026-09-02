package org.dependencyskills.codex.server

import org.dependencyskills.codex.core.Coordinate
import org.slf4j.LoggerFactory
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.sql.Statement
import java.util.concurrent.ConcurrentHashMap

/**
 * What agents actually ask the codex for (#33).
 *
 * **This exists because nothing is measured.** Not that the index is unused — that nobody knows.
 * RAD-0063 had to reason its way toward what to index rather than observing it, and RAD-0064
 * concluded the per-entry index is substrate rather than the product without any evidence that
 * agents read either. Two questions decide a lot of work, and both are answerable only from here:
 * which of a machine's thousands of libraries are ever asked about, and which queries come back
 * with nothing usable.
 *
 * **It records what was asked, never what was answered with.** A query's *need* and the *shape* of
 * the response are kept; the prose of an entry is not. The record is for counting, not a cache.
 *
 * **What it cannot see, and does not pretend to.** The codex has no view of what an agent did with
 * a result — whether the code got written, whether it compiled, whether the developer kept it. The
 * one available signal is that an agent came back for the whole entry after searching, and
 * [Kind.Get] linked to a preceding search is exactly that and nothing more. It is a proxy, and
 * reading it as "the answer was used" would be reading more than the data says.
 *
 * **Local, and structurally so.** A dependency graph is commercially revealing — it names a
 * company's stack, its vendors and its version debt — and the queries against it are worse, because
 * a need in a developer's own words describes what is being built. This is written to a file beside
 * the store, is never returned through any tool, and nothing here opens a socket. That is not a
 * setting; there is no code path that sends it anywhere.
 */
class UseRecord private constructor(
    private val db: Connection,
    private val keep: Int,
    private val trimEvery: Int,
) : AutoCloseable {

    /** Which tool was called. Two shapes of question, and only one of them carries prose. */
    enum class Kind { Search, Get }

    /** How the answer was ranked, which is worth knowing when a query returns nothing useful. */
    enum class Ranking { Vector, Lexical, None }

    /**
     * The last search per project, so a following `get` can be attributed to it.
     *
     * In memory rather than queried back: this is a hint for one process's lifetime, and a `get`
     * that arrives after a restart is simply unattributed, which is honest.
     */
    private val lastSearch = ConcurrentHashMap<String, Long>()

    /**
     * Records one search.
     *
     * [answered] is not `candidates > 0`. A search that matched nothing and a search that could not
     * look because nothing in scope is indexed are different failures, and the second is not
     * evidence about the library at all — see [CodexQueries.Answer].
     */
    fun search(
        project: String,
        need: String,
        scopeSize: Int,
        candidates: Int,
        notHarvested: Int,
        ranking: Ranking,
        coordinates: Collection<Coordinate>,
    ) = swallow {
        val id = insert(
            project = project, kind = Kind.Search, text = need, scopeSize = scopeSize,
            candidates = candidates, notHarvested = notHarvested, ranking = ranking, follows = null,
        )
        coordinates.forEach { attribute(id, it) }
        lastSearch[project] = id
    }

    /** Records one `get`, attributed to the search it followed when there was one. */
    fun get(project: String, symbol: String, found: Boolean, coordinates: Collection<Coordinate>) = swallow {
        val id = insert(
            project = project, kind = Kind.Get, text = symbol, scopeSize = 0,
            candidates = if (found) 1 else 0, notHarvested = 0, ranking = Ranking.None,
            follows = lastSearch[project],
        )
        coordinates.forEach { attribute(id, it) }
    }

    /**
     * What the record is for, read back.
     *
     * Deliberately a summary rather than a dump: the questions are "which libraries are asked
     * about" and "which queries answer nothing", and a caller that wanted the raw rows would be
     * building something this is not for.
     */
    fun summary(): Summary {
        val counts = db.createStatement().use { s ->
            s.executeQuery(
                """
                SELECT
                  COUNT(*) FILTER (WHERE kind = 'Search')                       AS searches,
                  COUNT(*) FILTER (WHERE kind = 'Search' AND candidates = 0
                                     AND not_harvested = 0)                     AS empty,
                  COUNT(*) FILTER (WHERE kind = 'Search' AND not_harvested > 0
                                     AND candidates = 0)                        AS unindexed,
                  COUNT(*) FILTER (WHERE kind = 'Search' AND ranking = 'Lexical') AS lexical,
                  COUNT(*) FILTER (WHERE kind = 'Get')                          AS gets,
                  COUNT(*) FILTER (WHERE kind = 'Get' AND follows IS NOT NULL)   AS getsAfterSearch
                FROM query
                """.trimIndent()
            ).use { r ->
                if (!r.next()) return Summary(0, 0, 0, 0, 0, 0, emptyList())
                listOf(
                    r.getInt("searches"), r.getInt("empty"), r.getInt("unindexed"),
                    r.getInt("lexical"), r.getInt("gets"), r.getInt("getsAfterSearch"),
                )
            }
        }
        val asked = db.createStatement().use { s ->
            s.executeQuery(
                """
                SELECT coordinate, COUNT(*) AS n FROM query_coordinate
                GROUP BY coordinate ORDER BY n DESC LIMIT 50
                """.trimIndent()
            ).use { r ->
                buildList { while (r.next()) add(r.getString("coordinate") to r.getInt("n")) }
            }
        }
        return Summary(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], asked)
    }

    /**
     * @param searches every search asked
     * @param answeredNothing searches that looked at an indexed scope and matched nothing — the
     *   ones that say something about the corpus
     * @param nothingIndexed searches that could not look, which say nothing about the corpus
     * @param lexicalFallback searches answered without the vector index
     * @param gets entries fetched whole
     * @param getsAfterSearch gets that followed a search — the only proxy for a result being used
     * @param askedAbout coordinates by how often they appeared in an answer, most first
     */
    data class Summary(
        val searches: Int,
        val answeredNothing: Int,
        val nothingIndexed: Int,
        val lexicalFallback: Int,
        val gets: Int,
        val getsAfterSearch: Int,
        val askedAbout: List<Pair<String, Int>>,
    )

    // -- writing ---------------------------------------------------------------------------------

    private fun insert(
        project: String,
        kind: Kind,
        text: String,
        scopeSize: Int,
        candidates: Int,
        notHarvested: Int,
        ranking: Ranking,
        follows: Long?,
    ): Long {
        db.prepareStatement(
            """
            INSERT INTO query (at, project, kind, text, scope_size, candidates, not_harvested,
                               ranking, follows)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """.trimIndent(),
            Statement.RETURN_GENERATED_KEYS,
        ).use { p ->
            p.setLong(1, System.currentTimeMillis())
            p.setString(2, project)
            p.setString(3, kind.name)
            p.setString(4, text)
            p.setInt(5, scopeSize)
            p.setInt(6, candidates)
            p.setInt(7, notHarvested)
            p.setString(8, ranking.name)
            if (follows == null) p.setNull(9, java.sql.Types.INTEGER) else p.setLong(9, follows)
            p.executeUpdate()
            return p.generatedKeys.use { if (it.next()) it.getLong(1) else 0L }
        }
    }

    private fun attribute(queryId: Long, coordinate: Coordinate) {
        db.prepareStatement("INSERT INTO query_coordinate (query_id, coordinate) VALUES (?, ?)").use {
            it.setLong(1, queryId)
            it.setString(2, coordinate.value)
            it.executeUpdate()
        }
    }

    /**
     * Keeps the newest [KEEP] rows and drops the rest.
     *
     * A machine that builds all day would otherwise grow this for ever. The bound is a stated
     * policy rather than an accident of how much disk somebody had.
     */
    private fun trim() {
        db.createStatement().use {
            it.executeUpdate(
                "DELETE FROM query WHERE id <= (SELECT MAX(id) FROM query) - $keep"
            )
            it.executeUpdate(
                "DELETE FROM query_coordinate WHERE query_id NOT IN (SELECT id FROM query)"
            )
        }
    }

    /**
     * Recording is a side effect and behaves like one.
     *
     * The answer is the product. A record that could fail a query — a locked file, a full disk —
     * would trade the thing being delivered for a measurement about it, so every failure here is
     * logged once at debug and dropped.
     */
    private inline fun swallow(body: () -> Unit) {
        try {
            body()
            if (++writes % trimEvery == 0) trim()
        } catch (e: Exception) {                                            // noqa: BLE001
            logger.debug("could not record a query: {}", e.message)
        }
    }

    @Volatile private var writes = 0

    override fun close() = runCatching { db.close() }.let { }

    companion object {
        private val logger = LoggerFactory.getLogger(UseRecord::class.java)

        /** Beside the store, so moving the store takes its record with it. */
        const val FILE_NAME = "usage.db"

        /** How many query rows are kept. Enough to answer the questions, bounded on purpose. */
        const val KEEP = 50_000

        const val TRIM_EVERY = 500

        fun file(store: Path): Path = store.resolveSibling(FILE_NAME)

        /**
         * Opens the record, or returns null when it cannot be opened.
         *
         * Null rather than an exception: a codex that cannot write a usage file must still answer
         * queries, because the queries are the point.
         */
        fun open(
            store: Path,
            keep: Int = KEEP,
            trimEvery: Int = TRIM_EVERY,
        ): UseRecord? = try {
            Class.forName("org.sqlite.JDBC")
            Files.createDirectories(store.parent)
            val db = DriverManager.getConnection("jdbc:sqlite:${file(store)}")
            db.createStatement().use {
                it.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS query (
                      id INTEGER PRIMARY KEY AUTOINCREMENT,
                      at INTEGER NOT NULL,
                      project TEXT NOT NULL,
                      kind TEXT NOT NULL,
                      text TEXT NOT NULL,
                      scope_size INTEGER NOT NULL,
                      candidates INTEGER NOT NULL,
                      not_harvested INTEGER NOT NULL,
                      ranking TEXT NOT NULL,
                      follows INTEGER
                    )
                    """.trimIndent()
                )
                it.executeUpdate(
                    """
                    CREATE TABLE IF NOT EXISTS query_coordinate (
                      query_id INTEGER NOT NULL,
                      coordinate TEXT NOT NULL
                    )
                    """.trimIndent()
                )
                it.executeUpdate("CREATE INDEX IF NOT EXISTS query_coordinate_id ON query_coordinate (query_id)")
            }
            UseRecord(db, keep, trimEvery)
        } catch (e: Exception) {                                            // noqa: BLE001
            logger.warn("could not open the usage record, so nothing will be recorded: {}", e.message)
            null
        }
    }
}
