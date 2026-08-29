package org.dependencyskills.codex.inference

import dscodex.dsc_apply_template
import dscodex.dsc_free
import dscodex.dsc_generate
import dscodex.dsc_load
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.toKString

@OptIn(ExperimentalForeignApi::class)
actual fun openGenerator(modelPath: String, contextTokens: Int, gpuLayers: Int): TextGenerator =
    NativeLlamaGenerator(modelPath, contextTokens, gpuLayers)

/**
 * The Kotlin/Native binding.
 *
 * **The shim is linked into the binary, not loaded from it.** There is no library to extract, no
 * path to search and no jar to unpack — which is the whole reason the native targets exist. A
 * native CLI or a native server is one executable with llama.cpp inside it.
 *
 * The same four symbols the JVM target reaches through FFM, from the same header, so the two
 * bindings cannot drift apart into two descriptions of one contract.
 */
@OptIn(ExperimentalForeignApi::class)
private class NativeLlamaGenerator(
    modelPath: String,
    contextTokens: Int,
    gpuLayers: Int,
) : TextGenerator {

    // cinterop maps `const char *` to String, so nothing needs pinning on the way in.
    private var session: COpaquePointer? = dsc_load(modelPath, contextTokens, gpuLayers)
        ?: throw GeneratorUnavailableException("llama.cpp could not load the model at $modelPath")

    override fun generate(prompt: String, maxTokens: Int): String {
        val handle = session ?: throw GeneratorUnavailableException("this generator is closed")
        return memScoped {
            // Templating and generation are separate calls so a failure to template is not
            // reported as a failure to generate. Different faults, different fixes.
            val templateBuffer = allocArray<ByteVar>(TEMPLATE_BYTES)
            val templated = dsc_apply_template(handle, prompt, templateBuffer, TEMPLATE_BYTES)
                .let { written ->
                    // A model with no chat template is a base model, not an error: the bare
                    // prompt is the right thing to send it.
                    if (written in 1..TEMPLATE_BYTES) templateBuffer.toKString() else prompt
                }

            val out = allocArray<ByteVar>(OUTPUT_BYTES)
            val written = dsc_generate(handle, templated, out, OUTPUT_BYTES, maxTokens)
            if (written < 0) throw GeneratorUnavailableException("generation failed ($written)")
            out.toKString()
        }
    }

    override fun close() {
        session?.let { dsc_free(it) }
        session = null
    }

    private companion object {
        const val OUTPUT_BYTES = 64 * 1024
        const val TEMPLATE_BYTES = 256 * 1024
    }
}
