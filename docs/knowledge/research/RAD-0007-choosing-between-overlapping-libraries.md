# Choosing Between Overlapping Libraries

RAD-0007 · 2026-08-19 · v3
Keywords: which of two overlapping libraries should the agent use; moment versus date-fns; is overlap a defect or the domain; how much overlap on the JVM versus npm; dependency scope as a statement of intent; who may author a preference signal; preferOver and similar tags; negative guidance; why selection does not close with a better model.

**Measured against:** the dependency graphs in `experiments/cost-model/data/`,
collected 2026-08-13 — Now in Android, Spring PetClinic, the median
`ktor-samples` sample, Compose Multiplatform `codeviewer`, and the npm, pip
and Go graphs. Overlap scan run 2026-08-14 against those files. Versions as
resolved on 2026-08-13.

**v2 (2026-08-19).** No new measurement. This record now **owns** the
overlap-is-domain position and the relationship taxonomy directly, cross-linking
ADR-0004 (re-minted as the two-layer design they belong to), and adds the
**preference-authorship trust model**: who may author a preference signal, how
much it is weighted, and where the `@similar` / `@category` / `@triggers` /
`@preferOver` tags sit on that line.

**v3 (2026-08-20).** Folds in the first measurement of this record's claim — the
**selection A/B** ([RAD-0018](RAD-0018-the-selection-ab.md)). Unaided, the sanctioned
library is picked **0/18**: no model, however capable or current, knows the
preference. The **declared dependency tree** redirects a single-choice classpath
almost universally, and where genuine ambiguity remains only an **authored
preference** resolves it — confirming that selection closes with neither
freshness nor model capability. See the measured bullet under Findings.

## Question

A real dependency graph contains several libraries that do the same job. An
agent that can now *see* all of them has to pick one, and picking wrong is a
new failure introduced by solving the first two.

An external reviewer put it concretely
([RAD-0004](RAD-0004-external-review-of-the-proposal.md) §3), paraphrased here at his
request: a project that includes both a node-graph library and a
graph-rendering library may find each describes itself in terms that collide
with the other, sending an agent to the wrong one for the task in hand.

This record takes overlap as the **domain rather than a defect**, and holds that
the discriminating knowledge — *several of these do X, we reach for that one,
here is why not the others* — cannot be harvested, because no library knows what
else is on your classpath. (ADR-0004 first set this out as the two-layer design;
here it earns the worked example and taxonomy it was missing.) A previous candidate
example was investigated and refuted by the data.

So: **is overlap real and demonstrable in a public graph, what signals
discriminate, and which part genuinely cannot be computed?**

## Trail

### A first pass that turned out to be measuring the wrong set

A capability scan across the collected JVM graphs found what looked like a
clean worked example. **Now in Android** appeared to carry three JSON
serialisation libraries: `kotlinx-serialization-json` declared in three
modules, with `gson` and `moshi` arriving transitively.

**It does not.** Restricted to the *importable* set — what is actually on a
compile classpath, measured with `collect-visible.init.gradle` — `gson`
appears **zero** times and `moshi` **zero** times. `gson` enters only through
the `:lint` module and `moshi` only through `:benchmarks`. Neither is on any
classpath a developer or an agent writes code against.

The Android Gradle Plugin explains exactly why. `lintChecks` and `lintPublish`
are build-tooling configurations: `lintPublish` packages a lint check into the
AAR's `lint.jar` so consumers run it, and **neither reaches a consumer's
compile classpath**. Now in Android's `core:designsystem` declares
`lintPublish(projects.lint)`, so the `:lint` module's own dependencies —
`gson` among them — are in the resolved graph as a lint tool's internals, not
as a JSON library anyone could choose.

The apparent overlap was manufactured by the collector taking a union across
every resolvable configuration, including build tooling. **That is a
methodology lesson worth keeping: the set you scan determines the problem you
think you have.**

### Restricted to what is importable, the JVM has almost no overlap at all

Re-running the scan against importable sets only, across four public JVM
projects:

| Project | Importable | Capability groups with >1 importable member |
|---|---|---|
| Now in Android | 311 | HTTP (`okhttp`, `retrofit`); DI (`dagger`, `hilt`, `javax.inject`, `jakarta.inject`); utils (`guava`, `commons-io`) |
| Spring PetClinic | 112 | Logging (`slf4j-api`, `logback-classic`, `log4j-api`, `commons-logging`, `log4j-to-slf4j`) |
| Compose MP `codeviewer` | 238 | **none** |
| Ktor sample | 63 | Logging (`slf4j-api`, `logback-classic`) |

