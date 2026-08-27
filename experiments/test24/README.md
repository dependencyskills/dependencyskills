# test24 — where the encoder size cutoff is

**Question:** how small can the embedding model be before retrieval degrades, and does the pooling nobody chose deliberately cost anything?

Written up as [RAD-0048](../../docs/knowledge/research/RAD-0048-where-the-encoder-size-cutoff-is.md). Opened because [RAD-0047](../../docs/knowledge/research/RAD-0047-a-jvm-embedding-runtime.md) put a number on the artifact that would have to reach every developer's machine: **2,267 MB**.

```
javac -cp "lib/*" -d . Sweep.java Control.java
java --enable-native-access=ALL-UNNAMED -cp "lib/*:." Sweep      # 5 models x 2 poolings, 220 entries
java --enable-native-access=ALL-UNNAMED -cp "lib/*:." Control    # full 14,899-entry control
```

`lib/` is a symlink to test23's jars. `models/` and `subset220.json` are generated; `models/` is git-ignored.

## Why 220 entries and not the full corpus

At 14,899 entries the raw-doc baseline is 0–1 of 17. **An encoder comparison scored there measures nothing** — every candidate returns zero and the sweep reports a tie. `subset220.json` is test5's deterministic construction: deduplicated by symbol, every query target retained, `seed=11`, so the numbers line up with test5's size-sweep table.

The full corpus runs anyway, as a control on the premise.

## Results

**Measured against:** DJL 0.36.0 over ONNX Runtime 1.21.1, OpenJDK 26.0.2.1, macOS arm64 CPU, 2026-08-27. Both poolings come from one forward pass, so this is a full factorial rather than a confound.

| model | size | pool | r@1 | r@3 | r@5 | r@10 | ms/emb |
|---|---|---|---|---|---|---|---|
| bge-m3 | 2,267 MB | mean | 5/17 | 6/17 | 8/17 | 13/17 | 75.9 |
| | | **CLS** | **7/17** | **9/17** | **11/17** | 13/17 | |
| bge-base-en-v1.5 | 418 MB | mean | 5/17 | 7/17 | 9/17 | 12/17 | 25.0 |
| | | CLS | 5/17 | 7/17 | 8/17 | 11/17 | |
| bge-small-en-v1.5 | 136 MB | mean | 4/17 | 7/17 | 8/17 | 10/17 | 8.7 |
| bge-small fp16 | 64 MB | mean | 4/17 | 7/17 | 8/17 | 10/17 | 18.1 |
| bge-small int8 | 33 MB | mean | 5/17 | 7/17 | 7/17 | 9/17 | 4.5 |

**The pooling was costing 2 of 17 at rank 1.** BGE-M3 with CLS — what its model card documents — beats mean 7/17 to 5/17. Every prior retrieval number in this project used mean, so all of them understate the encoder. It does not generalise: CLS is better for bge-m3, level for bge-base, and worse for int8. **Pooling is per-model.**

**fp16 is free.** Identical at every k to fp32, half the bytes. Not faster, though — 18.1 ms against 8.7, because the CPU upcasts.

## The control, which inverts the ladder

| corpus | bge-m3 (2,267 MB) | bge-small int8 (33 MB) |
|---|---|---|
| 220 entries | r@10 **13/17** | r@10 9/17 |
| 14,899 entries | r@1 0/17 · r@10 1/17 | r@1 0/17 · r@10 **2/17** |

At realistic scale the **33 MB model is marginally ahead of the 2.3 GB one**, and embeds the whole corpus in **67 seconds** against roughly 19 minutes.

Both are effectively zero, and that is the finding: encoder capacity is not what fails at scale. The retrieval key is.

## Caveat

**n = 17 queries.** A difference of one or two at any k is noise, and the int8 column moving in both directions at once — better at r@1, worse at r@10 — is what noise looks like at this n. What survives the caveat is the pooling result, the fp16 result, and the full-corpus inversion.
