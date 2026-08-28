package org.dependencyskills.codex.classifier

import java.io.DataInputStream
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Which register an instruction is hiding in — `precondition`, `deprecation`, `policy`, and the
 * rest of the shapes real API documentation is written in.
 *
 * **Kept separate from the decision deliberately.** Splitting the same call across nine classes
 * was measured catching 75.9% where the binary model catches 96%, at the same false-positive
 * cost, so attribution is not free and must not be what decides. This runs only on a sentence
 * the binary model has already flagged, and its answer is a label on that decision.
 *
 * It shares the binary model's vocabulary, so both see exactly the same features — one analyser
 * to keep honest rather than two that can drift apart.
 */
internal class RegisterModel(
    private val classes: List<String>,
    private val intercept: FloatArray,
    private val coef: Array<FloatArray>,
    private val index: Map<String, Int>,
) {

    /** The register, or null when the best class is `clean` — the model declining to attribute. */
    fun registerOf(sentence: String): String? {
        val counts = HashMap<String, Int>()
        CharNgrams.of(sentence, counts)
        val weights = HashMap<Int, Double>(counts.size)
        var norm = 0.0
        counts.forEach { (gram, count) ->
            val at = index[gram] ?: return@forEach
            val weight = (1.0 + Math.log(count.toDouble())) * idfAt(at)
            weights[at] = weight
            norm += weight * weight
        }
        if (norm == 0.0) return null
        val scale = 1.0 / Math.sqrt(norm)
        var best = -1
        var bestScore = Double.NEGATIVE_INFINITY
        for (c in classes.indices) {
            var dot = intercept[c].toDouble()
            val row = coef[c]
            weights.forEach { (at, weight) -> dot += weight * scale * row[at] }
            if (dot > bestScore) {
                bestScore = dot
                best = c
            }
        }
        return classes[best].takeUnless { it == CLEAN }
    }

    /** Set by [shipped]; the two models are fitted on one vocabulary and share its IDF. */
    private lateinit var idf: FloatArray

    private fun idfAt(at: Int) = idf[at]

    companion object {
        private const val MAGIC = "DSPR"
        private const val CLEAN = "clean"
        const val RESOURCE = "/dependencyskills/classifier/register.model"

        /**
         * The attribution model, or null when this build ships without one. Null is a supported
         * state: the decision does not depend on it, and a missing label is better than a
         * fabricated one.
         */
        fun shipped(): RegisterModel? {
            val stream = RegisterModel::class.java.getResourceAsStream(RESOURCE) ?: return null
            val binary = ProseModel.shipped()
            return stream.use { load(it, binary) }
        }

        fun load(stream: InputStream, vocabulary: ProseModel): RegisterModel =
            DataInputStream(stream.buffered()).use { input ->
                val magic = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
                require(magic == MAGIC) { "not a register model: magic was '$magic'" }
                require(input.readIntLe() == 1) { "unsupported register model format" }
                val classes = List(input.readIntLe()) {
                    String(ByteArray(input.readIntLe()).also { b -> input.readFully(b) }, Charsets.UTF_8)
                }
                val terms = input.readIntLe()
                require(terms == vocabulary.terms) {
                    "the register model was fitted on $terms terms and the prose model on " +
                        "${vocabulary.terms}; they must share a vocabulary"
                }
                val intercept = input.readFloats(classes.size)
                val coef = Array(classes.size) { input.readFloats(terms) }
                RegisterModel(classes, intercept, coef, vocabulary.indexView()).also {
                    it.idf = vocabulary.idfView()
                }
            }

        private fun DataInputStream.readIntLe(): Int = Integer.reverseBytes(readInt())

        private fun DataInputStream.readFloats(count: Int): FloatArray {
            val bytes = ByteArray(count * 4)
            readFully(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(count) { buffer.getFloat() }
        }
    }
}
