# Dependency Skills

Research into how a library can carry guidance an agent will actually use —
across Maven, npm, SPM, Go and Python — and an index that makes it findable at
the scale a real dependency graph reaches.

> **This is a research project, not a convention you can adopt.** It began as
> a publishing proposal and is currently *not* proposing that mechanism, because
> measurement moved the answer. There is no stable spec, no shipped
> implementation, and the direction has changed twice. Do not publish anything
> against it yet. What is worth reading now are the findings, in
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

**Under review.** The original mechanism — a `-skills.zip` sidecar artifact
published alongside a library — is recommended for abandonment in favour of
the documentation systems each ecosystem already has. That recommendation is
recorded, not decided.

**Holding.** The two-layer design: a small always-resident entry whose only job
is to fire at the right moment, and an on-demand index that maps a need to a
library. Knowing and finding. The measurements made this *more* load-bearing,
not less — a query service cannot answer reinvention, because reinvention is a
failure to ask.

**Open.** Whether the index is a local tool, a central service, or both.
Whether a library's guidance can be reached through the source repository the
publishing metadata already names. What development-time prompt injection
actually costs, which is the one objection with no answer yet.

## The research

Records live in [`docs/knowledge/research/`](docs/knowledge/research/). Each
states what was asked, the trail including the dead ends, what was measured
against what, and a recommendation that is explicitly not a commitment.

| | |
|---|---|
| **0001** | What a skill per dependency costs, measured across twelve public projects in four ecosystems |
| **0002** | Existing documentation systems as transport and content — KDoc, DocC, godoc, docstrings |
| **0003** | A capability server as a query front-end, local and central |
| **0004** | External review of the proposal, and what it changed |
| **0005** | A git-hosted codex reachable from published metadata |
| **0006** | Development-time prompt injection |
| **0007** | Choosing between overlapping libraries |
| **0008** | The field as it stands — what others have built, what it corroborates, and where each pattern's limits are |

Decisions that have actually been made are in
[`docs/knowledge/adr/`](docs/knowledge/adr/); several are now older than the
measurements and are flagged where that matters. What shipped and failed is in
[`docs/knowledge/postmortems/`](docs/knowledge/postmortems/) — v1 is inside
published artifacts on Maven Central and anyone can download one and look.

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
| `experiments/` | Spikes — the cost model, and test0 (the parse bake-off). Each self-contained: data plus a runnable harness |
| `spec/` | The convention. Normative, and currently ahead of what has been decided |
| `implementations/` | Per build system. Publishing and harvesting, per channel |
| `agent-skills/` | This project's own skills |
| `conformance/` | Runs an implementation against the fixtures |
| `fixtures/` | Sample skills, expected archives, malformed cases |

Implementations are organised by **build system**, not package ecosystem: a KMP
library reaches npm consumers through Gradle, so the Gradle implementation owns
that channel. See
[ADR-0005](docs/knowledge/adr/0005-repository-structure.md).

## State

Nothing is published and nothing is adoptable.

The Gradle implementation has a publisher for the Maven channel and has not
been compiled since the project was retargeted. The harvester, the index, the
emit steps for npm and SPM, and both skills are unwritten. A previous proposal
argued for a mechanism the research now recommends against; it has been
removed, and the reasoning survives in the research records.

The next things worth doing are measurements rather than code: whether an
injected instruction in a doc comment actually redirects an agent, and which of
the importable libraries a project's source really references.

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
[RAD-0008](docs/knowledge/research/0008-the-field-as-it-stands.md) says where we
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
