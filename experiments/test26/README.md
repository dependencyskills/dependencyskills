# test26 — can the in-process llama.cpp library replace DJL/ONNX as the encoder?

**Question:** the project built its own llama.cpp binding for the generative side. llama.cpp also embeds. Is the ONNX runtime still needed?

[RAD-0047](../../docs/knowledge/research/RAD-0047-a-jvm-embedding-runtime.md) settled that a JVM process can embed text well enough to reproduce this project's retrieval, using **DJL over ONNX Runtime**. That answer is sound and this does not revisit it. What changed is the other side of the ledger: after [RAD-0051](../../docs/knowledge/research/RAD-0051-a-jvm-generative-runtime.md), `libdscodex` already exists, already ships per-platform, and already runs inside the same process. If it can also encode, the ONNX runtime is a second native dependency earning nothing.

```
java --enable-native-access=ALL-UNNAMED -cp "../test23/lib/*" EncoderProbe.java [N] [--recall]
```

The reference is the **shipped** path, not MLX: `BAAI/bge-small-en-v1.5` ONNX, mean-pooled, at the digests pinned in [`encoder/build.gradle.kts`](../../implementations/codex/encoder/build.gradle.kts). The question is whether llama.cpp can replace *that*.

**Measured against:** llama.cpp `b19cbe9` built as `libdscodex` (static, `GGML_NATIVE=OFF`), DJL 0.36.0 over ONNX Runtime 1.21.1, OpenJDK 26, macOS arm64 CPU; `BAAI/bge-small-en-v1.5` official ONNX export (fp32, 134 MB) against the same weights converted to F16 GGUF (67 MB); corpus `test5/corpus.json`, 14,899 entries; 2026-08-28.

## The narrow risk was the tokenizer

Everything else about this swap is mechanical. The one thing that could go wrong quietly is that **llama.cpp implements WordPiece itself from the GGUF vocab** rather than reading `tokenizer.json`. A divergence there does not fail — it returns a vector of the right shape in a slightly wrong basis, and only re-running an eval would show it. That is the failure RAD-0047 was written to catch, and it is the only reason this probe exists rather than a patch.

## 1. The vectors agree

Over 300 randomly sampled entries, the same text through both runtimes:

| | |
|---|---|
| mean cosine(ONNX, llama.cpp) | **0.9999986** |
| minimum | **0.9999954** |
| below 0.99 | **0 of 300** |
| ONNX | 7.3 ms per embedding |
| llama.cpp | 11.1 ms per embedding |

Printed to seven places deliberately. F16 weights against fp32 should **not** agree exactly, and a cosine rendered as `1.00000` is indistinguishable from a probe accidentally comparing a buffer with itself. The residual — about 1.4 × 10⁻⁶ — is the evidence that two runtimes actually ran.

**The tokenizer reproduces.** For this model, WordPiece from the GGUF vocab and the HuggingFace tokenizer agree closely enough that no entry moves.

llama.cpp is **1.5× slower per embedding** here. Both are far inside what harvest needs (a batch job) and what query needs (one short string), and no attempt was made to tune either.

## 2. Pooling is an argument, and the default would have been wrong

`_try_set_pooling_type()` reads the model's own `1_Pooling/config.json` and writes the result into GGUF metadata. For `bge-small-en-v1.5` that is **CLS** — what the model card documents.

[RAD-0048](../../docs/knowledge/research/RAD-0048-where-the-encoder-size-cutoff-is.md) measured **mean** as the better pooling for this model, and the encoder jar declares `Encoder-Pooling: mean` in its manifest. So the converted model arrives configured the way this project measured to be wrong.

What that would have cost, had the default been taken:

| | mean cosine against the reference |
|---|---|
| llama.cpp, MEAN (asked for) | **0.9999986** |
| llama.cpp, CLS (what the GGUF declares) | **0.93030** |

`dsc_encoder_load` therefore takes pooling as a **required argument** and refuses `UNSPECIFIED` rather than falling back to the metadata. Three properties, in the probe's own output:

```
0. pooling is an argument, not a default
   GGUF declares      CLS (the model card's pooling)
   asked for          MEAN
   in effect          MEAN   dim 384
   UNSPECIFIED        refused, as designed
```

This is the same trap RAD-0047 hit — its first run returned cosine 0.75 and zero recall — and llama.cpp handles it better than ONNX does. Under the ONNX export, pooling is implicit in **which output tensor you read**; nothing names it, nothing warns, and nothing reads back. Under llama.cpp it is a named enum the caller must pass, the library logs a warning when the caller contradicts the model card, and `llama_pooling_type(ctx)` returns what actually took effect. The failure mode moves from silent to announced.

