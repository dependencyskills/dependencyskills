# Optimisations Worth Testing, When Speed Starts to Matter

RAD-0055 · 2026-08-28
Keywords: is the encoder fast enough; should we batch embeddings; batched generation for the summariser; reusing the system prompt's KV cache; quantising the encoder below F16; llama.cpp n_threads; Metal for the shipped native; Lucene scalar quantisation; what to optimise first; make it work make it right make it fast.
Measured against: nothing here is measured. The baseline figures it reasons from are `experiments/test26` (2026-08-28, macOS arm64), `experiments/test25` and [RAD-0051](RAD-0051-a-jvm-generative-runtime.md); the token-length distribution is `test5/corpus.json`, 14,899 entries, through `bge-small-en-v1.5`'s tokenizer.

## Question

> **Which performance work is worth doing on the codex, in what order, and what would each one have to show to be worth its cost?**

**None of it is needed now.** The pipeline works and the numbers are comfortable: a project harvest embeds in about two minutes, out of band, and a query embeds one short string. Make it work, make it right, make it fast — in that order, and this project is between the first two.

This exists so that when speed does start to mattering, the candidates are already named, sized and ordered, and nobody optimises the first thing they happen to think of. Every entry below records what it would cost, what it might return, and how to tell.

## Trail

### Where the baseline comes from, and why it is soft

