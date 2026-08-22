package org.dependencyskills.test0

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The raw arm, running for real. These assertions ARE the bake-off findings for
 * the PSI/raw side: what a caller-facing capability looks like at each doc level,
 * and where raw parsing goes blind (inherited docs) — the gap Dokka will fill.
 */
class KotlinPsiParserTest {

    private val fixtures = Path.of(
        System.getProperty("test0.dir") ?: error("test0.dir system property not set"),
    )
    private val parser = KotlinPsiParser()

    private fun entries(rel: String) = parser.parse(listOf(fixtures.resolve(rel)))
    private fun retry(level: String) =
        entries("test0/$level/Retry.kt").first { it.symbol.endsWith(".retryWithBackoff") }

    @Test
    fun `L0 - the symbol is there, but there is no capability to read`() {
        val e = retry("l0")
        assertTrue(e.signature.contains("retryWithBackoff"), "signature: ${e.signature}")
        assertNull(e.capability)
        assertTrue(e.triggers.isEmpty())
        assertEquals(Tier.Discovered, e.tier)
        assertEquals("none", e.source)
    }

    @Test
    fun `L2 - raw prose becomes the capability, and @since is picked up`() {
        val e = retry("l2")
        assertNotNull(e.capability)
        assertTrue(e.capability!!.contains("retry", ignoreCase = true), "capability: ${e.capability}")
        assertEquals("1.2.0", e.since)
    }

    @Test
    fun `L3 - designed tier - triggers, category, and an authored capability`() {
        val e = retry("l3")
        assertEquals(Tier.Designed, e.tier)
        assertEquals("resilience", e.category)
        assertTrue(e.triggers.any { it.contains("backoff") }, "triggers: ${e.triggers}")
        assertEquals("retry a failed operation with exponential backoff", e.capability)
    }

    @Test
    fun `the opaque name at L0 is the worst case - no signal at all`() {
        val e = entries("test0/l0/Retry.kt").first { it.symbol.endsWith(".apply") }
        assertNull(e.capability)
        assertTrue(e.triggers.isEmpty())
    }

    @Test
    fun `raw parsing is blind to inherited docs - the gap Dokka fills`() {
        val es = entries("test0/inherit/Retrier.kt")
        val ifaceRun = es.first { it.symbol == "test0.inherit.Retrier.run" }
        val overrideRun = es.first { it.symbol == "test0.inherit.DefaultRetrier.run" }
        assertNotNull(ifaceRun.capability, "the interface method is documented")
        assertNull(overrideRun.capability, "the override has no own KDoc; raw parsing cannot inherit it")
    }
}
