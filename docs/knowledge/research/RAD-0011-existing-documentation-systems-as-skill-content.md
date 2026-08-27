# Existing Documentation Systems as Skill Content

RAD-0011 · 2026-08-19 · v2
Keywords: is KDoc actually rich enough to be worth harvesting; how much library documentation exists; whether @sample bodies travel; custom tags; the Swift DocC catalog; Go's coverage with no tags; TSDoc; what a doc comment gives a caller; documentation coverage per ecosystem.

**Measured against:** `kotlinx-datetime` 0.8.0 sources for the KDoc content and
tag measurement; a 90-coordinate coverage sample (seeded, one version per
`group:artifact`) from the four public graphs in `experiments/cost-model/data/`,
stratified across Maven Central and Google Maven; Go 1.25.0 module cache for the
`cli/cli` build list; npm tree from `next-cms-tina`; Python from a fresh
virtualenv; `swift-composable-architecture` at its checked-in `Package.resolved`
for the DocC catalog. DocC and TSDoc behaviour per vendor documentation. All
2026-08-13.

**Split note.** Split out of [RAD-0002](RAD-0002-existing-documentation-systems-as-skill-transport.md)
on 2026-08-17. RAD-0002 settled the *transport* question — a carrier
(`-sources.jar`) already travels with the library. This record is the *content*
question: is what travels rich enough to *be* skill content, how much of it
exists, and what shape does an author's extra guidance take? Get is settled;
this is where the open work is.

**v2 (2026-08-19).** Adds the `@sample` reachability finding — the dominant KDoc
tag, whether its *body* actually travels in the `-sources.jar`, and why a
*resolved* sample (Dokka) is worth more than a raw one. Prompted by the
`experiments/test0` first pass (RAD-0009 v4).

## Question

Given that the source already reaches the consumer (RAD-0002), and the tooling to
parse it exists and runs at IDE scale (RAD-0009):

1. **Is KDoc rich enough** to be the source of skill content, rather than asking
   authors to write a separate `SKILL.md`?
2. **How much documentation actually exists** to harvest, as distinct from how
   widely the carrier is published?
3. **Custom tags:** authors need to add guidance no doc system has a tag for. Can
   KDoc carry it, and what do other ecosystems do?

This RAD recommends; it does not commit. It is the half of the pivot with real
open questions.

## Trail

### Is KDoc actually rich enough

`kotlinx-datetime` 0.8.0 sources, measured: **614 KDoc blocks, 232 KB, 37% of the
source by bytes.** Block size median 211 characters, p90 844, max 5804. Tag usage,
in order: `@sample` 362, `@see` 161, `@throws` 123 — the dominant tag is a pointer
to compiled, tested example code, the second is a cross-reference. This is
orientation, not parameter-listing boilerplate.

A representative block already supplies, against `spec/content.md`'s
requirements: when to reach for the API, the intended usage pattern, a trap that
looks reasonable ("please consider adding a `Duration` instead"), the alternative
to prefer, and an executable `@sample`. **What it does not and cannot supply is
the cross-library comparison** — "we use this date library rather than the other
one on your classpath, here is why." No library knows what else is on the
consumer's classpath. That is exactly the local knowledge the codex holds and no
harvest can (RAD-0007, RAD-0010).

### `@sample`: the dominant tag, and whether it actually reaches us

`@sample` is the most-used KDoc tag (362 uses in `kotlinx-datetime`) — a
fully-qualified reference to a function whose *body* Dokka renders as a worked
example. It is high-value content: real, compiled, test-able "how to use this,"
anchored to the exact declaration it documents. Two things complicate harvesting it.

