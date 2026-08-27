# ADR-0012: The index is a shared machine-level store, keyed by coordinate

Date: 2026-08-26 · Status: accepted · v2

**v2 (2026-08-27) — the path is `~/.gradle/dscodex/`, and it is configurable.** This record said
"a machine-level directory, in the manner of `~/.gradle/caches` or `~/.m2`" and left the actual
location open. Settled while triaging #1, against the machine rather than on taste:

- **Gradle collects `caches/`, not its root.** Every `gc.properties` lives under `caches/`, and
  `CACHEDIR.TAG` — which tells backup tools to skip a tree — sits at `caches/`, `jdks/` and
  `daemon/`, not at `~/.gradle` itself. A sibling of `caches/` is neither collected nor excluded
  from backups. That distinction matters more here than for Gradle's own caches, because ours is
  expensive to rebuild: a model call per documented declaration, against a re-download.
- **The precedent is direct.** `nodejs`, `yarn` and `binaryen` are third-party plugin caches at that
  root already, placed there by the Kotlin Multiplatform JS plugin.
- **The version goes in the schema, not the path.** `~/.m2` is Maven *2* and Maven 3 and 4 still use
  it. A version in a directory name becomes a lie and cannot detect or migrate an old store; a
  `schema_version` row can.

**One consequence is left open on purpose.** ADR-0005 makes the Gradle implementation responsible
for the Maven, npm and SPM channels a Kotlin Multiplatform library publishes to, so a store under
`.gradle/` will hold npm and SwiftPM entries. A Maven-native or npm-native implementation would then
have to look inside `.gradle` for them, which reads oddly.

The alternative is **one codex per build system**, each smaller and independently owned, at the cost
of harvesting a shared dependency more than once — the duplication this record exists to remove.
Choosing between them needs a second implementation to exist, so the decision here is only that the
**path is configurable**, which is what keeps the question answerable rather than foreclosed.

## Context

Twenty-two experiments have settled what the indexer must do. What they did not settle
is where it runs, what it costs, and who pays that cost.

**The expensive stage is per entry, not per project.** The summariser is a local model
call for every documented declaration
([`experiments/summariser`](../../../experiments/summariser/README.md)). One small real
project — 99 dependencies — yields **5,440 deduplicated entries**
([RAD-0019](../research/0019-retrieval-at-scale.md)). Rebuilding that per project puts
hours of model time in front of every checkout, and a developer with several projects
pays repeatedly for the same libraries.

**But a resolved coordinate is immutable.** `io.ktor:ktor-client-core:3.5.1` is the same
artifact on every machine, for ever. Its entries are a pure function of the coordinate,
so the result is cacheable with no invalidation problem — the property the Gradle module
cache already relies on, and which this project has been reading from throughout the
experiments.

Three further constraints come from measurement rather than preference:

- **Two faces, two vectors.** [RAD-0040](../research/0040-does-summarising-improve-retrieval.md)
  measured an index carrying both the raw documentation and the rewritten sentence
  reaching **15 of 17** within ten against 13 for documentation alone and 10 for the
  rewrite alone — and fusing the two texts into one key scoring **worse than either**.
- **The rewrite is quarantine, not retrieval lift.** The same record withdrew the claim
  that summarising improves recall. It stays because `test7` measured it stopping a
  planted credential (0 of 3 harm, 2 of 3 task), which is a different and still-standing
  result.
- **A retrieval key is not a channel.** A vector is read by nothing, so raw documentation
  can be *searched on* while only the rewrite is ever *shown*. That is what makes a
  degraded entry findable rather than invisible.

## Decision

**The index is a shared store on the machine, keyed by coordinate and version, with a
per-project view over it.**

- **Location.** A machine-level directory, in the manner of `~/.gradle/caches` or
  `~/.m2` — not inside any project, and not per checkout.
- **Key.** `group:artifact:version` and its ecosystem equivalents. Immutable, so entries
  are written once and never invalidated.
- **What is shared.** Harvest, parse, deduplication, classification, summarisation and
  both vectors. Every expensive stage.
- **What is per project.** Dependency resolution, and a **query scoped to the coordinate
  set that project resolved**. Nothing else.
- **Runtime.** Kotlin on the JVM, with Lucene as the derived index
  ([RAD-0010](../research/0010-how-the-codex-is-stored-and-served.md)). Chosen over a
  Python reference implementation despite every measured component existing in Python,
  because the port would have to happen anyway and the JVM is where the harvester already
  has to run.
- **Scope.** Declared dependencies by default; the transitive tail is opt-in.
  [RAD-0022](../research/0022-the-value-of-transitive-capabilities.md) measured that 11 of
  17 real capabilities lived only in the tail, so the default is not free — it is the
  safe side of a trade the operator can take deliberately.

### The per-project scope is a containment boundary, not a convenience

A shared store holds entries from every library any project on the machine has ever
resolved. If a query ranged over the whole store, a poisoned entry pulled in by one
project would be reachable from another that never depended on it — a laundering route of
exactly the shape [RAD-0029](../research/0029-the-agent-as-a-trust-launderer.md)
describes, created by our own caching decision.

**Queries are therefore filtered to the resolved coordinate set of the project asking.**
This is load-bearing and not a performance filter.

### Entries record what produced them

The store is long-lived and shared, so entries written months apart by different model
versions will coexist. Each entry records the summariser and encoder that produced it.
Without that, a result cannot be reproduced, a bad model version cannot be invalidated
selectively, and the pinning discipline the summariser already enforces is lost the moment
the output is cached.

## Consequences

**A dependency you already have costs nothing.** The second project to use a library pays
resolution and a set intersection. This is what makes a per-entry model call affordable at
all; without it the design is not viable and the summariser would have to be dropped.

**The first build on a machine is slow, and visibly so.** Nothing hides that. It is
amortised, not eliminated.

**Two runtimes are avoided at the cost of reimplementation.** The measured Python
components — extraction, the summariser's verifier, the classifier — are ported rather than
reused. The classifier ports cheaply: term frequencies and a dot product are what Lucene
already computes. The embedder does not, and
[RAD-0035](../research/0035-a-small-local-model-for-the-prose-gap.md) records the JVM
embedding runtime as the open piece.

**A precomputed store could be distributed, and deliberately is not.** Because the key is
immutable, someone could publish a prebuilt index and remove the cost entirely — while
importing their trust decisions wholesale.
[RAD-0036](../research/0036-can-the-corpus-be-poisoned.md) is about exactly that hazard.
Not now, and not without its own decision.

## Alternatives rejected

**Per-project index.** Simple, obviously correct isolation, no shared-state questions. It
puts hours of model time in front of every checkout and pays repeatedly for identical
libraries. Rejected on cost.

**Python reference implementation first.** Every measured component exists and it would run
sooner. Rejected because the port is not optional — the harvester has to reach a Gradle
build and a Lucene index — and a reference that is never the product accumulates its own
conformance problem.

**Indexing the transitive tail by default.** It is where the capabilities are. It also
pulls prose from libraries nobody chose, and the security posture this project has been
building rests on a boundary the developer can point at. Opt-in preserves both.
