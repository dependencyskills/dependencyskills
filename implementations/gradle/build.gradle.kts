plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

group = "org.dependencyskills"
version = "0.1.0"

repositories { mavenCentral() }

// Target JDK 17: the plugin should run for consumers still on 17, which is
// most of them. This governs what the plugin compiles against and targets,
// not the JVM Gradle itself launches on.
kotlin {
    jvmToolchain(17)
}

@Suppress("UnstableApiUsage")
testing.suites {
    getByName<JvmTestSuite>("test") { useJUnitJupiter() }
}

gradlePlugin {
    plugins {
        create("validate") {
            id = "org.dependencyskills.validate"
            implementationClass = "org.dependencyskills.gradle.AgentSkillsPlugin"
            displayName = "Agent Skills (validation)"
            description = "Validates a library's authored agent skills against the adopted format and " +
                "filesystem-naming rules. A placeholder skeleton — see docs/knowledge/research/."
        }
    }
}