**The body may not travel.** `@sample` targets live in a **separate samples source
set**, which is **not normally packaged into the published `-sources.jar`** (that
carries `main`). A harvester reading only the `-sources.jar` therefore sees the
*reference* but not the *function* to expand — the sample is unresolvable from the
carrier alone. The body needs another route: the samples source via the git/scm
coordinate (RAD-0015's remote read), or an author who ships samples in what
publishes. This also makes test0's `@sample` result **optimistic** — its samples
sit in the same tree, so the enriched arm resolves them; a real `-sources.jar`
harvest likely could not (RAD-0009 v4).

**A resolved sample decomposes into three kinds of reference; a raw one is opaque.**
A sample body mixes (1) the documenting library's **own** API — the capability it
illustrates; (2) calls to **other libraries that are also in the codex** — the
author demonstrating composition against real dependencies; and (3) made-up
**scaffolding** (a stubbed `fetchUser`, a fake endpoint) that is just noise.
Because `@sample` is anchored to the function it documents we already know *which*
capability it is about, and because Dokka **resolves** every reference we can sort
the body into those three: keep (1) as the worked example; **turn (2) into
cross-library edges** — an authoritative "these capabilities are used together,"
which is exactly the composition/relationship signal RAD-0007 found otherwise hard
to harvest, and it comes straight from the author's own example; and drop (3). Raw
text cannot make any of these splits. Two concrete, sample-specific reasons the
enriched (Dokka) resolution earns its place, beyond the marginal delta test0
measured on plain prose. It also nudges the codex toward a **graph** — entries
linked by co-use in samples — not a flat index (RAD-0013).

### The cost trap

Whole-library KDoc for `kotlinx-datetime` is roughly **59.5k tokens** — one
mid-sized library. So KDoc answers *what to say* and makes the budget problem
worse, not better. It is body content, never resident, and it needs something in
front of it that decides whether to open it at all. **The pivot strengthens the
case for the index, not replaces it.**

### How much KDoc is actually out there

90 coordinates sampled; 83 sources jars downloaded, 72 yielded parseable
declarations. Coverage is the share of public declarations immediately preceded
by a doc comment (approximate, line-based, undercounts comments above an
annotation block).

| | median | p25 | p75 | >25% | >50% |
|---|---|---|---|---|---|
| **All (n=72)** | **33%** | 20% | 69% | 69% | 32% |
| Maven Central (n=36) | 33% | 17% | 81% | 61% | 39% |
| Google Maven (n=36) | 34% | 29% | 56% | 78% | 25% |
| **Kotlin-majority (n=50)** | **30%** | 17% | 39% | 62% | **12%** |
| **Java-majority (n=22)** | **84%** | 69% | 92% | 86% | **77%** |

Across a real graph the median library documents **a third** of its public
declarations; only 32% document more than half. `kotlinx-datetime` was not
representative.

**The Java/Kotlin split is the finding, and it is cultural, not technical.**
Java-majority libraries document 84% at the median; Kotlin-majority 30%, only 12%
clearing 50%. The tooling is equivalent — Dokka consumes KDoc as javadoc consumed
Javadoc — so the gap is convention. Java library culture treated a published API
as a documentation surface, enforced by tooling and checkstyle. Kotlin arrived
into a "code is self-documenting, comments are a smell" norm — right for
application code, wrong for a library, which exists to be consumed by someone who
cannot read it. So the low number is not evidence KDoc cannot carry this; it is
evidence nobody has had a reason to write it. (`@sample`: 19 of 50 Kotlin
libraries use it, 0 of 22 Java ones *can* — Javadoc has no equivalent.)

### The constraint: custom tags

KDoc documents a fixed tag set and **no custom-tag mechanism is documented**;
neither KDoc nor Dokka states what happens to an unrecognised tag. One indirect
data point: `kotlinx-datetime` ships `@throw` (a typo) in a released artifact, so
an unknown tag does not fail a build — but whether Dokka renders, drops, or warns
on it is **unverified, and load-bearing.** It must be settled by experiment
before anything depends on custom tags.

| Ecosystem | Doc system | Custom tags |
|---|---|---|
| Kotlin/JVM | KDoc + Dokka | Fixed set; no documented extension point |
| TypeScript | TSDoc | **First-class** — `tsdoc.json` with `tagDefinitions`, shareable via `extends` |
| JavaScript | JSDoc | Custom tags via plugins, widely done |
| Python | docstrings + Sphinx | Very extensible — custom roles and directives |
| Go | doc comments | **No tags at all** — conventions carry the meaning |
| Swift | DocC | Directive set fixed; **the extension point is the article**, not a tag |

TSDoc is the model on the *tag* axis. But Swift and Go suggest the tag axis may
be the wrong one — see below.

### Swift: the artifact this project wanted to invent already exists

DocC compiles `///` comments together with a **documentation catalog** — a
`.docc` directory of markdown articles, extensions and resources *in the package
source tree*, generated locally on demand and already on the consumer's disk
because SPM ships source. Measured on `swift-composable-architecture`:

| | files | ~tokens |
|---|---|---|
| Whole catalog (markdown) | 74 | ~82k |
| `Articles/` | 32 | ~73k |
| — `MigrationGuides/` | **19** | ~22k |
| `Extensions/` (per-symbol prose) | 41 | ~8.5k |

The `Articles/` list reads like a skill-body spec written by someone who never
heard of this project: `GettingStarted`, `FAQ`, `Performance`, `TestingTCA`,
`DependencyManagement`, `Navigation`. **And nineteen `MigratingTo1.x` guides** —
which is `spec/content.md`'s drift requirement (state what moved, what it was
called, which version changed it), already an established Swift convention,
shipped inside the package. The author-extension point is not a tag — it is **an
article**: arbitrary markdown in a standard layout linked into the symbol graph.
Kotlin has a weaker form in Dokka's `includes` (`# Module` / `# Package`).

