package org.dependencyskills.codex.inference

import dscodex.dsc_embed
import dscodex.dsc_encoder_dim
import dscodex.dsc_encoder_load
import dscodex.dsc_encoder_pooling
import dscodex.dsc_free
import kotlinx.cinterop.COpaquePointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.FloatVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped

@OptIn(ExperimentalForeignApi::class)
actual fun openEncoder(modelPath: String, pooling: Pooling): TextEncoder =
    NativeLlamaEncoder(modelPath, pooling)

/**
 * The Kotlin/Native binding for the encoder face, linked in rather than loaded — the same
 * arrangement as [TextGenerator]'s, from the same header, so the two bindings cannot drift into
 * two descriptions of one contract.
 */
@OptIn(ExperimentalForeignApi::class)
private class NativeLlamaEncoder(modelPath: String, requested: Pooling) : TextEncoder {

    private var session: COpaquePointer?

    override val dimensions: Int
    override val pooling: Pooling

    init {
        if (requested == Pooling.None) {
            throw EncoderUnavailableException(
                "pooling None produces per-token vectors, not a sequence embedding",
            )
        }
        val handle = dsc_encoder_load(modelPath, requested.code)
            ?: throw EncoderUnavailableException("llama.cpp could not load the encoder at $modelPath")
        session = handle
        dimensions = dsc_encoder_dim(handle)
        val effective = dsc_encoder_pooling(handle)
        pooling = Pooling.ofCode(effective)
            ?: throw EncoderUnavailableException("llama.cpp reported an unknown pooling ($effective)")
        if (pooling != requested) {
            close()
            throw EncoderUnavailableException("asked for $requested pooling, got $pooling")
        }
    }

    override fun embed(text: String): FloatArray {
        val handle = session ?: throw EncoderUnavailableException("this encoder is closed")
        return memScoped {
            val out = allocArray<FloatVar>(dimensions)
            val width = dsc_embed(handle, text, out, dimensions)
            if (width < 0) throw EncoderUnavailableException("embedding failed ($width)")
            // A truncated vector is still a vector: it would index, score, and be wrong.
            if (width != dimensions) {
                throw EncoderUnavailableException("expected $dimensions dimensions, model produced $width")
            }
            FloatArray(dimensions) { out[it] }
        }
    }

    override fun close() {
        session?.let { dsc_free(it) }
        session = null
    }
}
