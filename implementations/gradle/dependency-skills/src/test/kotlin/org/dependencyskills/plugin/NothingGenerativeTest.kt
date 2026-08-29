package org.dependencyskills.plugin

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

/**
 * The plugin cannot run a model, and this is what enforces it.
 *
 * The summariser is a model call per documented declaration — one small project yields about
 * 5,400, and on a small model that is minutes while on a larger one it is hours. Running any of
 * that inside a build would be indefensible, so "never runs during a build" is one of the
 * summariser's acceptance criteria.
 *
 * A criterion of that shape is usually met with a comment and a good intention. This meets it
 * with the classpath: the plugin depends on `core` and nothing else, so the summariser's classes
 * are not merely unused here — they are **absent**, and the JVM will say so. Somebody who wants
 * to call it has to add a dependency, which is a visible act in a reviewed file rather than an
 * import nobody notices.
 *
 * The same holds for the generative runtime underneath it, which is the thing that would actually
 * load weights and allocate a multi-gigabyte arena.
 */
class NothingGenerativeTest {

    private fun loading(className: String) =
        assertFailsWith<ClassNotFoundException>("$className is reachable from the plugin") {
            Class.forName(className, false, javaClass.classLoader)
        }

    @Test
    fun `the summariser is not on the plugin's classpath`() {
        loading("org.dependencyskills.codex.summariser.Summariser")
        loading("org.dependencyskills.codex.summariser.Verification")
    }

    @Test
    fun `neither is the generative runtime it would need`() {
        // The summariser cannot run without a generator, so this is the second lock on the same
        // door - and the one that matters most, since this is what loads weights.
        loading("org.dependencyskills.codex.inference.TextGenerator")
        loading("org.dependencyskills.codex.inference.TextEncoder")
    }

    @Test
    fun `the store IS on the classpath, so this test can tell absence from a broken loader`() {
        // Without this, every assertion above would pass just as happily if the class loader were
        // misconfigured and nothing at all resolved. A test that cannot fail for the right reason
        // is not evidence.
        val store = Class.forName("org.dependencyskills.codex.core.Codex", false, javaClass.classLoader)
        assertTrue(store.name.endsWith("Codex"))
    }
}
