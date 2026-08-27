# The index is a shared machine-level store, keyed by coordinate

ADR-0012 · 2026-08-26 · Status: accepted · v3

**v3 (2026-08-27) — entries are content-addressed, and harvesting is demand-driven.** Two refinements that [RAD-0041](Research-RAD-0041-Deduplication-Under-An-Incremental-Store) forced, after this record made the store incremental and scoped without re-checking the deduplication rule inherited from the batch experiments.

**An entry is keyed by its content, and coordinates point at it.** A hash of `(symbol, signature, doc)` identifies the entry; a second table maps coordinate to entry. A query filters coordinates to the asking project's scope, joins, and takes distinct entries.

This was chosen over deduplicating at harvest, which RAD-0041 showed breaks twice over once the store is incremental and scoped: the surviving copy depends on which build ran first, so the store is not reproducible; and a project depending only on the artifact that *lost* cannot see the entry at all, silently. Content-addressing fixes both rather than relocating them — identity is content, not arrival order, and every owning coordinate is recorded, so no consumer loses an entry a sibling artifact happened to also carry.

The unit came out of the data rather than assumption. Measured over one real project's resolved set: 2,939 doc texts appear in more than one library, and **2,929 of them — 99.7% — are the same module published twice**, a Kotlin Multiplatform root metadata artifact beside its per-target sibling. Only **10** are genuinely different libraries sharing prose, and those merge correctly under content-addressing for free. "Which library is this from" becomes a *set*, which is the more truthful answer: the capability really is in both.

**Harvesting is driven by a project's dependency tree, not by the cache.** The plugin indexes what the calling project resolved and nothing else, so the first build on a machine is bounded by one dependency graph rather than by everything ever downloaded. `experiments/test5`'s real project is 59 documented libraries and 14,899 entries, which content-addressing stores as **6,892 distinct docs — 54% saved before any sharing between projects**. The 537,480 entries in `experiments/corpus/` are the ceiling for a machine that has built everything, not a starting cost.

The trade is a join on every query, and a schema that is a step less obvious to read. Both were accepted for correctness.

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

**One codex per build system, not one per machine.** The store belongs to the tool that resolved
the dependencies: the Gradle plugin writes `~/.gradle/dscodex/`, a Maven one would write under
`~/.m2/`, an npm one under npm's root. Each holds **everything that build system resolves** — the
Gradle codex carries the npm and Yarn entries a Kotlin Multiplatform build pulls in, because Gradle
is what pulled them.

This is the same seam [ADR-0005](Decisions-ADR-0005-Repository-Structure) already draws: one directory per
**build system**, not per package ecosystem. A single machine-wide store would cut across it, and
would need cross-tool coordination over a file none of them owns.

The cost is duplication in one narrow case — a library resolved by two different build systems on
the same machine is harvested into each. That is rarer than it sounds, since a KMP library reaches
npm consumers through Gradle rather than through npm. What this record set out to remove is
per-*project* duplication, and that is removed in full: every Gradle project on the machine shares
one codex.

The path stays **configurable** regardless, so a project that wants a shared store, or a different
root, is not blocked by the default.

## Context

Twenty-two experiments have settled what the indexer must do. What they did not settle
is where it runs, what it costs, and who pays that cost.

**The expensive stage is per entry, not per project.** The summariser is a local model
call for every documented declaration
([`experiments/summariser`](https://github.com/dependencyskills/dependencyskills/blob/HEAD/experiments/summariser/README.md)). One small real
project — 99 dependencies — yields **5,440 deduplicated entries**
([RAD-0019](Research-RAD-0019-Retrieval-At-Scale)). Rebuilding that per project puts
hours of model time in front of every checkout, and a developer with several projects
pays repeatedly for the same libraries.

**But a resolved coordinate is immutable.** `io.ktor:ktor-client-core:3.5.1` is the same
artifact on every machine, for ever. Its entries are a pure function of the coordinate,
so the result is cacheable with no invalidation problem — the property the Gradle module
cache already relies on, and which this project has been reading from throughout the
experiments.

Three further constraints come from measurement rather than preference:

- **Two faces, two vectors.** [RAD-0040](Research-RAD-0040-Does-Summarising-Improve-Retrieval)
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
  ([RAD-0010](Research-RAD-0010-How-The-Codex-Is-Stored-And-Served)). Chosen over a
  Python reference implementation despite every measured component existing in Python,
  because the port would have to happen anyway and the JVM is where the harvester already
  has to run.
- **Scope.** Declared dependencies by default; the transitive tail is opt-in.
  [RAD-0022](Research-RAD-0022-The-Value-Of-Transitive-Capabilities) measured that 11 of
  17 real capabilities lived only in the tail, so the default is not free — it is the
  safe side of a trade the operator can take deliberately.

### The per-project scope is a containment boundary, not a convenience

A shared store holds entries from every library any project on the machine has ever
resolved. If a query ranged over the whole store, a poisoned entry pulled in by one
project would be reachable from another that never depended on it — a laundering route of
exactly the shape [RAD-0029](Research-RAD-0029-The-Agent-As-A-Trust-Launderer)
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
[RAD-0035](Research-RAD-0035-A-Small-Local-Model-For-The-Prose-Gap) records the JVM
embedding runtime as the open piece.

**A precomputed store could be distributed, and deliberately is not.** Because the key is
immutable, someone could publish a prebuilt index and remove the cost entirely — while
importing their trust decisions wholesale.
[RAD-0036](Research-RAD-0036-Can-The-Corpus-Be-Poisoned) is about exactly that hazard.
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
