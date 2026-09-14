plugins {
    kotlin("jvm") version "2.4.20"
}

repositories {
    mavenCentral()
    maven { url = uri("@REPO@") }
}

dependencies {
    implementation("com.example.acme:acme-result:0.1.0")
    testImplementation(kotlin("test"))
}

tasks.test { useJUnitPlatform() }
