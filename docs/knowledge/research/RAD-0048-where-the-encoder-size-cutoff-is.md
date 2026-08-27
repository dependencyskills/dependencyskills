# Where the Encoder Size Cutoff Is

RAD-0048 · 2026-08-27 · v2
Keywords: how big does the embedding model have to be; can we ship something smaller than 2.3 GB; bge-small versus bge-base versus bge-m3; what does int8 quantization cost in recall; does fp16 lose anything; mean pooling versus CLS pooling; is a multilingual encoder worth it for English code documentation; what model ships with the plugin.

**v2 (2026-08-27) — the sweep ran.** Three results, in descending order of how much they change: the pooling nobody chose was costing **2 of 17 at rank 1**; **fp16 is free**; and at realistic corpus size a **33 MB** encoder is not worse than the **2,267 MB** one.

Measured against: DJL 0.36.0 over ONNX Runtime 1.21.1, OpenJDK 26.0.2.1, macOS arm64 CPU; `experiments/test24`; test5's 220-entry `subset(seed=11)` and its full 14,899-entry corpus, 17 real queries; 2026-08-27.

## Question

> **How small can the encoder be before retrieval degrades — and does the pooling nobody chose deliberately cost anything?**

[RAD-0047](RAD-0047-a-jvm-embedding-runtime.md) established that a JVM process reproduces this project's embeddings exactly, and in doing so put a number on the artifact: the BGE-M3 ONNX export is **2,267 MB**. That is what would have to reach every developer's machine.

It is very likely far more than the job needs, and the evidence that it is necessary turns out to be thin.

## Trail

### The evidence for a large encoder is one pilot against a straw man

[RAD-0019](RAD-0019-retrieval-at-scale.md) chose BGE-M3 on a bake-off: *"MiniLM-4bit 4/8, e5-large 5/8, nomic 6/8, BGE-M3 7/8"*. That record is careful to call it what it is — **N=58, eight queries, a pilot** — and its conclusion, that a genuinely strong encoder separates from weak ones on paraphrastic queries, is sound on its own terms.

But it does not establish that a *small* encoder is inadequate, because the small candidate was `MiniLM-4bit`: a 2021-era 22M-parameter model **and** 4-bit quantized. Two handicaps at once, and neither is inherent to being small. No modern small English retriever was in the comparison — `bge-small-en-v1.5` is the same family and the same training recipe as the winner, and was never tried.

### We are paying for capacity the task does not use

BGE-M3 is 568M parameters, **100+ languages**, an 8192-token context, and produces dense, sparse and ColBERT representations. This project indexes **English** code documentation, truncated to **900 characters** — roughly 200 tokens — and uses **dense only**.

Multilingual capacity and long-context capacity are most of the weight, and both are dead here.

### The project's own numbers argue the encoder is not the bottleneck

At 14,899 real entries, BGE-M3 scores **0/17 at rank 1** and 1/17 at rank 10. Whatever is failing is not encoder capacity — it is that raw harvested doc text is a poor retrieval key at scale, which is the finding that motivates the rewritten face in the first place ([RAD-0040](RAD-0040-does-summarising-improve-retrieval.md)).

Paying 2.3 GB for an encoder that scores zero is paying for the wrong thing. The interesting question is where the *cutoff* is, not whether the largest available model is the best.

### The size ladder, measured from the published artifacts

| model | ONNX size | relative |
|---|---|---|
| BGE-M3 fp32 — the reference | 2,267 MB | 1× |
| bge-base-en-v1.5 fp32 | 436 MB | 5× smaller |
| bge-small-en-v1.5 fp32 | 133 MB | 17× smaller |
| bge-small-en-v1.5 fp16 | 67 MB | 34× smaller |
| bge-small-en-v1.5 int8 | 34 MB | **67× smaller** |

All are BAAI's own family; the quantized variants are off-the-shelf exports. Sizes checked 2026-08-27.

### Pooling rides along for free

RAD-0047 found that every retrieval number this project holds was produced with **mean pooling**, because that is what `mlx-embeddings` does — while BGE-M3's card documents **CLS** for dense retrieval. Nobody chose mean; it was inherited.

