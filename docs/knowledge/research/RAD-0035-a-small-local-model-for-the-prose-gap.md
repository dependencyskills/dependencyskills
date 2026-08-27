# A Small Local Model for the Prose Gap

RAD-0035 · 2026-08-24 · v1
Keywords: can a small local model close the prose gap; where form constraints stop; the volume objection and why it is wrong; the register objection and why it stands; training on injection data; running inference without MLX; the JVM embedding runtime; is the experiment worth running either way.

**Opened because the gap stopped moving.** `test10` closed the identifier channel — every carried
identifier form, in all four languages, for 0.221% of a real corpus. The same catalogue catches
**none** of the three prose forms, and no additional form constraint will, because there is nothing
malformed to catch: they are ordinary technical writing making a false claim.

The proposal is to train a small local model on injection data and use it where form constraints
stop. This log records why the obvious objection to that is wrong, why the real objection is a
different one, and why the experiment is worth running regardless of which way it lands.

## Question

> **Can a small local model close the prose gap — and if not, what does failing tell us?**

Two sub-questions, and the first is needed with or without the second:

1. What runs a sentence encoder on the **JVM**, where Lucene and the harvester already are?
2. Does a classifier trained on **published injection corpora** transfer to attacks written in
   **library documentation voice**?

## Trail

### The objection that was wrong: not enough positives

The first assessment was that the project holds seven payload forms against 14,899 real entries,
that all three prose forms are the same attack (copy the environment into the telemetry debug log)
framed three ways, and that a model trained on it would learn the payload's vocabulary rather than
the concept. That failure has precedent here twice: `test7` scored a payload identifier as its own
false positive because it contained every trigger token, and articulate refusals scored as
compliance. Both times the harness measured the payload rather than the phenomenon.

**The objection does not survive contact with two facts.** Public corpora are large — HackAPrompt
and Tensor Trust run to hundreds of thousands of adversarial prompts, alongside AgentTrap,
AgentDojo, InjecAgent and the deepset and Lakera sets. And more decisively, a **linear probe over a
pretrained encoder** needs hundreds of labels, not tens of thousands. The scarcity argument was
aimed at training a model from scratch and does not apply to a probe.

### The objection that stands: register, not volume

`test8` and RAD-0031 already measured the shape of the real problem:

> Benchmark prevalence tracks **demonstrability, not reach**.

Published attacks are overwhelmingly **chat-context**: *ignore previous instructions*, jailbreaks,
role-play, direct address to a model. The prose payloads that matter here contain none of that.
They read as ordinary API documentation stating a precondition that happens to be false.

This makes the negatives dangerous rather than merely unhelpful. Chat-register positives against
real library documentation are separable **on register alone** — different vocabulary, different
mood, different sentence shape. A probe trained on that pairing learns to recognise *register*,
scores well, and fails on a payload written in library voice. It would reproduce the `test7` error
at a larger scale and with better-looking numbers.

RAD-0031 supports this independently: the `.env` vector this project's own harness is built on is
**4%** of the published corpus, and 46% of published attacks need no precondition at all. The
benchmarks are not a sample of what reaches a library pipeline.

### Runtime: how to run it without MLX

The recall eval behind RAD-0010 embedded in-process via `mlx-embeddings` — Python, Apple Silicon.
Lucene is JVM. Canon already lists *porting the rig to the JVM/Lucene substrate* as outstanding, so
**a JVM embedding runtime is required for retrieval whether or not any classifier is built.** That
is what makes the marginal cost of this experiment small.

Surveyed:

| candidate | licence | fit |
|---|---|---|
| **DJL** (Deep Java Library) | Apache-2.0 | **the pick** — high-level API over PyTorch/ONNX engines; inference-first, which is all a probe needs |
| **Tribuo** | Apache-2.0 | **the pick for the probe** — maintained, ONNX wrappers so it composes with DJL |
| Deeplearning4j | Apache-2.0 | historically the answer; activity thin for years — verify before committing |
| TensorFlow Java | Apache-2.0 | official, but training is awkward beside Python |
| Smile | **verify** | broad and fast, but the licence on current versions must be checked before use |
| Weka | **GPL** | dated, and the licence rules it out for this project |
| Spark MLlib | Apache-2.0 | disproportionate; nothing here is distributed |