And every surviving cluster is a false positive on inspection. Retrofit is
built *on* OkHttp. Hilt is built *on* Dagger, and `javax.inject` /
`jakarta.inject` are one specification at two versions. PetClinic's five
logging coordinates are one stack — a facade, its implementation, a bridge and
a legacy facade. Guava and commons-io are not the same capability; that was a
coarse grouping on my part.

**Across four real JVM graphs there is not a single genuine "two alternatives,
pick one" case.** That is a deflationary result and it should be stated
plainly rather than argued around.

### In npm the problem is everywhere

The same scan against the npm and pip graphs, where every installed package is
importable because the layout is flat:

| Project | Packages | Overlapping capability groups |
|---|---|---|
| **Next.js example, p90** | 995 | **8** — date/time, utility, HTTP, deep-equal, glob, colour, argument parsing, schema validation |
| Next.js example, largest | 919 | 8 |
| `create-next-app` default | 423 | 2 |
| `create-vite` react-ts | 84 | none |
| Django service | 46 | none |
| FastAPI service | 82 | none |

The p90 Next.js example carries `moment` *and* `date-fns`; `axios`,
`node-fetch` *and* `cross-fetch`; three glob libraries; three colour
libraries; four argument parsers; three schema validators. All transitive, all
importable.

**The worked example this record needs is therefore an npm one.** In
`next-cms-tina`, an agent asked to format a date finds both `moment` and
`date-fns` present and requirable. Moment's own project describes itself as a
legacy project in maintenance mode and points new work elsewhere — so the
library an agent's training data most strongly favours is the one its own
authors advise against, and both are sitting in `node_modules`. That is the
drift failure and the selection failure compounding, in a public project
anyone can reproduce with `npm install`.

### Why the two ecosystems differ, and it is the same mechanism twice

The JVM filters candidates before anything reaches an agent, in two ways npm
has no equivalent for:

- **`api` versus `implementation`.** A library's private dependencies do not
  land on a consumer's compile classpath at all.
- **Configuration roles.** Build tooling — lint checks, annotation
  processors, benchmarks — resolves into its own configurations, which are
  not classpaths anyone writes code against.

npm's flat `node_modules` has neither. Everything installed is requirable by
name, so every transitive is a candidate, and the ecosystem's habit of small
single-purpose packages guarantees several per capability.

So the clobbering problem is **ecosystem-shaped, and it is worst exactly where
the existing directory conventions live.** This is the second time the
measurements have inverted the project's framing: the JVM turns out to have
the richer metadata and the smaller problem, and npm the reverse.

### Dependency scope is published, and it is a statement of intent

Gradle distinguishes `api` from `implementation`, and the question is whether
that survives to a consumer. Measured against 40 Central coordinates on
2026-08-14:

| | |
|---|---|
| publish Gradle Module Metadata (`.module`) | **68%** |
| dependencies exposed via an api-usage variant | **78%** |
| implementation-only (runtime variant, not api) | 22% |

POM `<scope>` carries a lossier version for everything else: 55% `compile`
(33% explicit plus 22% defaulted), 29% `test`, 11% `runtime`, 5% `provided`.
Gradle maps `api` to `compile` and `implementation` to `runtime` when it
generates a POM, so the distinction is recoverable there too, just coarser.

The full set the `java-library` plugin defines, and what each means **for a
consumer**:

| Configuration | Consumer's compile classpath | Consumer's runtime classpath | Published as API |
|---|---|---|---|
| `api` | yes | yes | **yes** |
| `implementation` | **no** | yes | no |
| `compileOnly` | no | no | no |
| `compileOnlyApi` | yes | no | yes |
| `runtimeOnly` | **no** | yes | no |

The Android Gradle Plugin adds more, and the extras matter here because they
are where false candidates come from: `ksp`, `kapt` and `annotationProcessor`
run before compilation and are deliberately kept off the compile classpath;
`lintChecks` supplies build-time lint rules; `lintPublish` packages lint rules
into a published AAR's `lint.jar`. **None of these reaches a consumer's
compile classpath.** Variant-specific forms — `debugImplementation`,
`testImplementation`, `androidTestImplementation`, `freeImplementation` —
scope a dependency to a build type, flavour or test source set.

That table is the whole explanation for the corrected finding above: `gson`
reached Now in Android's graph through `lintPublish`, which is a row that
never touches a consumer's classpath.

