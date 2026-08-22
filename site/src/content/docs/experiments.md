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

## The same channel can be used against you

If an index can change what an agent writes, then so can an instruction hidden in the
material the index is built from — and the library authors supplying that material are not
all trustworthy. So we tested that too, planting an instruction inside a library's
"documentation" and varying only *where the text was placed*: as guidance the agent should
follow, as data quoted and labelled untrusted, or in the system prompt.

**Many agents follow it, and the obvious defence is not enough.** Quoting the text as
untrusted data helps a lot — several agents refused every attempt that way. But moving the
identical words into the system prompt defeats that defence outright, and a payload that
simply argues the untrusted-data label is a test harness defeats it on agents it otherwise
protects. Exposure varied enormously between models of similar capability, including within
one vendor's range, so this tracks how a model was trained rather than how big it is. With
real tools rather than code generation, an agent given a plausible pretext copied a planted
credentials file into a log.

This is a negative result about **this project's own proposal**, which is why it is
published alongside the design rather than quietly fixed: harvesting library prose into an
agent's context is exactly the hazard being described. The conclusion is architectural —
library content must be placed where it *cannot* be followed, and the transitive tail
excluded by default — because no property of the agent can be relied on instead. The
method, payloads, transcripts and a runnable kit are public, and the per-model numbers are
small-sample single measurements meant to be re-run rather than believed.

**[The full study, with the per-agent numbers and the mitigation, is here](/injection/).**

## Still open

These tests measured whether an agent *uses* a capability placed in front of it, whether
it *disambiguates* the right one among look-alikes, whether it reaches for the library
this project *prefers* among several that overlap, and whether library-supplied text can
redirect it. **Retrieval at scale is now measured too**, in two layers: search over an
index of hundreds of entries, and the full loop where the agent writes its own query
against that index. Recall alone found the right entry for 77% of needs described in a
caller's own words — and the agent loop found it every time in a pilot, because the agent
translates a plain-language need into the vocabulary the entry actually uses. That is the
single strongest argument for an index over a pile of documents.

What remains is less about whether the idea works and more about building it honestly.
Whether the *harvesting* stage is riskier in some languages than others is untested — the
injection work planted text directly rather than parsing it out of real source, and doc
conventions differ enormously in how much free text they invite. Whether documentation can
be automatically checked against the structure of the library that shipped it — the most
promising mitigation on the table — is a hypothesis, not a result. And the index itself
still has to be built for real corpora rather than a synthetic one.
