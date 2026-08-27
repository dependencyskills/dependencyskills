# A JVM Embedding Runtime

RAD-0047 · 2026-08-27 · v2
Keywords: how does a JVM process embed text; running BGE-M3 without MLX; DJL versus Tribuo versus ONNX Runtime; do JVM embeddings match the Python ones; does Lucene support a filtered kNN query; scope filter inside the search rather than after it; how does the model file ship; what blocks the two-faced index.

**v2 (2026-08-27) — the probe ran, and every question it asked came back favourable.** A JVM runtime reproduces the reference embeddings at cosine 0.99999 and returns bit-identical recall; Lucene holds two vectors per document and filters a kNN query by coordinate from inside the search. It also turned up something nobody was looking for: the reference vectors were **mean-pooled**, not CLS-pooled as BGE-M3 documents.

Measured against: DJL 0.36.0 over ONNX Runtime 1.21.1, Lucene 10.5.1, OpenJDK 26.0.2.1, macOS arm64 CPU; `BAAI/bge-m3` official ONNX export (fp32) against `mlx-community/bge-m3-mlx-fp16`; `experiments/test23`, 2026-08-27.

## Question

> **Can a JVM process embed text well enough to reproduce the retrieval this project has already measured — and can the search that uses those vectors enforce the per-project scope boundary from inside the query rather than after it?**

Two stories are stopped here. [#6](https://github.com/dependencyskills/dependencyskills/issues/6) needs two vectors per entry; [#7](https://github.com/dependencyskills/dependencyskills/issues/7) needs a local model to rewrite prose. Both were written saying "blocked on a JVM runtime", and neither can start until someone runs one.

The question is narrower than it looks, because the survey is already done. [RAD-0035](RAD-0035-a-small-local-model-for-the-prose-gap.md) compared the candidates, checked their licences, and picked. What is missing is not a decision but a **validation**: a recommendation nobody has executed is an assumption with a table attached.

## Trail

### What is already settled, and should not be re-litigated

RAD-0035 surveyed the JVM options and landed on **DJL** (Apache-2.0) as the general pick — inference-first, engine-agnostic over PyTorch and ONNX — with **Tribuo** (Apache-2.0) for the probe itself, since its ONNX wrappers compose with DJL. Deeplearning4j was flagged as historically the answer but thin on activity for years; Weka is GPL and ruled out; Smile's current licence needs checking before use.

It also disposed of the performance objection: **embedding is a batch job at harvest and a single short string at query time.** ONNX Runtime on CPU is adequate for both, and acceleration is an optimisation rather than a precondition. That matters because it removes GPU availability as a constraint on where the harvester can run.

And it established the reason this is not optional: [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) puts retrieval on Lucene, Lucene is JVM, and **a JVM embedding runtime is required whether or not any classifier is ever built.** The marginal cost of this probe is therefore small — the work is needed anyway.

### What is measured, and on what

Every retrieval number this project holds was produced in Python on Apple Silicon:

- [RAD-0019](RAD-0019-retrieval-at-scale.md) chose **BAAI BGE-M3** (MIT) on recall and licence, against multilingual-e5-large, all-MiniLM-L6-v2 and nomic-embed-text-v1.5. BGE-M3 separated from the field on the hardest paraphrastic case rather than merely edging it.
- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) produced the two-faced result — raw 13 of 17, rewrite 10 of 17, **both faces as two vectors 15 of 17**, fused into one vector 10 — using `mlx-community/bge-m3-mlx-fp16`.

`mlx-embeddings` is Apple-only. So the entire evidential basis for the index design sits on a runtime the product cannot ship, and the probe's first job is to find out whether it survives the move.

### The two things that could go wrong, and they are different

**The vectors could differ.** A different runtime, a different quantisation, a different tokenizer edge case — any of these can shift a vector enough to move a result. The failure mode is not a crash; it is the 15-of-17 quietly becoming 12-of-17 with nobody noticing, because the only way to see it is to re-run the eval. This is why the probe's success criterion is *reproducing a measurement*, not *loading a model*.

