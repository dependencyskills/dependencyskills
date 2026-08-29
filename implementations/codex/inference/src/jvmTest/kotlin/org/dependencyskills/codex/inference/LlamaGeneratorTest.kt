package org.dependencyskills.codex.inference

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
 * machine. Without it these fail loudly rather than skipping — a test that passes when its
 * subject is absent is the failure this project keeps re-learning.
 */
class LlamaGeneratorTest {

    private val model: String = System.getProperty("dscodex.test.model")
        ?: error("set -Ddscodex.test.model to a .gguf file")

    @Test
    fun `loads a model and generates from it, in this process`() {
        openGenerator(model, contextTokens = 2048).use { generator ->
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
        openGenerator(model, contextTokens = 2048).use { generator ->
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
        openGenerator(model, contextTokens = 2048).use { generator ->
            val started = System.nanoTime()
            repeat(3) { generator.generate("Name one colour.", maxTokens = 10) }
            val each = (System.nanoTime() - started) / 3 / 1_000_000
            println("--- $each ms per generation after load ---")
            assertTrue(each < 5_000, "expected sub-second generations, got ${each}ms")
        }
    }
}
