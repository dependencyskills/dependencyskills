package org.dependencyskills.codex.inference

import java.lang.foreign.Arena
import java.lang.foreign.FunctionDescriptor
import java.lang.foreign.Linker
import java.lang.foreign.MemorySegment
import java.lang.foreign.SymbolLookup
import java.lang.foreign.ValueLayout.ADDRESS
import java.lang.foreign.ValueLayout.JAVA_BYTE
import java.lang.foreign.ValueLayout.JAVA_INT
import java.lang.invoke.MethodHandle
import java.nio.file.Files
import java.nio.file.Path

actual fun openGenerator(modelPath: String, contextTokens: Int, gpuLayers: Int): TextGenerator =
    LlamaGenerator(modelPath, contextTokens, gpuLayers)

/**
 * The JVM binding, over FFM.
 *
 * Four functions, every argument a pointer or an int. That is the whole surface, and it is the
 * reason this is maintainable: llama.cpp's own entry points take parameter structs by value and
 * those structs gain fields between releases, so binding them from Java means a hand-written
 * memory layout per version — where a mismatch is not a compile error but silent corruption.
 * `native/dscodex_llama.c` absorbs that, and a change upstream breaks its compilation instead.
 */
private class LlamaGenerator(modelPath: String, contextTokens: Int, gpuLayers: Int) : TextGenerator {

    private val arena = Arena.ofShared()
    private val session: MemorySegment

    init {
        if (!Files.isRegularFile(Path.of(modelPath))) {
            throw GeneratorUnavailableException("no model file at $modelPath")
        }
        val handle = arena.allocateFrom(modelPath).let { path ->
            Native.load.invokeExact(path, contextTokens, gpuLayers) as MemorySegment
        }
        if (handle.address() == 0L) {
            arena.close()
            throw GeneratorUnavailableException("llama.cpp could not load the model at $modelPath")
        }
        session = handle
    }

    override fun generate(prompt: String, maxTokens: Int): String {
        // Templating and generation are separate calls so a failure to template is not reported
        // as a failure to generate. They are different faults with different fixes.
        val templated = applyTemplate(prompt)
        Arena.ofConfined().use { call ->
            val out = call.allocate(OUTPUT_BYTES.toLong())
            val written = Native.generate.invokeExact(
                session, call.allocateFrom(templated), out, OUTPUT_BYTES, maxTokens,
            ) as Int
            if (written < 0) throw GeneratorUnavailableException("generation failed ($written)")
            return readUtf8(out, written)
        }
    }

    private fun applyTemplate(prompt: String): String = Arena.ofConfined().use { call ->
        val out = call.allocate(TEMPLATE_BYTES.toLong())
        val written = Native.applyTemplate.invokeExact(
            session, call.allocateFrom(prompt), out, TEMPLATE_BYTES,
        ) as Int
        // A model with no chat template is not an error worth failing on — it is a base model,
        // and the bare prompt is the right thing to send it.
        if (written < 0 || written > TEMPLATE_BYTES) prompt else readUtf8(out, written)
    }

    private fun readUtf8(segment: MemorySegment, length: Int): String {
        val bytes = ByteArray(length)
        MemorySegment.copy(segment, JAVA_BYTE, 0, bytes, 0, length)
        return String(bytes, Charsets.UTF_8)
    }

    override fun close() {
        Native.free.invokeExact(session)
        arena.close()
    }

    private companion object {
        /** Generous: the caller bounds the real length with `maxTokens`. */
        const val OUTPUT_BYTES = 64 * 1024
        const val TEMPLATE_BYTES = 256 * 1024
    }
}

/**
 * The four symbols, looked up once.
 *
 * The library is carried in the jar, one per platform, and extracted on first use. That is how
 * `tree-sitter` reaches this project and how a consumer gets a working runtime from dependency
 * resolution alone, with no install step and no `java.library.path` to set.
 *
 * `dscodex.native.dir` overrides it, which is what a build that has just compiled the shim uses.
 */
internal object Native {

