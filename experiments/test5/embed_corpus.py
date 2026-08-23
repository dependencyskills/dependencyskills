#!/usr/bin/env python3
"""
test5 — embed the harvested corpus. Phase 1 of the retrieval measurement.

The retrieval KEY is the RAW doc text, with only the comment syntax stripped. Nothing here
rewrites it into a caller's words: that rewriting is the summarise step, and whether it is
needed is question 1. Cleaning `/**`, `*` and `*/` is parsing, not summarising.

Same encoder as every prior retrieval result (BGE-M3, MIT, in-process via mlx-embeddings) so
the number is comparable to RAD-0019's 77% on the synthetic corpus.

Run: uv run --with mlx-embeddings python embed_corpus.py
Out: corpus-vecs.json
"""
import json, os, re, sys

HERE = os.path.dirname(os.path.abspath(__file__))
EMB_MODEL = os.environ.get("EMB_MODEL", "mlx-community/bge-m3-mlx-fp16")
MAXCHARS = int(os.environ.get("MAXCHARS", "900"))     # KDoc leads with its summary

COMMENT = re.compile(r'^\s*/\*\*|\*/\s*$|^\s*\*\s?', re.M)


def key_text(e):
    """What gets embedded: the declaration's own name plus its raw documentation."""
    doc = COMMENT.sub("", e["doc"])
    doc = " ".join(doc.split())[:MAXCHARS]
    return f"{e['symbol'].split('.')[-1]}. {doc}"


def main():
    corpus = json.load(open(os.path.join(HERE, "corpus.json")))
    texts = [key_text(e) for e in corpus]
    print(f"# embedding {len(texts)} entries via {EMB_MODEL} (max {MAXCHARS} chars each)")

    from mlx_embeddings import load, generate
    model, tok = load(EMB_MODEL)
    vecs, B = [], 32
    for i in range(0, len(texts), B):
        vecs.extend(generate(model, tok, texts=texts[i:i + B]).text_embeds.tolist())
        if (i // B) % 25 == 0:
            print(f"  {i + len(texts[i:i+B])}/{len(texts)}", flush=True)
    json.dump({"model": EMB_MODEL, "maxchars": MAXCHARS, "vecs": vecs},
              open(os.path.join(HERE, "corpus-vecs.json"), "w"))
    print(f"# wrote corpus-vecs.json ({len(vecs)} vectors, dim {len(vecs[0])})")


if __name__ == "__main__":
    main()
