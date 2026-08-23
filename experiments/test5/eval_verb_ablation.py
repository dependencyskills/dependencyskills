#!/usr/bin/env python3
"""
test5 — how much retrieval signal do VERBS carry?

RAD-0026 asks whether a representation can hold enough meaning to retrieve while holding too
little to command. An imperative is a syntactic object built around a verb, so the cheapest
version of that idea is: suppress verbs in the indexed text and see what retrieval costs.

This measures ONLY the retrieval cost. It says nothing about whether suppression defeats an
injection — that is a separate measurement, and per RAD-0021 a degradation observed against a
non-adapting attacker is weak evidence anyway. But the cost question decides whether the idea is
worth pursuing at all: a cheap partial defence is worth having, an expensive one is not.

The queries here are verb-led by nature — "send a large file", "stop waiting", "hand them over"
— because that is how a developer states a need. So the naive deployment (suppress in the
corpus, leave queries alone) breaks the alignment that test5 measured as load-bearing. Two
repairs are tested: a consistent nonword cipher, and Soundex, which unlike an arbitrary cipher
is lossy and collision-tolerant so near-misses still collide.

CONDITIONS (only three require re-embedding the corpus; the rest reuse those vectors)

  baseline                    unmodified, the 29% raw-doc number from eval_recall.py
  deleted    corpus           verbs removed from the indexed text, queries untouched
  scrambled  corpus           verbs -> stable nonwords, queries untouched
  scrambled  corpus + query   the same mapping applied to both sides
  soundex    corpus           verbs -> Soundex codes, queries untouched
  soundex    corpus + query   the same mapping applied to both sides
  deleted    query            corpus untouched, verbs removed from the QUERY - the diagnostic
                              for how much of the signal lives on the verb at all

Run: uv run --with mlx-embeddings --with spacy \
       --with "https://github.com/explosion/spacy-models/releases/download/en_core_web_sm-3.8.0/en_core_web_sm-3.8.0-py3-none-any.whl" \
       python eval_verb_ablation.py
"""
import hashlib, json, os, re

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.environ.get("ABLATION_CACHE", "/tmp/test5-ablation")
EMB_MODEL = os.environ.get("EMB_MODEL", "mlx-community/bge-m3-mlx-fp16")
MAXCHARS = int(os.environ.get("MAXCHARS", "900"))
KS = [1, 3, 5, 10]

COMMENT = re.compile(r'^\s*/\*\*|\*/\s*$|^\s*\*\s?', re.M)
CONS = "bdfgklmnprstvz"
VOWL = "aeiou"


def key_doc(e):
    """The doc half of embed_corpus.key_text - the part the ablation applies to."""
    return " ".join(COMMENT.sub("", e["doc"]).split())[:MAXCHARS]


def nonword(lemma):
    """A stable pronounceable nonword for a lemma. Deterministic, so corpus and query agree."""
    h = hashlib.md5(lemma.encode()).digest()
    return "".join(CONS[h[i] % len(CONS)] + VOWL[h[i + 1] % len(VOWL)] for i in (0, 2, 4))


def soundex(w):
    """Standard Soundex. Lossy and collision-tolerant, unlike an arbitrary cipher."""
    w = re.sub(r'[^a-z]', '', w.lower())
    if not w:
        return ""
    code = {**dict.fromkeys("bfpv", "1"), **dict.fromkeys("cgjkqsxz", "2"),
            **dict.fromkeys("dt", "3"), "l": "4", **dict.fromkeys("mn", "5"), "r": "6"}
    out, last = w[0].upper(), code.get(w[0], "")
    for ch in w[1:]:
        d = code.get(ch, "")
        if d and d != last:
            out += d
        if ch not in "hw":
            last = d
    return (out + "000")[:4]


def ablate(texts, mode, nlp):
    """Rewrite each text with its VERB tokens deleted, scrambled or Soundex-coded."""
    out = []
    for doc in nlp.pipe(texts, batch_size=64, disable=["parser", "ner"]):
        parts = []
        for t in doc:
            if t.pos_ != "VERB":
                parts.append(t.text_with_ws)
            elif mode == "deleted":
                pass
            elif mode == "scrambled":
                parts.append(nonword(t.lemma_.lower()) + t.whitespace_)
            elif mode == "soundex":
                # LEMMA, not surface form: otherwise "sends" and "send" get different codes and
                # the corpus+query condition fails on morphology rather than on Soundex. Keeping
                # both mappings on the lemma leaves the mapping function as the only difference.
                parts.append(soundex(t.lemma_) + t.whitespace_)
        out.append("".join(parts))
    return out


