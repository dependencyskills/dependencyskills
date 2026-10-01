// The plugin a consuming project applies. It resolves nothing itself: it watches the
// configurations the build resolves anyway, diffs them against the store, and records what
// the store has never seen.

import com.vanniktech.maven.publish.GradlePlugin
import com.vanniktech.maven.publish.JavadocJar
import com.vanniktech.maven.publish.SourcesJar

plugins {
    `kotlin-dsl`
    `java-gradle-plugin`
    // Publishes to Maven Central, signed, with the POM Central requires — and applies
    // `maven-publish`, which `java-gradle-plugin` configures but does not apply: without it there
    // are no publish tasks and `publishToMavenLocal` silently publishes nothing from this project.
    id("com.vanniktech.maven.publish") version "0.37.0"
}

kotlin { jvmToolchain(17) }

dependencies {
    // NOTHING from the codex. This plugin watches compile classpaths and writes a text file;
    // it used to open the store, which put 11.4 MB of SQLite on every consuming project's
    // buildscript classpath and made every Gradle daemon a writer to one database. What the
    // plugin and the codex share is the scope file's format, not a type.

    // Kotlin Multiplatform exposes each compilation's compile-dependency configuration through
    // KGP, and there is no other public way to ask for it. compileOnly because a consuming
    // project that is not multiplatform must not be made to carry KGP: every use of it is
    // behind `pluginManager.withPlugin`, so the classes are only touched when KGP is present.
    compileOnly("org.jetbrains.kotlin:kotlin-gradle-plugin:2.4.0")

    testImplementation(kotlin("test"))
    testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
}

// The agent skills the plugin writes into a project that asks for them (AgentSkills), carried in
// the jar from their one source in `implementations/agent-skills/`, so a plugin version and the
// skills it writes can never drift apart. With an index, since a jar cannot list a directory.
// `scripts/` never travels: a skill this plugin writes gives an agent nothing to run.
val bundledSkills by tasks.registering(Sync::class) {
    val skills = "org/dependencyskills/plugin/skills"
    from(layout.projectDirectory.dir("../../agent-skills")) {
        include("librarian/**", "librarian-skill-author/**")
        exclude("**/scripts/**")
        into(skills)
    }
    into(layout.buildDirectory.dir("bundled-skills"))
    val index = layout.buildDirectory.file("bundled-skills/$skills/index.txt")
    doLast {
        val root = index.get().asFile.parentFile
        val files = root.walkTopDown().filter { it.isFile && it.name != "index.txt" }
            .map { it.relativeTo(root).invariantSeparatorsPath }.sorted()
        index.get().asFile.writeText(files.joinToString("\n", postfix = "\n"))
    }
}

sourceSets.main { resources.srcDir(bundledSkills) }

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
            id = "org.dependencyskills"
            implementationClass = "org.dependencyskills.plugin.DependencySkillsPlugin"
            displayName = "Dependency Skills"
            description = "Reports which of a project's dependencies the machine-level codex has " +
                "never seen, and records them for harvesting out of band. Applied to a library, " +
                "ships the library's own skill in its sources jar. Writes the librarian and " +
                "librarian-skill-author agent skills into a project that declares their blocks."
        }
    }
}

// Maven Central, under the verified `org.dependencyskills` namespace: the plugin jar and its marker,
// `org.dependencyskills:org.dependencyskills.gradle.plugin`, which is what lets a build
// that lists `mavenCentral()` among its plugin repositories resolve the plugin by id. The credentials
// and signing key are the publisher's Gradle properties, never this file.
mavenPublishing {
    publishToMavenCentral()
    signAllPublications()
    configure(GradlePlugin(javadocJar = JavadocJar.Empty(), sourcesJar = SourcesJar.Sources()))
    pom {
        name = "Dependency Skills Gradle Plugin"
        description = "Ships a library's own agent skill in its sources jar, reports a consuming project's " +
            "dependencies for the lightweight codex, and writes the librarian and librarian-skill-author " +
            "agent skills into a project whose build asks for them."
        inceptionYear = "2026"
        url = "https://github.com/dependencyskills/dependencyskills"
        licenses {
            license {
                name = "Apache-2.0"
                url = "https://www.apache.org/licenses/LICENSE-2.0"
            }
        }
        developers {
            developer {
                id = "bpappin"
                name = "bpappin"
                url = "https://github.com/bpappin"
            }
        }
        scm {
            url = "https://github.com/dependencyskills/dependencyskills"
            connection = "scm:git:https://github.com/dependencyskills/dependencyskills.git"
            developerConnection = "scm:git:ssh://git@github.com/dependencyskills/dependencyskills.git"
        }
    }
}

// Only a publish to Maven Central is signed. A local publish — what a trial project resolves from
// `mavenLocal()` — needs no key, and must not fail on a machine whose signing setup is for another build.
val publishingToCentral = gradle.startParameter.taskNames.any { "MavenCentral" in it }
tasks.withType<Sign>().configureEach {
    onlyIf("only a Maven Central publish is signed") { publishingToCentral }
}
