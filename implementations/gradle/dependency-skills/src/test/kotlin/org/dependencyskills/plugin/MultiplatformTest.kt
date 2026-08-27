package org.dependencyskills.plugin

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

/**
 * The multiplatform half, against a real KMP build.
 *
 * KGP is injected onto the TestKit plugin classpath by this module's build rather than resolved
 * from a repository, and the Kotlin runtime a compilation asks for is published as a stub into
 * the same local repository as the fixtures. So this runs offline like every other test here.
 */
class MultiplatformTest {

    private fun project() = TestProject.create(TestProject.kotlinGradlePlugin).apply {
        publish("com.example", "shared-dependency", "1.0")
        publish("com.example", "jvm-only-dependency", "1.0")
        // Stubs. No compilation is executed - only its compile-dependency configuration is
        // resolved - so what these hold does not matter, only that they resolve with no network.
        listOf("kotlin-stdlib", "kotlin-test", "kotlin-test-junit", "kotlin-test-junit5").forEach {
            publish("org.jetbrains.kotlin", it, "2.4.0")
        }
        publish("org.jetbrains", "annotations", "13.0")
    }

    @Test
    fun `collects every compilation's compile-dependency configuration`() {
        val project = project()
        project.buildWith(
            plugins = """
                kotlin("multiplatform")
                id("org.dependencyskills.plugin")
            """.trimIndent(),
            body = """
                kotlin {
                    jvm()
                    sourceSets {
                        commonMain.dependencies { api("com.example:shared-dependency:1.0") }
                        jvmMain.dependencies { api("com.example:jvm-only-dependency:1.0") }
                    }
                }
            """.trimIndent(),
        )

        // `dependencies --configuration` resolves the compilation's compile-dependency
        // configuration and nothing else. Compiling would be the more faithful trigger, but it
        // would need a real Kotlin runtime rather than the stubs above, which would put this
        // test at the mercy of what happens to be in the machine's cache.
        val result = project.run("dependencies", "--configuration", "jvmCompileClasspath")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)
        assertContains(result.output, "1 compile classpath")

        project.store().use {
            val recorded = it.recordedCoordinates()
            assertContains(recorded, "com.example:shared-dependency:1.0")
            assertContains(recorded, "com.example:jvm-only-dependency:1.0")
        }
    }
}
