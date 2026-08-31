package org.dependencyskills.codex.indexer

import org.dependencyskills.codex.core.Coordinate
import org.slf4j.LoggerFactory
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Duration
import kotlin.io.path.name

/** A sources jar, and whether we are the ones who put it there. */
data class SourcesJar(val path: Path, val fetched: Boolean)

/**
 * Gets a sources jar, from the build's cache if it is there and from the network if it is not.
 *
 * **Cache first, always.** Measured on one real machine, 3,829 of 6,892 cached jars already had
 * their sources beside them, because a sync fetches them by default. Downloading those again would
 * pay twice for the same bytes and fill a second directory with them.
 *
 * **What we fetch is ours, and what we found is theirs.** A jar taken from the build's cache is
 * read in place and never deleted — it belongs to the build, and this tool being uninstalled must
 * not be able to damage one. A jar this downloaded is staged in our own directory and deleted once
 * its coordinate is indexed, because entries are content-addressed and the jar has no second use.
 *
 * That asymmetry is the whole design, and getting it backwards would either delete a developer's
 * artifacts or accumulate a duplicate copy of every library on the machine.
 */
class SourcesSupplier(
    private val staging: Path,
    private val cache: (Coordinate) -> Path? = SourcesInCache::find,
    private val download: (Coordinate, Path) -> Boolean = MavenCentral::fetch,
) {

    private val logger = LoggerFactory.getLogger(SourcesSupplier::class.java)

    /**
     * The sources for [coordinate], or null when neither the cache nor the network has them.
     *
     * Null is a finding rather than a failure: the library publishes none, and the caller records
     * `NoSource` so it is not attempted again on every pass.
     */
    fun acquire(coordinate: Coordinate): SourcesJar? {
        cache(coordinate)?.let { return SourcesJar(it, fetched = false) }

        Files.createDirectories(staging)
        // Named for the coordinate, so an interrupted pass leaves something identifiable rather
        // than a temporary file nobody can attribute.
        val target = staging.resolve(coordinate.value.replace(':', '_') + "-sources.jar")
        val partial = target.resolveSibling(target.name + ".part")
        return try {
            if (!download(coordinate, partial)) {
                Files.deleteIfExists(partial)
                null
            } else {
                // Moved into place only once complete, so a killed fetch cannot leave a truncated
                // jar that reads as a library with no entries.
                Files.move(partial, target, StandardCopyOption.REPLACE_EXISTING)
                SourcesJar(target, fetched = true)
            }
        } catch (t: Throwable) {
            runCatching { Files.deleteIfExists(partial) }
            logger.warn("could not fetch sources for {}: {}", coordinate, t.message)
            null
        }
    }

    /** Deletes what we fetched. A jar found in the build's cache is left exactly where it was. */
    fun release(jar: SourcesJar) {
        if (!jar.fetched) return
        runCatching { Files.deleteIfExists(jar.path) }
            .onFailure { logger.warn("could not remove staged {}: {}", jar.path.name, it.message) }
    }

    /**
     * Removes anything left staged by a previous run.
     *
     * A pass killed part-way leaves jars nothing will ever ask for again — the coordinate is
     * re-attempted from the start, and re-fetched — so without this the staging area grows
     * quietly and for ever. Called at startup, where "everything here is an orphan" is true by
     * definition because no pass is running.
     */
    fun clean(): Int {
        if (!Files.isDirectory(staging)) return 0
        var removed = 0
        runCatching {
            Files.newDirectoryStream(staging).use { entries ->
                entries.forEach { if (runCatching { Files.deleteIfExists(it) }.getOrDefault(false)) removed++ }
            }
        }
        if (removed > 0) logger.info("removed {} staged sources left by a previous run", removed)
        return removed
    }
}

/**
 * Maven Central, by URL.
 *
 * Central's layout is deterministic, so a `maven:` coordinate maps to a URL with no resolver and no
 * dependency. Measured: `guava`, `kotlin-stdlib` and `ktor-server-core` sources all answer a ranged
 * GET, which is the whole mechanism for the common case.
 *
 * **What this cannot do is a private repository.** Those are configured per project, and the build
 * that resolved the coordinate already fetched from them with credentials this service does not
 * have. A private coordinate therefore records `NoSource` here — which is wrong in the sense that
 * sources may well exist, and right in the sense that this cannot reach them. Bytecode indexing is
 * the answer to that, not a resolver in this process.
 */
object MavenCentral {

    private const val BASE = "https://repo1.maven.org/maven2"

    private val client: HttpClient by lazy {
        HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build()
    }

    /** Writes the sources jar to [target], or returns false when Central does not have one. */
    fun fetch(coordinate: Coordinate, target: Path): Boolean {
        val url = url(coordinate) ?: return false
        val response = client.send(
            HttpRequest.newBuilder(URI.create(url)).GET().timeout(Duration.ofMinutes(5)).build(),
            HttpResponse.BodyHandlers.ofFile(target),
        )
        // 404 is the ordinary answer for a library that publishes no sources, and is not an error.
        return response.statusCode() == 200
    }

    /** `maven:group:artifact:version` to Central's path, where the group IS nested. */
    fun url(coordinate: Coordinate): String? {
        if (!coordinate.ecosystem.equals("maven", ignoreCase = true)) return null
        val parts = coordinate.value.split(':')
        if (parts.size != 3) return null
        val (group, artifact, version) = parts
        if (group.isBlank() || artifact.isBlank() || version.isBlank()) return null
        // Unlike Gradle's cache, which keeps the group as one directory, Central nests it.
        return "$BASE/${group.replace('.', '/')}/$artifact/$version/$artifact-$version-sources.jar"
    }
}
