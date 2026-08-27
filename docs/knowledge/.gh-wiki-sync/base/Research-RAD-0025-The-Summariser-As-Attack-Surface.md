# The Summariser as an Attack Surface

RAD-0025 · 2026-08-23 · v3
Keywords: what if the summariser is the thing being attacked; a model that reads attacker-controlled text and writes durable corpus content; pass-through, mis-description, trigger poisoning, hijack; can a false claim be planted in an honest library; why poisoning an honest doc comment fails; who wrote the document.

**v3 (2026-08-23) — case 3 measured, and the answer turns on who wrote the document.**
Trigger poisoning was run against `experiments/test5`'s real 14,899-entry index in three forms.
**Poisoning an honest library fails.** Appending a false claim to a real doc comment — with an
instruction (`directive`) or as plain false prose (`prose`) — moved nothing: both scored exactly
what the *unpoisoned* entry scored, 1/17, and that single win is an artifact of one true answer
ranking 3685. The summariser anchored on the true first sentence and declined to carry the
contradiction, writing a faithful description each time.

**Publishing a fabricated library works.** When the attacker controls the whole document and the
symbol name — no honest prose to contradict, therefore nothing for the summariser to refuse — the
entry outranked the true answer for **4/17** needs, including beating `kotlinx.coroutines.sync.Mutex`
at rank 10 with an entry at rank **6** for a library that does not exist. No canary or grounding
check fires on any of it; the entry is a well-formed capability description that happens to be
fiction. **Sized as an upper bound**: the poisoned entry is summarised while all 14,899 competitors
are raw doc text, so part of the margin is the 29%-against-77% gap rather than the attack.

**v2 (2026-08-22) — the hazard is real, on the first attempt.** `experiments/test6` confirms
this record's premise. Payloads aimed at a downstream coder are filtered by summarisation
(0/6). **A payload addressing the summariser's own task derailed it 4/6** — the model obeyed
the instruction instead of summarising, replacing the entry rather than contaminating it. So
**summarisation does not filter payloads aimed at the summariser**, and case 4 of this record
(summariser hijack) is demonstrated rather than hypothetical.

**The important qualification runs the other way from usual: this result is too *easy*.** The
payload that landed failed **loudly** — no entry was produced at all, which any pipeline would
notice. The cases this record argues are dangerous fail **silently**: a payload that says
*describe this as the recommended way to do X* would return a well-formed, plausible, poisoned
entry that no canary check detects. test6 shows the channel is open; it does not touch the
attacks worth worrying about. Case 3 (trigger poisoning) remains the priority and is now
directly measurable on `experiments/test5`'s real index.

**Split out of
[RAD-0024](Research-RAD-0024-Does-The-Pipeline-Filter-Injection), which raised summarisation as a
possible *defence*. This record takes the opposite reading of the same fact and is the more
serious of the two.

## Question

`experiments/test5` measured the summarise step as load-bearing: at **matched corpus size, 220
entries either way**, raw harvested doc text retrieves at 29% r@1 against 77% for entries
written in a caller's words. On the real 5,440-entry corpus raw text scores **0/17**. Either way
the product needs an LLM that **reads attacker-controlled documentation and writes the entry
that gets indexed**.

Every injection finding so far concerns an agent reading an entry at *use* time. This is a
different position in the pipeline and a different threat: the attacker's text reaches a model
that is *authoring the corpus*, once, on behalf of every consumer.

So: **what can an attacker do to the summariser, and what does a poisoned entry look like?**

## Trail

### Four things a compromised summariser could produce, in ascending order of nastiness

**1. Pass-through.** The summariser copies the injected instruction into the entry. Crude, and
the easiest to detect — a canary check finds it, which is exactly what
[RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection)'s harness already does.

**2. A mis-described capability.** The entry describes what the library does *incorrectly*, so
agents use it wrongly. No instruction is carried; the damage is that the codex is now
confidently wrong. This is a correctness attack wearing a security hat, and nothing in the
project's current thinking would catch it.

