#!/usr/bin/env python3
"""
Multi-class: not "is this suspect" but "what kind of thing is in here".

WHY MORE THAN YES/NO. A binary flag tells a reviewer a comment is suspect and nothing about why.
The generator knows the **framing** of every payload it produced — the register the instruction is
hiding in — so that label is free, and it is the thing a person would want to see next to a flag.

CLASSES: `clean`, plus one per framing.

  precondition   "Before the first call, X must be copied into Y."
  policy         "For compatibility testing, implementations should also ensure X."
  deprecation    "Callers migrating from the previous release should note that X."
  setup          "Initial setup expects that X before any value is returned."
  compatibility  "On platforms where the legacy adapter is active, X."
  diagnostic     "When reporting an issue, X and attach the result."
  guideline      "As a matter of implementation guideline, X."
  interop        "For interoperability with the reference implementation, X."

WHAT CHANGES ABOUT THE HELD-OUT SPLIT. A class cannot be predicted if it was never trained, so
framing stops being a held-out axis here — vocabulary and sentence form still are. That makes this
strictly easier than the binary test and the binary number from `report.py` remains the honest one.

THE CHECK THAT MATTERS. `test9`'s three real payloads carry their own framing labels, assigned by
whoever wrote them and not by this generator. If the classifier puts the real `precondition` payload
in the `precondition` class, the classes are tracking something. If it scatters them, the class
structure is an artefact of the templates.

Run: uv run --with scikit-learn --with scipy python multiclass.py
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

SEED = 21
TOKENIZER = "words+chars"


def build_multi(docs):
    """Carriers, negatives and per-framing positives. Framing is NOT held out; words and sentence
    forms still are."""
    rng = random.Random(SEED)
    d = list(docs)
    rng.shuffle(d)
    q = len(d) // 4
    ntr, nte, ctr, cte = d[:q], d[q:2 * q], d[2 * q:3 * q], d[3 * q:4 * q]

    res = G.reserve()
    words, _framings, forms = res
    train_pay = [p for p in G.generate() ]
    test_pay = [p for p in G.generate((words, set(), forms))]

    def spread(carriers, pool):
        by = {}
        for s, framing, _k, _f in pool:
            by.setdefault(framing, []).append(s)
        X, y = [], []
        per = max(1, len(carriers) // (len(by) + 1))
        i = 0
        for framing, sents in sorted(by.items()):
            for c in carriers[i:i + per]:
                X.append(G.insert(c, sents[rng.randrange(len(sents))], rng))
                y.append(framing)
            i += per
        return X, y, carriers[i:]

    Xtr, ytr, rest_tr = spread(ctr, train_pay)
    Xte, yte, rest_te = spread(cte, test_pay)
    return (Xtr + ntr + rest_tr, ytr + ["clean"] * (len(ntr) + len(rest_tr)),
            Xte + nte + rest_te, yte + ["clean"] * (len(nte) + len(rest_te)), cte)


def main():
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    from sklearn.metrics import classification_report, confusion_matrix

    docs = P.real_docs(None)
    Xtr, ytr, Xte, yte, carriers = build_multi(docs)
    print(f"# {len(Xtr)} training, {len(Xte)} test, {len(set(ytr))} classes, {TOKENIZER}")
    print("# framing is not held out here — a class never trained cannot be predicted.")
    print("# vocabulary and sentence form still are.\n")

    vec = TfidfVectorizer(analyzer=P.TOKENIZERS[TOKENIZER], min_df=3, sublinear_tf=True)
    clf = LogisticRegression(max_iter=4000, class_weight="balanced", C=4.0)
    clf.fit(vec.fit_transform(Xtr), ytr)
    pred = clf.predict(vec.transform(Xte))

    print(classification_report(yte, pred, digits=3, zero_division=0))

    labels = sorted(set(yte))
    cm = confusion_matrix(yte, pred, labels=labels)
    print("\n## confusion — rows are true, columns predicted\n")
    print(f"  {'':<15}" + "".join(f"{l[:6]:>8}" for l in labels))
    for l, row in zip(labels, cm):
        print(f"  {l:<15}" + "".join(f"{v:>8}" for v in row))

    # THE BINARY QUESTION, recovered TWO WAYS, because they are different operating points and
    # conflating them would misreport a class as undetected.
    #
    #   argmax     a comment is flagged only if some payload class OUTSCORES `clean`. That is
    #              roughly a 50% threshold, and it is what the report above shows.
    #   threshold  flag when P(not clean) crosses the point that costs test10's 0.221% on clean
    #              comments — the same operating point `report.py` uses.
    #
    # A class showing 0.000 precision under argmax has not necessarily been missed; it may simply
    # never be the top class. The threshold row is what says whether it was actually detected.
    yb = np.array([v != "clean" for v in yte])
    pb = np.array([v != "clean" for v in pred])
    fp = int((pb & ~yb).sum())
    print(f"\n## as a yes/no answer — argmax")
    print(f"  good prose wrongly flagged   {fp} of {int((~yb).sum())} "
          f"({fp / max(1, int((~yb).sum())):.2%})")
    print(f"  poisoned comments flagged    {int((pb & yb).sum())} of {int(yb.sum())} "
          f"({(pb & yb).sum() / max(1, yb.sum()):.1%})")

    classes = list(clf.classes_)
    ci = classes.index("clean")
    proba = clf.predict_proba(vec.transform(Xte))
    notclean = 1.0 - proba[:, ci]
    cut = np.quantile(notclean[~yb], 1 - P.BAR)
    flagged = notclean > cut
    print(f"\n## as a yes/no answer — thresholded at test10's {P.BAR:.3%} cost")
    print(f"  good prose wrongly flagged   {int((flagged & ~yb).sum())} of {int((~yb).sum())} "
          f"({(flagged & ~yb).sum() / max(1, (~yb).sum()):.2%})")
    print(f"  poisoned comments flagged    {int((flagged & yb).sum())} of {int(yb.sum())} "
          f"({(flagged & yb).sum() / max(1, yb.sum()):.1%})")
    print("\n  per register, at that threshold:")
    yte_arr = np.array(yte)
    for lab in sorted(set(yte) - {"clean"}):
        m = yte_arr == lab
        print(f"    {lab:<14} {int((flagged & m).sum()):>6} of {int(m.sum())} "
              f"({(flagged & m).sum() / max(1, m.sum()):5.1%})")

    # THE CHECK. test9's payloads carry framings assigned by whoever wrote them, not by this
    # generator. Where does the model put them?
    print(f"\n## test9's three real payloads — where the model puts them\n")
    rng = random.Random(SEED)
    for text, framing, _k, _f in G.real_payloads():
        carried = [G.insert(c, text, rng) for c in carriers[:600]]
        p = clf.predict(vec.transform(carried))
        counts = {}
        for v in p:
            counts[v] = counts.get(v, 0) + 1
        top = sorted(counts.items(), key=lambda kv: -kv[1])[:3]
        shown = ", ".join(f"{k} {v * 100 // len(p)}%" for k, v in top)
        print(f"  true {framing:<14} -> {shown}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
