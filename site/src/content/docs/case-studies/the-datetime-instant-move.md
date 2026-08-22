---
title: The datetime Instant that moved
description: A model keeps writing kotlinx.datetime.Clock after the types moved to the standard library — the freshness failure a version-pinned codex closes and a bigger model does not.
---

Where [the slug case](/case-studies/thirteen-slug-functions/) is about *space* —
a capability invisible from where the work happens — this one is about *time*.
The correct answer to "how do I get the current instant in Kotlin Multiplatform"
**changed**, and every model trained before the change keeps giving the old one,
confidently, because that is what most of its training data says. Model
capability is a smooth function of scale; a library's public API is a step
function of its release. The full record is in the repository under
`docs/knowledge/case-studies/`.

Everything here is public and inspectable — the deprecations are annotations you
can read in the artifact today.

## The move

For years the canonical way to read the clock in a KMP project was
`kotlinx-datetime`:

```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

val now: Instant = Clock.System.now()
```

Then the types moved into the **standard library**. `kotlin.time.Instant` and
`kotlin.time.Clock` were added to the Kotlin stdlib in **2.1.20**, and
**kotlinx-datetime 0.7.0** deprecated its own `Instant` and `Clock` in favour of
them. The current idiom is `kotlin.time.Clock.System.now()`, returning a
`kotlin.time.Instant`. The library did the migration cleanly: the deprecations
ship inside the artifact with a `ReplaceWith` naming the stdlib type, a
`0.7.1-0.6.x-compat` release keeps the old types for projects that still need
them, and bridge functions (`toStdlibInstant()`, `toDeprecatedInstant()`, …)
cross the boundary.

## The failure mode

An agent's prior for "current time in KMP" is dominated by five years of
`kotlinx.datetime.Clock.System.now()`. Working in a project already on 0.7.x,
that prior produces one of three wrong outputs — and the agent has no signal any
of them is wrong:

- **Deprecated path** — it writes the old `kotlinx.datetime` types and the build
  fills with deprecation warnings.
- **Unresolved reference** — in the normal artifact the old type is gone, so the
  import does not resolve, and the agent "fixes" it by pulling the wrong
  dependency.
- **Two incompatible `Instant`s** — a `kotlin.time.Instant` meets a
  `kotlinx.datetime.Instant` and they do not interoperate without an explicit
  bridge. This is where it bit the ecosystem downstream: `kotlinx.serialization`
  had no serializer for the moved type at first, and code generators referencing
  the old one broke on the upgrade.

The agent is not confused. It is **confident and stale**.

## Why a bigger model does not fix it

- **A model's prior is a popularity-weighted average over its training window.**
  The old idiom outweighs the new one by years of volume; scale sharpens the
  wrong prior, it does not unlearn it.
- **The discriminating fact is version-exact** — a property of *this build*, not
  general knowledge, flipping at a single release boundary.
- **Freshness is not a capability axis.** A model gets better at reasoning; it
  does not get more recent, and the gap to the library's latest release only
  grows between model updates.

Content-value drift closes with freshness, disambiguation with capability,
selection with neither. This is a pure freshness case.

## What the codex does

It harvests the library's **own current words, pinned to the resolved version**.
For 0.7.x the entry for `Clock.System.now` carries the `@Deprecated` annotation,
its `ReplaceWith`, the pointer to `kotlin.time.Clock`, and the bridge functions —
because all of that is in the 0.7.x `-sources.jar` the build already resolves. An
agent is answered from the version in front of it, not from the average of
everything ever written about it. No retraining, no bespoke skill, no human
noticing the release: the correction rides along with the dependency, because
that is where the correct answer lives and when it changed. This is the same fix
as at [ecosystem scale](/findings/) — make the current answer findable at the
moment of need — turned to the axis of time.