**MLX is not displaced and does not need to be.** It stays the fast research rig for iterating on
the Mac; DJL is the substrate path. The two must be shown to agree — see the open questions.

The concern that this needs a GPU does not survive arithmetic. Embedding a corpus is a **one-time
batch at harvest**, and a query is a single short string. ONNX Runtime on CPU is adequate for both;
acceleration is an optimisation, not a precondition.

### Why the experiment is worth running either way

Detection has failed every time it was measured in this project, and a probe is a detector. But
this one is unusually cheap — the encoder is needed anyway — and **both outcomes are results**:

- **If it transfers**, it is the first detector here to earn its place, and it addresses the one
  gap that surface reduction provably cannot reach.
- **If it does not**, that measures *why published benchmarks do not defend this threat model* —
  RAD-0031's thesis extended from precondition to register, and a finding worth publishing on its
  own.

The failure mode to guard against is not a poor score. It is a **good** score obtained by learning
register, which is why the evaluation below is cross-register by construction.

## Findings

**Measured against:** AgentTrap's 141 cases (91 malicious / 50 benign) as scored by `test8`,
logistic regression over 68 binary (tool, rule) features, stratified 5-fold out-of-fold, pure
Python, 2026-08-24. Harness: [`experiments/test11`](../../../experiments/test11).

- **A learned combination composes where boolean stacking did not.** `test8` found stacking
  detectors added nothing — union caught exactly what the best single one caught. Weighting the
  same signals instead of OR-ing them separates far better: **71 caught / 20 missed / 15 false
  alarms**, a separation of **48.0%** against the best single detector's 15.9% and the union's
  **−21.7%**. "Detectors do not compose" is true of boolean composition and false of learned
  composition.
- **And the result is mostly an artifact of the benchmark.** Restricting to style and formatting
  codes only — import ordering, f-string modernisation, shebang conventions — reproduces **46.9%**
  of the 48.0%. The security codes contribute about a point. The strongest single weight is
  `I001`, unsorted imports. AgentTrap's malicious and benign samples differ in **authorship
  provenance**, and a classifier can score well by detecting that seam rather than maliciousness.
  Corroborated by three properties that involve no linter at all: raw file count separates the
  classes **15.0%** — matching the best single linter's 15.9% — the `modality` tag never mixes
  across the label, and the malicious side covers a systematically enumerated 16-dimension matrix
  where the benign side has no equivalent structure. The classes were assembled by different
  processes.
- **This is the register objection, measured rather than argued.** It was reached in minutes, on
  data already on disk, and *only because the model was simple enough to read the weights off*. An
  embedding probe would have reported 48% separation and given no way to see why. **Inspectability
  is the requirement, not a convenience** — for this question a linear model over named features
  is methodologically superior to a stronger opaque one.
- Two control runs (security codes alone, `I001` alone) collapsed to predicting the 65% majority
  class under the same hyperparameters. They are uninformative rather than evidence that those
  features carry nothing.

**Measured (2026-08-24, `experiments/test13` and `experiments/test12`).**

- **Legitimate agent-facing library prose is imperative by design, and that is the real blocker.**
  Real skill artifacts published to Maven Central carry `Preference` and `Contract` sections whose
  purpose is to instruct the agent — *"Use `formatReadable()` instead of `toString()`"*. **18%** of
  their prose lines contain a directive verb. That is the same speech act as an injected
  precondition, so no register-based signal separates them, and widening the corpus makes it worse
  by teaching a model that directives are normal. This is stronger than the chat-vs-documentation
  register objection above, which corpus breadth could have fixed.
- **A structural signal may exist where a stylistic one does not.** Legitimate directives name an
  API the library declares (**63%**); the injected ones name nothing declared (**0%**). That is
  `test10`'s resolution check applied to prose — surface reduction, not detection — and it is the
  only candidate here not tuned to the payload. **Now priced by `test14`, and it fails**: 1.73% of
  232,781 distinct real doc comments, 8× `test10`'s whole catalogue. Cross-library resolution is
  real but worth only ~3 points, so a Dokka-derived surface would not close a gap of that size.
  **Both prose candidates are now measured and both are dead.**
