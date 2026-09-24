package org.dependencyskills.plugin

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertFalse

/**
 * The consumer's build fetches the sources jars its dependencies' skills travel in, because nothing
 * else on a command-line-only machine ever does (RAD-0079).
 */
class SourcesTest {

    private fun project() = TestProject.create().apply {
        publish("com.example", "alpha", "1.0", sources = true)
        publish("com.example", "gamma", "1.0", sources = true)
        publish("com.example", "plain", "1.0")
    }

    private fun declareGamma(project: TestProject) = project.file("settings.gradle.kts",
        project.path("settings.gradle.kts").toFile().readText() + """

            dependencyResolutionManagement {
                versionCatalogs { create("libs") { library("gamma", "com.example:gamma:1.0") } }
            }
        """.trimIndent())

    @Test
    fun `fetches the sources of what the compile classpath resolved and of what the catalog declares`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        declareGamma(project)
        val output = project.run("classes", "--info").output

        assertContains(output, "dependencyskills: 1 sources jars on the compile classpaths, 1 for declared libraries")
    }

    @Test
    fun `a dependency without a sources jar is skipped, not a failed build`() {
        val project = project()
        project.build("""dependencies { api("com.example:plain:1.0") }""")
        val output = project.run("classes", "--info").output

        assertContains(output, "dependencyskills: 0 sources jars on the compile classpaths, 0 for declared libraries")
    }

    @Test
    fun `switched off by the fetchSources property`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        val output = project.run("classes", "--info", "-PdependencySkills.fetchSources=false").output

        assertFalse("sources jars on the compile classpaths" in output, output)
    }

    @Test
    fun `works with the configuration cache, stored and reused`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        declareGamma(project)
        project.run("classes", "--configuration-cache")
        val reused = project.run("classes", "--configuration-cache", "--info").output

        assertContains(reused, "Reusing configuration cache")
        assertContains(reused, "dependencyskills: 1 sources jars on the compile classpaths, 1 for declared libraries")
    }
}