**This is the most useful signal in the graph, because it is the library
author saying what the consumer is meant to touch.** An `api` dependency is
part of the library's contract — you are expected to use its types. An
`implementation` dependency is one the author deliberately hid. A transitive
arriving only via `implementation` is, by the author's own declaration, not
for you.

Two things follow.

**It partly answers the relationship-kind problem.** A library that declares
`api` on another is exposing it; one that declares `implementation` is
consuming it privately. Combined with the direction of the dependency edge —
`logback-classic` depends on `slf4j-api`, not the reverse — that is a real,
published, machine-readable basis for distinguishing "alternative" from
"implementation of", which this record earlier called largely un-harvestable.
It does not settle every case, but it is far more than nothing and it is
already there for two thirds of the ecosystem.

**It explains a number RAD-0002 measured but did not account for.** The
importable set was found to be 86–99% of the resolved graph on the JVM, which
is high enough to be surprising. The mechanism is this: importable *is* the
`api` closure, and 78% of published dependencies are api-exposed. Libraries
use `api` liberally, so almost everything leaks through. Had `implementation`
been the norm, the importable set — and therefore the whole resident cost
problem — would be substantially smaller.

That makes `api`/`implementation` hygiene a **lever on the cost model**, not
just a build-tidiness concern, and a candidate check for the
completeness-verification plugin in
[RAD-0003](RAD-0003-central-capability-server.md): a library that marks internal
dependencies `api` inflates every consumer's surface.

### How other ecosystems compare

The JVM is unusually rich here, which inverts the usual framing of it as the
disadvantaged ecosystem.

| Ecosystem | Scope information published | Expresses "not for you"? |
|---|---|---|
| Gradle / Maven | GMM variants (68%), POM `<scope>` otherwise | **yes** — `api` vs `implementation` |
| npm | `dependencies`, `devDependencies`, `peerDependencies`, `optionalDependencies` | no — a flat `node_modules` makes everything reachable regardless |
| SPM | target-level dependencies; products are the exposed surface | partly — internal target deps are not products |
| pip | `install_requires` plus extras | no |
| Go | `// indirect` in `go.mod` | barely — direct versus not, nothing about exposure |
| Cargo | `dependencies` / `dev-` / `build-` | no |

npm's `peerDependencies` is the one strong intent signal outside the JVM —
"you must supply this" — but nothing in npm, pip or Go expresses "this
transitive is not part of my contract". **Unverified:** none of the non-JVM
rows was measured; they are read from each tool's documented manifest model.

### What discriminates, and what it costs

Signals available from the resolved graph, with no author cooperation:

1. **Declared versus transitive.** RAD-0002 measured declared as 3–18×
   smaller than importable. In the NiA JSON case it is decisive on its own,
   and [RAD-0006](RAD-0006-development-time-prompt-injection.md) notes it also cuts the
   trusted-prose surface by the same factor. One rule, three benefits.
2. **Dependency scope — `api` versus `implementation`.** Published for 68% of
   Central coordinates in Gradle Module Metadata and recoverable more coarsely
   from POM `<scope>`. The strongest available signal, because it is the
   author declaring what the consumer is meant to use.
3. **Module reach.** 20 modules against 1. A library used across a codebase is
   the one in use; a library in one module is a detail of that module.
4. **Module kind.** `:lint` and `:benchmarks` do not ship. The losing
   candidates were confined to exactly those.
5. **Configuration scope.** The `prod`/`dev` column added to the collectors in
   RAD-0001 separates main classpaths from test and tooling ones.
6. **Distance from root**, as a tie-break where the above do not settle it.

None of these needs a library to say anything, and all are computable from
data the build already produces. That matters because it means selection has a
useful default *before* any adoption of any convention.

What they cannot supply:

- **Relationship kind** — alternative, implementation, bridge, migration.
  Now partly *derivable* rather than merely guessable, from published scope
  plus edge direction, for the two thirds of coordinates carrying Gradle
  Module Metadata. Not settled for the rest, and not settled at all outside
  the JVM.
- **The reason.** "We use kotlinx-serialization because the project is KMP and
  Gson has no multiplatform story" is local knowledge, and it is the sentence
  that actually saves the next developer time.
- **Negative guidance.** "Do not use X for Y here" cannot be derived from a
  graph at all.

### Who may author preference, and how much to trust it

Some of this can be authored in a library's own docs via custom tags — but trust
in an authored signal depends on **who is speaking** and whether they have a stake
in the answer:

- **Self-referential** (`@capability`, `@notFor`) — a library describing what it
  is and is not for. Safe: a statement about itself.
