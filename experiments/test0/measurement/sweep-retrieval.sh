#!/usr/bin/env bash
#
# Retrieval / disambiguation A/B: the whole catalogue (4 real capabilities + 4
# opaque-named distractors with near-identical signatures) is put in front of the
# agent, and per task it must pick the CORRECT symbol. Two conditions:
#   Rbare — signatures only (syntactic face)
#   Rrich — full entries incl. capability / not-for / triggers (semantic face)
# Verdict per cell: correct | wrong (used the distractor) | both | reinvent.
#
# Usage: sweep-retrieval.sh <agent> <model-label>
#   sweep-retrieval.sh lmstudio openai/gpt-oss-20b
#   MLX_MODEL=<dir> [MLX_SYSTEM=...] sweep-retrieval.sh mlx qwen3-coder-30b-a3b
# Override CONDS to run one condition, e.g. CONDS=Rrich.
set -uo pipefail
AGENT="${1:?usage: sweep-retrieval.sh <agent> <model-label>}"
MODEL="${2:-}"
DIR="$(cd "$(dirname "$0")" && pwd)"
RUN="$DIR/run-$AGENT.sh"
RES="$DIR/results"; mkdir -p "$RES"
safe="${MODEL//\//_}"
CONDS="${CONDS:-Rbare Rrich}"
TSV="$RES/${AGENT}${MODEL:+-$safe}-retrieval.tsv"
printf "case\tcondition\tverdict\n" > "$TSV"
echo "# retrieval sweep: $AGENT ${MODEL:-default} [$CONDS]"

CASES="retry cache debounce parse"
right_for(){ case "$1" in retry)echo Policy;; cache)echo BoundedStore;; debounce)echo Coalescer;; parse)echo RowReader;; esac; }
wrong_for(){ case "$1" in retry)echo Governor;; cache)echo Cellar;; debounce)echo Metronome;; parse)echo Shredder;; esac; }

for c in $CASES; do
  R="$(right_for "$c")"; W="$(wrong_for "$c")"
  for cond in $CONDS; do
    resp="$("$RUN" "$DIR/prompts/task-$c-$cond.txt" "$MODEL" 2>/dev/null)"
    printf '%s' "$resp" > "$RES/${AGENT}${MODEL:+-$safe}-${c}-${cond}.out"
    hasR=0; hasW=0
    printf '%s' "$resp" | grep -qw "$R" && hasR=1
    printf '%s' "$resp" | grep -qw "$W" && hasW=1
    if   [ $hasR -eq 1 ] && [ $hasW -eq 0 ]; then v=correct
    elif [ $hasW -eq 1 ] && [ $hasR -eq 0 ]; then v=wrong
    elif [ $hasR -eq 1 ] && [ $hasW -eq 1 ]; then v=both
    else v=reinvent; fi
    printf "%s\t%s\t%s\n" "$c" "$cond" "$v" | tee -a "$TSV"
  done
done
echo "# done -> $TSV"
