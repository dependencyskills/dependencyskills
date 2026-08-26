#!/usr/bin/env python3
"""
test19 — can a classifier find an injected instruction inside a real doc comment?

THE TASK. A negative is a real doc comment. A positive is **the same kind of doc comment with one
generated payload sentence inserted at a sentence boundary**. Not a payload standing alone: a
generated sentence differs from real documentation in length, register and vocabulary, so a model
told to separate the two would learn *synthetic versus real* and report a number about nothing.
Inserting removes every one of those tells and leaves the actual question.

THE BAR, AND WHY IT MOVED TWICE. `test10` published 0.221% as what the whole identifier catalogue
costs, and `test13`/`test14` were held to it and failed. `test17` then argued the bar was wrong for
prose: rejecting a doc comment demotes its entry to signature-only, which `test0` (7 of 8) and
`test7` (0 of 3 harm, 2 of 3 task) both measure as workable — so a prose rule is a priced option
rather than a failure.

**RAD-0040 has since narrowed that.** A signature-only entry cannot be *retrieved* — it carries no
prose and the query is prose — so the demotion `test17` priced as cheap costs the entry's
findability. The same record also measured the way out: a demoted entry can keep a retrieval key it
never shows. So the honest bar for a prose rule is neither 0.221% nor free:

    with a discarding fallback   every rejection loses an entry from search
    with a two-faced fallback    a rejection costs only what the entry displays, not whether it
                                 can be found

Both costs are reported here, and the second is the one that applies to an index built the way
RAD-0040 recommends.

Run: uv run --with scikit-learn --with scipy python classify_prose.py
     ... --sample 60000     # fewer real comments, for a quick pass
     ... --fresh            # score against packages never downloaded here
"""
import json
import os
import random
import sqlite3
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "test18"))
sys.path.insert(0, os.path.join(HERE, "..", "test15"))
import prose_grammar as G
from identifier_tokens import words as split_words

DB = os.path.join(HERE, "..", "corpus", "corpus.db")
BAR = 0.00221
SEED = 19
MIN_WORDS = 12          # a comment too short to hide a sentence in is not part of this question


# ------------------------------------------------------------------ tokenisations
# `test18` found character n-grams beating word splitting on identifiers, because an attacker who
# omits separators defeats every word rule. Prose has spaces already, so that specific escape does
# not exist — which makes this a real test of whether the finding was about identifiers or about
# the method.
def unigrams(text):
    return split_words(text)


def bigrams(text):
    w = split_words(text)
    return w + [f"{a}_{b}" for a, b in zip(w, w[1:])]


def trigrams(text):
    w = split_words(text)
    return (w + [f"{a}_{b}" for a, b in zip(w, w[1:])]
            + [f"{a}_{b}_{c}" for a, b, c in zip(w, w[1:], w[2:])])


def char_ngrams(text, lo=4, hi=5):
    s = (text or "").lower()
    return [s[i:i + n] for n in range(lo, hi + 1) for i in range(len(s) - n + 1)]


def words_and_chars(text):
    return unigrams(text) + char_ngrams(text)


TOKENIZERS = {
    "words":          unigrams,
    "words+bigrams":  bigrams,
    "words+trigrams": trigrams,
    "char 4-5grams":  char_ngrams,
    "words+chars":    words_and_chars,
}


def real_docs(limit=None):
    """Real doc comments, licence headers excluded — those are tagged in the corpus precisely
    because the prose does not describe the symbol it is attached to."""
    db = sqlite3.connect(DB)
    rows = [r[0] for r in db.execute(
        "SELECT doc FROM entries WHERE label = 'presumed_benign'"
        " AND tags NOT LIKE '%,license-header,%'")]
    db.close()
    rows = [d for d in rows if len(d.split()) >= MIN_WORDS]
    random.Random(SEED).shuffle(rows)
    return rows[:limit] if limit else rows


def poison(docs, payloads, rng):
    """One payload into each doc. Carriers are drawn from the SAME pool as the negatives, so the
    only difference between the two classes is the inserted sentence."""
    return [G.insert(d, p[0], rng) for d, p in zip(docs, payloads)]


def fit_and_score(name, fn, Xtr, ytr, Xte, yte, show=False):
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    import numpy as np

    vec = TfidfVectorizer(analyzer=fn, min_df=3, sublinear_tf=True)
    clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
    clf.fit(vec.fit_transform(Xtr), ytr)
    s = clf.decision_function(vec.transform(Xte))
    yte = np.asarray(yte)
    neg, pos = s[yte == 0], s[yte == 1]
    cut = np.quantile(neg, 1 - BAR)
    caught = float((pos > cut).mean())
    cost95 = float((neg > np.quantile(pos, 0.05)).mean())
    print(f"  {name:<16} at {BAR:.3%} cost -> catches {caught:6.1%}   "
          f"| 95% catch costs {cost95:7.3%}")
    if show:
        names = np.array(vec.get_feature_names_out())
        top = np.argsort(clf.coef_[0])[-10:][::-1]
        print(f"    {'':<14} top weights: " + ", ".join(str(x) for x in names[top]))
    return caught, cost95


