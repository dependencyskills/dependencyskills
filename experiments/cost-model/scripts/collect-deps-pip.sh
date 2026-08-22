#!/usr/bin/env bash
# Python collector: builds a virtualenv from a requirements file and reads the
# installed graph out of it. Same output shape as the other three.
#
#   ./collect-deps-pip.sh <requirements-file> <label> [dev-requirements-file]
#
# Direct is what the requirements file names; everything else pip pulled in.
# The optional third argument is the development requirements, which become
# the `dev` rows — Python has no dependency-level dev flag either, so the split
# is only as good as the project's own separation of the two files.
#
# Needs network, because pip resolves.
set -euo pipefail
REQ="${1:?usage: collect-deps-pip.sh <requirements> <label> [dev-requirements]}"
LABEL="${2:?label required}"
DEVREQ="${3:-}"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HERE/../data"
OUT="$HERE/../data/$LABEL.tsv"
VENV="$(mktemp -d)/venv"

python3 -m venv "$VENV"
"$VENV/bin/pip" install -q --upgrade pip pipdeptree >/dev/null
"$VENV/bin/pip" install -q -r "$REQ" >/dev/null
PRODLIST="$("$VENV/bin/pip" list --format=freeze | cut -d= -f1 | tr 'A-Z' 'a-z' | sort -u)"
[ -n "$DEVREQ" ] && "$VENV/bin/pip" install -q -r "$DEVREQ" >/dev/null

LABEL="$LABEL" REQ="$REQ" DEVREQ="$DEVREQ" PRODLIST="$PRODLIST" \
  "$VENV/bin/python" - <<'PY' > "$OUT"
import json, os, re, subprocess, sys

label = os.environ["LABEL"]
prod  = set(os.environ["PRODLIST"].split())

def declared(path):
    if not path: return set()
    out = set()
    for line in open(path):
        line = line.split("#")[0].strip()
        if not line or line.startswith("-"): continue
        name = re.split(r"[<>=!\[~; ]", line)[0].strip().lower()
        if name: out.add(name)
    return out

direct = declared(os.environ["REQ"]) | declared(os.environ["DEVREQ"])

pipdeptree = os.path.join(os.path.dirname(sys.executable), "pipdeptree")
tree = json.loads(subprocess.check_output([pipdeptree, "--json-tree"]))
seen = {}
def rec(nodes, depth):
    for n in nodes:
        name = n["key"].lower()
        kind = "direct" if (depth == 0 and name in direct) else "transitive"
        prev = seen.get(name)
        if prev is None or (prev[0] == "transitive" and kind == "direct"):
            seen[name] = (kind, n.get("installed_version", ""))
        rec(n.get("dependencies", []), depth + 1)
rec(tree, 0)

# pip and its own bootstrap are not the project's dependencies
for noise in ("pip", "setuptools", "wheel", "pipdeptree"):
    seen.pop(noise, None)

for name in sorted(seen):
    kind, ver = seen[name]
    print(f"{label}\t{kind}\t{name}=={ver}\t{'prod' if name in prod else 'dev'}")
PY

count() { awk -F'\t' "$1" "$OUT" | cut -f3 | sed 's/==.*//' | sort -u | wc -l | tr -d ' '; }
D=$(count '$2=="direct"'); A=$(count '1')
DP=$(count '$2=="direct" && $4=="prod"'); AP=$(count '$4=="prod"')

printf '%s\n' "$LABEL"
printf '  FLOOR   direct deps       %-6s  ~%sk tokens   (prod only: %s)\n' "$D" "$(( D * 110 / 1000 ))" "$DP"
printf '  CEILING all resolved      %-6s  ~%sk tokens   (prod only: %s)\n' "$A" "$(( A * 110 / 1000 ))" "$AP"
printf '  -> %s\n' "$OUT"
