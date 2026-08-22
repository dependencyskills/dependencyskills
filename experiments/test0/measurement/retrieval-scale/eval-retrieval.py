#!/usr/bin/env python3
"""
Layer-1 retrieval recall eval. Embeds the corpus semantic faces and the caller queries
IN-PROCESS with mlx-embeddings (Apache-2.0, MLX-native — no server, no LM Studio), then
ranks each query three ways — vector (cosine), lexical (BM25), hybrid (Reciprocal Rank
Fusion) — and reports recall@k: did the ground-truth target land in the top-k, among the
full adversarial+noise corpus.

The retrieval KEY is the semantic face only (capability + triggers) — never the opaque
symbol — so this measures whether an entry written in the caller's words is retrievable
by the caller's own description of the need (RAD-0011 / RAD-0013).

Runtime is embedded (a library in this process), the production-shaped choice. Retrieval
math is pure Python.

Run:  uv run --with mlx-embeddings eval-retrieval.py
Env:  EMB_MODEL (default all-MiniLM-L6-v2-4bit), DOC_PREFIX, QUERY_PREFIX, KS
"""
import json, os, math, re

HERE = os.path.dirname(os.path.abspath(__file__))
EMB_MODEL = os.environ.get("EMB_MODEL", "mlx-community/bge-m3-mlx-fp16")  # MIT, strong dense retrieval
DOC_PREFIX = os.environ.get("DOC_PREFIX", "")      # e.g. "search_document: " for nomic
QUERY_PREFIX = os.environ.get("QUERY_PREFIX", "")  # e.g. "search_query: " for nomic
KS = [int(x) for x in os.environ.get("KS", "1,3,5,10").split(",")]

_MODEL = None


def embed(texts):
    global _MODEL
    from mlx_embeddings import load, generate
    if _MODEL is None:
        _MODEL = load(EMB_MODEL)
    model, tok = _MODEL
    # batch to keep memory modest and lengths uniform-ish
    out = []
    B = 32
    for i in range(0, len(texts), B):
        chunk = texts[i:i + B]
        res = generate(model, tok, texts=chunk)
        out.extend(res.text_embeds.tolist())
    return out


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a)); nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def tokenize(s):
    return re.findall(r"[a-z0-9]+", s.lower())


def bm25_scores(query, doc_tokens, df, avgdl, N, k1=1.5, b=0.75):
    scores = []
    for toks in doc_tokens:
        tf = {}
        for t in toks:
            tf[t] = tf.get(t, 0) + 1
        dl = len(toks); s = 0.0
        for t in set(query):
            if t not in tf:
                continue
            idf = math.log((N - df.get(t, 0) + 0.5) / (df.get(t, 0) + 0.5) + 1)
            s += idf * (tf[t] * (k1 + 1)) / (tf[t] + k1 * (1 - b + b * dl / avgdl))
        scores.append(s)
    return scores


def rank(scores):
    return sorted(range(len(scores)), key=lambda i: scores[i], reverse=True)


def rrf(rankings, weights=None, kconst=60):
    weights = weights or [1.0] * len(rankings)
    agg = {}
    for r, w in zip(rankings, weights):
        for pos, idx in enumerate(r):
            agg[idx] = agg.get(idx, 0.0) + w / (kconst + pos + 1)
    return sorted(agg, key=lambda i: agg[i], reverse=True)


def main():
    corpus = json.load(open(os.path.join(HERE, "corpus.json")))
    queries = json.load(open(os.path.join(HERE, "queries.json")))
    N = len(corpus)
    doc_texts = [f"{e['capability']}. triggers: {e['triggers']}" for e in corpus]
    doc_tokens = [tokenize(t) for t in doc_texts]
    df = {}
    for toks in doc_tokens:
        for t in set(toks):
            df[t] = df.get(t, 0) + 1
    avgdl = sum(len(t) for t in doc_tokens) / N
    syms = [e["symbol"] for e in corpus]

    print(f"# corpus {N} entries; embedding in-process via {EMB_MODEL}")
    doc_vecs = embed([DOC_PREFIX + t for t in doc_texts])
    q_vecs = embed([QUERY_PREFIX + q["query"] for q in queries])

    VW = float(os.environ.get("VW", "2.0"))  # vector weight in the weighted hybrid
    methods = ["vector", "lexical", "hybrid", f"hybrid+v{VW:g}"]
    hits = {m: {k: 0 for k in KS} for m in methods}
    print(f"\n{'query (target)':<34} {'method':<8} " + " ".join(f'r@{k}' for k in KS) + "   rank")
    for qi, q in enumerate(queries):
        ti = syms.index(q["target"])
        vscore = [cosine(q_vecs[qi], dv) for dv in doc_vecs]
        lscore = bm25_scores(tokenize(q["query"]), doc_tokens, df, avgdl, N)
        vrank, lrank = rank(vscore), rank(lscore)
        ranks = {"vector": vrank, "lexical": lrank,
                 "hybrid": rrf([vrank, lrank]),
                 f"hybrid+v{VW:g}": rrf([vrank, lrank], weights=[VW, 1.0])}
        label = q["target"].split(".")[-1]
        for m in methods:
            pos = ranks[m].index(ti) + 1
            cells = []
            for k in KS:
                ok = pos <= k
                hits[m][k] += ok
                cells.append(" ✓ " if ok else " · ")
            print(f"{label if m=='vector' else '':<34} {m:<8} " + " ".join(cells) + f"   #{pos}")
    Q = len(queries)
    print(f"\n# recall@k  (of {Q} queries, corpus N={N})")
    print(f"{'method':<10} " + " ".join(f'r@{k}' for k in KS))
    for m in methods:
        print(f"{m:<10} " + " ".join(f"{hits[m][k]}/{Q}" for k in KS))


if __name__ == "__main__":
    main()
