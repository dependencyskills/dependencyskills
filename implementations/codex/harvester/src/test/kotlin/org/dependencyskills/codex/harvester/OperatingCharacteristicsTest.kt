package org.dependencyskills.codex.harvester

import org.dependencyskills.codex.classifier.Decision
import org.dependencyskills.codex.classifier.ProseClassifier
import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.Coordinate
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * What the shipped classifier costs, measured on real documentation through the code that ships.
 *
 * The story asks for operating characteristics **measured against the classifier as shipped
 * rather than carried over from the experiment**, and that distinction is the reason this exists.
 * The experiment's numbers were produced in Python by a model fitted on a different sample; this
 * runs the committed weights, through the JVM implementation, over documentation harvested by
 * this project's own harvester.
 *
 * It lives here for the same reason the lexical baseline does: it needs a real corpus and the
 * classifier at once, and the harvester is the module that can see both.
 */
class OperatingCharacteristicsTest {

    @Test
    fun `what the shipped default costs on real harvested documentation`() {
        val jars = (System.getProperty("codex.corpus") ?: error("no corpus on the test JVM"))
            .split(File.pathSeparator).filter { it.isNotBlank() }.map { Path.of(it) }
        assertTrue(jars.size > 40)

        val store = createTempDirectory("characteristics").resolve("codex.db")
        val scope = LinkedHashSet<Coordinate>()
        Codex.open(store).use { codex ->
            val harvester = SourcesJarHarvester()
            jars.forEach { jar ->
                val coordinate = Coordinate("maven", jar.fileName.toString().removeSuffix("-sources.jar"))
                scope.add(coordinate)
                codex.harvest(coordinate, jar, harvester)
            }
        }

        val classifier = ProseClassifier()
        val rows = ArrayList<String>()
        var entries = 0
        var flagged = 0
        var sentences = 0
        val byRegister = HashMap<String, Int>()
        val examples = ArrayList<String>()
        val uncalibrated = HashMap<String, Int>()
        var micros = 0L

        // One entry, scored once. A multiplatform library publishes `-jvm` and non-`-jvm`
        // coordinates that content-addressing collapses onto the same entries, so walking the
        // scope would count several of them twice and quietly change the rate.
        val scored = HashSet<String>()
        Codex.open(store).use { codex ->
            scope.forEach { coordinate ->
                codex.entriesOf(coordinate).forEach { entry ->
                    if (!scored.add(entry.id)) return@forEach
                    if (entry.docFormat !in classifier.calibratedFor()) {
                        uncalibrated.compute(entry.docFormat) { _, n -> (n ?: 0) + 1 }
                        return@forEach
                    }
                    val doc = codex.rawDocumentation(entry.id) ?: return@forEach
                    entries++
                    val started = System.nanoTime()
                    val verdict = classifier.classify(doc, entry.docFormat)
                    micros += (System.nanoTime() - started) / 1_000
                    sentences += verdict.sentences.size
                    if (verdict.decision == Decision.Suspect) {
                        flagged++
                        verdict.register?.let { byRegister.compute(it) { _, n -> (n ?: 0) + 1 } }
                        if (examples.size < 12) examples.add(verdict.explain())
                    }
                }
            }
        }

        rows.add("| distinct entries scored | $entries |")
        rows.add("| sentences scored | $sentences |")
        rows.add("| **flagged** | **$flagged (${"%.3f".format(100.0 * flagged / entries)}%)** |")
        rows.add("| conventions with no operating point | ${uncalibrated.ifEmpty { "none" }} |")
        rows.add("| mean time to score one comment | ${"%.2f".format(micros.toDouble() / entries / 1000)} ms |")

        val report = buildString {
            appendLine("# Shipped classifier, operating characteristics")
            appendLine()
            appendLine("The committed weights, through the JVM classifier, over documentation harvested")
            appendLine("by this project from 59 pinned coordinates. Not the experiment's numbers.")
            appendLine()
            appendLine("| | |")
            appendLine("|---|---|")
            rows.forEach { appendLine(it) }
            appendLine()
            val attributed = byRegister.filterKeys { it !in setOf("javadoc", "kdoc", "jsdoc") }
            appendLine("Registers attributed among the flagged: ${attributed.ifEmpty { "none" }}.")
            appendLine("The attribution model declining to label everything the binary model flagged is")
            appendLine("what it should do when the flags are false positives rather than instructions.")
            appendLine()
            appendLine("## What it flagged")
            appendLine()
            examples.forEach { appendLine("- $it") }
        }
        val directory = Path.of(System.getProperty("codex.reports") ?: ".")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("classifier-characteristics.md"), report)
        println(report)

        // The measurement has to have happened. What it found is a finding, not a pass mark.
        assertTrue(entries > 1_000, "expected a real corpus; scored $entries")
        assertEquals(emptyMap(), uncalibrated, "every convention harvested here should be calibrated")
    }
}
