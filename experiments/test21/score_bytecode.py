#!/usr/bin/env python3
"""
Does `test18`'s identifier classifier hold up on names recovered from compiled classes?

TWO POPULATIONS, AND THE SECOND IS THE POINT.

  author-written     class, method and field names a person chose. The same kind of thing the
                     classifier was trained and calibrated on, arriving by a different route.
  compiler-generated `lambda$next$0`, `access$000`, `this$0`, `$$serializer`, bridge methods.
                     Nobody wrote these. The classifier has never seen one, and if they score high
                     then the false-positive rate on binary-only libraries is worse than the
                     published figure and nothing would have said so.

The model is trained exactly as `test18` trains it — on identifiers from the SOURCE corpus, with
`test15`'s generated payloads as positives, and the threshold set to `test10`'s 0.221% on source
identifiers. Nothing is retuned for bytecode; the question is whether the published operating point
survives the change of source.

Run: uv run --with scikit-learn --with scipy python score_bytecode.py
"""
import json
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "test18"))
sys.path.insert(0, os.path.join(HERE, "..", "test15"))
import classify as C
from identifier_tokens import TOKENIZERS


def main():
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    blob = json.load(open(os.path.join(HERE, "bytecode-identifiers.json")))
    src_neg = C.negatives(None)
    seen = set(src_neg)
    author = [i for i in blob["identifiers"] if i not in seen]
    synth = [i for i in blob["synthetic"] if i not in seen]
    ptr = C.positives(held_out=None)
    pte = C.positives(held_out=C._reserved_words())

    print(f"# {len(blob['libraries'])} artifacts read from bytecode, "
          f"{len(blob['binary_only'])} with no sources jar at all")
    print(f"# {len(author)} author-written identifiers and {len(synth)} compiler-generated ones,")
    print(f"#   none of which appear in the {len(src_neg)} source identifiers used for training\n")
    print("WHAT IS BEING COUNTED")
    print("  Each row is a tokenisation. The threshold is set once, on SOURCE identifiers, to the")
    print("  0.221% that test10 published as the cost of its whole rule catalogue. Nothing is")
    print("  retuned for bytecode.")
    print("  `author` and `synthetic` are real identifiers with nothing wrong with them, so a flag")
    print("  there is a MISTAKE — lower is better. `payload` is test15's generated attack forms,")
    print("  where a flag is CORRECT — higher is better.\n")

    print(f"  {'tokenisation':<16}{'author-written':>26}{'compiler-generated':>26}{'payloads':>18}")
    for name, fn in TOKENIZERS.items():
        if name == "whole":
            continue
        vec = TfidfVectorizer(analyzer=fn, min_df=2, sublinear_tf=True)
        clf = LogisticRegression(max_iter=2000, class_weight="balanced", C=4.0)
        clf.fit(vec.fit_transform(ptr + src_neg), [1] * len(ptr) + [0] * len(src_neg))
        cut = np.quantile(clf.decision_function(vec.transform(src_neg)), 1 - C.BAR)

        def rate(xs):
            if not xs:
                return 0, 0
            k = int((clf.decision_function(vec.transform(xs)) > cut).sum())
            return k, len(xs)

        a_k, a_n = rate(author)
        s_k, s_n = rate(synth)
        p_k, p_n = rate(pte)
        print(f"  {name:<16}{a_k:>10} of {a_n:<7} {a_k/a_n:>5.2%}"
              f"{s_k:>10} of {s_n:<7} {s_k/s_n:>5.2%}"
              f"{p_k:>8} of {p_n:<4} {p_k/p_n:>4.0%}")

    # What the worst offenders look like, because a rate says nothing about its character.
    fn = TOKENIZERS["words+chars"]
    vec = TfidfVectorizer(analyzer=fn, min_df=2, sublinear_tf=True)
    clf = LogisticRegression(max_iter=2000, class_weight="balanced", C=4.0)
    clf.fit(vec.fit_transform(ptr + src_neg), [1] * len(ptr) + [0] * len(src_neg))
    for label, xs in (("author-written", author), ("compiler-generated", synth)):
        if not xs:
            continue
        sc = clf.decision_function(vec.transform(xs))
        top = np.argsort(sc)[-12:][::-1]
        print(f"\n  highest-scoring {label}: " + ", ".join(xs[i] for i in top))
    return 0


if __name__ == "__main__":
    sys.exit(main())