    /** `macos-aarch64`, `linux-x86_64`, `windows-x86_64` — the directory names in the jar. */
    internal val platform: String = run {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        val family = when {
            os.startsWith("mac") || os.contains("darwin") -> "macos"
            os.startsWith("win") -> "windows"
            os.startsWith("linux") -> "linux"
            else -> throw GeneratorUnavailableException("no native build for os '$os'")
        }
        val cpu = when (arch) {
            "aarch64", "arm64" -> "aarch64"
            "x86_64", "amd64" -> "x86_64"
            else -> throw GeneratorUnavailableException("no native build for architecture '$arch'")
        }
        "$family-$cpu"
    }

    private val lookup: SymbolLookup = run {
        val arena = Arena.ofAuto()
        val name = System.mapLibraryName("dscodex")

        // A directory named explicitly wins, so a working copy can be tested before it is packaged.
        System.getProperty("dscodex.native.dir")?.let { dir ->
            val file = Path.of(dir).resolve(name)
            if (Files.isRegularFile(file)) return@run SymbolLookup.libraryLookup(file, arena)
        }
        extractFromJar(name)?.let { return@run SymbolLookup.libraryLookup(it, arena) }

        // Last resort: the platform's own search, for an operator who placed it themselves.
        runCatching { SymbolLookup.libraryLookup(name, arena) }.getOrElse {
            throw GeneratorUnavailableException(
                "no native library for $platform: it is not in this jar, not in " +
                    "dscodex.native.dir, and not on the library path", it,
            )
        }
    }

    /**
     * Unpacks the platform's library beside the JVM's temp directory, once.
     *
     * Keyed on size so a rebuilt library replaces a stale extraction — a developer who rebuilds
     * the shim and gets the previous one back would be debugging a ghost.
     */
    private fun extractFromJar(name: String): Path? {
        val resource = "/dscodex/$platform/$name"
        val stream = Native::class.java.getResourceAsStream(resource) ?: return null
        return stream.use { input ->
            val bytes = input.readBytes()
            val target = Path.of(System.getProperty("java.io.tmpdir"))
                .resolve("dscodex-$platform-${bytes.size}")
                .resolve(name)
            if (!Files.isRegularFile(target) || Files.size(target) != bytes.size.toLong()) {
                Files.createDirectories(target.parent)
                val partial = target.resolveSibling("$name.${ProcessHandle.current().pid()}")
                Files.write(partial, bytes)
                // Atomic, so two JVMs extracting at once cannot see a half-written library.
                runCatching { Files.move(partial, target, java.nio.file.StandardCopyOption.ATOMIC_MOVE) }
                    .onFailure { Files.deleteIfExists(partial) }
            }
            target.takeIf { Files.isRegularFile(it) }
        }
    }

    private fun handle(name: String, descriptor: FunctionDescriptor): MethodHandle =
        Linker.nativeLinker().downcallHandle(
            lookup.find(name).orElseThrow {
                GeneratorUnavailableException("libdscodex does not export $name")
            },
            descriptor,
        )

    val load: MethodHandle = handle("dsc_load", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT, JAVA_INT))
    val applyTemplate: MethodHandle = handle("dsc_apply_template", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT))
    val generate: MethodHandle = handle("dsc_generate", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT, JAVA_INT))
    val free: MethodHandle = handle("dsc_free", FunctionDescriptor.ofVoid(ADDRESS))

    // The encoder face. Same library, same lookup - see RAD-0054.
    val encoderLoad: MethodHandle = handle("dsc_encoder_load", FunctionDescriptor.of(ADDRESS, ADDRESS, JAVA_INT))
    val encoderPooling: MethodHandle = handle("dsc_encoder_pooling", FunctionDescriptor.of(JAVA_INT, ADDRESS))
    val encoderDim: MethodHandle = handle("dsc_encoder_dim", FunctionDescriptor.of(JAVA_INT, ADDRESS))
    val embed: MethodHandle = handle("dsc_embed", FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, JAVA_INT))
}
