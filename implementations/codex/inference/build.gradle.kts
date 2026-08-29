// In-process text generation, over llama.cpp.
//
// A Kotlin Multiplatform module because the natives are per-platform and the consumers need not
// all be the JVM: the store, the harvester and the MCP server are, a native CLI would not be, and
// both want the same library rather than two. Desktop only - nothing that loads a local model
// belongs in an Android or iOS artefact.
//
// The JVM target binds through FFM to `native/dscodex_llama.c`, a flat ABI of our own. Nothing
// but pointers, ints and bytes crosses the boundary, so llama.cpp's by-value parameter structs -
// which change between releases - are a compile-time concern in a hundred lines of C rather than
// a hand-maintained memory layout in Kotlin where a mismatch is silent corruption.

plugins {
    kotlin("multiplatform") version "2.4.0"
    `maven-publish`
}

kotlin {
    jvm {
        // FFM is final in 22. Everything here runs out of band - the harvest, the summariser, the
        // server - so it is not held to the Gradle plugin's floor.
        compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_22) }
    }

    // The native targets. Each links the shim's static archive INTO the binary through cinterop,
    // so a native consumer needs no extraction, no packaging and no library path at all - the
    // llama.cpp code is simply part of the executable.
    //
    // This is what makes a native CLI or a native server possible rather than theoretical, and it
    // is why the module is Kotlin Multiplatform instead of a plain JVM library with a jar full of
    // shared objects.
    val nativeTargets = listOf(
        macosArm64(), macosX64(), linuxX64(), linuxArm64(), mingwX64(),
    )

    nativeTargets.forEach { target ->
        val platform = when (target.name) {
            "macosArm64" -> "macos-aarch64"
            "macosX64" -> "macos-x86_64"
            "linuxX64" -> "linux-x86_64"
            "linuxArm64" -> "linux-aarch64"
            "mingwX64" -> "windows-x86_64"
            else -> error("no native build directory for ${target.name}")
        }
        // The static archive carries llama.cpp's ggml backends, which need the platform's own
        // maths and C++ libraries at final link. cinterop declares the archive; it cannot know
        // what that archive depends on.
        target.binaries.all {
            when (target.name) {
                "macosArm64", "macosX64" -> linkerOpts("-framework", "Accelerate", "-lc++")
                "linuxX64", "linuxArm64" -> linkerOpts("-lstdc++", "-lm")
                "mingwX64" -> linkerOpts("-lstdc++")
            }
        }

        target.compilations.getByName("main").cinterops.create("dscodex") {
            definitionFile = layout.projectDirectory.file("src/nativeMain/cinterop/dscodex.def")
            includeDirs(layout.projectDirectory.dir("src/nativeMain/cinterop"))
            extraOpts(
                "-libraryPath",
                layout.projectDirectory.dir("src/nativeMain/cinterop/libs/$platform").asFile.absolutePath,
            )
        }
    }

    sourceSets {
        commonTest.dependencies { implementation(kotlin("test")) }
    }

}

java { toolchain { languageVersion = JavaLanguageVersion.of(26) } }

// The natives are published as one artefact PER PLATFORM, not bundled together.
//
// A single fat jar is 29 MB and every consumer downloads all five to use one. Split, a consumer
// takes 4.6-7.0 MB. The classes jar carries none of them and stays tiny.
//
// Selection is by classifier rather than by Gradle attributes, deliberately. Attribute-based
// selection only fires when the CONSUMER's configuration carries `OperatingSystemFamily` and
// `MachineArchitecture`, which an ordinary JVM consumer's does not - so it resolves ambiguously
// and fails at resolution, or silently picks one and fails at dlopen. A classifier is explicit,
// and this project ships a Gradle plugin that can add the host's one without anyone typing it.
val nativePlatforms = listOf(
    "macos-aarch64", "macos-x86_64", "linux-aarch64", "linux-x86_64", "windows-x86_64",
)

tasks.named<ProcessResources>("jvmProcessResources") {
    // Under the source set so the location is conventional and the build script has one place to
    // write; excluded from the main jar so a consumer takes one platform rather than five.
    exclude("dscodex/**")
}

val nativeJars = nativePlatforms.map { platform ->
    tasks.register<Jar>("nativeJar${platform.split('-').joinToString("") { p -> p.replaceFirstChar(Char::uppercase) }}") {
        archiveClassifier = platform
        from(layout.projectDirectory.dir("src/jvmMain/resources/dscodex/$platform")) {
            into("dscodex/$platform")
            include("libdscodex.dylib", "libdscodex.so", "dscodex.dll")
        }
        // A native jar with nothing in it would resolve and then fail at load, which is worse
        // than not publishing it: the failure moves from build time to run time.
        onlyIf {
            layout.projectDirectory.dir("src/jvmMain/resources/dscodex/$platform").asFile
                .listFiles { f -> f.name.startsWith("libdscodex") || f.name == "dscodex.dll" }
                ?.isNotEmpty() == true
        }
    }
}

// Attached to the PUBLICATION, not to the legacy `archives` configuration. `maven-publish`
// ignores `archives`, so the jars were built, used by the tests through the classpath, and never
// published - a consumer would have resolved classes with no library behind them.
publishing.publications.withType<MavenPublication>().configureEach {
    if (name == "jvm") nativeJars.forEach { artifact(it) }
}

// Everything the tests need, so they run against the same layout a consumer gets.
val nativeForTests: FileCollection = files(nativeJars)

tasks.withType<Test>().configureEach {
    useJUnitPlatform()
    // The host's native jar on the test classpath - the same way a consumer gets exactly one.
    classpath += nativeForTests
    dependsOn(nativeJars)
    // FFM refuses to bind a native library without this, loudly, at first call.
    jvmArgs("--enable-native-access=ALL-UNNAMED")
    // Forwarded explicitly: a -D on the command line reaches the Gradle daemon, not this fork.
    providers.systemProperty("dscodex.test.model").orNull?.let { systemProperty("dscodex.test.model", it) }
}
