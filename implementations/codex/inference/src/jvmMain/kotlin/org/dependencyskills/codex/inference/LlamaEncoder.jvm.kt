package org.dependencyskills.codex.inference

import java.lang.foreign.Arena
import java.lang.foreign.MemorySegment
import java.lang.foreign.ValueLayout.JAVA_FLOAT
import java.nio.file.Files
import java.nio.file.Path

actual fun openEncoder(modelPath: String, pooling: Pooling): TextEncoder =
    LlamaEncoder(modelPath, pooling)

/**
 * The JVM binding for the encoder face, over the same flat ABI and the same loaded library as
 * [TextGenerator]. One native dependency serves both faces of the index — RAD-0054.
 */
private class LlamaEncoder(modelPath: String, requested: Pooling) : TextEncoder {

    private val arena = Arena.ofShared()
    private val session: MemorySegment

    override val dimensions: Int
    override val pooling: Pooling

    init {
        if (requested == Pooling.None) {
            throw EncoderUnavailableException(
                "pooling None produces per-token vectors, not a sequence embedding",
            )
        }
        if (!Files.isRegularFile(Path.of(modelPath))) {
            throw EncoderUnavailableException("no model file at $modelPath")
        }
        val handle = arena.allocateFrom(modelPath).let { path ->
            Native.encoderLoad.invokeExact(path, requested.code) as MemorySegment
        }
        if (handle.address() == 0L) {
            arena.close()
            throw EncoderUnavailableException("llama.cpp could not load the encoder at $modelPath")
        }
        session = handle
        dimensions = Native.encoderDim.invokeExact(session) as Int
        // Read back, not assumed. The runtime resolves pooling against the model's own metadata
        // and this is the only place that can say which side won.
        val effective = Native.encoderPooling.invokeExact(session) as Int
        pooling = Pooling.ofCode(effective)
            ?: throw EncoderUnavailableException("llama.cpp reported an unknown pooling ($effective)")
        if (pooling != requested) {
            close()
            throw EncoderUnavailableException("asked for $requested pooling, got $pooling")
        }
    }

    override fun embed(text: String): FloatArray = Arena.ofConfined().use { call ->
        val out = call.allocate(JAVA_FLOAT, dimensions.toLong())
        val width = Native.embed.invokeExact(session, call.allocateFrom(text), out, dimensions) as Int
        if (width < 0) throw EncoderUnavailableException("embedding failed ($width)")
        // A model wider than the buffer would have been silently truncated, and a truncated
        // vector is still a vector - it would index, score, and be wrong.
        if (width != dimensions) {
            throw EncoderUnavailableException("expected $dimensions dimensions, model produced $width")
        }
        out.toArray(JAVA_FLOAT)
    }

    override fun close() {
        Native.free.invokeExact(session)
        arena.close()
    }
}
