package org.dependencyskills.codex.indexer

import org.dependencyskills.codex.core.Coordinate
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Finding a sources jar the build already downloaded.
 *
 * Most of them are already there — 56% of one real machine's cached artifacts — so this is the
 * difference between indexing being nearly free and paying for every byte twice.
 */
class SourcesInCacheTest {

    private val home = createTempDir()
    private val env = mapOf("GRADLE_USER_HOME" to home.resolve(".gradle").toString())
    private val props = mapOf("user.home" to home.toString())

    private fun createTempDir(): Path = Files.createTempDirectory("cache")

    /** Writes a jar where Gradle would have put it, hash directory and all. */
    private fun cache(group: String, artifact: String, version: String, name: String): Path {
        val dir = home.resolve(".gradle/caches/modules-2/files-2.1")
            .resolve(group).resolve(artifact).resolve(version).resolve("0123456789abcdef")
        dir.createDirectories()
        return dir.resolve(name).also { it.writeText("not really a jar") }
    }

    @Test
    fun `finds the sources jar the build downloaded`() {
        val expected = cache("com.example", "alpha", "1.0", "alpha-1.0-sources.jar")
        val found = SourcesInCache.find(Coordinate("maven", "com.example:alpha:1.0"), env, props)
        assertEquals(expected, found)
    }

    @Test
    fun `the group keeps its dots, because Gradle does not nest it`() {
        // Maven nests `com/example/deep`; Gradle uses one directory per group. Translating between
        // them is the mistake that makes this silently find nothing on a real machine.
        val expected = cache("com.example.deep", "beta", "2.0", "beta-2.0-sources.jar")
        assertEquals(expected, SourcesInCache.find(Coordinate("maven", "com.example.deep:beta:2.0"), env, props))
    }

    @Test
    fun `a coordinate with only a main jar is not found`() {
        // Not a finding, and not an error - this machine simply has not downloaded them.
        cache("com.example", "gamma", "1.0", "gamma-1.0.jar")
        assertNull(SourcesInCache.find(Coordinate("maven", "com.example:gamma:1.0"), env, props))
    }

    @Test
    fun `something merely containing the word sources is not mistaken for it`() {
        // Named exactly. A `-sources-shaded.jar` or a `-sources.jar.sha1` is not the artifact, and
        // handing the wrong file to the harvester would present as a library with no entries.
        cache("com.example", "delta", "1.0", "delta-1.0-sources-shaded.jar")
        cache("com.example", "delta", "1.0", "delta-1.0-sources.jar.sha1")
        assertNull(SourcesInCache.find(Coordinate("maven", "com.example:delta:1.0"), env, props))
    }

    @Test
    fun `an unknown ecosystem is not guessed at`() {
        cache("com.example", "alpha", "1.0", "alpha-1.0-sources.jar")
        assertNull(SourcesInCache.find(Coordinate("npm", "left-pad@1.0.0"), env, props))
    }

    @Test
    fun `a malformed coordinate is null rather than an exception`() {
        listOf("no-colons-at-all", "com.example:alpha", "com.example::1.0", ":alpha:1.0").forEach {
            assertNull(SourcesInCache.find(Coordinate("maven", it), env, props), "for '$it'")
        }
    }

    @Test
    fun `an absent cache is null rather than an exception`() {
        assertNull(SourcesInCache.find(Coordinate("maven", "com.example:nothing:1.0"), env, props))
    }

    @Test
    fun `GRADLE_USER_HOME is honoured, because this is Gradle's own directory`() {
        // The opposite rule to the store, deliberately. The store must ignore it - the service
        // never sees a build's value - but this is Gradle's cache, so Gradle's variable is right.
        val elsewhere = createTempDir()
        val dir = elsewhere.resolve("caches/modules-2/files-2.1/com.example/eps/1.0/aaa")
        dir.createDirectories()
        val jar = dir.resolve("eps-1.0-sources.jar").also { it.writeText("x") }
        assertEquals(
            jar,
            SourcesInCache.find(
                Coordinate("maven", "com.example:eps:1.0"),
                mapOf("GRADLE_USER_HOME" to elsewhere.toString()),
                props,
            ),
        )
    }

    // -- the compiled artifact, and where multiplatform keeps it -------------------------------

    @Test
    fun `the classes jar is found under the coordinate that names it`() {
        val expected = cache("com.example", "zeta", "1.0", "zeta-1.0.jar")
        assertEquals(expected, SourcesInCache.classes(Coordinate("maven", "com.example:zeta:1.0"), env, props))
    }

    @Test
    fun `a multiplatform common module falls back to the JVM variant for its bytecode`() {
        // The defect this exists for. A build resolves the COMMON module and reports that
        // coordinate, which is correct - one entry per library, not one per target. But the common
        // module's artifact is Kotlin metadata, and the bytecode the visibility rule needs is
        // published beside it under `-jvm`. Without this the oracle is blind for every
        // multiplatform dependency and keeps what it never judged.
        cache("org.example", "coroutines", "1.0", "coroutines-metadata-1.0.jar")
        val jvm = cache("org.example", "coroutines-jvm", "1.0", "coroutines-jvm-1.0.jar")
        assertEquals(jvm, SourcesInCache.classes(Coordinate("maven", "org.example:coroutines:1.0"), env, props))
    }

    @Test
    fun `the coordinate's own classes jar wins over the JVM variant`() {
        // The fallback is a fallback. A library that publishes both must be read as itself.
        val own = cache("org.example", "eta", "1.0", "eta-1.0.jar")
        cache("org.example", "eta-jvm", "1.0", "eta-jvm-1.0.jar")
        assertEquals(own, SourcesInCache.classes(Coordinate("maven", "org.example:eta:1.0"), env, props))
    }

    @Test
    fun `a coordinate already naming the JVM variant is not asked for it twice`() {
        // Guards the obvious way to write this wrong: appending unconditionally would look for
        // `theta-jvm-jvm`, which nothing publishes, and the miss would be silent.
        cache("org.example", "theta-jvm-jvm", "1.0", "theta-jvm-jvm-1.0.jar")
        assertNull(SourcesInCache.classes(Coordinate("maven", "org.example:theta-jvm:1.0"), env, props))
    }

    @Test
    fun `a library with neither classes nor a JVM variant is null rather than an exception`() {
        // Still the ordinary case, and still not a finding: the harvest runs unjudged and says so.
        cache("com.example", "iota", "1.0", "iota-1.0-sources.jar")
        assertNull(SourcesInCache.classes(Coordinate("maven", "com.example:iota:1.0"), env, props))
    }
}