**Lucene may not filter a kNN query the way the design needs.** This is the part [#4's triage](https://github.com/dependencyskills/dependencyskills/issues/4) surfaced and it is the more dangerous of the two, because it is an architecture question wearing an API question's clothes.

The store is machine-wide ([ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md)) and a query must only see coordinates the asking project resolved. That restriction is a **containment boundary**, not a performance filter: an entry reachable from a project that never depended on it is the laundering route [RAD-0029](RAD-0029-the-agent-as-a-trust-launderer.md) describes, created by our own caching decision.

There are two ways to implement it and only one is correct:

| | |
|---|---|
| **Filter inside the kNN query** | The search considers only in-scope vectors. Correct. |
| **Fetch top-k, then drop out-of-scope results** | Wrong twice: the boundary becomes a post-filter a caller can forget, and a query whose top-k happens to be entirely out-of-scope returns *nothing* while in-scope matches sit below the cut. |

The second is what an implementer reaches for when the first turns out to be awkward, and it fails silently in exactly the case the boundary exists for. So whether Lucene supports a filtered kNN search — and whether it stays efficient when the filter is a set of several hundred coordinates rather than a handful — decides whether the vector index can hold the boundary at all, or whether scope has to stay in SQLite with the vectors serving only as a candidate generator.

### The question nobody has asked yet

**How does the model reach a developer's machine?** BGE-M3 is a file of some hundreds of megabytes. A Gradle plugin cannot reasonably carry it, and downloading it on first use makes the first build after adoption do something surprising and slow.

The ground is trodden — the Kotlin Multiplatform JS plugin downloads an entire Node runtime into `~/.gradle/nodejs`, which is precedent for both the mechanism and the location, and ADR-0012 already puts our store beside it. But the decision has not been made, and it interacts with [#7](https://github.com/dependencyskills/dependencyskills/issues/7)'s observation that everyone running this will hold a copy of whatever model does the work, so its behaviour is inspectable by anyone who wants to attack it.

This is named here rather than answered, because the probe will surface it whether or not it is planned for.

## Findings

**Measured.** All five questions the probe set itself, in the order it set them.

**1. A JVM process embeds text, on CPU, at 74 ms per entry.** DJL 0.36.0 over ONNX Runtime 1.21.1, loading the official `BAAI/bge-m3` ONNX export. No GPU, no Python.

**2. The vectors match.** Over 200 randomly sampled entries from the real harvested corpus, embedded on the JVM and compared to the stored MLX vector:

| | |
|---|---|
| mean cosine(MLX, ONNX) | **0.99999** |
| minimum | 0.99961 |
| below 0.99 | **0 of 200** |

fp16 against fp32 costs nothing measurable. The concern that motivated this probe — that the runtime move would quietly shift results — does not materialise.

**3. Retrieval is bit-identical.** ONNX query vectors against the MLX corpus, a mixed basis chosen so any systematic offset would show as lost recall:

| | r@1 | r@3 | r@5 | r@10 |
|---|---|---|---|---|
| MLX reference | 0/17 | 0/17 | 0/17 | 1/17 |
| ONNX on the JVM | 0/17 | 0/17 | 0/17 | 1/17 |

Those absolute numbers are poor and that is **[RAD-0019](RAD-0019-retrieval-at-scale.md)'s and test5's finding, not this one**: raw harvested documentation was already measured collapsing toward zero by 3,000 entries, and this corpus is 14,899. The question here was whether the runtime reproduces the measurement, and it reproduces it exactly — including how bad it is.

**4. Lucene holds two vectors per document**, queried independently, with no schema hack. This was [#6](https://github.com/dependencyskills/dependencyskills/issues/6)'s assumption and it holds.

**5. Lucene filters a kNN query by coordinate, from inside the search — and the post-filter really does fail.** Built deliberately so the globally nearest vectors all belong to coordinates the asking project does not have, over 15,000 documents and 600 coordinates:

| | in-scope hits |
|---|---|
| unfiltered kNN, k=10 | **0 of 10** |
| filtered kNN, k=10 | **10 of 10** |

A post-filter would have returned **nothing** with 250 in-scope entries present. This is no longer a reasoned warning; it is reproduced on Lucene 10.5.1.

Cost against filter size, mean of 20 queries:

| coordinates in filter | plain | with `--add-modules jdk.incubator.vector` |
|---|---|---|
| 50 | 995 µs | 882 µs |
| 200 | 1,490 µs | 1,079 µs |
| 500 | 2,962 µs | **1,603 µs** |

A 500-coordinate filter is a large real project, and 1.6 ms is comfortably interactive. **The Vector API is worth roughly 2× at that size**, and Lucene warns at startup when it is missing — so a plugin embedding this must set the flag.

### The pooling discrepancy, which nobody was looking for

The first run returned cosine **0.75** and zero recall. The cause was not the runtime, the precision, the tokenizer or the text: Java and Python produce byte-identical input strings, and MLX reproduces its own stored vectors at cosine 1.0.

The ONNX export exposes two outputs, `token_embeddings` and `sentence_embedding`. The pooled one is **CLS** — what BGE-M3's model card documents for dense retrieval. `mlx-embeddings` **mean-pools**. On identical text:

| pooling | cosine against the reference |
|---|---|
| CLS | 0.746 – 0.771 |
| mean | **1.00000** |

**So every retrieval number this project holds was measured with mean pooling, not the pooling the model documents.** Nothing measured is invalidated — the numbers are internally consistent and remain comparable to one another, and RAD-0019's encoder bake-off compared four encoders under the same treatment. What is *not* established is whether the documented pooling retrieves better, and that question did not exist until now.

## Recommendation

**Unblock [#6](https://github.com/dependencyskills/dependencyskills/issues/6).** Every precondition it was waiting on is measured and favourable. The runtime is DJL over ONNX Runtime, on CPU, and the vector index can hold the containment boundary itself.

**Write the scope filter into the kNN query, never over its results.** The failure is reproduced rather than argued: a post-filter returned zero results while 250 in-scope entries sat in the index. An empty result is indistinguishable from "your dependencies have nothing", so this fails silently and in the direction that looks like the tool not working.

**Set `--add-modules jdk.incubator.vector` wherever this runs.** Worth ~2× at a realistic filter size, and Lucene says so at startup.

**Measure CLS against mean pooling before shipping an encoder.** This is the one new question. It is cheap — re-embed the corpus one way and re-run an eval already written — and it is worth answering before a store fills with vectors produced under a pooling chosen by accident rather than by measurement.

**Quantisation is the next question after that, and it is a product question.** The fp32 export is **2.3 GB**. An int8 export is roughly 570 MB and untested here; a smaller encoder is smaller still, and RAD-0019 already measured all-MiniLM-L6-v2 at 4/8 against BGE-M3's 7/8. What quantisation costs in recall decides what actually ships, and it must be measured with one variable moving, not folded into this record.

**Still open, and unchanged by this probe:** how the model reaches a developer's machine. 2.3 GB is not a detail, and the answer interacts with quantisation above. The generative runtime [#7](https://github.com/dependencyskills/dependencyskills/issues/7) needs remains a different and larger problem; nothing here speaks to it.

## Connections

- [RAD-0035](RAD-0035-a-small-local-model-for-the-prose-gap.md) — the survey and the pick this probe validates
- [RAD-0019](RAD-0019-retrieval-at-scale.md) — the encoder bake-off and why BGE-M3
- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) — the two-faced measurement the probe must reproduce
- [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) — Lucene as the derived index
- [RAD-0029](RAD-0029-the-agent-as-a-trust-launderer.md) — why the scope filter is a containment boundary
- [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) — the machine-wide store that makes scope load-bearing
