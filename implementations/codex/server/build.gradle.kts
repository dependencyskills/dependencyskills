// The door. An MCP server over the store, and the last code that runs before third-party content
// reaches a model.
//
// Everything upstream prepares; this decides what crosses. Only the rewrite and the signature do
// — the raw documentation is a retrieval key and is never returned, whatever is asked.

plugins {
    kotlin("jvm") version "2.4.0"
    application
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_22) } }
java { sourceCompatibility = JavaVersion.VERSION_22; targetCompatibility = JavaVersion.VERSION_22 }

dependencies {
    api(project(":core"))
    // The index is optional at runtime: a store with no vectors still answers lexically, which is
    // what a machine that has harvested but not yet embedded has.
    implementation(project(":index"))
    implementation("io.modelcontextprotocol:kotlin-sdk:0.15.0")
    implementation("org.slf4j:slf4j-nop:2.0.17")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

application { mainClass = "org.dependencyskills.codex.server.MainKt" }

tasks.withType<Test>().configureEach { useJUnitPlatform() }

// A one-liner so a person can start the server by hand and look at what it says.
tasks.register("printClasspath") { doLast { println(sourceSets["main"].runtimeClasspath.asPath) } }
