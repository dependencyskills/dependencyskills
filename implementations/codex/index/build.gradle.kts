import java.time.Duration

// The two-faced vector index: a DERIVED index, never the source of truth.
//
// The store (`core`) holds the entries; this holds vectors computed from them and can be thrown
// away and rebuilt at any time. That is what makes an encoder change survivable — RAD-0010 calls
// Lucene the query cache and the text the truth, and ADR-0012 replaced the text with SQLite
// without changing which one is derived.
//
// JVM only. Lucene is a JVM library, so unlike `inference` there is nothing here a native target
// could use even if one wanted to.

plugins {
    kotlin("jvm") version "2.4.0"
    `java-library`
}

// Lucene 10 needs 21; FFM through `inference` needs 22. Like the summariser and the harvest, this
// runs out of band and is not held to the Gradle plugin's floor.
//
// A target rather than a toolchain, matching `inference`: pinning a toolchain here would demand a
// JDK 22 on every machine that builds the repo, when what is actually required is that the
// bytecode not claim to run on less.
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
    api(project(":inference"))
    api("org.apache.lucene:lucene-core:10.5.1")
    // Test-scope only, and one-directional: the harvester never depends on this. The comparison
    // against #4's lexical baseline needs a real harvested corpus and the vector index at once.
    testImplementation(project(":harvester"))
    // Also test-scope only, and also one-directional. The end-to-end measurement needs the whole
    // pipeline in one process - harvest, classify, summarise, index, query - and this is the
    // module that already sees most of it.
    testImplementation(project(":classifier"))
    testImplementation(project(":summariser"))
    // A real JSON parser, test-scope only. The rewrites fixture has escaped quotes and braces
    // inside string values, which is exactly where a hand-rolled scan silently returns nothing -
    // and "no rewrites" would have read as "the second face does not help".
    testImplementation("com.google.code.gson:gson:2.11.0")
    // The packaged model, on the test classpath only. A consumer chooses whether to carry 58 MB;
    // the library must not decide that for them by depending on it.
    testRuntimeOnly(project(mapOf("path" to ":encoder", "configuration" to "encoderArtifact")))
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

// ---------------------------------------------------------------------------------------------
// The same corpus the lexical baseline uses, read from the same manifest.
//
// #6 has to beat #4's baseline on the same 59 libraries and the same 17 needs. Listing the
// coordinates here as well would let the two measurements drift onto different corpora while
// still both reporting a recall number, which is the failure mode of every comparison that is
// not actually a comparison.
val corpus: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

val corpusCoordinates: List<String> =
    layout.projectDirectory.file("../../../experiments/test5/CORPUS-MANIFEST.md").asFile
        .readLines()
        .mapNotNull { Regex("""^\|\s*\d+\s*\|\s*`([^`]+)`""").find(it)?.groupValues?.get(1) }

dependencies {
    check(corpusCoordinates.size == 59) {
        "the manifest should name 59 coordinates; found ${corpusCoordinates.size}"
    }
    corpusCoordinates.forEach { corpus("$it:sources@jar") }
}

// Shared by both test tasks below, so the measurement cannot drift onto a different corpus or a
// different native from the suite that guards its shape.
tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // Lucene warns at startup without it, and RAD-0047 measured it worth ~2x on a filtered kNN
    // at a realistic filter size (2,962 us -> 1,603 us across 500 coordinates).
    jvmArgs("--add-modules", "jdk.incubator.vector", "--enable-native-access=ALL-UNNAMED")
    // Forwarded explicitly: a -D on the command line reaches the Gradle daemon, not this fork.
    listOf("codex.encoder.model", "codex.summariser.model").forEach { name ->
        providers.systemProperty(name).orNull?.let { systemProperty(name, it) }
    }

    // Lenient for the same reason the harvester is: a coordinate with no sources jar is a finding
    // the harvest reports, not a resolution failure.
    val corpusFiles = corpus.incoming.artifactView { isLenient = true }.files
    inputs.files(corpusFiles).withPropertyName("corpus")
    val experiments = layout.projectDirectory.file("../../../experiments").asFile
    // The inference module keeps its natives out of the plain jvm jar and attaches them as
    // per-platform classifier jars at publication, so a sibling module's tests have to be told
    // where the working copy is. This is what `dscodex.native.dir` exists for.
    val nativeDir = layout.projectDirectory
        .file("../inference/src/jvmMain/resources/dscodex/$hostPlatform").asFile
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dcodex.corpus=" + corpusFiles.joinToString(File.pathSeparator),
                "-Dcodex.needs=" + File(experiments, "test5/queries.json").absolutePath,
                "-Dcodex.rewrites=" + File(experiments, "summariser/summaries.json").absolutePath,
                "-Dcodex.reports=" + layout.buildDirectory.dir("reports").get().asFile.absolutePath,
                "-Ddscodex.native.dir=" + nativeDir.absolutePath,
            )
        }
    )
}

// The structural suite: synthetic vectors, no model, seconds. This is what `./gradlew test` runs.
tasks.test {
    filter {
        excludeTestsMatching("*TwoFacedRetrievalTest")
        excludeTestsMatching("*SummarisedRetrievalTest")
        excludeTestsMatching("*WholeCommentClassificationTest")
    }
}

// ---------------------------------------------------------------------------------------------
// The retrieval measurement, on its own task because it needs a 67 MB model this repository does
// not carry and takes about two minutes.
//
// NOT a skip. Run without `codex.encoder.model` it fails and says what is missing - the same
// stance the harvester's fixtures take, for the same reason. What it is not is part of `test`,
// because a default build that cannot pass without a model file nobody has is a build people
// learn to ignore.
//
//   ./gradlew :index:retrieval -Dcodex.encoder.model=/path/to/bge-small-en-v1.5-f16.gguf
val retrieval by tasks.registering(Test::class) {
    description = "Measures the two-faced index against #4's lexical baseline. Needs an encoder."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*TwoFacedRetrievalTest") }
    outputs.upToDateWhen { false }
}

// The end-to-end measurement: harvest, classify, summarise, index, query, in one pass over a real
// dependency graph. Half an hour of compute, two models, and the number the whole design rests on.
//
//   ./gradlew :index:endToEnd \
//     -Dcodex.encoder.model=/path/to/bge-small-en-v1.5-f16.gguf \
//     -Dcodex.summariser.model=/path/to/gemma-3-270m-it-qat-Q4_0.gguf
val endToEnd by tasks.registering(Test::class) {
    description = "The whole pipeline over one project's dependencies. Needs both models."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*SummarisedRetrievalTest") }
    timeout = Duration.ofHours(2)
    outputs.upToDateWhen { false }
}

// Re-asks a settled question against the shipped classifier: whole comment or per sentence?
// Needs the store :index:endToEnd leaves behind, and no model at all.
val wholeComment by tasks.registering(Test::class) {
    description = "Per-sentence against whole-comment classification, with planted payloads."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    filter { includeTestsMatching("*WholeCommentClassificationTest") }
    outputs.upToDateWhen { false }
}
