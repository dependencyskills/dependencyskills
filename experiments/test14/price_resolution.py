#!/usr/bin/env python3
"""
test14 — what does the surviving prose rule actually cost?

`test13` left exactly one live candidate. Legitimate directives in real published library
documentation name **an API the library declares** (63%); the injected ones named nothing declared
(0%). That is `test10`'s resolution check applied to prose rather than to identifiers, which puts
it in the category with an unbroken record here — resolving against a declared surface — rather
than the category that has failed every time, which is recognising attacks.

**An unpriced rule is not a result.** RAD-0021 was withdrawn on exactly this number, `test10` prices
every rule it ships, and `test13` watched a rule go from 4% to 29.8% when the population widened.

    How often does a directive in REAL library documentation point at something the library
    never declares?

THE NUMBER IS USELESS WITHOUT THE REASON. A legitimate directive naming an **inherited** or
**cross-library** symbol will not resolve against a surface extracted from one library's own
sources, and would be counted as an injection. So this does not report a single rate; it reports a
**taxonomy of why resolution failed**. If the cost is dominated by cross-library references, the
rule is not wrong — the surface is too narrow, and `test1` measured that a transitive graph join
recovers that more cheaply than Dokka (which in any case reaches only the Kotlin fifth of this
corpus).

Reads the local Gradle cache. No network, nothing executed.

Run:  uv run --with tree-sitter --with tree-sitter-language-pack python price_resolution.py
      ... --limit 200     # fewer libraries, for a quick pass
"""
import json
import os
import re
import sys
import zipfile
from collections import Counter, defaultdict

CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
HERE = os.path.dirname(os.path.abspath(__file__))
PRIVATE = os.path.join(HERE, "..", "test12", "private-groups.txt")
OUT = os.path.join(HERE, "resolution-results.json")

DECL_NODES = {"function_declaration", "class_declaration", "object_declaration",
              "property_declaration", "type_alias", "enum_entry",
              "method_declaration", "field_declaration", "constructor_declaration",
              "interface_declaration", "enum_declaration", "record_declaration"}

DOC = re.compile(rb'/\*\*(.*?)\*/', re.S)
CODE_FENCE = re.compile(r'```.*?```|\{@\w+[^}]*\}', re.S)
# Language keywords and built-in types are not a library's API surface. They appear in backticks
# constantly - "pass in `null`", "will be made `final`" - and every one was a false positive.
KEYWORDS = {
    "null", "true", "false", "this", "super", "final", "static", "public", "private", "protected",
    "abstract", "open", "override", "val", "var", "fun", "class", "object", "interface", "return",
    "throws", "throw", "new", "void", "int", "long", "short", "byte", "char", "float", "double",
    "boolean", "String", "Object", "Unit", "Any", "Nothing", "List", "Map", "Set", "Array",
    "Integer", "Boolean", "Double", "Float", "Long", "Byte", "Short", "Character", "Number",
    "Iterable", "Collection", "Exception", "Throwable", "RuntimeException", "Error", "Class",
    "inheritDoc", "code", "link", "see", "param", "deprecated",
}
STAR = re.compile(r'^\s*\*ā?', re.M)
TAG_ONLY = re.compile(r'^\s*@\w+')
DIRECTIVE = re.compile(r'\b(use|prefer|do not|don\'t|always|never|must|should|avoid|ensure|'
                       r'call|copy|write|send|read|append|include)\b', re.I)
CODE_SPAN = re.compile(r'`([^`]+)`')
WORD = re.compile(r'[A-Za-z_][A-Za-z0-9_]*')
# A token worth resolving. Bare PascalCase is DELIBERATELY excluded: English capitalises the
# first word of every sentence, so `Cancel the subscription...` offered `Cancel` as a code
# reference and the rule dutifully failed to resolve it. That inflated the cost from a parser
# artifact rather than from the phenomenon — the same error `test7` made twice. camelCase has no
# such collision, because prose does not produce it.
REFLIKE = re.compile(r'^[a-z]+[A-Z][A-Za-z0-9]*$')


def load_private():
    if not os.path.exists(PRIVATE):
        sys.exit("refusing to run: ../test12/private-groups.txt is missing")
    out = []
    for line in open(PRIVATE):
        line = line.split("#", 1)[0].strip()
        if line:
            out.append(line)
    return out


def clean_doc(raw):
    text = STAR.sub("", raw.decode("utf-8", "replace"))
    text = CODE_FENCE.sub(" ", text)          # examples are not directives
    keep = [l.strip() for l in text.splitlines()
            if l.strip() and not TAG_ONLY.match(l.strip())]
    return re.sub(r'\s+', ' ', " ".join(keep)).strip()


