#!/usr/bin/env python3
"""
Point the classifier at the corpus and see what it says about prose nobody has ever checked.

WHY. Every one of the 537,480 harvested doc comments carries `presumed_benign`, and the label means
exactly what it says: harvested from a real registry, unaudited. RAD-0036 records why that is the
weak point — the negative class in any classifier is written by whoever can publish a package. The
classifier has been trained against that class and has never been asked what it thinks of it.

THREE OUTCOMES, ALL WORTH HAVING.

  1. It flags something real. A payload in a published library nobody planted — the first positive
     this project would not have written itself.
  2. It flags only false positives. Then they are characterised over 537,480 comments instead of
     4,995, which is a far better estimate than anything in `report.py`.
  3. It flags ambiguous prose. Then `suspect` — a label the corpus schema has carried since it was
     built, with zero rows in it — finally has occupants, and they were found rather than assumed.

TRAINED WITHOUT THE THING IT IS JUDGING. The model is trained on a *held-out half* of the corpus
and scores the other half, so it never learned the comments it is grading. Without that the exercise
would be circular: a comment used as a negative in training is a comment the model was told is fine.

NOTHING IS EXECUTED and no library is contacted. This reads text already on disk.

Run: uv run --with scikit-learn --with scipy python audit_corpus.py --top 40
"""
import os
import random
import re
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "test18"))
import classify_prose as P
import prose_grammar as G

SEED = 25
SENT = re.compile(r'(?<=[.!?])\s+')


def sentences(t):
    return [s for s in SENT.split(t) if len(s.split()) >= 4] or [t]


def main():
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    top_n = int(sys.argv[sys.argv.index("--top") + 1]) if "--top" in sys.argv else 40
    rng = random.Random(SEED)

    db = sqlite3.connect(P.DB)
    rows = [(r[0], r[1], r[2], r[3]) for r in db.execute(
        "SELECT library, symbol, doc_format, doc FROM entries WHERE label='presumed_benign'"
        " AND tags NOT LIKE '%,license-header,%'")]
    db.close()
    rows = [r for r in rows if len(r[3].split()) >= P.MIN_WORDS]
    rng.shuffle(rows)
    half = len(rows) // 2
    train_rows, audit_rows = rows[:half], rows[half:]
    print(f"# {len(rows)} unaudited comments — {len(train_rows)} to train on, "
          f"{len(audit_rows)} to grade\n")

    # Sentence-level, character n-grams: the strongest configuration measured in test19.
    train_docs = [r[3] for r in train_rows]
    Xtr, ytr, _, _ = P.build("held-out", train_docs)
    str_X, str_y = [], []
    for x, y in zip(Xtr, ytr):
        if y == 0:
            for s in sentences(x):
                str_X.append(s)
                str_y.append(0)
    gen = G.generate()
    rng.shuffle(gen)
    for s, _f, _k, _fm in gen[:len(str_X) // 8]:
        str_X.append(s)
        str_y.append(1)

    vec = TfidfVectorizer(analyzer=P.TOKENIZERS["char 4-5grams"], min_df=3, sublinear_tf=True)
    clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
    clf.fit(vec.fit_transform(str_X), str_y)
    print(f"# trained on {len(str_X)} sentences ({sum(str_y)} generated payloads)\n")

    # Score every sentence of every held-out comment; a comment takes its worst sentence.
    flat, spans = [], []
    for _lib, _sym, _fmt, doc in audit_rows:
        ss = sentences(doc)
        spans.append((len(flat), len(flat) + len(ss)))
        flat.extend(ss)
    print(f"# scoring {len(flat)} sentences …", flush=True)
    sc = np.concatenate([clf.decision_function(vec.transform(flat[i:i + 20000]))
                         for i in range(0, len(flat), 20000)])
    worst = np.array([sc[a:b].max() for a, b in spans])
    best_sent = [flat[a + int(sc[a:b].argmax())] for a, b in spans]

    # CALIBRATE ON COMMENTS, NOT SENTENCES. A comment takes the score of its worst sentence, so a
    # threshold set to flag 0.221% of SENTENCES flags far more comments — about 0.221% x the mean
    # sentence count, which on this corpus is ~2.7. The first run of this file made that mistake
    # and reported 0.694% as if it were a finding.
    held = [r[3] for r in train_rows[:40000]]
    hflat, hspans = [], []
    for doc in held:
        ss = sentences(doc)
        hspans.append((len(hflat), len(hflat) + len(ss)))
        hflat.extend(ss)
    hsc = np.concatenate([clf.decision_function(vec.transform(hflat[i:i + 20000]))
                          for i in range(0, len(hflat), 20000)])
    cut = np.quantile(np.array([hsc[a:b].max() for a, b in hspans]), 1 - P.BAR)
    flagged = worst > cut
    print(f"\n## {int(flagged.sum())} of {len(audit_rows)} unaudited comments flagged "
          f"({flagged.mean():.3%}) at test10's {P.BAR:.3%} operating point\n")

    order = np.argsort(worst)[::-1][:top_n]
    print(f"## the {top_n} highest-scoring, with the sentence that scored\n")
    for i in order:
        lib, sym, fmt, _doc = audit_rows[i]
        s = " ".join(best_sent[i].split())
        print(f"  {worst[i]:+6.2f}  {lib[:44]:<44} {(sym or '-')[:22]:<22} {s[:150]}")

    print(f"\n## score distribution over {len(audit_rows)} comments\n")
    for q in (0.5, 0.9, 0.99, 0.999, 0.9999, 1.0):
        print(f"  {q:>8.4%}  {np.quantile(worst, q):+.2f}")
    print(f"  threshold {cut:+.2f}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
