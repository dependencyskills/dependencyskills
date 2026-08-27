package org.dependencyskills.codex.harvester

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class SourcesJarHarvesterTest {

    private fun harvested(jar: Path): HarvestResult.Harvested =
        assertIs<HarvestResult.Harvested>(SourcesJarHarvester().harvest(jar))

    // -- reading a real artifact -------------------------------------------------------------

    @Test
    fun `reads a real Java sources jar and counts what it saw`() {
        val result = harvested(Fixtures.javaSources)
        assertEquals(47, result.report.sourceFiles)
        assertEquals(816, result.report.declarations)
        assertEquals(229, result.report.documented)
        assertEquals(229, result.entries.size)
        assertEquals(0, result.report.unreadable)
        assertEquals(0, result.report.withParseErrors)
        assertTrue(result.entries.all { it.lang == "java" && it.docFormat == "javadoc" })
    }

    @Test
    fun `reads a real Kotlin sources jar and counts what it saw`() {
        val result = harvested(Fixtures.kotlinSources)
        assertEquals(54, result.report.sourceFiles)
        assertEquals(1353, result.report.declarations)
        assertEquals(324, result.report.documented)
        assertEquals(324, result.entries.size)
        assertEquals(0, result.report.unreadable)
        assertTrue(result.entries.all { it.lang == "kotlin" && it.docFormat == "kdoc" })
    }

    @Test
    fun `a package-rooted jar has no source sets and a multiplatform one names them`() {
        assertEquals(emptySet(), harvested(Fixtures.javaSources).report.sourceSets)
        assertEquals(setOf("commonMain", "jvmMain"), harvested(Fixtures.kotlinSources).report.sourceSets)
    }

    @Test
    fun `symbols are qualified by their package and enclosing types`() {
        val symbols = harvested(Fixtures.javaSources).entries.map { it.symbol }.toSet()
        assertContains(symbols, "org.slf4j.Logger.isDebugEnabled")
        assertContains(symbols, "org.slf4j.ILoggerFactory.getLogger")
    }

    // -- binding ------------------------------------------------------------------------------

    @Test
    fun `a licence header does not bind to the declaration below it`() {
        // Every file in this artifact opens with an Apache header written as a doc comment, so
        // this is the trap on real input rather than on a fixture built to contain it.
        val result = harvested(Fixtures.javaSources)
        assertTrue(result.entries.none { it.doc.startsWith("Copyright") })
        assertTrue(result.entries.none { it.doc.contains("WITHOUT WARRANTY OF ANY KIND") })
        // Refused, not invisible: the headers are counted as documentation nothing claimed.
        assertTrue(result.report.unclaimedDocs > 0, "the refused headers must still be reported")
    }

    @Test
    fun `a doc comment binds only across whitespace`() {
        val dir = createTempDirectory("harvest")
        val jar = jarOf(dir, "binding-sources.jar", "com/example/acme/Service.java" to """
            /**
             * Copyright 2026 Example. Licensed under the Apache License, version two.
             */
            package com.example.acme;

            /**
             * A service that does a documented thing for the callers that ask it to.
             */
            public interface Service {
                /** Runs the documented thing and hands back what it produced. */
                String run(int attempts);

                String undocumented(int attempts);
            }
        """.trimIndent())

        val result = harvested(jar)
        assertEquals(
            listOf("com.example.acme.Service", "com.example.acme.Service.run"),
            result.entries.map { it.symbol },
        )
        assertTrue(result.entries.none { it.doc.startsWith("Copyright") })
        assertEquals(1, result.report.unclaimedDocs)
    }

    @Test
    fun `a Kotlin doc comment binds even when the grammar parses it into the import list`() {
        // The KDoc between the last import and the first declaration is not a sibling of the
        // declaration it documents - tree-sitter puts it inside the import list. A rule that
        // looked at siblings would silently document nothing in a file shaped like this.
        val dir = createTempDirectory("harvest")
        val jar = jarOf(dir, "kotlin-sources.jar", "com/example/acme/Parse.kt" to """
            /**
             * Copyright 2026 Example. Licensed under the Apache License, version two.
             */
            package com.example.acme

            import kotlin.text.Regex

            /**
             * Parses the given text and returns how many matches it found in it.
             */
            public fun parse(input: String): Int = Regex("x").findAll(input).count()

            /** The number of attempts made before this gives up on the input entirely. */
            public val attempts: Int = 3
        """.trimIndent())

        val result = harvested(jar)
        assertEquals(
            listOf("com.example.acme.parse", "com.example.acme.attempts"),
            result.entries.map { it.symbol },
        )
        assertEquals("public fun parse(input: String): Int", result.entries[0].signature)
        assertEquals("public val attempts: Int", result.entries[1].signature)
        assertTrue(result.entries.none { it.doc.startsWith("Copyright") })
    }

    @Test
    fun `a comment among the modifiers stays out of the signature`() {
        val dir = createTempDirectory("harvest")
        val jar = jarOf(dir, "commented-sources.jar", "com/example/acme/Thing.kt" to """
            package com.example.acme

            /**
             * A thing that carries an annotation and a note to whoever maintains it.
             */
            @Deprecated("gone")
            // see the tracker, this is going away in the next release
            public class Thing(val name: String)
        """.trimIndent())

        assertEquals(
            "@Deprecated(\"gone\") public class Thing(val name: String)",
            harvested(jar).entries.single().signature,
        )
    }

    // -- nothing to harvest, said out loud -----------------------------------------------------

    @Test
    fun `an archive with no source in it says so instead of succeeding quietly`() {
        val result = SourcesJarHarvester().harvest(Fixtures.noSources)
        val noSource = assertIs<HarvestResult.NoSource>(result)
        assertContains(noSource.reason, "slf4j-api-2.0.17.jar")
        assertContains(noSource.reason, "no Kotlin or Java source")
    }

    @Test
    fun `an unreadable archive is a failure, not an absence`() {
        val dir = createTempDirectory("harvest")
        val broken = dir.resolve("truncated-sources.jar")
        Files.write(broken, byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00))
        assertIs<HarvestResult.Failed>(SourcesJarHarvester().harvest(broken))
    }

    @Test
    fun `a file that is not there is a failure, not an absence`() {
        val dir = createTempDirectory("harvest")
        assertIs<HarvestResult.Failed>(SourcesJarHarvester().harvest(dir.resolve("absent-sources.jar")))
    }

    @Test
    fun `an archive holding only resources reports no source rather than an empty harvest`() {
        val dir = createTempDirectory("harvest")
        val jar = jarOf(dir, "resources-sources.jar", "META-INF/LICENSE" to "Apache 2.0")
        assertIs<HarvestResult.NoSource>(SourcesJarHarvester().harvest(jar))
    }

    @Test
    fun `source that declares nothing documented is harvested, not reported as sourceless`() {
        // The distinction the two cases above exist for: there WAS source, and it said nothing.
        val dir = createTempDirectory("harvest")
        val jar = jarOf(dir, "bare-sources.jar", "com/example/acme/Bare.java" to """
            package com.example.acme;
            public class Bare { public int n; }
        """.trimIndent())
        val result = harvested(jar)
        assertTrue(result.entries.isEmpty())
        assertEquals(1, result.report.sourceFiles)
        assertTrue(result.report.declarations > 0)
    }

    // -- purity --------------------------------------------------------------------------------

    @Test
    fun `the same jar produces byte-identical entries every run`() {
        val once = harvested(Fixtures.kotlinSources).entries
        val twice = SourcesJarHarvester().let { assertIs<HarvestResult.Harvested>(it.harvest(Fixtures.kotlinSources)) }.entries
        assertEquals(fingerprint(once), fingerprint(twice))
        assertEquals(once, twice)
    }

    // -- offline -------------------------------------------------------------------------------

    @Test
    fun `everything it reads is already on disk`() {
        // Each fixture was resolved by the build into the Gradle module cache. The harvester is
        // handed a path and opens it; there is no coordinate to resolve and nothing to fetch.
        assertTrue(Fixtures.all.isNotEmpty())
        Fixtures.all.forEach {
            assertTrue(Files.isRegularFile(it), "$it should already be on disk")
            assertContains(it.toString(), "modules-2")
        }
    }
}
