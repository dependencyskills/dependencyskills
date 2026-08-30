package org.dependencyskills.codex.index

import org.dependencyskills.codex.core.ModelLocation
import org.dependencyskills.codex.inference.Pooling
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.jar.Manifest

/**
 * The encoder that ships in the `encoder` artifact, unpacked so llama.cpp can open it.
 *
 * **It has to become a file.** llama.cpp loads a model from a path or a `FILE*`, and there is no
 * portable way to make a `FILE*` from memory — `fmemopen` is POSIX and absent on Windows. So
 * bytes on the classpath are not enough, however much they are already in the process.
 *
 * Unpacked into [ModelLocation]'s directory rather than a working directory. That is machine-level
 * and is where [ADR-0013] says a model lives; a working directory would put 64 MB in every project
 * that ever asked a question.
 *
 * The same shape as the native library's extraction, and for the same reason: **keyed on the
 * digest**, so a new model version replaces a stale copy rather than being shadowed by it. A
 * developer who upgrades and silently keeps the old weights would be debugging a ghost.
 */
object PackagedEncoder {

    /** What the artifact declares about the model inside it. Read, never assumed. */
    data class Packaged(val model: Path, val name: String, val pooling: Pooling, val dimensions: Int)

    private const val RESOURCE = "/dependencyskills/encoder/model.gguf"

    /**
     * Unpacks the packaged encoder, or returns null when the artifact is not on the classpath.
     *
     * Null rather than an exception: a store that has been harvested but not embedded is an
     * ordinary state, and a caller can still answer lexically. What a caller must not do is treat
     * null as "there is no encoder" — it means *this* process cannot reach one.
     */
    fun unpack(): Packaged? {
        val manifest = manifest() ?: return null
        val digest = manifest.getValue("Encoder-Digest") ?: return null
        val pooling = manifest.getValue("Encoder-Pooling")?.let { declared ->
            // Read from the artifact, never hardcoded. The GGUF's own metadata says CLS for this
            // model and RAD-0048 measured mean as the better one, so the value that matters is
            // the one the artifact was built with - and #6 refuses to mix two in one index.
            Pooling.entries.firstOrNull { it.name.equals(declared, ignoreCase = true) }
        } ?: return null
        val dimensions = manifest.getValue("Encoder-Dimensions")?.toIntOrNull() ?: return null
        val name = manifest.getValue("Encoder-Model") ?: "unknown"

        val target = ModelLocation.directory().resolve("$digest.gguf")
        if (!Files.isRegularFile(target)) extract(target, digest)
        return Packaged(target, name, pooling, dimensions)
    }

    private fun extract(target: Path, expected: String) {
        val stream = PackagedEncoder::class.java.getResourceAsStream(RESOURCE)
            ?: error("the encoder artifact declares a model it does not contain")
        Files.createDirectories(target.parent)
        // Written beside and moved, so a second process cannot open a half-written model. The
        // native library's extraction learned this the same way.
        val partial = target.resolveSibling("${target.fileName}.${ProcessHandle.current().pid()}")
        try {
            stream.use { input -> Files.newOutputStream(partial).use { input.copyTo(it) } }
            val actual = MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(partial)).joinToString("") { "%02x".format(it) }
            // Checked here as well as at build time. The build verified what it packaged; this
            // verifies what arrived, and a jar can be replaced on a machine after it was built.
            check(actual == expected) {
                "the packaged encoder does not match its declared digest\n" +
                    "  expected $expected\n  actual   $actual"
            }
            Files.move(partial, target, StandardCopyOption.ATOMIC_MOVE)
        } finally {
            Files.deleteIfExists(partial)
        }
    }

    /**
     * The manifest of the jar this resource came from, not whichever one happens to be first.
     *
     * `JarURLConnection` resolves it from the resource's own URL, which matters: the classpath
     * holds many manifests and reading the wrong one would report another artifact's pooling.
     */
    private fun manifest(): java.util.jar.Attributes? {
        val url = PackagedEncoder::class.java.getResource(RESOURCE) ?: return null
        return runCatching {
            (url.openConnection() as java.net.JarURLConnection).manifest?.mainAttributes
        }.getOrNull()
    }
}
