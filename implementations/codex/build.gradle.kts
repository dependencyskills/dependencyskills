// Aggregator only. Nothing is published from here; each module carries its own build.
//
// This is the codex itself - the store, and later the harvester, the query layer and the
// MCP server. It is deliberately NOT a Gradle build-system implementation: nothing here
// depends on Gradle, so a Maven plugin, a CLI or the server can use it. The Gradle plugins
// live in the sibling `gradle/` build and depend on this one.

plugins { base }

allprojects {
    group = "org.dependencyskills.codex"
    version = providers.gradleProperty("version").getOrElse("0.1.0")
}
