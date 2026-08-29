// The quarantine: library prose in, one factual sentence out, and the original never leaves.
//
// A separate module from `core` on purpose. This is the only part of the system that loads a
// generative model, and keeping it out of the store means the Gradle plugin - which depends on
// `core` and nothing else - cannot reach it even by accident. That is the structural half of
// "never runs during a build"; the plugin's own test asserts the other half.

plugins {
    kotlin("jvm") version "2.4.0"
    `java-library`
}

// FFM through `inference` needs 22. Like the harvest and the index, this runs out of band and is
// not held to the Gradle plugin's floor. A target rather than a toolchain, matching `inference`.
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_22) } }
java { sourceCompatibility = JavaVersion.VERSION_22; targetCompatibility = JavaVersion.VERSION_22 }

dependencies {
    api(project(":core"))
    api(project(":inference"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
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
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    val nativeDir = layout.projectDirectory
        .file("../inference/src/jvmMain/resources/dscodex/$hostPlatform").asFile
    jvmArgumentProviders.add(
        CommandLineArgumentProvider { listOf("-Ddscodex.native.dir=" + nativeDir.absolutePath) }
    )
    // Forwarded explicitly: a -D on the command line reaches the Gradle daemon, not this fork.
    listOf("codex.summariser.model", "codex.refusals", "codex.reports").forEach { name ->
        providers.systemProperty(name).orNull?.let { systemProperty(name, it) }
    }
}

// The verifier's self-test and every shape rule run here: no model, seconds. What needs a model
// is the round trip, and that lives on its own task for the same reason the index's does.
tasks.test {
    filter {
        excludeTestsMatching("*RoundTripTest")
        excludeTestsMatching("*RefusalAnalysisTest")
    }
}

val roundTrip by tasks.registering(Test::class) {
    description = "One real doc comment through a real model and back. Needs codex.summariser.model."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*RoundTripTest") }
    outputs.upToDateWhen { false }
}

// Re-scoring refused candidates against the rules, with no model involved. Its own task because it
// needs a `refusals.tsv` from a real pass - which `:index:endToEnd` writes.
//
//   ./gradlew :summariser:refusals -Dcodex.refusals=.../refusals.tsv
val refusals by tasks.registering(Test::class) {
    description = "Re-judge captured refusals against the shipped rules. Needs codex.refusals."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*RefusalAnalysisTest") }
    outputs.upToDateWhen { false }
}
