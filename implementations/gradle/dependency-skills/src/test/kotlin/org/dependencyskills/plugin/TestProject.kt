package org.dependencyskills.plugin

import org.dependencyskills.codex.core.Codex
import org.dependencyskills.codex.core.CodexLocation
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

/**
 * A throwaway consuming project, its own store, and a Maven repository on disk.
 *
 * The repository is built here rather than resolved from Central so the tests prove the thing
 * the story asks for: everything the plugin needs is already local, and every run below passes
 * `--offline`.
 */
internal class TestProject(
    private val root: Path,
    private val extraPluginClasspath: List<File> = emptyList(),
) {

    val storeDirectory: Path = root.resolve("store")
    private val repository: Path = root.resolve("repo")
    private val projectDirectory: Path = root.resolve("project")

    fun store(): Codex = Codex.open(storeDirectory.resolve(CodexLocation.FILENAME))

    /**
     * Publishes a module into the local repository.
     *
     * [compile] dependencies appear on a consumer's compile classpath; [runtime] ones do not.
     * That distinction is the point of one of the tests: the compile classpath already excludes
     * other people's implementation details, and this proves it rather than assuming it.
     */
    fun publish(
        group: String,
        artifact: String,
        version: String,
        compile: List<String> = emptyList(),
        runtime: List<String> = emptyList(),
    ) {
        val dir = repository.resolve(group.replace('.', '/')).resolve(artifact).resolve(version)
        Files.createDirectories(dir)
        // A real, if empty, jar: javac reads every entry on the classpath and rejects a
        // zero-byte file as a corrupt archive.
        java.util.zip.ZipOutputStream(Files.newOutputStream(dir.resolve("$artifact-$version.jar"))).use {
            it.putNextEntry(java.util.zip.ZipEntry("META-INF/MANIFEST.MF"))
            it.write("Manifest-Version: 1.0\n".toByteArray())
            it.closeEntry()
        }
        fun deps(coordinates: List<String>, scope: String) = coordinates.joinToString("\n") {
            val (g, a, v) = it.split(':')
            "    <dependency><groupId>$g</groupId><artifactId>$a</artifactId>" +
                "<version>$v</version><scope>$scope</scope></dependency>"
        }
        Files.writeString(dir.resolve("$artifact-$version.pom"), """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>$group</groupId>
              <artifactId>$artifact</artifactId>
              <version>$version</version>
              <dependencies>
            ${deps(compile, "compile")}
            ${deps(runtime, "runtime")}
              </dependencies>
            </project>
        """.trimIndent())
    }

    /** Writes the consuming project's build. [body] goes inside the build script verbatim. */
    fun build(body: String) = buildWith("`java-library`\nid(\"org.dependencyskills.plugin\")", body)

    /** As [build], with the plugins block spelled out. */
    fun buildWith(plugins: String, body: String) {
        Files.createDirectories(projectDirectory)
        // Real source, because Gradle skips compilation when there is none - and a skipped
        // compileJava never resolves its compile classpath, so the plugin would see nothing and
        // the test would be measuring the wrong silence.
        source("src/main/java/com/example/consumer/App.java", "package com.example.consumer; public class App {}")
        source("src/test/java/com/example/consumer/AppTest.java", "package com.example.consumer; public class AppTest {}")
        Files.writeString(projectDirectory.resolve("settings.gradle.kts"), """
            rootProject.name = "consumer"
            dependencyResolutionManagement {
                repositories { maven { url = uri("${repository.toUri()}") } }
            }
        """.trimIndent())
        Files.writeString(projectDirectory.resolve("build.gradle.kts"), """
            plugins {
$plugins
            }

            $body
        """.trimIndent())
    }

    private fun source(path: String, body: String) {
        val file = projectDirectory.resolve(path)
        Files.createDirectories(file.parent)
        Files.writeString(file, body)
    }

    fun run(vararg arguments: String): BuildResult = runner(*arguments).build()

    private fun runner(vararg arguments: String): GradleRunner = GradleRunner.create()
        .withProjectDir(projectDirectory.toFile())
        // The plugin under test, plus anything a particular test needs injected rather than
        // resolved - KGP, for the multiplatform case.
        .withPluginClasspath(GradleRunner.create().withPluginClasspath().pluginClasspath + extraPluginClasspath)
        .withArguments(
            *arguments,
            // Everything the plugin needs is local. If any of this reached the network the
            // build would fail here rather than quietly succeeding on a warm cache.
            "--offline",
            "--stacktrace",
            "-PdependencySkills.codexDir=${storeDirectory.toAbsolutePath()}",
        )
        .forwardOutput()

    companion object {
        fun create(extraPluginClasspath: List<File> = emptyList()): TestProject {
            val root = Files.createTempDirectory("dependency-skills")
            root.toFile().deleteOnExit()
            return TestProject(root, extraPluginClasspath)
        }

        /** The Kotlin Gradle plugin, put on the test classpath by this module's build. */
        val kotlinGradlePlugin: List<File>
            get() = System.getProperty("kotlinPluginClasspath").orEmpty()
                .split(File.pathSeparator).filter { it.isNotBlank() }.map(::File)
    }
}

/** Coordinates in the store, as `group:artifact:version`, sorted. */
internal fun Codex.recordedCoordinates(): List<String> =
    org.dependencyskills.codex.core.HarvestState.entries
        .flatMap { coordinatesIn(it) }
        .map { it.coordinate.value }
        .sorted()