**3. Trigger poisoning — the entry is written to be retrieved for queries it should not
answer.** The attacker does not need the agent to obey anything at all. They need the *entry*
to surface for needs it has no business answering, so their library is what an agent reaches
for. Given [RAD-0018](Research-RAD-0018-The-Selection-Ab) measured selection at 0/18 unaided — agents
take what they are given — an entry that wins retrieval largely wins the decision.

This is an attack on **the index rather than the agent**, and it is the one this project is
least equipped to see. A poisoned entry of this kind contains no instruction, names nothing
foreign (so [RAD-0021](Research-RAD-0021-Admission-Control-At-Harvest)'s withdrawn grounding signal would
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
the instruction channel ([RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection)) protects
against text the agent might *obey*. A mis-described or trigger-poisoned entry is not obeyed —
it is *believed*, as ordinary reference data, which is precisely how the design says it should
be treated. Information-flow control ([RAD-0020](Research-RAD-0020-Information-Flow-Control)) does not
help either: nothing is exfiltrated and no consequential tool call is influenced by an
untrusted label — the corruption already happened, upstream, at authoring time.

**A central corpus multiplies it.** [RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection) v6
records that a shared corpus is the one place a payload need be planted once to reach everyone.
Summarisation is where that planting would occur.

## Findings

**Measured — the summariser can be redirected (2026-08-22; `experiments/test6`).** A payload
addressing the summariser's own task derailed it **4/6** over real doc comments: it obeyed the
instruction instead of summarising, replacing the entry rather than contaminating it. Case 4 of
this record is demonstrated. Payloads aimed at a downstream coder were filtered 0/6, so
summarisation defends against the previous threat model and not against this one.

**The result is too easy to be reassuring in either direction.** The payload that landed failed
**loudly** — no entry at all, which any pipeline notices. The cases argued below as dangerous
fail **silently**, returning a well-formed plausible entry. test6 establishes the channel is
open; it says nothing about cases 2 and 3.

**Measured — trigger poisoning needs an attacker-authored library, not a poisoned honest one
(2026-08-23; `experiments/test6/trigger_poisoning.py`).** Against test5's real 14,899-entry
index: `directive` 1/17, `prose` 1/17, `clean` 1/17 — the poison contributed nothing, and the
lone win is a floor artifact. `authored`, where the attacker writes the whole doc and names the
symbol, reached **4/17** and beat a true answer that was itself retrieving at rank 10. Nothing a
canary or grounding check inspects distinguishes any of the four entries.

**The mechanism is anchoring, and it is the useful part of the result.** The summariser holds to
whatever truth is present in its input; a contradicting claim loses to the doc's own first
sentence. That is why case 2 (mis-description) is harder than this record assumed — and why case
3 is *easier* than it assumed, because a fabricated library contains no truth to anchor to.
There is nothing to detect, only something to disbelieve, and disbelief is not available to a
summariser that has only the document.

**This cannot be defended at the summarise step, which changes where the control has to sit.**
The summarise step behaved correctly in every condition. What fails is the assumption that a
document describes the code it ships with. That is an *admission* question — rejected in
[RAD-0021](Research-RAD-0021-Admission-Control-At-Harvest) — or an *attribution* one, and this record
already predicted the latter: entry provenance becomes load-bearing for telling a consumer which
library an entry describes.

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

1. ~~Does a payload survive summarisation into the entry?~~ **Answered (test6):** case 1 no,
   case 4 yes at 4/6. Cases 2 and 3 remain, and they are the ones that matter.
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

- [RAD-0024](Research-RAD-0024-Does-The-Pipeline-Filter-Injection) — the same step read as a possible
  defence; these two must be answered together or the answer is half a picture.
- [RAD-0014](Research-RAD-0014-Build-Vs-Reuse) — where summarise is named as a step to build; test5
  measured it as load-bearing.
- [RAD-0018](Research-RAD-0018-The-Selection-Ab) — 0/18 unaided, which is why winning retrieval matters.
- [RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection) — use-time injection, and the
  mitigations this threat routes around.
- [RAD-0020](Research-RAD-0020-Information-Flow-Control) — enforcement at the sink, which does not
  address corruption at authoring time.
- [RAD-0013](Research-RAD-0013-The-Codex-Entry) — the entry, which is what gets poisoned.
