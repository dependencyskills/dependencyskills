# The Dependency Codex

PRD-0001 · 2026-08-26 · v1

Keywords: agent does not know what my dependencies do; agent reinvented something the project already depends on; agent wrote an old API that was renamed; which of these two libraries does this project use; make library docs searchable for a coding agent; index the classpath; why not one skill per dependency; why not a hosted docs service; why not unpack the jars; why not an artifact transform; local index of dependency capabilities.

## Problem

A coding agent working in a real project cannot see what the project's dependencies can already do, and it does not behave as though anything is missing — it writes code, confidently, as if the classpath were empty.

Three failures follow, and a developer meets them in this order. **Reinvention**: the agent writes its own retry-with-backoff loop into a service that already depends on a resilience library ([case study](../research/studies/RAD-0042-thirteen-slug-functions.md)). **Drift**: the model's knowledge of a library is averaged over every version it trained on, so it writes against a shape the library no longer has, and argues when corrected, because its confidence tracks how often a shape appeared rather than whether it is still true ([case study](../research/studies/RAD-0043-the-datetime-instant-move.md)). **Selection**: a project with hundreds of dependencies, a dozen of which could plausibly answer the question, still has to pick one, and the agent picks the one the team does not use ([case study](../research/studies/RAD-0044-the-legacy-library-everyone-remembers.md)).

**A better model does not fix this.** The agent reasons correctly over what it can see, and what it can see does not include the library, so this is a distribution problem rather than a capability one. The measurements say so directly. Content value is real and cross-model: without an entry, 0 of 8 model × capability cells used a capability that existed; with one, 8 of 8 ([RAD-0016](../research/RAD-0016-the-content-value-ab.md)). It is bounded, though — against three well-known public libraries, current frontier models needed no help at all, and the lift returned the moment the model was stale, small, or looking at code it had never seen. Nine local models spanning 270M to 70B parameters were *all* stale on the same real libraries, and scaling did not close it: **the value tracks the model's training gap to this classpath, not the model's size.** And one gap closes for nobody. Which of several overlapping libraries *this* project sanctions was picked correctly in **0 of 18** model × domain cells with no help; naming the preference fixed it in 17 of 18 ([RAD-0018](../research/RAD-0018-the-selection-ab.md)). No amount of model progress can supply local knowledge.

**The material already exists and nothing indexes it.** Across twelve public projects in four ecosystems, most of the dependency graph is callable and most of it already publishes documentation, and 93–98% of Maven libraries ship a sources jar ([ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md)). Two gaps remain. On the JVM — worst on Kotlin Multiplatform — **the library archives everything and never unpacks**, so a resolved dependency is a zip in a cache and none of its prose is on disk to search. And everywhere, **nothing indexes that documentation against the dependency graph**, so even where the text is on disk there is no path from "I need to retry this with backoff" to the library that already does it.

Why now: the pipeline is measured end to end and the remaining work is construction. Parsing is language-agnostic across five languages and four doc conventions with one rig ([RAD-0009](../research/RAD-0009-reusing-indexers-and-what-to-index.md)); enrichment turned out to be a graph join in the index rather than a parser feature; storage and retrieval are settled ([RAD-0010](../research/RAD-0010-how-the-codex-is-stored-and-served.md), [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md)); and a 684,392-entry corpus over 3,781 real libraries in three ecosystems exists as proof the extraction holds at scale.

## Goals / Non-goals

### Goals

- A developer applies one plugin and their agent can answer "does anything on this classpath already do X" against the versions actually resolved.
- The answer is **current and version-matched**, because it is derived from the artifacts the build resolved rather than from a model's memory or a docs site that moved on.
- The cost of indexing a library is paid **once per machine**, not once per project, and never during a build.
- The index is **derived and disposable** — deleting it and rebuilding produces the same content, because the package caches are the source of truth.
- Nothing about it can break a build.

### Non-goals

- **Not a hosted or central service.** A central capability server was considered and set aside ([RAD-0003](../research/RAD-0003-central-capability-server.md)); the index is local, and anything central is a later question with its own record.
- **Not a replacement for a library's documentation site.** It answers "what on my classpath does this", not "teach me this library".
- **Not authoring content.** The codex harvests what libraries already publish. The hand-authored half — this project's own preference between overlapping libraries, and negative guidance ([ADR-0004](../decisions/ADR-0004-librarian-and-codex.md)) — is real and measured as the only fix for selection, and is deliberately out of the first release.
- **Not a defence against fabricated capabilities.** Honest-looking prose describing something a library does not do beat the true answer 4 of 17 in measurement, and nothing in this design has purchase on it ([RAD-0037](../research/RAD-0037-unresolved-tensions.md)).
- **Not a package manager, an SBOM tool, or a vulnerability scanner.** It reads what the build already resolved and writes nothing back to it.
- **Not a per-library shipped artifact.** The `-skills.zip` sidecar was built, published and abandoned; the [postmortem](../research/postmortems/RAD-0046-v1-bundled-flat-files.md) says why, and [ADR-0003](../decisions/ADR-0003-library-skills-via-repository-artifacts.md) is superseded rather than deleted so the reasoning survives.

