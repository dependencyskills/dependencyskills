#!/usr/bin/env bash
#
# Real-library drift A/B for the local-model arm. Scoring is correct-API presence,
# not opaque-symbol presence: the question here is whether the model reached for the
# current API on its own (condition A) or needed the codex to correct a stale or
# absent one (condition B). This is the size-axis analogue of the older-model drift
# probe — a smaller model is expected to know fewer current APIs.
#
# Usage: sweep-external.sh <agent> <model-label>
#   sweep-external.sh lmstudio google/gemma-4-e4b   (served by LM Studio)
#   MLX_MODEL=<dir> sweep-external.sh mlx qwen3-coder-30b-a3b   (served by mlx_lm.server)
# See local-models.md. The runner is run-<agent>.sh; results are prefixed <agent>-.
set -uo pipefail
AGENT="${1:?usage: sweep-external.sh <agent> <model-label>}"
MODEL="${2:-}"
DIR="$(cd "$(dirname "$0")" && pwd)"
RUN="$DIR/run-$AGENT.sh"
RES="$DIR/results"; mkdir -p "$RES"
safe="${MODEL//\//_}"    # model ids may contain '/'
TSV="$RES/${AGENT}${MODEL:+-$safe}-external.tsv"
printf "library\tcondition\tverdict\n" > "$TSV"
echo "# external sweep: $AGENT ${MODEL:-default}"

score() { # <lib>; reads response on stdin; prints correct|stale|unclear
  python3 -c '
import sys, re
lib = sys.argv[1]; t = sys.stdin.read()
def has(p): return re.search(p, t) is not None
if lib == "datetime":
    stale = has(r"kotlinx\.datetime\.(Instant|Clock)")
    correct = has(r"kotlin\.time\.(Instant|Clock)")
    print("stale" if stale and not correct else "correct" if correct and not stale else "unclear")
elif lib == "arrow":
    stale = has(r"Validated")
    correct = has(r"zipOrAccumulate|mapOrAccumulate|arrow\.core\.raise\.either")
    print("stale" if stale and not correct else "correct" if correct and not stale else "unclear")
elif lib == "kaml":
    correct = has(r"Yaml\.default") and has(r"decodeFromString")
    print("correct" if correct else "unclear")
else:
    print("unclear")
' "$1"
}

for lib in datetime arrow kaml; do
  for cond in A B; do
    resp="$("$RUN" "$DIR/prompts/task-$lib-$cond.txt" "$MODEL" 2>/dev/null)"
    printf '%s' "$resp" > "$RES/${AGENT}${MODEL:+-$safe}-${lib}-${cond}.out"
    v="$(printf '%s' "$resp" | score "$lib")"
    printf "%s\t%s\t%s\n" "$lib" "$cond" "$v" | tee -a "$TSV"
  done
done
echo "# done -> $TSV"
