package org.dependencyskills.plugin

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** The `description` read as the lookup reads it, so the length the build checks is the one a consumer measures (#46). */
class SkillDescriptionTest {

    @Test
    fun `a folded description is its lines joined by spaces, not the indicator`() {
        val frontmatter = "name: acme\ndescription: >-\n  Format acme text\n  for display.\nlicense: MIT"
        assertEquals("Format acme text for display.", skillDescription(frontmatter))
    }

    @Test
    fun `a literal description keeps its line breaks`() {
        assertEquals("one\ntwo", skillDescription("description: |\n  one\n  two\nlicense: MIT"))
    }

    @Test
    fun `a plain or quoted description is the value`() {
        assertEquals("Format acme text.", skillDescription("description: Format acme text."))
        assertEquals("Format: acme text", skillDescription("description: \"Format: acme text\""))
    }

    @Test
    fun `an indicator with nothing under it is empty, so it reads as missing`() {
        assertEquals("", skillDescription("description: >-\nlicense: MIT"))
        assertNull(skillDescription("name: acme"))
    }

    @Test
    fun `a field that only ends in description is not the description`() {
        assertNull(skillDescription("name: acme\nmetadata:\n  description: not this one"))
    }
}
