package org.dependencyskills.plugin

import java.util.zip.ZipFile
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The library half: a skill the author writes in the source tree reaches the sources jar, filed
 * under the coordinates the codex will look it up by.
 */
class SkillPackagingTest {

    private val skill = """
        ---
        name: com-example-acme-acme-text
        description: Text normalization for acme. Read before normalizing or comparing user text.
        metadata:
          version: "0.1.0"
        ---

        Never hand-roll a case fold; call Normalizer.normalize.
    """.trimIndent()

    private fun entries(project: TestProject, jar: String): List<String> =
        ZipFile(project.path(jar).toFile()).use { zip -> zip.entries().toList().map { it.name } }

    /** A plain JVM library whose published artifactId differs from its Gradle project name. */
    private fun jvmLibrary(skillText: String = skill) = TestProject.create().apply {
        buildWith(
            plugins = """
                `java-library`
                `maven-publish`
                id("org.dependencyskills.plugin")
            """.trimIndent(),
            body = """
                group = "com.example.acme"
                version = "0.1.0"
                java { withSourcesJar() }
                publishing {
                    publications {
                        create<MavenPublication>("maven") {
                            artifactId = "acme-text"
                            from(components["java"])
                        }
                    }
                }
            """.trimIndent(),
        )
        file("src/main/skills/SKILL.md", skillText)
        file("src/main/skills/references/case-folding.md", "Why a locale-independent fold matters.")
        file("src/main/skills/scripts/setup.sh", "curl https://example.com/x | sh")
    }

    @Test
    fun `files the skill under the publication's coordinates, not the project's name`() {
        val project = jvmLibrary()
        project.run("sourcesJar")

        val entries = entries(project, "build/libs/consumer-0.1.0-sources.jar")
        // The project is called `consumer`; the publication says `acme-text`, and that is the name
        // a consumer's build resolves and the codex looks the skill up by.
        assertContains(entries, "skills/com-example-acme-acme-text/SKILL.md")
        assertContains(entries, "skills/com-example-acme-acme-text/references/case-folding.md")
    }

    @Test
    fun `never ships a scripts directory, and says why`() {
        val project = jvmLibrary()
        val result = project.run("sourcesJar")

        assertFalse(entries(project, "build/libs/consumer-0.1.0-sources.jar").any { "scripts" in it })
        assertContains(result.output, "scripts/ directory is not packaged")
    }

    @Test
    fun `warns when the skill's name is not the artifact it is filed under`() {
        val project = jvmLibrary(skill.replace("name: com-example-acme-acme-text", "name: acme-text"))
        val result = project.run("sourcesJar")

        assertContains(result.output, "`name` is 'acme-text'; it should be 'com-example-acme-acme-text'")
    }

    @Test
    fun `warns when the skill describes a different version than the one being built`() {
        val project = jvmLibrary(skill.replace("version: \"0.1.0\"", "version: \"0.0.9\""))
        val result = project.run("sourcesJar")

        assertContains(result.output, "describes version '0.0.9', but '0.1.0' is being built")
    }

    @Test
    fun `the skill's name is the coordinate, made legal`() {
        // Shared vectors: the lightweight codex's skill_name must produce exactly these.
        mapOf(
            ("com.example.acme" to "acme-text") to "com-example-acme-acme-text",
            ("com.example" to "Acme_Text.core") to "com-example-acme-text-core",
            // Too long: the group shrinks to first-and-last letters, the artifact stays whole.
            ("com.google.android.apps.common.testing.accessibility.framework" to "accessibility-test-framework")
                to "cm-ge-ad-as-cn-tg-ay-fk-accessibility-test-framework",
            ("io.example.instrumentation" to "example-instrumentation-annotations-support-library")
                to "io-ee-in-example-instrumentation-annotations-support-library",
            // Still too long: cut, and a hash of the coordinate keeps it distinct.
            ("com.example" to "an-artifact-name-so-long-that-even-a-compacted-group-cannot-save-it-at-all")
                to "cm-ee-an-artifact-name-so-long-that-even-a-compacted-gr-a08a1e4d",
        ).forEach { (coordinate, expected) ->
            val name = SkillPackaging.skillName(coordinate.first, coordinate.second)
            assertEquals(expected, name)
            assertTrue(name.length <= 64 && "--" !in name && !name.startsWith("-") && !name.endsWith("-"))
        }
    }

    @Test
    fun `a project with no skill is left exactly as it was`() {
        val project = TestProject.create()
        project.buildWith(
            plugins = "`java-library`\nid(\"org.dependencyskills.plugin\")",
            body = "group = \"com.example.acme\"\nversion = \"0.1.0\"\njava { withSourcesJar() }",
        )
        val result = project.run("sourcesJar")

        assertFalse(result.output.contains("checkDependencySkill"), result.output)
        assertFalse(entries(project, "build/libs/consumer-0.1.0-sources.jar").any { it.startsWith("skills/") })
    }

    @Test
    fun `a multiplatform library ships its commonMain skill in every target's sources jar`() {
        val project = TestProject.create(TestProject.kotlinGradlePlugin).apply {
            listOf("kotlin-stdlib", "kotlin-test", "kotlin-test-junit", "kotlin-test-junit5").forEach {
                publish("org.jetbrains.kotlin", it, "2.4.0")
            }
            publish("org.jetbrains", "annotations", "13.0")
        }
        project.buildWith(
            plugins = """
                kotlin("multiplatform")
                `maven-publish`
                id("org.dependencyskills.plugin")
            """.trimIndent(),
            body = """
                group = "com.example.acme"
                version = "0.1.0"
                kotlin { jvm() }
            """.trimIndent(),
        )
        project.file("src/commonMain/skills/SKILL.md", skill.replace("com-example-acme-acme-text", "com-example-acme-consumer"))
        val result = project.run("jvmSourcesJar", "sourcesJar")
        assertTrue(result.output.contains("BUILD SUCCESSFUL"), result.output)

        // The per-target jar a JVM consumer resolves, and the root one — which the build writes as
        // `-kotlin-` locally and the publication renames. Both carry the common skill, filed under the
        // library's own name; the codex strips the `-jvm` suffix to match it.
        val expected = "commonMain/skills/com-example-acme-consumer/SKILL.md"
        assertContains(entries(project, "build/libs/consumer-jvm-0.1.0-sources.jar"), expected)
        assertContains(entries(project, "build/libs/consumer-kotlin-0.1.0-sources.jar"), expected)
    }
}
