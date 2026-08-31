// The thing that actually runs the pipeline.
//
// Every stage was a library nobody called: the harvester, the classifier, the summariser and the
// index all existed and were tested, and a coordinate recorded by a build stayed `Pending` for
// ever. This module is the caller.
//
// Its own module rather than part of `server`, because the server is the door — the last code
// before third-party content reaches a model — and this is the kitchen. Keeping them apart also
// keeps the generative model out of the module that answers queries.

plugins {
    kotlin("jvm") version "2.4.0"
    `java-library`
}

kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_22) } }
java {
    toolchain { languageVersion = JavaLanguageVersion.of(26) }
    sourceCompatibility = JavaVersion.VERSION_22
    targetCompatibility = JavaVersion.VERSION_22
}

dependencies {
    api(project(":core"))
    // The whole pipeline, in the order it runs. This is the one module that sees all of it.
    implementation(project(":harvester"))
    implementation(project(":classifier"))
    implementation(project(":summariser"))
    api(project(":index"))
    // Logging only; the pipeline says what it is doing and a silent indexer cannot be told from a
    // broken one. slf4j-api alone - the binding is the application's choice, not a library's.
    implementation("org.slf4j:slf4j-api:2.0.17")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.withType<Test>().configureEach { useJUnitPlatform() }
