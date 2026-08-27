# encoder-small-en

The default embedding model, packaged for Maven Central.

```
org.dependencyskills:encoder-small-en:1.5.0        78 MB
```

Contains `BAAI/bge-small-en-v1.5` — the ONNX export published by the model's own authors, unmodified — plus its tokenizer, at `dependencyskills/encoder-small-en/` inside the jar.

## The weights are not in this repository

They are fetched from Hugging Face at build time and verified against **pinned SHA-256 digests**. A repository carrying 134 MB of third-party binary is a repository nobody can clone, and a checksum makes the provenance checkable rather than asserted.

The verification is a hard failure, not a warning: a digest mismatch stops the build and refuses to package. That path is tested by corrupting a file and confirming the build fails — a verifier that passes everything is indistinguishable from no verifier.

```
./gradlew :encoder-small-en:encoderJar
```

## Why this model

[RAD-0048](../../../docs/knowledge/research/RAD-0048-where-the-encoder-size-cutoff-is.md) swept five encoders from 2,267 MB down to 33 MB.

- **At realistic corpus size no encoder in that range rescues raw doc text** — a 33 MB model scored marginally *ahead* of the 2,267 MB one over 14,899 entries. Encoder capacity is not the bottleneck; the retrieval key is.
- Where recall can still be lost, size buys a little: 13/17 → 10/17 at r@10 across a 67× size range.
- **fp16 matched fp32 at every k**, and this artifact compresses to 78 MB anyway — less than the fp16 export ships raw. So the smaller variant buys nothing here, and taking it would have meant redistributing a community re-quantisation instead of the authors' own export.

`Encoder-Pooling: mean` in the manifest is not incidental. RAD-0048 measured pooling as a **per-model** property — CLS is better for bge-m3, level for bge-base, and clearly worse for the small quantised variants. A store's vectors cannot be mixed across poolings, so the value travels with the artifact.

## Versioning

Versioned by the **model**, not the plugin, via `encoderVersion` in `gradle.properties`. A plugin patch release must not re-ship 78 MB, and a model update must not force a plugin release.

## The multilingual case

bge-small-en is English-only, which is right for the default and wrong for someone indexing libraries documented in Chinese or Japanese. `bge-m3` covers 100+ languages at 2,267 MB — too large for Central, so it is fetched on demand rather than bundled. The encoder is a configuration point, not a constant.

## Licence provenance

The upstream repository declares `license: mit` in its model-card metadata but **ships no LICENSE file and no copyright line**. Rather than invent one, `LICENSE` cites the model card as the authoritative statement — quoting the declaration itself — and reproduces the MIT terms the author named. The weights are redistributed unmodified.

That is the most that can be honoured when the author has not published a copyright notice, and it is what we honour.
