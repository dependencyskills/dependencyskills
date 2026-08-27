package org.dependencyskills.gradle

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Assumptions
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.File

class SkillValidationTest {

    @TempDir lateinit var root: File

    private fun skill(name: String, frontmatter: String? = null, body: String = "# x\n") {
        val fm = frontmatter ?: "name: $name\ndescription: $LONG_DESC"
        File(root, name).mkdirs()
        File(root, "$name/SKILL.md").writeText("---\n$fm\n---\n\n$body")
    }

    private fun errors() = SkillValidation.validate(root).filter { it.fatal }
    private fun warnings() = SkillValidation.validate(root).filter { !it.fatal }

    @Test fun `a well-formed skill passes clean`() {
        skill("org.dependencyskills.types")
        assertTrue(SkillValidation.validate(root).isEmpty()) {
            SkillValidation.report(SkillValidation.validate(root))
        }
    }

    @Test fun `missing SKILL_md is fatal`() {
        File(root, "io.acme.thing").mkdirs()
        assertTrue(errors().any { it.message.contains("no SKILL.md") })
    }

    @Test fun `missing description is fatal`() {
        skill("io.acme.thing", "name: io.acme.thing")
        assertTrue(errors().any { it.message.contains("no 'description'") })
    }

    @Test fun `scaffold placeholder description is fatal`() {
        skill("io.acme.thing", "name: io.acme.thing\ndescription: <one line - what this is for>")
        assertTrue(errors().any { it.message.contains("placeholder") })
    }

    @Test fun `frontmatter name must match the directory`() {
        skill("io.acme.thing", "name: something.else\ndescription: $LONG_DESC")
        assertTrue(errors().any { it.message.contains("does not match its directory") })
    }

    // --- filesystem rules: these become directories on a consumer's machine

    @Test fun `windows reserved name is fatal even with an extension`() {
        skill("nul.helper")
        assertTrue(errors().any { it.message.contains("reserved device name") })
    }

    @Test fun `leading underscore is fatal because Android strips it`() {
        skill("_internal.thing")
        assertTrue(errors().any { it.message.contains("underscore") })
    }

    @Test fun `names differing only by case collide`() {
        skill("io.acme.Thing")
        skill("io.acme.thing")
        // Only exercisable on a case-sensitive filesystem. macOS and Windows
        // default volumes fold the two directories into one, leaving nothing to
        // collide here — which is exactly why the rule protects those consumers.
        Assumptions.assumeTrue(
            root.listFiles()?.count { it.isDirectory } == 2,
            "case-insensitive filesystem: cannot create two names differing only by case",
        )
        assertTrue(errors().any { it.message.contains("differ only by case") })
    }

    @Test fun `an unprefixed name warns about the flat namespace`() {
        skill("utils")
        assertTrue(warnings().any { it.message.contains("flat namespace") })
    }

    @Test fun `a thin description warns rather than fails`() {
        skill("io.acme.thing", "name: io.acme.thing\ndescription: Does things.")
        assertTrue(errors().none { it.message.contains("description") })
        assertTrue(warnings().any { it.message.contains("discriminative") })
    }

    // --- length rules

    @Test fun `an over-long description is fatal at the spec cap`() {
        val tooLong = "x".repeat(SkillValidation.MAX_DESCRIPTION + 1)
        skill("io.acme.thing", "name: io.acme.thing\ndescription: $tooLong")
        assertTrue(errors().any { it.message.contains("spec caps it") })
    }

    @Test fun `a description exactly at the cap is accepted`() {
        val exact = "x".repeat(SkillValidation.MAX_DESCRIPTION)
        skill("io.acme.thing", "name: io.acme.thing\ndescription: $exact")
        assertTrue(errors().none { it.message.contains("description") })
    }

    @Test fun `an over-long body warns about the context budget`() {
        val fat = "word ".repeat(SkillValidation.MAX_BODY_TOKENS * 4)
        skill("io.acme.thing", body = fat)
        assertTrue(warnings().any { it.message.contains("token") }) {
            SkillValidation.report(SkillValidation.validate(root))
        }
        assertTrue(errors().isEmpty()) { "body length should warn, not fail" }
    }

    @Test fun `a normal body does not warn about length`() {
        skill("io.acme.thing", body = "# thing\n\nA few paragraphs of ordinary prose.\n")
        assertTrue(warnings().none { it.message.contains("token") })
    }

    @Test fun `a deep bundled path warns about the Windows limit`() {
        skill("io.acme.thing")
        val deep = File(root, "io.acme.thing/references/" + "nested/".repeat(12))
        deep.mkdirs()
        File(deep, "a-fairly-long-reference-filename.md").writeText("x")
        assertTrue(warnings().any { it.message.contains("260-character") }) {
            SkillValidation.report(SkillValidation.validate(root))
        }
    }

    @Test fun `an empty skills directory is fatal`() {
        assertTrue(errors().any { it.message.contains("no skill directories") })
    }

    companion object {
        const val LONG_DESC =
            "An HTTP client with retry and backoff, for callers who would otherwise hand-roll one."
    }
}
