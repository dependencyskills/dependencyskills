package org.dependencyskills.codex.harvester

import org.dependencyskills.codex.core.NewEntry
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Real published artifacts, resolved by the build into the ordinary Gradle cache.
 *
 * These are pinned by version, so the counts asserted against them are stable. Nothing here
 * reaches the network: the build resolved them, and these tests read the files it left behind.
 */
internal object Fixtures {

    private val paths: List<Path> =
        (System.getProperty("codex.harvester.fixtures")
            // Not a skip. A test that quietly passes when its input is missing is the same
            // failure this story exists to prevent, one level up.
            ?: error("no fixtures on the test JVM - the build did not resolve the fixture configuration"))
            .split(File.pathSeparator)
            .map { Path.of(it) }

    /** Java, package-rooted, an Apache licence header in every file. */
    val javaSources: Path get() = named("slf4j-api-2.0.17-sources.jar")

    /** Kotlin, source-set-rooted: a multiplatform publication. */
    val kotlinSources: Path get() = named("kotlinx-serialization-core-jvm-1.11.0-sources.jar")

    /** A real archive holding no source at all - the same library's classes. */
    val noSources: Path get() = named("slf4j-api-2.0.17.jar")

    val all: List<Path> get() = paths

    private fun named(name: String): Path =
        paths.firstOrNull { it.fileName.toString() == name }
            ?: error("fixture $name is not on the test JVM; resolved: ${paths.map { it.fileName }}")
}

/**
 * A byte-for-byte fingerprint of what a harvest produced.
 *
 * List equality would already catch a changed entry. This exists so the purity assertion can say
 * what it means literally: same jar, same bytes, in the same order.
 */
internal fun fingerprint(entries: List<NewEntry>): String {
    val digest = MessageDigest.getInstance("SHA-256")
    entries.forEach { entry ->
        listOf(
            entry.symbol, entry.signature, entry.doc, entry.lang, entry.docFormat,
            entry.state.name, entry.provenance.extractor,
        ).forEach {
            digest.update(it.toByteArray(Charsets.UTF_8))
            digest.update(0)
        }
    }
    return digest.digest().joinToString("") { "%02x".format(it) }
}

/** A jar written from source text, for the cases no published library conveniently contains. */
internal fun jarOf(directory: Path, name: String, vararg files: Pair<String, String>): Path {
    val jar = directory.resolve(name)
    ZipOutputStream(Files.newOutputStream(jar)).use { out ->
        files.forEach { (path, source) ->
            out.putNextEntry(ZipEntry(path))
            out.write(source.toByteArray(Charsets.UTF_8))
            out.closeEntry()
        }
    }
    return jar
}
