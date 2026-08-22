package org.dependencyskills.test0

import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Proves the harness can see the test0 fixture before any parser exists. Once a
 * parser lands, these become assertions on the entries it produces.
 */
class FixtureWiringTest {

    private val test0 = Path.of(
        System.getProperty("test0.dir") ?: error("test0.dir system property not set"),
    )

    @Test
    fun `the retry capability is present at every doc level`() {
        for (level in listOf("l0", "l1", "l2", "l3")) {
            val file = test0.resolve("test0/$level/Retry.kt")
            assertTrue(file.exists(), "missing fixture: $file")
            val text = file.readText()
            assertTrue("retryWithBackoff" in text, "no transparent capability in $file")
            assertTrue("class Policy" in text, "no opaque capability in $file")
        }
    }

    @Test
    fun `L3 carries the custom tags as raw text`() {
        val l3 = test0.resolve("test0/l3/Retry.kt").readText()
        for (tag in listOf("@capability", "@triggers", "@category", "@notFor", "@similar", "@since")) {
            assertTrue(tag in l3, "L3 fixture is missing $tag")
        }
    }

    @Test
    fun `the inheritance case has a documented interface and an undocumented override`() {
        val file = test0.resolve("test0/inherit/Retrier.kt").readText()
        assertTrue("interface Retrier" in file)
        assertTrue("class DefaultRetrier" in file)
    }
}
