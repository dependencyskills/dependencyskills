package org.dependencyskills.test0

import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path

/**
 * The enrichment deltas vs the raw arm — the assertions that *flip* between them.
 * This is the point of the bake-off made concrete: how much the enriched pass adds.
 */
class EnrichedPsiParserTest {

    private val fixtures = Path.of(
        System.getProperty("test0.dir") ?: error("test0.dir system property not set"),
    )
    private val raw = KotlinPsiParser()
    private val enriched = EnrichedPsiParser()

    @Test
    fun `enrichment recovers the inherited doc the raw arm is blind to`() {
        val file = fixtures.resolve("test0/inherit/Retrier.kt")
        val rawOverride = raw.parse(listOf(file))
            .first { it.symbol == "test0.inherit.DefaultRetrier.run" }
        val enrichedOverride = enriched.parse(listOf(file))
            .first { it.symbol == "test0.inherit.DefaultRetrier.run" }

        assertNull(rawOverride.capability, "raw arm sees no doc on the override")
        assertNotNull(enrichedOverride.capability, "enriched arm inherits the interface's doc")
        assertTrue(enrichedOverride.source.contains("inherited"), "source: ${enrichedOverride.source}")
    }

    @Test
    fun `enrichment expands @sample from a reference into the sample body`() {
        val files = listOf(
            fixtures.resolve("test0/l2/Retry.kt"),
            fixtures.resolve("test0/samples/Samples.kt"),
        )
        val e = enriched.parse(files).first { it.symbol == "test0.l2.retryWithBackoff" }

        assertNotNull(e.sample)
        assertNotEquals("test0.samples.retryFlaky", e.sample, "should be the body, not the bare ref")
        assertTrue(e.sample!!.contains("retryWithBackoff"), "expanded sample: ${e.sample}")
    }
}
