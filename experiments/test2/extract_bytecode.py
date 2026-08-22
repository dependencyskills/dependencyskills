#!/usr/bin/env python3
"""
test2 — structure from bytecode (RAD-0012).

Many libraries ship a compiled `.jar` but **no `-sources.jar`**. This asks whether the
bytecode's public API structure recovers usable capability entries when there is no source
to parse — harvesting kaml BOTH ways and comparing:

  source path    : tree-sitter on the `-sources.jar`  (test1) — name + PARAM NAMES + KDoc
  structure path : `javap -public` on the compiled `.jar`     — name + types, NO param
                   names, NO docs, but fully-qualified SUPERTYPES

Kept separate from test0/test1 so the doc-path data stays clean (RAD-0012).

Run: uv run --with tree-sitter --with tree-sitter-language-pack extract_bytecode.py
"""
import io, os, re, subprocess, sys, urllib.request, zipfile

sys.path.insert(0, os.path.join(os.path.dirname(__file__), "..", "test1"))
from extract_treesitter import fetch_sources, extract   # the source path (tree-sitter)

REPO = "https://repo1.maven.org/maven2"
JAR = "/tmp/_test2_bytecode.jar"


def fetch_jar(coord):
    g, a, v = coord.split(":")
    url = f"{REPO}/{g.replace('.', '/')}/{a}/{v}/{a}-{v}.jar"
    open(JAR, "wb").write(urllib.request.urlopen(url, timeout=180).read())
    z = zipfile.ZipFile(JAR)
    return [n[:-6].replace("/", ".") for n in z.namelist()
            if n.endswith(".class") and "$" not in n]


def javap_structure(classes):
    out = subprocess.run(["javap", "-public", "-classpath", JAR] + classes,
                         capture_output=True, text=True).stdout
    classes_seen, methods, supers = 0, 0, {}
    cur = None
    for line in out.splitlines():
        m = re.match(r'\s*(?:public|final|abstract|\s)*(class|interface)\s+([\w.]+)(.*)\{', line)
        if m:
            cur = m.group(2); classes_seen += 1
            sup = re.findall(r'(?:implements|extends)\s+([\w.,<>?\s]+?)(?:\s*\{|$)', m.group(3))
            names = re.findall(r'([a-zA-Z_][\w.]*\.[A-Z]\w+)', " ".join(sup))
            supers[cur] = [n for n in names if "." in n]
            continue
        if cur and re.match(r'\s+public.*\(.*\);\s*$', line) and " class " not in line:
            methods += 1
    return classes_seen, methods, supers


def main():
    src_coord = "com.charleskorn.kaml:kaml:0.104.0"
    bc_coord = "com.charleskorn.kaml:kaml-jvm:0.104.0"

    _, _, zf = fetch_sources(src_coord)
    src = extract(zf)
    src_doc = sum(e["has_kdoc"] for e in src)

    classes = fetch_jar(bc_coord)
    ncls, nmeth, supers = javap_structure(classes)

    print("== kaml, harvested two ways ==")
    print(f"source  (tree-sitter/-sources.jar): {len(src):>4} public decls | "
          f"{src_doc} documented | param names: YES")
    print(f"bytecode (javap/compiled .jar):     {ncls} classes + {nmeth} public methods | "
          f"0 documented | param names: NO | supertypes: FQ")
    # the payoff: bytecode supertypes are fully-qualified -> resolve-in-index needs no imports
    y = supers.get("com.charleskorn.kaml.Yaml", [])
    print(f"\nbytecode supertype edge (no import resolution needed): Yaml -> {y}")

    # prove resolve-in-index works from the bytecode edge, against a tree-sitter index of the supertype lib
    _, _, szf = fetch_sources("org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0")
    idx = {e["symbol"]: e for e in extract(szf)}
    for st in y:
        e = idx.get(f"{st}.decodeFromString")
        if e and e["has_kdoc"]:
            print(f"resolve-in-index from bytecode: Yaml.decodeFromString  ←  {st}.decodeFromString (documented)")
            break


if __name__ == "__main__":
    main()
