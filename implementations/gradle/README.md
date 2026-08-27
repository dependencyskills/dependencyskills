# gradle

The Gradle plugins. **Its own build root** — `./gradlew` here builds everything under it, independently of the sibling `codex/` build, which it consumes through `includeBuild("../codex")`.

Published under `org.dependencyskills.gradle`, so the coordinate says which build root a module came from: `org.dependencyskills.gradle:dependency-skills` against `org.dependencyskills.codex:core` for the store.

| module | plugin id | what it is |
|---|---|---|
| `dependency-skills` | `org.dependencyskills.plugin` | reports which of a project's dependencies the codex has never seen |

## Naming

The plugin id had to be a namespace we own, because **a Gradle plugin id is also a Maven groupId** — declaring `id("X")` publishes a marker artifact at `X:X.gradle.plugin`. Central verifies groupId ownership against a domain and the Plugin Portal has the same rule, so a bare `dependency-skills` could be published to neither. The id follows the `io.ktor.plugin` shape: the owned namespace plus `.plugin`.

The Kotlin package is `org.dependencyskills.plugin` — **the plugin id, not the group and module**. That is a deliberate exception to the rule the codex modules follow, and it is forced: the artifact name `dependency-skills` is hyphenated and cannot be a package segment. Matching the id is the next most useful thing for a reader holding a stack trace.

## `dependency-skills`

A consuming project applies it. On every build it watches the compile classpaths the build resolves anyway, diffs them against the store, and records what the store has never seen. It harvests nothing itself.

**The build detects; something out of band harvests.** An artifact transform looks like the natural fit and is a trap twice over: the summariser needs a local model, so a transform would block `./gradlew build` on inference, and its output would live in Gradle's transform cache, which Gradle owns and evicts.

**There is no download event, and none is wanted.** Gradle's public API offers resolution events, not download events. A download hook would be the wrong instrument anyway — it fires only for artifacts *this* build fetched, so everything already in the cache from another project would never be indexed. Diffing the resolved set against the store catches all three cases: newly downloaded, long cached but never indexed, and anything the store lost to a schema bump.

**It asks the build for the compile classpath and never models scope.** A compile classpath resolves with `Usage=java-api`, so what comes back is already the importable set — this project's `api`, `implementation` and `compileOnly`, plus only the transitives its dependencies chose to expose. Interpreting the configuration hierarchy by hand gets `compileOnlyApi`, feature variants and platform constraints wrong. KMP names the same thing per compilation, through `KotlinCompilation.compileDependencyConfigurationName`.

**Scope is never stored.** It belongs to the *(project, source set) → coordinate* edge, not to the coordinate: the same artifact is `api` in one project and `implementation` in another, and the store is machine-wide. Which coordinates a query may see is computed per project, at query time.

```kotlin
plugins { id("org.dependencyskills.plugin") }

dependencySkills {
    harvester {
        transitive = true              // off by default; see RAD-0022
        ignore("com.example:noisy")
    }
}
```

### Its one boundary

On a **configuration-cache hit** the plugin observes nothing, because the configuration phase is skipped and the resolution results come out of the cache rather than being computed. That is sound as far as it goes — a hit means nothing about configuration changed, and dependency declarations are configuration — and the gap is a version that resolves differently without any build file changing: a dynamic version, or a changing module. It is asserted by a test rather than left to be discovered.

## What used to be here

`publisher/` held the v1 plugin, which validated agent skills a library author wrote by hand. That model is gone: [ADR-0009](../../docs/knowledge/decisions/ADR-0009-transport-is-sources-jar.md) settles that content comes from the sources jar a library already publishes, so there is nothing bespoke left to author and nothing for that plugin to check. A publish-side check that a library's *own* documentation is worth harvesting is a different tool and a later question.
