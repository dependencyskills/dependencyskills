#!/usr/bin/env python3
"""Recompute per-model arm totals from the raw results JSON — the numbers of record.

Run it for the table; import it to reuse the arithmetic. `make-vendor-letters.py` imports
`tally_all` so that vendor-facing figures come from the raw results and are never re-typed.
"""
import json, glob, os, collections

HERE = os.path.dirname(os.path.abspath(__file__))


def tally_file(path):
    """One results-*.json -> per-arm comply/total/err. The arithmetic of record."""
    d = json.load(open(path))
    n = d.get("N")
    arm = collections.defaultdict(lambda: [0, 0, 0, 0])  # comply, total, err, flag
    payloads = []
    for pid, a, comply, flag, errs in d["rows"]:
        arm[a][0] += comply; arm[a][1] += (n or 0); arm[a][2] += errs; arm[a][3] += flag
        if pid not in payloads:
            payloads.append(pid)
    return {"file": os.path.basename(path), "path": path, "model": d["model"],
            "base": d.get("base"), "n": n, "payloads": payloads,
            "arms": {a: {"comply": v[0], "total": v[1], "err": v[2], "flag": v[3]}
                     for a, v in sorted(arm.items())}}


def tally_all(directory=HERE):
    """Every results-*.json in `directory`, filename-sorted."""
    return [tally_file(p) for p in sorted(glob.glob(os.path.join(directory, "results-*.json")))]


def format_row(t):
    parts = " | ".join(f"{a}: {v['comply']}/{v['total']}" + (f" (err {v['err']})" if v["err"] else "")
                       for a, v in t["arms"].items())
    return f"{t['model']:<40} N={t['n']}  {parts}"


def main():
    for t in tally_all():
        print(format_row(t))


if __name__ == "__main__":
    main()
