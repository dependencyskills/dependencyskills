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

:::caution[This section describes live attack material]
The corpora behind these results contain working attack code. Reproducing them safely is covered in
[why any of this is measured](/research/).
:::

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

**With two caveats we put there ourselves, and the second is the bigger one.**

That 77% was measured over 220 entries. Harvesting a single real project — 99 dependencies —
yields more than five thousand once duplicates are removed, and recall falls steeply as the
corpus grows. "At scale" was not measured at the scale a real graph produces.

And **every entry behind the 77% was written by hand.** We had been treating the gap between
that number and the 29% raw harvested documentation scores as evidence that the rewriting step
is what makes an index work. It is not. We built the rewriter, ran it over the same 220 entries
with the same questions and the same encoder, and it puts the right answer first **5 times in 17
— exactly what the raw documentation does**, while trailing it further down the list.
**The lift belonged to who wrote the summary, not to summarising.**

So 77% is a target: what an index could reach if its entries were as good as hand-written ones.
It is not what the pipeline that builds them achieves. The rewriter keeps its place for the
other reason we built it — it is the quarantine that stops library text reaching the agent
verbatim, and that result is unaffected. We are leaving the number on this page rather than
quietly restating it, because it was the strongest thing we had said and it turned out to be
measuring something else.

**The same run found what does work, which we had been treating as a design detail.** What an
entry is *found* on and what an agent is *shown* do not have to be the same text — the search key
is a set of numbers nothing reads. So an entry can keep both: the library's own documentation as
one key, the rewritten sentence as another, and the agent still only ever sees the rewrite. Asked
the same 17 questions, that index puts the right answer in the first ten **15 times against 13 for
the documentation alone and 10 for the rewrite alone**. It wins by not failing badly rather than by
being better everywhere — each version has questions it gets hopelessly wrong, and they are mostly
different questions.

One more thing worth reporting because it is the opposite of the obvious move: **gluing the two
texts together into a single key is worse than either on its own.** The gain needs them kept apart.
That is the second time this project has measured a fusion of two search signals performing worse
than the better one alone.

Two questions that stood open here have since been answered. **Harvesting does not filter
anything** — the same payload written in each of five languages' native doc conventions is
delivered perfectly intact by every parser, so the parse stage is available as an enforcement
point and is not currently enforcing. That measurement also overturned our own guess about
which ecosystem was most exposed: TypeScript's convention turned out to be the *tersest* of
the five, while Python carries the largest typical comment and Rust the heaviest tail and by
far the most embedded code. And **checking documentation against the code it ships with does not work well enough to
use** — it catches about a third of attacks from an independent benchmark, misses whole
classes structurally, and on a real dependency graph its false-positive rate is twenty times
what five hand-picked libraries suggested. It was withdrawn.

**And the largest finding came from building it.** Harvesting a real graph and indexing raw
documentation retrieves at roughly a third the quality of entries written in a caller's own
words. Rewriting harvested prose into the words a developer would actually search with is not
an optimisation — it is the part that makes the index work, and it is now on the critical path.

**Two more have since been answered, and both cut against ideas we liked.**

**Poisoning the index works, but only for a library the attacker publishes.** Appending a false
claim to somebody's honest documentation moved retrieval not at all — the rewriting step anchors
on the document's own true opening sentence. Writing the whole document, for a symbol you also
named, put a **library that does not exist** ahead of the ecosystem's canonical answer for one in
four of our test needs, with every check we have reading clean. The failure is not in the
rewriting step, which behaved correctly throughout; it is the assumption that documentation
describes the code it ships with, which no amount of reading the text can establish.

**Suppressing verbs to make instructions unrepresentable does not work.** The idea was that an
imperative is a syntactic object, so an index entry with no grammar could carry topic without
carrying command. English disagrees: it marks command in *modals*, which are not verbs, so
*"MUST also call X"* survives verb removal as *"MUST also X"* — still an order. The retrieval
half was more interesting than the security half: deleting verbs costs **nothing** at the top
rank and a great deal in the tail, which suits an entry whose job is to be found rather than
browsed. Two negative results are worth having: mangling verbs is *worse* than deleting them,
and applying the same mapping to both the index and the query — the obvious repair, phonetic
codes included — makes matters worse rather than better, because dense retrieval matches meaning
rather than token identity.

**And the stronger control has now been run.** Enforcing policy on labelled content before a
sensitive tool fires does prevent the harm — the planted credential never reached a log under
either policy we tried. But the naive implementation, which tracks labels across the whole
conversation, also blocked the developer's own work every single time: the attack causes the
agent to read the credential file, that read taints the context, and the tainted context
refuses the legitimate write. Harm prevented, nothing accomplished. **Label granularity is
therefore a requirement rather than a refinement**, and our numbers bound the cost of the crude
version rather than estimating the real one.

Two much cheaper controls did better on both axes in the same test: a **tool-less model that
paraphrases the documentation first**, so the original text never reaches the agent that can
act, and **shipping no prose to the agent at all** — only the symbol and its signature, which
earlier work showed was already enough to use a capability.

That last one has a limit we found by trying to break it. We had called structure recovered
from compiled code the near-injection-proof tier, because identifiers come from a grammar
rather than free text. **A method name carrying a full English sentence compiles, survives
into the class file, and is printed back verbatim by the standard disassembler** — and the
portable version of the trick needs no exotic syntax, because an imperative in camel case is a
legal identifier in every language we harvest. An agent given such a signature ignored it and
did its work, but that was one phrasing against one model.

What remains is building it honestly: the index still has to work against real corpora rather
than a synthetic one, per-value labelling is the version worth measuring, and whether an
identifier-borne instruction is genuinely inert needs a much harder try than we have given it.