- **Neutral relationship** (`@similar X`) — "this is in the same space as X."
  Relates without ranking, so it feeds overlap **clustering** without letting one
  library disparage another. Safe.
- **Sorting tags** — `@category` (a fixed, governed taxonomy, crates.io-style not
  npm free-text) and `@triggers` (free attention-words). Both are decent
  grouping/retrieval signals; `@category` is trustworthy, while `@triggers` is
  free-form and therefore **gameable** (keyword-stuffing), so it is down-weighted
  and only helps atop a real semantic match, never alone.
- **Interested preference** (`@preferOver X`) — a library ranking itself over a
  named rival. Permitted but **heavily down-weighted and never able to exclude**:
  the author is an interested party, and a tag that lets one library disparage
  another turns the index into a surface for libraries excluding each other.
- **Consumer preference** — "in this project, prefer X over Y," authored by the
  team, not the dependency. **Highest trust**, because the consumer has no stake
  in the contest; it is the un-harvestable local knowledge above, and the piece
  that actually shortcuts the churn.

The line: a library may say what it *is* and neutrally what it is *near*; ranking
across libraries belongs to the disinterested consumer, and an interested
library's `@preferOver` is a weak nudge at most.

### The reviewer's remedy, and where it lands

The suggestion was that the consuming project supply its own `dep_usage.md` —
which is the same conclusion ADR-0004 reaches from the other direction and
which the scratch note calls the team-experience layer. The measurements
support it, with a qualification worth stating: **most of the work is
computable, so the local file should be small.** If a project has to write
down its choice for all 311 importable libraries, nobody will. If the graph
resolves the obvious cases and the file only carries the contested ones — plus
the reasons — it is a page, and it is the page worth writing.

The reviewer's later framing points the same way: a librarian can take account
of why the consuming project pulled a dependency in when deciding which
candidate is meant. Intent of declaration is itself a signal, and it is
local.

### Recovery matters more than accuracy

Mis-selection is the common case rather than the tail — overlap here is real,
not accidental — and the data supports treating it that way rather than aiming
for a correct-first-time ranker. Three consequences worth designing for:

- **Name the alternatives inside the entry the agent lands on**, so a wrong
  landing is self-correcting rather than silent.
- **Make the agent state which library it used and why**, so a human sees the
  wrong turn in the diff rather than in production.
- **Ask, when the signals genuinely tie.** An interactive tool can hand the
  choice back; a static index cannot. This is one of the things
  [RAD-0003](RAD-0003-central-capability-server.md) identifies as specific to the MCP
  form.

## Findings

**Measured.**

- **The selection A/B (RAD-0018, 2026-08-20) confirms the core claim: the local
  preference is unknown to every model, and only an authored standard supplies it
  on an ambiguous classpath.** Across six models (two frontier, four local) × three
  overlapping domains, condition A (task alone) picked the project's sanctioned
  library **0/18** — no model, however capable or current, knows the preference. The
  **declared dependency tree redirected** a single-choice classpath almost universally
  (17/18), but an **ambiguous** classpath (both libs declared) failed (3/18) and only
  the **authored preference resolved it** (17/18) — *including on both frontier models*.
  Unlike drift (closes with freshness) and disambiguation (closes with capability),
  **selection closes with neither** — the one gap model progress cannot close.
- **The JVM's apparent overlap is mostly an artifact of scanning the wrong
  set.** Now in Android's "three JSON libraries" reduces to one once
  restricted to the importable set: `gson` and `moshi` are on no compile
  classpath, having entered through `lintPublish` and `:benchmarks`.
