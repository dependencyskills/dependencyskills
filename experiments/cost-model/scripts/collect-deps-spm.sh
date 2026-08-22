#!/usr/bin/env bash
# Collect the resolved dependency set of a Swift package, same output shape as
# the Gradle and npm collectors.
#
#   ./collect-deps-spm.sh <project-dir> [label]
#
# SPM writes the whole resolved graph to Package.resolved as a flat list of
# pins, and the direct set is the `.package(...)` lines in Package.swift. Both
# are checked in, so direct-vs-transitive needs no build.
#
# The prod/dev split does need the shape of the graph, and Package.resolved
# does not record who pulled what. So each pin's own Package.swift is fetched
# at its pinned revision and the edges are read from it: a package is dev if
# every path to it from the root starts at a test target or a plugin. That is
# a fetch rather than a guess, and it is reproducible — the revisions are in
# Package.resolved.
#
#   Needs network for the manifest fetch. No swift toolchain.
set -euo pipefail
DIR="${1:?usage: collect-deps-spm.sh <project-dir> [label]}"
LABEL="${2:-$(basename "$DIR")}"
HERE="$(cd "$(dirname "$0")" && pwd)"
CACHE="${SPM_MANIFEST_CACHE:-/tmp/spm-manifests}"
mkdir -p "$HERE/../data" "$CACHE"
OUT="$HERE/../data/$LABEL.tsv"

LABEL="$LABEL" CACHE="$CACHE" python3 - "$DIR" > "$OUT" <<'PY'
import json, os, re, sys, urllib.request

d     = sys.argv[1]
label = os.environ["LABEL"]
cache = os.environ["CACHE"]

def identity(url):
    return url.rstrip("/").removesuffix(".git").rsplit("/", 1)[-1].lower()

def pkg_urls(manifest):
    return [identity(u) for u in re.findall(r'\.package\(\s*url:\s*"([^"]+)"', manifest)]

def split_targets(manifest):
    """-> (packages referenced by non-test targets, by test targets)"""
    chunks = re.split(r'\.(testTarget|target|executableTarget|macro)\(', manifest)
    prod, dev = set(), set()
    for kind, body in zip(chunks[1::2], chunks[2::2]):
        refs = {identity(r) for r in re.findall(r'package:\s*"([^"]+)"', body)}
        (dev if kind == "testTarget" else prod).update(refs)
    return prod, dev

def manifest_for(ident, location, revision):
    path = os.path.join(cache, f"{ident}.swift")
    if not os.path.exists(path):
        loc = location.rstrip("/").removesuffix(".git")
        if "github.com" not in loc:
            return ""                       # only GitHub is fetchable this way
        raw = loc.replace("github.com", "raw.githubusercontent.com") + f"/{revision}/Package.swift"
        try:
            with urllib.request.urlopen(raw, timeout=30) as r:
                open(path, "wb").write(r.read())
        except Exception:
            return ""
    return open(path, encoding="utf-8", errors="replace").read()

root_manifest = open(os.path.join(d, "Package.swift")).read()
resolved      = json.load(open(os.path.join(d, "Package.resolved")))
pins          = {p["identity"].lower(): p for p in resolved.get("pins", [])}
versions      = {k: v.get("state", {}).get("version", "") for k, v in pins.items()}

direct = set(pkg_urls(root_manifest))
root_prod, root_dev = split_targets(root_manifest)

# Edges from each dependency's own manifest. A dependency's test-only deps are
# not resolved into a consumer's graph, so only its product-facing edges count.
edges = {}
for ident, pin in pins.items():
    m = manifest_for(ident, pin.get("location", ""), pin.get("state", {}).get("revision", ""))
    if not m:
        edges[ident] = set()
        continue
    prod_refs, _dev_refs = split_targets(m)
    declared = set(pkg_urls(m))
    # keep only what its own non-test targets reference, intersected with what
    # actually resolved — a manifest can declare more than the consumer pulls
    edges[ident] = (prod_refs & declared & set(pins)) or set()

# Production reachability: start at the root's non-test target references.
prod, stack = set(), [p for p in (root_prod & direct)]
while stack:
    n = stack.pop()
    if n in prod or n not in pins:
        continue
    prod.add(n)
    stack.extend(edges.get(n, ()))

for name in sorted(pins):
    kind  = "direct" if name in direct else "transitive"
    scope = "prod" if name in prod else "dev"
    print(f"{label}\t{kind}\t{name}@{versions[name]}\t{scope}")
PY

count() { awk -F'\t' "$1" "$OUT" | cut -f3 | sed 's/@[^@]*$//' | sort -u | wc -l | tr -d ' '; }
D=$(count '$2=="direct"'); A=$(count '1')
DP=$(count '$2=="direct" && $4=="prod"'); AP=$(count '$4=="prod"')

printf '%s\n' "$LABEL"
printf '  FLOOR   direct deps       %-6s  ~%sk tokens   (prod only: %s)\n' "$D" "$(( D * 110 / 1000 ))" "$DP"
printf '  CEILING all resolved      %-6s  ~%sk tokens   (prod only: %s)\n' "$A" "$(( A * 110 / 1000 ))" "$AP"
printf '  -> %s\n' "$OUT"
