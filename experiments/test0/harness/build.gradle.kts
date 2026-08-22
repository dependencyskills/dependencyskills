plugins {
    kotlin("jvm") version "2.1.0"
}

repositories {
    mavenCentral()
}

kotlin {
    // JDK 21 is enough for the PSI (kotlin-compiler-embeddable) and Dokka arms.
    // Bump to 22+ when the tree-sitter (jtreesitter / FFM) arm is added.
    jvmToolchain(21)
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-compiler-embeddable:2.1.0")

    testImplementation(platform("org.junit:junit-bom:5.11.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Where the test0 fixture lives, relative to this module (../fixtures).
    systemProperty("test0.dir", file("../fixtures").absolutePath)
    testLogging {
        events("passed", "failed", "skipped")
    }
}
