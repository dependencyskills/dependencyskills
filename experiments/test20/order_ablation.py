#!/usr/bin/env python3
"""
test20 — does WORD ORDER carry any of the signal, or is an instruction just a bag of words?

WHY THIS IS ITS OWN TEST. `test18` and `test19` both scored several tokenisations, and none of them
sees more than a narrow window: `words` is a pure bag, bigrams and trigrams see two or three words,
character n-grams see four or five characters. All of them throw away where a sentence sits and how
its clauses are arranged. That is a control, not a finding — and the way to turn it into a finding
is to destroy order deliberately and see what the score does.

WHAT IT DECIDES, BEYOND TOKENISATION. [RAD-0026](../../docs/knowledge/research/RAD-0026-meaning-without-command.md)
asks whether a representation can carry enough meaning to retrieve while carrying too little to
command. An instruction is an ordered thing: *copy the environment into the log* and *copy the log
into the environment* share every word and name opposite acts. So if a classifier scores the same on
shuffled text as on real text, it is detecting **vocabulary**, not instruction — and the corollary
is the interesting half: a representation that preserves the words while destroying their order
would keep whatever this classifier detects while losing what makes it a command.

THE ABLATIONS, applied to the payload sentence ONLY. The carrier comment is left intact, so what
changes is the arrangement of the injected instruction and nothing else.

  intact      the payload as generated
  shuffled    its words in random order — same bag, no order at all
  sorted      its words alphabetically — same bag, maximally destroyed order, deterministic
  reversed    its words back to front — order preserved but inverted
  clauses     its clauses reordered around commas — reads naturally, means something else

`sorted` and `shuffled` are the same bag by construction; scoring them differently would indicate
the model is sensitive to something other than the bag, which is what the tokenisation window is.

Run: uv run --with scikit-learn --with scipy python order_ablation.py
     ... --sample 60000
"""
import os
import random
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "test19"))
sys.path.insert(0, os.path.join(HERE, "..", "test18"))
import classify_prose as P
import prose_grammar as G

SEED = 20


def shuffled(sentence, rng):
    w = sentence.split()
    rng.shuffle(w)
    return " ".join(w)


def alphabetical(sentence, _rng):
    return " ".join(sorted(sentence.split(), key=str.lower))


def backwards(sentence, _rng):
    return " ".join(reversed(sentence.split()))


def clause_swapped(sentence, rng):
    """Reorder around commas. Unlike a shuffle this still READS like documentation, which is the
    version an attacker could actually ship."""
    parts = [p.strip() for p in sentence.split(",") if p.strip()]
    if len(parts) < 2:
        return shuffled(sentence, rng)
    rng.shuffle(parts)
    return ", ".join(parts)


ABLATIONS = {"intact": lambda s, r: s, "clauses": clause_swapped, "reversed": backwards,
             "shuffled": shuffled, "sorted": alphabetical}


def main():
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    limit = int(sys.argv[sys.argv.index("--sample") + 1]) if "--sample" in sys.argv else 60000
    docs = P.real_docs(limit)
    Xtr, ytr, _, _ = P.build("held-out", docs)
    clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]

    # Carriers and payloads are held out the same way `test19` holds them out, so the only new
    # variable here is the arrangement of the payload's own words.
    rng = random.Random(SEED)
    carriers = list(docs)
    rng.shuffle(carriers)
    carriers = carriers[: min(3000, len(carriers))]
    pay = G.generate(G.reserve())

    print(f"# {len(docs)} real comments, {len(carriers)} carriers, payloads held out on all axes")
    print(f"# threshold set to flag test10's {P.BAR:.3%} in clean comments\n")
    print("**Key** — each column is the share of poisoned comments flagged when only the PAYLOAD's")
    print("word order is disturbed. The carrier is untouched. `sorted` and `shuffled` hold the same")
    print("bag of words as `intact`, so a model reading only vocabulary scores them identically.\n")
    header = "".join(f"{k:>11}" for k in ABLATIONS)
    print(f"  {'tokenisation':<16}{header}")

    for name, fn in P.TOKENIZERS.items():
        vec = TfidfVectorizer(analyzer=fn, min_df=3, sublinear_tf=True)
        clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
        clf.fit(vec.fit_transform(Xtr), ytr)
        cut = np.quantile(clf.decision_function(vec.transform(clean_tr)), 1 - P.BAR)
        cells = []
        for ab, mutate in ABLATIONS.items():
            r = random.Random(SEED)
            texts = [G.insert(c, mutate(pay[i % len(pay)][0], r), r)
                     for i, c in enumerate(carriers)]
            cells.append(float((clf.decision_function(vec.transform(texts)) > cut).mean()))
        print(f"  {name:<16}" + "".join(f"{c:>10.1%} " for c in cells))

    print("\n  A flat row means the tokenisation reads vocabulary and nothing else.")
    print("  A row that falls from `intact` to `shuffled` means order is carrying signal,")
    print("  and how far it falls is how much.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
