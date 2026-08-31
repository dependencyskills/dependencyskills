# What Fetches a Sources Jar

RAD-0061 · 2026-08-30
Keywords: what downloads the sources jar; fetch index and discard; do we need sources on disk; is there a Gradle config for downloading sources; init script in ~/.gradle/init.d; ArtifactResolutionQuery and SourcesArtifact; should we bundle Gradle in the service; Gradle Tooling API for dependency resolution; Maven Resolver versus Gradle; can we just GET it from Maven Central; how do sources get on disk during a sync; can a Gradle plugin turn on source downloading; downloadSources; hosted machine with no project; private repositories force a real resolver.
Measured against: Gradle 9.5.0 (and 8.14.3 / 9.4.1 distributions on disk), one real `~/.gradle/caches/modules-2/files-2.1`, macOS arm64, Maven Central over HTTPS, 2026-08-30. Everything else here is argument or read from documentation, and is marked as such.

## Question

> **Nothing in the codex resolves a `-sources.jar`. On a machine with no populated cache, what should?**

`SourcesJarHarvester` takes a *file*. [#26](https://github.com/dependencyskills/dependencyskills/issues/26)'s driver is "the caller that resolves coordinates to files", so how that file is obtained is unowned and undecided.

The proposal that prompted this: **bundle Gradle in the service and ask it to fetch**, at least on hosted machines where there is no project. Gradle already knows how to resolve a coordinate, and reimplementing that is how tools acquire a second, worse resolver.

A second question arrived with it, and shares the same subject: **can the Gradle plugin make sources appear during a sync**, so that nothing needs fetching later?

## Trail

### On a developer's machine, most of this is already done

Measured on one real cache: **3,829 of 6,892 jars have a `-sources.jar` beside them — 56%**. Gradle's `idea` plugin exposes [`downloadSources`, defaulting to `true`](https://docs.gradle.org/current/dsl/org.gradle.plugins.ide.idea.model.IdeaModule.html), and IntelliJ exposes the same under Settings | Build, Execution, Deployment | Build Tools | Gradle. Sources arrive without anyone asking.

So for a developer the fetching question is a minority case, and the first move is not to fetch at all: look in the ecosystem's own cache, at the layout it owns. That is where the majority already sits, and it costs nothing.

**The hosted case is the real question.** A machine dedicated to indexing for a team has no projects, no IDE and no populated cache, so every coordinate needs fetching and none of the above helps.

### Bundling Gradle

Read from documentation: the Tooling API [always uses the Gradle daemon](https://docs.gradle.org/current/userguide/tooling_api.html) — "subsequent calls will be executed in the same long-living process" — can download and install a distribution itself in the manner of the wrapper, is compiled for Java 8 compatibility, and supports running builds with the last five major Gradle versions.

Measured: the three Gradle distributions on this machine are **146 MB, 148 MB and 151 MB**. The Tooling API client does not have to ship one, but something has to be on disk before a build runs.

What it buys is the part that is easy to underestimate: **the same resolver the developer's build used**. Repository declarations, mirrors, credentials, exclusions, version alignment, snapshot metadata, and whatever a `settings.gradle.kts` does to resolution. A hand-rolled fetcher agrees with Gradle until it does not, and the disagreement shows up as a library that is silently absent from the index.

What it costs is a daemon. The service already holds a store, and would then hold a second long-living JVM whose memory is not ours to bound, on a machine whose budget we have just made configurable.

### The two things it would be competing with

**Maven Resolver** — the resolution library Maven itself uses, available as an ordinary dependency. Real dependency resolution, repositories, authentication and mirrors, without a daemon or a distribution. Not on this machine, so nothing here is measured; it is named because "bundle Gradle or write it ourselves" is a false pair and this is the third option.

**A plain HTTP GET.** Maven Central's layout is deterministic, so a `maven:` coordinate maps to a URL with no library at all. Measured, ranged GETs against Central:

| coordinate | result |
|---|---|
| `guava-33.4.0-jre-sources.jar` | HTTP 206 |
| `kotlin-stdlib-2.1.0-sources.jar` | HTTP 206 |
| `ktor-server-core-jvm-3.5.2-sources.jar` | HTTP 206 |

That is the whole mechanism for the common case. It handles no mirror, no private repository, no authentication and no snapshot metadata, and it is wrong the moment a coordinate did not come from Central.

### Gradle can be told to fetch sources, centrally, with no project edited

This is the finding that reframes the rest, and it was reached late because the question kept being answered as "can the *plugin* do it".

Gradle has **no `downloadSources` switch for ordinary resolution**. Sources are fetched when something asks, and the asking is `ArtifactResolutionQuery`:

```kotlin
dependencies.createArtifactResolutionQuery()
    .forComponents(ids)
    .withArtifacts(JvmLibrary::class, SourcesArtifact::class)
    .execute()
```

Measured, against a project declaring one dependency: this fetched **both** `commons-text-1.12.0-sources.jar` and its transitive `commons-lang3-3.14.0-sources.jar`, into the ordinary Gradle cache — the same place cache-first already looks.

Measured again with the task removed from the project entirely and supplied by an **init script**: identical result. And init scripts have a machine-wide home. [The documentation](https://docs.gradle.org/current/userguide/init_scripts.html) gives `$GRADLE_USER_HOME/init.gradle(.kts)` and any `*.init.gradle(.kts)` under `$GRADLE_USER_HOME/init.d/`, applied to "every build on that machine". `GRADLE_USER_HOME` is `~/.gradle` unless set.

So a developer can configure this centrally or per project, and **we can ship the script** rather than asking anyone to find a setting. The same script populates a hosted machine's cache, which is the case this RAD started from.

Two claims about this circulate and both were tested rather than taken:

**`--refresh-dependencies` is not required.** The advice that already-cached libraries need a forced refresh before their sources appear is wrong for this mechanism. Measured: a coordinate whose main jar was cached and whose sources were not went from **0 to 1** cached sources jars with no refresh flag. The query resolves a different artifact type, so a cached main jar does not suppress it — and recommending the flag makes a first run far more expensive than it needs to be, because it re-checks everything.

**The obvious shape breaks the configuration cache.** A task that reaches `configurations` and `dependencies` at execution time — equivalently, an `afterResolve` hook doing the query — fails on Gradle 9.5:

```
cannot serialize object of type 'DefaultProject' ... not supported with the configuration cache
invocation of 'Task.project' by task ':compileJava' at execution time
```

That is disqualifying for a script installed machine-wide. One project opting out of the configuration cache is a local cost; an `init.d` script doing it silently degrades **every build on the machine**, and the plugin already holds configuration-cache compatibility as a criterion. So a shipped script has to register a task that is invoked deliberately and written cache-safely — not a hook that fires on every build.

Also inert, and worth saying because it appears in most recipes: setting `downloadSources = true` on the `idea` and `eclipse` models does nothing for an ordinary build. Those govern what their own tasks generate.

The honest limits beyond that: it applies to builds, so a machine that never builds gains nothing; it costs the download it is asking for, so it is not free on a cold project; and TestKit builds use an isolated Gradle user home and would not see it.

### Can the plugin turn source downloading on?

Read from documentation, not measured. The sync-time download is an **IDE setting** — IntelliJ's Settings | Build Tools | Gradle | Download sources, and an equivalent under Advanced Settings. Gradle's `idea` plugin has `downloadSources`, but that governs the project files the `idea` task generates rather than what an IDE does when it syncs through the Tooling API. A build plugin is on the wrong side of that boundary.

The plugin could instead resolve sources itself, through a detached configuration or an `ArtifactResolutionQuery`. That works and is the wrong thing by default: it forces resolution the build did not ask for and downloads during a sync, which contradicts two criteria the plugin already holds — *does not force resolution*, and *never fails, blocks or slows a build*. On a cold project it would add the whole sources download to the developer's first sync.

## Findings

**Measured**

- `ArtifactResolutionQuery` with `SourcesArtifact` fetches sources for the whole resolved graph, transitives included, into the ordinary cache.
- The same query works from an init script with the project untouched, and init scripts have a documented machine-wide location under `GRADLE_USER_HOME`.
- It does **not** need `--refresh-dependencies`: sources arrived for a coordinate whose main jar was already cached (0 to 1 cached sources jars, no flag).
- Doing the query from a task at execution time **fails the configuration cache** on Gradle 9.5, so the shape matters as much as the mechanism.
- 56% of cached artifacts on one real developer machine already have their sources on disk (3,829 of 6,892).
- A Gradle distribution is ~150 MB (146–151 MB across three versions).
- A sources jar can be fetched from Maven Central by URL alone, with no resolver, for three unrelated coordinates.

**Read from documentation**

- Gradle's `idea` plugin defaults `downloadSources` to `true`; IntelliJ has its own setting controlling sync behaviour.
- The Tooling API always uses a daemon, can provision its own distribution, and supports the last five Gradle majors.

**Argued, not verified**

- A build plugin cannot reliably cause an IDE to download sources, because the decision lives in the IDE.
- Divergence between our fetching and Gradle's resolution would present as a silently missing library rather than as an error. This is the strongest argument for reusing a real resolver, and nothing here has measured how often it would bite.

## Recommendation

**Not yet a commitment. Three things are clear and one is open.**

**Cache-first, always.** Whatever fetches, it runs second. This is free, it covers the majority on a developer machine, and it is already an acceptance criterion on #26.

**Do not bundle Gradle for the developer case.** It buys correctness we mostly do not need there, and costs a daemon and a distribution on a machine that has both already.

**Ship an init script instead of asking for a setting — as a task, not a hook.** A `*.init.gradle.kts` under `$GRADLE_USER_HOME/init.d/` needs no project edited and no IDE preference found, and lands artifacts exactly where cache-first already looks. It must **register a task that something invokes**, not hook `afterResolve` on every build: the hook form breaks the configuration cache, and doing that machine-wide is worse than not shipping the script at all. Installing it is a deliberate act, since it downloads what it asks for, so it belongs to whatever installs the service — off by default, and never to the plugin, which would then be forcing resolution and slowing a sync against two constraints it already holds.

**Open: what the hosted case uses.** Plain HTTP is enough for a Central-only deployment and needs nothing. Maven Resolver is the smallest thing that is actually correct. Gradle is the only option guaranteed to agree with the build that produced the coordinates.

### What was decided after this was written

The question stopped mattering as much as it looked, because the dependency was removed rather than satisfied. **The driver fetches what is missing, indexes it, and discards it** — using a cached sources jar in place where one exists, and deleting only what it fetched itself. Entries are content-addressed, so a coordinate is indexed once ever and the artifact has no second use.

That demotes everything above. The init script, the IDE setting and a warm cache all become ways to make a pass *cheaper*, not conditions for it working, and a machine with an empty cache and no Gradle at all still indexes. Recorded on [#26](https://github.com/dependencyskills/dependencyskills/issues/26); this record keeps the trail.

**What would change the answer:** whether private or authenticated repositories are in scope. If they are, plain HTTP is finished immediately and the choice narrows to Maven Resolver or Gradle — and if the requirement is that indexing must never silently disagree with the build, that narrows again to Gradle. Nobody has stated that requirement yet, which is why this stops here.

## Connections

- [#26](https://github.com/dependencyskills/dependencyskills/issues/26) — the driver that needs a file, and where cache-first is already a criterion.
- [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md) — why the sources jar is the content at all.
- [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) — the machine-level store these artifacts feed.
- [RAD-0056](RAD-0056-installed-rather-than-resolved.md) — installed rather than resolved, which is the question the hosted case belongs to.
