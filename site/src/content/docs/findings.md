---
title: The findings
description: What the measurement showed, across four package ecosystems.
---

Measured across twelve public projects in four package ecosystems. Full method
and per-record findings are in [the research](/research/).

## There is no cheap floor

The number that matters is not what a project *declares* — it is what a
developer can *call*. An `api` dependency exposes its own dependencies to
consumers: the IDE autocompletes them, they compile, and nothing in the build
file mentions them. That **importable set** is most of the graph.

| Project | Ecosystem | Declared | Importable | Resolved |
|---|---|---|---|---|
| Ktor sample | JVM | 8 | 63 | 65 |
| Spring PetClinic | JVM | 16 | 112 | 113 |
| Now in Android | Android / KMP | 102 | 311 | 360 |
| Next.js app (p90) | npm | — | 995 | 995 |

On the JVM the importable set is **86–99%** of the resolved graph; in npm and
Python it is **100%**. One skill per library therefore costs **20k–139k tokens
resident** before any work begins — there is no small "declared" set to
retreat to.

## The documentation already travels

Every major ecosystem already ships a library's own words alongside the code:

- **JVM / KMP** — 93–98% of artifacts publish a `-sources.jar` (KDoc /
  Javadoc), including Kotlin Multiplatform root modules and 98% of Android
  AARs.
- **Go** — 100% doc-comment coverage across a 463-module graph, examples
  compiled and tested.
- **Swift** — a curated DocC catalogue *inside* the package, with per-version
  migration guides.
- **npm / Python** — sources are already exploded on disk; the doc comments
  are right there.

## The JVM's disadvantage is reachability, not content

This is the gap the project exists to close, and it is specifically a JVM and
Kotlin Multiplatform problem. **Gradle and Maven never unpack.** A resolved
dependency is an archive in a cache — `~/.gradle/caches`, `~/.m2` — and nothing
in it is on disk to scan, the way an npm package sits exploded in
`node_modules` or a Python one in `site-packages`. So a library ships all of
its documentation and the agent still cannot reach it.

Kotlin Multiplatform is the sharpest case: an Android AAR silently drops
`commonMain` resources, and a Kotlin/Native klib packages them not at all. The
docs exist; the packaging keeps them out of reach of the *directory-scan*
convention every other ecosystem relies on — not out of reach of a reader (an
IDE indexes these same archives, unopened, all day), but out of reach of a
tool that only knows how to scan a folder, which is what every existing skill
convention is. Closing the gap means reading the archive, not unpacking it.

## Coverage is cultural, not technical

How much gets documented is a convention, not a limit of the tooling. The
median library documents **33%** of its public declarations — but Java-majority
libraries reach **84%** and Kotlin-majority ones **30%**, on identical tooling.

## Selection, though, is an npm problem — not a JVM one

The one axis where the JVM is *ahead*. `api` versus `implementation`, plus
configuration roles, filter candidates before an agent ever sees them; npm's
flat `node_modules` does not. Genuine overlap between competing libraries — two
date libraries, three HTTP clients — is **pervasive in npm and essentially
absent** in four JVM graphs once restricted to the importable set. Now in
Android's apparent "three JSON libraries" collapses to one the moment you scan
what is importable rather than what is merely present. On selection, the JVM
has the richer metadata and the smaller problem.
