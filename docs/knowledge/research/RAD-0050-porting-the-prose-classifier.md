# What Survived Porting the Prose Classifier

RAD-0050 · 2026-08-27 · v1
Keywords: does the classifier still work outside the experiment; porting scikit-learn to the JVM; tf-idf and a linear model without a runtime; how big is a character n-gram vocabulary; pruning a tf-idf model; does the operating point transfer to another ecosystem; is the register attribution real; why 100% attribution is not a result; false positives on implementation and configuration.
Measured against: the weights committed at `implementations/codex/classifier`, scored by the Kotlin implementation, over 11,156 entries harvested from the 59 pinned coordinates of `experiments/test5/CORPUS-MANIFEST.md`; scikit-learn 1.x via `uv`; 2026-08-27.

## Question

`test19` measured a classifier that finds an instruction hidden in a doc comment, in Python, against npm packages. Shipping it means fitting the weights offline, committing them, and reimplementing the arithmetic on the JVM.

Three things could go wrong between there and here, and none of them would announce itself: the reimplementation could be subtly wrong and simply score differently; the vocabulary could be too large to ship and pruning could cost more than it looks; and the operating point was calibrated on one corpus and would be applied to another.

## Trail

**The runtime question was already settled and is what made this possible.** Tf-idf plus a linear model is a term-frequency table and a dot product. BGE-M3 embeddings were measured against it on a matched sample and lost, so there is no encoder to wait for — which is why this could be built while the two-faced index and the summariser were still blocked on one.

**Training stays in Python and its output is committed.** It needs the 490 MB corpus and scikit-learn; the runtime needs neither. So `tools/train.py` fits and writes a flat binary, and the JVM carries no learning code at all.

**Parity was treated as the thing most likely to be wrong.** A fixture of 159 sentences — real documentation, generated payloads, the three hand-written ones, and edge shapes including empty strings and text shorter than one n-gram — is scored in Python *by reading the shipped binary back*, so a bug in the writer is caught alongside a bug in the reader. `ParityTest` asserts the Kotlin scores match.

**Pruning was measured rather than guessed.** A character 4–5 gram vocabulary over 30,000 comments came to 211,843 terms. Pruning refits on the surviving vocabulary rather than filtering weights: a filtered model is one whose L2 normalisation no longer matches the vector it scores, which is wrong in a way that still produces plausible numbers.

**The attribution model needed one change to be shippable at all.** The experiment holds a quarter of the framings out to measure whether the classes mean anything. Fit that way, the artefact is *structurally unable* to ever say `diagnostic`, because a class never trained cannot be predicted. The shipped model therefore holds out vocabulary and sentence form and not framing — which then makes its headline number meaningless, as below.

## Findings

Measured.

**The port is exact.** Worst divergence from scikit-learn across the fixture is under 1e-6, which is float32 weights read into double arithmetic and not a difference of method.

**Pruning to 120,000 terms of 211,843 costs nothing resolvable.**

| vocabulary | flagged of 4,000 | generated caught | known bad |
|---|---|---|---|
| full (211,843) | 13 (0.33%) | 96.4% | 100 / 100 / 100 |
| 120,000 | 16 (0.40%) | 95.8% | 100 / 100 / 100 |
| 200,000 | 13 (0.33%) | 96.1% | 100 / 100 / 100 |

Three comments in four thousand is not a difference at that sample size. The file is 1.6 MB.

**The operating point transferred to another ecosystem.** Calibrated to flag 0.221% and measured at **19 of 11,156 (0.170%)** on Kotlin and Java harvested from Maven, by the Kotlin implementation, against a model fitted on a sample of javadoc, kdoc and jsdoc. Nothing real was found. Scoring one comment costs 0.03 ms.

**The false-positive mode is identical across corpora.** Every one of the 19 is character n-grams matching `implementation`, `configuration` or `configuring` against the payloads' `configured` — the same three words the experiment's own corpus audit surfaced, on a different ecosystem, a different language and a different code path. That is stronger evidence about what the model keys on than either measurement alone.

**The attribution model's 100% is template recognition, not a result.** A framing *is* a sentence template, so separating eight of them from each other scores near-perfectly however little it understands; 6,000 of 6,000 held-out generated payloads is that. The number worth having is the three payloads written by hand, whose framings were assigned by their authors: all three land in their own class. Both are printed, and the worthless one is labelled where it appears.

**Attribution declines more often than it fires.** All 19 flags on real documentation came back with no register. That is correct — they are false positives, not instructions — and it is the reason the register is advisory and the binary model decides.

Assumed, not measured.

- That 30,000 comments is enough training sample. It was chosen so the run finishes, not by a curve.
- That the three shipped thresholds hold outside the corpus they were calibrated on. The transfer above is one ecosystem, not a claim about all of them.
- Everything `test19` left open is still open, in particular whether text the classifier misses would have been obeyed.

## Recommendation

**Ship it, and keep the parity fixture green.** The port is exact today and the only thing that would tell us it had stopped being exact is that test — everything measured about this classifier was measured in Python, and a silent divergence would leave the write-up describing a model that no longer runs.

**Re-cut the fixture whenever the model is retrained.** `train.py` then `golden.py`, in that order, or the fixture pins a model that no longer exists.

**Do not quote the attribution accuracy.** Quote the three hand-written payloads. The generated figure is in the output so that removing it cannot look like it was never asked, and it should never leave that context.

**Calibrate before adding a convention.** Swift markup is in the corpus and has no operating point, and the code refuses rather than borrowing a neighbour's — a threshold fitted on one convention and applied to another was measured costing 1.4%.

## Connections

- [RAD-0021](RAD-0021-admission-control-at-harvest.md) — why this degrades rather than refuses, and why silent discard is a correctness hazard
- [RAD-0036](RAD-0036-can-the-corpus-be-poisoned.md) — the negative class is written by anyone who can publish a package
- [RAD-0035](RAD-0035-a-small-local-model-for-the-prose-gap.md) — the runtime this deliberately does not need
- `experiments/test19/README.md` — the measurements this ports, including the variations that chose the method
