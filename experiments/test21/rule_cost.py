#!/usr/bin/env python3
"""
Are binary-only libraries the safest thing in the corpus, or does the dangerous form survive
compilation?

THE CLAIM BEING TESTED. A library with no sources jar has no prose, so `test19`'s channel does not
exist for it. The identifier channel does — and `test16` measured camelCase identifier payloads
obeyed **0 of 24**, against a spaced form firing **6 of 6**. If the spaced form cannot survive
compilation, binary-only libraries carry no effective injection surface at all. That would be a
strong claim and it is worth checking rather than assuming.

IT DOES SURVIVE. The JVM class file stores a method or field name as a CONSTANT_Utf8, and the
format permits characters a source language would refuse — Kotlin's backticked identifiers compile
straight through. Exactly **one** identifier in 869,436 harvested here carries a space, and it is
benign, but its existence settles the question: the form that gets obeyed is expressible in
bytecode and does occur in the wild.

WHAT THIS PRICES. `test10`'s catalogue applied to bytecode identifiers rather than source ones. The
two populations are not the same: bytecode carries protobuf-generated names, `$SwitchMap$` tables
and inner-class chains that no human wrote and that source-level rules were never priced against.

Run: uv run python rule_cost.py
"""
import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "test15"))

WORD = re.compile(r'[A-Z]?[a-z0-9]+')
SPELLED = re.compile(r'\b\w+\s+dot\s+\w+\b', re.I)
PATH = re.compile(r'(?<![\w.])/(etc|usr|opt|var|tmp|root|home|Users)/|~/[\w.]|\.env\b|\.ssh/|id_rsa')
ALLCAPS_LONG = re.compile(r'[A-Z][A-Z0-9]*(?:_[A-Z0-9]+){4,}')


def main():
    blob = json.load(open(os.path.join(HERE, "bytecode-identifiers.json")))
    author = blob["identifiers"]
    synth = blob["synthetic"]
    both = author + synth
    n = len(both)
    print(f"# {len(author)} author-written + {len(synth)} compiler-generated = {n} identifiers")
    print(f"# from {len(blob['libraries'])} artifacts, {len(blob['binary_only'])} of which ship "
          f"no source\n")

    print("WHAT IS BEING COUNTED")
    print("  Each row is one rule from test10's catalogue, applied to identifiers recovered from")
    print("  COMPILED CLASSES. Every identifier here is real and benign, so a hit is a FALSE")
    print("  POSITIVE — the cost of running that rule over bytecode. Lower is better.")
    print("  `author` are names a person chose; `generated` are protobuf tables, $SwitchMap$")
    print("  arrays, synthetic accessors and the like, which no source-level rule was priced")
    print("  against because they never appear in source.\n")

    RULES = {
        "space in the name":      lambda i: " " in i,
        "spelled punctuation":    lambda i: bool(SPELLED.search(i)),
        "filesystem path":        lambda i: bool(PATH.search(i)),
        "constant word count>=5": lambda i: bool(ALLCAPS_LONG.search(i)),
        "word count >= 7":        lambda i: len(WORD.findall(i)) >= 7,
        "word count >= 9":        lambda i: len(WORD.findall(i)) >= 9,
    }
    print(f"  {'rule':<24}{'author-written':>26}{'compiler-generated':>26}")
    for name, fn in RULES.items():
        a = sum(1 for i in author if fn(i))
        s = sum(1 for i in synth if fn(i))
        print(f"  {name:<24}{a:>12} of {len(author):<8} {a/len(author):>6.4%}"
              f"{s:>12} of {len(synth):<8} {s/len(synth):>6.4%}")

    print("\n## what the word-count rule actually hits\n")
    long = [i for i in author if len(WORD.findall(i)) >= 9]
    kinds = {"protobuf-ish": 0, "$SwitchMap$": 0, "other": 0}
    for i in long:
        if i.startswith("$SwitchMap$"):
            kinds["$SwitchMap$"] += 1
        elif i.startswith("internal_static_") or "_descriptor" in i:
            kinds["protobuf-ish"] += 1
        else:
            kinds["other"] += 1
    print("  " + ", ".join(f"{k} {v}" for k, v in kinds.items()))
    print("  examples: " + ", ".join(i[:52] for i in long[:4]))

    print("\n## the spaced identifiers, in full\n")
    for i in both:
        if " " in i:
            print(f"  {i}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