- **Across four public JVM graphs, restricted to importable, there is no
  genuine two-alternatives case.** Every surviving cluster is a stack
  (PetClinic's five logging coordinates), a framework plus its implementation
  (Retrofit/OkHttp, Hilt/Dagger) or one specification at two versions
  (`javax.inject`/`jakarta.inject`).
- **In npm it is pervasive.** The p90 Next.js example has eight capability
  groups with multiple importable members, including `moment` alongside
  `date-fns` and three separate HTTP clients. The two small Python graphs have
  none, which is more likely a size effect than an ecosystem one.
- **Dependency scope is published**: 68% of Central coordinates carry Gradle
  Module Metadata distinguishing api-usage from runtime-usage variants; 78% of
  dependencies are api-exposed. POM `<scope>` carries a coarser version for
  the rest.

**Reasoned.**

- That the difference between the ecosystems is caused by `api`/`implementation`
  plus configuration roles on one side, and a flat `node_modules` on the
  other. The mechanism is documented in both cases; the causal claim is not
  separately tested.
- That relationship kind — alternative, implementation, facade, spec version —
  is the hard part, and that published scope plus edge direction derives much
  of it for the two thirds of coordinates carrying module metadata.
- **Preference authorship has a trust line.** Self-referential (`@capability`,
  `@notFor`) and neutral (`@similar`) author tags are trustworthy; sorting tags
  help ranking (`@category` governed, `@triggers` gameable and down-weighted); an
  interested `@preferOver` is a weak nudge that never excludes; and cross-library
  preference is the disinterested consumer's, weighted highest.

**Unverified.**

- The capability groupings are hand-made and coarse. `guava` and `commons-io`
  were grouped together and should not have been; other real overlaps may have
  been missed entirely because no group named them.
- Four JVM graphs and four npm graphs is a small sample, and absence of
  genuine overlap in four is not evidence of absence generally.
- Whether an agent actually mis-selects when two importable alternatives are
  present. Still unobserved — the `moment`/`date-fns` case is a plausible
  scenario, not a recorded failure.
- The non-JVM rows in the scope-comparison table are read from documentation,
  not measured.

## Recommendation

**Rank against the importable set, not the resolved graph.** This is the
single most consequential thing in this record, and it costs nothing: the
build system has already excluded lint tooling, annotation processors,
benchmarks and every private `implementation` dependency of every library in
the graph. Ranking over everything resolvable manufactures a problem that the
metadata had already solved. It also shrinks the trusted-prose surface that
[RAD-0006](RAD-0006-development-time-prompt-injection.md) is about, for free.

**Use an npm case as the worked example, not a JVM one.** The
`moment`/`date-fns` pair in the p90 Next.js example is public, reproducible
with `npm install`, and compounds two of the three failures — a legacy library
that training data favours, sitting importable beside its recommended
replacement. The Now in Android JSON case should not be used; it does not
survive analysis.

**Record the relationship taxonomy here** — alternative,
implementation of, facade or bridge, specification version, companion — and
that only the first is a decision; it lives in this record until it hardens into
an ADR. Every surviving JVM cluster in this record
was one of the latter four, so an index that reports co-presence without the
taxonomy will confidently offer choices that do not exist.

**Adopt the graph-derived signals as the default ranking**, in order:
importable first, then declared over transitive, then published dependency
scope, module reach, module kind, and distance from root as a tie-break.

**Keep preference authorship on the right side of the trust line.** Trust a
library on what it *is* (`@capability`/`@notFor`) and what it is *near*
(`@similar`); treat `@category`/`@triggers` as sorting signals (the free one
down-weighted, and only atop a semantic match); treat a library's `@preferOver`
as a weak nudge that can never exclude a rival; and weight the consumer's own
"prefer X over Y here" highest of all.

**Say plainly that this is an npm-shaped problem.** The project's framing has
the JVM as the disadvantaged ecosystem. On selection it is the opposite, for
the same reason it was on dependency scope: the JVM publishes more metadata
and its classpath is not flat. A proposal that leads with JVM overlap examples
will be arguing from its weakest case.

**What would change the answer.** Better capability groupings — or an
embedding-based grouping rather than a hand-written one — could surface real
JVM overlap this scan missed, and the hand-made list is the weakest part of
this record. An observed instance of an agent mis-selecting between two
importable libraries would move the whole problem from plausible to
demonstrated; there is still no such observation.

## Connections

- [ADR-0004](../decisions/ADR-0004-librarian-and-codex.md) — the two-layer design; overlap
  as domain, which this record supplies the worked example and taxonomy for.
- [RAD-0004](RAD-0004-external-review-of-the-proposal.md) §3 — where the objection was
  raised, and the `dep_usage.md` suggestion.
- [RAD-0002](RAD-0002-existing-documentation-systems-as-skill-transport.md) — the
  declared-versus-importable measurement that makes the first signal a ~10×
  filter.
- [RAD-0006](RAD-0006-development-time-prompt-injection.md) — the same
  declared-over-transitive rule as a security control.
- [RAD-0003](RAD-0003-central-capability-server.md) — asking the developer when signals
  tie is specific to the interactive form.
- [RAD-0013](RAD-0013-the-codex-entry.md) — the entry these signals and author tags
  populate.
- [RAD-0001](RAD-0001-cost-of-a-skill-per-dependency.md) — the import scan that would
  sharpen all of this, still unmeasured.
