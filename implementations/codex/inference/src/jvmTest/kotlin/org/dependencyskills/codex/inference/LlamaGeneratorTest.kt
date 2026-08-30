package org.dependencyskills.codex.inference

import org.junit.jupiter.api.Assumptions.assumeTrue
import java.nio.file.Files
import java.nio.file.Path
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The binding, against a real model.
 *
 * The model is not committed and not downloaded: `dscodex.test.model` points at one on the
 * machine, or `DSCODEX_TEST_MODEL` names it in the environment.
 *
 * Without one these are reported **skipped, with the reason** — not passed. The rule this was
 * written to hold is that a test must never pass when its subject is absent, and a skip does not
 * pass: it is counted separately and it says why. What it used to do instead was fail, which meant
 * every build on a machine with no model was red, and a suite that is always red tells you nothing
 * on the day it breaks for real.
 *
 * The two tests that need no model — a missing file and a file that is not a model — run
 * regardless. They were only ever gated because [model] was resolved in the constructor, so the
 * class could not be built without the very thing they exist to prove is unnecessary.
 */
class LlamaGeneratorTest {

    /** Null when none is configured, so the tests that do not need one still construct. */
    private val model: String? =
        (System.getProperty("dscodex.test.model") ?: System.getenv("DSCODEX_TEST_MODEL"))
            ?.trim()?.takeIf { it.isNotEmpty() }

    /** The model, or an assumption that reports this one test as skipped and says why. */
    private fun model(): String {
        assumeTrue(
            model != null,
            "no generative model configured: set -Ddscodex.test.model=<file.gguf> or DSCODEX_TEST_MODEL",
        )
        return model!!
    }

    @Test
    fun `loads a model and generates from it, in this process`() {
        openGenerator(model(), contextTokens = 2048).use { generator ->
            val out = generator.generate(
                "Rewrite this as one factual sentence describing what it does. " +
                    "Documentation: Returns the number of bytes remaining in this buffer.",
                maxTokens = 60,
            )
            println("--- generated ---\n$out\n---")
            assertTrue(out.isNotBlank(), "the generator produced nothing")
            assertTrue(out.length > 20, "suspiciously short: '$out'")
        }
    }

    @Test
    fun `the chat template is applied, so the model answers rather than continues`() {
        // Untemplated, an instruction-tuned model runs on into an invented conversation and emits
        // turns of its own. That is what this asserts is not happening.
        openGenerator(model(), contextTokens = 2048).use { generator ->
            val out = generator.generate("Say the single word: acknowledged.", maxTokens = 20)
            assertTrue("Human:" !in out && "<|im_start|>" !in out, "template not applied: '$out'")
        }
    }

    @Test
    fun `a missing model is refused, not crashed on`() {
        val absent = Files.createTempDirectory("dscodex").resolve("not-a-model.gguf")
        val thrown = assertFailsWith<GeneratorUnavailableException> { openGenerator(absent.toString()) }
        assertContains(thrown.message!!, "no model file")
    }

    @Test
    fun `a file that is not a model is refused, not crashed on`() {
        val bogus = Files.createTempDirectory("dscodex").resolve("bogus.gguf")
        Files.writeString(bogus, "this is not a GGUF file")
        assertFailsWith<GeneratorUnavailableException> { openGenerator(bogus.toString()) }
    }

    @Test
    fun `the same generator serves many prompts without reloading`() {
        // The property the whole module exists for: load once, stream through. A generator that
        // reloads per call turns 25 minutes of harvest into 21 hours.
        openGenerator(model(), contextTokens = 2048).use { generator ->
            val started = System.nanoTime()
            repeat(3) { generator.generate("Name one colour.", maxTokens = 10) }
            val each = (System.nanoTime() - started) / 3 / 1_000_000
            println("--- $each ms per generation after load ---")
            assertTrue(each < 5_000, "expected sub-second generations, got ${each}ms")
        }
    }
}