### Go: the highest coverage measured, with no tags at all

Across the full 463-module `cli/cli` build list, resolved into the module cache
(which is source, so this is what is literally on a Go developer's disk):

| | |
|---|---|
| readable source on disk | **463 of 463 — 100%** |
| doc comments on declarations | **462 of 463 — 100%** |
| ship a `doc.go` | 44% |
| ship `Example` functions (compiled, run by `go test`) | 42% |
| carry `// Deprecated:` | 40% |

**The highest documentation coverage of any ecosystem measured — 100% against
npm's 47% — achieved without tags, directives, or a catalog format.** The three
mechanisms map onto what this project specifies: `doc.go` is the article,
`Example` is `@sample` but stronger (a stale one fails the build), `// Deprecated:`
is the drift marker — **a recognised prose convention, not a tag**, which
`gopls`, `staticcheck` and `pkg.go.dev` all surface, and which reached 40%
adoption with no specification behind it.

### Docs are already on disk everywhere — the content is there, the index is not

| Ecosystem | In-source doc comments | Curated prose | Executable examples |
|---|---|---|---|
| Go | **100%** of 463 | `doc.go` — 44% | `Example`, compiled+tested — 42% |
| pip | docstrings — **97%** | — | — |
| Swift/SPM | `///` — 11% of TCA | **`.docc` catalog** | `@Snippet` |
| Kotlin/JVM | KDoc — 37% of one lib | Dokka `includes` | `@sample` — 362 in one lib |
| npm | TSDoc/JSDoc — **47%** | README only | — |

What is missing everywhere is not the content — it is an index over it. npm's 47%
is not a design weakness: participation has always been optional. Two things
follow.

**Partial participation is what makes the budget survivable.** Weighting resident
cost by each ecosystem's documentation rate, the worst npm case (Next.js p90, 995
packages, 62.7k full) halves to **29.5k**. Still an index problem, but the
ceiling stops being an argument-ender.

**An ecosystem that consumes documentation creates the incentive to write it.** A
library whose docs let an agent use it correctly gets reached for; one without
gets reinvented around. That pressure does not exist today — a reasonable
explanation for npm at 47% and Go, where the tooling has made docs load-bearing
for twenty years, at 100%. Coverage measures the current incentive, not a
ceiling.

Two requirements fall out:

- **The index must cover the whole graph, undocumented parts included.** If
  absence is invisible, the undocumented library is not filtered out — it is
  reinvented around (failure one). So index over the resolved graph; *populate*
  by whoever participates.
- **Natural selection optimises for the libraries that need it least.** Value
  runs inversely to training exposure (small, new, private libraries benefit
  most) — and those are the least documented and least able to win a selection
  contest. Market pressure alone leaves the high-value tail uncovered.

### Designed vs discovered

The distinction almost every open question resolves against:

| | **Designed** — the library opts in | **Discovered** — harvested from what exists |
|---|---|---|
| Source | version-addressed file, or capability guidance in KDoc | `-sources.jar`, doc comments, `doc.go`, `.docc` |
| Coverage | whatever adopts; starts at zero | 82–100% of a graph today |
| Quality | authored for a caller; can be required complete | median 33% documented |
| Verifiable | yes — a completeness check can gate it | no, best effort |
| Injection posture | instruction-shaped by intent, from an opt-in publisher | incidental prose; instruction-shaped text is anomalous |

