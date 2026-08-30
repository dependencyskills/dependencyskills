package org.dependencyskills.plugin

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CoordinatesTest {

    private val alpha = Coordinate("maven", "com.example:alpha:1.0")

    @Test
    fun `nothing is ignored by default`() {
        assertFalse(Coordinates.ignored(alpha, emptySet()))
    }

    @Test
    fun `a library is ignored at every version`() {
        // A developer naming a library does not mean "except when it upgrades", and an ignore
        // that silently stops applying on the next release is worse than one that never worked.
        assertTrue(Coordinates.ignored(alpha, setOf("com.example:alpha")))
        assertTrue(Coordinates.ignored(Coordinate("maven", "com.example:alpha:2.0"), setOf("com.example:alpha")))
    }

    @Test
    fun `a single version can still be named exactly`() {
        assertTrue(Coordinates.ignored(alpha, setOf("com.example:alpha:1.0")))
        assertFalse(Coordinates.ignored(Coordinate("maven", "com.example:alpha:2.0"), setOf("com.example:alpha:1.0")))
    }

    @Test
    fun `a near miss is not a match`() {
        assertFalse(Coordinates.ignored(alpha, setOf("com.example:alphabet")))
        assertFalse(Coordinates.ignored(alpha, setOf("com.example")))
    }
}
