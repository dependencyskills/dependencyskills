# Known-Bad as a First Gate

RAD-0060 · 2026-08-29
Keywords: a signature list for injection payloads; virus definitions for prose; should known-bad be dropped before the classifier; does a signature list cause false positives; how much rewording defeats a signature; what does the pipeline actually claim to catch; have we ever seen a real injection in a published library.
Measured against: `test9`'s 7 prose and doc-opening payloads against 11,155 doc comments harvested by this project from the 59 pinned coordinates of `experiments/test5/CORPUS-MANIFEST.md`; `experiments/test27`, 2026-08-29. AgentTrap's 91 cases were unavailable — that corpus is not vendored here.

## Question

> **Would a list of known payloads, matched before anything else runs, earn its place — and what would it cost in refused honest prose?**

The proposal is a virus-definition analogy: keep a list of injection text that has been seen in the wild, and when one appears, drop it without troubling the classifier or the summariser. Cheap, precise, and useless against anything new.

## Trail

### It is not the filter this design argues against, and that objection was mine to withdraw

This project's central claim is that **a filter has to be right and a rewriter does not** — every comment is processed identically, so there is nothing to classify and nothing to get wrong. A filter bolted into that would invert it.

A gate that only **rejects** does not. Everything it does not match proceeds through the full pipeline exactly as before, so a false negative costs nothing and nothing relies on it to let anything through. The asymmetry is what makes it additive: it can only remove work, never authorise it.

The cost is entirely in **false positives** — honest documentation refused because it resembles a payload. That is the thing to measure.

### Nothing off the shelf does this

[RAD-0028](RAD-0028-sast-tooling-as-a-detection-layer.md) measured the obvious candidates against 11 payloads:

| tool | caught |
|---|---|
| SpotBugs + find-sec-bugs | 0 of 11 |
| Semgrep, published rulesets | 0 of 11 |

Both look for code patterns; this is prose. So there is no ruleset to borrow, and a list would have to be built.

### What the measurement found

Overlap is 4-word shingles of a signature present in a document — how much of a known payload has to survive rewording to still be caught.

| threshold | real doc comments flagged, of 11,155 |
|---|---:|
| exact substring | 0 |
| overlap ≥ 50% | 0 |
| overlap ≥ 30% | 0 |
| **overlap ≥ 10%** | **0** |

The highest overlap any real doc comment reaches against any signature is **0%**. Not "under the threshold" — real library documentation and these payloads share no 4-word sequence at all.

### And the positive control, which is the half that matters

A null result from a harness that is silently not analysing is not a null result. Every row here has a known right answer:

| | overlap |
|---|---:|
| a signature against itself | 100% |
| planted verbatim in a real doc comment | 100% |
| the same payload, two phrases reworded | **55%** |
| the same meaning, fully paraphrased | **0%** |
| a real doc comment, untouched | 0% |

### What that shape means

**Precision is free.** Any threshold between 10% and 50% touches no real documentation. The standard objection to a signature filter — that it will start refusing honest prose — does not materialise, and there is an unusual amount of headroom to spend.

**Recall is the entire problem.** A paraphrase carrying identical meaning is indistinguishable from unrelated documentation. The mechanism detects **text reuse, not intent** — which is exactly what a virus signature does, and why the analogy holds right down to where it breaks.

**Both this and the classifier work for the same underlying reason**, and it is worth naming because it bounds both: attack prose and API documentation are different registers. A payload written in the idiom of library documentation would score 0% here and would be the hardest case for the classifier too. Neither mechanism addresses it, and nothing in the pipeline currently does.

### The base rate, which nobody has written down

Across the same 11,155 entries the classifier flagged **19**, and every one is ordinary documentation — *"Loads an application configuration."*, *"Configuration for the application."*

**This project has never observed an injection in a published library.** Every payload it has measured against was planted by us or drawn from a published benchmark.

That is much weaker evidence than it appears, and the weakness is the finding. Those 59 coordinates are `kotlin-stdlib`, `kotlinx-*`, `ktor-*` and `slf4j` — two publishers, among the most reviewed code on Maven Central. **Zero observed, in the sample least likely to contain one.** It does not establish a low base rate; it establishes that the base rate is unknown.

The corpus is nonetheless the right one for almost everything else it is used for. It is the base layer every JVM developer has, which makes it maximally representative of what is on a real classpath and minimally representative of where an attacker would go.

## Findings

**Measured (2026-08-29, `experiments/test27`).** Zero of 11,255 real doc comments match any of 7 known payloads at any overlap threshold down to 10%. A payload survives two reworded phrases at 55% overlap and a full paraphrase at 0%. The harness's positive control passes.

**Measured previously, cited.** Off-the-shelf SAST catches 0 of 11 (RAD-0028). The classifier flags 19 of 11,155 real entries, all benign.

**Established by argument.** A reject-only gate cannot invert the rewriter's claim, because nothing depends on it to admit anything.

**Assumed.** That a larger signature list keeps this precision. Seven signatures is a small sample and every one is ours; collisions become likelier as a list grows, and a list drawn from real attacks might use vocabulary closer to documentation than ours does.

## Recommendation

**Build it, and describe it accurately.** It is cheap, it has no measured cost in precision, and it catches the case that actually recurs in practice — copy-paste and light editing. It is **a gate against the unimaginative, not a defence**, and the write-up should say so in those words rather than reporting a catch rate that flatters it.

**Operating point around 30–40% overlap.** Above the 0% real documentation reaches and below the 55% a two-phrase edit survives, with the whole range between measured empty.

**Degrade, never delete.** A hit should set the entry `Degraded` — the state that already exists and is already measured safe — and record which signature fired. A dropped entry with no reason is an invisible deletion, and an unattributed rejection cannot be reviewed when a signature goes bad.

**Per sentence, not per comment**, matching the classifier's grain. One poisoned sentence should not cost a whole capability.

**The list does not exist and someone has to start it.** Virus definitions have decades of infrastructure behind them; injection prose has none. AgentTrap's 91 cases are the obvious seed and are not vendored here. Whether this project maintains a list, or waits for one, is a commitment rather than a feature.

**Do not let it become the thing relied on.** The moment a report says "the signature list caught N", the design's argument has been quietly inverted — the value was always that *nothing* is trusted to classify correctly. It reports; it never authorises.

**What would change the answer.** A signature list an order of magnitude larger, which is where precision would first be tested honestly. A real in-the-wild payload, which would say something about register that all seven of ours cannot. Or a paraphrase-robust matcher, which would move this from text reuse toward meaning and make it a different mechanism with a different cost.

## Connections

- [RAD-0028](RAD-0028-sast-tooling-as-a-detection-layer.md) — why nothing off the shelf covers this
- [RAD-0031](RAD-0031-which-vectors-reach-a-real-project.md) — why benchmark prevalence tracks demonstrability rather than reach
- [RAD-0036](RAD-0036-can-the-corpus-be-poisoned.md) — the publisher base, and why three is too narrow
- [RAD-0050](RAD-0050-porting-the-prose-classifier.md) — the classifier this would sit in front of
- [#24](https://github.com/dependencyskills/dependencyskills/issues/24) — retraining the classifier on found payloads, the alternative home for the same data
