rootProject.name = "gradle-plugins"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

// The store. A sibling build root rather than a module here, because nothing in the codex
// depends on a build system and filing it under `gradle/` would make it a Gradle artifact by
// proximity — ADR-0005 v3. Gradle substitutes `org.dependencyskills.codex:core` from it.
includeBuild("../codex")

include("dependency-skills")
