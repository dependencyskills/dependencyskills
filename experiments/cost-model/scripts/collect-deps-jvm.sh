#!/usr/bin/env bash
# Gradle collector, wrapper around collect-deps.init.gradle. Same reporting as
# the npm and SPM collectors so the three are comparable.
#
#   ./collect-deps-jvm.sh <project-dir> [label]
#
# Needs network the first time, because Gradle resolves.
#
# --no-configuration-cache: the init script reads Project at execution time,
# which a cached configuration cannot provide. Projects with the cache on (Now
# in Android, for one) fail with "Cannot get property 'configurations' on null
# object" without it.
set -euo pipefail
DIR="${1:?usage: collect-deps-jvm.sh <project-dir> [label]}"
LABEL="${2:-$(basename "$DIR")}"
HERE="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$HERE/../data"
OUT="$HERE/../data/$LABEL.tsv"

( cd "$DIR" && ./gradlew -I "$HERE/collect-deps.init.gradle" dsDeps -q --console=plain --no-configuration-cache 2>/dev/null ) \
  | grep '^DSDEP' | cut -f2- > "$OUT" || true

if [ ! -s "$OUT" ]; then
  echo "$LABEL: no rows collected — resolution failed, see the build output" >&2
  exit 1
fi

count() { awk -F'\t' "$1" "$OUT" | cut -f3 | awk -F: '{print $1":"$2}' | sort -u | wc -l | tr -d ' '; }
D=$(count '$2=="direct"'); A=$(count '1')
DP=$(count '$2=="direct" && $4=="prod"'); AP=$(count '$4=="prod"')
M=$(cut -f1 "$OUT" | sort -u | wc -l | tr -d ' ')

printf '%s\n' "$LABEL"
printf '  gradle modules            %s\n' "$M"
printf '  FLOOR   direct deps       %-6s  ~%sk tokens   (prod only: %s)\n' "$D" "$(( D * 110 / 1000 ))" "$DP"
printf '  CEILING all resolved      %-6s  ~%sk tokens   (prod only: %s)\n' "$A" "$(( A * 110 / 1000 ))" "$AP"
printf '  -> %s\n' "$OUT"
