# The Datetime `Instant` That Moved

RAD-0043 · 2026-08-21 · v1
Keywords: the correct API changed and the model did not; kotlinx-datetime Instant moved to kotlin.time; why a model writes the old import confidently; a popularity-weighted average over time; why a bigger model does not fix drift; what a version-pinned index closes and what it does not.

Where [the slug case](Research-Studies-RAD-0042-Thirteen-Slug-Functions) is about *space* — a
capability invisible from where the work happens — this one is about *time*. The
correct answer to "how do I get the current instant in Kotlin Multiplatform"
**changed**, and every model trained before the change keeps giving the old one,
confidently, because that is what most of its training data says. This is the
failure mode a fresh, version-pinned codex closes and a bigger model does not:
model capability is a smooth function of scale; a library's public API is a step
function of its release.

Unlike the slug case, everything here is public and inspectable — the
deprecations are annotations you can read in the artifact today.

---

## The move

For years, the canonical way to read the clock in a Kotlin Multiplatform project
was `kotlinx-datetime`:

```kotlin
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant

val now: Instant = Clock.System.now()
```

That idiom is in a very large share of the KMP code written between 2020 and
2025 — tutorials, answers, sample apps, and the libraries built on top of it.

Then the types moved into the **standard library**. `kotlin.time.Instant` and
`kotlin.time.Clock` were added to the Kotlin stdlib in **2.1.20**. In
**kotlinx-datetime 0.7.0**, `kotlinx.datetime.Instant` and
`kotlinx.datetime.Clock` were **deprecated** in favour of the stdlib types,
which are functionally identical. The current idiom is:

```kotlin
import kotlin.time.Clock
import kotlin.time.Instant

val now: Instant = Clock.System.now()   // now a kotlin.time.Instant
```

The migration was handled carefully by the library, which is what makes it a
clean example rather than a mess:

- The deprecations ship **inside the artifact**, each carrying a `ReplaceWith`
  that names the stdlib type — machine-readable migration guidance travelling
  with the code.
- A separate **compatibility release** (`0.7.1-0.6.x-compat`) keeps the old
  `kotlinx.datetime` types available for projects whose *other* dependencies
  still return them.
- Four bridge functions cross the boundary: `toStdlibInstant()`,
  `toStdlibClock()`, and their `toDeprecated…()` inverses.

Everything an author needs to do the right thing is in the 0.7.x sources.

---

## The failure mode

An agent's prior for "current time in KMP" is dominated by five years of
`kotlinx.datetime.Clock.System.now()`. Working in a project already on
kotlinx-datetime 0.7.x, that prior produces one of three wrong outputs, none of
which the model has any signal is wrong:

1. **Deprecated path.** It writes `kotlinx.datetime.Clock` / `Instant` and the
   build lights up with deprecation warnings the author has to chase down —
   the mildest case, and only mild because the library left the deprecated
   names in place.
2. **Unresolved reference.** In the normal (non-compat) artifact the old type is
   gone, so `import kotlinx.datetime.Instant` simply does not resolve, and the
   agent — still reasoning from its prior — "fixes" it by adding the wrong
   dependency or the compat artifact it did not need.
3. **Two incompatible `Instant`s.** The subtlest. A function returns a
   `kotlin.time.Instant` and the agent stores it where a `kotlinx.datetime.Instant`
   is expected (or the reverse), and the two do not interoperate without an
   explicit `toStdlibInstant()` / `toDeprecatedInstant()` bridge. Downstream this
   is where it actually bit the ecosystem: `kotlinx.serialization` had no
   serializer for the moved type at first, and code generators that referenced
   `kotlinx.datetime.Instant` broke on the upgrade.

The tell in all three: the agent is not confused. It is **confident and stale**.

---

## Why a bigger model does not fix it

The instinct is that this is a knowledge gap a stronger model closes. It is not,
and the reason is structural.

- **A model's prior is a popularity-weighted average over its training window.**
  The old idiom outweighs the new one by years of volume no matter how capable
  the model is. Scale makes the model *more* fluent in the majority answer, not
  less — it sharpens the wrong prior.
- **The discriminating fact is version-exact.** Whether `kotlinx.datetime.Instant`
  is the right type depends on which version of one library this project
  resolves. That is not general knowledge; it is a property of *this build*,
  and it flips at a single release boundary.
- **Freshness is not a capability axis.** A model gets better at reasoning; it
  does not get more *recent*. The gap between its training cutoff and the
  library's latest release only ever grows between model updates.

This is the freshness axis from the project's framing: content-value drift
closes with freshness, disambiguation with capability, selection with neither.
The datetime move is a pure freshness case — exactly the one a harvested,
version-pinned codex is built to close.

---

## What the codex does here

The codex harvests the library's **own current words, pinned to the resolved
version**. For kotlinx-datetime 0.7.x that means the entry for `Clock.System.now`
carries the `@Deprecated` annotation and its `ReplaceWith`, the pointer to
`kotlin.time.Clock`, and the existence of the bridge functions — because all of
that is right there in the 0.7.x `-sources.jar` the build already resolves. An
agent asking "how do I get the current time here" is answered from **the version
in front of it**, not from the average of everything ever written about it.

No model retraining, no bespoke skill authored for the migration, no human
noticing the release and writing a note. The correction rides along with the
dependency, because the dependency is where the correct answer lives and when it
changed.

---

## What it will not fix

- **A codex pinned to the wrong version is wrong too.** The value is entirely in
  matching the entry to the *resolved* version; harvest the wrong one and you
  have reproduced the model's staleness with extra steps. Version-exactness is
  not a nice-to-have here, it is the whole mechanism.
- **The move still has to have happened in the sources.** The codex is only as
  fresh as the artifact. It closes the model-cutoff-to-release gap; it cannot
  see a change that has not shipped.
- **Interop bugs across a graph are not purely local.** When one dependency
  returns the old type and another expects the new, the fix is a bridge call at
  the seam — the codex can surface that the two types differ and that a bridge
  exists, but choosing where to convert is still design.

---

## Reusable heuristics

- When an API for something as basic as "the current time" fails to resolve or
  warns as deprecated, suspect a **recent move**, not your own error — check the
  resolved version of the owning library before trusting your prior.
- Prefer the type the **project's resolved version** exposes. For time in Kotlin
  today that is `kotlin.time.Instant` / `kotlin.time.Clock`; reach for
  `kotlinx.datetime` only for the calendar types it still owns.
- When two libraries hand you two spellings of the same concept, look for a
  **bridge function** before assuming they interoperate — here,
  `toStdlibInstant()` / `toDeprecatedInstant()`.
- Treat "the answer everyone knows" for a fast-moving library as a **prior to
  verify against the current sources**, not a fact.

---

## Sources

- [kotlinx-datetime README — 0.7.0 deprecation, `0.7.1-0.6.x-compat`, and the conversion functions](https://github.com/Kotlin/kotlinx-datetime)
- [`DeprecatedInstant.kt` — the deprecation as it ships in the artifact](https://github.com/Kotlin/kotlinx-datetime/blob/master/core/common/src/DeprecatedInstant.kt)
- [kotlinx.serialization #3026 — no replacement serializer for the moved type](https://github.com/Kotlin/kotlinx.serialization/issues/3026)

Verified 2026-08-21. The version boundary is the point: kotlin.time.Instant/Clock
added in Kotlin 2.1.20; deprecation of the kotlinx.datetime types in
kotlinx-datetime 0.7.0.