## Requirements

### R1 — A library is indexed once per machine, not once per project

The store is keyed by resolved coordinate and shared across every project on the machine. A resolved coordinate is immutable, so entries are written once and never invalidated — and that property is what makes an expensive per-entry step affordable later. It belongs to the tool that resolved the dependencies: one store per build system, not one per machine and not one per package ecosystem.

> As a developer with six projects on one laptop, I want a library I already indexed for one of them to be instantly available to the others, so that the cost of adoption is paid once rather than every time I clone something.

> As a developer, I want to put the store somewhere else, so that a shared machine or an unusual layout does not block me.

### R2 — An entry is identified by its content, and coordinates point at it

Two artifacts carrying the same declaration and the same prose collapse to one entry owned by both, with no decision made at harvest about which copy survives. This is not a storage optimization; it is what makes the store reproducible and keeps a scoped query honest. Measured over one real project, 2,929 of 2,939 shared doc texts were the same module published twice for different targets, and content-addressing took 14,899 entries to 6,892 distinct ones ([RAD-0041](../research/RAD-0041-deduplication-under-an-incremental-store.md)).

> As a developer whose project depends on only one target of a multiplatform library, I want its entries to be there, so that a sibling artifact I never declared cannot have silently claimed them.

### R3 — Content comes from what the library already publishes

Nothing asks a library author to do anything. The codex reads the sources jar the library already ships, in place, without unpacking it — because Gradle and Maven never unpack, and the archive is the only copy on disk.

> As a library author, I want my library to be discoverable without publishing anything new, so that the index works on the ecosystem as it is rather than on the ecosystem after everyone adopts something.

> As a developer, I want a library with no sources jar to still appear with what can be recovered from its bytecode, so that a source-less dependency degrades rather than vanishes.

### R4 — Indexing is driven by what this project resolved

The first build on a machine is bounded by one dependency graph, not by everything ever downloaded. Declared dependencies are indexed by default; the transitive tail is available behind an explicit opt-in, because it is not free — 11 of 17 real capabilities were measured to live only in the tail ([RAD-0022](../research/RAD-0022-the-value-of-transitive-capabilities.md)), and that is a trade the operator takes deliberately rather than one taken for them.

> As a developer, I want my first build after adopting this to be about my project, so that adopting it does not mean indexing years of accumulated cache.

### R5 — The build detects; the work happens out of band

A build resolves its graph, compares it against the store, and records what is missing. It does not harvest, does not call a model, does not touch the network, and does not fail. A broken or absent index must never break a build, and a build must never wait on one.

> As a developer, I want `./gradlew build` to take exactly as long as it did before, so that the index is something I can leave switched on.

> As a build engineer, I want the plugin to be configuration-cache compatible and to resolve nothing at configuration time, so that it does not undo the build performance work we already did.

### R6 — A need in the caller's words returns entries

The query takes a plain-language need — the shape a developer or an agent has *before* knowing the library exists — and returns ranked candidates. This is the checkpoint the whole design is held to: if a real need against a real project's dependencies returns something useful with no embeddings, no classifier and no model, the spine works and everything after it is improvement.

> As an agent about to write a retry loop, I want to ask "retry with backoff" and be told the project already depends on something that does it, so that I use it instead of writing it.

### R7 — Results are limited to what this project can actually call

The store holds entries from every library any project on the machine ever resolved. A result for a library this project cannot call is worse than no result: it is a suggestion to add a dependency, dressed as a suggestion to use one. Enforced by the same filter as [PRD-0002](PRD-0002-the-trust-boundary.md)'s containment requirement, for a different and independently sufficient reason.

> As a developer, I want the answers to be about my project, so that I am never handed a library that isn't on my classpath as though it were.

### R8 — The index is reachable where an agent works

Served over MCP as two operations — search by need, and get one entry — so an agent asks a question rather than loading a catalogue into its context ([RAD-0010](../research/RAD-0010-how-the-codex-is-stored-and-served.md)). A build task cannot do this: the query has to be answerable when the agent asks, not when the build ran. It starts without a project attached, and says so when it has nothing.

> As a developer, I want to point my existing agent at this and have it work, so that adopting it is configuration rather than migration.

### R9 — Retrieval quality is measured against a stated baseline, and published either way

The first lexical pass is expected to be mediocre, and its numbers are the baseline every later improvement is held to. Raw harvested documentation retrieved at 29% first-hit over 220 entries and collapsed toward zero by 3,000 ([RAD-0019](../research/RAD-0019-retrieval-at-scale.md)), so an index that does not beat that has not earned the machinery it added. Results are recorded honestly including where they are bad, per [ADR-0011](../decisions/ADR-0011-publishing-posture-for-security-findings.md).

> As someone evaluating this, I want to see what it does not retrieve, so that I can tell whether it is worth running against my own project.

