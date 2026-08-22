#!/usr/bin/env python3
"""
test1 Phase B — the other languages.

Extract (symbol, signature, doc-comment) entries from real published source in several
ecosystems, with the SAME tree-sitter rig — proving the "language-agnostic parse behind a
common contract" claim (RAD-0009) outside Kotlin. Because resolution is index-side
(resolve-in-index), each language needs only *local* extraction, not a resolver — just its
declaration node types and its doc-comment convention:

  TypeScript : preceding block comment `/** ... */`  (JSDoc, like KDoc)
  Python     : a string literal as the first body statement  (docstring — different!)
  Rust       : preceding `///` line doc-comments

Source is fetched on demand (npm / PyPI / crates.io), read in place, never stored.

Run: uv run --with tree-sitter --with tree-sitter-language-pack extract_polyglot.py
"""
import io, json, sys, tarfile, urllib.request
from tree_sitter_language_pack import get_parser

UA = {"User-Agent": "dependencyskills-experiment/0.1 (research)"}


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=180).read()


def _tar_files(data, suffix, skip):
    tf = tarfile.open(fileobj=io.BytesIO(data), mode="r:gz")
    out = []
    for m in tf.getmembers():
        if m.isfile() and m.name.endswith(suffix) and not any(s in m.name for s in skip):
            out.append((m.name, tf.extractfile(m).read()))
    return out


def fetch_npm(pkg):
    meta = json.loads(get(f"https://registry.npmjs.org/{pkg}"))
    ver = meta["dist-tags"]["latest"]
    data = get(meta["versions"][ver]["dist"]["tarball"])
    files = _tar_files(data, ".d.ts", ("/test", "/node_modules/"))
    return f"{pkg}@{ver}", files


def fetch_pypi(pkg):
    meta = json.loads(get(f"https://pypi.org/pypi/{pkg}/json"))
    ver = meta["info"]["version"]
    sdist = next(u for u in meta["urls"] if u["packagetype"] == "sdist")
    files = _tar_files(get(sdist["url"]), ".py", ("/test", "/_vendor", "setup.py"))
    return f"{pkg}=={ver}", files


def fetch_crate(crate):
    meta = json.loads(get(f"https://crates.io/api/v1/crates/{crate}"))
    ver = meta["crate"]["max_stable_version"]
    data = get(f"https://crates.io/api/v1/crates/{crate}/{ver}/download")
    files = _tar_files(data, ".rs", ("/tests/", "/examples/", "/benches/"))
    return f"{crate}:{ver}", files


def fetch_github(repo):  # repo = "owner/name" — Swift/SwiftPM has no central tarball registry
    tag = json.loads(get(f"https://api.github.com/repos/{repo}/releases/latest"))["tag_name"]
    data = get(f"https://github.com/{repo}/archive/refs/tags/{tag}.tar.gz")
    files = _tar_files(data, ".swift", ("/Tests/", "/.build/", "/Snapshots/"))
    return f"{repo}@{tag}", files


def txt(src, n):
    return src[n.start_byte:n.end_byte].decode("utf-8", "replace")


def name_of(node, src, kinds=("identifier", "type_identifier", "property_identifier", "simple_identifier")):
    for ch in node.children:
        if ch.type in kinds:
            return txt(src, ch)
    return None


def sig_line(node, src):
    return txt(src, node).split("\n", 1)[0].strip()[:200]


# ---- per-language declaration kinds + doc-comment rules ----

def ts_doc(node, src):
    # exported decls are wrapped in `export_statement`; the JSDoc precedes the wrapper
    target = node.parent if node.parent and node.parent.type == "export_statement" else node
    p = target.prev_sibling
    if p and p.type == "comment" and txt(src, p).startswith("/**"):
        return txt(src, p)
    return None


def py_doc(node, src):
    blk = next((c for c in node.children if c.type == "block"), None)
    if not blk:
        return None
    for st in blk.children:
        if st.type == "expression_statement":
            s = next((c for c in st.children if c.type == "string"), None)
            return txt(src, s) if s else None
        if st.type == "string":
            return txt(src, st)
        if st.type != "comment":
            return None
    return None


def rust_doc(node, src):
    docs, p = [], node.prev_sibling
    while p is not None:
        if p.type == "attribute_item":
            p = p.prev_sibling; continue
        if p.type in ("line_comment", "block_comment"):
            t = txt(src, p)
            if t.startswith("///") or t.startswith("/**"):
                docs.append(t); p = p.prev_sibling; continue
        break
    return "\n".join(reversed(docs)) if docs else None


def swift_doc(node, src):
    docs, p = [], node.prev_sibling
    while p is not None and p.type == "comment":
        t = txt(src, p)
        if t.startswith("///") or t.startswith("/**"):
            docs.append(t); p = p.prev_sibling
        else:
            break
    return "\n".join(reversed(docs)) if docs else None


LANGS = {
    "typescript": dict(
        decls={"function_declaration", "class_declaration", "interface_declaration",
               "method_definition", "method_signature", "function_signature",
               "type_alias_declaration", "enum_declaration"},
        doc=ts_doc, recurse={"export_statement", "class_declaration", "interface_declaration",
                             "class_body", "interface_body", "object_type",
                             "internal_module", "module", "ambient_declaration"}),
    "python": dict(
        decls={"function_definition", "class_definition"},
        doc=py_doc, recurse={"class_definition", "block"}),
    "rust": dict(
        decls={"function_item", "struct_item", "enum_item", "trait_item"},
        doc=rust_doc, recurse={"trait_item", "declaration_list", "impl_item"}),
    "swift": dict(
        decls={"function_declaration", "class_declaration", "protocol_declaration",
               "property_declaration"},
        doc=swift_doc, recurse={"class_declaration", "protocol_declaration",
                                "class_body", "protocol_body"}),
}


def walk(node, src, cfg, out, prefix=""):
    for ch in node.children:
        if ch.type in cfg["decls"]:
            nm = name_of(ch, src)
            if nm and not nm.startswith("_"):
                kd = cfg["doc"](ch, src)
                out.append(dict(symbol=(prefix + "." + nm).lstrip("."), kind=ch.type,
                                signature=sig_line(ch, src), has_doc=bool(kd)))
            walk(ch, src, cfg, out, (prefix + "." + (nm or "")).lstrip("."))
        elif ch.type in cfg["recurse"]:
            walk(ch, src, cfg, out, prefix)


def extract(lang, files):
    parser = get_parser(lang); cfg = LANGS[lang]; entries = []
    for _, src in files:
        walk(parser.parse(src).root_node, src, cfg, entries)
    return entries


TARGETS = [
    ("python", "click", fetch_pypi),
    ("typescript", "commander", fetch_npm),
    ("rust", "anyhow", fetch_crate),
    ("swift", "apple/swift-argument-parser", fetch_github),
]


def main():
    print(f"{'ecosystem / package':<34} {'files':>5} {'decls':>6} {'doc':>6} {'cov%':>5}")
    for lang, pkg, fetch in TARGETS:
        try:
            pinned, files = fetch(pkg)
        except Exception as e:
            print(f"{lang}/{pkg:<28} FETCH FAILED: {str(e).splitlines()[-1][:34]}")
            continue
        es = extract(lang, files)
        n = len(es); d = sum(e["has_doc"] for e in es)
        print(f"{(lang+' / '+pinned):<34} {len(files):>5} {n:>6} {d:>6} {round(100*d/n) if n else 0:>4}%")
        for e in [x for x in es if x["has_doc"]][:2]:
            print(f"      e.g. {e['symbol']}  ::  {e['signature'][:70]}")


if __name__ == "__main__":
    main()
