# classifier

Scores harvested documentation for an instruction hiding inside it, and marks the entry when it finds one.

**Nothing is ever discarded.** A suspect entry keeps its row, its content address and its place in the search index; it is still found and still answers with its symbol and signature. What it loses is the right to have a rewrite produced for it — and the rewrite is the only thing that ever crosses to an agent.

That asymmetry is the design. [RAD-0021](../../../docs/knowledge/research/RAD-0021-admission-control-at-harvest.md) rejected admission control at harvest and called silent discard a correctness hazard, arguing for down-weighting instead. Dropping the entry — or dropping its retrieval key — would be a deletion wearing a safety control's costume, and an entry with no key cannot be found at all, which makes the safe outcome indistinguishable from the store losing it.

**It is not a fortress and must never be written up as one.** It catches casual and accidental injection. Rewording the same instruction costs it about ten points, and whether text it misses would have been obeyed is unmeasured and stays open.

## Using it

```kotlin
val classifier = ProseClassifier()
val verdict = classifier.classify(doc, docFormat = "javadoc")
if (verdict.isSuspect) println(verdict.explain())

// Or over everything a coordinate owns, marking the store as it goes.
val report = codex.classifyEntries(coordinate, classifier, onRejection = ::log)
```

The pass is **idempotent and reversible**: it sets the state each entry's current score implies, so re-running under a lower threshold restores what a stricter one degraded. A threshold is an operator's setting, and a control that can only ratchet one way turns a setting into a decision nobody can take back.

## The operating point is per documentation convention

The model generalises across conventions — catch was 100% in all nine train/test pairs — but the false-positive rate varies seventeen-fold by direction, and a threshold fitted on jsdoc and applied to javadoc cost 1.4%. So calibration is per-convention state, not one constant.

| convention | shipped threshold |
|---|---|
| `javadoc` | -2.6755 |
| `kdoc` | -2.8141 |
| `jsdoc` | -3.0323 |

A convention with no operating point is **refused rather than given a neighbour's number** — `NoCalibrationException`. Approximating would present as the classifier being noisy rather than as being misconfigured. `classifyEntries` reports those entries under `uncalibrated` instead of passing over them, because "nothing suspect here" and "nothing was looked at" must not be the same empty answer.

## What it costs, as shipped

The committed weights, through this Kotlin code, over documentation this project harvested from the 59 pinned coordinates of a real Ktor server project — not the experiment's numbers carried over.

| | |
|---|---|
| distinct entries scored | 11,156 |
| sentences scored | 22,375 |
| **flagged** | **19 (0.170%)** |
| time to score one comment | 0.03 ms |
| conventions with no operating point | none |

**0.170% against an operating point calibrated to 0.221%**, on a corpus and an ecosystem the model never saw — it was fitted on javadoc, kdoc and jsdoc sampled from the whole corpus, and measured here on Kotlin and Java from Maven. Nothing real was found, which is the second of the outcomes worth running it for.

Every one of the 19 is the same false positive the experiment documented: character n-grams matching `implementation`, `configuration` and `configuring` against the payloads' `configured`.

> `-2.7922` — "Most implementations should avoid calling [block] in-place."
> `-2.2389` — "Loads an application configuration."
> `-1.1705` — "Configuration for the application."

**Where it is weak.** Two registers were close to invisible to the multi-class variant at about 4% — and *which* two moved when the payload grammar changed, so that is a property of the grammar it was measured on rather than of the registers. Paraphrasing a payload costs roughly ten points, and `deprecation` is the weakest at 72%.

## The register is advisory

`ProseVerdict.register` names the shape the instruction was written in — `precondition`, `deprecation`, `policy` and five more. It comes from a **second model**, because splitting the same decision across nine classes was measured catching 75.9% where the binary model catches 96%. Attribution is not free, so it does not decide anything.

A null register on a suspect comment means the label is missing, never that the comment is clean. All 19 flags above came back unattributed, which is the right answer when the flags are false positives rather than instructions.

The honest attribution number is against the three payloads that were written by hand rather than generated: all three land in their own class. Against generated payloads it scores 100%, and that figure is **template recognition** — a framing *is* a sentence template — so it is recorded and labelled rather than quoted.

## Why there is no runtime here

Tf-idf plus a linear model is a term-frequency table and a dot product. The weights are fitted offline and committed, so this module has no learning code, no network and no model download — which is what made it buildable while the encoder and the summariser were still waiting on a runtime. BGE-M3 embeddings were measured against it on a matched sample and lost.

## Retraining

```
uv run --with scikit-learn --with scipy python tools/train.py
uv run --with scikit-learn --with scipy python tools/golden.py
```

Training needs `experiments/corpus/corpus.db`, which is 490 MB, gitignored and rebuildable. `train.py` prints what pruning costs and picks nothing for you; `golden.py` re-cuts the parity fixture.

**Run `golden.py` after `train.py`, always.** `ParityTest` scores the shipped binary against scikit-learn's own numbers for the same sentences, and it is the test this module exists to have: everything measured about this classifier was measured in Python, and a reimplementation that is subtly wrong does not fail — it just scores differently from the model the write-up describes.

### The model files

`prose.model`, 1.6 MB, little-endian:

```
"DSPC"                     magic
int32                      format version
float32                    intercept
int32                      term count
int32, bytes               vocabulary, newline-separated UTF-8
float32[terms]             idf
float32[terms]             coefficients
int32                      convention count
  int32, bytes, float32      name, threshold
```

`register.model`, 4.3 MB, `"DSPR"`, holds the class names and a coefficient row per class over the **same vocabulary** — one analyser to keep honest rather than two that can drift apart. `prose.json` beside them is human-readable metadata and nothing reads it at runtime.

**The vocabulary is pruned to 120,000 of 211,843 terms**, and the model is *refit* on the pruned vocabulary rather than having its weights filtered — a filtered model is one whose L2 normalisation no longer matches the vector it scores. `train.py` prints the sweep:

| vocabulary | flagged of 4,000 | generated caught | known bad |
|---|---|---|---|
| full (211,843) | 13 (0.33%) | 96.4% | 100 / 100 / 100 |
| 80,000 | 15 (0.38%) | 96.7% | 100 / 100 / 100 |
| **120,000** | **16 (0.40%)** | **95.8%** | **100 / 100 / 100** |
| 200,000 | 13 (0.33%) | 96.1% | 100 / 100 / 100 |

The differences are three comments in four thousand and are not resolvable at that sample size; 120,000 was taken because it is the smallest that shows no movement, at a third of the file size.
