package org.dependencyskills.codex.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexLocationTest {
    private val home = mapOf("user.home" to "/home/dev")

    @Test fun `defaults beside gradle's caches, not inside them`() {
        val d = CodexLocation.directory(env = emptyMap(), sysProps = home)
        assertEquals("/home/dev/.gradle/dscodex", d.toString())
        assertTrue(!d.toString().contains("caches"), "Gradle garbage-collects caches/; this must sit outside it")
    }

    @Test fun `respects GRADLE_USER_HOME`() {
        val d = CodexLocation.directory(env = mapOf("GRADLE_USER_HOME" to "/ci/gradle"), sysProps = home)
        assertEquals("/ci/gradle/dscodex", d.toString())
    }

    @Test fun `an override wins, so an unusual layout is not blocked`() {
        assertEquals("/shared/codex", CodexLocation.directory(
            env = mapOf("DEPENDENCYSKILLS_CODEX_DIR" to "/shared/codex"), sysProps = home).toString())
        assertEquals("/prop/codex", CodexLocation.directory(
            env = mapOf("GRADLE_USER_HOME" to "/ci/gradle"),
            sysProps = home + (CodexLocation.OVERRIDE_PROPERTY to "/prop/codex")).toString())
    }

    @Test fun `the version is in the schema, not the path`() {
        assertTrue(CodexLocation.directory(env = emptyMap(), sysProps = home).toString().endsWith("/dscodex"))
    }
}
