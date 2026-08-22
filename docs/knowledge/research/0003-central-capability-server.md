# A Central Capability Server for Library Discovery

RAD-0003 · 2026-08-13 · v1

**Measured against:** the measurements in
[RAD-0002](0002-existing-documentation-systems-as-skill-transport.md) and
[RAD-0001](0001-cost-of-a-skill-per-dependency.md), all collected 2026-08-13 —
Maven Central and Google Maven artifact availability, KDoc/Javadoc coverage
across 72 sampled libraries, `<scm>` metadata across 86 POMs, and the resident
cost model. No new network measurement was taken for this record beyond the
sources-jar population check below.

## Question

An idea captured while reading the v4 proposal, held out of the repository
deliberately: stand up **one MCP server** carrying library capability
information, which any agent queries by need — *"I need to do X, what have you
got?"* — instead of every library shipping a skill that sits resident in
context.

The scratch note (`outbox/mcp-capability-server-idea.md`, 2026-08-13) worked
the idea a long way and reached three conclusions worth testing rather than
re-deriving: that the "central service" objections mostly apply equally to
publishing to Maven Central and so do not discriminate; that the real fork is
*where the capability data lives*; and that harvesting existing docs solves
cold-start.

What this RAD has to settle:

1. Does the server **replace** the shipping convention, or sit alongside it?
2. What does it actually remove from the cost model, and what does it not?
3. Given RAD-0002's measurements, is the cold-start seed real?
4. Does thin documentation coverage today decide anything?

## Trail

### What the server genuinely removes, quantified

RAD-0001 measured resident cost as skills-present × per-entry cost, where
skills-present is whatever the agent scans. For Now in Android that is 311
importable libraries and ~19.7k tokens resident before any work starts; for a
Next.js application at the 90th percentile, 995 libraries and ~62.9k.

A query service changes the shape of that from **O(number of libraries)** to
**O(1) trigger + per-query results**. One resident entry at the corpus median
of ~63 tokens, instead of 311 of them. That is not an incremental saving; it
is the difference between a design that has a ceiling and one that does not,
and it dissolves the proposal's open question 7 (a per-description limit with
no aggregate budget) rather than answering it.

It also removes the consumer-side per-ecosystem work. The v4 proposal's
consumer story is a Gradle harvester, an npm scanner, a pip scanner and a
four-way discovery matrix. An agent that already speaks MCP needs none of it.
Publishers may need tooling; consumers — the many — need nothing installed.

### What it does not remove, and this is the correction

**A query service cannot answer reinvention.**

The README orders the three failures as reinvention, drift, selection, and
calls reinvention the worst because it is invisible. Reinvention is precisely
the case where *the agent never thought to ask*: it writes its own retry
helper having never established that a retry library was on the classpath. A
pull-based service answers a question that was asked. It is structurally
incapable of firing when nothing prompts it.

So the note's framing — "nothing resident until asked" — is too strong. The
irreducible resident cost is not zero; it is **one trigger**, whose only job is
to fire at the right moment and say *ask*. That is exactly the first of
ADR-0004's two layers, and it survives a capability server untouched. What the
server can replace is the second layer, the codex — *finding* — and it can do
that well.

Stated as a table, against the three failures:

| Failure | Resident skill per library | Capability server |
|---|---|---|
| **Reinvention** | fires without being asked | **cannot** — requires a resident trigger |
| **Drift** | as fresh as the shipped artifact | fresher; served copy tracks the current release |
| **Selection** | pays the whole graph resident | **its strongest case** — rank at query time, cost nothing until asked |

That is the honest division of labour, and it means the server is an
additional path for the librarian rather than a replacement for it — which is
also how the note's own three-layer model ends up.

### Cold start: measured, and better than the note assumed

The note identified cold-start as the hardest problem and proposed seeding
from existing docs. RAD-0002 measured whether that seed exists.

| | |
|---|---|
| `-sources.jar` published, Maven Central | **95.3%** (n=170) |
| `-sources.jar` published, Google Maven | **93.3%** (n=45); **98%** of AARs |
| `-sources.jar` on KMP root modules | yes, on every library checked |
| sources jars that are genuinely populated | **82%** (68/83) |
| `<scm>` in POM naming a fetchable git host | 90% Central, 82% Google Maven |

