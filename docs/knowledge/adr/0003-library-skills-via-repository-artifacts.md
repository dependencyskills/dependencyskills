# ADR-0003: Library agent-skills ship as repository artifacts, not archive payloads

Date: 2026-08-05 · Superseded by ADR-0009 (2026-08-19) · v2

**Superseded (2026-08-19).** This decided the **sidecar** transport — a library
ships a `-skills.zip` classifier artifact. The path was abandoned once measurement
showed the content it would carry **already ships** in the `-sources.jar`, so
**ADR-0009** supersedes it (content is got from existing carriers, no bespoke
sidecar). Kept and marked, not deleted: the records that cite it (RAD-0005,
RAD-0008, the v1 postmortem) lean on *why* the sidecar was tried and dropped, and
the ADR log is append-only. (The body still describes its own earlier v1/v2
iterations, retained as history.)

## Context

### The problem being solved

An agent writing code against a library gets it wrong in ways a human
wouldn't. Not usually by failing to find the library - it already knows the
library from training data - but by knowing it *stale and averaged*: writing
against a version that isn't the one resolved, using an idiom that compiles
but violates a threading or lifecycle rule, calling something deprecated two
releases ago.

A bundled agent skill is the library author's own account of how to call the
library, **version-matched to the artifact actually on the classpath**. That
is the thing a README cannot be: a README lives on a website, is written for
a human deciding whether to adopt, and nothing guarantees an agent ever
fetches it or fetches the right version of it.

Secondary case: agents reinvent code they cannot see - a fresh retry loop, a
fresh date helper - because nothing told them the module exists. That
argument applies mostly to internal modules. The primary case above applies
to published libraries and is the stronger one.

### The gap we are filling, and the gap we are not

