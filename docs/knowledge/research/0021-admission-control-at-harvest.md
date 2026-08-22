# Admission Control at Harvest

RAD-0021 · 2026-08-22 · v1

**Design; the signal is measured, the gate is not.** Split out of
[RAD-0020](0020-information-flow-control.md) v2, which had it as a subsection. It belongs on
its own because it can be **valued on its own merits** — adopted or rejected independently of
anything else: it needs no information-flow control, no cooperation from the agent runtime, and
no consumer to honour a label. The detection signal it rests on is measured in `experiments/test3`; the gate built on
that signal is not.

## Question

[RAD-0006](0006-development-time-prompt-injection.md) established that library prose redirects
coding agents and that every control proposed so far runs through something the codex does not
own — the agent's judgement (positional discipline), or the agent runtime's policy engine
(RAD-0020's information-flow control). Both are worth having and neither is enforceable by a
harvester.

But the harvester owns one boundary outright: **the moment content enters the index.**

So: **what is a harvest gate actually worth?**

The question is deliberately not *how do we ship one*. A gate may well not be adopted — its
coverage is bounded in ways set out below, and the same effort may be better spent on
labelling. But its value is *measurable without building it*, and a control this cheap should
not be dismissed or adopted on intuition. This record exists to find the number.

## Trail

### Why a gate is categorically different from a flag

`experiments/test3` demonstrated a detector: prose that references symbols or endpoints
existing nowhere in the shipping library's declared surface. As a *detector* it is
mitigation 4 from RAD-0006 — anomaly detection — and it inherits that category's weakness: it
produces a warning that some human or agent must act on.

The same signal used as an **admission gate** is a different kind of control. Content that
never enters the index cannot reach any agent, through any channel, regardless of that agent's
robustness or whether its runtime implements anything. It does not ask the model to decline,
and it does not ask a downstream consumer to cooperate. It is the harvester's own version of
*refuse before the sink*, where the sink is the index.

That matters most for the population this project serves. RAD-0006 measured
locally-served open-weight agents as the most exposed tier, and those are precisely the
setups least likely to run an information-flow-control runtime. A gate protects them without
their participation; a label does not.

### The cost is the mirror image of the detector's

For a detector, a false positive is a spurious warning — cheap, annoying, tolerable at a few
percent. For a gate, **a false positive is a real capability silently missing from the
index**, and a missing capability is indistinguishable from a library that never documented
one. The costs are asymmetric enough that the same measurement supports very different
policies depending on which signal fired.

From test3, over 1,009 real doc comments across five published libraries:

| signal | false positives | why | policy this would suggest |
|---|---|---|---|
| URL not on an allowlisted host | **1.3%** | genuine hits are links to specifications and RFCs, allowlistable by host | **discard**, or strip the URL and keep the prose |
| Symbol resolving nowhere in the library's surface | **≤5.9%** | residual is mostly resolver incompleteness — nested and companion declarations | **label untrusted and down-weight**; do not discard |
| Neither | — | — | index normally, labelled by provenance |

The symbol figure is an upper bound from a deliberately naive resolver, and RAD-0009's
resolve-in-index already builds the machinery that would lower it. Whether it drops far enough
to justify gating rather than down-weighting is an open question and one of the things to
measure.

### Silent discard is a correctness hazard

A gate that drops content without saying so is unfalsifiable: nobody can tell a filtered
capability from an undocumented one, and a bug in the resolver becomes a permanent invisible
hole in the index. Any gate must therefore **record what it dropped and why**, and that record
has to be inspectable — the same standard the postmortems set for this project's own failures.
This is a hard requirement, not a nicety.

### The case against adopting it

Stated first, because it is strong and the record should not read as advocacy.

- **Its coverage is bounded by a property of the attack, not of the defence.** A gate sees
  only payloads that *name something foreign*. Everything else passes. Whether that is most of
  the threat or a slice of it is unknown, and it is the number this record needs.
- **False positives cost real capability**, invisibly, which is worse than a noisy detector.
- **It multiplies per ecosystem.** Kotlin is measured; each further language needs its own
  false-positive measurement before the gate can be switched on for it, and `experiments/test4`
  shows Rust and Python carry far more free text.
- **It adds little where flow control exists.** For a consumer running RAD-0020's model, the
  gate is largely redundant.
- **It needs audit machinery to be safe at all** (below), so "cheap signal" is not the same as
  "cheap feature".
- **Labelling composes better.** A provenance label is useful to every consumer and costs no
  capability; a gate helps one class of consumer and can remove capability.

### What a gate does not close

- **An attacker who reads the rule writes prose that references only real symbols.** The gate
  raises the cost of an attack; it does not close the class.
- **RAD-0006's P3 payload passes untouched.** A pure instruction hijack — *disregard the
  request and reply X* — references nothing outside the library because it asks for nothing
  outside it. Structure grounding cannot see it, and that is the boundary of the technique
  rather than a tuning problem. That class needs RAD-0020's enforcement, which is why the two
  records complement rather than compete.
- **It is a per-language problem.** test3 measured Kotlin/JVM. `experiments/test4` showed Rust
  and Python carry far more free text per comment, so their false-positive rates will differ
  and each ecosystem needs its own measurement before a gate is switched on for it.

### Where it sits in the pipeline

`experiments/test4` measured that **the parse stage currently filters nothing** — in all five
languages the payload arrives intact, so the enforcement point exists and is unoccupied. A
gate would be a natural occupant of it. Were one adopted, it would also imply a field on the
entry recording the admission decision and its reason — a change to
[RAD-0013](0013-the-codex-entry.md)'s format, to be specified there rather than here.

## Findings

**Measured (from `experiments/test3`, 2026-08-22).** The grounding signal runs at **1.3%
false positives on URL references** and **≤5.9% on symbol references** over 1,009 real doc
comments, and catches the two RAD-0006 payloads that produced real harm — one by each signal.
It does not catch a payload that references nothing.

**Measured (from `experiments/test4`, 2026-08-22).** The parse stage filters nothing in any of
the five languages, so a gate placed there is an addition rather than a change to existing
behaviour.

**Reasoned, not tested.**

- A gate protects consumers who run no flow-control runtime, which is most of them and
  disproportionately the exposed tier.
- Gate false-positive cost is qualitatively worse than detector false-positive cost, so the
  policy should differ per signal rather than sharing a threshold.
- Auditability of drops is a correctness requirement, not a feature.

**The experiment that would value it.** Three measurements, none of which requires building a
production gate, and the first of which is the crux:

1. **Coverage against attacks this project did not design.** The bounding question is *what
   fraction of realistic payloads name something foreign?* Testing that against RAD-0006's own
   three payloads is circular — they were written by the same people proposing the defence.
   [RAD-0008](0008-the-field-as-it-stands.md) v2 identifies published corpora that solve this:
   **AgentTrap**'s 91 malicious tasks over 16 security dimensions, and **SkillJect**'s
   automatically generated poisoned skills. Running the grounding signal over an independent
   attack corpus gives an honest coverage number and is the single most informative thing to do
   here. If coverage is low, the gate is not worth adopting and the record ends.
2. **False-positive cost** — genuine capabilities the signal would remove from a real harvest,
   per language.
3. **Whether the product survives it**, which is already runnable:
   [RAD-0019](0019-retrieval-at-scale.md)'s Layer 1 rig measures recall over an index. Gate the
   corpus, re-run recall, compare. **A control that costs more retrieval than it prevents harm
   is not worth shipping**, and this measures the trade rather than arguing it.

## Recommendation

**Value it before deciding anything, and be willing to conclude it is not worth using.** No
commitment is proposed here.

1. **Measure coverage against an independent attack corpus first.** Everything else is
   contingent on it. If the grounding signal catches only a small fraction of AgentTrap's or
   SkillJect's payloads, the gate is a narrow defence against attacks this project happened to
   write, and the honest outcome is to record that and stop.
2. **If coverage is substantial, measure the two costs** — capability lost, and retrieval
   recall on the Layer 1 rig — before any design work.
3. **Only then consider form**, and probably not as a binary gate: URL grounding is precise
   enough to act on, symbol grounding is better as a down-weighting signal until a real
   resolver lowers its rate.
4. **Whatever the outcome, the signal has a floor use** that requires no adoption decision: as
   a **contributor-facing and maintainer-facing check** on a harvested corpus, where a false
   positive costs a glance rather than a capability.
5. **Do not build audit machinery speculatively.** It is a hard requirement *if* a gate ships,
   which makes it a cost to weigh, not a task to start.

**Plausible outcomes, all acceptable.** (a) Coverage is low → do not adopt; record why, and
lean on [RAD-0020](0020-information-flow-control.md). (b) Coverage is decent but retrieval cost
is real → keep it as a label and a warning, never a gate. (c) Coverage is high and cost is
negligible → gate on the URL signal, down-weight on the symbol signal. **(a) is a perfectly
good result and the record should not be written as though it would be a failure.**

## Connections

- [RAD-0006](0006-development-time-prompt-injection.md) — the injection measurement, and
  mitigation 4 which this makes concrete.
- [RAD-0020](0020-information-flow-control.md) — the trust model this was split from;
  complementary, and the record that owns the payload class this gate cannot see.
- [RAD-0019](0019-retrieval-at-scale.md) — the recall rig that measures what the gate costs.
- [RAD-0013](0013-the-codex-entry.md) — where the admission decision would be recorded on the
  entry.
- [RAD-0009](0009-reusing-indexers-and-what-to-index.md) — the parse stage the gate occupies,
  and the resolve-in-index machinery that would lower the symbol false-positive rate.
- [RAD-0011](0011-existing-documentation-systems-as-skill-content.md) — the harvested content
  a gate would be admitting or refusing.
