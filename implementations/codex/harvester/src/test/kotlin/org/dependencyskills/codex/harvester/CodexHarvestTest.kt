package org.dependencyskills.codex.harvester

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import org.dependencyskills.codex.core.HarvestState
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CodexHarvestTest {

    private fun store() = Codex.open(createTempDirectory("codex").resolve("codex.db"))

    private val slf4j = Coordinate("maven", "org.slf4j:slf4j-api:2.0.17")
    private val relocated = Coordinate("maven", "com.example.acme:slf4j-api-repackaged:2.0.17")
    private val serialization = Coordinate("maven", "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0")

    @Test
    fun `entries land in the store and survive a round trip`() {
        store().use { codex ->
            val result = assertIs<HarvestResult.Harvested>(codex.harvest(slf4j, Fixtures.javaSources))
            val stored = codex.entriesOf(slf4j)
            assertEquals(result.entries.size, stored.size)
            assertEquals(
                result.entries.map { it.symbol }.toSortedSet(),
                stored.map { it.symbol }.toSortedSet(),
            )
            val logger = stored.first { it.symbol == "org.slf4j.ILoggerFactory.getLogger" }
            assertEquals("java", logger.lang)
            assertEquals("javadoc", logger.docFormat)
            assertEquals(SourcesJarHarvester.EXTRACTOR, logger.provenance.extractor)
            assertEquals(setOf(slf4j), logger.coordinates)
        }
    }

    @Test
    fun `the same content from two artifacts collapses to one entry owned by both`() {
        store().use { codex ->
            // A relocated or repackaged artifact republishing identical source is the ordinary
            // case. RAD-0041 ruled out choosing a winner here: whichever build ran first would
            // decide, and a project depending only on the loser would see nothing at all.
            codex.harvest(slf4j, Fixtures.javaSources)
            val afterFirst = codex.entryCount()
            codex.harvest(relocated, Fixtures.javaSources)

            assertEquals(afterFirst, codex.entryCount(), "identical content must not create a second entry")
            assertEquals(afterFirst, codex.entriesOf(relocated).size)

            val shared = codex.entriesOf(relocated).first { it.symbol == "org.slf4j.ILoggerFactory.getLogger" }
            assertEquals(setOf(slf4j, relocated), shared.coordinates)
        }
    }

    @Test
    fun `harvest is a pure function of the jar`() {
        val empty = store().use { codex ->
            assertIs<HarvestResult.Harvested>(codex.harvest(slf4j, Fixtures.javaSources)).entries
        }
        val loaded = store().use { codex ->
            // A store that already holds this jar's entries, another artifact's entries, and the
            // coordinate itself. None of it may reach the harvester's output.
            codex.harvest(slf4j, Fixtures.javaSources)
            codex.harvest(relocated, Fixtures.javaSources)
            codex.harvest(serialization, Fixtures.kotlinSources)
            assertIs<HarvestResult.Harvested>(codex.harvest(slf4j, Fixtures.javaSources)).entries
        }
        assertEquals(fingerprint(empty), fingerprint(loaded))
        assertEquals(empty, loaded)
    }

    @Test
    fun `re-harvesting the same coordinate leaves the store where it was`() {
        store().use { codex ->
            codex.harvest(slf4j, Fixtures.javaSources)
            val after = codex.entryCount()
            repeat(2) { codex.harvest(slf4j, Fixtures.javaSources) }
            assertEquals(after, codex.entryCount())
            assertEquals(after, codex.entriesOf(slf4j).size)
        }
    }

    // -- states ---------------------------------------------------------------------------------

    @Test
    fun `a harvested coordinate is indexed`() {
        store().use { codex ->
            codex.harvest(slf4j, Fixtures.javaSources)
            assertEquals(HarvestState.Indexed, assertNotNull(codex.coordinate(slf4j)).state)
        }
    }

    @Test
    fun `a coordinate whose archive holds no source is recorded as such, not left pending`() {
        store().use { codex ->
            assertIs<HarvestResult.NoSource>(codex.harvest(slf4j, Fixtures.noSources))
            val record = assertNotNull(codex.coordinate(slf4j), "a sourceless coordinate must still be a record")
            assertEquals(HarvestState.NoSource, record.state)
            assertNotNull(record.lastAttempt)
            // The distinction that keeps it from being re-queued on every build for ever.
            assertTrue(codex.coordinatesIn(HarvestState.Pending).isEmpty())
            assertEquals(listOf(slf4j), codex.coordinatesIn(HarvestState.NoSource).map { it.coordinate })
        }
    }

    @Test
    fun `a coordinate that publishes no sources artifact at all is recorded the same way`() {
        store().use { codex ->
            codex.noSourcesPublished(slf4j)
            assertEquals(HarvestState.NoSource, assertNotNull(codex.coordinate(slf4j)).state)
        }
    }

    @Test
    fun `a failed harvest stays failed rather than becoming a sourceless artifact`() {
        store().use { codex ->
            val broken = createTempDirectory("harvest").resolve("truncated-sources.jar")
            Files.write(broken, byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00, 0x00))
            assertIs<HarvestResult.Failed>(codex.harvest(slf4j, broken))
            assertEquals(HarvestState.Failed, assertNotNull(codex.coordinate(slf4j)).state)
            // Failed may succeed later; NoSource will not. Conflating them either re-tries for
            // ever or gives up on an artifact that was only half-downloaded.
            assertTrue(codex.coordinatesIn(HarvestState.NoSource).isEmpty())
        }
    }

    @Test
    fun `the raw documentation never comes back out`() {
        store().use { codex ->
            codex.harvest(slf4j, Fixtures.javaSources)
            val stored = codex.entriesOf(slf4j).first { it.symbol == "org.slf4j.ILoggerFactory.getLogger" }
            // Structural rather than conventional: the Entry type has no field to hold it. What
            // a reader may be shown is the rewrite, and nothing has written one yet.
            assertEquals(null, stored.rewrite)
            assertContains(stored.signature, "getLogger")
        }
    }
}
