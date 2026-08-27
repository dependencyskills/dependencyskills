# A JVM Embedding Runtime

RAD-0047 · 2026-08-27 · v1
Keywords: how does a JVM process embed text; running BGE-M3 without MLX; DJL versus Tribuo versus ONNX Runtime; do JVM embeddings match the Python ones; does Lucene support a filtered kNN query; scope filter inside the search rather than after it; how does the model file ship; what blocks the two-faced index.

Not yet measured. This record specifies a probe and states what it must establish; the Findings section is empty until it runs.

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

**None yet.** This record specifies the probe; it will be revised with what the run establishes.

The probe should establish, in this order, stopping at the first failure:

1. **A JVM process loads BGE-M3 and produces embeddings.** Via DJL over ONNX Runtime, on CPU. Record the model artifact used, since a converted or quantised ONNX export is not the same object as the MLX one.
2. **The vectors are good enough.** Re-run RAD-0040's eval — the same 220 entries, the same 17 queries, both faces — on the JVM and compare against 15 of 17. The number to beat is the number already measured, not an absolute.
3. **Lucene holds two vector fields per document** without a schema hack, which is what [#6](https://github.com/dependencyskills/dependencyskills/issues/6) assumes.
4. **Lucene filters a kNN query by a set of coordinates**, and stays usable when that set is several hundred. If it cannot, say so plainly: the design changes rather than the criterion relaxing.
5. **Latency at query time on CPU**, for one short string, on ordinary developer hardware. Written down, because it decides whether this sits behind an interactive call.

Throughput at harvest is deliberately **not** on that list. RAD-0035 already argued it is a batch job, and a probe that optimises before it validates has answered the wrong question.

## Recommendation

**Run the probe before either blocked story is scheduled**, and run it as an experiment in `experiments/`, not as product code. Its output is this record's Findings section plus a decision about the runtime; nothing it builds should be lifted into `implementations/`.

**Do not treat RAD-0035's pick as settled until it runs.** DJL is a recommendation from a survey, and this project has already had to withdraw claims made by reasoning from specifications rather than measurement — [RAD-0027](RAD-0027-the-identifier-as-a-free-text-channel.md) was opened for exactly that reason.

**If the vectors do not reproduce**, the honest options are to re-run the encoder bake-off on the JVM rather than assume BGE-M3 is still the pick, or to accept a different encoder with its own measured number. What must not happen is carrying the Python number forward against a runtime that did not produce it.

**If Lucene cannot filter kNN by a large coordinate set**, the fallback is to keep scope enforcement in SQLite and use the vector index as a candidate generator only — with the filter applied to the candidate *set*, never to the final results. That is a worse design and should be recorded as such if it is taken.

Open, and deliberately left standing: how the model ships, and whether the generative runtime [#7](https://github.com/dependencyskills/dependencyskills/issues/7) needs is the same problem or a different one. They are related but not identical — one is an encoder, the other a text generator with a much larger footprint — and treating them as one question is how the smaller one gets held up by the larger.

## Connections

- [RAD-0035](RAD-0035-a-small-local-model-for-the-prose-gap.md) — the survey and the pick this probe validates
- [RAD-0019](RAD-0019-retrieval-at-scale.md) — the encoder bake-off and why BGE-M3
- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) — the two-faced measurement the probe must reproduce
- [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) — Lucene as the derived index
- [RAD-0029](RAD-0029-the-agent-as-a-trust-launderer.md) — why the scope filter is a containment boundary
- [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) — the machine-wide store that makes scope load-bearing