The note asked for the populated-jar number specifically. Of 83 downloaded,
5 (6%) contain no source at all and 10 (12%) contain one or two files. Those
are not defects: the empty ones are aggregators and shims —
`spring-boot-starter-tomcat`, `androidx.collection:collection-ktx` — which
have no source of their own to publish. **82% carry a real source tree**,
median 104 KB.

So the seed is real. A server can index essentially the whole JVM ecosystem
**with zero author adoption**, retroactively, including releases cut years
ago. That is a property no shipping convention can have, and it is the
strongest single argument for the server that exists.

**It should harvest from `-sources.jar`, not from git.** RAD-0002 found that
`<scm><tag>` identifies the released version in only **2%** of POMs, and that
**0 of 83** jar manifests record a commit. The repository tells you where the
code lives, not which commit shipped. A sources jar is checksummed and tied to
the resolved version by construction, so it is the version-accurate ingest
path; git is the retroactive, version-independent one.

### It routes around the piece that is stuck

The proposal's open question 2 — declaring a Module Metadata variant on a KMP
root module — is described there as "the only piece of this that is currently
stuck". A harvest-from-sources server does not touch it. KMP roots publish
`-sources.jar` reliably, and the `commonMain` KDoc inside is the
platform-independent API surface, which is platform-independent for the same
reason a skill is. The thing KMP breaks is routed around by the thing it does
not.

That downgrades the blocker from *required for any coverage* to *required only
for the authored premium tier*.

### Does thin coverage decide anything

RAD-0002 measured what is actually there to harvest, and it is thin: a median
library documents **33%** of its public declarations, Kotlin-majority
libraries **30%**, against **84%** for Java-majority ones.

**Current adoption is not the deciding factor**, for two reasons.

First, the Java/Kotlin split shows the ceiling is not a property of the
tooling. The same ecosystems, with equivalent doc processors, sit at 84% and
30%. The gap is the convention that code should be self-documenting — right
for application code, wrong for a library, which exists to be consumed by
someone who cannot read it. Nothing about 30% is structural.

Second, and more usefully: **completeness is checkable, so it can be a build
concern rather than an exhortation.** A library that wants to be usable by an
agent has to be complete in the same sense it has to compile — and a plugin
can verify that: are public declarations documented, is there a module-level
description, does the capability guidance exist, does `<scm><tag>` identify
the release. This project already intends `conformance/` to be the place where
an implementation "demonstrates it follows `spec/` rather than claiming to";
a publisher-side completeness check is the same idea pointed at libraries
instead of implementations.

That reframes the number. 30% is the state of an ecosystem with no incentive
and no check, not a forecast. It bounds what a harvested baseline can say
*today*, and it is the argument for shipping a check early rather than an
argument against the design.

### The fork the note identified: where does the data live

Three options, from the note, unchanged and still the right frame:

1. **In the artifact**, server harvests it — keeps permanence; still requires
   publishing something.
2. **Only in the server** — maximally uniform, no sidecar; the data is as
   durable as the server, and dies with it.
3. **Hybrid** — permanence from the artifact, query from the server.

Option 2 is the v1 postmortem's failure in a new outfit. The postmortem's
lesson is that a published artifact is permanent and an ephemeral mechanism is
not; a server that goes away takes the corpus with it, and every consumer
pinned to it breaks at once. RAD-0002 supplies a variant of option 1 that the
note did not have available: the artifact need not be a *new* one. Sources
jars are already permanent, already immutable, already checksummed.

### Two data types, and the trust boundary

The note's distinction is the most valuable thing in it and should survive
into any design:

- **Library-authored** — API surface, intended patterns, what it is for.
  Authoritative because the author wrote it, global, harvestable.
- **Team experience** — "we tried X, hit this, switched to Y, don't use Z for
  W." Un-harvestable, un-trainable, opinionated, local.

Blur them and an agent quotes a team's opinion as if it were the library's
specification. This is also the answer to why the server cannot be the whole
story: the cross-library discrimination ADR-0004 identifies as the valuable
part — *several of these do X, we reach for that one, here is why not the
others* — is team knowledge, and no central corpus can hold it because no
library knows what else is on your classpath.

