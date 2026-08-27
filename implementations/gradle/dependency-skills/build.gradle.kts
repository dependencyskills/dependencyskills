// The plugin a consuming project applies. It resolves nothing itself: it watches the
// configurations the build resolves anyway, diffs them against the store, and records what
// the store has never seen.

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
}

kotlin { jvmToolchain(17) }

dependencies {
    // The store, substituted from the sibling `codex` build root by the composite in settings.
    implementation("org.dependencyskills.codex:core")

    // Kotlin Multiplatform exposes each compilation's compile-dependency configuration through
    // KGP, and there is no other public way to ask for it. compileOnly because a consuming
    // project that is not multiplatform must not be made to carry KGP: every use of it is
    // behind `pluginManager.withPlugin`, so the classes are only touched when KGP is present.
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

// KGP on the TestKit plugin classpath, so a multiplatform test project can apply it without
// resolving anything: the classes are injected rather than fetched.
val kotlinPluginClasspath: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
}

dependencies { kotlinPluginClasspath("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0") }

tasks.test {
    val injected = kotlinPluginClasspath.incoming.files
    inputs.files(injected).withPropertyName("kotlinPluginClasspath")
    jvmArgumentProviders.add(
        CommandLineArgumentProvider {
            listOf("-DkotlinPluginClasspath=" + injected.joinToString(File.pathSeparator))
        }
    )
}

@Suppress("UnstableApiUsage")
testing.suites { getByName<JvmTestSuite>("test") { useJUnitJupiter() } }

gradlePlugin {
    plugins {
        create("dependencySkills") {
            id = "org.dependencyskills.plugin"
            implementationClass = "org.dependencyskills.plugin.DependencySkillsPlugin"
            displayName = "Dependency Skills"
            description = "Reports which of a project's dependencies the machine-level codex has " +
                "never seen, and records them for harvesting out of band."
        }
    }
}
