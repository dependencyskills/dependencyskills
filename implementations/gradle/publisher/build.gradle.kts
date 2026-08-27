// The Maven-channel publisher. Predates the codex work and is unrelated to it:
// it validates a library's authored agent skills at publish time.

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

kotlin { jvmToolchain(17) }

@Suppress("UnstableApiUsage")
testing.suites { getByName<JvmTestSuite>("test") { useJUnitJupiter() } }

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
