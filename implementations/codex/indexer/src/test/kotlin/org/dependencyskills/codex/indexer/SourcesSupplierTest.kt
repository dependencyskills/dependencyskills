package org.dependencyskills.codex.indexer

import org.dependencyskills.codex.core.Coordinate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.exists
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Where a sources jar comes from, and whose it is afterwards.
 *
 * Most of these are about the ownership rule, because inverting it is silent and expensive in both
 * directions: delete what we found and a developer's build cache loses artifacts to a tool that had
 * no business touching them; keep what we fetched and the machine accumulates a second copy of
 * every library ever indexed.
 */
class SourcesSupplierTest {

    private val alpha = Coordinate("maven", "com.example:alpha:1.0")

    private fun work(): Path = Files.createTempDirectory("supplier")

    @Test
    fun `a jar in the build's cache is used in place and never deleted`() {
        val work = work()
        val theirs = work.resolve("cache/alpha-1.0-sources.jar")
        theirs.parent.createDirectories()
        theirs.writeText("theirs")

        val supplier = SourcesSupplier(
            staging = work.resolve("staging"),
            cache = { theirs },
            download = { _, _ -> error("must not fetch when the cache has it") },
        )
        val jar = assertNotNull(supplier.acquire(alpha))
        assertEquals(theirs, jar.path)
        assertFalse(jar.fetched, "a cached jar is not ours")

        supplier.release(jar)
        assertTrue(theirs.exists(), "a jar belonging to the build was deleted")
    }

    @Test
    fun `a fetched jar is staged in our directory and removed when released`() {
        val work = work()
        val staging = work.resolve("staging")
        val supplier = SourcesSupplier(
            staging = staging,
            cache = { null },
            download = { _, target -> target.writeText("fetched"); true },
        )
        val jar = assertNotNull(supplier.acquire(alpha))
        assertTrue(jar.fetched)
        assertTrue(jar.path.startsWith(staging), "a fetched jar must be staged in our own directory")
        assertTrue(jar.path.exists())

        supplier.release(jar)
        assertFalse(jar.path.exists(), "what we fetched should not survive indexing")
    }

    @Test
    fun `nothing is ever written into the build's cache`() {
        // Uninstalling this tool must not be able to damage a build, which means never adding to
        // its cache either - not only never deleting from it.
        val work = work()
        val theirCache = work.resolve("cache").also { it.createDirectories() }
        val supplier = SourcesSupplier(
            staging = work.resolve("staging"),
            cache = { null },
            download = { _, target -> target.writeText("fetched"); true },
        )
        supplier.acquire(alpha)
        assertEquals(0, Files.list(theirCache).use { it.count().toInt() })
    }

    @Test
    fun `a library that publishes no sources is null, not an error`() {
        val supplier = SourcesSupplier(
            staging = work().resolve("staging"),
            cache = { null },
            download = { _, _ -> false },
        )
        assertNull(supplier.acquire(alpha))
    }

    @Test
    fun `a failed download leaves nothing behind`() {
        // A truncated jar would read as a library with no entries, which is worse than no jar at
        // all: it looks like a finding.
        val work = work()
        val staging = work.resolve("staging")
        val supplier = SourcesSupplier(
            staging = staging,
            cache = { null },
            download = { _, target -> target.writeText("half"); throw java.io.IOException("connection reset") },
        )
        assertNull(supplier.acquire(alpha))
        val left = Files.list(staging).use { it.toList() }
        assertTrue(left.isEmpty(), "a failed fetch left $left")
    }

    @Test
    fun `orphans from an interrupted run are cleaned up`() {
        // A killed pass re-fetches from the start, so whatever it staged is never asked for again.
        // Without this the staging area grows quietly and for ever.
        val work = work()
        val staging = work.resolve("staging").also { it.createDirectories() }
        staging.resolve("com.example_orphan_1.0-sources.jar").writeText("left over")
        staging.resolve("com.example_other_2.0-sources.jar.part").writeText("half written")

        val supplier = SourcesSupplier(staging, cache = { null }, download = { _, _ -> false })
        assertEquals(2, supplier.clean())
        assertEquals(0, Files.list(staging).use { it.count().toInt() })
    }

    @Test
    fun `cleaning an absent staging directory is not an error`() {
        assertEquals(0, SourcesSupplier(work().resolve("never-created")).clean())
    }

    @Test
    fun `Central's URL nests the group, unlike Gradle's cache`() {
        // The two layouts differ and using one for the other silently finds nothing. Gradle keeps
        // `com.example` as a single directory; Central nests it as `com/example`.
        assertEquals(
            "https://repo1.maven.org/maven2/com/example/alpha/1.0/alpha-1.0-sources.jar",
            MavenCentral.url(alpha),
        )
        assertNull(MavenCentral.url(Coordinate("npm", "left-pad@1.0.0")))
        assertNull(MavenCentral.url(Coordinate("maven", "not-a-coordinate")))
    }
}
