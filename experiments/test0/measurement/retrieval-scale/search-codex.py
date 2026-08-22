#!/usr/bin/env python3
"""
search-codex — the codex index exposed as an agent-facing tool (Layer 2).

Stands in for the MCP query tool of RAD-0003. Given a natural-language query, it
embeds it in-process (BGE-M3, MIT), ranks the corpus by cosine over the semantic
face (vector-primary — the Layer-1 finding, RAD-0019), and prints the top-k
candidates as JSON. The agent authors the query, reads these, and decides.

Deliberately returns the semantic face (capability + triggers) but NOT the
`target` flag or `notfor` — the agent must choose from what a real caller would see.

Usage:  uv run --with mlx-embeddings search-codex.py "your query in plain words" [-k 10]
Needs:  corpus-vecs.json (run embed-corpus.py once first).
"""
import json, math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
EMB_MODEL = os.environ.get("EMB_MODEL", "mlx-community/bge-m3-mlx-fp16")


def embed_one(text):
    from mlx_embeddings import load, generate
    model, tok = load(EMB_MODEL)
    return generate(model, tok, texts=[text]).text_embeds.tolist()[0]


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a)); nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def main():
    args = sys.argv[1:]
    k = 10
    if "-k" in args:
        i = args.index("-k"); k = int(args[i + 1]); args = args[:i] + args[i + 2:]
    query = " ".join(args).strip()
    if not query:
        print('usage: search-codex.py "query in plain words" [-k 10]', file=sys.stderr)
        sys.exit(2)

    corpus = json.load(open(os.path.join(HERE, "corpus.json")))
    cache = json.load(open(os.path.join(HERE, "corpus-vecs.json")))
    vecs = cache["vecs"]

    qv = embed_one(query)
    scored = sorted(
        ((cosine(qv, vecs[i]), i) for i in range(len(corpus))),
        reverse=True,
    )[:k]
    results = [
        {
            "rank": r + 1,
            "symbol": corpus[i]["symbol"],
            "capability": corpus[i]["capability"],
            "triggers": corpus[i]["triggers"],
            "score": round(s, 4),
        }
        for r, (s, i) in enumerate(scored)
    ]
    print(json.dumps(results, indent=2))


if __name__ == "__main__":
    main()
