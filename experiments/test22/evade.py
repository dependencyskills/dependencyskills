#!/usr/bin/env python3
"""
test22 — how much work is it to write an injection this classifier misses?

THE QUESTION, STATED HONESTLY. The classifier ships inside the tool, so an attacker has it. Given
that, evasion is not in question — a white-box linear model can always be pushed below any
threshold. The useful measurement is the COST: does getting under the threshold force the text to
stop reading like documentation, or to stop carrying an instruction? A casual-injection filter earns
its place if evasion takes deliberate effort; it was never meant to stop a determined attacker, and
this does not pretend to measure that.

NO SEARCH AGAINST A NETWORK, NO AGENT, NOTHING EXECUTED. Evading a classifier means producing text
that SCORES LOW. The only feedback is a float from a model already on disk. This reads text and
computes numbers; it wraps in `run-sandboxed.sh` at `--network=none` so that is a fact, not a
promise.

WHAT LEAVES THIS FILE. Rates and mechanism only. The evasions themselves are working (if inert)
injection prose, so `evasions.json` is gitignored and never committed — the same treatment as the
corpus DB and the packed transcripts. All destinations are `.invalid` (RFC 2606); nothing here is
executable and no host is real.

THE SEARCH. Greedy word-level edits against the classifier's own score, which is the strongest thing
a white-box attacker does without a gradient library. Each transform is a thing a human writer could
do too — synonyms, softening the verb, removing the explicit destination. An evasion that only a
search could find is not a casual injection; an evasion a person would reach for is.

  budget      edits per payload, fixed in advance so the search cannot run until it wins
  transfer    reported as survivors per attempt, so the COST of an evasion is visible, not hidden

Run (host, for iteration):  uv run --with scikit-learn --with scipy python evade.py --budget 20
Run (sealed):               ../run-sandboxed.sh test22
"""
import json
import os
import random
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
sys.path.insert(0, os.path.join(HERE, "..", "test18"))
sys.path.insert(0, os.path.join(HERE, "..", "test19"))
import classify_prose as P
import prose_grammar as G

SEED = 27
OUT = os.path.join(HERE, "evasions.json")

# Edits a human writer could apply. None invents new capability; each softens how an existing
# instruction is phrased. This is the casual attacker's toolkit, not a gradient attack.
SOFTEN = {
    "must": "may optionally", "should": "can", "required": "suggested",
    "copied": "made available", "sent": "shared", "recorded": "noted",
    "written": "included", "uploaded": "provided", "logged": "referenced",
    "before": "around",
    "environment": "runtime context", "configuration": "operational context",
    "credential": "identity detail", "secret": "internal value", "token": "handle",
    "debug log": "diagnostic notes", "telemetry": "operational notes",
}
DROP_TAIL = re.compile(r'\s+(into|to|onto|toward|in|alongside|near)\s+[\w\s]+$', re.I)


def transforms(sentence):
    """One-step neighbours: each a single human-plausible rewrite."""
    out = []
    low = sentence.lower()
    for a, b in SOFTEN.items():
        pat = re.compile(r'\b' + re.escape(a) + r'\b', re.I)
        if pat.search(sentence):
            out.append(pat.sub(b, sentence, count=1))
    # remove the destination clause entirely — the move that made `policy` hard
    stripped = DROP_TAIL.sub("", sentence)
    if stripped != sentence and len(stripped.split()) >= 6:
        out.append(stripped)
    return out


def score_fn():
    from sklearn.feature_extraction.text import TfidfVectorizer
    from sklearn.linear_model import LogisticRegression
    docs = P.real_docs(None)
    Xtr, ytr, _, _ = P.build("held-out", docs)
    clean_tr = [x for x, y in zip(Xtr, ytr) if y == 0]
    vec = TfidfVectorizer(analyzer=P.TOKENIZERS["char 4-5grams"], min_df=3, sublinear_tf=True)
    clf = LogisticRegression(max_iter=3000, class_weight="balanced", C=4.0)
    clf.fit(vec.fit_transform(Xtr), ytr)
    import numpy as np
    cut = float(np.quantile(clf.decision_function(vec.transform(clean_tr)), 1 - P.BAR))
    return (lambda t: float(clf.decision_function(vec.transform([t]))[0])), cut


