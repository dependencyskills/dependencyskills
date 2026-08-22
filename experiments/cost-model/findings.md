# Findings: measured dependency counts and what they cost

Measured 2026-08-13, in two passes. The first covered three KMP projects by
one author. The second added twelve public projects across npm, SPM, Python
and the JVM, found a defect in the collector the first pass had used, and —
more importantly — established that *declared* dependencies are the wrong
floor. See "The floor is not what the build declares" below.

**Measured against:** Gradle resolution as of 2026-08-13 (Gradle 9.5.1, JDK 17
and 21, Android SDK platform 35/36); npm 10.9.7 on Node 22.22.2, registry as
of 2026-08-13; SPM pins as checked in; pip 25 on Python 3.11. Description
lengths from 67 skills across four public collections, cloned 2026-08-13;
spec ceiling from the `agentskills` reference validator at the same date.

## Three numbers, not two

The first pass recorded two: **declared** (what the build file names) and
**all resolved** (every module in the graph). It presented declared as the
floor and treated it as the honest headline.

That is wrong for the JVM, and wrong in the direction that matters. A JVM or
Android developer routinely declares a handful of libraries and writes code
against dozens more, because an `api` dependency exposes its own dependencies
to consumers — the IDE autocompletes them, they compile, and nothing in the
build file mentions them. Relying on that is normal practice, not an accident.

So the number that describes what a developer is actually working with is
neither of the two recorded. It is **importable**: everything resolved onto a
compile classpath.

| Project | Declared | Importable | All resolved | Importable as % of resolved |
|---|---|---|---|---|
| Ktor sample, median | 8 | **63** | 65 | 97% |
| Compose Multiplatform `codeviewer` | 13 | **238** | 278 | 86% |
| Spring PetClinic | 16 | **112** | 113 | 99% |
| Now in Android | 102 | **311** | 360 | 86% |

Production configurations only; test and build tooling excluded. Collected
with `scripts/collect-visible.init.gradle`, which is in this directory.

**On the JVM the importable set is 86–99% of the entire resolved graph.** The
declared count understates what a developer can reach by 3x to 18x. A Compose
Multiplatform example that names 13 dependencies puts 238 within reach.

## The floor is not what the build declares

This changes the conclusion of the first pass rather than refining it.

The earlier framing was: the floor (declared) is cheap, the ceiling (all
resolved) is an arithmetic limit nobody would actually pay, and the truth is
somewhere between. The middle was left unmeasured.

Measured, the middle is not in the middle. It sits against the ceiling. For
these four projects the cost of a skill per importable library is:

| Project | Declared | Importable | Cost of the importable set |
|---|---|---|---|
| Ktor sample | 8 | 63 | ~4.0k tokens |
| Spring PetClinic | 16 | 112 | ~7.1k |
| Compose Multiplatform `codeviewer` | 13 | 238 | **~15.0k** |
| Now in Android | 102 | 311 | **~19.6k** |

The ceiling was never merely arithmetic on the JVM. It is very close to the
real number, and the gap between "what you declared" and "what you can call"
is where the cost lives.

What is still not measured is which of the importable libraries a project's
source actually references. That needs an import scan, and it is now the
single most valuable thing left to measure — it is the difference between
~20k tokens and something smaller, for a project of Now in Android's size.

## Correction: the first pass over-counted declared dependencies

Separately, and not to be confused with the point above: the first pass took
declared dependencies to be the first-level edges of the resolution result,
`resolutionResult.root.dependencies`. That number was too high, but **not**
because it was capturing importable transitives. It was counting version
constraints.

Measured on Now in Android, which is public and can be checked: of the root's
first-level edges, **187 are constraints** contributed by BOMs and platforms
and **85 are real dependency edges**. All 85 real edges are declared in the
build. Of the constraint names, 154 are declared nowhere — entries like
`androidx.annotation:annotation-jvm` that a BOM pins a version for whether or
not the module ever enters the graph.

So the old number was a BOM constraint list. It was larger than the declared
set, and it was not the importable set either; the two answer different
questions and neither is `root.dependencies`. The collector now reads
`configuration.allDependencies` for declared, and the new
`collect-visible.init.gradle` for importable.

On Spring PetClinic the old definition yields 116 against 25 declarations;
the corrected count of 25 matches its `build.gradle` line for line.

**Consequence for the first-pass rows.** The `private-1` and `private-2`
declared counts below are over-stated by an unknown factor, and neither
project has an importable figure at all. Both need recollecting with both
scripts. The all-resolved counts are unaffected — that side never used the
faulty definition, and all three reproduce exactly from the raw TSVs.