Complementary, not competing: discovery gives whole-ecosystem coverage from day
one with no adoption; design gives quality and verifiability where someone cared.
**An agent should be told which tier a claim came from**, because the trust
differs — the same labelling RAD-0003 needs for library-vs-team data. It also
localises the coverage problem: the 33% figure is about the *discovered* tier,
and the completeness check belongs to the *designed* one.

## Findings

**Measured.**

- KDoc in a real library is 37% of source by bytes, dominated by `@sample`/`@see`,
  and carries intent, traps and in-library alternatives.
- Whole-library KDoc is ~59.5k tokens for one mid-sized library.
- **Coverage across a real graph is thin: median 33% of declarations, 30% for
  Kotlin, against 84% for Java-majority — the gap is cultural, not capability.**
- Go has **100%** doc-comment coverage across 463 modules with no tag vocabulary;
  its article/sample/drift mechanisms (44%/42%/40%) are conventions, not syntax.
- 99% of npm and 100% of Python distributions ship readable source; 47% and 97%
  carry doc comments.
- **Swift ships a curated DocC catalog inside the package** — TCA's is ~82k
  tokens including 19 per-version migration guides, which is the drift
  requirement already conventional.
- KDoc has a fixed tag set, no extension point; TSDoc has first-class custom
  tags; DocC's and Dokka's extension point is the article, not a tag.

**Unverified and load-bearing.** What Dokka does with an unrecognised block tag.
Settle by experiment before designing around custom tags.

## Recommendation

**Content source is KDoc (and its per-ecosystem equivalents), harvested — the
discovered tier.** It carries what a library knows about itself: purpose, usage,
traps, in-library alternatives, executable samples, versioned with the artifact.

**Keep both skills.** `to-library-skill` survives, its job changed from "write
the skill" to "carry what KDoc cannot express, and declare the tags for it." The
librarian and codex survive unchanged and are *more* necessary — one library is
~59.5k tokens; cross-library selection is knowledge no library holds.

**Make designed-vs-discovered a first-class concept, and label every claim's
tier.** Discovery for coverage from day one; design for quality where someone
opts in; a completeness check gates the designed tier.

**Specify a convention, not a schema.** Go is the counter-example to the
custom-tag framing: no tags, highest coverage. The pattern across Go/Swift/Kotlin
is *authors write prose in a place tooling knows to look*; the per-ecosystem
question is only *which place* — the ADR-0007 shape applied to content. **Treat
the DocC catalog as the model**, not just prior art: it already layers per-symbol
detail, curated articles, and per-version migration guides in a standard
location. It is missing only the index.

**Index the whole graph anyway; coverage rates are an input to the cost model,
not a threshold to clear.**

**What would change the answer.**

- If Dokka rejects or silently drops unknown tags with no extension point, the
  authored layer must carry more and the content half weakens.
- **Coverage came back thin** (33%, 30% Kotlin): harvesting KDoc today yields far
  less than the 93–98% transport rate implies, and a first version should promise
  accordingly. This is the thing to *test* — the value spike: does a codex built
  from real, thin, harvested content change agent behaviour?
- Whether Kotlin's documentation rate moves once there is a reason to write KDoc.
  The Java comparison says the ceiling is far above 30%; nothing says how fast.

## Connections

- [RAD-0002](RAD-0002-existing-documentation-systems-as-skill-transport.md) — the
  transport half; get is settled, this is content.
- [RAD-0009](RAD-0009-reusing-indexers-and-what-to-index.md) — the parse tooling that
  extracts this content; the scope fork (index everything vs opt-in) is the
  designed/discovered question in another guise.
- [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) — where the harvested
  content is stored, and the local preference no harvest can supply.
- [RAD-0003](RAD-0003-central-capability-server.md) — the query layer, which also
  needs the designed/discovered trust label.
- [RAD-0001](RAD-0001-cost-of-a-skill-per-dependency.md) — the cost model the
  ~59.5k-token figure feeds.
- [RAD-0006](RAD-0006-development-time-prompt-injection.md) — harvesting prose from
  112–995 libraries; the injection posture differs by tier.
