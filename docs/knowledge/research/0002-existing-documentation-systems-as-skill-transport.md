# Existing Documentation Systems as Skill Transport

RAD-0002 · 2026-08-13 (split 2026-08-17) · v2

**Measured against:** Maven Central and Google Maven (`dl.google.com/dl/android/maven2`)
as of 2026-08-13; coordinate samples drawn from the dependency graphs collected
in `experiments/cost-model/data/` (Spring PetClinic, Now in Android, Compose
Multiplatform `codeviewer`, Ktor sample). npm tree from `next-cms-tina`; Python
distributions from a fresh virtualenv; Go 1.25.0 module cache for the `cli/cli`
build list; `swift-composable-architecture` at its checked-in `Package.resolved`
— all 2026-08-13.

**Split note.** This record originally covered both transport and content. On
2026-08-17 the content half — whether KDoc and its cross-ecosystem equivalents
are rich enough to *be* skill content, how much exists, and the custom-tag
constraint — was moved to
[RAD-0011: Existing Documentation Systems as Skill Content](0011-existing-documentation-systems-as-skill-content.md).
This record is now transport only: whether a suitable carrier already travels
with a library. The two are complementary and cross-reference.

## Question

The withdrawn sidecar decision proposed a bespoke `-skills.zip` classifier
artifact as the way a JVM library ships an agent skill, on the premise that
nothing suitable already travels with a library.

Does something already travel with it?

1. **Is `-sources.jar` — or `-javadoc.jar` — already published** widely enough
   that a new artifact is unnecessary?
2. **Is the source reachable without an artifact at all**, via the repository
   the publishing metadata already names — the model SwiftPM uses?

(Whether what travels is *rich enough to use* is the content question, now
RAD-0011.)

**Corroborated independently.** The idea was held before this investigation
began, but an external reviewer of the v3 proposal proposed the same mechanism
unprompted (see [RAD-0004](0004-external-review-of-the-proposal.md) §4). A
convention is a bet that others find the same shape natural; someone reaching
for it from their own experience is evidence the bet is sound. What this record
adds is measurement.

This RAD recommends; it does not commit.

## Trail

### What is already published

180 coordinates were sampled at random (seeded) from four public JVM dependency
graphs and their Maven Central directory listings read — the cheapest route,
one request per artifact-version, CDN-cached. 170 listed successfully.

| Maven Central, n=170 | |
|---|---|
| `-sources.jar` | **95.3%** |
| `-javadoc.jar` | 92.4% |
| both | 92.4% |
| neither | 4.7% |
| `.module` (Gradle Module Metadata) | 62.9% |

Split by packaging, the 4.7% resolves: `jar` 99% sources / 96% javadoc (n=155);
`aar` 89% / 89% (n=9); `pom`-only 0% / 0% (n=6, BOMs and platforms with no source
to publish). **There is essentially no library in the sample that ships code
without also shipping its source.**

Google Maven had to be probed differently — its directory listing returns **HTTP
404** — so `HEAD` requests were used on a 45-coordinate sample, and a second
60-coordinate sample split by packaging.

| Google Maven | |
|---|---|
| `-sources.jar` | **93.3%** (n=45) |
| `-javadoc.jar` | **6.7%** (n=45) |
| `-sources.jar`, AAR-packaged | **98%** (50 of 51) |
| `-sources.jar`, JAR-packaged | 100% (8 of 8) |

**This is the finding that decides the shape.** `-javadoc.jar` is near-universal
on Central and effectively absent on Google Maven, where roughly half an Android
graph lives. Any design resting on javadoc is dead for Android on arrival.
`-sources.jar` is near-universal on both, and on AARs specifically — the
packaging the whole JVM problem is about — it is 98%.

KMP root modules were checked directly, because the sidecar's known blocker
lived there: `kotlinx-datetime`, `kotlinx-coroutines-core` and `ktor-client-core`
all publish `-sources.jar` **on the root module**. (`kotlinx-coroutines-core`
publishes no javadoc at all, reinforcing the point above.)

### What it costs a publisher who does not already do it

Every major toolchain has a one-line opt-in, and most publishing plugins take it
by default:

- Gradle JVM — `java { withSourcesJar(); withJavadocJar() }`
- Android — `android { publishing { singleVariant("release") { withSourcesJar() } } }`,
  available since **AGP 7.1**
- Kotlin Multiplatform — the plugin publishes a sources jar by default
- `com.vanniktech.maven.publish` configures it across JVM, Android, KMP and
  Gradle-plugin projects without per-type knowledge

So the publisher-side ask drops from "adopt a new artifact this proposal
invented" to "keep doing what you already do." That is a categorically different
adoption argument.

### The git coordinate is already in the metadata