A second defect was fixed in the same pass: the configuration filter was
anchored on a capital letter (`CompileClasspath`), so a single-module JVM
project's own `compileClasspath` and `runtimeClasspath` were skipped and only
its `test*` configurations collected. Every KMP and Android configuration is
prefixed (`jvmMain…`, `debug…`) and so was matched either way, which is why
the first pass did not show it.

## How far the importable set reaches is a property of the packaging system

The JVM result does not transfer, and the differences are large enough to
change which ecosystems the cost argument applies to.

| Ecosystem | What is importable without declaring it | Why |
|---|---|---|
| Gradle / Maven | transitives exposed by `api` edges — measured at 86–99% of resolved | compile classpath carries the whole `api` closure |
| npm, yarn (flat) | **everything installed** | `node_modules` is hoisted and flat; any package there resolves by name |
| pip | **everything installed** | `site-packages` is flat; any distribution there imports |
| pnpm | declared only | nested store, symlinked per package — the layout that prevents it |
| SPM | declared only | only a declared package's products are visible to a target |

For npm and pip this is the phantom-dependency problem, and it means their
importable set is not 86% of resolved but 100% of it. The `create-next-app`
default puts 423 packages within reach — 26.6k tokens. The p90 Next.js
example puts 995 within reach — 62.7k tokens, before a file of the project
has been read.

SPM is the outlier in the other direction: 15 declared, 17 resolved, and only
the 15 importable. The ceiling argument genuinely does not apply to Swift,
which `subjects.md` asked to have published either way.

## Two measured inputs

**A description costs about 60 tokens, not 110.** The first pass measured 18
skills from a single suite: median 483, mean 439 characters. Measured instead
across **67 skills from four public collections** — `anthropics/skills`,
`mattpocock/skills`, `cloudflare/skills` and `vercel-labs/skills` — the
distribution is materially cheaper:

| | min | p25 | median | p75 | p90 | max |
|---|---|---|---|---|---|---|
| description, characters | 51 | 125 | **236** | 336 | 453 | 1071 |

At ~4 characters per token the median is ~59 tokens. The earlier 110-token
figure came from one author's house style and is roughly double what the
published corpus actually uses. Skill *names* average 13 characters.

The `agentskills` reference validator caps a description at **1024
characters** (`MAX_DESCRIPTION_LENGTH`), so the spec ceiling is about 300
tokens per skill. One skill in the corpus already exceeds it at 1071.

Seven skills in `antfu/skills-npm` were excluded: they are test fixtures
("A test skill in pkg-a"), and at 21–43 characters they would have dragged
the distribution down without being evidence of anything.

**Dependency counts, all three ends.** Declared is a floor nobody works at,
importable is what the developer can call, all resolved is the graph.

## What the conventions actually cost

Every convention in `landscape.md` works the same way at load time: a skill's
`name` and `description` are resident for every installed skill, and the body
is read only when the skill is invoked. So the resident cost is the number of
skills present multiplied by the per-entry cost — and the number of skills
present is decided by whatever the agent scans, not by what the project
declared.

Per-entry resident cost, using the corpus above plus name and list overhead:

| | description | ÷ chars/token | per skill |
|---|---|---|---|
| optimistic | p25, 125 ch | 4.5 | **32 tokens** |
| central | median, 236 ch | 4.0 | **63 tokens** |
| conservative | p90, 453 ch | 3.5 | **139 tokens** |
| spec ceiling | 1024 ch | 3.5 | 303 tokens |

Applied to the measured graphs, at full adoption — every library in scope
ships a skill:

| Project | Skills in scope | Optimistic | Central | Conservative | Conservative as % of a 200k window |
|---|---|---|---|---|---|
| `swift-composable-architecture` | 15 | 0.5k | 0.9k | 2.1k | 1% |
| Django service | 46 | 0.3k | 2.9k | 6.4k | 3% |
| FastAPI service | 82 | 0.3k | 5.2k | 11.4k | 6% |
| `create-vite` react-ts | 84 | 0.3k | 5.3k | 11.7k | 6% |
| Ktor sample | 63 | 0.3k | 4.0k | 8.8k | 4% |
| Next.js example, median | 73 | 0.3k | 4.6k | 10.2k | 5% |
| Spring PetClinic | 112 | 0.8k | 7.1k | 15.6k | 8% |
| Compose MP `codeviewer` | 238 | 0.4k | 15.1k | 33.2k | 17% |
| Now in Android | 311 | 2.7k | **19.7k** | **43.4k** | 22% |
| `create-next-app` default | 423 | 0.5k | 26.8k | 59.0k | 29% |
| Next.js example, largest | 919 | 1.3k | 58.1k | 128.1k | 64% |
| Next.js example, p90 | 995 | 0.5k | **62.9k** | **138.7k** | **69%** |

"Skills in scope" is the importable set — measured for Gradle, and equal to
everything installed for npm and pip because their layouts are flat. The
optimistic column is the *declared* count instead, which is the point below.

