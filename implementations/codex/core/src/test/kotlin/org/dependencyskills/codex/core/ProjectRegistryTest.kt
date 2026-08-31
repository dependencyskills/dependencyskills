package org.dependencyskills.codex.core

import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * Which projects exist, and what each may search.
 *
 * The rules here are not conveniences. Scope is the containment boundary — a shared store holds
 * entries from every library any project on this machine has ever resolved — so every test below is
 * about a way the boundary could widen or collapse without anyone noticing.
 */
class ProjectRegistryTest {

    private val alpha = Coordinate("maven", "com.example:alpha:1.0")
    private val beta = Coordinate("maven", "com.example:beta:1.0")
    private val gamma = Coordinate("maven", "com.example:gamma:1.0")

    private fun store(): Codex = Codex.open(createTempDirectory("registry").resolve("codex.db"))

    @Test
    fun `a project nobody has reported is null, not empty`() {
        // The distinction the whole table exists for. "No build has told me about this project" and
        // "this project resolved nothing" are different facts, and answering both with an empty
        // result is how a broken setup looks exactly like a working one.
        store().use { codex ->
            assertNull(codex.projectScope("/work/never-built"))
        }
    }

    @Test
    fun `what a project reported comes back`() {
        store().use { codex ->
            codex.recordProject("/work/a", "/work/a", "maven", listOf(alpha, beta))
            val scope = assertNotNull(codex.projectScope("/work/a"))
            assertEquals(setOf(alpha, beta), scope.coordinates)
            assertEquals(1, scope.contributors)
        }
    }

    @Test
    fun `a rebuild replaces the previous set, so a removed dependency leaves`() {
        store().use { codex ->
            codex.recordProject("/work/a", "/work/a", "maven", listOf(alpha, beta))
            codex.recordProject("/work/a", "/work/a", "maven", listOf(alpha))
            assertEquals(setOf(alpha), assertNotNull(codex.projectScope("/work/a")).coordinates)
        }
    }

    @Test
    fun `projects sharing a name see the union`() {
        store().use { codex ->
            codex.recordProject("/work/server", "acme", "maven", listOf(alpha))
            codex.recordProject("/work/client", "acme", "maven", listOf(beta))

            assertEquals(setOf(alpha, beta), assertNotNull(codex.projectScope("/work/server")).coordinates)
            assertEquals(setOf(alpha, beta), assertNotNull(codex.projectScope("/work/client")).coordinates)
            assertEquals(2, assertNotNull(codex.projectScope("/work/server")).contributors)
        }
    }

    @Test
    fun `one member's build does not erase another member's coordinates`() {
        // The reason rows are keyed by path rather than by name. Keyed by name, each member's build
        // would replace the whole group and the scope would flap between them on alternating
        // builds - presenting as intermittently missing libraries rather than as a bug.
        store().use { codex ->
            codex.recordProject("/work/server", "acme", "maven", listOf(alpha))
            codex.recordProject("/work/client", "acme", "maven", listOf(beta))
            codex.recordProject("/work/server", "acme", "maven", listOf(alpha, gamma))

            assertEquals(
                setOf(alpha, beta, gamma),
                assertNotNull(codex.projectScope("/work/client")).coordinates,
            )
        }
    }

    @Test
    fun `unnamed projects never share scope`() {
        // With no name configured the name IS the path, which cannot collide. Defaulting to
        // something like a root project name would merge two unrelated projects both called `app`,
        // making one project's poisoned entry reachable from another that never depended on it.
        store().use { codex ->
            codex.recordProject("/work/a", "/work/a", "maven", listOf(alpha))
            codex.recordProject("/work/b", "/work/b", "maven", listOf(beta))

            assertEquals(setOf(alpha), assertNotNull(codex.projectScope("/work/a")).coordinates)
            assertEquals(setOf(beta), assertNotNull(codex.projectScope("/work/b")).coordinates)
        }
    }

    @Test
    fun `a project can be renamed into a group, and out of one`() {
        store().use { codex ->
            codex.recordProject("/work/a", "/work/a", "maven", listOf(alpha))
            codex.recordProject("/work/b", "acme", "maven", listOf(beta))
            assertEquals(setOf(alpha), assertNotNull(codex.projectScope("/work/a")).coordinates)

            codex.recordProject("/work/a", "acme", "maven", listOf(alpha))
            assertEquals(setOf(alpha, beta), assertNotNull(codex.projectScope("/work/a")).coordinates)

            codex.recordProject("/work/a", "/work/a", "maven", listOf(alpha))
            assertEquals(setOf(alpha), assertNotNull(codex.projectScope("/work/a")).coordinates)
            assertEquals(setOf(beta), assertNotNull(codex.projectScope("/work/b")).coordinates)
        }
    }

    @Test
    fun `a project that resolved nothing is registered, and empty`() {
        store().use { codex ->
            codex.recordProject("/work/empty", "/work/empty", "maven", emptyList())
            val scope = assertNotNull(codex.projectScope("/work/empty"), "registered, so not null")
            assertTrue(scope.coordinates.isEmpty())
        }
    }

    @Test
    fun `the registry survives a reopen, so a restart does not lose what a build reported`() {
        val file = createTempDirectory("registry").resolve("codex.db")
        Codex.open(file).use { it.recordProject("/work/a", "acme", "maven", listOf(alpha)) }
        Codex.open(file).use {
            assertEquals(setOf(alpha), assertNotNull(it.projectScope("/work/a")).coordinates)
        }
    }
}