### R10 — Every entry records what produced it

Extractor version now, and the model and encoder that touch it later. Without provenance a result cannot be reproduced, and a bad version of a component cannot be invalidated selectively — the only remedy left is deleting everything.

> As an operator, I want to invalidate the entries a specific bad version produced, so that one bad release does not cost me the whole store.

### R11 — A skip never looks like an absence

Every step distinguishes "nothing here" from "did not run". Zero new coordinates reads differently from a plugin that failed silently; an empty result reads differently from an empty store; a jar with no sources reports that rather than succeeding quietly. This is the failure this project keeps re-learning — checking only the newest version once dropped 42 artifacts silently, `androidx.compose.ui` among them.

> As a developer, I want to be told when the index has nothing rather than being handed silence, so that I do not conclude a library has no capabilities when it was never read.

### R12 — The design extends to other ecosystems without being redesigned

One store schema holds entries from any ecosystem the build system resolves, and they stay distinguishable. One extraction rig already covers five languages and four documentation conventions. A second build system gets its own store in its own root, on the seam [ADR-0005](../decisions/ADR-0005-repository-structure.md) already draws.

> As a Kotlin Multiplatform developer, I want the npm packages my Gradle build pulled in to be in the same index as the Maven ones, so that the index matches my project rather than my package managers.

## Decisions

The choices these requirements depend on are recorded elsewhere and are not restated here:

| Decision | Record |
|---|---|
| The index is a shared, machine-level, coordinate-keyed store; entries are content-addressed; harvesting is demand-driven | [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) |
| Content comes from the sources jar, not from a published sidecar | [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md) |
| Two layers: a resident librarian nudge, and the codex it points at | [ADR-0004](../decisions/ADR-0004-librarian-and-codex.md) |
| Plugin and server live under `implementations/`; experiments stay out of it | [ADR-0005](../decisions/ADR-0005-repository-structure.md) |
| Conform to each ecosystem's existing conventions rather than inventing one | [ADR-0007](../decisions/ADR-0007-conform-to-existing-conventions.md) |
| Findings are published as observations, with the failures included | [ADR-0011](../decisions/ADR-0011-publishing-posture-for-security-findings.md) |

Contracts that follow from them, stated once here because stories cite them:

- **The unit of storage is an entry**: a symbol, a signature, a doc, a language, a documentation format, and the set of coordinates that own it.
- **Harvest is a pure function of one archive.** Same archive in, same entries out, no reads of prior state, no order dependence. Everything that used to be decided at harvest — deduplication in particular — is now a property of the store's schema.
- **Text is the source of truth and the index is derived.** SQLite holds the entries; a search index is built from them and can be thrown away.
- **The build detects and something out of band harvests.** Gradle's artifact transforms fit the shape and are rejected: they would run inference inside a build, and their output would live in a cache Gradle owns and evicts.

## Stories

| Story | Covers |
|---|---|
| [#1](https://github.com/dependencyskills/dependencyskills/issues/1) — The store | R1, R2, R10, R11, R12 |
| [#2](https://github.com/dependencyskills/dependencyskills/issues/2) — Harvest one jar | R2, R3, R11 |
| [#3](https://github.com/dependencyskills/dependencyskills/issues/3) — Gradle plugin: report what is not indexed | R4, R5, R11 |
| [#4](https://github.com/dependencyskills/dependencyskills/issues/4) — Query | R6, R7, R9, R11 |
| [#6](https://github.com/dependencyskills/dependencyskills/issues/6) — The two-faced index | R9 |
| [#8](https://github.com/dependencyskills/dependencyskills/issues/8) — MCP server | R7, R8, R11 |

#1 through #4 are the tracer bullet: a real project's dependencies searchable with no embeddings, no classifier and no model. #6 and #8 are backlog and depend on how the first four land.

Not yet covered by a story: **R3's bytecode fallback** (measured in [RAD-0012](../research/RAD-0012-structure-from-bytecode.md) and working in `experiments/`, but not sliced), and **R12's second build system** (deliberately deferred until a second one exists to want it).

## Open questions

- **How the tree-sitter native library ships inside a Gradle plugin.** A packaging question rather than a parsing one, and the ground is trodden — the Kotlin Multiplatform JS plugin downloads an entire Node runtime into the same `~/.gradle` root. Must be answered before #2 ships, not before it starts.
- **When Lucene arrives as the derived index.** [RAD-0010](../research/RAD-0010-how-the-codex-is-stored-and-served.md) settles that it does; #4's lexical pass is deliberately naive and the question is whether it is replaced before or after the vector work.
- **Whether the transitive tail should stay opt-in.** R4 takes the safe side of a measured trade. If the miss rate in real use looks like RAD-0022's 11 of 17, the default is wrong.
- **When local preference is authored.** [RAD-0018](../research/RAD-0018-the-selection-ab.md) makes it the only fix for the one gap model progress cannot close, and the first release does not have it. This is the largest deliberate omission in the document.
