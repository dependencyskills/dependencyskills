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
    fun `names a dependency added since the last build, even under -q, and only then`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        val first = project.run("classes", "-q").output
        // The first build has nothing to compare with, and every dependency being new says nothing.
        assertFalse("new since the last build" in first, first)

        project.build("""dependencies { api("com.example:alpha:1.0"); api("com.example:gamma:1.0") }""")
        val second = project.run("classes", "-q").output
        assertContains(second, "dependencyskills: new since the last build: com.example:gamma.")
        assertFalse("com.example:alpha," in second, second)

        val third = project.run("classes", "-q", "--rerun-tasks").output
        assertFalse("new since the last build" in third, third)
    }

    /** A root with two modules, `:a` using alpha and `:b` using gamma, each applying the plugin. */
    private fun twoModules(include: String = "\":a\", \":b\"") = project().apply {
        build("")
        file("settings.gradle.kts", path("settings.gradle.kts").toFile().readText() + "\ninclude($include)\n")
        for ((module, library) in listOf("a" to "alpha", "b" to "gamma")) {
            file("$module/build.gradle.kts", """
                plugins { `java-library`; id("org.dependencyskills.plugin") }
                dependencies { api("com.example:$library:1.0") }
            """.trimIndent())
            file("$module/src/main/java/com/example/$module/Lib.java", "package com.example.$module; public class Lib {}")
        }
    }

    @Test
    fun `building one module keeps what the others resolved, and says only what that module added`() {
        val project = twoModules()
        project.run("classes")
        val both = project.path(sbom).toFile().readText()
        assertContains(both, "pkg:maven/com.example/alpha@1.0")
        assertContains(both, "pkg:maven/com.example/gamma@1.0")
        assertContains(both, """{"name":"dependencyskills:project","value":":b"}""")

        // Only :a is compiled. :b's dependency stays in scope - it used to vanish here.
        project.file("a/build.gradle.kts", """
            plugins { `java-library`; id("org.dependencyskills.plugin") }
            dependencies { api("com.example:alpha:1.0"); api("com.example:beta:1.0") }
        """.trimIndent())
        val output = project.run(":a:classes", "-q").output
        val merged = project.path(sbom).toFile().readText()
        assertContains(merged, "pkg:maven/com.example/gamma@1.0")
        assertContains(merged, "pkg:maven/com.example/beta@1.0")
        assertContains(output, "new since the last build: com.example:beta.")
    }

    @Test
    fun `a module removed from the build takes its dependencies with it`() {
        val project = twoModules()
        project.run("classes")
        project.file("settings.gradle.kts",
            project.path("settings.gradle.kts").toFile().readText().replace("include(\":a\", \":b\")", "include(\":a\")"))
        project.run(":a:classes")

        val text = project.path(sbom).toFile().readText()
        assertContains(text, "pkg:maven/com.example/alpha@1.0")
        assertFalse("gamma" in text, text)
    }

    @Test
    fun `lists a library the version catalog declares before any module uses it, marked as declared`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.file("settings.gradle.kts", project.path("settings.gradle.kts").toFile().readText() + """

            dependencyResolutionManagement {
                versionCatalogs { create("libs") { library("gamma", "com.example:gamma:1.0"); library("alpha", "com.example:alpha:1.0") } }
            }
        """.trimIndent())
        project.run("classes")

        val lines = project.path(sbom).toFile().readLines()
        val gamma = lines.single { "pkg:maven/com.example/gamma@1.0" in it }
        assertContains(gamma, """{"name":"dependencyskills:declared","value":"true"}""")
        // Resolved and declared: a dependency like any other, attributed to its project.
        val alpha = lines.single { "pkg:maven/com.example/alpha@1.0" in it }
        assertContains(alpha, """{"name":"dependencyskills:project","value":":"}""")
        assertFalse("declared" in alpha, alpha)
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