### Retrieval, not generation

Carried forward from the note without change, because it is correct and it is
what `AGENTS.md` demands. A retrieval layer returns sourced facts with
provenance. A generative layer answering in its own words reintroduces exactly
the failure being escaped — a model averaging over inputs, one hop from ground
truth. If an AI sits in the middle it is **a router that cites, not an oracle
that answers**. "Try Y, here is its real entry" is the good case; "here is how
Y works", invented, is the failure.

## Findings

**Measured** (all from RAD-0001 and RAD-0002; see those for method).

- Resident cost scales with the number of libraries in scope: 311 → ~19.7k
  tokens for Now in Android, 995 → ~62.9k for a large Next.js app. A query
  service replaces that with one resident trigger of ~63 tokens plus per-query
  cost.
- The cold-start seed exists: 93–98% of libraries publish `-sources.jar`, 82%
  of those are genuinely populated, and KMP roots publish it reliably.
- Version-accurate ingest must come from the artifact: `<scm><tag>` is usable
  in 2% of POMs and no sampled jar manifest records a commit.
- What is there to harvest is thin: median 33% of public declarations
  documented, 30% for Kotlin-majority libraries, 84% for Java-majority ones.

**Reasoned, not measured.**

- That a pull-based service cannot address reinvention. This follows from the
  definition of the failure — the agent never asks — rather than from an
  experiment, and it is the load-bearing claim of this record. It could be
  tested: give an agent a capability tool and a task whose solution exists in
  an undeclared dependency, and see whether it queries unprompted.
- That publisher-side completeness checking would move documentation coverage.
  Plausible from the Java/Kotlin split and from how lint adoption works
  generally; unmeasured.

**Unverified and consequential.**

- Whether harvested KDoc can be transformed into capability language without a
  generative step that breaks provenance. The note flags that KDoc is written
  in the API's words ("resilience policies") and the index needs the caller's
  ("retry with backoff"). That transform is where the retrieval/generation
  line gets crossed, and nothing here establishes it can be done safely.
- Governance, operation and funding of a central service. Maven Central proves
  it is possible, not that this project can do it. A local server avoids the
  question entirely.
- **Development-time prompt injection.** Harvesting documentation from
  everything reachable means ingesting attacker-controllable natural language
  from 311 libraries (Now in Android) to 995 (large Next.js). A central
  intermediary cuts both ways here: it is the only place that could scan,
  sign or revoke, and it is a single point of compromise serving every agent.
  Unresolved anywhere in this repository — see
  [RAD-0004](0004-external-review-of-the-proposal.md) §2.
- Whether a cached-and-pinned query response satisfies offline, air-gapped and
  CI builds, or whether those still need an on-disk path.

## Recommendation

**Both deployments are live, and local comes first.** Central and local are
two shapes of the same thing, not competing designs.

