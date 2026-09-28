package org.dependencyskills.plugin

import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * The plugin writes the agent skills a project declares a block for, and never one it does not, and
 * treats a skill someone edited as theirs unless told otherwise.
 */
class AgentSkillsTest {

    private val librarian = bundled("librarian/SKILL.md")

    private fun bundled(path: String): String =
        File("../../agent-skills/$path").readText()

    private val keepEdits = "refresh = org.dependencyskills.plugin.SkillRefresh.UnlessEdited"

    private fun project(blocks: String) = TestProject.create().apply {
        build("dependencySkills {\n$blocks\n}")
    }

    /** Records [files] in the manifest as what the plugin last wrote at [path], as an earlier version would have. */
    private fun TestProject.recordedAs(path: String, files: Map<String, String>) = file(AgentSkills.MANIFEST, """
        {"changes": [{"kind": "skill", "path": "$path", "files": {${
            files.entries.joinToString(", ") { "\"${it.key}\": \"${AgentSkills.digest(it.value.toByteArray())}\"" }
        }}}]}
    """.trimIndent())

    private fun TestProject.text(path: String) = path(path).toFile().readText()

    @Test
    fun `nothing is written without a block`() {
        val project = project("")
        project.run("classes")

        assertFalse(Files.exists(project.path(".agents/skills")))
        assertFalse(Files.exists(project.path(AgentSkills.MANIFEST)))
    }

    @Test
    fun `the consumer block writes the librarian skill, and only it`() {
        val project = project("consumer { }")
        val output = project.run("classes").output

        assertEquals(librarian, project.text(".agents/skills/librarian/SKILL.md"))
        assertFalse(Files.exists(project.path(".agents/skills/to-library-skill")))
        assertFalse(Files.exists(project.path(".claude")), "no .claude/ in the project, so no copy for Claude Code")
        assertContains(output, "dependencyskills: wrote the librarian skill to .agents/skills/librarian/")
        assertContains(project.text(AgentSkills.MANIFEST), "\"path\": \".agents/skills/librarian\"")
    }

    @Test
    fun `the author block writes to-library-skill with its references and assets`() {
        val project = project("author { }")
        project.run("classes")

        assertEquals(bundled("to-library-skill/SKILL.md"), project.text(".agents/skills/to-library-skill/SKILL.md"))
        assertTrue(Files.isRegularFile(project.path(".agents/skills/to-library-skill/references/per-language.md")))
        assertTrue(Files.isRegularFile(project.path(".agents/skills/to-library-skill/assets/SKILL.template.md")))
        assertFalse(Files.exists(project.path(".agents/skills/librarian")))
    }

    @Test
    fun `a project with a claude directory gets a copy for Claude Code, not a link`() {
        val project = project("consumer { }")
        Files.createDirectories(project.path(".claude"))
        project.run("classes")

        val copy = project.path(".claude/skills/librarian")
        assertFalse(Files.isSymbolicLink(copy))
        assertEquals(librarian, project.text(".claude/skills/librarian/SKILL.md"))
    }

    @Test
    fun `a second build changes nothing and says nothing`() {
        val project = project("consumer { }")
        project.run("classes")
        val manifest = project.text(AgentSkills.MANIFEST)
        val output = project.run("classes").output

        assertFalse("dependencyskills: wrote" in output, output)
        assertEquals(manifest, project.text(AgentSkills.MANIFEST))
    }

    @Test
    fun `an unedited copy from an earlier version is updated`() {
        val project = project("consumer { }")
        project.file(".agents/skills/librarian/SKILL.md", "an earlier librarian")
        project.recordedAs(".agents/skills/librarian", mapOf("SKILL.md" to "an earlier librarian"))
        val output = project.run("classes").output

        assertEquals(librarian, project.text(".agents/skills/librarian/SKILL.md"))
        assertContains(output, "dependencyskills: updated the librarian skill in .agents/skills/librarian/")
    }

