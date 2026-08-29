# One Runtime for Both Faces of the Index

RAD-0054 · 2026-08-28
Keywords: do we still need DJL now that llama.cpp is in process; can llama.cpp embed; does the GGUF tokenizer match the HuggingFace one; bge-small-en-v1.5 as GGUF; llama_pooling_type as an argument; dropping ONNX Runtime; how many native dependencies does the plugin need; is the ONNX runtime still earning its place.
Measured against: llama.cpp b19cbe9 built as `libdscodex` (static, `GGML_NATIVE=OFF`), DJL 0.36.0 over ONNX Runtime 1.21.1, OpenJDK 26, macOS arm64 CPU; `BAAI/bge-small-en-v1.5` official ONNX export (fp32) against the same weights as F16 GGUF; `experiments/test26`, 14,899 entries, 2026-08-28.

## Question

> **The project now has its own in-process llama.cpp binding. llama.cpp also embeds. Is the ONNX runtime still needed?**

[RAD-0047](RAD-0047-a-jvm-embedding-runtime.md) established that a JVM process can embed text well enough to reproduce this project's retrieval, using **DJL over ONNX Runtime**. That answer was correct and this does not overturn it. What changed is the other side of the ledger. [RAD-0051](RAD-0051-a-jvm-generative-runtime.md) found both published JVM llama.cpp bindings frozen and led to building one, so `libdscodex` now exists, ships per platform, and runs inside the same process. If it can also encode, the ONNX runtime is a **second** native dependency earning nothing that the first does not already provide.

The question is therefore about subtraction, not capability. Nothing was wrong with DJL.

## Trail

### What llama.cpp already has

Read from `llama.h` and `src/` at b19cbe9 before any code was written:

| needed | present |
|---|---|
| BERT architecture | `LLM_ARCH_BERT` — `bge-small-en-v1.5` is BERT |
| mean pooling | `LLAMA_POOLING_TYPE_MEAN`, the pooling [RAD-0048](RAD-0048-where-the-encoder-size-cutoff-is.md) pinned |
| encoder path | `llama_encode`, `llama_set_embeddings`, `llama_get_embeddings_seq` |
| conversion | `conversion/bert.py`, registered for `BertModel` |

The shim gained four functions — `dsc_encoder_load`, `dsc_encoder_pooling`, `dsc_encoder_dim`, `dsc_embed` — and they are smaller than the generative path that already worked.

### The one thing that could go wrong quietly

Everything about this swap is mechanical except the **tokenizer**. llama.cpp implements WordPiece itself from the GGUF vocab rather than reading `tokenizer.json`. A divergence there does not fail: it returns a vector of the right shape in a slightly wrong basis, and only re-running an eval would show it.

That is precisely the failure RAD-0047 was written to catch, and it is why this is a probe rather than a patch.

### The pooling default was wrong, exactly as expected, and the tooling is better for it

`_try_set_pooling_type()` reads the model's own `1_Pooling/config.json` and writes the result into GGUF metadata. For `bge-small-en-v1.5` that is **CLS** — what the model card documents. RAD-0048 measured **mean** as better for this project's use, and the encoder jar already declares `Encoder-Pooling: mean` in its manifest.

So the converted model arrives configured the way this project measured to be wrong. Under the ONNX export the equivalent choice is implicit in **which output tensor you read**; nothing names it, nothing warns, and nothing reads back — which is how RAD-0047's first run returned cosine 0.75 and zero recall, and looked like a runtime bug for a while.

llama.cpp handles the same trap better on all three counts. An explicit `params.pooling_type` always wins over the metadata (`llama-context.cpp:129`; the metadata only fills an `UNSPECIFIED`); the library logs a warning naming both values when the caller contradicts the model card (`:3708`); and `llama_pooling_type(ctx)` returns what actually took effect (`:3771`). So `dsc_encoder_load` takes pooling as a **required argument** and refuses `UNSPECIFIED` rather than defaulting. The failure mode moves from silent to announced.

A second way the metadata misleads turned up during the run: the first conversion wrote **no pooling field at all**, because `_try_set_pooling_type()` locates the config through `modules.json` and that file had not been downloaded. With no metadata llama.cpp falls back to `POOLING_TYPE_NONE` and `llama_get_embeddings_seq` returns null. A conversion from a partial download therefore produces a model that is unusable rather than wrong — which is the better of the two failures, and still an argument for never taking the default.

### Why recall was the wrong instrument, and what replaced it

The obvious test — embed the corpus with both runtimes and compare recall@k — came back identical across all three arms, including a deliberately mixed basis. It is also nearly worthless as evidence.

Raw harvested documentation was already measured collapsing toward zero by 3,000 entries ([RAD-0019](RAD-0019-retrieval-at-scale.md)), and this corpus is 14,899. Only **1 of 17** needs lands anywhere at all. Three identical rows are consistent with the runtimes matching; they are equally consistent with a great deal of damage, because a disagreement has almost nothing left to destroy.

