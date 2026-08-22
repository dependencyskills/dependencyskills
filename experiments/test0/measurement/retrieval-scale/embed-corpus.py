#!/usr/bin/env python3
"""
Layer-2 setup: embed the corpus ONCE and cache the vectors, so the agent-facing
search tool (`search-codex.py`) is cheap to call repeatedly — it only has to embed
the single query at call time, not the whole 220-entry corpus.

Same in-process encoder as Layer 1 (BGE-M3, MIT, via mlx-embeddings), same retrieval
key: the semantic face only (capability + triggers), never the opaque symbol.

Run:  uv run --with mlx-embeddings embed-corpus.py
Out:  corpus-vecs.json  — { "model": ..., "vecs": [[...], ...] } aligned to corpus.json order
"""
import json, os

HERE = os.path.dirname(os.path.abspath(__file__))
EMB_MODEL = os.environ.get("EMB_MODEL", "mlx-community/bge-m3-mlx-fp16")


def embed(texts):
    from mlx_embeddings import load, generate
    model, tok = load(EMB_MODEL)
    out = []
    B = 32
    for i in range(0, len(texts), B):
        res = generate(model, tok, texts=texts[i:i + B])
        out.extend(res.text_embeds.tolist())
    return out


def main():
    corpus = json.load(open(os.path.join(HERE, "corpus.json")))
    texts = [f"{e['capability']}. triggers: {e['triggers']}" for e in corpus]
    print(f"# embedding {len(texts)} corpus entries via {EMB_MODEL} ...")
    vecs = embed(texts)
    out = {"model": EMB_MODEL, "vecs": vecs}
    json.dump(out, open(os.path.join(HERE, "corpus-vecs.json"), "w"))
    print(f"# wrote corpus-vecs.json ({len(vecs)} vectors, dim {len(vecs[0])})")


if __name__ == "__main__":
    main()
