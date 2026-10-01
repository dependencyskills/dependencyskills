package org.dependencyskills.plugin

import java.nio.file.Files
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
        // Everything the code can import, by default: alpha exposes beta on the compile classpath. alpha's
        // runtime-only gamma is not importable, so not in scope.
        assertContains(text, "\"purl\":\"pkg:maven/com.example/beta@1.0\"")
        assertFalse("gamma" in text, text)
    }

    @Test
    fun `with transitive switched off, lists only what the project declares`() {
        val project = project()
        project.build("""
            dependencies { api("com.example:alpha:1.0") }
            dependencySkills { harvester { transitive = false } }
        """.trimIndent())
        project.run("classes")

        val text = project.path(sbom).toFile().readText()
        assertContains(text, "\"purl\":\"pkg:maven/com.example/alpha@1.0\"")
        assertFalse("beta" in text, text)
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
                plugins { `java-library`; id("org.dependencyskills") }
                // Declared only: these tests follow one dependency per module, and alpha exposes beta.
                dependencySkills { harvester { transitive = false } }
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
            plugins { `java-library`; id("org.dependencyskills") }
            dependencySkills { harvester { transitive = false } }
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

    @Test
    fun `without a service URL the full codex is not told, and the build says nothing about it`() {
        val project = TestProject.create().apply {
            publish("com.example", "alpha", "1.0")
            fullCodex = false
            build("""dependencies { api("com.example:alpha:1.0") }""")
        }
        val output = project.run("classes").output

        assertTrue(Files.isRegularFile(project.path("build/dependencyskills/bom.cdx.json")))
        assertEquals(emptyList(), project.registrations())
        assertEquals(emptyList(), project.warmings())
        assertFalse("codex service" in output, output)
        assertFalse("coordinates recorded" in output, output)
    }

    @Test
    fun `a library an included build supplies is reported by its coordinates, with its project directory`() {
        val project = TestProject.create()
        project.build("""dependencies { api("com.example:inc:1.0") }""")
        // A sibling build that publishes com.example:inc, standing in for the module through includeBuild.
        project.file("../inc/settings.gradle.kts", "rootProject.name = \"inc\"")
        project.file("../inc/build.gradle.kts", """
            plugins { `java-library` }
            group = "com.example"
            version = "2.0"
        """.trimIndent())
        project.file("../inc/src/main/java/com/example/inc/Inc.java", "package com.example.inc; public class Inc {}")
        project.file("settings.gradle.kts", project.path("settings.gradle.kts").toFile().readText() + "\nincludeBuild(\"../inc\")\n")
        project.run("classes")

        val sbom = project.path("build/dependencyskills/bom.cdx.json").toFile().readText()
        // Parsed, not only searched: a reader that cannot parse the file keeps the last good scope, silently.
        groovy.json.JsonSlurper().parseText(sbom)
        assertContains(sbom, "\"purl\":\"pkg:maven/com.example/inc@2.0\"")
        val included = project.path("../inc").toFile().canonicalFile
        assertTrue(sbom.contains("\"name\":\"dependencyskills:source\",\"value\":\"") &&
            sbom.substringAfter("dependencyskills:source\",\"value\":\"").substringBefore("\"").let { java.io.File(it).canonicalFile == included }, sbom)
    }
}

