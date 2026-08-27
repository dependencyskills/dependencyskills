// The codex store. Deliberately not Gradle-specific: it is a plain JVM library so the
// MCP server, a Maven plugin or a CLI can use it without dragging Gradle in.

plugins {
    kotlin("jvm") version "2.4.0"
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api("org.xerial:sqlite-jdbc:3.53.4.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test { useJUnitPlatform() }
