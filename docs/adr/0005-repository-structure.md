# ADR-0005: One repository, split by whether a human is driving

Date: 2026-08-12 · Status: accepted

Numbering continues from the decisions moved here from the story-tools
repo; ADR-0001 and ADR-0002 are not part of this project and were left
behind.

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
the implementations, the conformance harness and the fixtures.

**`implementations/`** — one directory per package ecosystem. Headless:
runs in a build, in CI, with no human present. Each covers publishing,
harvesting, or both, and a table in its README says which. A CLI or an MCP
server belongs here too.

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

## Consequences

- The spec versions with the implementations, so nobody reads spec v2
  against a plugin built for v1. This is the main reason for the monorepo
  and the main thing that would be lost by splitting.
- Implementations will not be the same size, and should not be made so.
  Maven and Gradle never unpack a dependency and need a published sidecar;
  npm and SPM already expose files on disk and need a mirror step. The
  asymmetry is the argument the project is making.
- If release cadences genuinely diverge later, the seam to split along is
  `implementations/` versus `integrations/`, because nothing crosses it.
- CI runs several toolchains rather than one. Accepted.
