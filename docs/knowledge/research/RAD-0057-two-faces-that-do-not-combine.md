# Two Faces That Do Not Combine

RAD-0057 · 2026-08-29
Keywords: does the two-faced index actually retrieve better; why did RAD-0040's 15 of 17 not reproduce; max versus reciprocal rank fusion for two vector faces; complementary rather than corroborating signals; why RRF made recall worse; what a summariser costs over a real dependency graph; why most rewrites are refused.
Measured against: the whole pipeline in one pass — `libdscodex` over llama.cpp b19cbe9, `gemma-3-270m-it-qat-Q4_0` as summariser, `bge-small-en-v1.5` F16 GGUF as encoder with mean pooling, Lucene 10.5.1, OpenJDK 26, macOS arm64; the 59 pinned coordinates of `experiments/test5/CORPUS-MANIFEST.md`, 11,155 entries, that experiment's 17 needs; `:index:endToEnd`, 2026-08-29.

## Question

> **Does searching each entry on both of its faces retrieve better than searching one, at the size a real dependency graph produces?**

[RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) measured raw documentation at 13 of 17, the rewrite at 10, and **both faces as two vectors at 15** — over 220 entries. That result is why [#6](https://github.com/dependencyskills/dependencyskills/issues/6) exists and why the index holds two vectors rather than one.

It had never been re-measured at scale, and it could not be until [#7](https://github.com/dependencyskills/dependencyskills/issues/7) existed to produce a rewrite for every entry rather than for 220 of them.

## Trail

### The whole pipeline, run once

Harvest, classify, summarise, index and query, over one real project's resolved dependencies:

| stage | | |
|---|---|---|
| harvest | 11,155 entries | 37 s |
| classify | 19 flagged (0.170%) | under 1 s |
| summarise | 9,900 stored, 1,238 degraded, 17 withheld | **24 min** |
| index and query | 9,900 rewrite faces of 11,155 | 112 s |

The summariser ran at **0.13 s an entry**, which matches `test25`'s 0.1 s for this model and confirms the estimate [RAD-0055](RAD-0055-optimisations-worth-testing.md) reasoned from. The 17 withheld are entries the classifier had already degraded, where a perfectly good rewrite was produced and deliberately not stored — the [#5](https://github.com/dependencyskills/dependencyskills/issues/5)/#7 interaction firing on real data rather than in a unit test.

### The result, and it is not the one the design predicted

| arm | searches | recall@1 | recall@10 |
|---|---:|---|---|
| lexical (#4's baseline) | 11,155 | 1 of 17 | 2 of 17 |
| documentation face only | 11,155 | 1 of 17 | **3 of 17** |
| rewrite face only | 9,900 | 1 of 17 | **3 of 17** |
| both faces, `DisjunctionMaxQuery` | 11,155 | 1 of 17 | **3 of 17** |
| both, scored exactly | 11,155 | 1 of 17 | **3 of 17** |
| both, reciprocal rank fusion | 11,155 | 1 of 17 | **2 of 17** |
| *either face's own top ten* | 11,155 | — | *5 of 17* |

**Adding the second face bought nothing.** And the earlier partial-coverage run's rewrite-face score of 9 of 17 was entirely an artefact of a 397-entry haystack that happened to contain every target — at full coverage that face scores 3, like the other one.

### Three explanations, tried in order

**It could have been the approximation.** `DisjunctionMaxQuery` unions two separate top-k searches, and the union of two top-k lists is not the top-k of the union. So the same combination was computed **exactly** — every one of the 11,155 entries ranked by the better of its two faces, with no top-k in between. It returns **3 of 17**, identical. The implementation is faithful; that was my first diagnosis and it was wrong.

**It could have been score calibration.** Two faces embed different text — a doc comment and a one-sentence paraphrase — so a doc-face cosine of 0.72 and a rewrite-face 0.72 need not mean the same thing, and `max` requires that they do. Reciprocal rank fusion combines by rank instead and is immune to that. It returns **2 of 17** — *worse than max*.

**What is left is the premise.** The faces are genuinely complementary: taking each face's own top ten and keeping everything in either gives **5 of 17**, against 3 for either alone. Each face finds needs the other misses, exactly as RAD-0040 described. Two of the seventeen show it directly:

| need | doc face | rewrite face | combined |
|---|---|---|---|
| `HttpRequestRetryConfig.retryIf` | **10** | — | — |
| `kotlinx.coroutines.flow.retry` | — | **5** | — |

Each was found by one face and lost by every combiner.

### Why every combiner lost them, and why RRF lost more

**Max and RRF both reward agreement between the faces. The design's premise is that they disagree.**

Under `max`, an entry a single face ranks 5th still has to out-score every entry the *other* face ranks highly, and there are 11,155 of those. Under RRF the penalty is explicit: an entry at rank 5 on one face and rank 8,000 on the other scores `1/65`, while an entry at rank 20 on **both** scores `1/80 + 1/80` and wins. RRF is built to reward corroboration, so it is structurally the wrong instrument for signals chosen because they fail differently — and it produced the worst number in the table, which is what that structure predicts.

RAD-0040 measured *fusing the vectors* as worse than keeping them apart. This is a different operation on the same intuition, and it lands the same way.

### Why the numbers are so low in absolute terms

Not this record's finding. [RAD-0019](RAD-0019-retrieval-at-scale.md) and `test5` already measured raw documentation collapsing toward zero by 3,000 entries; this corpus is 11,155. [RAD-0049](RAD-0049-the-lexical-baseline.md) set the lexical floor at 2 of 17. Everything here sits in that regime, and the two-faced index was the proposal for escaping it.

### What the refusals say, and it is not about safety

Of 1,238 degradations:

| rule | count |
|---|---:|
| too long | 580 |
| more than one sentence | 429 |
| imperative | 151 |
| names something outside the signature | 36 |
| contains code or markup | 29 |
| addresses a reader | 13 |

**1,009 of 1,238 — 81% — are shape failures.** A 270 MB model asked for one sentence under 40 words produces two, or fifty. Only 229 refusals, **2.1% of the corpus**, are the safety rules doing safety work. So 9% of entries lost their rewrite face to formatting, and that is a model-capability or prompt problem wearing a verifier's clothes.

## Findings

**Measured.**

1. **RAD-0040's two-faced gain does not reproduce at 11,155 entries.** Both faces score 3 of 17 at recall@10, the same as either face alone.
2. **The faces are complementary, as claimed.** The union of their individual top tens is 5 of 17.
3. **No combiner tried reaches that union.** Exact max: 3. Lucene's `DisjunctionMaxQuery`: 3. Reciprocal rank fusion: 2.
4. **The combination is the failure, not the approximation.** Exact and approximate agree exactly, so #6's implementation is faithful to the rule it was given. The rule is what is wrong.
5. **A summariser pass over one project's dependencies costs about 24 minutes** at 0.13 s an entry on a 270 MB model.
6. **81% of verification refusals are shape, not safety.**

**Assumed, and the largest weakness here.** That 17 needs can distinguish 3 from 5. They cannot, with any confidence — the whole result rests on two questions moving, and a different set of needs could plausibly reorder every row in the table. Everything above is directional. The *mechanism* — that max and RRF both reward agreement between deliberately disagreeing signals — is an argument that does not depend on the sample size, and is the part worth carrying forward.

## Recommendation

**Stop trying to fuse the two faces into one ranking.** Three combiners now agree that it loses recall each face had, and the reason is structural rather than a tuning failure. What the measurement supports is returning **each face's own results and merging them as a set** — which is what the 5-of-17 bound is. For a search that hands an agent five or ten candidates, presenting the union of two faces' top five each is an ordinary thing to do and needs no calibration between them.

**That revises an acceptance criterion of #6**, which reads *"an entry scores as its best-matching face"*. That rule is now measured as the wrong one. The index shape — two vectors, never concatenated, scope enforced inside the query — is untouched and still correct; what changes is what the query layer does with two result sets.

**Re-measure with more needs before treating any of this as settled.** Seventeen is too few to separate 3 from 5, and this corpus has exactly one project's dependencies behind it. That is the cheapest way to raise confidence in any direction, and it costs no model calls: the store is persisted and a re-run is 2.5 minutes.

**Look at the shape failures before the safety rules.** Recovering most of the 1,009 costs nothing in safety and would give the rewrite face 9% more coverage. Candidates, none tested: a stop condition at the first sentence boundary, a smaller `MAX_WORDS` with a retry, or a model that follows a length instruction more reliably. Whether more coverage moves retrieval at all is the open question, and finding 2 above says not to assume it.

**What would change the answer.** A larger needs set. A materially better rewrite — these come from a 270 MB model, and RAD-0051 established that degradation rate cannot rank models, so the faithfulness and retrieval axes were never measured. Or a corpus small enough that RAD-0040's regime applies, which is the case this contradicts rather than the case that matters.

## Connections

- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) — the 15 of 17 this fails to reproduce, and the vector-fusion result this echoes
- [RAD-0049](RAD-0049-the-lexical-baseline.md) — the 2 of 17 floor everything here is measured against
- [RAD-0019](RAD-0019-retrieval-at-scale.md) — why raw documentation retrieves badly at this size
- [RAD-0055](RAD-0055-optimisations-worth-testing.md) — the summariser throughput this confirms, and the batching it makes concrete
- [RAD-0051](RAD-0051-a-jvm-generative-runtime.md) — why degradation rate cannot rank the model that produced these rewrites
