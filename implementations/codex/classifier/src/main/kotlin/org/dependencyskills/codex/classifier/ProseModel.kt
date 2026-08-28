package org.dependencyskills.codex.classifier

import java.io.DataInputStream
import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * The fitted weights, as a term-frequency table and a dot product.
 *
 * There is no learning here and there is not meant to be. `tools/train.py` fits the model against
 * the corpus and commits the result, so the runtime carries no scikit-learn, no network and no
 * download — which is what makes this classifier usable while the encoder and the summariser are
 * still waiting on a runtime.
 *
 * The file format is documented in the module README. It is read by this class and nothing else.
 */
internal class ProseModel(
    private val index: Map<String, Int>,
    private val idf: FloatArray,
    private val coef: FloatArray,
    private val intercept: Float,
    val thresholds: Map<String, Float>,
) {

    val terms: Int get() = idf.size

    /** The shared vocabulary and IDF, for the attribution model fitted on the same terms. */
    internal fun indexView(): Map<String, Int> = index
    internal fun idfView(): FloatArray = idf

    /**
     * The margin for one sentence: positive means the model puts it on the payload side of its
     * own boundary, but the decision is made against a calibrated threshold rather than against
     * zero, so this number is only meaningful next to one.
     *
     * Reproduces scikit-learn's `TfidfVectorizer(sublinear_tf=True)` followed by
     * `LogisticRegression.decision_function`: sublinear term frequency, smoothed IDF, L2
     * normalisation, then a dot product. Every one of those has to match or the shipped model
     * quietly scores differently from the one that was measured.
     */
    fun score(sentence: String): Double {
        val counts = HashMap<String, Int>()
        CharNgrams.of(sentence, counts)
        var norm = 0.0
        var dot = 0.0
        counts.forEach { (gram, count) ->
            val at = index[gram] ?: return@forEach
            val weight = (1.0 + Math.log(count.toDouble())) * idf[at]
            norm += weight * weight
            dot += weight * coef[at]
        }
        if (norm == 0.0) return intercept.toDouble()
        return dot / Math.sqrt(norm) + intercept
    }

    companion object {
        private const val MAGIC = "DSPC"
        const val FORMAT_VERSION = 1
        const val RESOURCE = "/dependencyskills/classifier/prose.model"

        fun load(stream: InputStream): ProseModel = DataInputStream(stream.buffered()).use { input ->
            val magic = ByteArray(4).also { input.readFully(it) }.toString(Charsets.US_ASCII)
            require(magic == MAGIC) { "not a prose model: magic was '$magic'" }
            val version = input.readIntLe()
            require(version == FORMAT_VERSION) {
                "prose model is format $version; this build reads $FORMAT_VERSION"
            }
            val intercept = Float.fromBits(input.readIntLe())
            val termCount = input.readIntLe()
            val blob = ByteArray(input.readIntLe()).also { input.readFully(it) }
            val index = HashMap<String, Int>(termCount * 2)
            // Split on the byte rather than allocating one huge String and splitting that: the
            // blob is several megabytes and the intermediate would be another copy of it.
            var at = 0
            var start = 0
            for (i in blob.indices) {
                if (blob[i] == NEWLINE) {
                    index[String(blob, start, i - start, Charsets.UTF_8)] = at++
                    start = i + 1
                }
            }
            if (start < blob.size) index[String(blob, start, blob.size - start, Charsets.UTF_8)] = at++
            require(at == termCount) { "prose model declares $termCount terms and holds $at" }

            val idf = input.readFloats(termCount)
            val coef = input.readFloats(termCount)
            val thresholds = HashMap<String, Float>()
            repeat(input.readIntLe()) {
                val name = ByteArray(input.readIntLe()).also { b -> input.readFully(b) }
                thresholds[String(name, Charsets.UTF_8)] = Float.fromBits(input.readIntLe())
            }
            ProseModel(index, idf, coef, intercept, thresholds)
        }

        /** The model on the classpath. */
        fun shipped(): ProseModel =
            ProseModel::class.java.getResourceAsStream(RESOURCE)
                ?.let { load(it) }
                ?: error("the prose model is missing from the classpath at $RESOURCE")

        private const val NEWLINE = '\n'.code.toByte()

        /** The file is little-endian, because that is what numpy wrote. */
        private fun DataInputStream.readIntLe(): Int = Integer.reverseBytes(readInt())

        private fun DataInputStream.readFloats(count: Int): FloatArray {
            val bytes = ByteArray(count * 4)
            readFully(bytes)
            val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
            return FloatArray(count) { buffer.getFloat() }
        }

        private fun DataInputStream.readFully(into: ByteArray) {
            var read = 0
            while (read < into.size) {
                val n = read(into, read, into.size - read)
                if (n < 0) throw EOFException("prose model ended after $read of ${into.size} bytes")
                read += n
            }
        }
    }
}