def library_records(private, limit=None):
    """Per library: the names it declares, and its doc comments. One pass, newest version only."""
    from tree_sitter_language_pack import get_parser
    parsers = {"kotlin": get_parser("kotlin"), "java": get_parser("java")}
    seen = 0
    for group in sorted(os.listdir(CACHE)):
        gp = os.path.join(CACHE, group)
        if not os.path.isdir(gp):
            continue
        if any(group == p or group.startswith(p + ".") for p in private):
            continue
        for artifact in sorted(os.listdir(gp)):
            ap = os.path.join(gp, artifact)
            if not os.path.isdir(ap):
                continue
            vers = sorted((v for v in os.listdir(ap) if os.path.isdir(os.path.join(ap, v))),
                          reverse=True)[:1]
            for version in vers:
                vp = os.path.join(ap, version)
                jar = None
                for h in os.listdir(vp):
                    hp = os.path.join(vp, h)
                    if os.path.isdir(hp):
                        for f in os.listdir(hp):
                            if f.endswith("-sources.jar"):
                                jar = os.path.join(hp, f)
                                break
                    if jar:
                        break
                if not jar:
                    continue
                try:
                    z = zipfile.ZipFile(jar)
                except (zipfile.BadZipFile, OSError):
                    continue
                declared, docs = set(), []
                with z:
                    for n in z.namelist():
                        lang = ("kotlin" if n.endswith(".kt")
                                else "java" if n.endswith(".java") else None)
                        if not lang:
                            continue
                        try:
                            src = z.read(n)
                        except (zipfile.BadZipFile, OSError, RuntimeError):
                            continue
                        # declarations
                        stack = [parsers[lang].parse(src).root_node]
                        while stack:
                            nd = stack.pop()
                            if nd.type in DECL_NODES:
                                for ch in nd.children:
                                    if ch.type in ("simple_identifier", "identifier",
                                                   "type_identifier"):
                                        declared.add(
                                            src[ch.start_byte:ch.end_byte].decode("utf-8", "replace"))
                                        break
                            stack.extend(nd.children)
                        # doc comments
                        for m in DOC.finditer(src):
                            body = clean_doc(m.group(1))
                            if len(body.split()) >= 6:
                                docs.append(body)
                if declared and docs:
                    seen += 1
                    yield {"library": f"{group}:{artifact}:{version}", "publisher": group,
                           "declared": declared, "docs": docs}
                    if limit and seen >= limit:
                        return


def references(sentence):
    """What a directive points at: backticked code spans, plus bare camel/Pascal tokens."""
    toks = set()
    for span in CODE_SPAN.findall(sentence):
        for t in WORD.findall(span):
            if len(t) > 2 and t not in KEYWORDS:
                toks.add(t)
    bare = CODE_SPAN.sub(" ", sentence)
    for t in WORD.findall(bare):
        if len(t) > 2 and REFLIKE.match(t) and t not in KEYWORDS:
            toks.add(t)
    return toks


def main():
    limit = None
    if "--limit" in sys.argv:
        limit = int(sys.argv[sys.argv.index("--limit") + 1])
    private = load_private()

    libs = []
    for rec in library_records(private, limit):
        libs.append(rec)
        if len(libs) % 100 == 0:
            print(f"  … {len(libs)} libraries", file=sys.stderr)

    universe = set()
    for l in libs:
        universe |= l["declared"]

    counts = Counter()
    unresolved_examples = []
    per_publisher = Counter()

    # KMP publishes one artifact per target, so the same doc comment appears in `annotation`,
    # `annotation-iosarm64`, `annotation-js` and so on. Counting each is counting a publishing
    # convention, not a corpus. `test5` measured 63% of a real harvest is duplicate; dedupe by
    # (publisher, doc) so a rule is priced against distinct documentation.
    seen = set()
    for l in libs:
        own = l["declared"]
        for doc in l["docs"]:
            key = (l["publisher"], doc)
            if key in seen:
                counts["duplicate"] += 1
                continue
            seen.add(key)
            counts["docs"] += 1
            if not DIRECTIVE.search(doc):
                continue
            counts["directive"] += 1
            refs = references(doc)
            if not refs:
                counts["no_reference"] += 1
                continue
            counts["with_reference"] += 1
            if refs & own:
                counts["resolves_own"] += 1
            elif refs & universe:
                counts["resolves_cross_library"] += 1
            else:
                counts["resolves_nowhere"] += 1
                per_publisher[l["publisher"]] += 1
                if len(unresolved_examples) < 10:
                    unresolved_examples.append((l["library"], doc[:110]))

    d = counts["docs"] or 1
    dire = counts["directive"] or 1
    print(f"\n# {len(libs)} libraries, {len(universe):,} distinct declared names, "
          f"{counts['docs']:,} doc comments\n")
    print("**Key** — the rule flags a directive whose references resolve nowhere. Every other row")
    print("is a reason it did NOT flag, and `cross-library` is the row that decides whether the")
    print("rule is wrong or the surface is too narrow.\n")
    print(f"{'outcome':<34}{'count':>10}{'of directives':>16}{'of all docs':>14}")
    for k, label in (("directive", "contain a directive verb"),
                     ("no_reference", "  no code reference at all"),
                     ("resolves_own", "  resolves in its own library"),
                     ("resolves_cross_library", "  resolves in ANOTHER library"),
                     ("resolves_nowhere", "  RESOLVES NOWHERE — flagged")):
        c = counts[k]
        print(f"{label:<34}{c:>10}{c/dire:>15.1%}{c/d:>14.2%}")

    cost = counts["resolves_nowhere"] / d
    print(f"\n  RULE COST on all real documentation: {cost:.3%}")
    print(f"  (test10's entire catalogue costs 0.221%)")

    print("\n# where the flags land\n")
    for p, n in per_publisher.most_common(8):
        print(f"  {n:>6}  {p}")

    print("\n# a sample of what it flags — is this an attack, or a false positive?\n")
    for lib, doc in unresolved_examples:
        print(f"  [{lib.split(':')[1][:28]:<28}] {doc}")

    with open(OUT, "w") as fh:
        json.dump({"libraries": len(libs), "universe": len(universe),
                   "counts": dict(counts), "cost": cost,
                   "per_publisher": dict(per_publisher.most_common(40))}, fh, indent=1)
    print(f"\n  wrote {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
