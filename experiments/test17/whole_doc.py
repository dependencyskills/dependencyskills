#!/usr/bin/env python3
"""
test17 — classify a doc comment as a whole.

`test13` and `test14` both worked on *sentences*, and both failed. A sentence pulled out of a doc
comment loses the thing that might matter: whether the document as a whole was **describing a
capability** or **instructing a reader**. This treats one doc comment as one document, which is
also the unit the harvester actually emits.

TWO PHASES, because the first can invalidate the second.

  --diagnose   fit nothing. Compute whole-document features over the corpus, normalise each doc
               against ITS OWN LIBRARY, and look at where things land. Three checks decide whether
               a classifier is worth training at all.

  --classify   one-class novelty model over the whole corpus. Trained only on real documentation;
               there is no supervised alternative with 3 known-bad examples against 235,627
               known-good ones.

THE TRAP THIS IS BUILT TO AVOID. Bag-of-words over library documentation measures **topic**, not
stance: a doc about elliptic curves is far from any global centroid, and that is a subject rather
than an anomaly. Two choices prevent it. Features describe the document as a whole - length,
sentence shape, mood, address, how much of it is about the API versus about the reader's
environment - and never its vocabulary. And "normal" is computed **per library**, so an attacker's
doc is compared with the docs it has to sit beside, which is also the actual threat.

THE NUMBER THAT DECIDES ADOPTION is not catch rate. It is **false rejection on publishers the model
never saw**, priced against `test10`'s 0.221%, exactly like every other control here. So the
threshold is set to hit that budget and the catch rate is whatever falls out - not the reverse.

Run:  python3 whole_doc.py --diagnose
      python3 whole_doc.py --classify
"""
import json
import math
import os
import re
import sys
from collections import defaultdict

HERE = os.path.dirname(os.path.abspath(__file__))
CORPUS = os.path.join(HERE, "..", "test12", "prose-corpus.jsonl")
BUDGET = 0.00221          # test10's whole catalogue, the bar every control here is held to

SENT = re.compile(r'(?<=[.!?])\s+')
WORD = re.compile(r"[A-Za-z][A-Za-z0-9'_-]*")
CODE = re.compile(r'`[^`]*`')
MODAL = re.compile(r'\b(must|should|shall|need to|have to|required|ensure|make sure)\b', re.I)
IMPER = re.compile(r'^\s*(use|call|set|pass|add|remove|create|invoke|prefer|avoid|do not|don\'t|'
                   r'note|see|consider|check|ensure|make|write|read|copy|send|configure)\b', re.I)
SECOND = re.compile(r'\b(you|your|yours)\b', re.I)
FIRST = re.compile(r'\b(we|our|us)\b', re.I)
ENVISH = re.compile(r'\b(environment|env|config|configuration|log|logging|debug|telemetry|file|'
                    r'path|directory|credential|secret|token|host|url|endpoint|process|shell)\b',
                    re.I)
ORDER = re.compile(r'\b(before|after|first|then|prior to|subsequently|initially|finally)\b', re.I)
COND = re.compile(r'\b(if|when|unless|whenever|provided that|in case)\b', re.I)
PASSIVE = re.compile(r'\b(is|are|was|were|be|been)\s+\w+(ed|en)\b', re.I)
DEPREC = re.compile(r'\b(deprecat|instead|replaced by|no longer|migrat|superseded)\w*\b', re.I)
LINK = re.compile(r'https?://|\{@link')

FEATURES = ["words", "sentences", "mean_sent_len", "modal_rate", "imperative_rate",
            "second_person", "first_person", "code_rate", "envish_rate", "order_rate",
            "cond_rate", "passive_rate", "deprec_rate", "link_rate", "mean_word_len",
            "digit_rate", "caps_rate"]


