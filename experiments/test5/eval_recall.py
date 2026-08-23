#!/usr/bin/env python3
"""
test5 — recall over the real harvested corpus. Phase 2.

Answers two of test5's questions against the same rig that produced RAD-0019's 77% on the
synthetic corpus, so the numbers are directly comparable:

  Q1  does RAW harvested doc text retrieve, with no summarise step?
  Q2  what does the transitive tail add? (RAD-0022) - the same queries are scored against
      the full index and against a declared-only index.

A third index drops kotlin-stdlib, which is a *direct* dependency contributing 49% of the
corpus and, being the best-known library in the ecosystem, the one with the least training gap
(RAD-0016). If removing it improves recall, corpus composition matters more than provenance.

Run: uv run --with mlx-embeddings python eval_recall.py
"""
import json, math, os

HERE = os.path.dirname(os.path.abspath(__file__))
KS = [1, 3, 5, 10]


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a)); nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def main():
    corpus = json.load(open(os.path.join(HERE, "corpus.json")))
    vecs = json.load(open(os.path.join(HERE, "corpus-vecs.json")))
    queries = json.load(open(os.path.join(HERE, "queries.json")))
    V = vecs["vecs"]
    assert len(V) == len(corpus), f"{len(V)} vectors vs {len(corpus)} entries"

    from mlx_embeddings import load, generate
    model, tok = load(vecs["model"])
    qv = generate(model, tok, texts=[q["query"] for q in queries]).text_embeds.tolist()

    views = {
        "full (declared + transitive)": list(range(len(corpus))),
        "declared only":                [i for i, e in enumerate(corpus) if e["provenance"] == "direct"],
        "full minus kotlin-stdlib":     [i for i, e in enumerate(corpus) if "kotlin-stdlib" not in e["library"]],
    }

    print(f"# {len(corpus)} entries, {len(queries)} queries, encoder {vecs['model']}\n")
    for name, idx in views.items():
        hits = {k: 0 for k in KS}
        present = 0
        misses = []
        for q, v in zip(queries, qv):
            tgt = next((i for i in idx if corpus[i]["symbol"] == q["target"]), None)
            if tgt is None:
                misses.append((q["target"], "not in this index"))
                continue
            present += 1
            scored = sorted(idx, key=lambda i: cosine(v, V[i]), reverse=True)
            pos = scored.index(tgt) + 1
            for k in KS:
                hits[k] += pos <= k
            if pos > 10:
                misses.append((q["target"].split(".")[-1], f"#{pos}"))
        n = len(queries)
        cells = "  ".join(f"r@{k} {hits[k]}/{n}" for k in KS)
        print(f"{name:<30} entries {len(idx):>6}   target present {present}/{n}   {cells}")
        if misses:
            print(f"{'':<30} misses: " + ", ".join(f"{a} {b}" for a, b in misses[:6]))
    print("\n# RAD-0019 synthetic baseline, hand-written semantic faces: r@1 20/26 (77%)")


if __name__ == "__main__":
    main()
