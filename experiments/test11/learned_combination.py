#!/usr/bin/env python3
"""
test11 — do detector signals compose when they are *weighted* rather than OR-ed?

`test8` stacked the linters and found it bought nothing: the union caught exactly what the best
single tool caught, and requiring two to agree was worse than either alone. The conclusion drawn
was that the detectors are nested rather than independent.

That conclusion is about **boolean** composition. It says nothing about whether the same signals
carry information when a model is allowed to weight them - which is a different question and the
one a "just use basic ML" proposal actually asks.

WHY A LINEAR MODEL, DELIBERATELY. A stronger model would answer the headline question better and
the important question worse. The finding here is not the score; it is *which features produce the
score*, and that is only legible if the model is a weighted sum over named features. An embedding
probe would report the same separation and give no way to see what it keyed on. For this question
inspectability is the requirement, not a convenience.

NO DEPENDENCIES. The project has neither sklearn nor numpy installed, and logistic regression over
68 binary features and 141 rows does not need them.

INPUT is `test8`'s scored output rather than the corpus itself: each case is already reduced to the
set of (tool, rule) pairs that fired on it, with AgentTrap's own benign/malicious label. That means
this harness runs offline and never touches the attack code - see ../../SAFETY.md.

Run:  python3 learned_combination.py
"""
import json
import math
import os
import random

HERE = os.path.dirname(os.path.abspath(__file__))
SCORED = os.path.join(HERE, "..", "test8", "results-agenttrap-lint.json")
SEED = 17
FOLDS = 5

D = json.load(open(SCORED))
FEATS = sorted({f"{t}:{c}" for r in D for t, cs in r["fired"].items() for c in cs})
IDX = {f: i for i, f in enumerate(FEATS)}
Y = [0 if r["benign"] else 1 for r in D]          # 1 = malicious


def vector(record):
    v = [0.0] * len(FEATS)
    for tool, codes in record["fired"].items():
        for code in codes:
            v[IDX[f"{tool}:{code}"]] = 1.0
    return v


X = [vector(r) for r in D]


def train(rows, labels, epochs=400, lr=0.5, l2=0.02):
    """Batch gradient descent on L2-regularised logistic regression. Deterministic."""
    n = len(rows[0])
    w = [0.0] * n
    b = 0.0
    for _ in range(epochs):
        gw = [0.0] * n
        gb = 0.0
        for x, y in zip(rows, labels):
            z = b + sum(wi * xi for wi, xi in zip(w, x))
            p = 1 / (1 + math.exp(-max(-30, min(30, z))))
            err = p - y
            for i, xi in enumerate(x):
                if xi:
                    gw[i] += err * xi
            gb += err
        m = len(rows)
        for i in range(n):
            w[i] -= lr * (gw[i] / m + l2 * w[i])
        b -= lr * (gb / m)
    return w, b


def predict(w, b, x):
    return 1 if b + sum(wi * xi for wi, xi in zip(w, x)) > 0 else 0


def stratified_folds(k=FOLDS):
    """Same split every run. The class balance is 91/50, so stratifying is not optional."""
    rng = random.Random(SEED)
    folds = [[] for _ in range(k)]
    for label in (0, 1):
        idxs = [i for i, y in enumerate(Y) if y == label]
        rng.shuffle(idxs)
        for j, i in enumerate(idxs):
            folds[j % k].append(i)
    return folds


def out_of_fold(columns):
    """Predictions made only by a model that never saw the row. Anything else is memorisation."""
    rows = [[x[i] for i in columns] for x in X]
    pred = [None] * len(Y)
    folds = stratified_folds()
    for k in range(FOLDS):
        test = set(folds[k])
        tr = [i for i in range(len(Y)) if i not in test]
        w, b = train([rows[i] for i in tr], [Y[i] for i in tr])
        for i in test:
            pred[i] = predict(w, b, rows[i])
    return pred


def score(pred):
    c = sum(1 for p, y in zip(pred, Y) if y == 1 and p == 1)
    m = sum(1 for p, y in zip(pred, Y) if y == 1 and p == 0)
    f = sum(1 for p, y in zip(pred, Y) if y == 0 and p == 1)
    l = sum(1 for p, y in zip(pred, Y) if y == 0 and p == 0)
    return c, m, f, l


def is_security(feature):
    """ruff's S-rules (bandit-derived) and bandit's own B-rules, and nothing that merely starts
    with the letter.

    The first version of this tested `startswith("S")`, which swallowed `SIM*` - flake8-simplify,
    pure style - and put it in the security bucket. Both families are letter-then-digits, so the
    digits are the discriminator. This matters because the ablation below is the finding.
    """
    code = feature.split(":", 1)[1]
    return code[:1] in ("S", "B") and code[1:].isdigit()


def row(name, pred, note=""):
    c, m, f, l = score(pred)
    sep = c / (c + m) - f / (f + l) if (c + m) and (f + l) else 0.0
    # A model that predicts one class for everything scores 0.0 separation and must be labelled,
    # or a reader takes it for a measurement rather than a failure to fit.
    degenerate = " <- predicts one class for everything; did not fit" if (c + f == len(Y)
                                                                          or m + l == len(Y)) else ""
    print(f"{name:<40}{c:>8}{m:>8}{f:>13}{l:>12}{sep:>11.1%}  {note}{degenerate}")


def main():
    print(f"# {len(D)} cases ({sum(Y)} malicious / {len(Y)-sum(Y)} benign), "
          f"{len(FEATS)} (tool, rule) features\n")
    print("**Key** — `caught`/`missed` are the malicious cases; `false alarm`/`left alone` are the")
    print("benign ones. `separation` is the catch rate minus the false-alarm rate: 0% means the")
    print("model is no better than guessing the majority class.\n")

    print(f"{'model':<40}{'caught':>8}{'missed':>8}{'false alarm':>13}{'left alone':>12}"
          f"{'separation':>12}")

    for tool in ["ruff default", "ruff naming N", "ruff security S", "bandit"]:
        row(f"one detector: {tool}", [1 if r["fired"].get(tool) else 0 for r in D])
    row("all four, OR-ed (test8's stacking)", [1 if any(r["fired"].values()) else 0 for r in D])

    allcols = list(range(len(FEATS)))
    row("LEARNED weighting, 5-fold OOF", out_of_fold(allcols))

    print("\n# which features carry it — the ablation is the point\n")
    print(f"{'feature subset':<40}{'caught':>8}{'missed':>8}{'false alarm':>13}{'left alone':>12}"
          f"{'separation':>12}")
    sec = [i for i, f in enumerate(FEATS) if is_security(f)]
    sty = [i for i, f in enumerate(FEATS) if not is_security(f)]
    row("security-intent codes only", out_of_fold(sec), f"({len(sec)} features) ")
    row("style / formatting codes only", out_of_fold(sty), f"({len(sty)} features) ")

    print("\n# what the model actually learned (fit on everything, for inspection only)\n")
    w, b = train(X, Y)
    ranked = sorted(zip(FEATS, w), key=lambda kv: -kv[1])
    print("  toward MALICIOUS")
    for f, wt in ranked[:8]:
        print(f"    {wt:+.3f}  {f}{'   [security]' if is_security(f) else '   [style]'}")
    print("  toward BENIGN")
    for f, wt in ranked[-5:]:
        print(f"    {wt:+.3f}  {f}{'   [security]' if is_security(f) else '   [style]'}")


if __name__ == "__main__":
    main()
