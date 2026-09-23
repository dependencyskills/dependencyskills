package org.dependencyskills.plugin

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The file handoff to the lightweight codex: the resolved compile classpath, as a CycloneDX SBOM in
 * the root build directory.
 */
class ReportFileTest {

    private fun project() = TestProject.create().apply {
        publish("com.example", "beta", "1.0")
        publish("com.example", "gamma", "1.0")
        publish("com.example", "alpha", "1.0", compile = listOf("com.example:beta:1.0"), runtime = listOf("com.example:gamma:1.0"))
    }

    private val sbom = "build/dependencyskills/bom.cdx.json"

    @Test
    fun `writes the compile classpath as a CycloneDX SBOM, with no service running`() {
        val project = project()
        project.stopService()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.run("classes")

        val text = project.path(sbom).toFile().readText()
        assertContains(text, "\"bomFormat\": \"CycloneDX\"")
        assertContains(text, "\"purl\":\"pkg:maven/com.example/alpha@1.0\"")
        // Declared dependencies only, by default - the transitive tail is opt-in (RAD-0022), and the
        // SBOM is the same set the HTTP report carries, not a wider one.
        assertFalse("beta" in text, text)
        assertFalse("gamma" in text, text)
    }

    @Test
    fun `with the transitive tail switched on, lists what is importable and nothing more`() {
        val project = project()
        project.build("""
            dependencies { api("com.example:alpha:1.0") }
            dependencySkills { harvester { transitive = true } }
        """.trimIndent())
        project.run("classes")

        val text = project.path(sbom).toFile().readText()
        // Exposed on the compile classpath by alpha, so importable, so in scope.
        assertContains(text, "\"purl\":\"pkg:maven/com.example/beta@1.0\"")
        // alpha's runtime-only dependency is not importable, so not in scope. An SBOM plugin
        // describing the runtime classpath would list it; this one describes the compile classpath.
        assertFalse("gamma" in text, text)
    }

    @Test
    fun `leaves the file alone when nothing changed`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.run("classes")
        val file = project.path(sbom).toFile()
        val first = file.lastModified()
        Thread.sleep(1_100)   // past the filesystem's timestamp resolution
        project.run("classes", "--rerun-tasks")

        assertEquals(first, file.lastModified(), "an unchanged SBOM was rewritten")
    }

    @Test
    fun `a project that resolves nothing gets an SBOM with no components, not a missing file`() {
        val project = project()
        project.build("")
        project.run("classes")

        val text = project.path(sbom).toFile().readText()
        assertTrue("\"components\": [\n  ]" in text, text)
    }
}
