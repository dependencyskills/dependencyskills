package org.dependencyskills.codex.core

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What happens when a summariser's output reaches the store.
 *
 * The interesting case is the one that looks like an edge: an entry the *classifier* already
 * degraded, for which the summariser then produces a perfectly good sentence. Storing it would
 * quietly overturn a safety decision made for a different reason, and the two decisions are not
 * interchangeable.
 */
class RecordSummaryTest {

    private val maven = Coordinate("maven", "com.example.acme:acme-core:1.0.0")

    private fun entry(symbol: String, state: EntryState = EntryState.Whole) = NewEntry(
        symbol = "com.example.acme.$symbol",
        signature = "fun $symbol(input: String): String",
        doc = "Does the documented thing with the input it is given.",
        lang = "kotlin",
        docFormat = "kdoc",
        provenance = Provenance(extractor = "tree-sitter"),
        state = state,
    )

    private fun store() = Codex.open(createTempDirectory("summary").resolve("codex.db"))

    @Test
    fun `a verified rewrite becomes the entry's displayable prose`() {
        store().use { codex ->
            codex.put(maven, listOf(entry("run")))
            val id = codex.entriesOf(maven).single().id

            assertEquals(
                SummaryOutcome.Stored,
                codex.recordSummary(id, "Runs the documented thing and returns what it produced.", "model-1.0"),
            )
            val stored = codex.entry(id)!!
            assertEquals("Runs the documented thing and returns what it produced.", stored.rewrite)
            assertEquals("model-1.0", stored.provenance.summariser)
            assertEquals(EntryState.Whole, stored.state)
        }
    }

    @Test
    fun `nothing surviving verification degrades the entry and keeps its key`() {
        store().use { codex ->
            codex.put(maven, listOf(entry("run")))
            val id = codex.entriesOf(maven).single().id

            assertEquals(SummaryOutcome.Degraded, codex.recordSummary(id, null, "model-1.0"))
            val stored = codex.entry(id)!!
            assertEquals(EntryState.Degraded, stored.state)
            assertNull(stored.rewrite, "a degraded entry has no displayable prose")
            // The retrieval key is untouched. An entry with no key would be indistinguishable
            // from one silently dropped, which is the failure this state exists to avoid.
            assertTrue(codex.rawDocumentation(id)!!.isNotEmpty())
            assertEquals("model-1.0", stored.provenance.summariser)
        }
    }

    @Test
    fun `a good rewrite does not un-degrade what the classifier flagged`() {
        // The two degradations answer different questions. The classifier's is about what the
        // LIBRARY wrote; verification's is about what the MODEL wrote. Paraphrasing suspect prose
        // well does not make the prose less suspect, so this must not promote the entry.
        store().use { codex ->
            codex.put(maven, listOf(entry("run", state = EntryState.Degraded)))
            val id = codex.entriesOf(maven).single().id

            assertEquals(
                SummaryOutcome.Withheld,
                codex.recordSummary(id, "Runs the documented thing and returns what it produced.", "model-1.0"),
            )
            val stored = codex.entry(id)!!
            assertEquals(EntryState.Degraded, stored.state)
            assertNull(stored.rewrite, "the withheld rewrite must not reach the displayable column")
        }
    }

    @Test
    fun `the model is recorded even when its output was withheld`() {
        // Provenance is about what ran, not about what survived. An entry whose summariser field
        // is empty is one nothing has looked at; that must stay distinguishable from one whose
        // output was refused.
        store().use { codex ->
            codex.put(maven, listOf(entry("run", state = EntryState.Degraded)))
            val id = codex.entriesOf(maven).single().id
            codex.recordSummary(id, "A perfectly good sentence about the capability.", "model-1.0")
            assertEquals("model-1.0", codex.entry(id)!!.provenance.summariser)
        }
    }

    @Test
    fun `an unknown entry is reported rather than written as nothing`() {
        store().use { codex ->
            assertEquals(
                SummaryOutcome.Unknown,
                codex.recordSummary("not-an-id", "A sentence.", "model-1.0"),
            )
        }
    }

    @Test
    fun `re-summarising with a newer model replaces both the sentence and the provenance`() {
        // A model change is invalidated selectively rather than by emptying the store, so the
        // second pass has to overwrite the first cleanly.
        store().use { codex ->
            codex.put(maven, listOf(entry("run")))
            val id = codex.entriesOf(maven).single().id
            codex.recordSummary(id, "The first sentence about this capability.", "model-1.0")
            codex.recordSummary(id, "The second sentence about this capability.", "model-2.0")

            val stored = codex.entry(id)!!
            assertEquals("The second sentence about this capability.", stored.rewrite)
            assertEquals("model-2.0", stored.provenance.summariser)
        }
    }

    @Test
    fun `a later failure removes the earlier sentence rather than leaving it behind`() {
        // The trap: a re-run whose output is refused must not leave the previous model's prose in
        // place while recording the new model's name. That entry would then display one model's
        // sentence under another model's provenance.
        store().use { codex ->
            codex.put(maven, listOf(entry("run")))
            val id = codex.entriesOf(maven).single().id
            codex.recordSummary(id, "The first sentence about this capability.", "model-1.0")

            assertEquals(SummaryOutcome.Degraded, codex.recordSummary(id, null, "model-2.0"))
            val stored = codex.entry(id)!!
            assertNull(stored.rewrite)
            assertEquals("model-2.0", stored.provenance.summariser)
            assertEquals(EntryState.Degraded, stored.state)
        }
    }
}