def embed(model, tok, texts, label):
    from mlx_embeddings import generate
    vecs, B = [], 32
    for i in range(0, len(texts), B):
        vecs.extend(generate(model, tok, texts=texts[i:i + B]).text_embeds.tolist())
        if (i // B) % 50 == 0:
            print(f"    {label}: {i + len(texts[i:i+B])}/{len(texts)}", flush=True)
    return vecs


def corpus_vecs(mode, corpus, nlp, model, tok):
    """Embed the corpus under one ablation, cached on disk - each set is ~340MB."""
    os.makedirs(CACHE, exist_ok=True)
    path = os.path.join(CACHE, f"vecs-{mode}.json")
    if os.path.exists(path):
        print(f"  [{mode}] cached")
        return json.load(open(path))
    print(f"  [{mode}] tagging {len(corpus)} docs")
    docs = ablate([key_doc(e) for e in corpus], mode, nlp)
    texts = [f"{e['symbol'].split('.')[-1]}. {d}" for e, d in zip(corpus, docs)]
    v = embed(model, tok, texts, mode)
    json.dump(v, open(path, "w"))
    return v


def subset(corpus, queries, n, seed=11):
    """Deduped by symbol, every target retained, `n` total entries — the same construction as
    test5's corpus-size sweep, so the numbers line up with that table.

    Necessary because at full size the raw-doc baseline is 0-1/17: an ablation scored there
    measures nothing, since there is no recall left to lose. Signal exists at 220.
    """
    import random
    seen, keep = set(), []
    for i, e in enumerate(corpus):
        if e["symbol"] not in seen:
            seen.add(e["symbol"]); keep.append(i)
    targets = {q["target"] for q in queries}
    tgt = [i for i in keep if corpus[i]["symbol"] in targets]
    rest = [i for i in keep if corpus[i]["symbol"] not in targets]
    random.Random(seed).shuffle(rest)
    return sorted(tgt + rest[:max(0, n - len(tgt))])


def score(corpus, queries, V, qv, idx=None):
    """r@k over the whole corpus. numpy because seven conditions x 14.9k entries in pure
    Python is minutes of cosine per condition for no reason."""
    import numpy as np
    M = np.asarray(V, dtype=np.float32)
    M /= (np.linalg.norm(M, axis=1, keepdims=True) + 1e-12)
    Q = np.asarray(qv, dtype=np.float32)
    Q /= (np.linalg.norm(Q, axis=1, keepdims=True) + 1e-12)
    if idx is not None:
        M = M[idx]
        corpus = [corpus[i] for i in idx]
    sims = Q @ M.T
    # test5's harvest is ~63% duplicate symbols - the same declaration reached through several
    # artifacts, each with its own doc text and therefore its own vector. "Did the right answer
    # surface?" is then best-rank-over-all-copies. Taking one arbitrary occurrence (first or
    # last) scores a different entry per condition and is what made the baseline read 1/17 at
    # every k.
    index = {}
    for i, e in enumerate(corpus):
        index.setdefault(e["symbol"], []).append(i)
    hits, n = {k: 0 for k in KS}, len(queries)
    for row, q in zip(sims, queries):
        t = index.get(q["target"])
        if not t:
            continue
        best = max(float(row[i]) for i in t)
        pos = int((row > best).sum()) + 1
        for k in KS:
            hits[k] += pos <= k
    return hits, n


def main():
    corpus = json.load(open(os.path.join(HERE, "corpus.json")))
    queries = json.load(open(os.path.join(HERE, "queries.json")))
    qtexts = [q["query"] for q in queries]

    import spacy
    nlp = spacy.load("en_core_web_sm")
    from mlx_embeddings import load
    model, tok = load(EMB_MODEL)

    print(f"# {len(corpus)} entries, {len(queries)} queries, encoder {EMB_MODEL}")
    print(f"# verb ablation cache: {CACHE}\n")

    base = json.load(open(os.path.join(HERE, "corpus-vecs.json")))["vecs"]
    assert len(base) == len(corpus), f"{len(base)} vectors vs {len(corpus)} entries"

    print("# embedding ablated corpora")
    CV = {"baseline": base}
    for mode in ("deleted", "scrambled", "soundex"):
        CV[mode] = corpus_vecs(mode, corpus, nlp, model, tok)

    print("\n# embedding queries")
    QV = {"plain": embed(model, tok, qtexts, "q-plain")}
    for mode in ("deleted", "scrambled", "soundex"):
        QV[mode] = embed(model, tok, ablate(qtexts, mode, nlp), f"q-{mode}")

    runs = [
        ("baseline",                       "baseline",  "plain"),
        ("verbs deleted   - corpus only",  "deleted",   "plain"),
        ("verbs scrambled - corpus only",  "scrambled", "plain"),
        ("verbs scrambled - corpus+query", "scrambled", "scrambled"),
        ("verbs soundex   - corpus only",  "soundex",   "plain"),
        ("verbs soundex   - corpus+query", "soundex",   "soundex"),
        ("verbs deleted   - query only",   "baseline",  "deleted"),
    ]
    SIZES = [220, 1000, 3000, None]          # None = full deduped, matching test5's table
    subs = {s: subset(corpus, queries, s) if s else subset(corpus, queries, 10 ** 9)
            for s in SIZES}

    print(f"\n# r@k at 220 entries - the only size where the raw-doc baseline has signal to lose")
    print(f"{'condition':<34}" + "".join(f"{'r@'+str(k):>8}" for k in KS))
    for label, cm, qm in runs:
        hits, n = score(corpus, queries, CV[cm], QV[qm], subs[220])
        print(f"{label:<34}" + "".join(f"{str(hits[k])+'/'+str(n):>8}" for k in KS))

    print(f"\n# r@1 against corpus size (test5's sweep, every condition)")
    hdr = "".join(f"{(str(s) if s else 'full ' + str(len(subs[None]))):>12}" for s in SIZES)
    print(f"{'condition':<34}{hdr}")
    for label, cm, qm in runs:
        cells = ""
        for s in SIZES:
            hits, n = score(corpus, queries, CV[cm], QV[qm], subs[s])
            cells += f"{str(hits[1]) + '/' + str(n):>12}"
        print(f"{label:<34}{cells}")

    print("\n# a sample of what the ablation does to one query")
    s = qtexts[6]
    print(f"  plain     {s}")
    for mode in ("deleted", "scrambled", "soundex"):
        print(f"  {mode:<9} {ablate([s], mode, nlp)[0]}")


if __name__ == "__main__":
    main()