npm, SPM, Cargo, Go and NuGet all unpack dependencies onto disk, so a
directory convention works and [library-skills.io](https://library-skills.io)
already covers them. **We are not solving those.**

Gradle and Maven never unpack. A dependency is an archive in
`~/.gradle/caches/modules-2/files-2.1/` or `~/.m2/repository/`. Nothing in
the resolved graph is browsable, so a directory convention structurally
cannot work. That is Android, all of KMP, and the JVM server ecosystem -
the one major ecosystem where the emerging convention has no answer.

### Why the obvious approach failed

Two prior iterations, both abandoned:

**v1 - a bespoke flat file** (`.ai-skills/<id>.ai-skill.md`). Written before
the Agent Skills spec settled. Its fatal flaw: *it was not a skill.* It was
markdown with a suggestive name and a private convention around it, readable
only by software written to read it. It also used a `skill-id` frontmatter
key where the spec wants `name`. Lesson: **a bundled artifact is only useful
if it is the thing the ecosystem already knows how to load.**

**v2 - canonical in-archive path plus a manifest attribute**
(`META-INF/agents/skills/<name>/SKILL.md`, announced via `Agent-Skills` in
`MANIFEST.MF`). Works well for plain jars - a jar storing its skill at a
deliberately non-standard path was discovered purely from its declaration.
It dies on Android, measured three ways:

- AGP excludes `/META-INF/MANIFEST.MF` and `/META-INF/**/MANIFEST.MF` from
  an AAR's nested `classes.jar`, and an AAR has no manifest of its own.
  **There is nowhere to put the declaration.**
- On AGP 8, a KMP library's `src/commonMain/resources/` is silently dropped
  from the AAR entirely - JVM jar has the skill, AAR does not, nothing warns
  ([KT-46493](https://youtrack.jetbrains.com/issue/KT-46493), open since
  2021).
- Kotlin/Native packages `commonMain` resources into klibs not at all.

None of this per-target behaviour is documented by JetBrains or Google; it
was established by building libraries and unzipping the output. **A
packaging convention verifiable only by experiment is not one a spec can
lean on.**

## Decision

### 1. The skill is published as a repository artifact

`<artifact>-<version>-skills.zip`, a classified artifact on the library's
existing coordinates. On KMP it hangs off the **root** module - the skill is
platform-independent, so per-target copies would be N identical files and
would force consumers to know a target name.

This is not a new mechanism. `-sources` and `-javadoc` have always worked
this way, and every KMP library on Central already publishes
`<artifact>-<version>-kotlin-tooling-metadata.json` by exactly this route.

The packaging problem disappears rather than being worked around: one
platform-independent file, no AAR nesting, no AGP resource wiring, no klib
gap, no manifest attribute.

### 2. It is declared as a Gradle Module Metadata variant

A classifier artifact alone is discoverable only by constructing its URL -
nothing in the POM or `maven-metadata.xml` records that it exists. A GMM
variant records it with exact filenames and checksums, which is how Gradle
consumers fetch sources today without probing.

**This mechanism is already in use for exactly this shape of payload.**
Measured in a real Android/KMP Gradle cache (468 `.module` files):

| Publisher | Variant usage | Payload |
|---|---|---|
| AndroidX | `library-version-metadata` (38) | `apiLevels.json`, `-versionMetadata.json` |
| Compose Multiplatform | `kotlin-multiplatformresources` (40) | `kotlin_resources.zip` |
| Compose Multiplatform (JS) | `kotlin-multiplatformresourcesjs` (27) | same |
| AndroidX | `androidx-multiplatform-docs` (24) | — |

So the proposal upstream is not "invent a mechanism" but "add one more
`org.gradle.usage` value beside these."

Three rules, learned from a real failure. Compose Multiplatform's resources
variant declares three dependencies, and requesting it transitively **fails**
because Gradle then hunts for a `kotlin-multiplatformresources` variant of
`kotlin-stdlib` that does not exist. Therefore:

- the skills variant declares **no dependencies**;
- its capability is set **explicitly** (Gradle derives capabilities from the
  project name, not the artifactId -
  [gradle/gradle#16577](https://github.com/gradle/gradle/issues/16577));
- its attributes are distinct, so ordinary resolution can never match it.

### 3. Delivered as a Gradle plugin, not a script

The decisive reason is the **resolved dependency graph**. A script must guess
- walking the cache gives a conflict-unresolved superset of everything ever
downloaded, not your dependencies. A plugin gets the real graph, and Gradle
performs variant selection, repository selection, checksum verification and
caching for free. This also removes any need to parse `.module` by hand,
follow `available-at` redirects (1983 of them in one real cache), or guess
which repository a module came from - the cache does not record that.

Consequence: **no network probing, ever.** Probing was measured at ~148s for
a 374-dependency graph, with misses bypassing the CDN to hit origin - the
exact traffic pattern Sonatype's 2025 consumption limits target.

Maven needs its own plugin. The existing shell scripts keep their
cross-ecosystem coverage but stop being the strategic piece.

### 4. Layout: one librarian skill, bodies beside it

```
.agents/skills/librarian/SKILL.md     committed   - the index, one skill
.agents/libraries/<name>/SKILL.md     gitignored  - harvested bodies
```

**Why not one skill per library.** Skill bodies load lazily but
*descriptions do not*. A transitive Android graph is hundreds of modules; if
AndroidX ever shipped skills, dropping one entry per library into
`.agents/skills/` would flood every agent's skill list. And not every agent
is careful about this - designing around the hope that they are is a bad bet.
One aggregated index costs a single description line regardless of
dependency count: a naive agent sees one skill; a capable one loads the index,
finds the library it needs, and reads that body.

**Why not `docs/libraries/`.** That was the earlier answer and it was wrong -
it repeats the v1 mistake of inventing a private location beside a standard
one. An agent has to be *told* to look in `docs/`, which is precisely the
"adoption step in front of the discovery mechanism" that
[pnpm RFC #13422](https://github.com/orgs/pnpm/discussions/13422) objects to.
`docs/` is also convention, not standard - widespread because GitHub Pages
can serve from it, but specified nowhere, and the emerging agent-facing
conventions (`AGENTS.md`, `llms.txt`) sit at the repo root.

**Why `libraries` and not `modules` or `dependencies`.** In a Gradle build
"module" means *your own subprojects*, so `.agents/modules/` would read as
the opposite of what it holds; `node_modules` escapes this only because the
`node_` prefix disambiguates. `libraries` matches the vocabulary already in
use across this whole space - library-skills.io, `to-library-skill`,
"library skill" - so the proposal reads as one idea.

**Gitignored, like `node_modules`.** Every Gradle or Maven developer syncs a
new checkout before anything works; that is the first thing anyone does. The
harvested tree is derived and need not be committed. The **librarian is
committed**, so a fresh clone still carries the signal: an agent sees the
index, and the index states when it was generated and how to regenerate it.

**Directory key is the skill's declared name** (`org.dependencyskills.types`),
not the coordinate - Gradle resolves one version per module, so there is no
collision, and the name is what the librarian references. The resolved
version goes in frontmatter, since version-matching is the entire point.

**The librarian must survive the installer.** It lives in `.agents/skills/`
but is written by the plugin, so it has to be recorded as not-ours in
MANAGED.md or a skills refresh will prune it as unknown - the same
problem and the same solution as caveman.

### 5. In-archive bundling is dropped entirely

No archive scanning. Not jars, not AARs, not the nested `classes.jar`, and
the `Agent-Skills` manifest attribute is abandoned rather than kept as a
jar-only optimisation.

Reasons, in order of weight. We spent real effort establishing that the
bundled path is broken on Android and absent on native; **supporting a
mechanism while telling publishers not to use it is incoherent.** And
scanning was where all the cost and fragility lived: one real cache held
7,462 jars and 555 AARs, each opened to check for a file that is almost never
there.

**On the installed base.** No third party publishes JVM library skills, so
there is no ecosystem to stay compatible with. But v1 did ship: an earlier
skill went out on Maven Central under the maintainer's own coordinates, as
`META-INF/ai-skills/*.ai-skill.md` with a "paste this prompt first" README
section to make an agent look for it. Those artifacts are permanent and cannot
be withdrawn, which is precisely why the migration tooling exists and why the
current design treats "once published it is published" as a hard constraint
rather than a slogan. The absence of an installed base licenses dropping the
*mechanism*; it does not un-publish what already went out.

The consequence that matters most is on the **publisher** side: if nothing
ever reads the archive, the skill no longer has to be a packaged resource at
all. It is just a directory a Zip task reads.

```
src/agent-skills/<name>/SKILL.md          ← authored here
```

not `src/commonMain/resources/META-INF/agents/skills/...`. No resource
semantics, no per-target packaging behaviour, and **the entire KT-46493
problem disappears for publishers too**, not only for consumers. Inside the
zip the layout is flat — `<name>/SKILL.md` — since `META-INF/` only ever
meant "classpath resource" and it is not one now.

Accepted cost, stated plainly: a consumer without the plugin — or on Maven,
until a Maven plugin exists — has no path at all. Bundling would at least
have let a naive script find something. That does not outweigh the above,
given the plugin is the delivery mechanism and the scripts would be scanning
for something nobody publishes.

## Constraints

**Publication is permanent.** Once on Central, a version's artifacts cannot
be changed or withdrawn. Mitigations, in order of importance:

1. **Version the payload, not the packaging.** A format marker inside the
   zip means a reader identifies what it has from the contents, so the outer
   naming stops being load-bearing and a later convention can supersede this
   one without making old artifacts unreadable.
2. The classifier artifact is **additive and inert** - nothing resolves it
   unless asked, and Maven ignores `.module` entirely. Getting it wrong
   leaves a stray file, not a broken library. The **variant** is the risky
   part, because it participates in resolution; hence the three rules above.
3. Test the full round trip against a local file repository first, and take
   the first outing on a pre-release version.

**Filesystem naming**, since skill names become directory names. Verified
against Microsoft's Win32 naming rules:

- A leading period is explicitly permitted ("it is acceptable to specify a
  period as the first character of a name"), and periods within a name are
  fine. So `.agents/libraries/org.dependencyskills.types/` is safe.
- **Never end a name with a period or space** - Win32 strips them.
- **Reserved device names are forbidden**, including with an extension:
  `CON`, `PRN`, `AUX`, `NUL`, `COM1`–`COM9`, `LPT1`–`LPT9`. `NUL.txt` is
  equivalent to `NUL`. Unlikely with coordinates, but a validator should
  reject them.
- **Path length**: 260 characters before Windows 10 1607, and beyond that
  only with a registry or Group Policy change. Nested skill trees under a
  deep project directory can approach it - keep the layout shallow.
- Android additionally strips `**/_*`, so **no leading underscores** in
  skill directory names.
- macOS and Windows are case-insensitive by default, so two skills differing
  only in case collide. Coordinates are lowercase by convention.

## Consequences

- The Android and native packaging problems stop being ours - we route
  around them rather than working around them.
- Consumers can fetch a library's skill **without downloading the library**,
  which answers "does the library I am considering ship one?" - something
  bundling structurally cannot do.
- We take on a published, versioned Gradle plugin with a compatibility
  surface, including configuration-cache and isolated-projects constraints.
- Maven consumers get the classifier artifact but not variant resolution,
  until a Maven plugin exists.
- The cross-ecosystem shell scripts become legacy support rather than the
  main line, and their JVM archive-scanning becomes dead code.
- Skill sources move out of `resources/` in every library we publish. Any
  migration already run against the `META-INF/agents/skills/` in-resources
  layout needs redoing against `src/agent-skills/`.
- How an agent finds the right skill among many is a separate decision -
  see ADR-0004.

## Open questions

1. **The KMP root-module blocker.** `addVariantsFromConfiguration` throws on
   a KMP root component - it is not an `AdhocComponentWithVariants`. Compose
   Multiplatform gets its per-target variants through Kotlin-plugin
   internals, not a public extension point. **On KMP we can publish the
   classifier artifact but not the variant declaration**, which means
   consumers must name the classifier by hand. This is load-bearing for the
   design, and is the single most valuable thing the upstream proposal could
   get answered.
2. Attribute or capability for variant selection? Test fixtures use a
   capability with identical attributes; Compose uses a bespoke
   `org.gradle.usage` value and no capability. Both work; the ecosystem
   should pick one.
3. Classifier and extension naming: `-skills.zip` matches the `-sources`
   precedent. Alternatives: `-agent-skills.zip`, `.skills.zip`.
4. Should the librarian be regenerated on every build, or on an explicit
   task? Automatic is friendlier; automatic also means diff churn in a
   committed file.

## References

- ADR-0004 - the librarian skill and the harvested codex
- a previous proposal - the outward-facing version
- [Agent Skills spec](https://github.com/agentskills/agentskills)
- [library-skills.io](https://library-skills.io/create/)
- [pnpm RFC #13422](https://github.com/orgs/pnpm/discussions/13422)
- [KT-46493](https://youtrack.jetbrains.com/issue/KT-46493) - commonMain
  resources missing from Android artifacts
- [KT-63189](https://youtrack.jetbrains.com/issue/KT-63189) - JetBrains on
  the absence of a general KMP resources solution
- [gradle/gradle#16577](https://github.com/gradle/gradle/issues/16577) -
  test-fixtures capability naming
- [Naming Files, Paths, and Namespaces](https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file)
- [Maven Central publishing requirements](https://central.sonatype.org/publish/requirements/)
