# Does the Pipeline Itself Filter Injection?

RAD-0024 · 2026-08-22 · v1

**Design; nothing measured.** Sketched from an idea raised in conversation and recorded before
it is lost. Every control this project has considered was **added** to the pipeline — a gate
([RAD-0021](0021-admission-control-at-harvest.md), rejected), labels
([RAD-0020](0020-information-flow-control.md)), positional discipline
([RAD-0006](0006-development-time-prompt-injection.md)). This asks the opposite question.

## Question

Harvested documentation does not reach an agent as it was written. It is **chunked** into
index units, possibly **summarised** into a capability description, and then **retrieved** —
so the agent sees a handful of entries selected by relevance, not a library's documentation.

An injected instruction is a *coherent span of text written to be read as a directive.* Every
one of those three steps damages coherent spans, none of them for security reasons.

So: **does the pipeline already disrupt injection as a side effect of doing its job — and if
so, how much of the defence is free?**

## Trail

### Three transformations, in ascending order of how much they disturb the text

**Chunking is the weakest, and it is the one to be most sceptical of.** Splitting a doc comment
into index units may sever an instruction across a boundary, but chunking is a *representation*
choice, not a security boundary. An attacker who knows the chunk size writes to fit inside one,
and short payloads fit anywhere — RAD-0006's P3 is one sentence. Chunking also does nothing if
the entry is reassembled for display. Treat any protection here as incidental and unreliable.

**Summarising is far stronger, and this project is building it anyway.** `experiments/test5`
measured the summarise step as **load-bearing for retrieval** — raw doc text retrieves at 29%
r@1 against 77% for caller's-words entries at matched corpus size — so it is on the critical
path regardless of security ([RAD-0014](0014-build-vs-reuse.md) v3). That matters here because
summarising *rewrites* the text rather than slicing it: an instruction survives into the entry
only if the summariser reproduces it. **A control the product needs anyway, that may also
happen to filter injection, is the cheapest defence available** — if it works.

It may not. The summariser is itself an LLM reading attacker-controlled text, which is the
exact shape of the problem rather than an escape from it: a payload could be carried through,
or could redirect the summariser into writing a poisoned capability description. That is the
central risk to test, and it could equally make things worse.

**Retrieval is the one nobody has counted.** The agent is shown the top-k entries for the query
it asked. An instruction sitting in the documentation of an unrelated capability is never
retrieved and never seen. This bears directly on
[RAD-0006](0006-development-time-prompt-injection.md)'s surface figure: that record sizes the
exposure as **112–995 libraries**, which is the *corpus*, not the *per-interaction* surface. If
an agent sees five entries per query, the surface an attacker must land in is far narrower —
they must get a payload into an entry that matches a query the developer will actually make.

The counter is real and probably decisive in part: an attacker chooses common needs — logging,
HTTP, serialisation, dates — precisely to maximise retrieval probability, and a poisoned
popular library is retrieved constantly. So retrieval narrows the surface without bounding it,
and the interesting quantity is *how much*.

### Why this is worth a record rather than a footnote

If any of it holds, it changes the shape of the argument. This project currently tells adopters
that injection is a live hazard needing architectural mitigation, which is true and measured.
It does not currently know **how much of the mitigation it already gets for free** from a
pipeline built for entirely different reasons. That is a gap in the honest accounting, in the
project's own favour, and this project has been careful to correct claims in the other
direction.

### The obvious objection

Fragmentation and paraphrase are **obfuscation-adjacent defences**, and this project has
already measured what happens to those: RAD-0021 withdrew a signal that encoding defeated, and
`experiments/test4` showed the parse stage filters nothing today. A defence that works because
an attacker has not adapted is not a defence. Any finding here must state what it costs an
attacker to route around — a payload written to survive summarisation, or aimed at a
high-retrieval capability — rather than what it costs one who is not trying.

## Findings

**Nothing measured.**

**Reasoned.**

- The three transformations differ by an order of magnitude in how much they disturb text;
  chunking is weakest and summarising strongest.
- Summarisation is the interesting one because it is already required for retrieval, so any
  security effect is free — and because it could plausibly make matters worse by putting an
  LLM in front of attacker-controlled text.
- RAD-0006 sizes the injection surface as a corpus. The per-interaction surface is a different
  and smaller number that nobody has computed.

**What to find out, in order.**

1. **Does an injected instruction survive summarisation?** The cheapest and most informative
   test, and everything is already built: run RAD-0006's payloads through a summarise step and
   check whether the canary reaches the entry. Three outcomes, all useful — filtered
   (a free defence), carried through (no defence), or *the summariser itself redirected*
   (a new hazard this project has not considered).
2. **What is the per-interaction surface?** Over test5's real corpus, for a set of realistic
   needs, count how many distinct libraries appear in the top-k. That converts RAD-0006's
   112–995 into a number that reflects what an agent actually sees.
3. **What does routing around it cost?** Only after 1 and 2, and only framed as attacker
   effort, per the objection above.
4. **Survey first.** Chunking and paraphrase as injection defences are an obvious idea and
   [RAD-0008](0008-the-field-as-it-stands.md) has had to withdraw three novelty claims. Check
   before claiming anything.

## Connections

- [RAD-0006](0006-development-time-prompt-injection.md) — the injection measurement, and the
  corpus-sized surface figure this would refine.
- [RAD-0014](0014-build-vs-reuse.md) — summarise as a step the product needs anyway.
- [RAD-0013](0013-the-codex-entry.md) — the entry, and therefore what chunking produces.
- [RAD-0019](0019-retrieval-at-scale.md) — retrieval, the third transformation.
- [RAD-0021](0021-admission-control-at-harvest.md) — the rejected added control, and the
  encoding objection that applies here too.
- [RAD-0020](0020-information-flow-control.md) — enforcement, which does not depend on any of
  this working.
