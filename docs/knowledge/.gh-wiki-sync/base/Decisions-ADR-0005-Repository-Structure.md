# One repository, split by whether a human is driving

ADR-0005 · 2026-08-12 · Status: accepted · v4
Keywords: how should this repository be laid out; one repo or one per ecosystem; why not -gradle, -maven, -ivy repos; where do spikes and prototypes live; poc versus experiments; what belongs in implementations; splitting by ecosystem versus by whether a human is driving; where does code that depends on no build system go; why the codex is not under gradle; one build root per directory; where did the publisher plugin go; why is there no publish-side implementation.

**v4 (2026-08-27) — there is no publish-side implementation, and the paragraph below that assumes one is stale.**

"Splitting `publish/` from `consume/`" argues against a directory split partly on the grounds that one Gradle jar would register both plugin ids. Only one id exists now: the v1 publisher was deleted when ADR-0009 settled that content comes from the sources jar a library already publishes, which leaves an author nothing bespoke to author and that plugin nothing to check. The decision is untouched — the split still belongs in the spec rather than the tree — but a reader should not go looking for the second half in the tree.

**v3 (2026-08-27) — `implementations/` is one build root per directory, and not all of them are build systems.**

This record said "one directory per **build system**" and then, four paragraphs later, "a CLI or an MCP server belongs here too". Both were true and they disagreed; building the codex made the disagreement load-bearing, so it is resolved here in favour of what turned out to be right.

**The rule is now: each directory under `implementations/` is its own build root** — its own settings file, its own wrapper, buildable and releasable alone. Most are build-system implementations and the per-build-system reasoning below still governs those, unchanged. `codex/` is not one, and the exception is the point rather than an untidiness.

**Why the codex cannot live under `gradle/`.** The store, and in time the harvester, the query layer and the server, depend on no build system at all. A Maven plugin, a CLI and the MCP server all have to use the store — so the store must not be a Gradle artifact. Filing it under `gradle/` would have made it one by proximity, which is the same mistake as filing by ecosystem, one level along: naming a thing after one of its consumers. The Gradle plugins depend on the codex; the codex depends on nothing of theirs, and separate build roots are what keeps that true rather than merely intended.

A plugin reaches it with `includeBuild("../codex")`, which Gradle substitutes — so the seam costs nothing at the call site.

**What this does not change.** One repository; the human-driving line between `implementations/` and `integrations/`; per-build-system filing for the directories that *are* build systems, including the KMP argument below, which is the load-bearing part of this record and is untouched.

The trade is that "implementations" is now a slightly loose word for the directory — it holds deployables, and one of them is a library. That was accepted over the alternatives: a second top-level directory for one occupant is the prediction this record warns about under `integrations/`, and nesting the codex inside a build system is the error being avoided.

ADR-0001 and ADR-0002 belong to an earlier project. Some later numbers are
gaps, left where a premature record was withdrawn to be re-decided from
research.

**v2 (2026-08-19):** added `experiments/` as the home for spikes — renamed from
`poc/` to match the tree's spelled-out naming — and drew the line that
`implementations/` is working code only, never a test harness.

## Context

This project is a convention plus reference implementations of it, across
several package ecosystems and potentially several editors. The question is
what lives together and what does not.

Three earlier attempts at the split were rejected in discussion, and the
reasons are worth keeping because each looks correct until it meets a
concrete case.

**Separate repositories per ecosystem** (`-gradle`, `-maven`, `-ivy`). The
convention — classifier name, variant attributes, capability format,
archive layout — is the one thing every implementation must agree on, and
three repositories means three places for it to fork. The failure would
surface at the worst moment: an archive published by one implementation
that another cannot read. The test that catches this publishes with one and
consumes with another, and it cannot exist across separate repositories.

**Splitting `publish/` from `consume/`.** These are genuinely different
audiences — a library author adopts publishing, a consuming project adopts
harvesting — but they are the same build tool. One Gradle jar registers
both plugin ids, shares the spec constants, and versions once. A project
that is both a library and a consumer applies both. Splitting the directory
would mean two builds and duplicated plumbing for one artifact. **The split
belongs in the spec, where `publishing.md` and `discovery.md` are separate
documents, not in the tree.**

