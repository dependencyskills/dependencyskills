---
title: The dependency nobody declared
description: Most of what compiles was never declared, and an agent cannot tell the difference — the importable-set measurement felt from the agent's side.
---

The other case studies are about a right answer being hard to reach. This one is
the opposite failure: **too much is reachable.** Nearly everything an agent can
import compiles and works — including a large surface the project never asked for
and does not control. It is the **provenance** axis: not *can I find it* or *is
it fresh*, but *is this actually mine to build on*. It is the concrete face of
this project's headline measurement — that the **importable** set, not the
declared set, is what an agent really works against. The full record is in the
repository under `docs/knowledge/case-studies/`.

## The setup

Build tools separate dependencies a library uses *internally* from ones it
*exposes*. On the JVM, Gradle calls these `implementation` and `api`, and an
`api` dependency leaks onto the consumer's classpath. The canonical example:
**OkHttp** returns **Okio** types from its public API — a response body is an
`okio.BufferedSource` — so in any project depending on OkHttp this compiles:

```kotlin
import okio.ByteString   // nothing in your build file mentions Okio
```

Nothing names Okio; the IDE autocompletes it, it compiles, tests pass. A
first-rate library, arrived as a side effect of depending on something else. And
it is not an edge case: across four JVM graphs this project measured, the
importable set was **86–99%** of the resolved graph, and **100%** in npm and
Python. **Most of what compiles was never declared.**

## The failure mode

An agent gathers context from the classpath — what imports, what compiles — and
that view **cannot distinguish** the project's declared, controlled core from the
incidental surface that rode in transitively. So it builds on `okio.*` directly,
three hops from anything declared. It works. Then:

- **A bump** — a new OkHttp drops or changes that Okio type, and code breaks in a
  file that did not change.
- **A swap** — OkHttp replaced by another client, and the `okio.*` imports
  evaporate, though the change was "swap the HTTP client."
- **A collision** — the project later declares Okio at a different version than
  OkHttp pulled, and the two disagree.

Every break is at a distance from the edit, rooted in a dependency edge nobody
wrote down.

## The importable-set number, felt

Importable ≫ declared sounds like an argument about how many skills a codex must
carry. From the agent's side it is a **correctness** fact: the gap between
declared and importable is exactly the surface an agent will build on *thinking
it is stable* when it is incidental. This is why the design **weights declared
over transitive** — a declared dependency is one the project chose and controls;
a transitive one is a detail of someone else's build that can move without
notice. The codex presents the declared core as the thing to build on, and marks
the transitive surface as reachable but not yours.

## Why a bigger model does not fix it

- **The classpath does not carry provenance.** Once code compiles, declared and
  transitive look identical; no reasoning recovers a fact the input lacks.
- **The signal is in the build graph, not the code** — which the codex indexes
  and a model reading source does not see.
- **It is a property of *this* build**, not general knowledge.

## What it will not fix

- Sometimes the transitive type is the right tool, and the fix is to **declare
  it**, not avoid it — the codex flags the undeclared use; adopting it is still a
  choice.
- **Provenance is not quality.** "Declared" means controlled and chosen, not
  better.
- In a monorepo, a sibling module's type is neither declared-external nor a
  stranger's transitive — provenance is a spectrum, not two buckets.