Both poolings come out of a single forward pass, so measuring them costs nothing extra and this sweep is a full factorial rather than a confound. That closes the question raised as [#10](https://github.com/dependencyskills/dependencyskills/issues/10).

### Where to measure, and why not on the full corpus

At 14,899 entries the raw-doc baseline is 0–1 of 17. An encoder comparison scored there **measures nothing, because there is no recall left to lose** — every candidate would return zero and the sweep would report a tie.

So the discriminating corpus is `test5`'s deterministic 220-entry subset — deduplicated by symbol, every query target retained, `seed=11` — the same construction as test5's size sweep, so the numbers line up with that table. The full corpus is run as well, but as a **control**: it should stay near zero for every candidate, which is the evidence that the ceiling is the retrieval key rather than the model.

## Findings

**Measured — the sweep, at 220 entries.** Five models, both poolings, 17 queries.

| model | size | pool | r@1 | r@3 | r@5 | r@10 | ms/emb |
|---|---|---|---|---|---|---|---|
| bge-m3 | 2,267 MB | mean | 5/17 | 6/17 | 8/17 | 13/17 | 75.9 |
| | | **CLS** | **7/17** | **9/17** | **11/17** | 13/17 | |
| bge-base-en-v1.5 | 418 MB | mean | 5/17 | 7/17 | 9/17 | 12/17 | 25.0 |
| | | CLS | 5/17 | 7/17 | 8/17 | 11/17 | |
| bge-small-en-v1.5 | 136 MB | mean | 4/17 | 7/17 | 8/17 | 10/17 | 8.7 |
| bge-small fp16 | 64 MB | mean | 4/17 | 7/17 | 8/17 | 10/17 | 18.1 |
| bge-small int8 | 33 MB | mean | 5/17 | 7/17 | 7/17 | 9/17 | 4.5 |

**1. The pooling was costing us, and it is the largest single result here.** BGE-M3 with **CLS** — the pooling its own model card documents — scores **7/17 at r@1 against mean's 5/17**, and 11/17 against 8/17 at r@5. Every retrieval number this project holds was produced with mean pooling, so **every one of them understated the encoder**. This closes the question [RAD-0047](RAD-0047-a-jvm-embedding-runtime.md) raised.

It does **not** generalise: CLS is better for bge-m3, level for bge-base, and clearly worse for int8 (2/17 against 5/17 at r@1). **Pooling is a per-model property and must be measured per model**, not set globally.

**2. fp16 is free.** bge-small at fp32 and fp16 score **identically at every k** — 4/7/8/10. Half the bytes, no measurable loss. It is not faster, though: 18.1 ms against fp32's 8.7 ms, because the CPU has no native fp16 kernel and upcasts.

**3. Size buys something at 220 entries, and nothing at 14,899.** The ladder degrades gently — 13/17 → 12/17 → 10/17 → 9/17 at r@10 across a 67× size range. But the control inverts it:

| corpus | bge-m3 (2,267 MB) | bge-small int8 (33 MB) |
|---|---|---|
| 220 entries | r@10 **13/17** | r@10 9/17 |
| **14,899 entries** | r@1 0/17 · r@10 **1/17** | r@1 0/17 · r@10 **2/17** |

At realistic scale the 33 MB model is **marginally ahead of the 2.3 GB one**. Both are effectively zero, and that is the finding: **encoder capacity is not what is failing.** The retrieval key is — raw harvested doc text, exactly as [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) concluded. Spending 2.3 GB to score zero is spending it in the wrong place.

**4. Small is also fast.** 4.5 ms against 75.9 ms per embedding, and the full 14,899-entry corpus embedded in **67 seconds** against roughly 19 minutes. That changes what a first harvest costs on a developer's machine, not just what it downloads.

**Unmeasured, and stated so it is not over-read:** n = 17 queries. A difference of one or two at any k is noise. The 13-against-10 gap at 220 entries is suggestive rather than established, and the int8 column moving in both directions at once (better at r@1, worse at r@10) is what noise looks like at this n. What survives that caveat is the pooling result, the fp16 result, and the full-corpus inversion — all of which are either large or directionally consistent.

## Recommendation

**Make the encoder configurable, and ship a small one by default.** The multilingual capacity in BGE-M3 is not dead weight universally — a developer indexing libraries documented in Chinese or Japanese genuinely needs it — it is dead weight for the *default* case of English code documentation. A configuration point turns a 2.3 GB imposition into an opt-in for the people it serves.

**The default should be `bge-small-en-v1.5` at fp16, 64 MB.** fp16 costs nothing measurable against fp32 and halves the artifact. int8 at 33 MB is defensible and slightly faster, but its numbers move in both directions and it is the one candidate where CLS pooling collapses — hold it as an option rather than a default until there is more than 17 queries behind it.

**Record the pooling per model, and pin it with the encoder.** CLS is right for bge-m3 and wrong for int8. Since [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) already requires an entry to record what produced it, the pooling belongs in that same provenance — a store whose vectors were built under one pooling cannot be mixed with another.

**Re-run the affected numbers with CLS before treating them as the ceiling.** Every published retrieval figure used mean pooling and therefore understates BGE-M3. This does not invalidate any comparison — all four encoders in RAD-0019's bake-off got the same treatment — but the absolute numbers are low by roughly 2 of 17 at r@1, and [RAD-0019](RAD-0019-retrieval-at-scale.md)'s encoder ranking may not survive being redone per-model.

**Do not read this as "the encoder does not matter."** It matters at 220 entries. What the control establishes is narrower and more useful: at the scale a real project actually has, **no encoder in this range rescues raw doc text**, so the next gain is in the retrieval key rather than the model. That is an argument for the rewritten face, not against embeddings.

## Connections

- [RAD-0047](RAD-0047-a-jvm-embedding-runtime.md) — the JVM probe that put a number on the artifact and found the pooling discrepancy
- [RAD-0019](RAD-0019-retrieval-at-scale.md) — the original bake-off, its pilot size, and why BGE-M3 was picked
- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) — why raw doc text is a weak retrieval key at scale
- [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) — the index this encoder feeds
