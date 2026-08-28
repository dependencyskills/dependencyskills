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

dependencies {
    corpus("org.jetbrains.kotlin:kotlin-stdlib:2.4.10:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:1.11.0:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.11.0:sources@jar")
    corpus("io.ktor:ktor-server-core-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-client-core-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-client-core:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-core:3.5.2:sources@jar")
    corpus("io.ktor:ktor-http-jvm:3.5.2:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.11.0:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-serialization-core:1.11.0:sources@jar")
    corpus("io.ktor:ktor-http:3.5.2:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-html-jvm:0.12.0:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-html:0.12.0:sources@jar")
    corpus("io.ktor:ktor-utils-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-utils:3.5.2:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-io-core-jvm:0.9.1:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-io-core:0.9.1:sources@jar")
    corpus("org.jetbrains.kotlin:kotlin-reflect:2.3.21:sources@jar")
    corpus("io.ktor:ktor-io-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-io:3.5.2:sources@jar")
    corpus("io.ktor:ktor-network-jvm:3.5.2:sources@jar")
    corpus("org.jetbrains.kotlin:kotlin-test:2.4.10:sources@jar")
    corpus("io.ktor:ktor-websockets-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-websockets:3.5.2:sources@jar")
    corpus("io.ktor:ktor-network:3.5.2:sources@jar")
    corpus("io.ktor:ktor-http-cio:3.5.2:sources@jar")
    corpus("io.ktor:ktor-http-cio-jvm:3.5.2:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-io-bytestring-jvm:0.9.1:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-coroutines-test-jvm:1.11.0:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-io-bytestring:0.9.1:sources@jar")
    corpus("io.ktor:ktor-network-tls-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-test-host-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-netty-jvm:3.5.2:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-coroutines-debug:1.11.0:sources@jar")
    corpus("io.ktor:ktor-network-tls:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-websockets-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-websockets:3.5.2:sources@jar")
    corpus("io.ktor:ktor-client-cio-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-network-tls-certificates-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-serialization-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-serialization:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-html-builder-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-html-builder:3.5.2:sources@jar")
    corpus("io.ktor:ktor-client-cio:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-call-logging-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-client-apache5-jvm:3.5.2:sources@jar")
    corpus("org.jetbrains.kotlin:kotlin-test-junit:2.4.10:sources@jar")
    corpus("io.ktor:ktor-events-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-events:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-default-headers-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-server-default-headers:3.5.2:sources@jar")
    corpus("io.ktor:ktor-sse-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-sse:3.5.2:sources@jar")
    corpus("io.ktor:ktor-websocket-serialization-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-websocket-serialization:3.5.2:sources@jar")
    corpus("org.jetbrains.kotlinx:kotlinx-coroutines-slf4j:1.11.0:sources@jar")
    corpus("io.ktor:ktor-test-dispatcher-jvm:3.5.2:sources@jar")
    corpus("io.ktor:ktor-test-dispatcher:3.5.2:sources@jar")
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
