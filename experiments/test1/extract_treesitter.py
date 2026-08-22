#!/usr/bin/env python3
"""
test1 Phase A — the tree-sitter (raw) arm.

Extract (symbol, signature, KDoc) entries from a real Maven `-sources.jar`, reading the
archive IN PLACE (RAD-0015 — no extraction), with tree-sitter-kotlin. This is the "raw"
half of the tree-sitter-vs-Dokka bake-off: what a fast syntactic parse surfaces from real,
un-resolved source across the KDoc-coverage spectrum.

Jars are fetched on demand from Maven Central and never stored in the repo — the coordinate
is the reference. Run:
  uv run --with tree-sitter --with tree-sitter-language-pack extract_treesitter.py \
      com.charleskorn.kaml:kaml:0.104.0 [more coords...]
Add --json <dir> to dump per-library entries.
"""
import sys, io, os, json, re, urllib.request, zipfile
import xml.etree.ElementTree as ET
from tree_sitter_language_pack import get_parser

REPO = "https://repo1.maven.org/maven2"
KP = get_parser("kotlin")


def latest_version(group, artifact):
    url = f"{REPO}/{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
    root = ET.fromstring(urllib.request.urlopen(url, timeout=60).read())
    return (root.findtext("versioning/release") or root.findtext("versioning/latest")
            or [e.text for e in root.findall("versioning/versions/version")][-1])


def fetch_sources(coord):
    p = coord.split(":")
    group, artifact = p[0], p[1]
    version = p[2] if len(p) > 2 else latest_version(group, artifact)
    url = f"{REPO}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}-sources.jar"
    data = urllib.request.urlopen(url, timeout=180).read()
    return f"{group}:{artifact}:{version}", len(data), zipfile.ZipFile(io.BytesIO(data))


def txt(src, n):
    return src[n.start_byte:n.end_byte].decode("utf-8", "replace")


def name_of(node, src):
    for ch in node.children:
        if ch.type in ("simple_identifier", "type_identifier"):
            return txt(src, ch)
    return None


def kdoc_before(node, src):
    p = node.prev_sibling
    if p and p.type in ("modifiers",):          # comments can precede modifiers
        p = node.prev_sibling
    if p and p.type == "multiline_comment":
        t = txt(src, p)
        return t if t.startswith("/**") else None
    return None


def signature(node, src):
    body = {"function_body", "class_body", "enum_class_body", "block"}
    end = node.end_byte
    for ch in node.children:
        if ch.type in body:
            end = ch.start_byte
            break
    return src[node.start_byte:end].decode("utf-8", "replace").strip()


def dotted(*parts):
    return ".".join(x for x in parts if x)


def private(sig, name):
    mods = sig.split(name)[0] if name and name in sig else sig
    return bool(re.search(r"\b(private|internal|protected)\b", mods))


def get_imports(root, src):
    explicit, star = {}, []                     # simple->FQN ; star-imported packages
    for ch in root.children:
        if ch.type == "import_list":
            for imp in ch.children:
                if imp.type != "import_header":
                    continue
                # the path is either one `identifier` node (dotted) or several simple_identifiers
                idn = next((c for c in imp.children if c.type == "identifier"), None)
                if idn is not None:
                    fqn = txt(src, idn)
                else:
                    parts = [txt(src, c) for c in imp.children if c.type == "simple_identifier"]
                    fqn = ".".join(parts) if parts else ""
                if not fqn:
                    continue
                if txt(src, imp).rstrip().endswith("*"):
                    star.append(fqn.rstrip("*").rstrip("."))     # `import pkg.*`
                else:
                    explicit[fqn.split(".")[-1]] = fqn
    return explicit, star


def super_candidates(node, src, explicit, star, pkg):
    # candidate supertype FQNs the index can try (explicit import wins; else star pkgs / own pkg)
    cands = []
    for ch in node.children:
        if ch.type == "delegation_specifier":
            for u in ch.children:
                if u.type == "user_type":
                    ti = next((c for c in u.children if c.type == "type_identifier"), None)
                    if ti:
                        nm = txt(src, ti)
                        if nm in explicit:
                            cands.append(explicit[nm])
                        else:
                            cands += [f"{p}.{nm}" for p in star]
                            if pkg:
                                cands.append(f"{pkg}.{nm}")
                    break
    return cands


def mk(ch, src, pkg, prefix, nm, kind, supers):
    sig = signature(ch, src)
    mods = sig.split(nm, 1)[0] if nm in sig else sig
    kd = kdoc_before(ch, src)
    return dict(symbol=dotted(pkg, prefix, nm), member=nm, kind=kind,
                signature=sig.split("\n")[0].strip()[:200],
                has_kdoc=bool(kd),
                is_override=bool(re.search(r"\boverride\b", mods)),   # inherited-doc candidate
                has_sample=bool(kd and "@sample" in kd),              # resolved-sample candidate
                super_types=supers)                                   # enclosing class's supertypes (FQN)


def walk(node, src, pkg, prefix, out, explicit, star, supers):
    for ch in node.children:
        t = ch.type
        if t in ("class_declaration", "object_declaration"):
            nm = name_of(ch, src); sig = signature(ch, src)
            own = super_candidates(ch, src, explicit, star, pkg)
            if nm and not private(sig, nm):
                out.append(mk(ch, src, pkg, prefix, nm, t[:-12], own))
            walk(ch, src, pkg, dotted(prefix, nm) if nm else prefix, out, explicit, star, own)
        elif t in ("function_declaration", "property_declaration"):
            nm = name_of(ch, src); sig = signature(ch, src)
            if nm and not private(sig, nm):
                out.append(mk(ch, src, pkg, prefix, nm, t[:-12], supers))
        else:
            walk(ch, src, pkg, prefix, out, explicit, star, supers)


def extract(zf):
    entries = []
    for nm in zf.namelist():
        if not nm.endswith(".kt"):
            continue
        src = zf.read(nm)
        tree = KP.parse(src)
        pkg = ""
        for ch in tree.root_node.children:
            if ch.type == "package_header":
                pkg = txt(src, ch).replace("package", "").strip().rstrip(";")
                break
        explicit, star = get_imports(tree.root_node, src)
        walk(tree.root_node, src, pkg, "", entries, explicit, star, [])
    return entries


def main():
    args = sys.argv[1:]
    coords, jdir, i = [], None, 0
    while i < len(args):
        if args[i] == "--json":
            jdir = args[i + 1]; os.makedirs(jdir, exist_ok=True); i += 2
        else:
            coords.append(args[i]); i += 1
    hdr = f"{'library':<44} {'decls':>6} {'kdoc':>5} {'cov%':>5} {'undocOvr':>8} {'@sample':>7}"
    print(hdr)
    for coord in coords:
        try:
            pinned, size, zf = fetch_sources(coord)
        except Exception as e:
            print(f"{coord:<44} FETCH FAILED: {str(e).splitlines()[-1][:40]}")
            continue
        es = extract(zf)
        n = len(es); k = sum(e["has_kdoc"] for e in es)
        undoc_ovr = sum(e["is_override"] and not e["has_kdoc"] for e in es)   # inherited-doc ceiling
        samples = sum(e["has_sample"] for e in es)
        cov = round(100 * k / n) if n else 0
        print(f"{pinned:<44} {n:>6} {k:>5} {cov:>4}% {undoc_ovr:>8} {samples:>7}")
        if jdir:
            with open(os.path.join(jdir, pinned.replace(":", "_") + ".json"), "w") as f:
                json.dump(es, f, indent=2)


if __name__ == "__main__":
    main()