[RAD-0054](RAD-0054-one-runtime-for-both-faces.md) measured the encoder at **7.3 ms** (ONNX) against **11.1 ms** (llama.cpp) per embedding. Both are one string at a time, single-threaded, on one laptop, with nothing tuned. Extrapolated to a small project — about 5,400 documented declarations, two vectors each under [#6](https://github.com/dependencyskills/dependencyskills/issues/6) — that is 79 s against 120 s.

The ratio stops mattering the moment DJL is dropped. What survives is the absolute: **roughly two minutes of embedding per project harvest**, out of band.

The generative side is where the real cost is. [RAD-0051](RAD-0051-a-jvm-generative-runtime.md) measured in-process generation at **25 minutes against 21 hours** on one dependency graph, and `test25` clocked gemma-3-270m at 0.1 s per entry against Qwen2.5-0.5B at 9.9 s. At 5,400 entries a project, that is minutes or hours depending on the model — an order of magnitude more headroom than the encoder has, and correspondingly more worth attention.

### What the token lengths say, and it changes a design

The retrieval keys are short and heavily skewed. Over all 14,899 entries of `test5/corpus.json`, through the shipped tokenizer:

| | tokens |
|---|---|
| median | **58** |
| p90 | 154 |
| p95 | 211 |
| p99 | 268 |
| p99.9 | 314 |
| max | 379 |

Only 1.37% exceed 256 tokens and 0.07% exceed 320, against the 512-token context the encoder is loaded with.

That does **not** make a case for shrinking the context on its own — llama.cpp computes over the tokens actually in the batch, not over `n_ubatch`, and the compute buffer at 512 tokens is 5.76 MiB against 67 MB of weights. There is nothing there.

It does change how batching would have to be built. A batch sized in **sequences** must reserve for the worst case: 32 × 512 = 16,384 tokens of micro-batch. A batch packed to a **token budget** of the same size holds about 70 typical entries instead of 32, because the median is 58 tokens and not 512. Same memory, roughly twice the work per micro-batch, and the skew is why.

### What is already known and should not be re-derived

- **The Vector API is worth about 2× on Lucene's filtered kNN** at a realistic filter size ([RAD-0047](RAD-0047-a-jvm-embedding-runtime.md): 2,962 µs → 1,603 µs at 500 coordinates). Not a question — set `--add-modules jdk.incubator.vector`.
- **In-process beats a subprocess by roughly 50× on a dependency graph** (RAD-0051), because a subprocess pays model load per entry. Already taken.
- **Per-CPU tuning of the native is priced and deferred** ([RAD-0053](RAD-0053-cpu-tuning-for-the-shipped-native.md)): ggml has no ARM runtime dispatch on macOS and the delta is one extension on the wrong side of the workload. Not repeated here.
- **Degradation rate cannot rank models** (RAD-0051). Any candidate below that changes the model must be scored on faithfulness and retrieval, never on how often the verifier rejects.

## Findings

**Nothing here is measured.** This section exists to say so plainly rather than to leave the reader inferring it from the absence of numbers. The table below is a research agenda, ordered by expected return against cost, and every row is a hypothesis.

| # | candidate | changes the ABI? | expected return |
|---|---|---|---|
| A | thread count on both paths | no | unknown, possibly large, free to try |
| B | batched generation for the summariser | yes | the largest single win available |
| C | reusing the system prompt's KV cache | yes | a constant removed from every call |
| D | batched embedding, packed by token budget | yes | bounded by ~2 minutes a harvest |
| E | quantising the encoder below F16 | no | 67 MB → ~20–35 MB, unknown recall cost |
| F | Metal on macOS | build only | unknown; costs a shipped `.metallib` |
| G | Lucene scalar quantisation | no | smaller index, unknown recall cost |

### A. Thread count — do this one first

Neither `n_threads` nor `n_threads_batch` is set anywhere; both take llama.cpp's default. This changes no interface, ships no new file, and can be measured in an afternoon. **It is the only candidate that could be wrong to skip**, because every other row is priced against a baseline that may itself be untuned.

### B. Batched generation for the summariser

The summariser is about 5,400 model calls per project, each fully independent — no shared state, no ordering, and the output of one never feeds another. llama.cpp runs several sequences in one context with distinct seq ids. This is the shape that batching was invented for, and it is the path where the baseline is measured in hours rather than minutes.

To measure: generation throughput at n_parallel of 1, 4, 8, 16 against resident memory at each, on the smallest model that `test25` found viable. The verifier's degradation rate must be **identical** across all of them; a change there means sequences are contaminating each other, which is a correctness failure wearing a performance result's clothes.

### C. Reusing the system prompt's KV cache

Every summariser call sends a byte-identical prefix — the four-property system prompt, roughly 170 tokens — followed by a symbol, a signature and a doc whose median length is short. The prefix is plausibly **half the prompt tokens on a typical call**, decoded fresh every time.

llama.cpp can retain the KV for a common prefix and decode only the varying suffix. The risk is precisely the thing #7 exists to prevent: state surviving between two documents that must not see each other. So the acceptance bar is not throughput, it is that a run with prefix reuse produces **byte-identical output** to a run without it, over the whole sample. Anything less and it does not ship, whatever it saves.

### D. Batched embedding, packed by token budget

Per the token distribution above: pack to a token budget, not a sequence count. Bounded upside — the whole embedding cost is about two minutes a harvest, so even perfect batching saves two minutes, once, out of band. Worth doing only if #6 turns up an access pattern that makes it matter, or after B and C have taken the large numbers off the table.

### E. Quantising the encoder below F16

67 MB F16 → roughly 35 MB at Q8_0 or 20 MB at Q4_K_M, with corresponding resident memory. [RAD-0048](RAD-0048-where-the-encoder-size-cutoff-is.md) found int8 **collapsing** under CLS pooling, so this is not free and cannot be assumed from the file size.

Measure with `test26` phase 4 — top-100 ranking agreement against the F16 vectors. Recall@k on that corpus is **not** the instrument; only one query in seventeen lands at all, so a regression has almost nothing to destroy and would pass unnoticed. One variable at a time: quantisation with pooling held at mean.

### F. Metal on macOS

`GGML_METAL=OFF` today, because Metal needs a `.metallib` shipped beside the library and the summariser runs on CPU by default. Worth pricing rather than assuming: it trades a packaging problem for throughput on the platform most developers here are using, and the packaging problem is the same class as the one the static link already solved.

### G. Lucene scalar quantisation

Lucene supports quantised kNN vectors, which shrinks the index and speeds the search. Same acceptance bar as E: ranking agreement against unquantised, not recall@k. Interacts with the scope filter — RAD-0047 measured filter cost against filter size, and that measurement would need repeating.

### The one that is not tuning

**Not embedding what is already embedded.** The store is content-addressed and machine-wide ([ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md)), and a vector is a pure function of (entry content, encoder, pooling) — all three of which #6 already requires be recorded per entry. So the second project that depends on a library should pay nothing, and the numbers above describe a *cold* harvest that most harvests are not.

That is worth more than every row in the table combined, and it is a property of #6's design rather than a knob to turn. If it is already the intent, this RAD has nothing to add to it. If it is not, that is the finding.

[RAD-0052](RAD-0052-distributing-a-precomputed-codex.md) takes the same argument one step further and removes the local model from the common case entirely.

## Recommendation

**Do none of this now.** The pipeline works, it is being made right, and nothing here is blocking anything. Ordering exists so the work is cheap to start, not so it starts.

**When it does matter, in this order:** A (threads, no ABI, may invalidate every other baseline), then B and C on the generative path where the numbers are hours, then D, E, F, G as the specific need appears. Confirm the caching property before any of them, because it may make the whole question smaller.

**Two rules that apply to every row.** Ranking agreement is the instrument for anything that touches vectors — `test26` phase 4, never recall@k on this corpus. And byte-identical output is the bar for anything that shares state between documents, because #7's guarantee is not a performance property and must not be traded for one.

**What would change the answer:** a harvest that people actually wait on; a corpus an order of magnitude larger than one project; a decision to run this as a service ([#8](https://github.com/dependencyskills/dependencyskills/issues/8)) where throughput is someone's bill rather than a developer's coffee break.

## Connections

- [RAD-0054](RAD-0054-one-runtime-for-both-faces.md) — the encoder baseline, and the untuned figures this reasons from
- [RAD-0051](RAD-0051-a-jvm-generative-runtime.md) — the generative baseline, and why in-process was the first optimisation
- [RAD-0053](RAD-0053-cpu-tuning-for-the-shipped-native.md) — per-CPU tuning, priced and deferred, not repeated here
- [RAD-0047](RAD-0047-a-jvm-embedding-runtime.md) — the Vector API result and the filtered-kNN cost curve
- [RAD-0048](RAD-0048-where-the-encoder-size-cutoff-is.md) — why quantisation cannot be assumed safe
- [RAD-0052](RAD-0052-distributing-a-precomputed-codex.md) — the distribution answer that may remove the cost rather than reduce it
