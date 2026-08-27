# Cost of a Skill Per Dependency

RAD-0001 · 2026-08-13 · v1
Keywords: how many dependencies a real project has; cost of one skill per
          library; resident context budget; importable vs declared vs
          resolved; phantom dependencies; BOM constraints miscounted as
          direct; why not one skill per dependency
Measured against: Gradle 9.5.1, JDK 17 and 21, Android SDK platform 35/36;
npm 10.9.7 on Node 22.22.2, registry as of 2026-08-13; SPM pins as checked in;
pip 25 on Python 3.11. Description corpus cloned 2026-08-13. Spec ceiling from
the `agentskills` reference validator at the same date.

## Question

Two halves of one question, prompted by ADR-0004.

**How many libraries is a real project actually working with?** Not how many
it declares — how many an agent would have to know about. The design assumed
"a project has hundreds of dependencies" and had never checked.

**What does that cost under the conventions actually being proposed?** Every
proposal in `docs/knowledge/reference/landscape.md` answers where the file goes; none publishes
what the result costs in resident context at realistic dependency counts.

It matters because ADR-0004's two-layer design rests entirely on the claim
that one skill per library does not scale. If a real project has forty
dependencies, the index is over-engineering and the honest thing is to say so.

A first pass had measured three KMP projects by one author. Nobody can clone
those, so nothing in them could settle an argument with the npm or SPM
communities — which is where the conventions already exist and where the cost
argument has to land.

## Trail

### What was built

A proof of concept in `experiments/cost-model/`, which still runs. Four collectors,
one per ecosystem, all emitting the same shape so the numbers are comparable:

```
<module>  <direct|transitive>  <coordinate>  <prod|dev>
```

Gradle via an init script; npm via `npm ls --all --json` run twice and
differenced for the dev set; SPM by parsing the checked-in `Package.resolved`
and `Package.swift`, so no toolchain is needed and anyone with the repository
can reproduce it; pip via a built virtualenv and `pipdeptree`. Deduplication
is on library name, never name-plus-version — a skill describes a library, not
a release. A fifth collector was added late; see "The metric was wrong".

Subjects were fixed in `experiments/cost-model/subjects.md` before anything ran. Where
a subject named a set rather than one project, the choice was made by rule so
it could not be argued to be flattering: rank every example by declared
dependency count, ties alphabetical, take the median, the p90 and the maximum.

**One deliberate substitution.** `subjects.md` names `vercel/next.js` and
`vitejs/vite`. Their own monorepo graphs are build toolchains, not consumer
graphs, and the population a distribution convention applies to is people who
depend on them. Applications were measured instead. This changes what the
numbers mean, so it is recorded rather than assumed.

### Four things that were wrong

**1. "Direct" was counting BOM constraints.** The first pass took direct
dependencies to be `resolutionResult.root.dependencies`. Spring PetClinic
reported 116 against a build file declaring about 25, and the reported set
included `logback` and `micrometer-*`, which appear nowhere in it.

Measured on Now in Android: of the root's first-level edges, **187 are
constraints** contributed by BOMs and platforms and **85 are real dependency
edges**. All 85 real edges are declared. Of the constraint names, **152 of 154
appear nowhere** in the build files or version catalog — entries like
`androidx.annotation:annotation-jvm` that a BOM pins a version for whether or
not the module ever enters the graph.

Fixed by reading `configuration.allDependencies`. PetClinic's corrected count
of 25 matches its build file line for line. It survived the first pass because
the two definitions agree *exactly* on ordinary classpaths — 15=15, 24=24,
14=14, 22=22 — and diverge only where a plugin synthesises a flat
configuration or a BOM contributes constraints.

**2. The configuration filter dropped the main classpath.** The pattern was
anchored on a capital letter (`CompileClasspath`), so a single-module JVM
project's own lowercase `compileClasspath` and `runtimeClasspath` never
matched and only its `test*` configurations were collected. Surfaced when the
Ktor sample reported zero production dependencies, which is impossible for a
server. Every KMP and Android configuration is prefixed — `jvmMain…`,
`debug…` — and so matched either way, which is why a KMP-only first pass never
saw it.

**3. The description cost came from one author.** "About 110 tokens" was
measured across 18 skills from a single suite. Re-measured across 67 skills
from four public collections — `anthropics/skills`, `mattpocock/skills`,
`cloudflare/skills`, `vercel-labs/skills` — the median is 236 characters, or
about 59 tokens. Roughly half the original figure. The first pass had flagged
this risk in its own caveats and not acted on it.

A near-miss inside the fix: `antfu/skills-npm` was included at first, and its
seven "skills" are test fixtures — "A test skill in pkg-a", 21 to 43
characters. They would have pulled the median down while being evidence of
nothing. A corpus assembled by cloning whatever is available needs looking at,
not just counting.

