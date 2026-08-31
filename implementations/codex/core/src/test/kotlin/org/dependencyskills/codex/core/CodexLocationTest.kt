package org.dependencyskills.codex.core

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CodexLocationTest {
    private val home = mapOf("user.home" to "/home/dev")

    @Test fun `defaults to a dotdir in the home directory, the way developer tools do`() {
        val d = CodexLocation.directory(env = emptyMap(), sysProps = home)
        assertEquals("/home/dev/.dscodex", d.toString())
    }

    @Test fun `is not inside any build system's directory`() {
        // The store belonged to Gradle when the plugin opened it. The plugin no longer depends on
        // this module at all, so a path under `.gradle` would claim an ownership that has moved.
        val d = CodexLocation.directory(env = emptyMap(), sysProps = home).toString()
        assertTrue(".gradle" !in d, "the store is not Gradle's any more: $d")
        assertTrue(".m2" !in d, "nor Maven's: $d")
    }

    @Test fun `ignores GRADLE_USER_HOME`() {
        // Deliberate, and the reason is worth a test rather than a comment. The service is started
        // from a shell or a launch agent and never sees the value a build was run with, so
        // honouring it would let the two disagree about where the store is and neither would notice.
        val d = CodexLocation.directory(env = mapOf("GRADLE_USER_HOME" to "/ci/gradle"), sysProps = home)
        assertEquals("/home/dev/.dscodex", d.toString())
    }

    @Test fun `an override wins, so an unusual layout is not blocked`() {
        assertEquals("/shared/codex", CodexLocation.directory(
            env = mapOf("DEPENDENCYSKILLS_CODEX_DIR" to "/shared/codex"), sysProps = home).toString())
        assertEquals("/prop/codex", CodexLocation.directory(
            env = mapOf(CodexLocation.OVERRIDE_ENV to "/env/codex"),
            sysProps = home + (CodexLocation.OVERRIDE_PROPERTY to "/prop/codex")).toString())
    }

    @Test fun `the version is in the schema, not the path`() {
        assertTrue(CodexLocation.directory(env = emptyMap(), sysProps = home).toString().endsWith("/.dscodex"))
    }

    @Test fun `the old location can still be named, so its contents can be reported`() {
        assertEquals(
            "/home/dev/.gradle/dscodex/codex.db",
            CodexLocation.legacyDatabaseFile(env = emptyMap(), sysProps = home).toString(),
        )
        // GRADLE_USER_HOME is part of what the old path meant, so it is honoured for the lookup
        // even though it no longer decides where the store lives.
        assertEquals(
            "/ci/gradle/dscodex/codex.db",
            CodexLocation.legacyDatabaseFile(
                env = mapOf("GRADLE_USER_HOME" to "/ci/gradle"), sysProps = home).toString(),
        )
    }
}
