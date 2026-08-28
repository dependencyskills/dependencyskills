package org.dependencyskills.codex.classifier

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * The JVM classifier against scikit-learn's own scores for the same sentences.
 *
 * This is the test the module exists to have. Everything measured about this classifier was
 * measured in Python, and what ships is a reimplementation of that arithmetic — sublinear term
 * frequency, smoothed IDF, L2 normalisation, a dot product. Get any one of those subtly wrong and
 * nothing fails: the model simply scores differently from the one the write-up describes, and
 * every number in it quietly stops being about what runs.
 *
 * The fixture is produced by `tools/golden.py`, which reads the shipped binary back rather than
 * scoring from the objects that wrote it — so a bug in the writer is caught here too.
 */
class ParityTest {

    private val golden = Golden.load()

    @Test
    fun `every golden sentence scores the same as scikit-learn`() {
        val model = ProseModel.shipped()
        assertTrue(golden.cases.size > 100, "expected a real fixture; found ${golden.cases.size}")
        var worst = 0.0
        golden.cases.forEach { case ->
            val got = model.score(case.text)
            worst = maxOf(worst, abs(got - case.score))
        }
        // Float32 weights read into double arithmetic: the difference is rounding, not method.
        assertTrue(worst < 1e-6, "worst divergence from scikit-learn was $worst")
    }

    @Test
    fun `the shipped model is the one the fixture was cut from`() {
        val model = ProseModel.shipped()
        assertEquals(golden.terms, model.terms)
        golden.thresholds.forEach { (convention, threshold) ->
            assertEquals(threshold, model.thresholds[convention]!!.toDouble(), 1e-6)
        }
    }

    @Test
    fun `the known-bad payloads score over every shipped threshold`() {
        // The three payloads test9 actually measured landing. Nothing generated, nothing this
        // project wrote to be caught.
        val model = ProseModel.shipped()
        val known = golden.cases.filter { it.kind == "known-bad" }
        assertEquals(3, known.size)
        known.forEach { case ->
            model.thresholds.forEach { (convention, threshold) ->
                assertTrue(
                    case.score > threshold,
                    "${case.register} scored ${case.score}, under $convention's $threshold",
                )
            }
        }
    }

    @Test
    fun `real documentation sits below the threshold`() {
        val model = ProseModel.shipped()
        val real = golden.cases.filter { it.kind == "real" }
        assertTrue(real.size > 50)
        val over = real.count { it.score > model.thresholds[it.convention]!! }
        // A handful is the operating point working, not a failure — it is calibrated to flag
        // about two comments in a thousand. Many would mean the port is wrong.
        assertTrue(over <= 2, "$over of ${real.size} real sentences flagged")
    }

    @Test
    fun `text too short to hold an n-gram scores without failing`() {
        val model = ProseModel.shipped()
        golden.cases.filter { it.kind == "edge" }.forEach {
            val got = model.score(it.text)
            assertTrue(abs(got - it.score) < 1e-6, "'${it.text}' scored $got, expected ${it.score}")
        }
    }
}
