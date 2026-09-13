// RAD-0073 test 2, Kotlin Multiplatform: does one documentation-only file in
// commonMain reach every target's published output, and does any target warn
// about it or compile something from it?
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("com.android.kotlin.multiplatform.library") version "9.3.2"
    `maven-publish`
}

group = "com.example.acme"
version = "0.1.0"

kotlin {
    jvm()
    android {
        namespace = "com.example.acme.text"
        compileSdk = 37
        minSdk = 24
    }
    js { nodejs() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { nodejs() }
    iosArm64()
    iosSimulatorArm64()
    macosArm64()
    linuxX64()

    compilerOptions {
        allWarningsAsErrors.set(true)
        extraWarnings.set(true)
    }
}

publishing {
    repositories {
        maven {
            name = "local"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}