**4. The metric was wrong, not just the number.** The first three are
collector defects. This one is conceptual and was caught in review rather than
by any check: a JVM or Android developer routinely declares a handful of
libraries and writes code against dozens more, because an `api` dependency
exposes its own dependencies to consumers. The IDE autocompletes them, they
compile, and nothing in the build file mentions them. Relying on that is
normal practice.

So "declared" describes neither what the build says nor what the developer
works with. A fifth collector was written to measure what is *importable* —
everything resolved onto a compile classpath.

### Two smaller ones, caught before publishing

The SPM prod/dev split was initially **guessed**: transitives were marked
production unless referenced from a test target. `Package.resolved` is flat
and records no parentage, so `swift-docc-symbolkit` and
`swift-snapshot-testing` — both reachable only through dev-only parents — came
out as production. Fixed by fetching each pinned dependency's own
`Package.swift` at its pinned revision and deriving the edges, which is
reproducible because the revisions are checked in.

Now in Android would not collect at all until `--no-configuration-cache` was
passed: the init script reads `Project` at execution time, which a cached
configuration cannot provide. Not an error in the results, but any project
with the configuration cache on silently fails to collect, which turns into a
missing row nobody notices.

### What was verified

The three first-pass all-resolved counts reproduce exactly from their raw
TSVs — 59, 970 and 1,318 — using an independently written counter. Direct is a
subset of all resolved, and production a subset of each, asserted across all
twelve public subjects.

## Findings

**Measured.**

The importable set is most of the graph. On the JVM:

| Project | Declared | Importable | Resolved | Importable as % of resolved |
|---|---|---|---|---|
| Ktor sample | 8 | 63 | 65 | 97% |
| Spring PetClinic | 16 | 112 | 113 | 99% |
| Compose MP `codeviewer` | 13 | 238 | 278 | 86% |
| Now in Android | 102 | 311 | 360 | 86% |

A published description costs about 59 tokens at the median of 67 public
skills (236 characters; p90 453; max 1071). The `agentskills` reference
validator caps a description at 1024 characters, so the spec ceiling is about
300 tokens per skill.

Resident cost at full adoption, central to conservative: 0.9k–2.1k for
`swift-composable-architecture`, 7.1k–15.6k for Spring PetClinic, 19.7k–43.4k
for Now in Android, 62.9k–138.7k for a Next.js application at the 90th
percentile of the framework's own examples. At the spec's ceiling that last
figure is 301k, which exceeds any current context window.

Full tables in `experiments/cost-model/findings.md`.

**Derived, not separately measured.** npm and pip make everything installed
importable, because `node_modules` and `site-packages` are flat and any
package present resolves by name. This is the phantom-dependency problem and
it is a property of the install layout; it was reasoned from the layout rather
than tested by importing an undeclared package. pnpm and SPM bound visibility
to declarations for the same class of reason.

**Unverified.** The private KMP rows (`private-1`, `private-2`) were collected
with the faulty direct definition, have no importable figure, and need
recollecting. Adoption rates are a guess in either direction; the tables
assume full adoption and scale linearly below it.

## Recommendation

**The claim holds, and the reason changed.** One skill per library does not
scale — but not because a directory scan pays a ceiling nobody really faces
while a declared set stays cheap. There is no cheap set. In every ecosystem
except Swift's and pnpm's, the developer-visible set is nearly the whole
graph.

**Split what the field offers into three, and adopt two of them.** The format
(`SKILL.md`, `name` + `description`) is adoptable as-is; inventing an
alternative is exactly the v1 mistake. The directory layouts are adoptable
wherever a tree exists. The loading model — scan a directory, make everything
found resident — cannot be adopted anywhere, including npm: a scan cannot load
less than what is on disk, and nothing in any spec bounds the aggregate.

**The index must read the resolved declared graph, not a directory listing.**
This is the operative consequence for ADR-0004, and it promotes the index from
a cost optimisation to the load-bearing part of the design.

**What would change the answer.** If the importable set is the wrong number —
if agents in practice only ever reach for declared dependencies — the floor
comes back and the index is less urgent. If adoption stays in the low single
digits indefinitely, the arithmetic never binds. And the one measurement that
would most change the shape is unmade: which of the importable libraries a
project's source actually references. That needs an import scan, it is the
difference between 20k tokens and something smaller for a project of Now in
Android's size, and it is the only signal that narrows the set further.

**Not yet committed.** The three-way split above is a recommendation, not a
decision. ADR-0007 currently commits to conforming to conventions this project
does not control without drawing the boundary at the loading model; if this
recommendation hardens, that boundary belongs in an ADR — either an amendment
to 0007 or a new record cross-linked to this one.

## Connections

- `experiments/cost-model/` — the proof of concept. Still runs. `README.md` poses the
  question as it stood before anything was measured, `findings.md` carries the
  full tables, `scripts/` the five collectors, `data/` the raw graphs.
- ADR-0004 — the two-layer design this
  tests.
- [ADR-0007](../adr/0007-conform-to-existing-conventions.md) — conformance,
  and where this recommends drawing its boundary.
- [landscape.md](../reference/landscape.md) — the conventions whose cost was
  modelled.
