---
title: What the tests show
description: What the experiments measured — where an index of library capabilities changes what a coding agent does, and where it does not.
---

The project's central claim is testable: does giving a coding agent an index of a
library's capabilities actually change what it writes? We ran it — real coding tasks,
through real developer agents from **two different vendors** — Claude Code and
Google's Antigravity (Gemini) — scored on whether the agent used an existing
capability or reinvented it from scratch.

## The one-line conclusion

**The index is worth exactly the model's knowledge gap.** Where a model already knows
a library, the index adds nothing. Where it does not, the value is real and reliable.

## What we measured

- **When the capability is genuinely new to the model, the index flips its
  behaviour.** On invented capabilities the model could not have seen before, agents
  reinvented the functionality *every time* without the index and used the provided
  capability *every time* with it — the same result on both vendors' agents.
- **A bare signature is enough to be used.** The entry does not need rich prose to
  change behaviour — the symbol and its signature alone carry it. Prose earns its keep
  at the margins, where the signature cannot tell the agent whether the capability fits
  the task at hand (and a cautious model leans on that prose more than a trusting one).
- **For a well-known public library, a current model needs no help.** Given real,
  popular libraries — kotlinx-datetime and Arrow — current agents already wrote the
  correct, current API with no index at all. Even **kaml**, a YAML library that
  documents just 4% of its public surface, was known cold: models learn APIs from
  *code*, not only from docs. Documentation coverage is not the same as how well a
  model knows a library.
- **The value returns the moment there is a gap.** Point the same test at an older
  model (an earlier Gemini) — one whose training predates a library's API change — and
  without the index it writes the outdated API, while with the index it writes the
  current one. The index closes the gap.
- **A model you run yourself gets the biggest lift of all — and size doesn't rescue it.**
  We ran the same test against nine open models on a laptop, from a tiny 270M up to a
  dense 70B, five model families. On the invented capabilities they all flipped just like
  the frontier agents — from reinventing every time to using the provided one every time.
  And where a current frontier model showed *no* lift on real libraries because it already
  knew them, the local models were **stale** — reaching for APIs that had been *removed* —
  and the index corrected them. Crucially, this held **all the way up to the 70B**: the
  biggest, most capable local model was still stale on every one of the real libraries,
  and the index made it current. The value tracks the model's *training gap* to the code
  on the classpath, **not its parameter count** — a big local model is not automatically
  up to date on the exact version you depend on. (The only floor is the model's own
  competence: a 270M can only use a capability it is capable of writing at all.)

## What it means

The index earns its keep exactly where a model's training falls short:

- **Your own code** — private, internal, first-party modules that were never in any
  training set.
- **Version drift** — libraries that changed after a model was trained, or a project
  pinned to a version the model does not default to.
- **Smaller and local models** — the ones you run yourself. They know less, so they
  lean on the index more, and the test bears this out directly: a small open model on a
  laptop, stale on real libraries and blank on new ones, is pulled up to using the
  correct current API by the index. This is where the index arguably matters most — it
  can make a private, personal model behave well enough to actually use.
- **Genuinely new libraries** — not yet absorbed into the training corpus.
- **Your project's own conventions** — which library it prefers among several that all
  fit. This is *local* knowledge, in no training set, and the test shows it is the one
  thing model progress **cannot** fix: with both libraries on the classpath, even the
  strongest current agents reverted to their own habit, and only the project's recorded
  standard redirected them — the same way a small local model did. Version drift closes as
  models refresh; capability gaps close as models improve; *this* gap does neither.

It does *not* meaningfully help an agent use a popular public dependency it already
knows well. That is an honest boundary, and a useful one — it says where to point the
effort. There is a further prospect the same harvested data suggests: it is exactly
the clean, current, version-matched material that would make good **training data**,
closing the gap at its source rather than only patching it at run time.

## Still open

These tests measured whether an agent *uses* a capability placed in front of it, whether
it *disambiguates* the right one among look-alikes, and whether it reaches for the library
this project *prefers* among several that overlap — all now measured. One harder question
remains: whether the agent can *find* the right entry among **hundreds**, retrieved from
an index rather than placed in front of it (retrieval at scale). That is an engineering
build — the searchable index — as much as a measurement, and it is the next step.
