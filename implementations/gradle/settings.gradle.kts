rootProject.name = "gradle-plugins"

dependencyResolutionManagement {
    repositoriesMode = RepositoriesMode.FAIL_ON_PROJECT_REPOS
    repositories { mavenCentral() }
}

// The consumer plugin (#3) will need the codex store. When it does, add:
//   includeBuild("../codex")
// and depend on org.dependencyskills:core — Gradle substitutes the sibling project.
// Not declared yet, because nothing here uses it and an unused composite is a prediction.

include("publisher")
