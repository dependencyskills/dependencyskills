# gradle

The Gradle plugins. **Its own build root** — `./gradlew` here builds everything under it, independently of the sibling `codex/` build, which it consumes through `includeBuild("../codex")`.

Published under `org.dependencyskills.gradle`, so the coordinate says which build root a module came from: `org.dependencyskills.gradle:dependency-skills` against `org.dependencyskills.codex:core` for the store.

| module | plugin id | what it is |
|---|---|---|
| `dependency-skills` | `org.dependencyskills.plugin` | reports which of a project's dependencies the codex has never seen; applied to a library, ships the library's own skill in its sources jar |

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

### The library half: shipping a skill *(alpha)*

Applied to a library, the same plugin ships the library's own skill. The author writes an Agent Skill directory named for the skill, so it is a valid skill where it is written:

```
src/main/skills/<name>/SKILL.md            a JVM library
src/commonMain/skills/<name>/SKILL.md      a Kotlin Multiplatform library
```

and every sources jar the build produces carries that directory at `skills/<name>/` — prefixed `commonMain/` in a multiplatform jar, as that jar prefixes everything. `references/` and `assets/` travel with it; `scripts/` never does.

**The build says what `<name>` is**, so nobody computes it: `./gradlew -q :<module>:dependencySkillName` prints the name and the path. A skill in a wrongly named directory, or the alpha's first flat `skills/SKILL.md`, still ships under the right name, and `checkDependencySkill` says where to move it.

**`<name>` is the library's coordinate, made a legal skill name.** The Agent Skills specification requires the name to match its directory and allows only lowercase letters, digits and single hyphens, up to 64 characters — no dots, colons, underscores or `--`. So `com.example.acme:acme-text` is filed as `com-example-acme-acme-text`, and a coordinate that would run past 64 characters — about 2% of real libraries — has its group shrunk to the first and last letter of each segment while the artifact stays whole: `cm-ge-ad-as-cn-tg-ay-fk-accessibility-test-framework`. If even that is too long it is cut and ends in eight hex digits of a SHA-256 of `group:artifact`; one library in 3,184 surveyed needed that. Chosen by measurement over initials, which merged sibling groups ([RAD-0075](../../docs/knowledge/research/RAD-0075-naming-the-skill-file.md)). The encoding is one-way, and nothing decodes it: the codex knows each jar's real coordinate, encodes it the same way, and compares. It is the coordinate rather than the artifactId so that the name is unique per library — two groups can each publish a `core` — and it is npm's `skills/<name>/SKILL.md` exactly.

**The author never types the coordinates.** They are read from the build's publication, so a library whose artifactId differs from its Gradle project name still files the skill under the name a consumer resolves. That matters because the codex takes a skill only from the artifact whose coordinates it is filed under, and would refuse a mismatch as republishing ([RAD-0076](../../docs/knowledge/research/RAD-0076-skills-republished-by-a-third-party.md)).

**A `scripts/` directory is never shipped**, and the build says so. A library's skill tells an agent how to use the library; it never hands the agent something to run.

**`checkDependencySkill` warns, for now, rather than fails**: a directory not named for the skill, missing frontmatter, a `name` that is not the coordinate's skill name, no `description`, `allowed-tools`, or a `metadata.version` that is absent or is not the version being built.

A project with no skill file is untouched, which is every purely consuming one. Where the file lives and what it is called is still open — [RAD-0075](../../docs/knowledge/research/RAD-0075-naming-the-skill-file.md) — and this is the alpha of one answer, built to be tried.

**Sources jars are matched as `Zip`, not `Jar`.** The java plugin's is a `bundling.Jar`; Kotlin Multiplatform's are `org.gradle.jvm.tasks.Jar`, which in Gradle 9 extends `Zip` directly. Matching on `Jar` shipped the skill for JVM libraries and silently skipped every multiplatform one.

### Two codexes, two handoffs

Every build reports its resolved set both ways, because the plugin cannot know which codex a developer runs:

- **To the full codex, over HTTP** — `POST /projects` to `serviceUrl`, as above.
- **To the lightweight codex, as a file** — a CycloneDX 1.6 SBOM at `build/dependencyskills/bom.cdx.json` in the root build directory, listing the same set: the compile classpath, declared dependencies unless `transitive` is on, plus the libraries a version catalog declares that no module uses yet, marked as declared. Each entry names the module that resolved it, so building one module replaces only that module's entries. It is rewritten only when it changes, and a rewrite that added dependencies names them in the build output — `dependencyskills: new since the last build: …` — at quiet level, so an agent running `-q` still sees it. The lightweight codex ([`experiments/minimal-codex`](../../experiments/minimal-codex/), `pkgindex.py mcp`) is an MCP server over stdio that the agent's harness starts inside the project; it reads the file when an agent asks, and re-indexes when the file has changed. No process runs between sessions, and nothing watches anything.

**The build also fetches the sources jars**, because that is where a JVM library's skill travels and a build otherwise never downloads one — on a machine that only builds from the command line, the lookup would find nothing ([RAD-0079](../../docs/knowledge/research/RAD-0079-what-each-ecosystem-needs-from-the-lightweight-codex.md)). A `dependencySkillsSources<Classpath>` task runs before each compile task, asks Gradle for the sources variant of exactly what that classpath resolved and of what the catalog declares, and skips any library that has none. It goes through the project's own repositories and cache. `-PdependencySkills.fetchSources=false` turns it off.

The file is also what keeps scope out of the agent's hands. Scope is what the build resolved, and it is written only by the build; were it set through the MCP interface the agent queries, the agent — or an instruction injected into it — could widen its own.

An SBOM is the format because it is an existing convention for exactly this — the resolved dependency graph, written into the build directory — rather than one this project invented ([ADR-0007](../../docs/knowledge/decisions/ADR-0007-conform-to-existing-conventions.md)). It is not yet read from other SBOM plugins: those usually describe the runtime classpath, which is a wider scope than what a project can import, and are refreshed only when their own task runs.

## What used to be here

`publisher/` held the v1 plugin, which validated agent skills a library author wrote by hand into `META-INF/ai-skills/`. That placement failed — it never reached a sources jar ([RAD-0065](../../docs/knowledge/research/RAD-0065-what-v1-skill-authors-wrote-unprompted.md)) — and [ADR-0009](../../docs/knowledge/decisions/ADR-0009-transport-is-sources-jar.md) settled that content comes from the sources jar a library already publishes. The library half above is not that plugin back: it ships into the sources jar, from the source tree, under the library's own coordinates.
