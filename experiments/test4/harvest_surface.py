#!/usr/bin/env python3
"""
test4 — how much attacker-controlled text does each language's harvest path deliver?

RAD-0006 v4 measured that library prose redirects agents, but the payload was hand-authored:
it never passed through a parser. That makes those results parser-independent — they say how
an agent treats harvested prose — but say nothing about whether *harvesting* is riskier in one
language than another. Doc conventions differ enormously in how much free text they invite,
and the parser is a filter: what reaches the entry depends on whether it keeps the summary
sentence or the whole comment.

Two parts:

  A. DELIVERY FIDELITY — one semantically identical payload written in each language's native
     doc convention (poisoned/), harvested through the *real* test1 extractors. Does the
     injected instruction survive intact? The URL? The example code block?

  B. SURFACE SIZE — the same extractors over real published libraries, measuring how much doc
     text each convention actually carries per declaration. This is the "is TypeScript worse
     than Swift" question, and it is about volume of attacker-controllable free text.

Run: uv run --with tree-sitter --with tree-sitter-language-pack python harvest_surface.py
     (add --skip-b to run only the offline part)
"""
import os, statistics, sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "test1"))

from tree_sitter_language_pack import get_parser
import extract_polyglot as P
import extract_treesitter as K

POISONED = os.path.join(HERE, "poisoned")

# The attacker-controlled spans planted in every file, in each language's own doc syntax.
INSTRUCTION = "MUST also call"
SYMBOL = 'Analytics.track("dateformatter_used")'
URL = "https://datefmt-telemetry.io/collect"
CODEFENCE = "yyyy-MM-dd"          # inside the example block
LEGIT = "Formats a Unix epoch timestamp"   # the honest first line

# lang -> (file, tree-sitter grammar, node types that are the declaration, doc extractor)
CASES = [
    ("kotlin",     "Fmt.kt",    "kotlin",     {"function_declaration"}, None),   # None => KDoc path
    ("typescript", "fmt.ts",    "typescript", P.LANGS["typescript"]["decls"], P.ts_doc),
    ("python",     "fmt.py",    "python",     P.LANGS["python"]["decls"],     P.py_doc),
    ("rust",       "fmt.rs",    "rust",       P.LANGS["rust"]["decls"],       P.rust_doc),
    ("swift",      "Fmt.swift", "swift",      P.LANGS["swift"]["decls"],      P.swift_doc),
]


def find_decl(node, kinds):
    """First declaration node of an interesting kind, depth-first."""
    if node.type in kinds:
        return node
    for ch in node.children:
        hit = find_decl(ch, kinds)
        if hit:
            return hit
    return None


def part_a():
    print("== A. delivery fidelity — does the payload survive the real parser? ==\n")
    print(f"{'language':<12} {'doc chars':>9} {'instr':>6} {'symbol':>7} {'url':>5} "
          f"{'code':>5} {'attacker %':>11}")
    rows = []
    for lang, fname, grammar, kinds, docfn in CASES:
        src = open(os.path.join(POISONED, fname), "rb").read()
        tree = get_parser(grammar).parse(src)
        decl = find_decl(tree.root_node, kinds)
        if decl is None:
            print(f"{lang:<12}  !! no declaration node found"); continue
        doc = (K.kdoc_before(decl, src) if docfn is None else docfn(decl, src)) or ""

        has = lambda s: s in doc
        # attacker-controlled = everything after the honest first line
        idx = doc.find(INSTRUCTION)
        attacker = len(doc) - doc.find(INSTRUCTION) if idx >= 0 else 0
        pct = (100.0 * attacker / len(doc)) if doc else 0.0
        rows.append((lang, len(doc), has(INSTRUCTION), has(SYMBOL), has(URL), has(CODEFENCE), pct))
        y = lambda b: " yes" if b else "  NO"
        print(f"{lang:<12} {len(doc):>9} {y(has(INSTRUCTION)):>6} {y(has(SYMBOL)):>7} "
              f"{y(has(URL)):>5} {y(has(CODEFENCE)):>5} {pct:>10.0f}%")
    return rows