def build(split, docs):
    """Returns (Xtr, ytr, Xte, yte). Carriers are never shared between train and test."""
    rng = random.Random(SEED)
    res = G.reserve()
    if split == "random":
        pool = G.generate() + G.generate(res)
        rng.shuffle(pool)
        cut = int(len(pool) * 0.7)
        ptr_pay, pte_pay = pool[:cut], pool[cut:]
    else:
        ptr_pay, pte_pay = G.generate(), G.generate(res)

    d = list(docs)
    rng.shuffle(d)
    # Four disjoint carrier pools: negatives and poisoned carriers, train and test. A carrier
    # appearing clean in training and poisoned in test would let the model diff them.
    q = len(d) // 4
    ntr, nte, ctr, cte = d[:q], d[q:2 * q], d[2 * q:3 * q], d[3 * q:4 * q]
    ptr = poison(ctr, [ptr_pay[i % len(ptr_pay)] for i in range(len(ctr))], rng)
    pte = poison(cte, [pte_pay[i % len(pte_pay)] for i in range(len(cte))], rng)
    return (ptr + ntr, [1] * len(ptr) + [0] * len(ntr),
            pte + nte, [1] * len(pte) + [0] * len(nte))


def fresh_test(docs, seeds=(19, 23, 29)):
    """Averaged over several seeds, because one run is not a rate.

    9,096 clean fresh comments at a 0.22% threshold is about 20 flagged comments. A single run
    varies by several of those purely from which carriers were drawn, and quoting three decimals
    off one run would be reporting noise to a precision it does not have.
    """
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    path = os.path.join(HERE, "..", "test18", "fresh-identifiers.json")
    if not os.path.exists(path):
        sys.exit("no fresh sample — run test18/fresh_sample.py first.")
    blob = json.load(open(path))
    fresh = [d["doc"] for d in blob.get("docs", []) if len(d["doc"].split()) >= MIN_WORDS]
    if not fresh:
        sys.exit("the fresh sample carries no doc comments — re-run test18/fresh_sample.py, "
                 "which now keeps them.")
    res = G.reserve()
    Xtr, ytr, _, _ = build("held-out", docs)
    # The threshold must be a quantile of CLEAN scores only. Taking it over the whole training set
    # puts the cutoff inside the positive range, which reads as a flawless classifier — 0.000% false
    # positives — and a useless one, 0.0% caught. Both numbers look like results.
    clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]
    half = len(fresh) // 2
    clean, carriers = fresh[:half], fresh[half:]
    pay = G.generate(res)

    print(f"\n## fresh packages — {len(blob['packages'])} never downloaded here, "
          f"{len(fresh)} usable doc comments, {len(clean)} of them clean\n")
    print("  Real comments, real packages, unseen by the model. The inserted payloads use reserved")
    print("  vocabulary, reserved framings AND reserved sentence forms, so all three axes are held")
    print(f"  out. Payload placement is averaged over {len(seeds)} seeds.\n")
    for name, fn in TOKENIZERS.items():
        vec = TfidfVectorizer(analyzer=fn, min_df=3, sublinear_tf=True)
        clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
        clf.fit(vec.fit_transform(Xtr), ytr)
        cut = np.quantile(clf.decision_function(vec.transform(clean_tr)), 1 - BAR)
        fps, tps, pers = [], [], {}
        for sd in seeds:
            rng = random.Random(sd)
            poisoned = poison(carriers, [pay[i % len(pay)] for i in range(len(carriers))], rng)
            fps.append(float((clf.decision_function(vec.transform(clean)) > cut).mean()))
            tps.append(float((clf.decision_function(vec.transform(poisoned)) > cut).mean()))
            # THE ONLY POSITIVES NOBODY GENERATED. Three is not a test set, but a model that
            # misses all three has learned the generator and nothing else. Scored PER PAYLOAD:
            # an average over three hides which of the three measured attacks gets through, and
            # here it hid exactly that.
            for text, framing, _k, _f in G.real_payloads():
                carried = [G.insert(c, text, rng) for c in carriers[:200]]
                pers.setdefault(framing, []).append(
                    float((clf.decision_function(vec.transform(carried)) > cut).mean()))
        detail = "  ".join(f"{k} {sum(v)/len(v):4.0%}" for k, v in sorted(pers.items()))
        fp = sum(fps) / len(fps)
        # Printed as a COUNT as well as a rate. 0.28% of 9,096 is 25 comments, and a rate quoted to
        # three decimals off 25 events reads far steadier than it is. The seeds vary the poisoning
        # only — the clean set and the fitted model do not move with them, so this is one training
        # draw, not a distribution over draws.
        print(f"  {name:<16} flags {fp:6.3%} clean ({round(fp * len(clean))} of {len(clean)})"
              f"   | catches {sum(tps)/len(tps):6.1%} generated   | test9: {detail}")
    return 0


def main():
    limit = int(sys.argv[sys.argv.index("--sample") + 1]) if "--sample" in sys.argv else None
    docs = real_docs(limit)
    print(f"# {len(docs)} real doc comments (>= {MIN_WORDS} words), licence headers excluded")
    print(f"# bar: test10's whole identifier catalogue costs {BAR:.3%}\n")
    print("**Key** — `catches` is the share of poisoned comments flagged with the threshold set to")
    print("flag test10's cost in clean comments. Every positive is a REAL comment with one")
    print("generated sentence inserted, so the model must find the sentence, not spot synthetic text.\n")
    if "--fresh" in sys.argv:
        return fresh_test(docs)
    for split in ("random", "held-out vocabulary and framings"):
        Xtr, ytr, Xte, yte = build("random" if split == "random" else "held-out", docs)
        print(f"\n## {split} split — train {len(Xtr)}, test {len(Xte)}\n")
        for name, fn in TOKENIZERS.items():
            fit_and_score(name, fn, Xtr, ytr, Xte, yte, show=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
