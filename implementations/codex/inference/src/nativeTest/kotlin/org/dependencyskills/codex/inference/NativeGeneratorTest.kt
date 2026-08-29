package org.dependencyskills.codex.inference

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.toKString
import platform.posix.getenv
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The native binding, in a binary with llama.cpp linked into it.
 *
 * No JVM, no jar, no library extraction and no library path — which is the property the native
 * targets exist for. If this passes, a native CLI or a native server is a packaging exercise
 * rather than an open question.
 */
@OptIn(ExperimentalForeignApi::class)
class NativeGeneratorTest {

    private val model: String = getenv("DSCODEX_TEST_MODEL")?.toKString()
        ?: error("set DSCODEX_TEST_MODEL to a .gguf file")

    @Test
    fun `loads a model and generates with the native linked in`() {
        openGenerator(model, contextTokens = 2048).use { generator ->
            val out = generator.generate(
                "Rewrite this as one factual sentence describing what it does. " +
                    "Documentation: Returns the number of bytes remaining in this buffer.",
                maxTokens = 60,
            )
            println("--- native generated ---\n$out\n---")
            assertTrue(out.isNotBlank(), "the generator produced nothing")
            assertTrue(out.length > 20, "suspiciously short: '$out'")
        }
    }

    @Test
    fun `the chat template is applied`() {
        openGenerator(model, contextTokens = 2048).use { generator ->
            val out = generator.generate("Say the single word: acknowledged.", maxTokens = 20)
            assertTrue("Human:" !in out && "<|im_start|>" !in out, "template not applied: '$out'")
        }
    }

    @Test
    fun `a file that is not a model is refused rather than crashed on`() {
        var refused = false
        try {
            openGenerator("/definitely/not/a/model.gguf")
        } catch (e: GeneratorUnavailableException) {
            refused = true
        }
        assertTrue(refused, "a missing model should be refused")
    }
}