**The optimistic column is not a possible outcome of the conventions as
written.** It assumes only declared libraries contribute a skill. But every
distribution convention in `landscape.md` — `.agents/skills/`,
`skills/<name>/SKILL.md`, mise's symlink-on-install — is a *directory scan*.
The agent loads what is present in the tree, and what is present is the
resolved graph, not the declared subset. Reaching the optimistic column
requires something that knows which dependencies were declared and filters on
it. Nothing in the landscape proposes that except an index, which is what
ADR-0004 argues for.

So the honest reading is: **the conventions as proposed deliver the central
and conservative columns.** For a mid-sized Android application that is 20k–43k
tokens resident before any work begins. For a Next.js application at the 90th
percentile of the framework's own examples it is 63k–139k, and at the spec's
1024-character ceiling it is 301k — more than any current context window.

Adoption scales this linearly and is the one honest reason the numbers might
be smaller in practice: at 10% of libraries shipping a skill, take 10% of the
column. That is a prediction about uptake, not a property of the design, and
it gets worse over time rather than better.

**Two corrections that move in opposite directions.** The first pass used 110
tokens per description against a declared count inflated by BOM constraints.
The description figure was about 2x too high; the count was too low, because
declared was the wrong set. Correcting both, Now in Android moves from a
"floor" of 66 × 110 ≈ 7k to a realistic 311 × 63 ≈ 20k. The direction of the
argument is unchanged and the magnitude is larger.

## First pass — KMP, one author

| Project | Gradle modules | Declared | All resolved |
|---|---|---|---|
| `aughtone-types` — small KMP library | 1 | 7 † | 59 |
| private-1 — application, early | 18 | 66 † | 970 |
| private-2 — application, large | 51 | 370 † | 1,318 |

† Collected with the faulty definition. Recollect, and collect importable too,
before quoting.

## Second pass — twelve public projects, four ecosystems

Every row is a public project anyone can clone and re-measure. Counts are
unique library names, not name-plus-version. `prod` excludes development and
test dependencies; the unqualified column is everything an agent working in
the repository would see.

| Project | Ecosystem | Declared | (prod) | All resolved | (prod) | Cost at 63 tok |
|---|---|---|---|---|---|---|
| `swift-composable-architecture` | SPM | 15 | 13 | 17 | 13 | 1.1k |
| Django service, 7 declared | pip | 9 | 6 | 46 | 31 | 2.9k |
| FastAPI service, 7 declared | pip | 9 | 6 | 82 | 68 | 5.2k |
| `create-vite` react-ts | npm | 9 | 2 | 84 | 3 | 5.3k |
| Next.js example, median | npm | 10 | 7 | 73 | 67 | 4.6k |
| `create-next-app` default | npm | 17 | 9 | 423 | 62 | 26.6k |
| Next.js example, p90 | npm | 16 | 14 | 995 | 962 | **62.7k** |
| Next.js example, largest | npm | 42 | 15 | 919 | 344 | 57.9k |
| Ktor sample, median | Gradle | 10 | 8 | 98 | 65 | 6.2k |
| Spring PetClinic | Gradle | 25 | 16 | 173 | 113 | 10.9k |
| Compose Multiplatform `codeviewer` | Gradle/KMP | 12 | 12 | 278 | 278 | 17.5k |
| Now in Android | Gradle/Android | 85 | 76 | 405 | 360 | 25.5k |

Subjects were fixed in advance in `subjects.md`. Where a subject named a set
rather than one project, the choice was made by rule rather than by eye: the
Next.js and Ktor examples are the median, p90 and maximum of all 224 and 30
examples respectively, ranked by declared dependency count, ties broken
alphabetically. Nothing was picked for its number.

## Why the current specifications cannot be adopted wholesale

ADR-0007 commits this project to conforming to conventions it does not
control. The measurements do not overturn that, but they do split it: there
are three separable things in the field, and they are adoptable to very
different degrees.

**The format is adoptable as-is.** `SKILL.md`, frontmatter requiring `name`
and `description`, optional `scripts/`, `references/`, `assets/`. Nothing
measured here argues against any of it. Conform, and do not invent an
alternative.

**The directory layouts are adoptable wherever a tree exists.**
`.agents/skills/<name>/SKILL.md` under `node_modules/` or `site-packages/`
works, and being the third convention in circulation is worse than being the
second. The JVM is excluded structurally rather than by preference — nothing
unpacks — which is ADR-0003's whole subject.

**The loading model is the part that cannot be adopted, and it is not
ecosystem-specific.** Every convention in `landscape.md` discovers skills by
scanning a directory, and everything discovered is resident. That couples
resident context to the *resolved* graph, and the measurements say what that
costs:

