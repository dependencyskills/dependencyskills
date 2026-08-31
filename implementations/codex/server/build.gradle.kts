// The door. An MCP server over the store, and the last code that runs before third-party content
// reaches a model.
//
// Everything upstream prepares; this decides what crosses. Only the rewrite and the signature do
// — the raw documentation is a retrieval key and is never returned, whatever is asked.

plugins {
    kotlin("jvm") version "2.4.0"
    // A build reports what it resolved as JSON, and `@Serializable` generates nothing without it.
    kotlin("plugin.serialization") version "2.4.0"
    application
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_22) } }
// A toolchain, not bare source/target compatibility. Those set what the compiler EMITS and say
// nothing about what runs the tests, so the test JVM was whatever daemon happened to be alive -
// and a daemon pinned to 21 cannot load the class files this emits. Matches `inference`.
java {
    toolchain { languageVersion = JavaLanguageVersion.of(26) }
    // The toolchain says which JDK runs the build and the tests; these say what it EMITS. Both
    // are needed: without the toolchain the tests ran on whatever daemon was alive, and without
    // these the Java and Kotlin tasks disagree about the target and the build refuses to compile.
    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
}

dependencies {
    api(project(":core"))
    // The index is optional at runtime: a store with no vectors still answers lexically, which is
    // what a machine that has harvested but not yet embedded has.
    implementation(project(":index"))
    // The pipeline. The server decides WHEN to index; the indexer knows how.
    implementation(project(":indexer"))
    implementation("io.modelcontextprotocol:kotlin-sdk:0.15.0")

    // The service. Netty and the plugins the host installs; the MCP SDK brings its own Ktor
    // pieces for the streamable-HTTP route.
    implementation("io.ktor:ktor-server-core:3.5.2")
    implementation("io.ktor:ktor-server-netty:3.5.2")
    implementation("io.ktor:ktor-server-compression:3.5.2")
    implementation("io.ktor:ktor-server-status-pages:3.5.2")

    // The container. The store and the index are opened once and injected; the queries over them
    // are built per request, because scope is not a property of the process.
    implementation("io.insert-koin:koin-ktor:4.2.2")
    implementation("io.insert-koin:koin-logger-slf4j:4.2.2")

    // Configuration, from a file beside the store. 2.9.0 rather than 3.0.0.RC3: a release
    // candidate is not a release, and this decides how much of a developer's machine gets used.
    implementation("com.sksamuel.hoplite:hoplite-core:2.9.0")
    implementation("com.sksamuel.hoplite:hoplite-toml:2.9.0")

    // slf4j-simple, not slf4j-nop. A service that logs nothing cannot be told from one that is
    // working, which is the failure this project keeps re-learning. It writes to stderr, so the
    // stdio transport's stdout stays pure protocol.
    implementation("org.slf4j:slf4j-simple:2.0.17")

    // The packaged encoder, at RUNTIME and not only in tests. `index` keeps it test-scope so a
    // library does not force 58 MB on whoever depends on it - that is the consumer's call. This
    // is the application making that call: the service is the thing that embeds, and without the
    // encoder on its own classpath it starts, accepts registrations, and silently indexes nothing.
    runtimeOnly(project(mapOf("path" to ":encoder", "configuration" to "encoderArtifact")))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

// The service is the entry point. The stdio transport is still built and still runnable - see
// the `stdio` task below - but it is no longer what `run` or the start scripts mean.
application { mainClass = "org.dependencyskills.codex.server.ApplicationKt" }

/** Runs the stdio transport by hand, for a client that can only launch a child process. */
tasks.register<JavaExec>("stdio") {
    group = "application"
    description = "Runs the MCP server over stdio instead of HTTP."
    mainClass = "org.dependencyskills.codex.server.MainKt"
    classpath = sourceSets["main"].runtimeClasspath
    standardInput = System.`in`
}

/** The directory name the shim is built into for this machine - the same names the jar uses. */
val hostPlatform: String = run {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val family = when {
        os.startsWith("mac") -> "macos"
        os.startsWith("win") -> "windows"
        else -> "linux"
    }
    val cpu = if (arch == "aarch64" || arch == "arm64") "aarch64" else "x86_64"
    "$family-$cpu"
}

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    jvmArgs("--enable-native-access=ALL-UNNAMED", "--add-modules", "jdk.incubator.vector")
    // The inference module keeps its natives out of the plain jvm jar, so a sibling's tests have
    // to be told where the working copy is - and the protocol tests hand this whole classpath to
    // a child process, which needs it too.
    val nativeDir = layout.projectDirectory
        .file("../inference/src/jvmMain/resources/dscodex/$hostPlatform").asFile
    jvmArgumentProviders.add(
        CommandLineArgumentProvider { listOf("-Ddscodex.native.dir=" + nativeDir.absolutePath) }
    )
}

// A one-liner so a person can start the server by hand and look at what it says.
tasks.register("printClasspath") { doLast { println(sourceSets["main"].runtimeClasspath.asPath) } }
