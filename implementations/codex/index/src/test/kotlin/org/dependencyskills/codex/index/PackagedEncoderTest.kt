package org.dependencyskills.codex.index

import org.dependencyskills.codex.inference.Pooling
import org.dependencyskills.codex.inference.openEncoder
import java.nio.file.Files
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The packaged encoder, taken off the classpath and opened.
 *
 * This is the step between "the model ships in a jar" and "a process can use it", and it did not
 * exist: every caller until now was handed a filesystem path by a test. A model that cannot be
 * reached from a running process is a model that ships and does nothing.
 */
class PackagedEncoderTest {

    /** Point [org.dependencyskills.codex.core.ModelLocation] somewhere disposable. */
    private fun <T> intoTemp(body: () -> T): T {
        val dir = createTempDirectory("packaged")
        val key = "dependencyskills.models.dir"
        val previous = System.getProperty(key)
        System.setProperty(key, dir.toString())
        try {
            return body()
        } finally {
            if (previous == null) System.clearProperty(key) else System.setProperty(key, previous)
        }
    }

    @Test
    fun `the packaged model is unpacked and opens`() {
        intoTemp {
            val packaged = assertNotNull(
                PackagedEncoder.unpack(),
                "the encoder artifact is not on the test classpath",
            )
            assertTrue(Files.isRegularFile(packaged.model), "nothing was written to ${packaged.model}")
            assertEquals("BAAI/bge-small-en-v1.5", packaged.name)
            assertEquals(384, packaged.dimensions)

            // The pooling comes from the artifact, not from this test and not from the GGUF's own
            // metadata - which says CLS, and which RAD-0048 measured as the wrong one to use.
            assertEquals(Pooling.Mean, packaged.pooling)

            openEncoder(packaged.model.toString(), packaged.pooling).use { encoder ->
                assertEquals(packaged.dimensions, encoder.dimensions)
                assertEquals(packaged.pooling, encoder.pooling)
                val vector = encoder.embed("writes bytes to the response as they become available")
                assertEquals(384, vector.size)
                assertTrue(vector.any { it != 0f }, "the model produced an empty vector")
            }
        }
    }

    @Test
    fun `unpacking twice does not extract twice`() {
        intoTemp {
            val first = assertNotNull(PackagedEncoder.unpack())
            val stamp = Files.getLastModifiedTime(first.model)
            val second = assertNotNull(PackagedEncoder.unpack())
            assertEquals(first.model, second.model)
            assertEquals(stamp, Files.getLastModifiedTime(second.model), "the model was rewritten")
        }
    }

    @Test
    fun `the unpacked file is named for its digest, so a new model cannot be shadowed`() {
        // The trap the native library's extraction already learned: a stale copy under a stable
        // name silently wins, and a developer who upgrades debugs a ghost.
        intoTemp {
            val packaged = assertNotNull(PackagedEncoder.unpack())
            assertTrue(
                packaged.model.fileName.toString().startsWith("86776c71"),
                "expected a digest-named file, got ${packaged.model.fileName}",
            )
        }
    }

    @Test
    fun `a corrupted extraction is refused rather than opened`() {
        intoTemp {
            val packaged = assertNotNull(PackagedEncoder.unpack())
            // Replace the unpacked model with something that is not it, and re-unpack: the digest
            // check is on the extraction, so a truncated file has to be caught on the way in.
            Files.write(packaged.model, "not a model".toByteArray())
            Files.delete(packaged.model)
            val again = assertNotNull(PackagedEncoder.unpack())
            assertTrue(Files.size(again.model) > 60_000_000, "the model was not re-extracted")
        }
    }
}
