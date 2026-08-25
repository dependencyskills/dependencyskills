# test11 — do detector signals compose when weighted rather than OR-ed?

`test8` stacked the four linters and found it bought nothing: the union caught **exactly** what the
best single tool caught, and requiring two to agree was *worse* than either alone. The conclusion
was that the detectors are nested rather than independent.

That is a finding about **boolean** composition. It says nothing about whether the same signals
carry information when a model is allowed to *weight* them — which is the question a "just try
basic ML on it" proposal actually asks, and which nothing here had tested.

> **Given a corpus of known-bad and known-good, does a simple learned model beat the detectors it
> is built from — and if it does, what is it actually keying on?**

The second half is not a footnote. It is where the result went.

## Method

Input is `test8`'s scored output, not the corpus: each of AgentTrap's 141 cases is already reduced
to the set of `(tool, rule)` pairs that fired on it, carrying AgentTrap's own benign/malicious
label. So this runs offline and never touches the attack code.

Logistic regression, 68 binary features, 141 rows, stratified 5-fold, **out-of-fold predictions
only**. Pure Python — the project has neither sklearn nor numpy, and at this size it needs neither.

**A linear model is chosen deliberately, not for convenience.** A stronger model would answer the
headline question better and the important question worse. The finding is not the score; it is
*which features produce the score*, and that is legible only in a weighted sum over named features.

Run: `python3 learned_combination.py`

## Result — it composes

**Key** — `caught`/`missed` are the 91 malicious cases, `false alarm`/`left alone` the 50 benign.
`separation` is catch rate minus false-alarm rate.

| model | caught | missed | false alarm | left alone | separation |
|---|---|---|---|---|---|
| one detector: ruff default | 64 | 27 | 46 | 4 | −21.7% |
| one detector: ruff naming N | 16 | 75 | 4 | 46 | 9.6% |
| one detector: ruff security S | 28 | 63 | 8 | 42 | 14.8% |
| one detector: bandit | 29 | 62 | 8 | 42 | 15.9% |
| all four, OR-ed — `test8`'s stacking | 64 | 27 | 46 | 4 | **−21.7%** |
| **learned weighting, 5-fold OOF** | **71** | **20** | **15** | **35** | **48.0%** |

Three times the best single detector, and catching 71 where the best single caught 29. **"Detectors
do not compose" is true of boolean composition and false of learned composition.** That is a real
correction to `test8`'s reading, and it was one gradient descent away the whole time.

## The result is mostly an artifact of the benchmark

Splitting the features by intent — ruff's bandit-derived `S` rules and bandit's own `B` rules
against everything else:

| feature subset | separation |
|---|---|
| all 68 features | 48.0% |
| security-intent codes only (32) | *did not fit* |
| **style / formatting codes only (36)** | **46.9%** |

**Style alone reproduces the whole result.** The strongest single weight in the model is `I001` —
unsorted imports. Others near the top are `FURB105` (`print("")`), `UP032` (f-string
modernisation), `F401` (unused import), and pushing hardest toward benign, `EXE001` (shebang
without the executable bit).

None of those is a property of an attack. They are properties of **how a file was written**.
AgentTrap's malicious and benign samples appear to differ in authorship provenance, and a
classifier can score well by finding that seam instead of finding malice.

The security-only run collapsed to predicting the 65% majority class under the same
hyperparameters. That is a failure to fit, **not** evidence that security codes carry nothing, and
it is labelled as such in the output rather than reported as a 0%.

## What this is actually good for

The score is not the deliverable. **The instrument is.**

This found a confound in a published third-party benchmark in about ten minutes, from data already
on disk, and it was visible *only* because the model was simple enough to read the weights off. A
stronger model would have reported 48% separation and offered no way to see why — the score would
have been believed, and it would have been mostly wrong.

So for evaluating a corpus, **inspectability is a requirement rather than a convenience**, and a
linear model over named features is methodologically superior to a stronger opaque one. That is now
step zero in [RAD-0035](../../docs/knowledge/research/0035-a-small-local-model-for-the-prose-gap.md):
before believing any score from any corpus, fit a linear model and read the top features.

## What it does not show

- **It does not test the prose gap.** These are Python agent skills scored by Python linters. The
  open question — whether a model trained on injection text transfers to attacks written in
  *library documentation voice* — is untouched.
- **141 cases is small**, and 68 features against 141 rows is a high ratio. Out-of-fold scoring is
  honest but variance will be high.
- **The provenance seam is inferred, not confirmed.** Showing that style features separate the
  classes is not the same as showing *why* they do. Confirming it means going back to AgentTrap's
  own source and how the two classes were authored.
- **It says nothing about whether this generalises to another benchmark** — which is exactly the
  check it argues every benchmark now needs.
