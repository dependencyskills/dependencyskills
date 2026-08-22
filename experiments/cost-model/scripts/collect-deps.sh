#!/usr/bin/env bash
# Collect the resolved dependency set of a Gradle project, and report BOTH
# ends of the range.
#
#   ./collect-deps.sh <project-dir> [label]
#
# Direct is the floor and every resolved module is the ceiling. The truth is
# in between: a transitive dependency your own code calls is one you would
# want a skill for, and the resolution graph cannot tell you which those
# are. Recording one number would be picking an answer to a question we have
# not answered.
#
# Needs network the first time, because Gradle resolves.
set -euo pipefail
DIR="${1:?usage: collect-deps.sh <project-dir> [label]}"
LABEL="${2:-$(basename "$DIR")}"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HERE/../data"
OUT="$HERE/../data/$LABEL.tsv"

( cd "$DIR" && ./gradlew -I "$HERE/collect-deps.init.gradle" dsDeps -q --console=plain 2>/dev/null ) \
  | grep '^DSDEP' | cut -f2- > "$OUT" || true

ga()      { cut -f3 "$OUT" | awk -F: '{print $1":"$2}' | sort -u | wc -l | tr -d ' '; }
ga_direct(){ awk -F'\t' '$2=="direct"' "$OUT" | cut -f3 | awk -F: '{print $1":"$2}' | sort -u | wc -l | tr -d ' '; }
gav()     { cut -f3 "$OUT" | sort -u | wc -l | tr -d ' '; }
mods()    { cut -f1 "$OUT" | sort -u | wc -l | tr -d ' '; }

D=$(ga_direct); A=$(ga); V=$(gav); M=$(mods)
# 110 tokens per description, measured - see findings.md
printf '%s\n' "$LABEL"
printf '  gradle modules            %s\n' "$M"
printf '  FLOOR   direct deps       %-6s  ~%sk tokens\n' "$D" "$(( D * 110 / 1000 ))"
printf '  CEILING all resolved      %-6s  ~%sk tokens\n' "$A" "$(( A * 110 / 1000 ))"
printf '          (with versions)   %s\n' "$V"
printf '  -> %s\n' "$OUT"