**Splitting `plugins/` from `extensions/`.** The ecosystems do not agree on
the words. VS Code ships extensions, JetBrains ships plugins, Gradle ships
plugins. A JetBrains integration and a Gradle publisher would both be
"plugins" in different buckets, and npm has no plugin mechanism at all.

## Decision

**One repository**, `dependencyskills/dependencyskills`, holding the spec,
the implementations, the conformance harness, the fixtures and the experiments.

**`implementations/`** — one **build root** per directory: its own settings
file, its own wrapper, buildable alone. Headless: runs in a build, in CI,
with no human present.

Most are one per **build system**, not per package ecosystem. The exception
is `codex/`, which depends on no build system and must not — see v3 above.

The distinction only surfaces on cross-compiling toolchains, and Kotlin
Multiplatform is the mainstream one: a single KMP source set publishes to
Maven as a JVM jar, an Android AAR and native and JS klibs, to npm for JS
and wasm consumers, and to SPM or CocoaPods for Apple targets. The build is
Gradle, so **the Gradle implementation is responsible for every one of those
channels** — a KMP library reaches npm consumers through Gradle and never
touches `implementations/npm`, which is for a package genuinely authored in
npm. Filing by ecosystem would have split one build's responsibilities
across four directories.

The consequence is accepted rather than regretted: the Gradle implementation
carries far more than the others, and its per-channel emit step is core
rather than a later nicety. Without it a KMP library ships a skill its iOS
and JS consumers cannot see.

A CLI or an MCP server belongs here too — in `codex/`, since neither is
tied to a build system.

**`integrations/`** — editor and agent integrations, which have a human on
the other end. **Not created yet, deliberately**, because there is nothing
to put in it and an empty directory is a prediction rather than a fact.
When the first one exists, create it then.

The line between them is whether a human is driving. It is a real seam:
a Gradle plugin and a VS Code extension share no language, no build system,
no distribution channel and no release cadence. Publishing and harvesting at
least share a jar; these share nothing. Where the line blurs, it blurs
toward `implementations/`, since `integrations/` is the narrower claim.

**No root build.** Each implementation is self-contained with its own build
system — Gradle for one, Maven for another, npm for a third. `conformance`
shells out to whichever are present, which is also what a third-party
implementer would run against their own.

**`experiments/`** — spikes and proofs of concept: code written to find
something out, not to ship. The usual standards are deliberately off here and
nothing leaves by being copied (see `experiments/README.md`). An experiment is
**self-contained** — its data and its runnable harness live together in one
directory (test0 carries a graded source set and a Gradle harness side by side).
It is explicitly **not** `implementations/`: a test harness is not a reference
implementation, and putting one there would hold a throwaway spike to production
standards and blur what `implementations/` means. Renamed from `poc/` in v2 to
match the tree's spelled-out naming.

## Consequences

- The spec versions with the implementations, so nobody reads spec v2
  against a plugin built for v1. This is the main reason for the monorepo
  and the main thing that would be lost by splitting.
- Implementations will not be the same size, and should not be made so.
  Maven and Gradle never unpack a dependency and need a published sidecar;
  npm and SPM already expose files on disk and need an emit step. The
  asymmetry is the argument the project is making.
- This project's own skills live at `implementations/agent-skills/`, a
  **sibling** of `gradle/` and `codex/` rather than inside either. (Moved from
  the repository root on 2026-08-29; the reasoning below is unchanged and was
  the reason for the sibling placement rather than a per-implementation copy.
  What changed is only that it now sits with the rest of the source.)

  They are spec-level — the skill teaches the convention, and only its install template differs per build system. A copy
  per implementation would drift, which is what the monorepo exists to
  prevent. It also dogfoods the rule libraries are asked to follow: one
  authoring location, many publication channels.
- Ecosystem-specific decisions get their own ADRs, decided separately
  rather than folded in by amendment; a project-wide decision (like the
  index) is recorded once.
- If release cadences genuinely diverge later, the seam to split along is
  `implementations/` versus `integrations/`, because nothing crosses it.
- CI runs several toolchains rather than one. Accepted.