The sharper question is whether the two runtimes **order the corpus the same way**, which does not depend on any of them being right. That is the measurement that carries the finding, and it was added after the first one came back uninformative.

## Findings

**Measured**, over 14,899 entries and 17 needs, on macOS arm64 CPU.

**1. The vectors agree, and the tokenizer reproduces.** Over 300 randomly sampled entries:

| | |
|---|---|
| mean cosine(ONNX, llama.cpp) | **0.9999986** |
| minimum | **0.9999954** |
| below 0.99 | **0 of 300** |

Reported to seven places on purpose: F16 weights against fp32 should *not* agree exactly, and a cosine printed as `1.00000` is indistinguishable from a probe comparing a buffer with itself. The residual of about 1.4 × 10⁻⁶ is the evidence that two runtimes actually ran.

**2. They rank the whole corpus the same way.**

| | |
|---|---|
| mean top-10 agreement | **9.8 / 10** |
| mean top-100 agreement | **99.7 / 100** |
| queries with a perfect top-10 | 14 of 17 |

The sharpest single case: a gold entry at rank **4,289** under ONNX and **4,297** under llama.cpp — eight positions out of 14,899. The runtimes are not agreeing only on the easy cases; the residual disagreement is ties reshuffling at the sixth decimal place.

**3. The pooling default would have cost most of the signal.**

| | mean cosine against the reference |
|---|---|
| llama.cpp, MEAN (asked for) | 0.9999986 |
| llama.cpp, CLS (what the GGUF declares) | **0.93030** |

**4. What the swap subtracts.**

| | DJL + ONNX Runtime | llama.cpp |
|---|---|---|
| jars on the classpath | ~160 MB (`onnxruntime` 137 MB, `tokenizers` 19 MB, plus `api`, `gson`, `jna`) | none — the native already ships for #7 |
| native dependencies | a second one, per platform | the one that already exists |
| model file | 134 MB fp32 ONNX | **67 MB** F16 GGUF |
| ms per embedding | **7.3** | 11.1 |
| pooling | implicit in which output tensor you read | a required argument, warned on, readable back |

**Assumed, not measured.** That 1.5× on embedding does not matter. Embedding is a batch job at harvest and one short string at query, which is RAD-0035's reasoning and is unchanged; but no throughput measurement was taken at harvest scale under either runtime.

## Recommendation

**Take the swap, for the shipped encoder only.** `bge-small-en-v1.5` through `libdscodex` reproduces the ONNX path closely enough that no entry moves, and it avoids a second native dependency and ~160 MB of jars, and takes 20 MB off the published encoder artifact. One library then serves both faces of the index — the encoder for [#6](https://github.com/dependencyskills/dependencyskills/issues/6) and the generator for [#7](https://github.com/dependencyskills/dependencyskills/issues/7).

**Keep pooling a required argument, wherever this is wrapped.** The measured cost of the default is 0.930 against 0.99999. #6's acceptance criteria already ask that pooling be recorded per entry and that vectors under different pooling never mix; `dsc_encoder_pooling` makes that assertable rather than remembered, and the encoder jar's `Encoder-Pooling` manifest attribute is the value to assert against.

**Re-run `test26` phase 4 whenever the encoder or its quantisation changes.** Top-100 ranking agreement is the sensitive instrument here; recall@k on this corpus is not, and reading it as one is how a runtime change would pass unnoticed.

**Do not generalise this to BGE-M3.** It is XLM-RoBERTa over SentencePiece, which llama.cpp tokenizes by an entirely different path. Nothing here transfers.

**Quantisation below F16 is the next question and is separate.** RAD-0048 found int8 collapsing under CLS pooling, so Q8 and Q4 need measuring with one variable moving. A 67 MB encoder is already small enough that the distribution question RAD-0047 left open looks different — and [RAD-0052](RAD-0052-distributing-a-precomputed-codex.md) may remove it from the common case entirely.

**What would change the answer:** a second encoder that tokenizes differently; a platform where the GGUF WordPiece implementation diverges; or a harvest-scale throughput measurement showing the 1.5× is not free after all.

## Connections

- [RAD-0047](RAD-0047-a-jvm-embedding-runtime.md) — the probe this narrows; its Lucene findings stand unchanged
- [RAD-0051](RAD-0051-a-jvm-generative-runtime.md) — why `libdscodex` exists at all
- [RAD-0048](RAD-0048-where-the-encoder-size-cutoff-is.md) — why the pooling is mean and not the documented CLS
- [RAD-0035](RAD-0035-a-small-local-model-for-the-prose-gap.md) — the survey that picked DJL, and the reasoning about batch-versus-query cost that still holds
- [RAD-0019](RAD-0019-retrieval-at-scale.md) — why raw documentation retrieves badly at this corpus size, which is not this probe's finding
