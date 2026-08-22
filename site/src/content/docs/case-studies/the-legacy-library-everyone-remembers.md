---
title: The legacy library everyone remembers
description: An agent reaches for Moment.js in new code because its prior knows it best — and why "which date library" is half a fact the codex closes and half a gap nothing does.
---

The first two case studies are about a *right* answer that is hard to find — in
[space](/case-studies/thirteen-slug-functions/) and in
[time](/case-studies/the-datetime-instant-move/). This one is about a field with
*several* answers, where the agent reaches for the one it has seen most. It is
the **selection** axis: the gap that closes with neither freshness nor model
capability. Everything here is public — the library announces its own status in
its own docs. The full record is in the repository under
`docs/knowledge/case-studies/`.

## The field

For roughly a decade, **Moment.js** was how you did dates in JavaScript — and so
it dominates the corpus a model's prior is built from. Its maintainers then
declared the project **"a legacy project in maintenance mode"** (their words,
since 2020) and told people to stop using it for new work: a large,
non-tree-shakeable bundle and a mutable API, to the point that Chrome DevTools
flags it by name. They point at the live alternatives — **Luxon**, **Day.js**,
**date-fns**, **js-Joda**, and the language-level **Temporal** API.

So the field has one library everyone *remembers* and four the maintainers
*recommend*.

## The failure mode

Ask an agent to add date handling to a new project and the default output is
`moment()` — not because Moment is broken, but because the prior is a
decade-deep popularity average and Moment sits on top of it. The agent installs
a maintenance-mode dependency into greenfield code, with complete confidence. The
discriminating signal exists — Moment says "I am legacy" on its own front page —
but it is nowhere in the model's weighting, because weighting is volume, and the
volume predates the deprecation.

## Two problems, not one

"Which library" is really two questions that close differently:

- **"Not this one."** That Moment is legacy and a live alternative should be
  preferred is a fact *the library itself now carries*. A codex that harvests
  Moment's current docs surfaces "maintenance mode — prefer Luxon / date-fns /
  Day.js" the moment the project resolves it. **The codex closes this half** —
  freshness, pointed at selection.
- **"Then which one?"** Choosing among Luxon, Day.js, date-fns and Temporal
  depends on things no library knows: the project's bundle budget, immutable
  objects vs tree-shakeable functions, whether the team is standardising on
  `Temporal`, what the codebase already uses. **Nothing closes this half** — not
  fresh docs, because it is in no library's docs; not a bigger model, because it
  is not a knowledge question. It needs a local, project-owned layer.

This is why the design keeps a local layer above the harvested codex rather than
pretending the codex can rank the alternatives on its own. Overlap resolves
partly to published signal and partly to a decision no library can make for you.

## What it will not fix

- The codex can say *prefer a live alternative*; it cannot say *prefer this one*
  without inventing a project preference it has no basis for.
- A recommendation can itself go stale — the codex is only as right as the
  version of that page it last harvested.
- **"Popular" is not "preferred."** Day.js being the easy migration from Moment
  does not make it the right choice over date-fns or Temporal — and the codex
  must not collapse selection back into popularity, the very failure it corrects.
