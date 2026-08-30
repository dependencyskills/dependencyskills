package org.dependencyskills.plugin

import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DependencySkillsPluginTest {

    /**
     * A graph with one of every edge that matters.
     *
     * `alpha` exposes `beta` on a consumer's compile classpath and keeps `gamma` to itself.
     * That asymmetry is what proves the plugin is asking Gradle rather than interpreting scope.
     */
    private fun project() = TestProject.create().apply {
        publish("com.example", "beta", "1.0")
        publish("com.example", "gamma", "1.0")
        publish("com.example", "alpha", "1.0", compile = listOf("com.example:beta:1.0"), runtime = listOf("com.example:gamma:1.0"))
        publish("com.example", "only-compiled-against", "1.0")
        publish("com.example", "test-only", "1.0")
    }

    // -- collecting -------------------------------------------------------------------------

    @Test
    fun `collects what the project declared, from the compile classpath`() {
        val project = project()
        project.build("""
            dependencies {
                api("com.example:alpha:1.0")
                compileOnly("com.example:only-compiled-against:1.0")
            }
        """.trimIndent())
        project.run("classes")

        run {
            val recorded = project.recordedCoordinates()
            assertContains(recorded, "com.example:alpha:1.0")
            assertContains(recorded, "com.example:only-compiled-against:1.0")
        }
    }

    @Test
    fun `other people's implementation details never appear`() {
        // `gamma` is alpha's runtime-only dependency. It is on the runtime classpath and not on
        // the compile classpath, and nothing here has to know that: asking for the compile
        // classpath is what excludes it. A hand-rolled walk of the configuration hierarchy is
        // exactly what would get this wrong.
        val project = project()
        project.build("""
            dependencies { api("com.example:alpha:1.0") }
            dependencySkills { harvester { transitive = true } }
        """.trimIndent())
        project.run("classes")

        run {
            val recorded = project.recordedCoordinates()
            assertContains(recorded, "com.example:beta:1.0")
            assertFalse("com.example:gamma:1.0" in recorded, "a runtime-only transitive is not importable")
        }
    }

    @Test
    fun `the transitive tail is an explicit opt-in`() {
        val declaredOnly = project()
        declaredOnly.build("""dependencies { api("com.example:alpha:1.0") }""")
        declaredOnly.run("classes")
        run {
            assertEquals(listOf("com.example:alpha:1.0"), declaredOnly.recordedCoordinates())
        }

        val widened = project()
        widened.build("""
            dependencies { api("com.example:alpha:1.0") }
            dependencySkills { harvester { transitive = true } }
        """.trimIndent())
        widened.run("classes")
        run {
            assertEquals(listOf("com.example:alpha:1.0", "com.example:beta:1.0"), widened.recordedCoordinates())
        }
    }

    @Test
    fun `the recorded set is the union across source sets`() {
        val project = project()
        project.build("""
            dependencies {
                api("com.example:alpha:1.0")
                testImplementation("com.example:test-only:1.0")
            }
        """.trimIndent())
        // Only the main classes are built. The test source set's compile classpath resolves
        // anyway, and what the store holds must not depend on which target was assembled.
        project.run("classes", "testClasses")

        run {
            val recorded = project.recordedCoordinates()
            assertContains(recorded, "com.example:alpha:1.0")
            assertContains(recorded, "com.example:test-only:1.0")
        }
    }

    // -- what is stored ---------------------------------------------------------------------

    @Test
    fun `the build writes down what this project may search`() {
        // The store is machine-wide and records no project-to-coordinate edge, deliberately. So
        // the only thing that knows a project's scope is the build that resolved it, and the MCP
        // server that enforces the boundary has to be told. This is the telling.
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.run("classes")

        val lines = Files.readAllLines(project.scopeFile)
        assertTrue(lines.any { it == "maven:com.example:alpha:1.0" }, "scope was: $lines")
        assertTrue(lines.first().startsWith("#"), "the file should say what wrote it")
    }

    @Test
    fun `a removed dependency leaves the scope`() {
        // Written whole rather than appended. A scope that only grew would keep answering
        // questions about a library the project no longer has, which is the containment boundary
        // widening quietly rather than a stale cache.
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0")
            api("com.example:beta:1.0") }""")
        project.run("classes")
        assertTrue(Files.readAllLines(project.scopeFile).any { it.endsWith("beta:1.0") })

        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.run("classes", "--rerun-tasks")

        val lines = Files.readAllLines(project.scopeFile)
        assertTrue(lines.none { it.endsWith("beta:1.0") }, "a dropped dependency stayed in scope: $lines")
        assertTrue(lines.any { it.endsWith("alpha:1.0") })
    }

    @Test
    fun `an ignored library is not recorded, whatever version it is at`() {
        val project = project()
        project.build("""
            dependencies { api("com.example:alpha:1.0") }
            dependencySkills { harvester { transitive = true; ignore("com.example:beta") } }
        """.trimIndent())
        project.run("classes")

        run {
            assertEquals(listOf("com.example:alpha:1.0"), project.recordedCoordinates())
        }
    }

    // -- reporting ----------------------------------------------------------------------------

    @Test
    fun `nothing resolved reads differently from nothing new`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")

        val nothingResolved = project.run("help")
        assertContains(nothingResolved.output, "no compile classpath resolved")
        // Nothing written, rather than an empty scope written. An empty scope means "search
        // nothing", so a build that learned nothing must not overwrite what the last one knew.
        assertTrue(project.recordedCoordinates().isEmpty())

        val resolved = project.run("classes")
        assertContains(resolved.output, "1 compile classpath, 1 coordinate recorded")
    }

    @Test
    fun `it does not force resolution`() {
        // `help` resolves nothing. If the plugin were resolving configurations itself - at
        // configuration time or otherwise - this would record the graph anyway.
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.run("help")
        assertTrue(project.recordedCoordinates().isEmpty())
    }

    // -- staying out of the way ----------------------------------------------------------------

    @Test
    fun `switched off, it records nothing`() {
        val project = project()
        project.build("""
            dependencies { api("com.example:alpha:1.0") }
            dependencySkills { enabled = false }
        """.trimIndent())
        project.run("classes")
        assertTrue(project.recordedCoordinates().isEmpty())
    }

    @Test
    fun `it can be switched off from the command line`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.run("classes", "-PdependencySkills.enabled=false")
        assertTrue(project.recordedCoordinates().isEmpty())
    }

    @Test
    fun `it works with the configuration cache`() {
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")

        val miss = project.run("classes", "--configuration-cache", "--warning-mode=all")
        assertTrue(miss.output.contains("BUILD SUCCESSFUL"))
        assertFalse(miss.output.contains("problems were found storing the configuration cache"))
        // Resolving at configuration time is the classic way a plugin like this slows every
        // build down and breaks the cache. Gradle says so when it happens; it must not happen.
        assertFalse(miss.output.contains("was resolved during configuration time"))
        assertContains(miss.output, "1 compile classpath, 1 coordinate recorded")
        assertEquals(listOf("com.example:alpha:1.0"), project.recordedCoordinates())
    }

    @Test
    fun `on a configuration cache hit it observes nothing, because nothing resolves`() {
        // Worth asserting rather than assuming, because it is the plugin's one real boundary.
        // A cache hit skips the configuration phase entirely: the resolution results come out
        // of the cache instead of being computed, so `afterResolve` does not fire and the
        // plugin neither records nor reports.
        //
        // That is sound as far as it goes. A hit means nothing about configuration changed, and
        // dependency declarations are configuration - so the resolved set is the one already in
        // the store. The gap is a version that resolves differently without any build file
        // changing: a dynamic version, or a changing module.
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.run("classes", "--configuration-cache")

        val hit = project.run("classes", "--configuration-cache")
        assertContains(hit.output, "Reusing configuration cache")
        assertFalse(hit.output.contains("dependency-skills:"), "a cache hit is silent")
    }

    @Test
    fun `a changed dependency invalidates the cache and is picked up`() {
        // The case that matters: declaring a new dependency is a configuration change, so the
        // entry is discarded, the configuration phase runs, and the plugin sees the new
        // coordinate. This is why the boundary above is a boundary rather than a hole.
        val project = project()
        project.build("""dependencies { api("com.example:alpha:1.0") }""")
        project.run("classes", "--configuration-cache")

        project.build("""
            dependencies {
                api("com.example:alpha:1.0")
                implementation("com.example:only-compiled-against:1.0")
            }
        """.trimIndent())
        val after = project.run("classes", "--configuration-cache")

        assertContains(after.output, "2 coordinates recorded")
        assertEquals(
            listOf("com.example:alpha:1.0", "com.example:only-compiled-against:1.0"),
            project.recordedCoordinates(),
        )
    }
}
