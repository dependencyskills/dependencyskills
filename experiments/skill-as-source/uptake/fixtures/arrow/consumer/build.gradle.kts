plugins {
    kotlin("jvm") version "2.4.20"
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("io.arrow-kt:arrow-core:2.2.3")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
