package org.dependencyskills.codex.server

import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * What the operator gets to decide.
 *
 * The settings here decide how much of somebody's machine this uses, so the tests are mostly about
 * the two ways configuration goes wrong quietly: a default that is not the safe one, and a file
 * that does not apply without saying so.
 */
class CodexConfigTest {

    private fun store() = createTempDirectory("config").resolve("codex.db")

    @Test
    fun `no file is not an error, and the defaults are the conservative ones`() {
        // A machine with no config runs the version that does not surprise anyone: one coordinate
        // at a time, and no model held while nothing is happening.
        val config = CodexConfig.load(store())
        assertEquals(1, config.indexing.concurrency)
        assertFalse(config.indexing.keepModelResident, "holding a model resident must be asked for")
        assertEquals("127.0.0.1", config.server.host, "a dependency graph does not go on the network by default")
        assertEquals(SERVER_PORT, config.server.port)
    }

    @Test
    fun `the model can be held resident, which is the point of the file`() {
        // A workstation with memory to spare, or a service dedicated to many machines, pays the
        // load once rather than on every pass.
        val store = store()
        CodexConfig.file(store).writeText(
            """
            [indexing]
            keepModelResident = true
            concurrency = 4
            """.trimIndent(),
        )
        val config = CodexConfig.load(store)
        assertTrue(config.indexing.keepModelResident)
        assertEquals(4, config.indexing.concurrency)
        // Untouched keys keep their defaults rather than becoming null or zero.
        assertEquals(300, config.indexing.unloadAfterIdleSeconds)
        assertEquals("127.0.0.1", config.server.host)
    }

    @Test
    fun `the listening address can be widened deliberately`() {
        val store = store()
        CodexConfig.file(store).writeText(
            """
            [server]
            host = "0.0.0.0"
            port = 9000
            """.trimIndent(),
        )
        val config = CodexConfig.load(store)
        assertEquals("0.0.0.0", config.server.host)
        assertEquals(9000, config.server.port)
    }

    @Test
    fun `a malformed file is refused, not quietly replaced with defaults`() {
        // The failure this exists to prevent: somebody caps the service to one worker, mistypes it,
        // and finds it running eight with nothing anywhere saying why. Config that silently does
        // not apply is worse than config that fails.
        val store = store()
        CodexConfig.file(store).writeText(
            """
            [indexing]
            concurrency = "not a number"
            """.trimIndent(),
        )
        assertFailsWith<Throwable> { CodexConfig.load(store) }
    }

    @Test
    fun `the config sits beside the store, wherever that is`() {
        // It follows the store rather than deciding for itself, so moving the store with --store
        // takes its configuration with it instead of silently reading somebody else's.
        val store = store()
        assertEquals(store.parent, CodexConfig.file(store).parent)
        assertTrue(CodexConfig.file(store).fileName.toString() == CodexConfig.FILE_NAME)
    }
}
