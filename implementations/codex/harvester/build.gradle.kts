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
