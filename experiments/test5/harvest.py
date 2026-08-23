#!/usr/bin/env python3
"""
test5 — harvest a real dependency graph into codex entries.

The smallest thing that answers test5's three questions. Reuses `../test1`'s fetcher and
Kotlin extractor and `../test3`'s URL-grounding signal rather than reimplementing either.

Emits one entry per documented public declaration:

    symbol      fully-qualified name
    signature   the declaration line (the syntactic face, RAD-0013)
    doc         the RAW KDoc text - NOT summarised into caller's words. Its absence is
                question 1, so nothing here rewrites it.
    library     the coordinate it came from
    provenance  "direct" or "transitive", carried from the resolved graph
    url_flag    RAD-0021's surviving signal, attached as a label rather than a gate

Undocumented declarations are dropped: with no summarise step a bare signature has no
retrieval key, and RAD-0011 already measured how many there are (median 33% documented).

Run:  uv run --with tree-sitter --with tree-sitter-language-pack python harvest.py [graph]
Out:  corpus.json
"""
import json, os, re, sys, collections

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "test1"))

from tree_sitter_language_pack import get_parser
import extract_treesitter as K

GRAPH = os.path.join(HERE, "..", "cost-model", "data",
                     (sys.argv[1] if len(sys.argv) > 1 else "ktor-filelisting") + ".tsv")
OUT = os.path.join(HERE, "corpus.json")
URL_RE = re.compile(r'https?://[^\s`\)\]>"]+')
DECLS = {"function_declaration", "class_declaration", "property_declaration",
         "object_declaration"}


def read_graph(path):
    """coordinate -> 'direct' | 'transitive'  (direct wins if a coordinate appears as both)."""
    prov = {}
    for line in open(path):
        parts = line.rstrip("\n").split("\t")
        if len(parts) < 3:
            continue
        kind, coord = parts[1], parts[2]
        if kind not in ("direct", "transitive"):
            continue
        if coord.count(":") < 2:            # want group:artifact:version
            continue
        if prov.get(coord) != "direct":
            prov[coord] = kind
    return prov


def entries_for(coord, provenance, parser):
    """Fetch one library's -sources.jar and pull documented public declarations."""
    label, _, zf = K.fetch_sources(coord)
    out = []
    for name in zf.namelist():
        if not name.endswith(".kt"):
            continue
        src = zf.read(name)
        root = parser.parse(src).root_node
        pkg = ""
        for ch in root.children:                      # package header, if any
            if ch.type == "package_header":
                pkg = K.txt(src, ch).replace("package", "").strip()
                break

        def walk(node, prefix):
            for ch in node.children:
                if ch.type in DECLS:
                    nm = K.name_of(ch, src)
                    if nm and not nm.startswith("_"):
                        doc = K.kdoc_before(ch, src)
                        if doc and doc.strip():
                            sym = ".".join(p for p in (pkg, prefix, nm) if p)
                            out.append({
                                "symbol": sym,
                                "signature": K.signature(ch, src),
                                "doc": doc.strip(),
                                "library": label,
                                "provenance": provenance,
                                "url_flag": bool(URL_RE.search(doc)),
                            })
                    walk(ch, ".".join(p for p in (prefix, nm or "") if p))
                else:
                    walk(ch, prefix)

        walk(root, "")
    return out


def main():
    prov = read_graph(GRAPH)
    print(f"# {os.path.basename(GRAPH)}: {len(prov)} coordinates "
          f"({sum(v=='direct' for v in prov.values())} direct, "
          f"{sum(v=='transitive' for v in prov.values())} transitive)\n")

    parser = get_parser("kotlin")
    corpus, ok, no_sources = [], 0, []
    for i, (coord, p) in enumerate(sorted(prov.items()), 1):
        try:
            got = entries_for(coord, p, parser)
        except Exception as e:
            no_sources.append((coord, str(e)[:40])); continue
        ok += 1
        corpus.extend(got)
        if got:
            print(f"  [{i:>3}/{len(prov)}] {p:<10} {coord.split(':')[1][:38]:<38} "
                  f"{len(got):>4} entries")

    json.dump(corpus, open(OUT, "w"), indent=1)
    byp = collections.Counter(e["provenance"] for e in corpus)
    libs = collections.Counter(e["provenance"] for e in
                               {e["library"]: e for e in corpus}.values())
    print(f"\n# libraries with a -sources.jar: {ok}/{len(prov)}   "
          f"(no sources / fetch failed: {len(no_sources)})")
    print(f"# libraries contributing entries: {sum(libs.values())} "
          f"({libs['direct']} direct, {libs['transitive']} transitive)")
    print(f"# ENTRIES: {len(corpus)}  ({byp['direct']} direct, {byp['transitive']} transitive)")
    print(f"# url-flagged entries: {sum(e['url_flag'] for e in corpus)}")
    lens = sorted(len(e["doc"]) for e in corpus) or [0]
    print(f"# doc length: median {lens[len(lens)//2]}  p90 {lens[int(.9*(len(lens)-1))]}  "
          f"max {max(lens)}")
    print(f"# wrote {OUT}")


if __name__ == "__main__":
    main()