### A second way the metadata can be absent entirely

The first conversion here wrote **no pooling field at all**, because `_try_set_pooling_type()` locates the pooling config through `modules.json` and that file had not been downloaded. With no metadata, llama.cpp falls back to `POOLING_TYPE_NONE` — no pooling — and `llama_get_embeddings_seq` returns null.

So a conversion from a partial download produces a model that is not wrong so much as unusable, and it looks like a runtime bug. Another argument for never taking the default.

## 3. Retrieval

Both runtimes embedded all 14,899 entries; the 17 needs from `test5/queries.json` were then scored three ways.

|  | r@1 | r@3 | r@5 | r@10 |
|---|---|---|---|---|
| ONNX queries vs ONNX corpus | 1/17 | 1/17 | 1/17 | 1/17 |
| llama.cpp queries vs llama.cpp corpus | 1/17 | 1/17 | 1/17 | 1/17 |
| llama.cpp queries vs ONNX corpus (mixed basis) | 1/17 | 1/17 | 1/17 | 1/17 |

**Those absolute numbers are bad, and they are not this probe's finding.** Raw harvested documentation was already measured collapsing toward zero by 3,000 entries ([RAD-0019](../../docs/knowledge/research/RAD-0019-retrieval-at-scale.md), `test5`) and this corpus is 14,899. RAD-0047 reported the same shape for BGE-M3. The two-faced index (#6) and the summariser (#7) are what address it; a runtime swap cannot.

**And identical recall is a weak instrument here.** When only one query in seventeen lands at all, a disagreement between the runtimes has almost nothing to destroy. The three rows agreeing is consistent with the runtimes matching, but it would also be consistent with a great deal of damage.

## 4. So the real test is whether they rank the corpus the same way

Over 14,899 candidates, does swapping the runtime change what comes back?

| | |
|---|---|
| mean top-10 agreement | **9.8 / 10** |
| mean top-100 agreement | **99.7 / 100** |
| queries with a perfect top-10 | 14 of 17 |

And the gold entry's rank under each runtime, for the needs where it sits deep in the corpus:

| need (truncated) | rank, ONNX | rank, llama.cpp |
|---|---|---|
| the page has moved somewhere else permanently | 1 | 1 |
| I have a folder of assets on disk | 24 | 24 |
| two coroutines keep touching the same | 35 | 35 |
| start a second piece of work at the same | 133 | 132 |
| I want the same set of headers attached | 539 | 543 |
| if the far end never answers, stop waiting | 1,715 | 1,713 |
| one part of my program produces items | 4,289 | 4,297 |

A gold entry sitting at rank 4,289 under one runtime and 4,297 under the other is the sharpest form of the result: **8 positions out of 14,899**. The runtimes are not merely agreeing on the easy cases; they order the whole corpus the same way, and the small disagreements are ties reshuffling at the sixth decimal place.

This is the measurement the recall table could not make. It is also cheap, and it is the one to re-run if the encoder or its quantisation ever changes.

## Verdict

**llama.cpp reproduces the shipped encoder.** Cosine 0.9999986, top-100 ranking agreement 99.7%, no entry moved. The tokenizer concern — the only narrow risk — does not materialise for this model.

What that buys, if the swap is taken:

| | DJL + ONNX Runtime | llama.cpp |
|---|---|---|
| jars on the classpath | ~160 MB (`onnxruntime` 137 MB, `tokenizers` 19 MB, `api`, `gson`, `jna`) | none — the native is already shipped for #7 |
| native dependencies | a second one, per platform | the one that already exists |
| model file | 134 MB fp32 ONNX | **67 MB** F16 GGUF |
| ms per embedding | 7.3 | 11.1 |
| pooling | implicit in which output tensor you read | a required argument, warned on, readable back |

The cost is 1.5× per embedding, against a batch job at harvest and one short string at query.

## What this does not say

- **Only one model, on one platform.** `bge-small-en-v1.5` is BERT/WordPiece. BGE-M3 is XLM-RoBERTa/SentencePiece and llama.cpp tokenizes it by a different path entirely; nothing here transfers to it. Measured on macOS arm64 only.
- **Nothing about quantisation below F16.** RAD-0048 found int8 collapsing under CLS pooling, so Q8/Q4 is a separate measurement with one variable moving.
- **Nothing about Lucene.** RAD-0047's phase A stands unchanged — two vectors per document, filtered kNN inside the query. This swaps what produces the vectors, not what stores them.
- **Nothing about retrieval quality.** Both runtimes retrieve equally badly on raw documentation, which is a known prior result and the reason #6 and #7 exist.
