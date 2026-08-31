package org.dependencyskills.plugin

import com.sun.net.httpserver.HttpServer
import org.gradle.testkit.runner.BuildResult
import org.gradle.testkit.runner.GradleRunner
import java.io.File
import java.net.InetSocketAddress
import java.nio.file.Files
import java.nio.file.Path

/**
 * A throwaway consuming project, its own store, and a Maven repository on disk.
 *
 * The repository is built here rather than resolved from Central so the tests prove the thing
 * the story asks for: everything the plugin needs is already local, and every run below passes
 * `--offline`.
 */
/** A loopback port nothing listens on, so "the service is down" is the default in tests. */
private const val UNREACHABLE = "http://127.0.0.1:1"

internal class TestProject(
    private val root: Path,
    private val extraPluginClasspath: List<File> = emptyList(),
) {

    val storeDirectory: Path = root.resolve("store")
    private val repository: Path = root.resolve("repo")
    private val projectDirectory: Path = root.resolve("project")

    /**
     * A stand-in for the codex service, recording what the plugin actually sent it.
     *
     * A stub rather than the real service because these tests are about the plugin: what it
     * reports, that it reports it once, and — most of it — that it does no harm when nobody is
     * listening. Running the real service here would test the service.
     */
    private var stub: HttpServer? = null
    private val registrations = mutableListOf<String>()
    private val warmings = mutableListOf<String>()

    init {
        // Running by default. "What did the plugin report" is what almost every test here asks,
        // and a test that wants the service absent is making a point that deserves to be visible
        // in the test body rather than assumed from its absence.
        startService()
    }

    /** Starts the stub and returns its URL. Started for every project; see [stopService]. */
    fun startService(status: Int = 200): String {
        stub?.let { return serviceUrl!! }
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/projects") { exchange ->
            // Exactly `/projects`, because `createContext` matches by PREFIX and the warm-up
            // signal is `/projects/syncing`. Without this the stub counts a sync as a
            // registration, which reads as the plugin reporting twice. Ktor routes the two
            // separately; only this double had to be told.
            val body = exchange.requestBody.readBytes().decodeToString()
            if (exchange.requestURI.path == "/projects") {
                synchronized(registrations) { registrations += body }
            } else {
                synchronized(warmings) { warmings += body }
            }
            exchange.sendResponseHeaders(status, -1)
            exchange.close()
        }
        server.start()
        stub = server
        serviceUrl = "http://127.0.0.1:${server.address.port}"
        return serviceUrl!!
    }

    /**
     * Replaces the stub with one that accepts a connection and never answers.
     *
     * Refusing a connection is the easy failure; hanging is the one that would sit in the middle of
     * somebody's build. This exists to prove the request timeout is real rather than nominal.
     */
    fun blackHoleService() {
        stopService()
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        // Long enough to outlast the request timeout, short enough that shutting the stub down
        // does not sit waiting on a sleeping handler.
        server.createContext("/projects") { Thread.sleep(8_000) }
        server.start()
        stub = server
        serviceUrl = "http://127.0.0.1:${server.address.port}"
    }

    fun stopService() {
        stub?.stop(0)
        stub = null
    }

    /** Every registration body the plugin posted, in order. */
    fun registrations(): List<String> = synchronized(registrations) { registrations.toList() }

    /** Every start-of-sync signal, which carries no coordinates and records nothing. */
    fun warmings(): List<String> = synchronized(warmings) { warmings.toList() }

    /**
     * The coordinates the last registration carried, as `group:artifact:version`, sorted.
     *
     * Read off the wire rather than out of a file or the store: what the plugin *sends* is now its
     * entire output, so it is the only thing worth asserting against.
     */
    fun recordedCoordinates(): List<String> =
        registrations().lastOrNull()
            ?.substringAfter(""""coordinates":[""")?.substringBefore(']')
            ?.split(',')
            ?.map { it.trim('"') }
            ?.filter { it.isNotBlank() }
            ?.map { it.substringAfter("maven:") }
            ?.sorted()
            ?: emptyList()

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

    /** Null once [stopService] has been called, which is a test making a point about absence. */
    private var serviceUrl: String? = null

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
            // Pointed at the stub when one is running, and at a port nothing answers on when not.
            // The second case is deliberate and is what most of these tests are about.
            "-PdependencySkills.serviceUrl=${serviceUrl ?: UNREACHABLE}",
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


