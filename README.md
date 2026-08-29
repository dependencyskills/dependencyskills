# Dependency Skills

A coding agent should know what the libraries on its classpath can already do.
This builds the thing that tells it — a local index of your dependencies'
capabilities, harvested from what they already ship, across Maven, npm, SPM, Go
and Python.

> **Being built, and not yet adoptable.** The architecture is settled and the
> measurements behind it are published, but there is no release and no stable
> spec. Do not publish anything against it yet.
>
> **The measuring came first, and it is still most of what is here.**
> Twenty-seven experiments and fifty-five research records, because the point was
> to find out whether this works before writing it. It does, with limits we
> publish — several of which killed ideas this project had already committed to
> in writing.
>
> The pipeline now runs end to end for Maven, Kotlin and Java: a sources jar goes
> in, and a need written in plain words comes back with entries from your own
> dependency graph, scoped to what your project actually resolved. What is
> missing is the rewriter that makes library prose safe to show, and the server
> an agent talks to. If you came looking for something to install, there still
> isn't one.
>
> How it is shaped and why:
> [ADR-0012](docs/knowledge/decisions/ADR-0012-a-shared-machine-level-index-store.md).
> What the measurements found, including the ones that killed our own ideas:
> [`docs/knowledge/research/`](docs/knowledge/research/).

## What actually goes wrong

Three failures, in the order a developer meets them. This part has held up.

**Reinvention.** An agent writes its own version of something the project
already depends on, having never established that the library was there. Two
things went wrong at once, and they need different fixes: nothing announced the
library, and there was nothing to search even if it had thought to look — a
resolved dependency graph is coordinates and archives, and none of it is
searchable. This is the worst of the three because it is invisible. The output
compiles, passes, and quietly duplicates tested code.

It also happens with code the agent *can* see, in the same project. That
observation is what extends this past third-party libraries: visibility is
necessary and not sufficient, because the agent does not go looking when it is
confident it already knows.

**Drift.** A model's knowledge of a library is averaged across every version it
trained on, so it writes against a shape the library no longer has — most
stubbornly where something long-established has recently moved or been renamed,
because that is exactly where the old shape dominates. And it argues the point,
since its confidence tracks how often that shape appeared rather than whether
it is still true.

**Selection.** Fix the first two and this one arrives in their place: a project
with hundreds of importable libraries, several of which could plausibly answer
the question in front of it, still has to pick one. Overlap is not a defect to
be cleaned up. It is what dependency graphs look like.

**A better model does not fix any of this.** The agent is not reasoning badly;
it is reasoning correctly over what it can see, and what it can see does not
include the library. Capability cannot substitute for information that is not
reachable — which makes this a distribution problem rather than one to wait
out.

And a skill is worth most in the opposite place from where you would guess. A
model has priors about a widely-used library: stale, but present. About a
small, new or private one it has close to nothing. **The value of shipping a
skill runs inversely to the model's training exposure to it**, and by count
most of a dependency graph sits at the low-exposure end.

## What the measurements changed

Everything below was measured against public projects, with the collectors and
raw graphs in [`experiments/cost-model/`](experiments/cost-model/). Several of these reversed a
position this project previously held in writing.

**The cost is real, and larger than the design assumed.** A project's
*importable* set — what a developer can call without touching the build file —
is 86–99% of the resolved graph on the JVM and 100% of it in npm and Python,
where the install layout is flat. That is 311 libraries for a mid-sized Android
application and 995 for a large Next.js one. At the median published skill
description, one entry per library is 20k–139k tokens resident before any work
starts. There is no cheap floor to retreat to.

**The documentation mostly already ships.** 93–98% of JVM libraries publish
`-sources.jar`, including on KMP root modules and on 98% of Android AARs. Go
ships source for 100% of a module graph and doc comments for 100% of it. Swift
packages ship a curated documentation catalog — articles, and per-version
migration guides — inside the package. So the artifact this project set out to
invent largely exists already, and what is missing everywhere is that nothing
indexes it.

**The JVM is not the disadvantaged ecosystem.** It publishes `api` versus
`implementation`, so a library's private dependencies never reach a consumer's
compile classpath, and its build tooling resolves into configurations nobody
writes code against. npm has neither. Measured, that makes overlap between
competing libraries pervasive in npm — eight capability groups in one real
graph — and essentially absent in four JVM graphs once restricted to what is
importable.

**Documentation coverage is thin, and it is cultural.** A median library
documents 33% of its public declarations. Java-majority libraries reach 84%;
Kotlin-majority ones 30%, with identical tooling. That gap is the convention
that code should be self-documenting — reasonable for application code, wrong
for a library, which exists to be read by someone who cannot read it.

## Where the design currently stands