A published POM carries an `<scm>` block, and Maven Central's publishing
requirements mandate one.

| | `<scm>` present | host |
|---|---|---|
| Maven Central (n=41) | **90%** | 36 `github.com`, 1 `gitbox.apache.org` |
| Google Maven (n=45) | **82%** | 37 `android.googlesource.com` |

Every SCM URL found resolves to a real, publicly fetchable git host. (An earlier
pass reported 0% for Google Maven; that was a classification error — the pattern
only matched GitHub and GitLab, and AOSP uses Gitiles.)

**So there is a third transport, and it needs no repository artifact at all.**
The dependency graph gives coordinates; the POM gives the source repository; the
repository serves files over HTTPS. A library could publish a pre-built index at
a well-known path in its own repository — the analogue of `.agents/skills/` for
ecosystems that have a tree, reachable by anyone holding the coordinate. This is
exactly SwiftPM's model, where a package *is* a git coordinate.

It is strictly better than the sidecar on three counts and worse on two.
Better: nothing to publish, nothing to version, and it **works retroactively** —
a library that shipped years ago can add an index today without cutting a
release, which no artifact-based mechanism can do. Worse: it is not
content-addressed, so it can drift from the released version and carries no
checksum.

#### Version drift: measured, and it is the route's real defect

The obvious answer is that `<scm><tag>` records the release tag. Measured across
86 POMs, it does not:

| | |
|---|---|
| `<scm>` present but **no `<tag>`** | **81%** (70 of 86) |
| no `<scm>` at all | 14% (12 of 86) |
| `<tag>` identifying the released version | **2%** (2 of 86) |
| `<tag>HEAD</tag>` — the Maven default, left unedited | 2% (2 of 86) |

Of 83 sources jars, **all 83 have a `META-INF/MANIFEST.MF` and none records any
revision, commit, SHA, SCM or VCS header.** **So there is no path from a released
Maven artifact to the commit it was built from.** SwiftPM has no such discrepancy
because a package *is* a git coordinate: `Package.resolved` pins both the
semantic version and the immutable commit, so "the docs in the repo" and "the
docs in the release" cannot diverge.

Both objections to this route — the default branch describes unreleased work,
and git is mutable and unchecksummed — are consequences of inheriting git's
defaults, and both dissolve once the file is *designed* rather than discovered
(version-addressed paths; git object IDs are content hashes; 99% of Central
publishers already sign). Working that into a specification is its own design
piece, now [RAD-0005: A Git-Hosted Codex](0005-a-git-hosted-codex.md). What
matters here is only that the route survives its objections and is worth
specifying, as an *additional* source.

### Transport reaches the consumer everywhere — the JVM just later

In every ecosystem with an unpacked tree, the library's source (and its
documentation) is *already on the consumer's disk*, versioned with the artifact:

| Ecosystem | Reaches the consumer |
|---|---|
| Go | **100%** — the module cache is source |
| pip | **100%** — ships readable source |
| Swift / SPM | **already on disk** — SPM ships source, including the `.docc` catalog |
| npm | **99%** ship readable source |
| Kotlin / JVM | **93–98%** as `-sources.jar` — the only ecosystem where source must be fetched, one HTTP GET away |

The JVM is the exception only in that its source is one GET away rather than
already extracted — a far smaller gap than "the JVM cannot participate," which is
how earlier drafts framed it. (What *content* is in those trees, and how much, is
RAD-0011.)

### The sidecar: what was investigated, and why it should be abandoned

Recorded because it was a deliberate design that survived two revisions and
should not be silently dropped.

**What it was.** A `<artifact>-<version>-skills.zip` classifier artifact
containing `<name>/SKILL.md` trees, hung off the KMP root module, declared in
Gradle Module Metadata as a variant with an `ai-skill` usage attribute and an
explicit capability, with nothing ever reading the archive.

**It was well-reasoned and it works.** The classifier route is real precedent
(`-sources`, `-javadoc`, `kotlin-tooling-metadata.json`), dodges every AGP and
klib packaging failure, and is fetchable without the library. The Gradle Module
Metadata work is genuinely hard-won: the variant must declare no dependencies,
and the capability must be set explicitly.

**Why abandon it.**

1. **It duplicates an artifact that already exists at 93–98% adoption**, on both
   repositories, on AARs, and on KMP roots.
2. **It requires publisher action; sources does not.** A new classifier is a
   build change every publisher must make; nearly all already publish sources,
   most without thinking about it.
3. **The sidecar's one known blocker disappears.** `addVariantsFromConfiguration`
   throws on a KMP root module, so on KMP you got the artifact but not the
   variant declaration — "the single most useful correction this document could
   receive". `-sources.jar` is already published on KMP roots by the standard
   plugin. The blocker is not solved; it is made irrelevant.
