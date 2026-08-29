package org.dependencyskills.codex.core

import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ModelLocationTest {

    private val home = mapOf("user.home" to "/home/dev")

    @Test
    fun `models sit beside the store, not inside it`() {
        assertEquals(
            "/home/dev/.gradle/dscodex/models",
            ModelLocation.directory(emptyMap(), home).toString(),
        )
    }

    @Test
    fun `the store's override moves the models with it`() {
        // Someone who relocated the store did not relocate half of it.
        val moved = ModelLocation.directory(
            emptyMap(),
            home + (CodexLocation.OVERRIDE_PROPERTY to "/srv/shared/dscodex"),
        )
        assertEquals("/srv/shared/dscodex/models", moved.toString())
    }

    @Test
    fun `a models directory can be shared without moving the store`() {
        val shared = ModelLocation.directory(
            emptyMap(),
            home + (ModelLocation.OVERRIDE_PROPERTY to "/srv/models"),
        )
        assertEquals("/srv/models", shared.toString())
    }

    @Test
    fun `an environment override works for someone who cannot pass a system property`() {
        assertEquals(
            "/srv/models",
            ModelLocation.directory(
                mapOf(ModelLocation.OVERRIDE_ENV to "/srv/models"), home,
            ).toString(),
        )
    }

    @Test
    fun `a model that is not installed is absent rather than an error`() {
        val empty = createTempDirectory("models")
        assertNull(
            ModelLocation.find(
                "bge-m3-f16.gguf", emptyMap(),
                home + (ModelLocation.OVERRIDE_PROPERTY to empty.toString()),
            ),
        )
    }

    @Test
    fun `an installed model is found by its file name`() {
        val dir = createTempDirectory("models")
        val model = dir.resolve("bge-m3-f16.gguf")
        Files.writeString(model, "not really a model")
        assertEquals(
            model,
            ModelLocation.find(
                "bge-m3-f16.gguf", emptyMap(),
                home + (ModelLocation.OVERRIDE_PROPERTY to dir.toString()),
            ),
        )
    }

    @Test
    fun `a directory with the model's name is not a model`() {
        val dir = createTempDirectory("models")
        Files.createDirectory(dir.resolve("bge-m3-f16.gguf"))
        assertNull(
            ModelLocation.find(
                "bge-m3-f16.gguf", emptyMap(),
                home + (ModelLocation.OVERRIDE_PROPERTY to dir.toString()),
            ),
        )
    }

    @Test
    fun `the instructions name the exact path and both overrides`() {
        // The message is the documentation. Somebody meeting this has not read the README and
        // should not have to: everything needed to act is in front of them.
        val text = ModelLocation.instructionsFor("bge-m3-f16.gguf", emptyMap(), home)
        assertTrue(text.contains("/home/dev/.gradle/dscodex/models/bge-m3-f16.gguf"), text)
        assertTrue(text.contains(ModelLocation.OVERRIDE_PROPERTY), text)
        assertTrue(text.contains(ModelLocation.OVERRIDE_ENV), text)
        assertTrue(text.contains("Nothing downloads it for you"), text)
    }
}
