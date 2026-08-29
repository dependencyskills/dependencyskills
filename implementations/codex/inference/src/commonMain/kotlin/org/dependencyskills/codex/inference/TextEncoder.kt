package org.dependencyskills.codex.inference

/**
 * How a model's per-token outputs become one vector.
 *
 * **Never defaulted, anywhere in this file or below it.** Pooling is a per-model property that
 * cannot be inferred from the model: `bge-small-en-v1.5` documents CLS and RAD-0048 measured mean
 * as the better one for this project's use, so the model's own declaration is not the answer. The
 * cost of guessing is not a failure — it is cosine 0.93 against 0.99999, vectors of the right
 * shape in the wrong basis, and nothing anywhere that would notice.
 *
 * The [code] is llama.cpp's `llama_pooling_type` and is written out rather than taken from
 * `ordinal`, so reordering this enum cannot silently repool an index.
 */
enum class Pooling(val code: Int) {
    /** No pooling: the model returns per-token vectors and there is nothing to compare. */
    None(0),
    Mean(1),
    Cls(2),
    Last(3);

    companion object {
        fun ofCode(code: Int): Pooling? = entries.firstOrNull { it.code == code }
    }
}

/**
 * A local text encoder, loaded once and used many times.
 *
 * The same reasoning as [TextGenerator]: embedding is a batch job at harvest — one resolved graph
 * is 14,899 documented declarations, each embedded twice under the two-faced index — and a model
 * reloaded per call pays for itself every time.
 *
 * Deliberately tiny, and for the same reason: no network, no tools, nothing to reach.
 */
interface TextEncoder : AutoCloseable {

    /** The width of the vectors this produces. An index mixing widths is not an index. */
    val dimensions: Int

    /**
     * The pooling actually in effect, read back from the runtime rather than remembered.
     *
     * This is what makes "vectors under different pooling never share an index" assertable
     * instead of conventional. Compare it against what the model was published with — the
     * encoder artifact declares that in its jar manifest as `Encoder-Pooling`.
     */
    val pooling: Pooling

    /** Embeds one string. The vector is pooled and **not** normalised; the index decides that. */
    fun embed(text: String): FloatArray
}

/** Opening an encoder failed. Callers degrade rather than propagate: the index is an aid. */
class EncoderUnavailableException(message: String, cause: Throwable? = null) :
    RuntimeException(message, cause)

/**
 * Opens an encoder over a model file.
 *
 * [pooling] has no default and [Pooling.None] is refused: it yields per-token vectors, so there
 * is no sequence embedding to return and the failure would otherwise surface far from its cause.
 */
expect fun openEncoder(modelPath: String, pooling: Pooling): TextEncoder
