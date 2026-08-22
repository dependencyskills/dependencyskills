# Admission Control at Harvest

RAD-0021 · 2026-08-22 · v2

**v2 (2026-08-22) — the crux question is answered, and the answer is no.** The coverage
measurement this record called for has been run against AgentTrap's independently-authored
corpus (`experiments/test3`, part B). **Structure grounding catches a minority of attacks it
did not write: 36% of prose-borne malicious skills, 61% on the closest analogue, at a 10%
false-positive rate on that corpus.** The misses are structural classes rather than tuning
gaps. Per this record's own stated outcomes that lands on (b): **keep the signal as a label and
a warning, and do not build the gate.** Findings below.

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
- **Encoding defeats it, and that is a real hole.** A payload that is base64-encoded, or
  encoded in any scheme the model can decode and a matcher cannot, presents no visible URL and
  no visible symbol — so both grounding signals miss it entirely. RAD-0006's own P6 payload
  demonstrated the mechanism (obeyed in the instruction and system arms), and published cases
  exist of encoded payloads passing content filters that would have caught the plaintext. Any
  gate is one encoding away from being bypassed, which is the arms-race objection in its
  concrete form.

  **The partial answer is a different signal, and it is cheap.** An encoded blob sitting in a
  doc comment is *itself* anomalous in a way it is not in a file written to instruct an agent.
  KDoc, docstrings and `///` comments have decades of human readers and doc-tool processing
  behind them, so high-entropy or non-prose spans stand out — which is RAD-0006's
  discovered-versus-designed argument arriving as a detector. That is a shape check rather than
  a grounding check, it is not what test3 measured, and whether it has a usable false-positive
  rate on real corpora is unknown. It is worth measuring alongside the grounding signals rather
  than assumed.

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

**Measured — coverage against an independent corpus (2026-08-22; `experiments/test3` part B).**
This is the measurement the record was written to obtain. Run against **AgentTrap**'s 91
malicious and 50 benign agent skills — independently authored, drawn from real ecosystems —
the URL grounding signal catches:

| population | caught |
|---|---|
| closest analogue (prose in the skill file, n=18) | **61%** |
| all prose-borne malicious (n=72) | **36%** |
| all malicious including code-borne (n=91) | 31% |
| **false positives on benign skills (n=50)** | **10%** |

Only the URL signal transfers: a skill has no symbol graph, so the symbol signal has no honest
analogue in this corpus and was not scored rather than invented.

**The misses are structural, not tuning gaps.** Of 72 prose-borne malicious skills, 46 are
missed, and they never name an external destination in prose at all: **output-channel
exfiltration** (`.env` contents emitted inside the agent's own output — no network call),
**local destructive action**, **agent-config poisoning** (stripping safety blocks from a
`CLAUDE.md`, appending an auto-approve directive), **resource abuse** (token bombs — nothing
foreign is referenced), **encoding** (steganographic payloads at 17%, confirming the hole
above), and payloads whose URL sits in helper code where a prose check never looks. Whole
dimensions score zero because the concept the signal tests for is simply absent from them.

**False-positive rate is a property of the corpus.** 10% here against **1.3%** on real library
documentation, which is expected rather than contradictory: skills are workflow instructions
that legitimately call services; doc comments rarely name an endpoint. A library corpus is the
favourable case for this signal, and the 1.3% figure is the one that applies to the codex.

**Measured — false positives on library documentation (2026-08-22; `experiments/test3` part A).**
The grounding signal runs at **1.3% on URL references** and **≤5.9% on symbol references** over
1,009 real doc comments, and catches the two RAD-0006 payloads that produced real harm — one by
each signal. It does not catch a payload that references nothing.

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

**Do not build the gate. Keep the signal.** The coverage measurement was the condition this
record set for itself, and it came back at 36% of prose-borne attacks it did not author. A
control that misses roughly two thirds of an independent benchmark cannot be an admission rule:
refusing content on it would remove real capability while leaving output-channel exfiltration,
config poisoning, resource abuse and encoded payloads untouched. That is the worst combination
available — a cost that is certain against a benefit that is partial.

1. **Keep URL grounding as a label and a warning**, where its 1.3% rate on library
   documentation is cheap and a false positive costs a glance rather than a capability. It
   catches a real class, including the exfiltration that did genuine harm in RAD-0006.
2. **Do not gate on the symbol signal either.** Part A's false-positive asymmetry already
   argued for down-weighting over discarding, and nothing here changes that.
3. **Do not build audit machinery.** It was a hard requirement only if a gate shipped.
4. **The retrieval-cost experiments are moot** and should not be run — they priced a control
   that is not being adopted. [RAD-0022](0022-the-value-of-transitive-capabilities.md) still
   wants the Layer 1 rig for its own question.
5. **Route the uncovered classes to [RAD-0020](0020-information-flow-control.md).** Every miss
   catalogued above is an attack that names nothing foreign, which is precisely what enforcing
   at the sink handles and detection cannot.

**This is outcome (b) from the version of this record written before the measurement, and it is
a good result.** The record cost a day and prevented building a control that would have looked
principled, measured well against our own payloads, and failed quietly against real ones.

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
