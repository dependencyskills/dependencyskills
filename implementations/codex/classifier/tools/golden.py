#!/usr/bin/env python3
"""Write golden vectors: sentences and the scores scikit-learn gives them.

The JVM classifier is a reimplementation of scikit-learn's arithmetic, and a reimplementation
that is subtly wrong does not announce itself - it just scores differently from the model that
was measured, and every number in the write-up quietly stops describing what ships. These are the
fixture that makes that visible.

    uv run --with scikit-learn --with scipy python golden.py
"""
import json
import os
import random
import struct
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from train import (CONVENTIONS, MIN_WORDS, OUT, SAMPLE, SEED, char_ngrams,  # noqa: E402
                   docs_by_convention, sentences)

sys.path.insert(0, os.path.normpath(os.path.join(HERE, "..", "..", "..", "..", "experiments", "test19")))
import prose_grammar as G  # noqa: E402

TESTS = os.path.join(HERE, "..", "src", "test", "resources")


def load_model():
    """Reads the shipped binary back, so the fixture is scored by the ARTEFACT rather than by the
    in-memory objects that produced it. A writer bug would otherwise pass unnoticed."""
    import numpy as np
    with open(os.path.join(OUT, "prose.model"), "rb") as f:
        assert f.read(4) == b"DSPC"
        assert struct.unpack("<i", f.read(4))[0] == 1
        intercept = struct.unpack("<f", f.read(4))[0]
        count = struct.unpack("<i", f.read(4))[0]
        blob = f.read(struct.unpack("<i", f.read(4))[0]).decode("utf-8").split("\n")
        assert len(blob) == count, (len(blob), count)
        idf = np.frombuffer(f.read(count * 4), dtype="<f4")
        coef = np.frombuffer(f.read(count * 4), dtype="<f4")
        thresholds = {}
        for _ in range(struct.unpack("<i", f.read(4))[0]):
            name = f.read(struct.unpack("<i", f.read(4))[0]).decode("utf-8")
            thresholds[name] = struct.unpack("<f", f.read(4))[0]
    return {t: i for i, t in enumerate(blob)}, idf, coef, intercept, thresholds


def score(sentence, index, idf, coef, intercept):
    """The same arithmetic the JVM does, from the same file - sublinear tf, smoothed idf, L2."""
    import math
    counts = {}
    for g in char_ngrams(sentence):
        counts[g] = counts.get(g, 0) + 1
    norm = 0.0
    dot = 0.0
    for gram, n in counts.items():
        at = index.get(gram)
        if at is None:
            continue
        w = (1.0 + math.log(n)) * float(idf[at])
        norm += w * w
        dot += w * float(coef[at])
    if norm == 0.0:
        return float(intercept)
    return dot / math.sqrt(norm) + float(intercept)


def main():
    index, idf, coef, intercept, thresholds = load_model()
    by_convention = docs_by_convention()
    rng = random.Random(SEED)

    cases = []
    # Real sentences from comments no training sentence came from.
    for convention in CONVENTIONS:
        for doc in by_convention[convention][SAMPLE + 6000:SAMPLE + 6000 + 40]:
            for s in sentences(doc)[:1]:
                cases.append({"text": s, "kind": "real", "convention": convention})
    # Generated payloads, and the three written by hand.
    for text, framing, _k, _f in G.generate(G.reserve())[:30]:
        cases.append({"text": text, "kind": "generated", "register": framing})
    for text, framing, _k, _f in G.real_payloads():
        cases.append({"text": text, "kind": "known-bad", "register": framing})
    # Shapes that break naive implementations: non-ASCII, empty, shorter than one n-gram.
    for text in ["", "a", "abc", "ünïcödé characters in a real sentence here",
                 "Ends without punctuation", "   leading and trailing   "]:
        cases.append({"text": text, "kind": "edge"})

    for case in cases:
        case["score"] = score(case["text"], index, idf, coef, intercept)

    os.makedirs(TESTS, exist_ok=True)
    out = {"thresholds": thresholds, "terms": len(index), "cases": cases}
    path = os.path.join(TESTS, "golden-scores.json")
    with open(path, "w") as f:
        json.dump(out, f, indent=1)
    print(f"wrote {len(cases)} golden cases to {path}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