4. **It repeats the v1 mistake in a subtler form.** The v1 postmortem's lesson is
   that a bundled artifact is only useful if it is the same thing the ecosystem
   already knows how to load. `-skills.zip` is a new thing; `-sources.jar` is
   what it has loaded for twenty years.
5. **Permanence cuts against it.** A published artifact cannot be withdrawn.
   Committing libraries to a classifier this project invented is exactly the risk
   the README's experimental warning describes; `-sources.jar` carries none.

**What abandoning it costs.** Two real things. A sources jar carries no
*declaration* that skill-shaped guidance is present, so "fetchable before I
depend on it" weakens to "fetchable, then inspected". And a sources jar is the
whole source, not a curated file — larger, and it must be parsed rather than
read. (That parse cost is a content-harvesting concern; see RAD-0011.)

## Findings

**Measured.**

- `-sources.jar` is published by 95.3% of a Central sample (n=170) and 93.3% of a
  Google Maven sample (n=45); 98% of Google Maven AARs (n=51); and by the root
  module of every KMP library checked.
- `-javadoc.jar` is 92.4% on Central but **6.7% on Google Maven**. Javadoc is not
  a viable carrier for Android.
- Google Maven directory listing returns 404; probing is required there.
- The source repository is in the published metadata: `<scm>` present in 90% of
  Central POMs and 82% of Google Maven POMs, every one resolving to a fetchable
  git host — so a pre-built index at a well-known path in the library's own
  repository is reachable from a coordinate alone.
- But there is **no path from a released artifact to its commit**: `<scm><tag>`
  identifies the release in only 2% of POMs, and no sources-jar manifest records
  a revision.
- Every ecosystem with an unpacked tree already has the source on the consumer's
  disk (Go/pip/npm 99–100%, SPM ships source); the JVM is the one that fetches.

**Assumed or reasoned, not measured.**

- That a `-sources.jar` can be fetched and selectively read without full
  extraction. Zip central directories permit it; not tested here. *(Load-bearing
  for the harvester — see RAD-0009 and RAD-0011.)*
- That most publishers get sources "for free" from a publishing plugin rather
  than by deliberate choice.

## Recommendation

**Pivot the transport to `-sources.jar`.** This is the substrate decision, and it
is decision-ready: the evidence is strong and the ask is "keep doing what you
do." It should become the packaging ADR that replaces the withdrawn sidecar
decision.

**Extend the scope to first-party modules.** In a multi-module project the team's
own libraries are in the source tree, so their documentation is readable with no
transport at all — the cheapest case and arguably the highest value, because
reinvention is worst where an agent rewrites a helper three modules over. One
mechanism covers first-party and third-party; the sidecar covered only
third-party.

**Add the git coordinate as an additional route** (→ RAD-0005), version-addressed
so it does not drift, signed with the key 99% of publishers already hold. It is
the only mechanism that works retroactively, and adoption is the whole problem.

**Name the smallest possible ask of the ecosystem: populate `<scm><tag>`.** It is
already in the POM schema, `maven-release-plugin` sets it, and it is one line —
the cheapest fix to the only serious defect in the git route, and far easier to
advocate than a classifier nobody has heard of.

**Reframe the transport claim.** The JVM is no longer "the ecosystem that cannot
participate"; every ecosystem already ships its documentation with the library,
the JVM one GET away. What remains universal is that nothing *indexes* it — which
is the content-and-index story (RAD-0011, RAD-0003), not a transport gap.

**What would change the answer.** If sources jars cannot be read selectively, the
fetch-and-parse cost may be prohibitive at 300+ dependencies and the
curated-artifact argument returns. If `<scm><tag>` adoption cannot be moved, the
git route stays second to `-sources.jar` for anything version-exact.

## Connections

- The withdrawn sidecar decision (in `_to_delete/premature-adrs/`) — what this
  recommends replacing; the transport ADR is a new record, not a supersession.
- [RAD-0011](0011-existing-documentation-systems-as-skill-content.md) — the
  content half split out of this record: is what travels rich enough to use.
- [RAD-0005](0005-a-git-hosted-codex.md) — the git-hosted route, its own design.
- [RAD-0009](0009-reusing-indexers-and-what-to-index.md) — the harvester that
  reads these carriers; the selective-read cost is its feasibility question.
- [ADR-0007](../adr/0007-conform-to-existing-conventions.md) — conforming to what
  exists; `-sources.jar` is a stronger instance than a new classifier.
- [RAD-0006](0006-development-time-prompt-injection.md) — the security objection
  to harvesting prose, split out.
- [RAD-0004](0004-external-review-of-the-proposal.md) §4 — the independent arrival
  at this mechanism.
