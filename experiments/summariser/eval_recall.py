#!/usr/bin/env python3
"""
Phase 2 — does the summariser actually lift retrieval, or was the 77% an artefact of hand-writing?

THE CLAIM BEING CHECKED. `test5` measured, at a matched 220 entries: raw harvested doc text
the correct answer coming back first **29%** of the time (17 queries), against hand-written
caller's-words entries at **77%** (26 queries). That
gap is why RAD-0014 named *summarise* as something this project must build, and it is quoted
throughout as "not an optimisation, it is the product". It has one hole: **the 77% was written by a
person.** Whether a local model produces entries that retrieve like the hand-written ones, or like
the raw text, or somewhere between, has never been measured.

WHAT IS AND IS NOT COMPARABLE. The controlled comparison is **raw against summarised on the same
17 queries, the same 220 entries, the same encoder** — everything held constant but the prose. The
77% is on a different query set (26) and a different entry set (synthetic), so it is a reference
point, not a row in the same table. It is printed as such.

DEGRADED ENTRIES ARE COUNTED, NOT EXCLUDED. Where verification rejected the output the entry falls
back to signature-only, which carries no prose, and it goes into the index that way — because that
is what the index would actually contain. Excluding them would measure a summariser that never
fails, which is not the one that exists.

Run:  uv run --with mlx-embeddings python eval_recall.py
"""
import json
import math
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "test5"))
from eval_verb_ablation import subset

EMB_MODEL = os.environ.get("EMB_MODEL", "mlx-community/bge-m3-mlx-fp16")
MAXCHARS = int(os.environ.get("MAXCHARS", "900"))
COMMENT = re.compile(r'^\s*/\*\*|\*/\s*$|^\s*\*\s?', re.M)
KS = [1, 3, 5, 10]
N, SEED = 220, 11


def raw_key(e):
    """Exactly `test5/embed_corpus.py`'s key. Copied rather than imported so a later edit there
    cannot silently move this baseline out from under a published number."""
    doc = COMMENT.sub("", e["doc"])
    return f"{e['symbol'].split('.')[-1]}. {' '.join(doc.split())[:MAXCHARS]}"


def summarised_key(e, s):
    """The same shape with the prose swapped — or the signature, where the entry degraded."""
    tail = e["symbol"].split(".")[-1]
    if s and not s["degraded"] and s.get("capability"):
        return f"{tail}. {s['capability']}"
    return f"{tail}. {(s or {}).get('signature') or e.get('signature') or ''}".strip()


def cosine(a, b):
    dot = sum(x * y for x, y in zip(a, b))
    na = math.sqrt(sum(x * x for x in a))
    nb = math.sqrt(sum(y * y for y in b))
    return dot / (na * nb) if na and nb else 0.0


def score(order_idx, corpus, queries, V, qv):
    hits, ranks = {k: 0 for k in KS}, []
    for q, v in zip(queries, qv):
        tgt = next((i for i in order_idx if corpus[i]["symbol"] == q["target"]), None)
        if tgt is None:
            ranks.append((q["target"].split(".")[-1], None))
            continue
        pos = sorted(order_idx, key=lambda i: cosine(v, V[i]), reverse=True).index(tgt) + 1
        ranks.append((q["target"].split(".")[-1], pos))
        for k in KS:
            hits[k] += pos <= k
    return hits, ranks


