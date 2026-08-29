# encoder

The default embedding model, packaged for Maven Central.

```
org.dependencyskills.codex:encoder:1.5.0        58 MB
```

Contains `BAAI/bge-small-en-v1.5` as **GGUF F16**, one file at `dependencyskills/encoder/model.gguf`. The tokenizer vocabulary is inside it, which is why there is no `tokenizer.json` beside it any more.

## The weights ARE in this repository, and that is a reversal

They used to be fetched from Hugging Face at build time, because "a repository carrying 134 MB of third-party binary is a repository nobody can clone". Two things changed that.

**Nobody publishes a first-party GGUF**, of this model or of any other we might want, so the file has to be produced by somebody. **And the conversion is byte-reproducible** — two independent runs give an identical digest — which is what makes committing a derived binary defensible instead of asking anyone to trust ours. `model/RECIPE.md` records the source digests, the converter commit and the command.

The file is 64.5 MB and compresses to 58. For calibration, this repository already commits about 78 MB of native libraries, deliberately, and unlike those the model changes only when the model version does.

What did **not** change is the check. A binary in a repository can be replaced, corrupted by a bad merge or truncated in transfer, and none of those announce themselves — so the digest is still pinned and still compared on every build, and it is still a hard failure:

```
> model.gguf failed verification.
  The committed model is not the one model/RECIPE.md describes. Do not package it.
```

The build needs no network and no Python.

```
./gradlew :encoder:encoderJar
```

It is deliberately **not** wired into `assemble`: a contributor who is not shipping a model should not pay to build one.

## Why this model

[RAD-0048](../../../docs/knowledge/research/RAD-0048-where-the-encoder-size-cutoff-is.md) swept five encoders from 2,267 MB down to 33 MB.

- **At realistic corpus size no encoder in that range rescues raw doc text** — a 33 MB model scored marginally *ahead* of the 2,267 MB one over 14,899 entries. Encoder capacity is not the bottleneck; the retrieval key is.
- Where recall can still be lost, size buys a little: 13/17 → 10/17 at r@10 across a 67× size range.
- **fp16 matched fp32 at every k**, which is why F16 is what ships. The earlier reasoning against it — that taking a smaller variant would mean redistributing a *community* re-quantisation — no longer applies, because the conversion is ours and reproducible. [RAD-0054](../../../docs/knowledge/research/RAD-0054-one-runtime-for-both-faces.md) then measured the GGUF against the ONNX export it replaced: cosine **0.9999986**, and **99.7%** top-100 ranking agreement over 14,899 entries. No entry moved.

`Encoder-Pooling: mean` in the manifest is not incidental. RAD-0048 measured pooling as a **per-model** property — CLS is better for bge-m3, level for bge-base, and clearly worse for the small quantised variants. A store's vectors cannot be mixed across poolings, so the value travels with the artifact.

## The name says nothing about the model, on purpose

The artifact is `encoder`, not `encoder-bge-small-en-v1.5`. Which model is inside is declared in the jar manifest — `Encoder-Model`, `Encoder-Dimensions`, `Encoder-Pooling` — where a consumer can read it, rather than in a coordinate nobody inspects on a classpath. Changing the model is then a version bump instead of a new coordinate and a migration for everyone depending on the old one.

## Versioning

Versioned by the **model**, not the plugin, via `encoderVersion` in `gradle.properties`. A plugin patch release must not re-ship 58 MB, and a model update must not force a plugin release.

## The multilingual case, and it is not supported yet

bge-small-en is English-only, which is right for the default and wrong for someone indexing libraries documented in Chinese or Japanese. `bge-m3` covers 100+ languages at 2,267 MB — far too large to publish as a dependency.

**Nothing downloads it, and nothing will.** A model that big is **installed by the developer**, at the path `ModelLocation` names, and a build that quietly pulled 2.3 GB the first time somebody ran it would have done something surprising. When an installed model is missing, the message says exactly which file to put where. That is [ADR-0013](../../../docs/knowledge/decisions/ADR-0013-how-a-model-reaches-a-developer.md).

Installing one is not the same as being supported. Language is not detected at harvest, the index enforces a single encoder basis, and no multilingual retrieval has been measured here at all — [#17](https://github.com/dependencyskills/dependencyskills/issues/17) holds that work. The encoder is a configuration point, not a constant; the configuration just does not yet know what to do with a corpus that is not English.

## Licence provenance

The upstream repository declares `license: mit` in its model-card metadata but **ships no LICENSE file and no copyright line**. Rather than invent one, `LICENSE` cites the model card as the authoritative statement — quoting the declaration itself — and reproduces the MIT terms the author named. The weights are redistributed unmodified.

That is the most that can be honoured when the author has not published a copyright notice, and it is what we honour.
