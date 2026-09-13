#!/usr/bin/env python3
"""Two-stage retrieval: rank members, aggregate their hits to the declaring package.

The technique RAD-0070 measured and then refuted, kept so the refutation is
reproducible. It exploits a property RAD-0049 measured independently — lexical
search lands in the right neighbourhood while failing to choose within it — by
scoring packages from their members' hits rather than from their own text, so a
two-member package is not penalised for having almost no prose.

Measured, and this is the point of keeping it:

    five self-authored needs, one library      4 of 5  at rank 1
    RAD-0049's 17 needs, its 59 coordinates    3 of 17 at rank 1

The first number does not replicate. The needs were written by the agent that
knew the targets, so they shared vocabulary with them — which is the one
condition under which lexical is known to succeed. A self-authored need set
measures paraphrase, not retrieval.

    python3 twostage.py "a lock so only one coroutine at a time runs this"
    python3 twostage.py --evaluate <needs.json>
"""

import json
import sys
from collections import defaultdict

import codex

DEPTH = 25          # reciprocal-rank weighting is stable across depth; count is not


def package_of(symbol):
    """The declaring package. Drops the member, then a type if one is left."""
    bits = symbol.split(".")[:-1]
    if bits and bits[-1][:1].isupper():
        bits = bits[:-1]
    return ".".join(bits)


def rank_packages(need, db, depth=DEPTH, coordinates=None):
    """Packages ranked by their members' hits, weighted by reciprocal rank.

    Reciprocal rank rather than a count of hitting members: counting is
    depth-sensitive and reintroduces the size bias this is meant to remove,
    because the largest package collects the most hits in a deep result set.
    Measured at 4 of 5 then 2 of 5 as depth went 10 to 25, where reciprocal
    rank held at 4 of 5. Dividing by package size instead over-corrects and
    lets a tiny package win on one weak hit.
    """
    scores = defaultdict(float)
    for position, row in enumerate(codex.search(need, db, limit=depth, coordinates=coordinates)):
        scores[package_of(row[0])] += 1.0 / (position + 1)
    return sorted(scores.items(), key=lambda kv: -kv[1])


def evaluate(needs, db):
    """Both arms over a needs file, so the comparison is one run rather than two."""
    member_at1 = member_at10 = stage_at1 = stage_at3 = 0
    for need in needs:
        target = need["target"]
        rows = codex.search(need["query"], db, limit=DEPTH)
        symbols = [r[0] for r in rows]
        member_at1 += symbols[:1] == [target]
        member_at10 += target in symbols[:10]
        ranked = [p for p, _ in rank_packages(need["query"], db)]
        wanted = package_of(target)
        stage_at1 += ranked[:1] == [wanted]
        stage_at3 += wanted in ranked[:3]
    n = len(needs)
    print(f"                    member-level   two-stage")
    print(f"  rank-1            {member_at1} of {n}        {stage_at1} of {n}")
    print(f"  ten / three       {member_at10} of {n}        {stage_at3} of {n}")


if __name__ == "__main__":
    store = codex.store()
    if len(sys.argv) > 2 and sys.argv[1] == "--evaluate":
        with open(sys.argv[2], encoding="utf-8") as handle:
            evaluate(json.load(handle), store)
    elif len(sys.argv) > 1:
        for package, score in rank_packages(" ".join(sys.argv[1:]), store)[:5]:
            print(f"  {score:6.3f}  {package}")
    else:
        print(__doc__.strip().splitlines()[0])
        print('\nusage: twostage.py "<need>" | twostage.py --evaluate <needs.json>')