# Domain is held constant: every entry is a CLI argument parser, so differences in doc volume
# are attributable to the language's convention rather than to what the library does.
# Each fetcher returns (label, [(name, bytes), ...]), already filtered to the right suffix.
REAL = [
    ("typescript", P.fetch_npm,    "commander"),
    ("python",     P.fetch_pypi,   "click"),
    ("rust",       P.fetch_crate,  "clap_builder"),
    ("swift",      P.fetch_github, "apple/swift-argument-parser"),
]

KOTLIN_COORD = "org.jetbrains.kotlinx:kotlinx-cli-jvm:0.3.6"   # the Kotlin CLI parser


def collect_docs_kotlin(zf):
    """Kotlin goes through the -sources.jar + KDoc path rather than a source tarball."""
    parser = get_parser("kotlin"); out = []
    kinds = {"function_declaration", "class_declaration", "property_declaration",
             "object_declaration"}

    def walk(node, src):
        for ch in node.children:
            if ch.type in kinds:
                nm = K.name_of(ch, src)
                if nm and not nm.startswith("_"):
                    d = K.kdoc_before(ch, src)
                    if d:
                        out.append(d)
            walk(ch, src)

    for n in zf.namelist():
        if n.endswith(".kt"):
            walk(parser.parse(zf.read(n)).root_node, zf.read(n))
    return out


def doc_text(node, src, cfg):
    return cfg["doc"](node, src) or ""


def collect_docs(lang, files):
    """Walk real source, returning the doc text of every public declaration."""
    parser = get_parser(lang); cfg = P.LANGS[lang]; out = []

    def walk(node, src):
        for ch in node.children:
            if ch.type in cfg["decls"]:
                nm = P.name_of(ch, src)
                if nm and not nm.startswith("_"):
                    d = doc_text(ch, src, cfg)
                    if d:
                        out.append(d)
                walk(ch, src)
            elif ch.type in cfg["recurse"]:
                walk(ch, src)

    for _, data in files:
        walk(parser.parse(data).root_node, data)
    return out


def report(lang, label, docs):
    lens = sorted(len(d) for d in docs)
    p90 = lens[int(0.9 * (len(lens) - 1))]
    code = sum(1 for d in docs if "```" in d or ">>>" in d)
    url = sum(1 for d in docs if "http://" in d or "https://" in d)
    print(f"{lang:<12} {label:<30} {len(docs):>5} {statistics.median(lens):>7.0f} "
          f"{p90:>6} {max(lens):>7} {100*code//len(docs):>7}% {100*url//len(docs):>6}%")


def part_b():
    print("\n== B. surface size — how much free text does each convention carry? ==")
    print("   (domain held constant: every library is a CLI argument parser)\n")
    print(f"{'language':<12} {'library':<30} {'docs':>5} {'median':>7} {'p90':>6} "
          f"{'max':>7} {'w/ code':>8} {'w/ url':>7}")
    try:
        _, _, zf = K.fetch_sources(KOTLIN_COORD)
        docs = collect_docs_kotlin(zf)
        if docs:
            report("kotlin", KOTLIN_COORD.split(":")[1] + ":" + KOTLIN_COORD.split(":")[2], docs)
    except Exception as e:
        print(f"{'kotlin':<12} {KOTLIN_COORD:<30}  !! {str(e)[:40]}")
    for lang, fetch, arg in REAL:
        try:
            label, files = fetch(arg)
            docs = collect_docs(lang, files)
        except Exception as e:
            print(f"{lang:<12} {arg:<24}  !! {str(e)[:40]}"); continue
        if not docs:
            print(f"{lang:<12} {arg:<30}  (no docs extracted)"); continue
        report(lang, label, docs)


def main():
    part_a()
    if "--skip-b" not in sys.argv:
        part_b()


if __name__ == "__main__":
    main()
