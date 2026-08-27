# The Legacy Library Everyone Remembers

RAD-0044 · 2026-08-21 · v1
Keywords: the agent picked the library it has seen most, not the one we use; several valid answers in one field; why this is two problems rather than one; the part freshness closes and the part nothing does; the selection axis; local preference that no model can have.

The first two case studies are about a *right* answer that is hard to find — in
[space](RAD-0042-thirteen-slug-functions.md) and in
[time](RAD-0043-the-datetime-instant-move.md). This one is about a field with
*several* answers, where the agent reaches for the one it has seen most, and
where "which one" splits cleanly into a part a fresh codex closes and a part
nothing does. It is the **selection** axis: the gap that closes with neither
freshness nor model capability.

Everything here is public. The library in question announces its own status in
its own docs.

---

## The field

For roughly a decade, **Moment.js** was how you did dates in JavaScript. It is in
an enormous share of the tutorials, answers, and code written across that period,
and that is exactly the corpus a model's prior is built from.

Moment's maintainers then did something unusually clear-eyed: they declared the
project **"a legacy project in maintenance mode"** (their words, since 2020) and
told people to stop using it for new work. The reasons are concrete — a large,
non-tree-shakeable bundle and a mutable API — and browsers took notice: Chrome
DevTools flags Moment by name for its size. The maintainers point at the live
alternatives: **Luxon**, **Day.js**, **date-fns**, **js-Joda**, and the
language-level **Temporal** API.

So the field has one library everyone *remembers* and four the maintainers
*recommend*.

---

## The failure mode

Ask an agent to add date handling to a new project and the default output is
`moment()`. Not because it is wrong that Moment works — it does — but because the
prior is a decade-deep popularity average, and Moment sits on top of it. The
agent installs a maintenance-mode dependency into greenfield code, ships the
bundle-size problem its own toolchain would flag, and does it with complete
confidence.

The discriminating signal *exists* — Moment says "I am legacy" on its own front
page — but it is nowhere in the model's weighting, because weighting is volume,
and the volume predates the deprecation.

---

## Why this is two problems, not one

"Which library" looks like a single question. It is two, and they close
differently:

**Part one — "not this one."** That Moment is legacy and a live alternative
should be preferred is a *fact the library itself now carries*, in its current
docs and status. This is the freshness axis again, pointed at selection: a codex
that harvests Moment's own current words surfaces "maintenance mode — prefer
Luxon / date-fns / Day.js" the moment the project resolves it. **The codex closes
this half**, exactly the way it closes the datetime move.

**Part two — "then which one?"** Choosing among Luxon, Day.js, date-fns and
Temporal is not a fact any of them can carry. It depends on things no library
knows: this project's bundle budget, whether the team wants an immutable
object model or a tree-shakeable pile of pure functions, whether they are
standardising on the platform `Temporal` API, what the rest of the codebase
already uses. **Nothing closes this half** — not fresh docs, because it is not
in any library's docs; not a bigger model, because it is not a knowledge
question. It is a local preference, and it needs a local layer to answer.

This is the project's position on selection stated as a worked case: overlap
resolves partly to published, harvestable signal (scope, direction, a library's
own deprecation notice) and partly to a decision **no library can make for
you** — which is why the design keeps a local, project-owned layer above the
harvested codex rather than pretending the codex can rank the alternatives on
its own.

---

## Why a bigger model does not fix part two

The freshness half yields to fresh data. The selection half does not yield to
scale, because there is no correct answer in general — only a correct answer
*for this project*, and that answer was never written down anywhere a model or a
codex could read it. A more capable model chooses more fluently among the
alternatives; it still cannot know that this team banned new runtime
dependencies last quarter, or that they are mid-migration to `Temporal`. That
fact lives in the project, so the tool that applies it has to live there too.

---

## What it will not fix

- **The codex can say "prefer a live alternative"; it cannot say "prefer *this*
  one."** If it tried, it would be inventing a project preference it has no basis
  for — the selection gap dressed up as an answer.
- **A recommendation can itself go stale.** Moment's list of alternatives is
  current today; the codex is only as right as the version of that page it last
  harvested. Freshness is load-bearing even for the half it does close.
- **"Popular" is not "preferred."** Day.js's API-compatibility with Moment makes
  it the *easy* migration, not automatically the *right* choice for a project
  that would be better served by date-fns or Temporal. The codex must not
  collapse selection back into popularity — the very failure it is correcting.

---

## Reusable heuristics

- Before adding a well-known library to new code, check its **own current
  status** — the maintainers may have deprecated it in favour of alternatives
  you were not weighting. Your prior is a decade behind the front page.
- Split "which library" into **"not the legacy one"** (a published, checkable
  fact) and **"which live one"** (a project decision). Answer the first from the
  library's own docs; escalate the second to whoever owns the project's
  conventions.
- When several libraries do the same job, the tie-breaker is almost never in any
  of their docs — it is in **your** codebase. Look at what the project already
  depends on before introducing a fifth option.
- Treat "everyone uses X" as evidence about the **past**, not about what this
  project should adopt today.

---

## Sources

- [Moment.js docs — "a legacy project in maintenance mode"](https://momentjs.com/docs/)
- [Moment.js project-status recommendations — Luxon, Day.js, date-fns, js-Joda, Temporal](https://github.com/moment/momentjs.com/blob/master/docs/moment/-project-status/01-recommendations.md)

Verified 2026-08-21. Moment.js entered maintenance mode in September 2020; the
recommendation list is current as of harvest.