*Local* — an MCP server scoped to one project, its index built from the
resolved graph and harvested `-sources.jar` content rather than by directory
scanning (which avoids §3's objection). It keeps the property that matters:
resident cost drops from O(number of libraries) to O(1) trigger plus per-query
results. It sheds every governance, operation, funding and
single-point-of-compromise question below. It works offline, needs nobody to
operate it, and is ADR-0004's local librarian exposed as a tool rather than as
a file. **This is the shortest path to something demonstrable**, it requires
no ecosystem adoption, and anyone who would benefit already has an MCP-capable
agent — that is the premise of the whole project.

*Central* — not dead, and not merely a bigger local one. It is the registry
concept rebuilt in agent-native form: what Maven Central is to builds, this is
to agents. It is the only shape that can answer *"what exists that I do not
already depend on"*, which is the case a local index structurally cannot
serve, and the only place a corpus can be scanned, signed or revoked centrally.
It carries the governance cost in exchange.

**Treat the server as a third layer, not a replacement.** It does not negate
the shipping convention and it does not negate
[RAD-0002](0002-existing-documentation-systems-as-skill-transport.md). The layering
that falls out of the measurements:

1. **Content substrate** — existing documentation systems, per RAD-0002.
   `-sources.jar` on the JVM, the source tree everywhere else. Permanent,
   author-owned, versioned with the release.
2. **Local librarian** — ADR-0004's two layers. The resident trigger is
   **irreducible** and must survive any server, because reinvention is a
   failure to ask. The local codex becomes a pinned cache of whatever the
   server returns, which also answers the reproducibility objection.
3. **Capability query service** — the discovery front-end over the corpus, and
   the answer to *selection*. This is the proposal's open question 4 realised
   as a live service.

**Name what MCP adds over a static index, because it is not just delivery.**
Doc indexing alone works and is the substrate; the server form adds a
dimension that is specifically agentic:

- **A tool is a first-class affordance; a document is not.** This bears
  directly on the trigger problem above. A skill file is data an agent may
  consult; a tool is something models are trained to invoke, and it sits in
  the one list an agent consults by default. That does not eliminate the
  reinvention failure — the agent still has to decide to call — but a tool
  description is a materially better trigger surface than a file it has to
  remember to read.
- **Interaction.** A static codex answers the question it was written for. A
  tool can be asked follow-ups, narrowed, and — for the clobbering case in
  RAD-0004 §3 — can hand an ambiguous choice back rather than guessing.
- **Ranking against the actual query**, using the caller's words rather than
  the author's, which is the transform problem the harvested-KDoc baseline
  otherwise has to solve up front for everything.
- **Write access.** The team-experience layer is the one thing no corpus can
  harvest; a tool can accept it as the team learns it. A file cannot.

**Seed it by harvesting, not by asking, and label the tiers.** The measured
93–98% sources availability means the index can cover the ecosystem
retroactively with no author adoption at all, which is the only credible
answer to cold-start. Ship *discovered* content — harvested from what a
library already publishes — as the baseline for everything, and *designed*
content — authored for this system by a library that opted in and passes a
completeness check — as the tier above it. RAD-0002 sets out the distinction;
the server is where it becomes operational, because a query answer should say
which tier it came from. An agent weighing "the author asserted this for
agents" against "this was inferred from prose written for humans" is making a
different judgement in each case, and the same labelling discipline this
record already demands between library-authored and team-experience data
applies here.

**Ship a completeness check early.** This is the concrete near-term deliverable
that falls out of the coverage measurement, and it is small: a build-side
plugin that verifies a library is documentation-complete — public declarations
documented, module-level description present, `<scm><tag>` populated. It gives
an author aiming to be agent-friendly something to run instead of a
convention to read, and it is the mechanism by which 30% moves. `conformance/`
already exists in this repository for demonstrating conformance rather than
claiming it; this is the same instrument pointed at libraries.

**Keep the shippable thing shippable.** Layers 2 and 3 are a much larger track
than the packaging convention, and the convention must not be re-scoped as
"phase one of a platform" — which is how a shippable thing stalls. Note where
the layers touch (the local librarian is the shared cache) and keep the scopes
apart. This RAD is not a reason to delay the convention; it is a reason to
make sure the convention does not foreclose the server.

**What would change the answer.**

- If an agent given a capability tool *does* query unprompted in the
  reinvention case, the resident trigger becomes optional and the server gets
  substantially stronger. This is testable and worth testing.
- If the KDoc-to-capability-language transform cannot be done without
  generation, the harvested baseline is much less useful than it looks and the
  authored tier carries almost everything.
- If nobody credible will operate the service, layer 3 is a design that cannot
  ship regardless of its merits, and the effort belongs in layers 1 and 2.

## Connections

- [RAD-0001](0001-cost-of-a-skill-per-dependency.md) — the resident cost this is
  proposed to remove.
- [RAD-0002](0002-existing-documentation-systems-as-skill-transport.md) — the
  content substrate and the harvest seed.
- ADR-0004 — the two layers; the trigger
  survives, the codex is what a server could serve.
- [docs/postmortems/v1-bundled-flat-files.md](../postmortems/v1-bundled-flat-files.md)
  — why a server-only corpus repeats the ephemeral-mechanism failure.
- `conformance/` — where a publisher-side completeness check belongs.
- `outbox/mcp-capability-server-idea.md` — the scratch note this record
  supersedes; most of the reasoning here is its, tested against measurement.