1. **A scan cannot load less than what is on disk.** The importable set is
   86–99% of the resolved graph on the JVM and 100% of it in npm and pip.
   There is no declared-only subset for a directory scan to find, because the
   directory does not record which packages were declared. Adopting the
   discovery model means adopting 311 resident entries for Now in Android and
   995 for a Next.js application at the 90th percentile of the framework's own
   examples — 20k and 63k tokens at the corpus median, before any work starts.

2. **The spec bounds the wrong axis.** `MAX_DESCRIPTION_LENGTH = 1024`
   constrains a single skill and nothing constrains the total. A 1024-character
   description is unremarkable at fifteen skills and catastrophic at 995: the
   same p90 Next.js graph costs 301k tokens at the cap, which exceeds any
   current context window. A per-item limit with no aggregate budget is not a
   budget.

3. **The field's only answer to scale is unowned.** `skilld`'s guidance is to
   be selective and add skills only for packages the agent struggles with.
   That is the correct instinct and it puts the work on the consumer, by hand,
   per project — which is precisely the "adoption step in front of the
   discovery mechanism" that pnpm RFC #13422 objects to, and which this
   project's own v1 postmortem concedes against itself. It also does not
   survive the numbers: nobody hand-curates 311 candidates, and re-curates
   them at every dependency bump.

**The useful corollary is that viability is a property of the package
manager's layout, not of the convention.** SPM and pnpm bound what is visible
to what was declared — nested store, no hoisting — so for them the scan *is*
the declared set and the conventions work unmodified. `swift-composable-architecture`
comes to 15 entries and about 0.9k tokens. npm's flat `node_modules` and
Python's flat `site-packages` do not bound it, and the JVM does not expose a
tree at all. So the same specification is sound in one ecosystem, expensive in
two, and inapplicable in a third, for reasons that have nothing to do with how
the specification is written.

**What that leaves.** Adopt the format and the layout; replace the loading
model with one that reads the resolved declared graph and puts descriptions
behind an index rather than resident. That is ADR-0004, and the measurement is
what moves it from an optimisation to the load-bearing part of the design.
ADR-0007 says conform to conventions we do not control; this is the boundary
of that commitment, and it may warrant recording as an amendment.

## Caveats, which matter more than the numbers

**All-resolved counts are a union across every resolvable configuration** —
compile and runtime, every KMP target, and test and build tooling. An agent
working on one target sees fewer. The importable figures are narrower on
purpose: production configurations only.

**Importable is measured for Gradle and derived elsewhere.** The four JVM
rows come from resolving compile classpaths. The npm, pip, pnpm and SPM rows
in the packaging-system table are properties of each tool's install layout,
not separate measurements. They are checkable — a flat `node_modules` either
resolves an undeclared package or it does not — but this pass did not run
that check.

**Most transitive modules would never ship a skill.** A large fraction are
internal fragments of larger libraries — stdlib pieces, androidx internals,
annotation processors. Being importable is not the same as being something a
developer would import. That is the gap an import scan would close.

**Version deduplication changes the count.** Unique `group:name` is what
matters for skills, since a skill describes a library rather than a
version; counting `group:name:version` gives higher numbers because
different configurations resolve different versions. Every table uses unique
names.

**The dev/prod split is declared in npm and derived everywhere else.** Gradle
has no dev flag, so it is taken from the configuration name. SPM has none
either, so it is derived by fetching each pinned dependency's own manifest and
asking whether any path to it from the root avoids a test target or plugin;
the revisions are in `Package.resolved`, so that derivation is reproducible.
Python's split is only as good as a project's separation of its requirements
files, and the two Python rows use requirement lists written for this
measurement rather than taken from a real project — treat them as indicative
of a stack, not as a measured project.

**The npm subjects are consumers, not frameworks.** The `vercel/next.js` and
`vitejs/vite` monorepos were deliberately not measured: their graphs are their
own build toolchains, and the population the convention applies to is people
who depend on them. This was a decision, not an oversight.

**Two subjects in `subjects.md` were not collected.** A mid-sized Ktor service
was substituted with the median `ktor-samples` sample, and no Ktor library
graph was taken. Nothing else on the list was skipped.

## What this changes

The index has to read the resolved graph rather than a directory listing —
that conclusion survives, and is stronger. But the reason has changed. It is
not that scanning a tree pays a ceiling nobody really faces while the declared
set is cheap. It is that on the JVM the developer-visible set is 86–99% of the
graph, and in npm and pip it is all of it, so there is no cheap floor to
retreat to in any ecosystem except Swift's.

What that leaves is selection, which is the third of the three failures the
README names. If a project of Now in Android's size puts 311 libraries within
reach and a convention cannot afford 34k tokens of descriptions, then
something has to choose which of the 311 matter — and the only signal that
narrows it further is what the source actually imports. That measurement is
the next thing worth doing, and it is now the load-bearing one.
