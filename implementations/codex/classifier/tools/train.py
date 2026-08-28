#!/usr/bin/env python3
"""Fit the shipped prose classifier and write the model the JVM loads.

This is the training half of `experiments/test19`, ported to produce an ARTEFACT rather than a
table. The experiment settled the method — tf-idf over character 4-5 grams plus a linear model,
scored per sentence — and this fits that method once, prunes it to something shippable, and
calibrates a threshold per documentation convention.

WHY IT IS PYTHON AND NOT PART OF THE BUILD. Training needs `experiments/corpus/corpus.db`, which
is 490 MB, gitignored and rebuildable, and it needs scikit-learn. The JVM side needs neither: the
model is a term-frequency table and a dot product. So training is an offline step whose output is
committed, and the runtime carries no learning code at all.

    uv run --with scikit-learn --with scipy python train.py

WHY THE VOCABULARY IS PRUNED. A character 4-5 gram vocabulary over hundreds of thousands of
comments runs to millions of terms, which is not a thing to put in a jar. Pruning changes the
model, so the model is REFIT on the pruned vocabulary rather than having its weights filtered —
a filtered model is one whose L2 normalisation no longer matches the vector it scores, which is a
silent wrongness. The cost of pruning is measured below and printed.
"""
import json
import os
import random
import re
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.normpath(os.path.join(HERE, "..", "..", "..", ".."))
sys.path.insert(0, os.path.join(REPO, "experiments", "test19"))
sys.path.insert(0, os.path.join(REPO, "experiments", "test18"))
sys.path.insert(0, os.path.join(REPO, "experiments", "test15"))

import prose_grammar as G  # noqa: E402

DB = os.path.join(REPO, "experiments", "corpus", "corpus.db")
OUT = os.path.join(HERE, "..", "src", "main", "resources", "dependencyskills", "classifier")

SEED = 19
MIN_WORDS = 12
MIN_SENTENCE_WORDS = 4
SENT = re.compile(r'(?<=[.!?])\s+')

# test10's whole identifier catalogue costs this much in clean comments. The operating point is
# calibrated to it so the number means the same thing it meant in test19.
BAR = 0.00221

# How many terms survive pruning. Chosen by the sweep this script prints, not by taste.
KEEP = 120_000

CONVENTIONS = ("javadoc", "kdoc", "jsdoc")

# The training sample per convention. Large enough that the vocabulary is not the sample's
# accident, small enough that this finishes.
SAMPLE = 30_000


def char_ngrams(text, lo=4, hi=5):
    """Identical to test19's analyser. Any drift here is drift between the measured model and the
    shipped one, and it would not announce itself."""
    s = (text or "").lower()
    return [s[i:i + n] for n in range(lo, hi + 1) for i in range(len(s) - n + 1)]


def sentences(text):
    return [s for s in SENT.split(text or "") if len(s.split()) >= MIN_SENTENCE_WORDS]


def docs_by_convention():
    import sqlite3
    db = sqlite3.connect(DB)
    out = {}
    for convention in CONVENTIONS:
        rows = [r[0] for r in db.execute(
            "SELECT doc FROM entries WHERE label = 'presumed_benign'"
            " AND doc_format = ? AND tags NOT LIKE '%,license-header,%'", (convention,))]
        rows = [d for d in rows if len(d.split()) >= MIN_WORDS]
        random.Random(SEED).shuffle(rows)
        out[convention] = rows
        print(f"# {convention}: {len(rows)} comments")
    db.close()
    return out


