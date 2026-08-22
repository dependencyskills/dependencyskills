#!/usr/bin/env bash
#
# Selection A/B (RAD-0007): among genuinely overlapping real libraries, does the agent
# reach for the one THIS project prefers? Condition A = task alone (the model's default);
# B = task plus the project's recorded standard. Verdict per cell: preferred | other |
# mixed | none. The value is the shift from "other" (A) to "preferred" (B).
#
# Usage: sweep-selection.sh <agent> <model-label>
#   sweep-selection.sh lmstudio openai/gpt-oss-20b
#   MLX_MODEL=<dir> [MLX_SYSTEM=...] sweep-selection.sh mlx qwen3-coder-30b-a3b
set -uo pipefail
AGENT="${1:?usage: sweep-selection.sh <agent> <model-label>}"
MODEL="${2:-}"
DIR="$(cd "$(dirname "$0")" && pwd)"
RUN="$DIR/run-$AGENT.sh"
RES="$DIR/results"; mkdir -p "$RES"
safe="${MODEL//\//_}"
CONDS="${CONDS:-A dep1 dep2 dep2pref}"
TSV="$RES/${AGENT}${MODEL:+-$safe}-selection.tsv"
printf "domain\tcondition\tverdict\n" > "$TSV"
echo "# selection sweep: $AGENT ${MODEL:-default} [$CONDS]"

score() { # <domain>; reads output on stdin; prints preferred|other|mixed|none
  python3 -c '
import sys, re
dom = sys.argv[1]; t = sys.stdin.read()
PAT = {
  "json":   (r"moshi|@JsonClass|JsonAdapter",
             r"kotlinx\.serialization|encodeToString|@Serializable|com\.google\.gson|\bGson\b|jackson|ObjectMapper"),
  "http":   (r"io\.ktor|ktor\.client",
             r"okhttp|OkHttpClient|Retrofit|HttpURLConnection|java\.net\.http|\bHttpRequest\b|apache\.http"),
  "assert": (r"strikt|expectThat",
             r"assertEquals|assertTrue|assertThat|shouldBe|org\.junit|assertj|\bTruth\b|kotest"),
}
pref, other = PAT[dom]
p = re.search(pref, t, re.I) is not None
o = re.search(other, t, re.I) is not None
print("preferred" if p and not o else "other" if o and not p else "mixed" if p and o else "none")
' "$1"
}

for dom in json http assert; do
  for cond in $CONDS; do
    resp="$("$RUN" "$DIR/prompts/task-sel-$dom-$cond.txt" "$MODEL" 2>/dev/null)"
    printf '%s' "$resp" > "$RES/${AGENT}${MODEL:+-$safe}-sel-${dom}-${cond}.out"
    v="$(printf '%s' "$resp" | score "$dom")"
    printf "%s\t%s\t%s\n" "$dom" "$cond" "$v" | tee -a "$TSV"
  done
done
echo "# done -> $TSV"
