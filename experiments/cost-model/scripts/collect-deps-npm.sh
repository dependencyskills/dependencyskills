#!/usr/bin/env bash
# Collect the resolved dependency set of an npm project, and report BOTH ends
# of the range — the same shape as collect-deps.sh does for Gradle.
#
#   ./collect-deps-npm.sh <project-dir> [label]
#
# Direct is the floor and every resolved package is the ceiling, for the same
# reason as the Gradle collector: a transitive dependency your own code calls
# is one you would want a skill for, and the tree cannot tell you which.
#
# npm distinguishes production from development dependencies and Gradle's
# collected configurations did not, so a fourth column records it. An agent
# working in the repo sees both; an agent reasoning about shipped code sees
# only prod. Rows with no fourth column (the Gradle files) are production.
#
# Needs `npm install` to have run — the tree is read from node_modules.
set -euo pipefail
DIR="${1:?usage: collect-deps-npm.sh <project-dir> [label]}"
LABEL="${2:-$(basename "$DIR")}"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HERE/../data"
OUT="$HERE/../data/$LABEL.tsv"

cd "$DIR"
# npm ls exits non-zero on peer-dependency complaints while still emitting a
# usable tree. Spike quality: take the tree, ignore the grumbling.
npm ls --all --json           > /tmp/npm-all.json  2>/dev/null || true
npm ls --all --json --omit=dev > /tmp/npm-prod.json 2>/dev/null || true

LABEL="$LABEL" python3 - /tmp/npm-all.json /tmp/npm-prod.json > "$OUT" <<'PY'
import json, os, sys

def walk(path):
    with open(path) as f:
        root = json.load(f)
    seen = {}
    def rec(node, depth):
        for name, child in (node.get("dependencies") or {}).items():
            ver = child.get("version", "")
            kind = "direct" if depth == 0 else "transitive"
            prev = seen.get(name)
            # a package reachable both directly and transitively is direct
            if prev is None or (prev[0] == "transitive" and kind == "direct"):
                seen[name] = (kind, ver)
            rec(child, depth + 1)
    rec(root, 0)
    return seen

allpkgs = walk(sys.argv[1])
prod    = walk(sys.argv[2])
label   = os.environ["LABEL"]

for name in sorted(allpkgs):
    kind, ver = allpkgs[name]
    scope = "prod" if name in prod else "dev"
    print(f"{label}\t{kind}\t{name}@{ver}\t{scope}")
PY

count() { awk -F'\t' "$1" "$OUT" | cut -f3 | sed 's/@[^@]*$//' | sort -u | wc -l | tr -d ' '; }
D=$(count '$2=="direct"'); A=$(count '1');
DP=$(count '$2=="direct" && $4=="prod"'); AP=$(count '$4=="prod"')

printf '%s\n' "$LABEL"
printf '  FLOOR   direct deps       %-6s  ~%sk tokens   (prod only: %s)\n' "$D" "$(( D * 110 / 1000 ))" "$DP"
printf '  CEILING all resolved      %-6s  ~%sk tokens   (prod only: %s)\n' "$A" "$(( A * 110 / 1000 ))" "$AP"
printf '  -> %s\n' "$OUT"
