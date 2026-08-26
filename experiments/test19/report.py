#!/usr/bin/env python3
"""
The two numbers, and nothing else.

  1. How often does the classifier catch KNOWN BAD prose?
  2. How often does it flag GOOD prose as bad?

Known bad is `test9`'s three measured payloads. Three. That is the whole set, and it is the reason
`prose_grammar.py` exists — but a generated payload is not known bad, so its catch rate is reported
separately and never added to the same total.

Good prose is real doc comments from 381 npm packages this machine had never downloaded.

Run: uv run --with scikit-learn --with scipy python report.py
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


def main():
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    docs = P.real_docs(None)
    Xtr, ytr, _, _ = P.build("held-out", docs)
    clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]

    blob = json.load(open(os.path.join(HERE, "..", "test18", "fresh-identifiers.json")))
    fresh = [d["doc"] for d in blob["docs"] if len(d["doc"].split()) >= P.MIN_WORDS]
    half = len(fresh) // 2
    good, carriers = fresh[:half], fresh[half:]

    print(f"# trained on {len(docs)} local doc comments")
    print(f"# tested on {len(blob['packages'])} npm packages never downloaded here\n")

    for name in ("words", "words+bigrams", "char 4-5grams", "words+chars"):
        fn = P.TOKENIZERS[name]
        vec = TfidfVectorizer(analyzer=fn, min_df=3, sublinear_tf=True)
        clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
        clf.fit(vec.fit_transform(Xtr), ytr)
        cut = np.quantile(clf.decision_function(vec.transform(clean_tr)), 1 - P.BAR)

        wrong = int((clf.decision_function(vec.transform(good)) > cut).sum())
        print(f"## {name}")
        print(f"   good prose wrongly flagged   {wrong} of {len(good)}   ({wrong/len(good):.2%})")
        rng = random.Random(20)
        for text, framing, _k, _f in G.real_payloads():
            carried = [G.insert(c, text, rng) for c in carriers]
            hit = int((clf.decision_function(vec.transform(carried)) > cut).sum())
            print(f"   known bad caught  {framing:<13} {hit} of {len(carriers)}"
                  f"   ({hit/len(carriers):.0%})")
        pay = G.generate(G.reserve())
        gen = [G.insert(c, pay[i % len(pay)][0], rng) for i, c in enumerate(carriers)]
        g = int((clf.decision_function(vec.transform(gen)) > cut).sum())
        print(f"   (generated payloads, not known bad: {g} of {len(carriers)} "
              f"= {g/len(carriers):.0%})\n")
    return 0


if __name__ == "__main__":
    sys.exit(main())
