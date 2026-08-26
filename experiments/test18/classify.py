#!/usr/bin/env python3
"""
test18 — can a straight ML classifier separate an exfiltration-shaped identifier from real code?

WHAT IS BEING ASKED. Every identifier control this project ships is a hand-written rule, and
`test10` published what the whole catalogue costs: **0.221%** of real identifiers flagged. That is
the bar. A classifier is only interesting if, at that same cost, it catches more than the rules do —
or if it catches things the rules structurally cannot, like `copyenvtolog`, which has no separator
for a word-counting rule to find.

WHERE THE DATA COMES FROM, AND WHAT THAT LIMITS.

  negative  460,803 distinct declared names from `experiments/corpus` — real identifiers from
            1,798 maven libraries, 1,959 npm packages and 23 SwiftPM repositories.
  positive  generated from `test15`'s grammar (verb x object x target, with and without a
            connector) — the vocabulary of published attacks, not invented here.

**The positive class is our own generator.** So the catch rate measures "can a model learn this
grammar", not "can it catch an attacker", and it must never be quoted as the latter — RAD-0036
records the same trap from the other side. The **false-positive rate is the honest half**: it is
measured against half a million real identifiers nobody wrote for this experiment, and it is the
number that has killed every previous approach.

TWO SPLITS, BECAUSE ONE OF THEM IS A LIE. Splitting the generated positives at random leaves the
same verbs and objects in train and test, so a model scores well by memorising `env` and `secret`.
The honest split holds out **whole grammar components** — entire verbs, objects and targets that
never appear in training — so a test positive is built from words the model has never seen in a
positive. Both are reported. The gap between them is the memorisation.

POSITIVES ARE camelCase ONLY. 10% of real declared names carry an underscore and 4.5% are all-caps,
so surface form is not a giveaway here — but generating positives in a form the negatives rarely
take would let a model win on punctuation. Holding form constant makes the result mean something.

THE THIRD TEST IS THE ONLY CLEAN ONE. `--fresh` scores the trained model against identifiers from
packages this machine has never downloaded (`fresh_sample.py`). Both other splits hold out rows
from the same caches the model trained on; this holds out the *code*. It is also a harder test than
it looks, because the training negatives are 68% Kotlin Multiplatform and the fresh sample is all
JavaScript — so a rate that survives it survived a change of ecosystem too.

Run: uv run --with scikit-learn --with scipy python classify.py
     ... --sample 80000     # fewer negatives, for a quick pass
     ... --fresh            # false-positive rate on never-downloaded packages
"""
import itertools
import os
import random
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "test15"))
from tokenizers import TOKENIZERS

DB = os.path.join(HERE, "..", "corpus", "corpus.db")
BAR = 0.00221                      # test10's whole catalogue, as a false-positive rate
SEED = 17


def grammar():
    from constraint_space import VERBS, OBJECTS, TARGETS, camel
    return VERBS, OBJECTS, TARGETS, camel


def positives(held_out=None):
    """Every verb/object/target combination, camelCase, with and without a connector.

    `held_out` is a (verbs, objects, targets) triple of words reserved for the test split; when
    given, only combinations using at least one reserved word are produced.
    """
    verbs, objects, targets, camel = grammar()
    out = []
    for v, o, t in itertools.product(verbs, objects, targets):
        reserved = held_out and (v in held_out[0] or o in held_out[1] or t in held_out[2])
        if held_out is not None and not reserved:
            continue
        if held_out is None and _reserved_words() and (
                v in _reserved_words()[0] or o in _reserved_words()[1]
                or t in _reserved_words()[2]):
            continue
        for conn in ([], ["to"]):
            out.append(camel([v, o] + conn + [t]))
    return sorted(set(out))


_RESERVED = None