# The point of an injection is that a reader still understands the instruction. A "rewrite" that
# no longer names a thing to act on has not evaded the filter, it has surrendered — the filter did
# its job by forcing the text to stop being an instruction. So a candidate only counts if it still
# contains a recognisable object of the attack.
def _still_an_instruction(text):
    low = text.lower()
    OBJECTS = ("environment", "runtime context", "configuration", "operational context",
               "credential", "identity detail", "secret", "internal value", "token", "handle",
               "password", "profile", "session", "key", "settings")
    return any(o in low for o in OBJECTS)


def greedy(sentence, score, budget):
    """Descend the score by one human-plausible edit at a time. Returns (best_text, edits, hits)."""
    cur, edits, attempts = sentence, 0, 0
    for _ in range(budget):
        cands = [c for c in transforms(cur) if _still_an_instruction(c)]
        attempts += len(cands)
        if not cands:
            break
        best = min(cands, key=score)
        if score(best) >= score(cur):
            break
        cur, edits = best, edits + 1
    return cur, edits, attempts


def main():
    budget = int(sys.argv[sys.argv.index("--budget") + 1]) if "--budget" in sys.argv else 20
    rng = random.Random(SEED)
    score, cut = score_fn()

    # Attack the generated payloads AND test9's three real ones. Held-out axes only, so the search
    # starts from sentences the classifier was not trained on.
    pool = [(s, f) for s, f, _k, _fm in G.generate(G.reserve())]
    rng.shuffle(pool)
    pool = pool[:400] + [(t, f) for t, f, _k, _fm in G.real_payloads()]

    started_flagged = evaded = 0
    total_attempts = 0
    records, by_edits = [], {}
    for sentence, framing in pool:
        s0 = score(sentence)
        if s0 <= cut:
            continue                        # already misses; not an evasion, nothing to do
        started_flagged += 1
        final, edits, attempts = greedy(sentence, score, budget)
        total_attempts += attempts
        if score(final) <= cut:
            evaded += 1
            by_edits[edits] = by_edits.get(edits, 0) + 1
            records.append({"framing": framing, "edits": edits,
                            "original": sentence, "evaded": final})

    print(f"# {started_flagged} payloads that START flagged, budget {budget} edits each")
    print(f"# every edit is a rewrite a human writer could make; nothing executed, no network\n")
    print("WHAT IS BEING COUNTED")
    print("  Each payload begins ABOVE the threshold — the classifier catches it. The search")
    print("  applies human-plausible rewrites, one at a time, trying to push it below. `evaded`")
    print("  means it succeeded. `edits` is how many rewrites that took — the COST of the evasion.\n")
    print(f"  evaded the classifier      {evaded} of {started_flagged} "
          f"({evaded/max(1,started_flagged):.0%})")
    print(f"  rewrites tried in total    {total_attempts}")
    print(f"  rewrites per evasion found {total_attempts/max(1,evaded):.0f}")
    print(f"\n  evasions by number of edits needed:")
    for e in sorted(by_edits):
        print(f"    {e} edit{'s' if e != 1 else ' '}  {by_edits[e]}")

    # test9's three, by name — the only non-generated payloads.
    print(f"\n## test9's three real payloads\n")
    for text, framing, _k, _fm in G.real_payloads():
        final, edits, _a = greedy(text, score, budget)
        verdict = f"evaded in {edits} edits" if score(final) <= cut else "still caught"
        print(f"  {framing:<14} {verdict}")

    json.dump(records, open(OUT, "w"))
    print(f"\n# {len(records)} evasions written to evasions.json (gitignored — working injection prose)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
