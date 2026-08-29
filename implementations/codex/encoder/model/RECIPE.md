# How `model.gguf` was produced

`BAAI/bge-small-en-v1.5`, converted to GGUF F16. Committed rather than fetched, so the build
needs no network and no Python — and so a clean clone can produce the published artifact.

**The conversion is byte-reproducible.** Two independent runs of the command below yield an
identical digest, which is what makes committing a derived binary defensible instead of asking
anyone to trust ours. It is also more provenance than the ONNX export this replaced: nobody can
reproduce that one either, and for it we trusted a digest and nothing else.

## Result

```
sha256  86776c71a9890f0246d12022ee8e5e9cf382012917ad8e611bb269b91f6b3e21
size    67,582,560 bytes
format  GGUF, F16, arch bert, 384 dimensions, 512 context, 197 tensors
```

The tokenizer vocabulary is **inside** the GGUF, which is why there is no `tokenizer.json` beside
it any more. That vocabulary is what llama.cpp tokenizes from, and `experiments/test26` measured
it reproducing the Hugging Face tokenizer at cosine 0.9999986 over 300 real entries — the one risk
of the move, checked rather than assumed.

## Inputs

From `https://huggingface.co/BAAI/bge-small-en-v1.5/resolve/main`:

| file | sha256 |
|---|---|
| `config.json` | `094f8e891b932f2000c92cfc663bac4c62069f5d8af5b5278c4306aef3084750` |
| `model.safetensors` | `3c9f31665447c8911517620762200d2245a2518d6e7208acc78cd9db317e21ad` |
| `modules.json` | `84e40c8e006c9b1d6c122e02cba9b02458120b5fb0c87b746c41e0207cf642cf` |
| `special_tokens_map.json` | `b6d346be366a7d1d48332dbc9fdf3bf8960b5d879522b7799ddba59e76237ee3` |
| `tokenizer.json` | `d241a60d5e8f04cc1b2b3e9ef7a4921b27bf526d9f6050ab90f9267a1f9e5c66` |
| `tokenizer_config.json` | `9261e7d79b44c8195c1cada2b453e55b00aeb81e907a6664974b4d7776172ab3` |
| `vocab.txt` | `07eced375cec144d27c900241f3e339478dec958f92fddbc551f295c992038a3` |
| `1_Pooling/config.json` | `d1caf60c96f5fba2157c0c26b76d80818fad6cf0b8eb5e73ec372ff9818eba5c` |

**`modules.json` is not optional.** The converter finds the pooling configuration through it, and
without it no pooling is written into the GGUF at all — llama.cpp then falls back to no pooling
and returns nothing from `llama_get_embeddings_seq`. A conversion from a partial download produces
a model that is unusable rather than wrong, which is the better failure and still a trap.

## Converter

`llama.cpp` at commit `b19cbe9` (*convert: prevent ndarray conversion in LazyChunkedTensor*).

## Command

```bash
python3 convert_hf_to_gguf.py <source-dir> --outfile model.gguf --outtype f16
```

## What the GGUF declares, and why the code overrides it

The converter writes the model card's own pooling — **CLS** — into `bert.pooling_type`.
[RAD-0048](../../../../docs/knowledge/research/RAD-0048-where-the-encoder-size-cutoff-is.md)
measured **mean** as the better pooling for this model and this corpus, and the jar manifest
declares `Encoder-Pooling: mean` accordingly.

So the metadata in this file is *not* the value to use. `dsc_encoder_load` requires pooling as an
argument and refuses to default to the metadata, because taking the default costs cosine 0.93030
against 0.9999986 — vectors of the right width in the wrong basis, which index, score and rank,
wrongly. See [ADR-0013](../../../../docs/knowledge/decisions/ADR-0013-how-a-model-reaches-a-developer.md).
