# What Each Ecosystem Needs From the Lightweight Codex

RAD-0079 · 2026-09-24 · v1

Keywords: beyond Gradle; npm, Maven, Python, Go, Cargo and Swift consumers; the resolved set from a lockfile or a standard SBOM; does every build system need a plugin; where an installed package sits on disk; source-shipping versus binary-shipping ecosystems; the sources jar a build never downloads; missing sources as the JVM's particular problem; fetching sources at build time; the skill name outside Maven coordinates; authorship when a package ships several skills; which ecosystem next.

## Question

The lightweight codex works for Gradle projects consuming JVM and Kotlin Multiplatform libraries: a Gradle plugin reports what the project resolved, and a local lookup serves the skills those libraries ship. **What would it take to serve npm, Maven, Python, Go, Cargo and Swift projects — and does each need a build plugin of ours, as Gradle did?**

Where a library puts its skill so that it survives packaging is not this record's question. [RAD-0075](Research-RAD-0075-Naming-The-Skill-File) measured that across ecosystems and [RAD-0077](Research-RAD-0077-The-Npm-In-Package-Skill) took npm's existing convention; this record starts from the consumer's side.

## Trail

### Three things the codex needs from any ecosystem

1. **The resolved set** — which libraries, at which versions, this project uses. It is the scope: the lookup serves skills only for these.
2. **Where each installed package is on this machine**, and whether its skill is in it.
3. **The skill's name** — the rule that turns a package's identity into a legal skill name, which is also how a skill is tied to the library it describes ([RAD-0076](Research-RAD-0076-Skills-Republished-By-A-Third-Party)).

### The resolved set is already a standard

The Gradle plugin hands the codex a CycloneDX SBOM, and it does so because standard tools already produce one. For most ecosystems the resolved set therefore needs nothing of ours:

| ecosystem | resolved set from |
|---|---|
| Gradle | this project's plugin |
| Maven | the CycloneDX Maven plugin, filtered to the compile scope |
| npm | `package-lock.json`, or `npm sbom` |
| Python | `uv.lock`, `poetry.lock`, or cyclonedx-py over the environment |
| Go | `go.sum` / `go list -m all` |
| Cargo | `Cargo.lock` |
| Swift | `Package.resolved` |

What earned the Gradle plugin its place on the consumer side is not the SBOM itself but three things an SBOM generator does not do: it lists libraries a version catalog declares before any module uses them, it merges by module so building one module does not shrink the scope, and it says in the build output when a build added a dependency ([RAD-0078](Research-RAD-0078-Recommending-A-Dependency-The-Project-Does-Not-Have) v2). Those are conveniences. Elsewhere a consumer-side plugin of ours is optional, and justified only by the same three.

One difference matters. SBOM tools usually describe what is installed or shipped; the codex wants what the project may **import**. On the JVM those differ — a runtime-only dependency is on the classpath and not importable — which is why the Gradle plugin reads compile classpaths and a Maven SBOM would need filtering by scope. In npm and Python everything installed is importable, so the distinction mostly disappears.

### Where the skill is on disk: two families

**Source-shipping ecosystems — npm, Python, Go, Cargo.** The installed package *is* its source, so a `skills/` directory the author shipped is on disk the moment the dependency is installed: in the project's `node_modules/<package>/`, the environment's `site-packages`, Go's module cache, Cargo's registry sources. RAD-0075 measured that it survives packaging in each, given the one-line fixes it names for setuptools and npm's `files`. Nothing has to be fetched.

**Binary-shipping ecosystems — the JVM through Gradle and Maven.** The installed artifact is compiled, and the skill travels in a *separate* artifact, the sources jar ([ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar)). A build never downloads it; an IDE downloads it when it syncs a project, if it is configured to. So whether a JVM library's skill is on the machine depends on how the machine has been used, not on the library:

- In the Gradle cache of a machine where an IDE is used daily, 1,966 of 2,298 cached library versions (86%) have a sources jar beside the jar. In the same machine's local Maven repository, 109 of 265 (41%).
- In one consumer project that had been opened in an IDE, the lookup's first index found no sources jar for 14 of the 18 dependencies it had not seen before.
- On a machine or in a CI job that has only ever built from the command line, the figure would be near zero, and the lookup would find no skills at all.

The lookup deliberately does not download inside a tool call, because a first call on a large project would hold the agent for minutes. The fix belongs to the build: the Gradle plugin can resolve the sources artifact of each in-scope dependency while the build runs, which puts it in Gradle's own cache with Gradle's own repositories, credentials and checksums, before anyone asks. A Maven plugin would do the same through Maven's resolver.

**Swift.** Binary frameworks carry almost nothing a text skill can ride; RAD-0075 found the routes that do reach an XCFramework — a KDoc in the umbrella header, a file written into the bundle — and they want their own consumer-side reading.

### The skill's name outside Maven coordinates

The current rule encodes `group:artifact` into a legal skill name, and a skill is accepted only from the artifact whose coordinate its name encodes. That is Maven-shaped. Every ecosystem's packages have a package URL with a namespace and a name — npm's `@scope/name`, PyPI's normalised name with no namespace, Go's module path, Cargo's crate name — so an encoding exists for each, and step 2 of the rule (compacting long groups) would matter mostly for Go's long module paths.

