# The Dependency Nobody Declared

RAD-0045 · 2026-08-21 · v1

The other case studies are about a right answer being hard to reach. This one is
the opposite failure: **too much is reachable.** Nearly everything an agent can
import compiles and works — including a large surface the project never asked
for and does not control. An agent builds on that surface because the compiler
accepts it, and the compiler accepting it is not the same as it being safe to
depend on. This is the **provenance** axis: not *can I find it* or *is it fresh*,
but *is this actually mine to build on*.

It is the concrete face of this project's headline measurement — that the
**importable** set, not the *declared* set, is what an agent is really working
against.

---

## The setup

Build tools distinguish dependencies a library uses *internally* from ones it
exposes in its *public API*. On the JVM, Gradle calls these `implementation` and
`api`; an `api` dependency leaks onto the consumer's classpath, because you need
its types to call the library at all.

The canonical example: **OkHttp**'s public API returns **Okio** types — a
response body is read as an `okio.BufferedSource`. So Okio is an `api`
dependency of OkHttp, and in any project that depends on OkHttp this compiles:

```kotlin
import okio.ByteString   // nothing in your build file mentions Okio

val hash = someByteString.sha256()
```

Nothing in the build file names Okio. The IDE autocompletes it, it compiles, the
tests pass. It is a real, first-rate library — and it arrived as a side effect of
a dependency on something else.

This is not an edge case. Across four JVM dependency graphs this project
measured, the **importable** set — everything a developer can call without
touching the build file — was **86–99%** of the resolved graph, and in npm and
Python it was **100%**. Most of what compiles was never declared.

---

## The failure mode

An agent gathers context from the classpath: what can I import, what types
exist, what compiles. That view **cannot distinguish** the project's declared,
controlled core from the incidental surface that rode in transitively. Both look
identical — both autocomplete, both compile.

So the agent builds on `okio.*` directly, three hops from anything the project
declared. It works. Then one of these happens:

1. **The direct dependency is bumped** and changes or drops its transitive — a
   new OkHttp that no longer exposes that Okio type, or exposes a different
   version — and code the agent wrote breaks, in a file that did not change,
   because a dependency the project never declared moved underneath it.
2. **The direct dependency is replaced** — OkHttp swapped for Ktor's client —
   and the `okio.*` imports evaporate, even though the change was "swap the HTTP
   client," which had nothing visibly to do with Okio.
3. **Two versions collide** — the project later declares Okio directly at a
   different version than the one OkHttp pulled, and now the transitive and the
   declared disagree.

In every case the break is at a distance from the edit, and the root cause is a
dependency edge nobody wrote down.

---

## Why this is the importable-set number, felt

The measurement — importable ≫ declared — sounds like an argument about how many
skills a codex must carry. This is the same fact from the agent's side: the
gap between declared and importable is precisely the surface an agent will build
on *thinking it is stable* when it is incidental. The number is not just a
budgeting concern; it is a **correctness** concern about what the agent chooses
to depend on.

It is also why the design **weights declared over transitive** — a decision
first reached for a different reason (it roughly tenfolds the prompt-injection
surface an agent trusts) but which lands here too. A declared dependency is one
the project chose and controls; a transitive one is a detail of someone else's
build that can change without notice. The codex should present the declared core
as the thing to build on, and mark the transitive surface as what it is: reachable,
but not yours.

---

## Why a bigger model does not fix it

- **The classpath does not carry provenance.** By the time code compiles, the
  distinction between declared and transitive is gone; a `import okio.ByteString`
  looks the same either way. No amount of reasoning recovers a fact the input
  does not contain.
- **The signal is in the build graph, not the code.** Whether Okio is declared,
  and by whom, lives in the resolved dependency graph — which is exactly what the
  codex indexes and the model, reading source, does not see.
- **It is a property of *this* project's build**, not general knowledge. Whether
  `okio.ByteString` is safe to depend on here depends on whether this build
  declares Okio — a per-project fact, not something learnable at scale.

---

## What it will not fix

- **Sometimes the transitive type is genuinely the right tool**, and the fix is
  to *declare* it, not avoid it — promote it to a direct dependency so the
  project controls its version. The codex can flag "you are using an undeclared
  dependency"; deciding to adopt it is still a choice.
- **Provenance does not imply quality.** A declared dependency can be worse than
  a transitive one. "Declared" means *controlled and chosen*, not *better* — the
  guidance is about stability of what you build on, not a ranking.
- **First-party modules complicate the line.** In a monorepo, a type from a
  sibling module is neither a declared external dependency nor a stranger's
  transitive; provenance is a spectrum, and the codex has to model more than two
  buckets.

---

## Reusable heuristics

- If you import from a package your **build file does not name**, stop: it is
  arriving transitively. Either declare it directly (so its version is yours to
  control) or find the equivalent in a dependency you *did* declare.
- **"It compiles" is not "it's mine."** The compiler accepts the whole importable
  surface; only a fraction of it is under this project's control.
- Prefer to build on **declared** dependencies. When a break appears in an
  unchanged file after a dependency bump, suspect a transitive that moved.
- When you do want a transitive type, the fix is usually to **promote it to a
  declared dependency**, not to route around it.

---

## Connections

- [Cost of a Skill Per Dependency](Research-RAD-0001-Cost-Of-A-Skill-Per-Dependency)
  — the importable-vs-declared measurement this case makes concrete.
- [External Review of the Publishing Proposal](Research-RAD-0004-External-Review-Of-The-Proposal)
  — where weighting declared over transitive was adopted (a ~10× filter on the
  trusted surface).
