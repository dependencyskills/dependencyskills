#!/usr/bin/env python3
"""
Variations on the prose classifier. Four of them share a training setup, so they share a file.

  --sentences   classify each SENTENCE, flag the comment if any sentence fires
  --curve       the whole cost/catch curve rather than one operating point
  --ablate      remove the attack vocabulary from training entirely
  --cross       train on one ecosystem's doc convention, test on another

WHY `--ablate` MATTERS MOST. Everything measured so far could be a keyword list with extra steps.
`env`, `secret`, `token`, `log` and their neighbours are both the generator's vocabulary and the
obvious thing a linear model would latch onto. Deleting those terms from the feature space asks
whether anything else was ever being used. A collapse there would qualify every other number in
`test19` and `test18`, so it is worth knowing before the rest.

NO NEW RUNTIME. TF-IDF and a linear model are a term-frequency table and a dot product, which is
what Lucene already computes. Nothing here commits the pipeline to anything it does not have.

Run: uv run --with scikit-learn --with scipy python variations.py --ablate
"""
import json
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "test18"))
sys.path.insert(0, os.path.join(HERE, "..", "test15"))
import classify_prose as P
import prose_grammar as G

SEED = 22
SENT = re.compile(r'(?<=[.!?])\s+')


def fresh():
    blob = json.load(open(os.path.join(HERE, "..", "test18", "fresh-identifiers.json")))
    docs = [d["doc"] for d in blob["docs"] if len(d["doc"].split()) >= P.MIN_WORDS]
    half = len(docs) // 2
    return docs[:half], docs[half:], blob["packages"]


def train(Xtr, ytr, analyzer, C=4.0):
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    vec = TfidfVectorizer(analyzer=analyzer, min_df=3, sublinear_tf=True)
    clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=C)
    clf.fit(vec.fit_transform(Xtr), ytr)
    return vec, clf


def cutoff(vec, clf, clean, bar=P.BAR):
    import numpy as np
    return np.quantile(clf.decision_function(vec.transform(clean)), 1 - bar)


def known_bad(vec, clf, cut, carriers, rng):
    import numpy as np
    out = {}
    for text, framing, _k, _f in G.real_payloads():
        carried = [G.insert(c, text, rng) for c in carriers]
        out[framing] = float((clf.decision_function(vec.transform(carried)) > cut).mean())
    return out


# --------------------------------------------------------------------------- 1. per sentence
def run_sentences(docs):
    """Score each sentence; a comment is flagged if any of its sentences is.

    The payload is ONE sentence inside a comment averaging many, so at comment level its signal is
    diluted by everything around it. Scoring sentences also says WHICH sentence, which is more
    useful to a reviewer than a label on the whole comment.
    """
    import numpy as np
    rng = random.Random(SEED)
    Xtr, ytr, _, _ = P.build("held-out", docs)
    clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]
    good, carriers, packages = fresh()
    pay = G.generate(G.reserve())

    # Sentence-level training set: every sentence of a clean comment is clean; the inserted
    # sentence is the only positive. That is a much harder and much more honest labelling than
    # calling every sentence of a poisoned comment positive.
    str_X, str_y = [], []
    for x, y in zip(Xtr, ytr):
        for s in SENT.split(x):
            if len(s.split()) >= 4:
                str_X.append(s)
                str_y.append(0)
    for s, _f, _k, _fm in G.generate()[:len(str_X) // 8]:
        str_X.append(s)
        str_y.append(1)

    print(f"# sentence-level: {len(str_X)} sentences, {sum(str_y)} of them payloads\n")
    for name in ("words+bigrams", "char 4-5grams", "words+chars"):
        fn = P.TOKENIZERS[name]
        vec, clf = train(str_X, str_y, fn)
        # Calibrated on whole clean COMMENTS via their worst sentence, so the operating point
        # means the same thing as everywhere else in test19.
        def worst(texts):
            out = []
            for t in texts:
                sents = [s for s in SENT.split(t) if len(s.split()) >= 4] or [t]
                out.append(float(clf.decision_function(vec.transform(sents)).max()))
            return np.array(out)
        cut = np.quantile(worst(clean_tr[:4000]), 1 - P.BAR)
        fp = int((worst(good) > cut).sum())
        kb = {}
        for text, framing, _k, _f in G.real_payloads():
            kb[framing] = float((worst([G.insert(c, text, rng) for c in carriers[:1500]])
                                 > cut).mean())
        detail = "  ".join(f"{k} {v:4.0%}" for k, v in sorted(kb.items()))
        print(f"  {name:<16} good flagged {fp:>3} of {len(good)} ({fp/len(good):.2%})"
              f"   | known bad: {detail}")
    return 0


# --------------------------------------------------------------------------- 3. the curve
def run_curve(docs):
    import numpy as np
    rng = random.Random(SEED)
    Xtr, ytr, _, _ = P.build("held-out", docs)
    clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]
    good, carriers, _ = fresh()
    print("# cost/catch curve — one operating point hides whether a rate is a cliff or a plateau\n")
    # Pushed down until it breaks. The first run of this was flat at 100% from 0.05% to 5%, which
    # says the operating point everything else uses is nowhere near the edge — so the informative
    # range is below it, not above.
    bars = [0.000005, 0.00002, 0.00005, 0.0002, 0.0005, 0.00221, 0.01]
    def label(b):
        return f"{b*100:g}%"
    print(f"  {'tokenisation':<16}" + "".join(f"{label(b):>9}" for b in bars))
    for name in ("words+bigrams", "char 4-5grams", "words+chars"):
        vec, clf = train(Xtr, ytr, P.TOKENIZERS[name])
        gs = clf.decision_function(vec.transform(good))
        cells = []
        for b in bars:
            cut = np.quantile(clf.decision_function(vec.transform(clean_tr)), 1 - b)
            kb = known_bad(vec, clf, cut, carriers[:1200], random.Random(SEED))
            cells.append(sum(kb.values()) / len(kb))
        print(f"  {name:<16}" + "".join(f"{c:>9.0%}" for c in cells))
    print("\n  Each column is the mean catch over test9's three real payloads at that cost,")
    print("  where cost is the share of CLEAN TRAINING comments flagged.")
    return 0