    @Test
    fun `under UnlessEdited an edited copy is kept, with a warning that it was not updated`() {
        val project = project("consumer { $keepEdits }")
        project.file(".agents/skills/librarian/SKILL.md", "an earlier librarian, edited")
        project.recordedAs(".agents/skills/librarian", mapOf("SKILL.md" to "an earlier librarian"))
        val output = project.run("classes").output

        assertEquals("an earlier librarian, edited", project.text(".agents/skills/librarian/SKILL.md"))
        assertContains(output, "dependencyskills: .agents/skills/librarian/ has local edits, so it was NOT updated")
        assertContains(output, "remove the refresh setting from dependencySkills { consumer { } }")
    }

    @Test
    fun `a same-named skill nothing recorded is treated as edited`() {
        val project = project("author { $keepEdits }")
        project.file(".agents/skills/to-library-skill/SKILL.md", "somebody else's skill")
        val output = project.run("classes").output

        assertEquals("somebody else's skill", project.text(".agents/skills/to-library-skill/SKILL.md"))
        assertContains(output, "remove the refresh setting from dependencySkills { author { } }")
    }

    @Test
    fun `under UnlessEdited an edit to the version already carried is kept without a warning`() {
        val project = project("consumer { $keepEdits }")
        project.run("classes")
        project.file(".agents/skills/librarian/SKILL.md", librarian + "\nA line of our own.\n")
        val output = project.run("classes").output

        assertContains(project.text(".agents/skills/librarian/SKILL.md"), "A line of our own.")
        assertFalse("has local edits" in output, output)
    }

    @Test
    fun `by default an edited copy is overwritten, with a warning saying so and how to keep edits`() {
        val project = project("consumer { }")
        project.file(".agents/skills/librarian/SKILL.md", "an earlier librarian, edited")
        project.file(".agents/skills/librarian/notes.md", "a file of our own")
        project.recordedAs(".agents/skills/librarian", mapOf("SKILL.md" to "an earlier librarian"))
        val output = project.run("classes").output

        assertEquals(librarian, project.text(".agents/skills/librarian/SKILL.md"))
        assertFalse(Files.exists(project.path(".agents/skills/librarian/notes.md")))
        assertContains(output, "dependencyskills: OVERWROTE local edits to the librarian skill in .agents/skills/librarian/")
        assertContains(output, "dependencySkills { consumer { refresh = SkillRefresh.UnlessEdited } }")
    }

    @Test
    fun `a block switched off, or the plugin switched off, writes nothing`() {
        val off = project("consumer { enabled = false }")
        off.run("classes")
        assertFalse(Files.exists(off.path(".agents/skills")))

        val pluginOff = project("consumer { }")
        pluginOff.run("classes", "-PdependencySkills.enabled=false")
        assertFalse(Files.exists(pluginOff.path(".agents/skills")))
    }

    @Test
    fun `keeps what the installer recorded in the shared manifest`() {
        val project = project("consumer { }")
        project.file(AgentSkills.MANIFEST, """
            {"version": "0.1.0a1", "changes": [{"kind": "claude-mcp", "name": "librarian"}]}
        """.trimIndent())
        project.run("classes")

        val manifest = project.text(AgentSkills.MANIFEST)
        assertContains(manifest, "\"kind\": \"claude-mcp\"")
        assertContains(manifest, "\"version\": \"0.1.0a1\"")
        assertContains(manifest, "\"path\": \".agents/skills/librarian\"")
    }

    @Test
    fun `works with the configuration cache, stored and reused`() {
        val project = project("consumer { }")
        project.run("classes", "--configuration-cache")
        Files.walk(project.path(".agents/skills")).sorted(Comparator.reverseOrder()).forEach(Files::delete)
        val reused = project.run("classes", "--configuration-cache").output

        assertContains(reused, "Reusing configuration cache")
        assertEquals(librarian, project.text(".agents/skills/librarian/SKILL.md"))
    }
}
