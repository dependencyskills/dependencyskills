# The Summariser as an Attack Surface

RAD-0025 · 2026-08-22 · v1

**Design; nothing measured.** Split out of
[RAD-0024](0024-does-the-pipeline-filter-injection.md), which raised summarisation as a
possible *defence*. This record takes the opposite reading of the same fact and is the more
serious of the two.

## Question

`experiments/test5` measured the summarise step as load-bearing: raw harvested doc text
retrieves at 29% r@1 against 77% for entries written in a caller's words, so the product needs
an LLM that **reads attacker-controlled documentation and writes the entry that gets indexed**.

Every injection finding so far concerns an agent reading an entry at *use* time. This is a
different position in the pipeline and a different threat: the attacker's text reaches a model
that is *authoring the corpus*, once, on behalf of every consumer.

So: **what can an attacker do to the summariser, and what does a poisoned entry look like?**

## Trail

### Four things a compromised summariser could produce, in ascending order of nastiness

**1. Pass-through.** The summariser copies the injected instruction into the entry. Crude, and
the easiest to detect — a canary check finds it, which is exactly what
[RAD-0006](0006-development-time-prompt-injection.md)'s harness already does.

**2. A mis-described capability.** The entry describes what the library does *incorrectly*, so
agents use it wrongly. No instruction is carried; the damage is that the codex is now
confidently wrong. This is a correctness attack wearing a security hat, and nothing in the
project's current thinking would catch it.

**3. Trigger poisoning — the entry is written to be retrieved for queries it should not
answer.** The attacker does not need the agent to obey anything at all. They need the *entry*
to surface for needs it has no business answering, so their library is what an agent reaches
for. Given [RAD-0018](0018-the-selection-ab.md) measured selection at 0/18 unaided — agents
take what they are given — an entry that wins retrieval largely wins the decision.

This is an attack on **the index rather than the agent**, and it is the one this project is
least equipped to see. A poisoned entry of this kind contains no instruction, names nothing
foreign (so [RAD-0021](0021-admission-control-at-harvest.md)'s withdrawn grounding signal would
not have seen it either), and reads exactly like a well-written capability description. It is
*supposed* to be in caller's words. That is the whole problem.

**4. Summariser hijack proper.** The injected text redirects the summarising model itself into
emitting attacker-chosen content for entries **other than the one being summarised**, or into
poisoning a batch. Whether the summariser's context spans multiple libraries decides whether
this is possible at all, which makes batching a security-relevant design choice rather than a
throughput one.

### Why this is worse than use-time injection

**It is written once and served forever.** Use-time injection must succeed on each interaction,
against whichever agent happens to be reading, and RAD-0006 measured that outcome varying
wildly by model. A poisoned *entry* is produced once at harvest and then served to every
consumer, by every agent, until the corpus is rebuilt. The variance that protected some readers
disappears.

**It survives the mitigations this project has settled on.** Placing library content outside
the instruction channel ([RAD-0006](0006-development-time-prompt-injection.md)) protects
against text the agent might *obey*. A mis-described or trigger-poisoned entry is not obeyed —
it is *believed*, as ordinary reference data, which is precisely how the design says it should
be treated. Information-flow control ([RAD-0020](0020-information-flow-control.md)) does not
help either: nothing is exfiltrated and no consequential tool call is influenced by an
untrusted label — the corruption already happened, upstream, at authoring time.

**A central corpus multiplies it.** [RAD-0006](0006-development-time-prompt-injection.md) v6
records that a shared corpus is the one place a payload need be planted once to reach everyone.
Summarisation is where that planting would occur.

## Findings

**Nothing measured.**

**Reasoned.**

- Adding a summariser moves an LLM to a position where it reads untrusted text and writes
  durable corpus content, which is a strictly worse position than reading it at use time.
- The interesting attacks produce entries containing **no instruction at all**, so every
  detector this project has considered — canary checks, structure grounding, instruction-shape
  anomaly detection — is aimed at the wrong artifact.
- Trigger poisoning couples directly to RAD-0018's 0/18 selection result: winning retrieval is
  close to winning the decision.
- Both settled mitigations are orthogonal to this. Neither is wrong; both are aimed elsewhere.

**What to find out, in order.**

1. **Does a payload survive summarisation into the entry?** The cheap first cut, shared with
   RAD-0024: run RAD-0006's payloads through a summarise step and canary-check the output.
   Answers case 1 only, and answering it is not answering this record.
2. **Can a payload make the summariser mis-describe a capability?** Plant a doc comment that
   argues the capability is something it is not, and compare the generated entry against one
   summarised from the clean doc. This is case 2 and needs a judge rather than a canary.
3. **Can a payload make an entry retrievable for needs it should not answer?** Case 3, and the
   important one. Measurable on `experiments/test5`'s rig: summarise a poisoned doc, index it
   alongside the real corpus, and check whether it surfaces for queries belonging to other
   capabilities.
4. **Does batching widen it?** Summarise several libraries in one context and check for
   cross-contamination between entries.

**What would change the design.** If trigger poisoning works, entry provenance stops being
metadata for a trust label and becomes load-bearing for *attribution* — a consumer needs to
know which library an entry describes and who wrote the text it came from. If summariser
hijack across a batch works, the summarise step must process one library per context, which is
a throughput cost worth paying.

## Connections

- [RAD-0024](0024-does-the-pipeline-filter-injection.md) — the same step read as a possible
  defence; these two must be answered together or the answer is half a picture.
- [RAD-0014](0014-build-vs-reuse.md) — where summarise is named as a step to build; test5
  measured it as load-bearing.
- [RAD-0018](0018-the-selection-ab.md) — 0/18 unaided, which is why winning retrieval matters.
- [RAD-0006](0006-development-time-prompt-injection.md) — use-time injection, and the
  mitigations this threat routes around.
- [RAD-0020](0020-information-flow-control.md) — enforcement at the sink, which does not
  address corruption at authoring time.
- [RAD-0013](0013-the-codex-entry.md) — the entry, which is what gets poisoned.