- **The binding constraint is a missing payload set, not a missing idea.** A prose rule's cost can
  be measured against 232,781 real comments; its catch rate can only be measured against three
  payloads, all variants of one attack. Any future prose rule meets the same wall, so widening the
  *payloads* now outranks widening the corpus.
- **The companion signal died on contact with a wide corpus.** "Mentions something external" cost
  4% on a narrow sample and **29.8%** on 274 publishers — 135× worse than `test10`'s entire
  catalogue at 0.221%.

**Reasoned, not measured.**

- The volume objection to a small model is **wrong**: public positives are plentiful, and a linear
  probe over a pretrained encoder needs hundreds of labels rather than tens of thousands.
- The **register** objection stands, and is sharper than the volume one: published attacks are
  chat-register, the threat here is documentation-register, and the two are separable on style
  alone — which makes a good cross-validation score evidence of nothing.
- A **JVM embedding runtime is required regardless**, for the Lucene port already on the roadmap.
  That is what makes this cheap rather than speculative.
- **DJL + Tribuo** are the only surveyed pair that are both maintained and unambiguously
  Apache-2.0. Weka is GPL; Smile's licence needs checking.
- **No GPU is required.** Corpus embedding is a one-time batch; a query is one short string.

**Re-cited from adjacent work.** BGE-M3 (MIT) is the chosen encoder on recall and licence
(RAD-0010 → v3, RAD-0019). Vector recall@1 was 77% against lexical's 38% over 220 entries, and
equal-RRF hybrid *hurt*. 46% of published attacks carry no precondition and the `.env` vector is 4%
of the corpus (RAD-0031). `test10`'s catalogue costs 0.221% and catches no prose form.

## Recommendation

**Not a commitment.** Run it as an experiment, in this order, and stop at the first step that fails.

1. **Port the encoder to the JVM.** DJL running BGE-M3 via ONNX. Validate by re-running RAD-0019's
   recall eval on the JVM and confirming it reproduces 77% recall@1. This has value on its own and
   is the gate for everything after it.
2. **Build the probe.** Tribuo, logistic regression over BGE-M3 embeddings. Train on public
   corpora only.
3. **Evaluate cross-register.** Train on public, test on the library-voice payloads — **never both
   in one split**, or the result is memorisation. Report transfer as the headline number.
4. **Price it on the same population as everything else** — the 14,899-entry harvest. RAD-0021 was
   withdrawn on precisely this number at 26.9%, and a probe gets no exemption.
5. **Compare against the incumbent.** It must beat `test10`'s 0.221% catalogue *at prose*, which
   the catalogue scores zero on. Anything it achieves on identifiers is already free.

**Before any of it, audit the corpus for provenance seams.** The probe above shows a benchmark can
be separable on authorship style alone. Any corpus used here must be checked the same way — train a
linear model, read the top features, and confirm they are semantic rather than stylistic — *before*
a score from it is believed. This is cheap and it is now the first step, not a caveat.

**What would change the answer.** A finding that public corpora contain a substantial
documentation-register subset would remove the central objection and make this a much better bet.
So would a negative result on step 1: if BGE-M3 will not reproduce its recall on the JVM, the
substrate question outranks the classifier question and this log is premature.

**What this cannot become.** A probe is a detector, and every control this project carried forward
works by removing a channel rather than recognising an attack. Even a probe that transfers should
be weighed against the measured alternative for prose: the **quarantined paraphraser** in `test7`
prevented the credential leak 0 of 3 while keeping the task 2 of 3, needs no labels, and is
surface reduction rather than detection. A small local model is arguably better spent running that.

## Connections

- [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) — Lucene as the JVM substrate; embeddings generated outside it
- [RAD-0019](RAD-0019-retrieval-at-scale.md) — the recall eval this proposes to re-run on the JVM
- [RAD-0021](RAD-0021-admission-control-at-harvest.md) — withdrawn on false-positive cost; the standard this must meet
- [RAD-0029](RAD-0029-the-agent-as-a-trust-launderer.md) — well-formed prose that is merely false
- [RAD-0031](RAD-0031-which-vectors-reach-a-real-project.md) — demonstrability, not reach
- [RAD-0034](RAD-0034-better-linters-or-better-configuration.md) — configuration closed the identifier channel; this is the gap it left