# --------------------------------------------------------------------------- 6. ablate vocabulary
BANNED = set("""env environment config configuration secret secrets token tokens key keys
credential credentials password passwords profile settings session log logs logging debug
telemetry host hosts url urls endpoint sink trace audit report cache backup mirror""".split())


def run_ablate(docs):
    """Delete the attack vocabulary from the feature space and re-measure.

    If the classifier still works, it learned something structural. If it collapses, it is a
    keyword list with extra steps — which would be the single most important thing to know about
    every number in test18 and test19.
    """
    import numpy as np
    from identifier_tokens import words as split_words
    rng = random.Random(SEED)
    Xtr, ytr, _, _ = P.build("held-out", docs)
    clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]
    good, carriers, _ = fresh()

    def strip(text):
        return " ".join(w for w in (text or "").split()
                        if re.sub(r'\W', '', w).lower() not in BANNED)

    def ablated(analyzer):
        return lambda t: analyzer(strip(t))

    print(f"# vocabulary ablation — {len(BANNED)} terms removed from every text, both classes\n")
    for name in ("words+bigrams", "char 4-5grams", "words+chars"):
        fn = P.TOKENIZERS[name]
        for label, an in (("intact", fn), ("ablated", ablated(fn))):
            vec, clf = train(Xtr, ytr, an)
            cut = cutoff(vec, clf, clean_tr)
            fp = int((clf.decision_function(vec.transform(good)) > cut).sum())
            kb = known_bad(vec, clf, cut, carriers[:1500], random.Random(SEED))
            detail = "  ".join(f"{k} {v:4.0%}" for k, v in sorted(kb.items()))
            print(f"  {name:<16} {label:<8} good flagged {fp:>3} of {len(good)}"
                  f" ({fp/len(good):.2%})   | known bad: {detail}")
        print()
    return 0


# --------------------------------------------------------------------------- 5. cross-ecosystem
def run_cross():
    """Train on one doc convention, test on another. `doc_format` is in the corpus for this."""
    import numpy as np
    import sqlite3
    rng = random.Random(SEED)
    db = sqlite3.connect(P.DB)
    by = {}
    for fmt in ("javadoc", "kdoc", "jsdoc", "swift-markup"):
        rows = [r[0] for r in db.execute(
            "SELECT doc FROM entries WHERE doc_format=? AND label='presumed_benign'"
            " AND tags NOT LIKE '%,license-header,%'", (fmt,))]
        rows = [d for d in rows if len(d.split()) >= P.MIN_WORDS]
        random.Random(SEED).shuffle(rows)
        if len(rows) >= 4000:
            by[fmt] = rows[:40000]
    db.close()
    print(f"# doc conventions with enough material: "
          + ", ".join(f"{k} ({len(v)})" for k, v in by.items()) + "\n")
    print(f"  {'train on':<14}{'test on':<14}{'good flagged':>14}{'known bad (mean)':>19}")
    fn = P.TOKENIZERS["words+chars"]
    for src, sdocs in by.items():
        Xtr, ytr, _, _ = P.build("held-out", sdocs)
        clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]
        vec, clf = train(Xtr, ytr, fn)
        cut = cutoff(vec, clf, clean_tr)
        for dst, ddocs in by.items():
            good = ddocs[: min(6000, len(ddocs) // 2)]
            carriers = ddocs[len(ddocs) // 2:][:1200]
            fp = int((clf.decision_function(vec.transform(good)) > cut).sum())
            kb = known_bad(vec, clf, cut, carriers, random.Random(SEED))
            mark = "  <- same" if src == dst else ""
            print(f"  {src:<14}{dst:<14}{fp:>7} of {len(good):<5}"
                  f"{sum(kb.values())/len(kb):>18.0%}{mark}")
    return 0


def main():
    docs = P.real_docs(None)
    if "--sentences" in sys.argv:
        return run_sentences(docs)
    if "--curve" in sys.argv:
        return run_curve(docs)
    if "--ablate" in sys.argv:
        return run_ablate(docs)
    if "--cross" in sys.argv:
        return run_cross()
    print(__doc__)
    return 0


if __name__ == "__main__":
    sys.exit(main())
