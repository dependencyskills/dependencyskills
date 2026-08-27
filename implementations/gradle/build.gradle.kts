// The Gradle plugins. A separate build from `codex/` because they are a build-system
// implementation and the codex is not — ADR-0005's seam, made real.

plugins { base }

allprojects {
    group = "org.dependencyskills.gradle"
    version = providers.gradleProperty("version").getOrElse("0.1.0")
}
