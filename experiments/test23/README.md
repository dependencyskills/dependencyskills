# test23 — a JVM embedding runtime

**Question:** can a JVM process embed text well enough to reproduce the retrieval this project has already measured, and can the search that uses those vectors enforce the per-project scope boundary from inside the query rather than after it?

Specified by [RAD-0047](../../docs/knowledge/research/RAD-0047-a-jvm-embedding-runtime.md). This is the probe that blocks [#6](https://github.com/dependencyskills/dependencyskills/issues/6).

Everything this project has measured about retrieval came from `mlx-embeddings` — Python, Apple Silicon. The product is a JVM plugin. Nobody had checked the move.

```
java --add-modules jdk.incubator.vector --enable-native-access=ALL-UNNAMED \
     -cp "lib/*" LuceneProbe.java          # phase A - no model needed
java -cp "lib/*" EmbedProbe.java [N]       # phase B - needs model/, N = sample size
```

## Phase A — Lucene: two vectors, and a filtered kNN

**Measured against:** Lucene 10.5.1, OpenJDK 26.0.2.1, macOS arm64, 2026-08-27. 15,000 synthetic documents, 600 coordinates, 1024-dim vectors (BGE-M3's width), cosine similarity.

The corpus is built so the globally nearest vectors all belong to coordinates the asking project does **not** have, with in-scope matches sitting below them. That is the case a post-filter gets wrong.

| | result |
|---|---|
| two `KnnFloatVectorField` per document, queried independently | **works, no schema hack** |
| unfiltered kNN, k=10 — in-scope hits | **0 of 10** |
| filtered kNN, k=10, filter = 10 coordinates | **10 of 10 in scope** |

**A post-filter would have returned nothing**, with 250 in-scope entries in the index. This is not a performance difference; it is the difference between a working query and an empty one, and the empty one looks exactly like "your dependencies have no matches".

Filter cost against filter size, mean of 20 queries:

| coordinates in filter | without Vector API | with `--add-modules jdk.incubator.vector` |
|---|---|---|
| 50 | 995 µs | 882 µs |
| 200 | 1,490 µs | 1,079 µs |
| 500 | 2,962 µs | 1,603 µs |

A 500-coordinate filter is a large real project. **1.6 ms is comfortably interactive**, and the Vector API is worth roughly 2× at that size — Lucene warns when it is missing, and a plugin that embeds this needs the JVM flag set.

## Phase B — DJL over ONNX Runtime: do the vectors match?

Reference is `experiments/test5/corpus-vecs.json`: 14,899 entries embedded by `mlx-community/bge-m3-mlx-fp16`, 900-char truncation, the text built by `test5/embed_corpus.py`. This runs the **official `BAAI/bge-m3` ONNX export** through DJL on CPU, reproducing that text construction exactly, and compares.

Two tests:

1. **Vector agreement** — `cosine(MLX vector, ONNX vector)` per entry, over a random sample.
2. **Recall in a mixed basis** — ONNX **query** vectors scored against the **MLX corpus**. Stronger than it sounds: if the two runtimes disagree systematically, a mixed basis loses recall rather than hiding the disagreement behind a matching offset.

The reference is fp16 on MLX against fp32 on ONNX, so exact equality is not expected and is not the bar. The bar is whether retrieval survives.

**Measured against:** DJL 0.36.0 over ONNX Runtime 1.21.1, OpenJDK 26.0.2.1, macOS arm64 CPU, `BAAI/bge-m3` official ONNX export (fp32, 2.3 GB); reference `mlx-community/bge-m3-mlx-fp16`; 2026-08-27.

### The pooling trap, found the hard way

The first run returned **cosine 0.75** against the reference and **zero** recall. The cause was not fp16-vs-fp32, the tokenizer, or the text — Java and Python produce byte-identical input strings, and MLX reproduces its own stored vectors at cosine 1.0.

The ONNX export has **two outputs**: `token_embeddings` and `sentence_embedding`. The pooled output is **CLS**, which is what BGE-M3's model card documents for dense retrieval. `mlx-embeddings` **mean-pools**. On identical text:

| pooling | cosine against the reference |
|---|---|
| CLS (`sentence_embedding`) | 0.746 – 0.771 |
| mean-pooled | **1.00000** |

**Every retrieval number this project holds was measured with mean pooling, not the pooling BGE-M3 documents.** The measurements are internally consistent and remain comparable to each other; what is not established is whether CLS retrieves *better*. That is a separate question and cheap to answer now.

### 1. Do the vectors match?

Over 200 randomly sampled entries, embedding each on the JVM and comparing to the stored MLX vector:

| | |
|---|---|
| mean cosine(MLX, ONNX) | **0.99999** |
| minimum | 0.99961 |
| below 0.99 | **0 of 200** |
| CPU cost | 74 ms per embedding |

fp16 against fp32 costs nothing measurable.

### 2. Does retrieval survive?

ONNX **query** vectors scored against the **MLX corpus** — a mixed basis, so any systematic offset shows up as lost recall:

| | r@1 | r@3 | r@5 | r@10 |
|---|---|---|---|---|
| MLX reference (`test5/eval_recall.py`) | 0/17 | 0/17 | 0/17 | 1/17 |
| **ONNX on the JVM** | **0/17** | **0/17** | **0/17** | **1/17** |

**Identical.** The port reproduces the reference exactly.

The absolute numbers are poor, and that is **test5's finding, not a JVM one**: raw harvested doc text was already measured collapsing toward zero by 3,000 entries, and this corpus is 14,899. The probe asked whether the runtime reproduces the measurement, and it does — including reproducing how bad it is.

## Notes

`model/` holds the ONNX export (2.3 GB of weights) and is git-ignored. The size is itself a finding: RAD-0047 flags how the model reaches a developer's machine as an open question, and 2.3 GB is why it is not a detail.
