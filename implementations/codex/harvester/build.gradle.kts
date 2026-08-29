// Sources jar in, entries out. Depends on `core` for the store's types and on nothing
// Gradle-specific: a Maven plugin, a CLI or the MCP server harvests with the same code.

plugins {
    kotlin("jvm") version "2.4.0"
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core"))

    // Tree-sitter over JNI, with the native library for every platform inside the jar.
    // Settled by RAD-0009 v6 and not reopened here; how the native library ships inside a
    // Gradle plugin is a packaging question and out of scope for this module.
    implementation("io.github.bonede:tree-sitter:0.26.6")
    implementation("io.github.bonede:tree-sitter-java:0.23.5")
    implementation("io.github.bonede:tree-sitter-kotlin:0.3.8.1")

    // Test-scope only, and one-directional: the classifier depends on `core`, never on this.
    // The operating-characteristics measurement needs a real harvested corpus and the shipped
    // classifier at once, and this is the module that can see both.
    testImplementation(project(":classifier"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

// Real published artifacts, resolved into the ordinary Gradle cache and read from there.
//
// The alternative — a hand-built jar in `src/test/resources` — would test the harvester
// against source this repository wrote, which is the one input guaranteed not to surprise it.
// These are pinned by version, so the counts asserted against them are stable.
val fixtures: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

dependencies {
    // Java, package-rooted, javadoc throughout.
    fixtures("org.slf4j:slf4j-api:2.0.17:sources@jar")
    // Kotlin, source-set-rooted (commonMain + jvmMain) — a multiplatform publication.
    fixtures("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0:sources@jar")
    // The same library's CLASSES jar: a real archive with no source in it at all.
    fixtures("org.slf4j:slf4j-api:2.0.17@jar")
}

tasks.test {
    useJUnitPlatform()
    val fixtureFiles = fixtures.incoming.files
    inputs.files(fixtureFiles).withPropertyName("fixtures")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-Dcodex.harvester.fixtures=" + fixtureFiles.joinToString(File.pathSeparator))
        }
    )
}

// ---------------------------------------------------------------------------------------------
// The retrieval baseline corpus.
//
// These 59 coordinates are one real Ktor server project's resolved dependencies, pinned exactly
// as `experiments/test5/CORPUS-MANIFEST.md` records them - 14,899 documented declarations across
// 59 libraries. They are pinned rather than re-resolved so the recall numbers this produces are
// comparable with the ones already measured against the same corpus with embeddings.
//
// A published Maven version is immutable, so re-fetching these reproduces the same harvest.
val corpus: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
}

// Read from the manifest rather than listed here, so the harvester and the vector index cannot
// drift onto different corpora. #6 has to beat this baseline on the same 59 libraries; two
// hand-maintained copies of that list would make the comparison quietly stop being one.
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

tasks.test {
    // Lenient: not every one of these publishes a sources jar, and a coordinate that does not is
    // a finding the harvest reports rather than a resolution failure.
    val corpusFiles = corpus.incoming.artifactView { isLenient = true }.files
    inputs.files(corpusFiles).withPropertyName("corpus")
    val needs = layout.projectDirectory.file("../../../experiments/test5/queries.json").asFile
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf(
                "-Dcodex.corpus=" + corpusFiles.joinToString(File.pathSeparator),
                "-Dcodex.needs=" + needs.absolutePath,
                "-Dcodex.reports=" + layout.buildDirectory.dir("reports").get().asFile.absolutePath,
            )
        }
    )
}
