package org.dependencyskills.test0

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/** Generates the bake-off report to build/reports/test0/bakeoff.md (and stdout). */
class GenerateReportTest {

    @Test
    fun `generate the bake-off report`() {
        val fixtures = Path.of(
            System.getProperty("test0.dir") ?: error("test0.dir system property not set"),
        )
        val md = Report.generate(fixtures)

        val out = Path.of("build/reports/test0/bakeoff.md")
        Files.createDirectories(out.parent)
        Files.writeString(out, md)
        println("\n$md")

        assertTrue(md.contains("## Summary"))
    }
}