def featurise(doc):
    """Whole-document properties. Deliberately no vocabulary — that is the topic leak."""
    sents = [s for s in SENT.split(doc) if s.strip()]
    words = WORD.findall(doc)
    n = max(len(words), 1)
    ns = max(len(sents), 1)
    return [
        len(words),
        len(sents),
        len(words) / ns,
        len(MODAL.findall(doc)) / n * 100,
        sum(1 for s in sents if IMPER.match(s)) / ns * 100,
        len(SECOND.findall(doc)) / n * 100,
        len(FIRST.findall(doc)) / n * 100,
        len(CODE.findall(doc)) / n * 100,
        len(ENVISH.findall(doc)) / n * 100,
        len(ORDER.findall(doc)) / n * 100,
        len(COND.findall(doc)) / n * 100,
        len(PASSIVE.findall(doc)) / ns * 100,
        len(DEPREC.findall(doc)) / n * 100,
        len(LINK.findall(doc)) / n * 100,
        sum(len(w) for w in words) / n,
        sum(c.isdigit() for c in doc) / max(len(doc), 1) * 100,
        sum(c.isupper() for c in doc) / max(len(doc), 1) * 100,
    ]


def load():
    if not os.path.exists(CORPUS):
        sys.exit(f"no corpus at {CORPUS} — run ../test12/cache_harvest.py first")
    rows = []
    with open(CORPUS) as fh:
        for line in fh:
            r = json.loads(line)
            rows.append(r)
    return rows


def payloads():
    sys.path.insert(0, os.path.join(HERE, "..", "test9"))
    from payloads import PROSE
    return {k: (v if isinstance(v, str) else str(v)) for k, v in PROSE.items()}


def stats(vectors):
    """Per-feature mean and standard deviation."""
    n = len(vectors)
    if n < 2:
        return None
    mean = [sum(v[i] for v in vectors) / n for i in range(len(FEATURES))]
    var = [sum((v[i] - mean[i]) ** 2 for v in vectors) / (n - 1) for i in range(len(FEATURES))]
    sd = [math.sqrt(x) if x > 1e-12 else 1e-6 for x in var]
    return mean, sd


MIN_SIBLINGS = 30      # below this a library's own statistics are noise


def local_models(rows, global_stats):
    """Per-library statistics. An injected doc has to sit among ITS library's other docs, so that
    is the population it should look unusual against.

    Scoring globally instead measures *topic and format*: the longest, most heavily formatted
    documents in the corpus win, which is what the first run of this actually found. Libraries with
    too few siblings fall back to the global model rather than being scored against noise.
    """
    by_lib = defaultdict(list)
    for r in rows:
        by_lib[r["library"]].append(r["v"])
    out = {}
    for lib, vecs in by_lib.items():
        out[lib] = stats(vecs) if len(vecs) >= MIN_SIBLINGS else global_stats
    return out


def novelty(vec, mean, sd):
    """Distance from normal, in standard deviations, summed across features.

    No covariance — with 17 features and no numpy, a diagonal model is the honest choice and its
    failure mode (correlated features counted twice) inflates scores uniformly rather than
    selectively.
    """
    return math.sqrt(sum(((vec[i] - mean[i]) / sd[i]) ** 2 for i in range(len(FEATURES))))


