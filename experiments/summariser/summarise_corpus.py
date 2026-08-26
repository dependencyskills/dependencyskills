#!/usr/bin/env python3
"""
Phase 1 of the summariser's own measurement — summarise the exact corpus slice `test5` scored.

WHY THIS EXISTS. `test5` measured how often the correct answer came back as the very first hit:
raw harvested doc text **29%**, hand-written caller's-words entries **77%**. The project has quoted
that gap ever since as the reason the
summarise step is the product rather than an optimisation. But the 77% was written **by hand**. No
machine has ever produced it. This runs the actual summariser over the same entries so the claim can
be checked rather than inherited.

The slice is built by `test5`'s own `subset()` at `seed=11`, `n=220` — deduped by symbol, every
query target retained. Reusing that construction verbatim is the point: the raw baseline this is
compared against was measured on these exact entries.

RESUMABLE, because it is ~220 local model calls. Re-running skips whatever `summaries.json`
already holds, so an interrupted run costs only what it had not finished.

Run:  uv run python summarise_corpus.py
      ... --limit 20      # a short run, to see it working
Out:  summaries.json (gitignored — derived, and rebuilt by re-running)
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "test5"))

import summarise as S
from eval_verb_ablation import subset

OUT = os.path.join(HERE, "summaries.json")
N = 220
SEED = 11


def load_slice():
    corpus = json.load(open(os.path.join(HERE, "..", "test5", "corpus.json")))
    queries = json.load(open(os.path.join(HERE, "..", "test5", "queries.json")))
    idx = subset(corpus, queries, N, seed=SEED)
    return corpus, queries, idx


def main():
    limit = int(sys.argv[sys.argv.index("--limit") + 1]) if "--limit" in sys.argv else None
    corpus, queries, idx = load_slice()
    if limit:
        idx = idx[:limit]
    targets = {q["target"] for q in queries}

    done = {}
    if os.path.exists(OUT):
        done = {r["symbol"]: r for r in json.load(open(OUT))}
        print(f"# resuming — {len(done)} already summarised")

    if S.MODEL != S.PINNED_MODEL:
        print("  ** model overridden — this run is NOT comparable to test7's measured result **")
    print(f"# summarising {len(idx)} entries with {os.path.basename(S.MODEL)}\n")

    for n, i in enumerate(idx, 1):
        e = corpus[i]
        if e["symbol"] in done:
            continue
        result, reason = S.summarise(e)
        result["reason"] = reason
        result["is_target"] = e["symbol"] in targets
        done[e["symbol"]] = result
        state = ("FAILED" if reason.startswith("__ERROR__")
                 else "DEGRADED" if result["degraded"] else "ok")
        print(f"  {n:>3}/{len(idx)}  {state:<9} {e['symbol'][-46:]:<46} "
              f"{(result.get('capability') or reason)[:58]}", flush=True)
        # Written every time: 220 model calls is long enough that losing the lot to one
        # interruption would make re-running the expensive option.
        json.dump(list(done.values()), open(OUT, "w"), indent=1)

    rows = [done[corpus[i]["symbol"]] for i in idx if corpus[i]["symbol"] in done]
    failed = sum(1 for r in rows if r["reason"].startswith("__ERROR__"))
    degraded = sum(1 for r in rows if r["degraded"] and not r["reason"].startswith("__ERROR__"))
    kept = sum(1 for r in rows if not r["degraded"])
    tgt_deg = sum(1 for r in rows if r["is_target"] and r["degraded"])
    print(f"\n  {kept} summarised, {degraded} degraded to signature-only, {failed} model failures")
    if failed:
        print("  ** model failures measure the harness, not the docs — fix them before scoring **")
    if kept + degraded:
        print(f"  degradation rate over completed calls: {degraded/(kept+degraded):.0%}")
    print(f"  query targets degraded: {tgt_deg} of {sum(1 for r in rows if r['is_target'])}"
          "   (a degraded target has no prose left to retrieve on)")
    print(f"\n# wrote {OUT}")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