def _reserved_words():
    """A third of each grammar axis, reserved for the honest split. Deterministic."""
    global _RESERVED
    if _RESERVED is None:
        verbs, objects, targets, _ = grammar()
        r = random.Random(SEED)
        def cut(xs):
            xs = sorted(xs)
            r.shuffle(xs)
            return set(xs[:max(1, len(xs) // 3)])
        _RESERVED = (cut(verbs), cut(objects), cut(targets))
    return _RESERVED


def negatives(limit=None):
    db = sqlite3.connect(DB)
    rows = [r[0] for r in db.execute("SELECT DISTINCT name FROM declared WHERE length(name) >= 3")]
    db.close()
    random.Random(SEED).shuffle(rows)
    return rows[:limit] if limit else rows


def evaluate(name, fn, Xtr, ytr, Xte, yte, show_features=False):
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    import numpy as np

    vec = TfidfVectorizer(analyzer=fn, min_df=2, sublinear_tf=True)
    A = vec.fit_transform(Xtr)
    clf = LogisticRegression(max_iter=2000, class_weight="balanced", C=4.0)
    clf.fit(A, ytr)
    scores = clf.decision_function(vec.transform(Xte))

    yte = np.asarray(yte)
    neg, pos = scores[yte == 0], scores[yte == 1]
    # THE COMPARABLE NUMBER. Pick the threshold that flags exactly `BAR` of real identifiers —
    # test10's published cost for its whole catalogue — and ask what it catches there. Comparing
    # accuracy or F1 to a rule set would be comparing nothing.
    cutoff = np.quantile(neg, 1 - BAR)
    caught = float((pos > cutoff).mean())
    fpr = float((neg > cutoff).mean())
    # And the other direction: what does catching 95% of them cost?
    cost95 = float((neg > np.quantile(pos, 0.05)).mean())

    print(f"  {name:<16} at {BAR:.3%} cost -> catches {caught:6.1%}   "
          f"| 95% catch costs {cost95:7.3%}   (n+ {len(pos)}, n- {len(neg)})")

    if show_features:
        names = np.array(vec.get_feature_names_out())
        top = np.argsort(clf.coef_[0])[-12:][::-1]
        print(f"    {'':<14} top weights: " + ", ".join(names[top][:12]))
    return caught, cost95


def run(split, Xneg, show_features=False):
    """`split` is 'random' or 'held-out vocabulary'."""
    import numpy as np
    r = random.Random(SEED)
    if split == "random":
        pos = positives(held_out=None) + positives(held_out=_reserved_words())
        pos = sorted(set(pos))
        r.shuffle(pos)
        cut = int(len(pos) * 0.7)
        ptr, pte = pos[:cut], pos[cut:]
    else:
        ptr, pte = positives(held_out=None), positives(held_out=_reserved_words())

    neg = list(Xneg)
    r.shuffle(neg)
    cut = int(len(neg) * 0.7)
    ntr, nte = neg[:cut], neg[cut:]

    Xtr, ytr = ptr + ntr, [1] * len(ptr) + [0] * len(ntr)
    Xte, yte = pte + nte, [1] * len(pte) + [0] * len(nte)
    print(f"\n## {split} split — train {len(ptr)}+/{len(ntr)}-, test {len(pte)}+/{len(nte)}-\n")
    for name, fn in TOKENIZERS.items():
        evaluate(name, fn, Xtr, ytr, Xte, yte, show_features=show_features)


def fresh_test(neg):
    """Train on everything local, then score identifiers from packages never downloaded here."""
    import json
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    path = os.path.join(HERE, "fresh-identifiers.json")
    if not os.path.exists(path):
        sys.exit("no fresh-identifiers.json — run fresh_sample.py first. A false-positive rate "
                 "quoted without it would be measured on the training caches.")
    blob = json.load(open(path))
    fresh, packages = blob["identifiers"], blob["packages"]
    # An identifier the model trained on is not a fresh observation, whoever published it.
    unseen = sorted(set(fresh) - set(neg))
    ptr = positives(held_out=None)
    pte = positives(held_out=_reserved_words())

    print(f"\n## fresh packages — {len(packages)} never downloaded here, "
          f"{len(fresh)} identifiers ({len(unseen)} not in the training set)\n")
    print("  The false-positive column is the honest one: real identifiers, real packages, and")
    print("  neither the model nor this project has seen them. Catch rate is still measured")
    print("  against our own generator and is still optimistic.\n")
    for name, fn in TOKENIZERS.items():
        vec = TfidfVectorizer(analyzer=fn, min_df=2, sublinear_tf=True)
        A = vec.fit_transform(ptr + neg)
        clf = LogisticRegression(max_iter=2000, class_weight="balanced", C=4.0)
        clf.fit(A, [1] * len(ptr) + [0] * len(neg))
        cutoff = np.quantile(clf.decision_function(vec.transform(neg)), 1 - BAR)
        f = clf.decision_function(vec.transform(unseen))
        caught = float((clf.decision_function(vec.transform(pte)) > cutoff).mean())
        flagged = f > cutoff
        worst = [unseen[i] for i in np.argsort(f)[-5:][::-1]]
        print(f"  {name:<16} flags {flagged.mean():7.3%} of fresh identifiers "
              f"({int(flagged.sum())} of {len(unseen)})   | catches {caught:5.1%} of held-out payloads")
        print(f"    {'':<14} highest scoring: " + ", ".join(worst))
    return 0


def main():
    limit = int(sys.argv[sys.argv.index("--sample") + 1]) if "--sample" in sys.argv else None
    neg = negatives(limit)
    print(f"# {len(neg)} real identifiers, positives generated from test15's grammar")
    print(f"# bar: test10's whole catalogue costs {BAR:.3%} of real identifiers\n")
    print("**Key** — `catches` is the share of generated payloads flagged when the threshold is set")
    print("to flag test10's cost in real identifiers. `95% catch costs` is the reverse: the share of")
    print("real identifiers flagged if 95% of payloads must be caught. Lower cost is better.")
    if "--fresh" in sys.argv:
        return fresh_test(neg)
    run("random", neg, show_features=True)
    run("held-out vocabulary", neg, show_features=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