def training_set(clean_docs, payloads):
    """Every sentence of a clean comment is clean; a generated payload sentence is the positive.

    Harder and more honest than labelling every sentence of a poisoned comment positive, and it
    is what the sentence-level variant measured.
    """
    x, y = [], []
    for doc in clean_docs:
        for s in sentences(doc):
            x.append(s)
            y.append(0)
    for text, _f, _k, _fm in payloads[:max(1, len(x) // 8)]:
        x.append(text)
        y.append(1)
    return x, y


def fit(x, y, vocabulary=None):
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    vec = TfidfVectorizer(analyzer=char_ngrams, min_df=3, sublinear_tf=True,
                          vocabulary=vocabulary)
    clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
    clf.fit(vec.fit_transform(x), y)
    return vec, clf


def worst_sentence(vec, clf, texts):
    """A comment scores as its worst sentence, so the operating point means what it meant in
    test19 — a rate over comments, not over sentences."""
    import numpy as np
    out = []
    for t in texts:
        s = sentences(t) or [t]
        out.append(float(clf.decision_function(vec.transform(s)).max()))
    return np.array(out)


def main():
    import numpy as np

    by_convention = docs_by_convention()
    rng = random.Random(SEED)

    # One model, trained across conventions: test19 measured discrimination transferring between
    # them and only the operating point failing to. Training per convention would be three models
    # to keep honest instead of three numbers.
    train_docs = []
    for convention in CONVENTIONS:
        train_docs += by_convention[convention][:SAMPLE // len(CONVENTIONS)]
    rng.shuffle(train_docs)

    held = G.reserve()
    train_payloads = G.generate()
    test_payloads = G.generate(held)
    print(f"# payloads: {len(train_payloads)} train, {len(test_payloads)} held out\n")

    x, y = training_set(train_docs, train_payloads)
    print(f"# {len(x)} sentences, {sum(y)} of them payloads")

    full_vec, full_clf = fit(x, y)
    full_terms = np.array(full_vec.get_feature_names_out())
    print(f"# full vocabulary: {len(full_terms)} terms\n")

    order = np.argsort(-np.abs(full_clf.coef_[0]))

    # Held-out evaluation material, from comments no training sentence came from.
    evaluate_on = {c: by_convention[c][SAMPLE:SAMPLE + 4000] for c in CONVENTIONS}
    carriers = by_convention["javadoc"][SAMPLE + 4000:SAMPLE + 5500]

    def measure(vec, clf, label):
        cut = float(np.quantile(worst_sentence(vec, clf, train_docs[:3000]), 1 - BAR))
        caught = (worst_sentence(vec, clf, [G.insert(c, p[0], rng)
                                            for c, p in zip(carriers, test_payloads * 99)]) > cut).mean()
        known = {}
        for text, framing, _k, _f in G.real_payloads():
            poisoned = [G.insert(c, text, rng) for c in carriers[:800]]
            known[framing] = float((worst_sentence(vec, clf, poisoned) > cut).mean())
        clean = evaluate_on["javadoc"]
        fp = int((worst_sentence(vec, clf, clean) > cut).sum())
        print(f"  {label:<22} flagged {fp:>3} of {len(clean)} ({fp / len(clean):.2%})"
              f"   generated {caught:5.1%}   known bad: "
              + "  ".join(f"{k} {v:4.0%}" for k, v in sorted(known.items())))
        return cut

    print("## what pruning costs\n")
    measure(full_vec, full_clf, f"full ({len(full_terms)})")
    for keep in (40_000, 80_000, KEEP, 200_000):
        if keep >= len(full_terms):
            continue
        vocabulary = {t: i for i, t in enumerate(full_terms[order[:keep]])}
        vec, clf = fit(x, y, vocabulary)
        measure(vec, clf, f"pruned to {keep}")

    # The shipped model, refit on the pruned vocabulary so its normalisation matches its weights.
    print(f"\n## shipping {KEEP} terms\n")
    vocabulary = {t: i for i, t in enumerate(full_terms[order[:KEEP]])}
    vec, clf = fit(x, y, vocabulary)

    # A threshold per convention. test19 measured catch transferring across conventions and the
    # false-positive rate varying seventeen-fold by direction, so this is per-convention state
    # rather than one constant.
    thresholds = {}
    for convention in CONVENTIONS:
        calibration = by_convention[convention][SAMPLE:SAMPLE + 3000]
        thresholds[convention] = float(np.quantile(worst_sentence(vec, clf, calibration), 1 - BAR))
        clean = evaluate_on[convention]
        fp = int((worst_sentence(vec, clf, clean) > thresholds[convention]).sum())
        print(f"  {convention:<10} threshold {thresholds[convention]:+.4f}"
              f"   flagged {fp:>3} of {len(clean)} ({fp / len(clean):.2%})")

    write(vec, clf, thresholds)
    write_registers(*fit_registers(vocabulary, train_docs))
    return 0


def register_payloads():
    """Payloads for the attribution model, with framing NOT held out.

    Holding a framing out is right for measuring detection and wrong for shipping attribution: a
    class the model never trained on cannot be predicted, so reserving a quarter of the framings
    produces an artefact that is structurally unable to ever say `diagnostic`. Vocabulary and
    sentence form stay held out, which is what makes the attribution number mean anything.
    """
    original = G.reserve
    try:
        def framing_free(seed=19):
            axes, _framings, forms = original(seed)
            return axes, set(), forms
        G.reserve = framing_free
        return G.generate(), G.generate(framing_free())
    finally:
        G.reserve = original


def fit_registers(vocabulary, clean_docs):
    """A second model that says which register an instruction is hiding in.

    Separate from the decision on purpose. test19 measured that splitting the same decision across
    nine classes catches 75.9% where the binary model catches 96%, at the same false-positive
    cost - so the attribution is not free and must not be the thing that decides. The binary model
    decides; this one only labels what it flagged.

    It shares the pruned vocabulary, so both models see exactly the same features and there is one
    analyser to keep honest rather than two.
    """
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    train_payloads, test_payloads = register_payloads()
    x, y = [], []
    for doc in clean_docs:
        for s in sentences(doc):
            x.append(s)
            y.append("clean")
    by_framing = {}
    for text, framing, _k, _f in train_payloads:
        by_framing.setdefault(framing, []).append(text)
    per_class = max(1, len(x) // 8 // max(1, len(by_framing)))
    for framing, texts in sorted(by_framing.items()):
        for text in texts[:per_class]:
            x.append(text)
            y.append(framing)

    vec = TfidfVectorizer(analyzer=char_ngrams, min_df=3, sublinear_tf=True, vocabulary=vocabulary)
    clf = LogisticRegression(max_iter=4000, class_weight="balanced", C=4.0)
    clf.fit(vec.fit_transform(x), y)

    print(f"\n## register attribution ({len(by_framing)} registers, framing not held out)\n")

    # THE NUMBER THAT MEANS SOMETHING. test9's three payloads were written by hand, and their
    # framings were assigned by whoever wrote them rather than by this generator, so they are the
    # only positives here nobody templated.
    real = [(text, framing) for text, framing, _k, _f in G.real_payloads()]
    for text, framing in real:
        got = clf.predict(vec.transform([text]))[0]
        mark = "ok " if got == framing else "MISS"
        print(f"  {mark} hand-written {framing:<14} -> {got}")

    # AND THE NUMBER THAT DOES NOT. A framing IS a sentence template, so separating eight of them
    # from each other is template recognition and scores near 100% however little it understands.
    # Kept, and kept labelled, because deleting it would leave the impression it was never asked.
    held = [(text, framing) for text, framing, _k, _f in test_payloads[:6000]]
    predicted = clf.predict(vec.transform([h[0] for h in held]))
    right = sum(1 for (_t, f), pr in zip(held, predicted) if pr == f)
    print(f"\n  generated payloads, held-out vocabulary and form: {right} of {len(held)} "
          f"({right / len(held):.1%}) - this is template recognition, not a generalisation number")
    return clf, vocabulary


def write_registers(clf, vocabulary):
    import numpy as np
    classes = [str(c) for c in clf.classes_]
    path = os.path.join(OUT, "register.model")
    with open(path, "wb") as f:
        f.write(b"DSPR")
        f.write(struct.pack("<i", 1))
        f.write(struct.pack("<i", len(classes)))
        for name in classes:
            b = name.encode("utf-8")
            f.write(struct.pack("<i", len(b)))
            f.write(b)
        f.write(struct.pack("<i", len(vocabulary)))
        f.write(clf.intercept_.astype(np.float32).tobytes())
        f.write(clf.coef_.astype(np.float32).tobytes())
    print(f"wrote {path} ({os.path.getsize(path) / 1e6:.1f} MB)")


def write(vec, clf, thresholds):
    """A flat binary the JVM reads with no parser.

    Text would be inspectable and roughly three times the size for terms this short; the format
    is documented in the module README and read by exactly one class.
    """
    import numpy as np
    os.makedirs(OUT, exist_ok=True)
    terms = vec.get_feature_names_out()
    idf = vec.idf_.astype(np.float32)
    coef = clf.coef_[0].astype(np.float32)

    path = os.path.join(OUT, "prose.model")
    with open(path, "wb") as f:
        f.write(b"DSPC")                                  # magic
        f.write(struct.pack("<i", 1))                     # format version
        f.write(struct.pack("<f", float(clf.intercept_[0])))
        f.write(struct.pack("<i", len(terms)))
        blob = "\n".join(terms).encode("utf-8")
        f.write(struct.pack("<i", len(blob)))
        f.write(blob)
        f.write(idf.tobytes())
        f.write(coef.tobytes())
        # Thresholds live here rather than beside in JSON, so the runtime needs no parser and
        # the operating point cannot drift away from the weights it was calibrated against.
        f.write(struct.pack("<i", len(thresholds)))
        for convention, threshold in sorted(thresholds.items()):
            name = convention.encode("utf-8")
            f.write(struct.pack("<i", len(name)))
            f.write(name)
            f.write(struct.pack("<f", threshold))
    meta = {
        "analyzer": "char 4-5 grams, lowercased",
        "tfidf": {"sublinear_tf": True, "smooth_idf": True, "norm": "l2", "min_df": 3},
        "terms": len(terms),
        "thresholds": thresholds,
        "bar": BAR,
        "trained_on": {"sample_per_convention": SAMPLE // len(CONVENTIONS),
                       "conventions": list(CONVENTIONS)},
    }
    # Human-readable only. Nothing at runtime reads this; it exists so a reader can see what the
    # binary beside it was fitted from without running anything.
    with open(os.path.join(OUT, "prose.json"), "w") as f:
        json.dump(meta, f, indent=2, sort_keys=True)
    print(f"\nwrote {path} ({os.path.getsize(path) / 1e6:.1f} MB) and prose.json")


if __name__ == "__main__":
    sys.exit(main())