def main():
    corpus = json.load(open(os.path.join(HERE, "..", "test5", "corpus.json")))
    queries = json.load(open(os.path.join(HERE, "..", "test5", "queries.json")))
    idx = subset(corpus, queries, N, seed=SEED)

    path = os.path.join(HERE, "summaries.json")
    if not os.path.exists(path):
        sys.exit("no summaries.json — run summarise_corpus.py first.")
    sums = {r["symbol"]: r for r in json.load(open(path))}

    # A partial summarisation would score as a weak summariser rather than as an unfinished run.
    missing = [i for i in idx if corpus[i]["symbol"] not in sums]
    if missing:
        sys.exit(f"summaries.json covers {len(idx)-len(missing)} of {len(idx)} entries — "
                 f"{len(missing)} missing. Finish summarise_corpus.py before scoring; a partial "
                 "run reads as a bad summariser rather than as an incomplete one.")
    failed = [i for i in idx if sums[corpus[i]["symbol"]]["reason"].startswith("__ERROR__")]
    if failed:
        sys.exit(f"{len(failed)} entries are model failures, not summaries. Those measure the "
                 "harness. Re-run summarise_corpus.py to retry them before scoring.")

    kept = sum(1 for i in idx if not sums[corpus[i]["symbol"]]["degraded"])
    if kept == 0:
        sys.exit("every entry degraded — there is no summarised index to score. A run that "
                 "produced no prose must not report a retrieval number.")

    from mlx_embeddings import load, generate
    model, tok = load(EMB_MODEL)

    def embed(texts):
        out, B = [], 32
        for i in range(0, len(texts), B):
            out.extend(generate(model, tok, texts=texts[i:i + B]).text_embeds.tolist())
        return out

    views = {"raw harvested doc text": {i: raw_key(corpus[i]) for i in idx},
             "summarised":             {i: summarised_key(corpus[i], sums[corpus[i]["symbol"]])
                                        for i in idx}}
    qv = embed([q["query"] for q in queries])

    tgt_deg = sum(1 for q in queries
                  if sums.get(q["target"], {}).get("degraded"))
    print(f"# {len(idx)} entries, {len(queries)} queries, encoder {EMB_MODEL}")
    print(f"# {kept} summarised, {len(idx)-kept} degraded to signature-only "
          f"({(len(idx)-kept)/len(idx):.0%})")
    print(f"# query targets degraded: {tgt_deg} of {len(queries)} — those retrieve on a "
          f"signature, not on prose\n")
    print("**Key** — `top N` is how many of the queries got the correct answer back inside that")
    print("many hits. `top 1` is the strict one: the right entry came back first. Higher is")
    print("better. In the per-query list below the number is a RANK, so lower is better.\n")

    results = {}
    for name, keys in views.items():
        V = {}
        vecs = embed([keys[i] for i in idx])
        for i, v in zip(idx, vecs):
            V[i] = v
        hits, ranks = score(idx, corpus, queries, V, qv)
        results[name] = (hits, ranks)
        cells = "  ".join(f"top {k:<2} {hits[k]:>2}/{len(queries)}" for k in KS)
        print(f"  {name:<24} {cells}   first {hits[1]/len(queries):.0%}")

    print("\n  reference (NOT the same table): test5's hand-written caller's-words entries,")
    print("  26 queries over a synthetic 220-entry corpus — came back first 20/26 (77%).")

    # DIAGNOSTIC, NOT THE HEADLINE. The headline includes degraded targets because the index
    # would. But rewriting and falling back are two different mechanisms with opposite effects on
    # retrieval, and averaging them together hides both. This isolates the rewriter.
    live = [j for j, q in enumerate(queries) if not sums.get(q["target"], {}).get("degraded")]
    if live and len(live) < len(queries):
        sub_q = [queries[j] for j in live]
        sub_v = [qv[j] for j in live]
        print(f"\n## diagnostic — the {len(live)} queries whose target was NOT degraded\n")
        print("  Isolates the rewriter from the fallback. A subset chosen by an outcome of the run,")
        print("  so it is a diagnostic and never a headline.\n")
        for name, keys in views.items():
            V = {i: v for i, v in zip(idx, embed([keys[i] for i in idx]))}
            hits, _ = score(idx, corpus, sub_q, V, sub_v)
            cells = "  ".join(f"top {k:<2} {hits[k]:>2}/{len(sub_q)}" for k in KS)
            print(f"  {name:<24} {cells}   first {hits[1]/len(sub_q):.0%}")

    print("\n## per query, rank of the correct answer — lower is better, 1 means it came first\n")
    raw_r = dict(results["raw harvested doc text"][1])
    sum_r = dict(results["summarised"][1])
    for name in raw_r:
        a, b = raw_r[name], sum_r[name]
        moved = "" if a == b else ("  better" if (b or 999) < (a or 999) else "  worse")
        deg = "  [degraded]" if any(
            q["target"].split(".")[-1] == name and sums.get(q["target"], {}).get("degraded")
            for q in queries) else ""
        print(f"  {name[:40]:<40} raw {str(a or '>220'):>5}   summarised "
              f"{str(b or '>220'):>5}{moved}{deg}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