**Decided, and it was the biggest reversal.** The original mechanism — a
`-skills.zip` sidecar artifact published alongside a library — is **abandoned**.
Measurement showed the content it would have carried already ships in the
`-sources.jar` that 93–98% of libraries publish, so the sidecar asked every
publisher in the world to do new work for something already done
([ADR-0009](docs/knowledge/decisions/ADR-0009-transport-is-sources-jar.md),
superseding [ADR-0003](docs/knowledge/decisions/ADR-0003-library-skills-via-repository-artifacts.md)).
Nothing a library author does changes; the indexing happens on the consumer's
machine, from what they already downloaded.

**Holding.** The two-layer design: a small always-resident entry whose only job
is to fire at the right moment, and an on-demand index that maps a need to a
library. Knowing and finding. The measurements made this *more* load-bearing,
not less — a query service cannot answer reinvention, because reinvention is a
failure to ask.

**Open.** How a local model reaches a developer's machine — smaller than it was
now the encoder is 67 MB, and possibly removable altogether, since a public
coordinate's summary is the same object on every machine and could simply be
published ([RAD-0052](docs/knowledge/research/RAD-0052-distributing-a-precomputed-codex.md)).
And what development-time prompt injection actually costs, still the deepest
question here — though no longer the one with no answer at all: the quarantine is
designed, the prose classifier is measured at a 0.170% flag rate on real
harvested documentation, and a tool-less paraphraser placed in front of the agent
stopped a planted credential leaking while the developer's task still
completed.

## The research

Fifty-five records in [`docs/knowledge/research/`](docs/knowledge/research/),
numbered 0001–0055 with no gaps. Each states what was asked, the trail including
the dead ends, what was measured against what, and a recommendation that is
explicitly *not* a commitment — that separation is the point, and it is why
"do not adopt this" reads here as a result rather than a failure.
[The index](docs/knowledge/research/README.md) lists every one with its finding
in a line. If you are starting, these eight carry most of the weight:

| | |
|---|---|
| **0001** | What one skill per dependency costs, across twelve public projects in four ecosystems — the measurement that reshaped everything after it |
| **0006** | Development-time prompt injection: the surface, and the measured model × payload × arm matrix |
| **0019** | Retrieval at scale — where index recall stops being the problem and the agent loop starts |
| **0029** | An agent wrote an injected instruction into its own doc comment, promoting a third-party payload to first-party |
| **0037** | Where this project's own findings contradict each other, listed rather than quietly reconciled |
| **0040** | The 29%→77% retrieval gap was measured against hand-written entries and does not reproduce |
| **0046** | v1, shipped and superseded — a convention nothing could read |
| **0049** | What lexical search alone retrieves, which is the number the vector index has to beat |

Nine decisions that have actually been made are in
[`docs/knowledge/decisions/`](docs/knowledge/decisions/); several are now older than the
measurements and are flagged where that matters. What shipped and failed is in
[`docs/knowledge/research/postmortems/`](docs/knowledge/research/postmortems/) — v1 is inside
published artifacts on Maven Central and anyone can download one and look. Four
worked examples of the failure this project fixes, traced in real codebases, are
in [`studies/`](docs/knowledge/research/studies/).

## Why Kotlin Multiplatform is the reference case

Not because it is the only one, but because it is the hardest — and a design
that survives it works elsewhere by degeneration.

A KMP library is one source set published through several package ecosystems at
once: Maven as a JVM jar, an Android AAR and native and JS klibs; npm for JS
and wasm consumers; SPM or CocoaPods for Apple targets. A KMP author absorbs
all of those conventions as a matter of routine. So when an agent working in an
iOS app and an agent working in a Spring service depend on the same library,
they should find the same guidance. Today neither does, and the reason is
different in each ecosystem.

## Layout

