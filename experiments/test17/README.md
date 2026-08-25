# test17 — classify a whole doc comment: pass it on, or don't

`test13` and `test14` both worked on **sentences**, and both failed. A sentence pulled from a doc
comment loses the thing that might matter — whether the document as a whole was *describing a
capability* or *instructing a reader*. This treats one doc comment as one document, which is also
the unit the harvester emits and the unit a gate has to decide on.

> **Given the whole comment, do we pass it on to the agent or not?**

Run: `python3 whole_doc.py --diagnose` then `--classify`. No network, nothing executed.

## What the right cost bar is — a correction to the tests before this one

Every prose rule so far was scored against `test10`'s **0.221%** and declared dead above it. That
bar is wrong for prose, and the error runs backwards through `test13` and `test14`.

`test10`'s rules reject **identifiers**, where a false rejection loses an API. A prose rule rejects
a **doc comment**, and the entry survives as symbol plus signature. The project has already
measured what that fallback is worth:

- a bare signature is enough for an agent to **use** a capability — 7 of 8 (`test0`)
- sending the agent **no prose at all** prevented the planted credential leak 0 of 3 while the
  developer's task still completed 2 of 3 (`test7`)

So a false rejection is not a destroyed entry. It is an entry demoted to a state this project
already carries as a working control. It is also incoherent to call 1.73% expensive in a pipeline
whose successful strategy is **removing 82% of the surface on purpose**.

The cost is not zero: prose is what makes an entry *findable*, and raw doc text retrieves at 29%
against 77% for caller's-words entries (`test5`). **Rejecting a comment costs discoverability, not
usability.** The real question was never "is this under 0.221%" but *how much retrieval are we
willing to lose to close a channel* — and that is a graded decision, not a threshold.

**And the comparison is against nothing, not against perfection.** Unaided, agents pick the right
library **0 of 18** (`test0/measurement`). A filter that rejects a third of prose still delivers two
thirds of an index that does not otherwise exist. Half a codex beats no codex, so a prose rule does
not have to be cheap — it has to be *worth its loss*.

That shifts which number matters. A rejection rate spread evenly across libraries costs coverage
uniformly; one concentrated on particular publishers removes whole libraries from the index. **The
generalisation gap on unseen publishers (1.3×) is therefore a more important measurement than the
rejection rate**, and it is the one to drive down.

## The design, and the trap it avoids

Bag-of-words over library documentation measures **topic**, not stance: a doc about elliptic curves
is far from any global centroid, and that is a subject rather than an anomaly. Two choices prevent
that. Features describe the document **as a whole** — length, sentence shape, mood, address, how
much is about the API versus the reader's environment — and never its vocabulary. And normal is
computed **per library**, so a doc is compared with the ones it must sit beside, which is also the
threat.

Per-library normalisation was not a refinement. Scoring globally, unseen publishers were rejected
**2.0×** more often than budget and the top of the tail was ASCII-art banners and gRPC service
descriptions. Per-library took that to **1.3×**, and the calibrated gate now tracks its budget
(5% requested → 5.006% delivered).

## Result 1 — the aggregate model fails, and it was my bug

| budget | rejected, unseen publishers | payloads caught |
|---|---|---|
| 0.221% | 0.278% | **0 of 3** |
| 1.0% | 0.955% | 0 of 3 |
| 2.0% | 1.896% | 1 of 3 |

Read alone that says whole-document novelty does not work. It is more specific than that.

## Result 2 — the signal was there, and equal weighting buried it

Probing the model with the payloads rather than reading its weights:

| feature | payload z-scores |
|---|---|
| `envish_rate` | **8.0, 6.3, 3.7** |
| `order_rate` | 6.2 |
| `deprec_rate` | 3.9 |
| `modal_rate` | 2.5, 3.0, 2.3 |
| the other 13 | all under 1.0 |

The payloads are four to eight standard deviations out on **two or three** features and
indistinguishable on **thirteen**. Summing all seventeen equally dilutes a strong signal into
noise. That is a defect in the aggregation, not an absence of signal.

Scoring on the discriminative features alone:

| model | budget | rejected, unseen | caught |
|---|---|---|---|
| all 17 features | 0.500% | 0.929% | 0 of 3 |
| discriminative 5 | 0.500% | 0.272% | 1 of 3 |
| **`envish_rate` alone** | **0.500%** | **0.158%** | **2 of 3** |

## Result 3 — the same signal, priced 200× apart

`test13` measured "mentions something external" at **29.8%** and killed it. That was a **binary
presence** test. As a **library-normalised density** the same signal costs **0.158%**.

The difference is what per-library normalisation does to it. A logging library talks about logs
constantly — that is its normal, and it cancels. A date-formatting library whose doc comment is a
quarter environment-words is eight standard deviations from its own siblings.

**A rule's form can be worth two hundred times its content.** That holds regardless of whether this
particular rule survives, and it is the transferable finding here.

## The caveat that governs everything above

**The discriminative features were chosen because the payloads scored high on them.** That is
exactly the circularity `test13` was bitten by. With three payloads, "the discriminative subset" is
fitted to three examples, and the word list behind `envish_rate` was written after reading them.

So Result 2 and Result 3 are **hypotheses generated from three points, not measured controls.**
Validating them needs payloads that were not used to select the feature — the same missing payload
set that blocked `test14` and `test15`.

What does **not** depend on the payloads, and stands: per-library normalisation improves
generalisation 2.0× → 1.3×; the gate calibrates correctly against its budget; and equal-weighted
aggregation destroyed a signal present at 8 sd.
