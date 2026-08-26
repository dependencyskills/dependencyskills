#!/usr/bin/env python3
"""
Does a sentence embedding beat TF-IDF here, and is it worth the runtime it costs?

THE QUESTION IS NOT ONLY ACCURACY. Everything else in `test19` is TF-IDF plus a linear model — a
term-frequency table and a dot product, which is what Lucene already computes. It ports to the
pipeline with **no new runtime**. BGE-M3 does not: it needs an embedding runtime, and the one used
here (`mlx-embeddings`) is Apple-only. RAD-0035 already names a JVM embedding runtime as something
the Lucene port would need anyway, so the honest framing is *measure whether the dependency earns
itself*, not adopt it because it is available.

MATCHED COMPARISON. Both arms get the same subsample, the same split and the same classifier —
logistic regression — so the only variable is the representation. Subsampled because embedding
100,000 comments to answer a yes/no question is not a good trade.

Run: uv run --with scikit-learn --with scipy --with mlx-embeddings python embed_variant.py
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

SEED = 23
EMB_MODEL = os.environ.get("EMB_MODEL", "mlx-community/bge-m3-mlx-fp16")
N = int(os.environ.get("N", "12000"))       # per side of the training set


def main():
    import numpy as np
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression

    rng = random.Random(SEED)
    docs = P.real_docs(None)
    Xtr, ytr, _, _ = P.build("held-out", docs)
    idx = list(range(len(Xtr)))
    rng.shuffle(idx)
    idx = idx[:N * 2]
    Xtr = [Xtr[i] for i in idx]
    ytr = [ytr[i] for i in idx]
    clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]

    blob = json.load(open(os.path.join(HERE, "..", "test18", "fresh-identifiers.json")))
    fresh = [d["doc"] for d in blob["docs"] if len(d["doc"].split()) >= P.MIN_WORDS]
    half = len(fresh) // 2
    good, carriers = fresh[:half], fresh[half:half + 1200]
    real = {f: [G.insert(c, t, rng) for c in carriers]
            for t, f, _k, _fm in G.real_payloads()}

    print(f"# matched subsample: {len(Xtr)} training ({sum(ytr)} poisoned), "
          f"{len(good)} clean fresh comments\n")

    def report(tag, score_tr_clean, score_good, score_real):
        cut = np.quantile(score_tr_clean, 1 - P.BAR)
        fp = int((score_good > cut).sum())
        detail = "  ".join(f"{k} {float((v > cut).mean()):4.0%}"
                           for k, v in sorted(score_real.items()))
        print(f"  {tag:<26} good flagged {fp:>3} of {len(good)} ({fp/len(good):.2%})"
              f"   | known bad: {detail}")

    # ---- arm 1: TF-IDF, the incumbent
    for name in ("words+bigrams", "words+chars"):
        vec = TfidfVectorizer(analyzer=P.TOKENIZERS[name], min_df=3, sublinear_tf=True)
        clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
        clf.fit(vec.fit_transform(Xtr), ytr)
        report(f"tfidf {name}",
               clf.decision_function(vec.transform(clean_tr)),
               clf.decision_function(vec.transform(good)),
               {k: clf.decision_function(vec.transform(v)) for k, v in real.items()})

    # ---- arm 2: BGE-M3 embeddings
    from mlx_embeddings import load, generate
    model, tok = load(EMB_MODEL)

    def embed(texts, B=32):
        out = []
        for i in range(0, len(texts), B):
            out.extend(generate(model, tok, texts=texts[i:i + B]).text_embeds.tolist())
            if i and i % (B * 40) == 0:
                print(f"    … {i}/{len(texts)}", file=sys.stderr, flush=True)
        return np.asarray(out)

    print(f"\n  embedding with {EMB_MODEL} …", flush=True)
    Etr = embed(Xtr)
    clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
    clf.fit(Etr, ytr)
    report("bge-m3, whole comment",
           clf.decision_function(Etr[np.array(ytr) == 0]),
           clf.decision_function(embed(good)),
           {k: clf.decision_function(embed(v)) for k, v in real.items()})

    # SENTENCE LEVEL, because comparing comment-level embeddings against comment-level TF-IDF
    # under-tests the alternative: `variations.py --sentences` showed the payload's signal is
    # diluted by the carrier, and an embedding of a whole comment is dominated by whatever the
    # comment is mostly about. This gives the embedding the same advantage.
    import re as _re
    SENT = _re.compile(r'(?<=[.!?])\s+')

    def sentences(t):
        return [x for x in SENT.split(t) if len(x.split()) >= 4] or [t]

    str_X, str_y = [], []
    for x, y in zip(Xtr, ytr):
        for sent in sentences(x):
            str_X.append(sent)
            str_y.append(0)
    gen = G.generate()
    rng.shuffle(gen)
    for sent, _f, _k, _fm in gen[:len(str_X) // 8]:
        str_X.append(sent)
        str_y.append(1)
    print(f"\n  sentence-level: {len(str_X)} sentences, {sum(str_y)} payloads", flush=True)
    Es = embed(str_X)
    clfs = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
    clfs.fit(Es, str_y)

    def worst(texts):
        flat, spans = [], []
        for t in texts:
            ss = sentences(t)
            spans.append((len(flat), len(flat) + len(ss)))
            flat.extend(ss)
        sc = clfs.decision_function(embed(flat))
        return np.array([sc[a:b].max() for a, b in spans])

    report("bge-m3, per sentence",
           worst([x for x, y in zip(Xtr, ytr) if y == 0][:3000]),
           worst(good),
           {k: worst(v[:400]) for k, v in real.items()})
    return 0


if __name__ == "__main__":
    sys.exit(main())