| Path | What it holds |
|---|---|
| `docs/knowledge/` | Research records, decisions, postmortems and reference material |
| `experiments/` | The measurements — the cost model and twenty-seven numbered tests, plus the shared corpus, the summariser and the classifiers. Each self-contained: data plus a runnable harness |
| `site/` | The published site at [dependencyskills.org](https://dependencyskills.org) |
| `spec/` | The convention. Normative, and currently ahead of what has been decided — `discovery.md`, the hard part, is unwritten |
| `implementations/` | Per build system, plus the codex itself — six modules: the store, the harvester, the classifier, the encoder, the in-process runtime and the vector index, alongside the Gradle consumer plugin |
| `agent-skills/` | This project's own skills — **empty** |
| `conformance/` | Runs an implementation against the fixtures — **empty** |
| `fixtures/` | Sample skills, expected archives, malformed cases — **empty** |

The directories marked empty are scaffolding for work that has not started.
They are kept because the layout is a decision
([ADR-0005](docs/knowledge/decisions/ADR-0005-repository-structure.md)) and an empty
directory with a README is a clearer statement of intent than a missing one.

Implementations are organised by **build system**, not package ecosystem: a KMP
library reaches npm consumers through Gradle, so the Gradle implementation owns
that channel. See
[ADR-0005](docs/knowledge/decisions/ADR-0005-repository-structure.md).

## State

Nothing is published and nothing is adoptable. The measuring is done; the
building is most of the way through one ecosystem.

**Settled.** Where library content comes from
([ADR-0009](docs/knowledge/decisions/ADR-0009-transport-is-sources-jar.md)), and the
shape of the indexer that consumes it
([ADR-0012](docs/knowledge/decisions/ADR-0012-a-shared-machine-level-index-store.md)):
a shared machine-level store keyed by coordinate, so a library is indexed once
per machine rather than once per project; declared dependencies by default with
the transitive tail opt-in; and a boundary that decides what an agent is ever
allowed to read.

**Built**, for Maven with Kotlin and Java sources:

| | |
|---|---|
| the store | content-addressed entries, scoped per project, SQLite |
| the harvester | a sources jar read in place, tree-sitter, each doc comment bound to the declaration it belongs to |
| the Gradle plugin | reports which of a project's dependencies the store has never seen |
| the query layer | a need in plain words, lexical, scoped to what this project resolved |
| the classifier | degrades suspect prose without losing the entry — 0.170% flagged on real harvested documentation |
| the runtime | llama.cpp in process, one native library per platform, generation and embedding from the same one |
| the index | two vectors per entry, never concatenated, scope enforced inside the search rather than over its results |

**Unwritten.** The summariser — the rewriter that is the quarantine, and the
reason library prose would never reach an agent verbatim. The MCP server. The
npm and SPM harvesters. Both skills.

**Open.** Whether prose a filter misses is prose an agent would have obeyed —
the gap between catching text and preventing harm.

**And one that is no longer open, because it was measured and the answer is
poor.** Retrieval at the size a real dependency graph produces: over 11,155
entries harvested from one project's 59 dependencies, lexical search puts the
right answer in the first ten for **2 of 17** needs and the two-faced vector
index for **4**. Double, and nowhere near enough. The rewrite face helps
sharply where it exists but covers 3.6% of entries until the summariser is
written, so the number should move. Publishing it now is the point: a retrieval
design with no baseline cannot tell an improvement from a change.

## If you are working on this too

Several projects are attacking the same problem from different directions —
they are listed in
[`docs/knowledge/reference/landscape.md`](docs/knowledge/reference/landscape.md).
**We would rather adopt your solution than ship ours.** The point is a working
system, not being the one who built it.

So, concretely:

**If you have solved something we are stuck on, please say so** — in an issue,
a discussion, a pull request, or a link to your own write-up. The open problems
are listed above and in the research records; the ones we would most like
someone else's answer to are declaring a Module Metadata variant on a Kotlin
Multiplatform root module, and what development-time prompt injection through a
dependency actually costs.

**If we have characterised your project wrongly, tell us.** The landscape file
is descriptive on purpose, and
[RAD-0008](docs/knowledge/research/RAD-0008-the-field-as-it-stands.md) says where we
think each pattern's limits are. Both were written from the outside, from
sources read on a date that is recorded. Arguing with a position you no longer
hold helps nobody, and we would rather be corrected than accurate-sounding.

**If our measurements are wrong, that is the most useful thing you could tell
us.** Every subject is a public project, the collectors are in
`experiments/cost-model/scripts/`, and the raw graphs are in `experiments/cost-model/data/`.
Several findings here already reversed positions this project held in writing,
and two of those reversals came from outside.

**Public comment gets credited and linked.** That is a rule in
[AGENTS.md](AGENTS.md), not a courtesy — if you contribute in the open, your
name and a link to what you said belong beside the thing it changed. Private
review is never named or quoted, for the same reason.

What we are not interested in is a competition over whose convention wins.
There are already four incompatible npm layouts, and a fifth would help nobody.
If the answer turns out to be somebody else's mechanism with our measurements
attached to it, that is a good outcome.

> *"We look for things. Things to make us go."*
> — the Pakleds, Star Trek: The Next Generation, "Samaritan Snare" (1989)

Same here. We are looking for things that make it go.

## Contributing

Contributions are accepted under the
[Developer Certificate of Origin](https://developercertificate.org/) — sign
your commits with `git commit -s`. Commits are signed and merged with merge
commits only; see [AGENTS.md](AGENTS.md) for why. Code contributions are under
Apache-2.0; contributions to the written material are under CC BY 4.0 (see below).

## License

Two licenses, split by *kind* of material — this is a research project as much as a
codebase, and the two are shared on different terms:

- **Source code** — everything that is code — under the
  [Apache License 2.0](LICENSE).
- **Written material** — the documentation and research records under `docs/`, the
  specifications under `spec/`, and the website prose under `site/` — under
  [Creative Commons Attribution 4.0 International (CC BY 4.0)](https://creativecommons.org/licenses/by/4.0/).
  Use it, quote it, build on it — commercially or not — as long as you credit
  **Brill Pappin** and link back. It is the ordinary scientist's bargain: the
  method and findings are free to use; the source is expected to be cited.

Maintained by [Brill Pappin](https://github.com/bpappin).

Copyright © 2026 Brill Pappin.