def diagnose():
    """Fit nothing. Three checks that can kill the classifier before it is trained."""
    rows = load()
    by_lib = defaultdict(list)
    for r in rows:
        by_lib[r["library"]].append(r)
    print(f"# {len(rows):,} doc comments, {len(by_lib):,} libraries, "
          f"{len({r['publisher'] for r in rows})} publishers\n")

    # Feature everything once.
    for r in rows:
        r["v"] = featurise(r["doc"])

    pubs = sorted({r["publisher"] for r in rows})
    cut = int(len(pubs) * 0.75)
    train_pubs, held_pubs = set(pubs[:cut]), set(pubs[cut:])
    train = [r for r in rows if r["publisher"] in train_pubs]
    held = [r for r in rows if r["publisher"] in held_pubs]
    print(f"  train: {len(train):,} docs / {len(train_pubs)} publishers")
    print(f"  held out: {len(held):,} docs / {len(held_pubs)} publishers\n")

    g = stats([r["v"] for r in train])
    m, sd = g
    local = local_models(train, g)
    held_local = local_models(held, g)
    tr_scores = sorted(novelty(r["v"], *local[r["library"]]) for r in train)
    thresh = tr_scores[int(len(tr_scores) * (1 - BUDGET))]

    print("## check 1 — do publishers the model never saw look normal?\n")
    held_flagged = sum(1 for r in held if novelty(r["v"], *held_local[r["library"]]) > thresh)
    tr_flagged = sum(1 for s in tr_scores if s > thresh)
    print(f"  threshold set to reject {BUDGET:.3%} of TRAINING docs: {tr_flagged:,} of {len(train):,}")
    print(f"  same threshold on HELD-OUT publishers: {held_flagged:,} of {len(held):,} "
          f"= {held_flagged/max(len(held),1):.3%}")
    ratio = (held_flagged / max(len(held), 1)) / BUDGET
    print(f"  -> unseen publishers are rejected {ratio:.1f}x more often than the budget allows")
    print("     (1.0x means the model generalises; a large number means it learned house style)\n")

    print("## check 2 — where do the known payloads land?\n")
    ps = payloads()
    print(f"  {'payload':<22}{'novelty':>10}{'percentile of real docs':>26}{'gate':>10}")
    for name, text in ps.items():
        s = novelty(featurise(text), m, sd)
        pct = sum(1 for x in tr_scores if x < s) / len(tr_scores)
        print(f"  {name:<22}{s:>10.2f}{pct:>25.2%}{'REJECT' if s > thresh else 'pass':>10}")

    print("\n## check 3 — what else is in the tail? (read these by eye)\n")
    scored = sorted(((novelty(r["v"], *local[r["library"]]), r) for r in train),
                    key=lambda t: -t[0])
    for s, r in scored[:8]:
        print(f"  [{s:6.1f}] {r['doc'][:104]}")
    return 0


def classify():
    """The gate. One decision per doc comment: pass it on, or do not."""
    rows = load()
    for r in rows:
        r["v"] = featurise(r["doc"])
    pubs = sorted({r["publisher"] for r in rows})
    cut = int(len(pubs) * 0.75)
    train = [r for r in rows if r["publisher"] in set(pubs[:cut])]
    held = [r for r in rows if r["publisher"] in set(pubs[cut:])]

    g = stats([r["v"] for r in train])
    m, sd = g
    local = local_models(train, g)
    held_local = local_models(held, g)
    tr = sorted(novelty(r["v"], *local[r["library"]]) for r in train)
    ps = payloads()

    print(f"# the gate: {len(train):,} docs to fit, {len(held):,} from unseen publishers to price\n")
    print("**Key** — the threshold is set to a false-rejection budget on documentation the model")
    print("never saw. `caught` is how many of the known payloads the same setting rejects.\n")
    print(f"{'budget':>9}{'threshold':>12}{'actually rejected (unseen)':>28}{'payloads caught':>18}")
    for budget in (0.00221, 0.005, 0.01, 0.02, 0.05):
        t = tr[min(int(len(tr) * (1 - budget)), len(tr) - 1)]
        rej = sum(1 for r in held if novelty(r["v"], *held_local[r["library"]]) > t)
        caught = sum(1 for text in ps.values() if novelty(featurise(text), m, sd) > t)
        print(f"{budget:>8.3%}{t:>12.2f}{f'{rej:,} = {rej/max(len(held),1):.3%}':>28}"
              f"{f'{caught} of {len(ps)}':>18}")

    print("\n  A gate is only usable if the left column and the third column agree, and the")
    print("  fourth is non-zero at that setting.")
    return 0


if __name__ == "__main__":
    if "--classify" in sys.argv:
        sys.exit(classify())
    sys.exit(diagnose())
