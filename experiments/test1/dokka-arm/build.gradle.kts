// test1 Phase A — Dokka (enriched) arm. Documents an extracted -sources.jar so we can
// measure the realized enrichment delta (inherited docs, resolved @sample) over the
// tree-sitter raw arm. Run: JAVA_HOME=<jdk21> ./gradlew dokkaGfm
plugins {
    kotlin("jvm") version "2.1.0"
    id("org.jetbrains.dokka") version "1.9.20"
}
repositories { mavenCentral() }
dependencies {
    // the library's compile classpath, so Dokka resolves types (and same-run supertypes)
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-core:1.9.0")
    implementation("it.krzeminski:snakeyaml-engine-kmp:4.0.1")
    implementation("com.squareup.okio:okio:3.16.4")
}
kotlin { jvmToolchain(21) }
