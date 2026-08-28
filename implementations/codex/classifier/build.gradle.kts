// The prose classifier. A term-frequency table and a dot product — no learning code, no
// network, no model download. The weights are fitted offline by `tools/train.py` and committed.

plugins {
    kotlin("jvm") version "2.4.0"
    `java-library`
}

kotlin { jvmToolchain(17) }

dependencies {
    api(project(":core"))
    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

tasks.test { useJUnitPlatform() }
