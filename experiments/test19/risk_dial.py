#!/usr/bin/env python3
"""
The classifier as a configuration dial rather than a verdict.

WHAT THIS IS FOR. A single threshold forces one policy on everybody. The multi-class model assigns
a **register** — the shape of documentation the instruction is hiding in — and a register is
something an operator can hold an opinion about. A project that cannot tolerate losing entries sets
the dial low and accepts that subtle prose gets through; one that would rather lose a few comments
than miss anything sets it high. Both are defensible and they are not the same project.

REJECTED IS NOT DELETED. A comment above the threshold degrades to signature-only, which RAD-0040
measured as findable if the entry keeps a retrieval key it never shows. It is also worth logging:
the operator can look at what was dropped, which is how a dial gets tuned and how the `suspect`
label in the corpus gets its first real occupants.

THE POINT OF THE TABLE. At each cost setting, what fraction of each register is caught. A register
that stays low across the whole dial is not a tuning problem, it is a blind spot, and an operator
should know which ones those are before trusting a setting.

Run: uv run --with scikit-learn --with scipy python risk_dial.py
"""
import json
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "test18"))
import classify_prose as P
import prose_grammar as G
import multiclass as M

SEED = 26
# Cost is the share of CLEAN comments rejected. The names are the operator's language, not a
# claim about safety — a "strict" setting is strict about prose, not about outcomes.
DIAL = [("permissive", 0.0005), ("cautious", 0.00221), ("strict", 0.01), ("paranoid", 0.05)]


def main():
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    rng = random.Random(SEED)
    docs = P.real_docs(None)
    Xtr, ytr, Xte, yte, carriers = M.build_multi(docs)
    vec = TfidfVectorizer(analyzer=P.TOKENIZERS["words+chars"], min_df=3, sublinear_tf=True)
    clf = LogisticRegression(max_iter=4000, class_weight="balanced", C=4.0)
    clf.fit(vec.fit_transform(Xtr), ytr)

    classes = list(clf.classes_)
    ci = classes.index("clean")
    proba = clf.predict_proba(vec.transform(Xte))
    risk = 1.0 - proba[:, ci]
    yte = np.array(yte)
    clean_risk = risk[yte == "clean"]

    registers = sorted(set(yte) - {"clean"})
    poisoned = yte != "clean"
    n_clean = int((yte == "clean").sum())
    n_poisoned = int(poisoned.sum())
    per_reg = {r: int((yte == r).sum()) for r in registers}

    print(f"# {len(Xte)} held-out comments: {n_clean} clean, {n_poisoned} poisoned "
          f"({per_reg[registers[0]]} per register)\n")
    print("WHAT IS BEING COUNTED")
    print("  Every comment in this table is a real doc comment harvested from a real library.")
    print("  A POISONED one has had a single generated payload sentence inserted into it.")
    print("  A CLEAN one has not been touched.")
    print("  Each column is a threshold setting. A comment scoring above it is REJECTED, which")
    print("  means its entry degrades to signature-only — it is not deleted, and it can be logged.")
    print()
    print("  Each cell is: HOW MANY OF THAT ROW'S COMMENTS WERE REJECTED, and that as a share of")
    print("  the row's `n`. For a register row, rejecting is CORRECT and higher is better. For the")
    print("  `clean` row, rejecting is a MISTAKE and lower is better — that row is the price the")
    print("  operator pays for the column they choose.\n")

    head = "".join(f"{n:>16}" for n, _ in DIAL)
    print(f"  {'register':<15}{'n':>8}{head}")
    cuts = {n: float(np.quantile(clean_risk, 1 - c)) for n, c in DIAL}

    def cells(mask, denom):
        out = ""
        for n, _ in DIAL:
            k = int((risk[mask] > cuts[n]).sum())
            out += f"{k:>8} {k / denom:>6.0%} "
        return out

    for reg in registers:
        print(f"  {reg:<15}{per_reg[reg]:>8}" + cells(yte == reg, per_reg[reg]))
    print(f"  {'-' * (23 + 16 * len(DIAL))}")
    print(f"  {'any register':<15}{n_poisoned:>8}" + cells(poisoned, n_poisoned))
    print(f"  {'clean (the cost)':<15}{n_clean:>8}" + cells(yte == "clean", n_clean))
    print(f"\n  {'lost per 10,000 entries':<23}"
          + "".join(f"{round(c * 10000):>16}" for _, c in DIAL))

    # test9's real payloads across the dial — the only positives nobody generated.
    n_car = 800
    print(f"\n## test9's three real payloads, each inserted into {n_car} real comments\n")
    print(f"  {'payload':<15}{'n':>8}{head}")
    for text, framing, _k, _fm in G.real_payloads():
        carried = [G.insert(c, text, rng) for c in carriers[:n_car]]
        pr = 1.0 - clf.predict_proba(vec.transform(carried))[:, ci]
        row = ""
        for n, _ in DIAL:
            k = int((pr > cuts[n]).sum())
            row += f"{k:>8} {k / n_car:>6.0%} "
        print(f"  {framing:<15}{n_car:>8}{row}")

    json.dump({n: cuts[n] for n, _ in DIAL}, open(os.path.join(HERE, "dial.json"), "w"), indent=1)
    print(f"\n  thresholds written to dial.json")
    return 0


if __name__ == "__main__":
    sys.exit(main())