But the rule's *purpose* does not carry over unchanged. On the JVM it assumes one skill per library, named for the library, and it is what keeps a republisher from filing a skill under someone else's coordinate (RAD-0076). npm's existing practice, which RAD-0077 recommends adopting as it stands, lets one package ship several skills under names its author chose. Requiring the name to equal the package would reject skills already published.

Authorship survives without the name rule. A skill found inside a package's own installed directory was shipped by that package; what must not happen is serving it *as another library's*.

**Settled for npm: two orders of skill.** `to-library-skill` does the same job in npm as on the JVM, writing one skill named for the package's coordinate — `@scope/name` encoded by the same rule — and that skill is **first-order**: it is what the lookup lists and serves first for the package. Any other skill the package ships, under whatever name its author chose, is **second-order**: indexed, attributed to the package that carries it and never to another library, and listed after the first-order one. The ecosystem's existing practice is picked up rather than rejected, and a skill written to this project's shape is preferred where both exist.

### Which ecosystems need a plugin of ours

- **Gradle:** yes, for both halves, and it exists. The missing piece is fetching sources at build time.
- **Maven:** for library authors, the sources plugin can carry a `skills/` directory with configuration, but checking the name and printing it is what makes the Gradle plugin usable, and wants a small Maven plugin. For consumers, the CycloneDX plugin gives the resolved set, but the sources jars are missing even more often than in the Gradle cache.
- **npm:** none. Authors add `skills` to `files` (RAD-0077); consumers already have a lockfile.
- **Python, Go, Cargo:** none for consumers. Python authors need the one `package-data` line RAD-0075 verified.
- **Swift:** a separate question, following RAD-0075's routes.

## Findings

**Measured.**

- On a machine where an IDE is used daily, 86% of library versions in the Gradle cache (1,966 of 2,298) and 41% in the local Maven repository (109 of 265) have a sources jar beside the jar.
- In one consumer project opened in an IDE, the lookup's first index found no sources jar for 14 of the 18 dependencies it had not already seen.
- npm 11.13 ships an `npm sbom` command; its output was not examined here.
- With the fix in place, a command-line `classes` build of a one-dependency consumer on an empty Gradle home fetched the sources jars of everything on its compile classpath — including the platform module's, `kotlinx-datetime-jvm`, for a multiplatform library recorded under its root coordinate — and the codex located them from that coordinate. Gradle derives a sources variant for a library published with a POM alone, so one variant request covers both kinds of publication.

**Inherited ([RAD-0075](Research-RAD-0075-Naming-The-Skill-File), [RAD-0077](Research-RAD-0077-The-Npm-In-Package-Skill)).**

- A `SKILL.md` survives packaging in the JVM sources jars, hatchling and uv_build, Go module zips, Cargo crates, and npm, with one-line fixes for setuptools and npm's `files`.
- npm's convention is `skills/<name>/SKILL.md` at the package root, one package possibly shipping several.

**Argued, not measured.**

- The resolved set is available without a plugin of ours in every ecosystem listed; the Gradle plugin's consumer half is justified by catalog declarations, per-module merging and the build-output notice, not by the SBOM.
- Missing skills are a JVM problem specifically, because only there does the skill travel in an artifact a build never downloads; the fix is to fetch sources during the build, not during a lookup.
- Outside the JVM, requiring the name to equal the coordinate would reject existing practice; ranking the coordinate-named skill first and the package's others second, all attributed to the carrier, keeps both.

## Recommendation

**Not a commitment.**

1. **Fix the JVM first.** The Gradle plugin fetches the sources jar for each in-scope dependency during the build. Without it the lookup finds nothing on a machine that only builds from the command line, which is where agents usually build.
2. **npm next.** It is the largest ecosystem, packages ship their source so authors need no plugin, the lockfile gives the resolved set, and Kotlin Multiplatform libraries already publish to npm. The work is in the codex: read `pkg:npm` package URLs, find the package under the project's `node_modules`, and serve its skills attributed to it.
3. **Write the name rule per ecosystem into the specification before any non-JVM release.** For npm it is settled above: the coordinate-named skill first, other skills in the package second, every one attributed to its carrier.
4. **Maven after npm**, as a small plugin for both halves. Python, Go and Cargo when someone asks — each is a lockfile reader and a location in the codex. Swift follows RAD-0075's routes.

**What would change the answer.** If the SBOM a standard tool produces turns out to lose something the scope depends on — a scope, a workspace boundary — a consumer-side plugin becomes necessary in that ecosystem. If npm authors converge on naming a package's one skill after the package, one naming rule could serve everywhere. If fetching sources during the build proves too slow for large projects, it moves behind a switch, and the lookup says plainly which dependencies it could not read.

## Connections

- [RAD-0075](Research-RAD-0075-Naming-The-Skill-File) — where a skill survives packaging, per ecosystem, and the coordinate as a skill name.
- [RAD-0077](Research-RAD-0077-The-Npm-In-Package-Skill) — npm's in-package convention, adopted as it stands.
- [RAD-0076](Research-RAD-0076-Skills-Republished-By-A-Third-Party) — why a skill is attributed to the artifact that carries it.
- [RAD-0078](Research-RAD-0078-Recommending-A-Dependency-The-Project-Does-Not-Have) — the consumer-side conveniences that justify the Gradle plugin.
- [ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar) — the sources jar as the JVM's transport, and the artifact a build does not fetch.
- [ADR-0012](Decisions-ADR-0012-A-Shared-Machine-Level-Index-Store) — the scope the resolved set defines.
