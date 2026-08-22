#!/usr/bin/env bash
#
# Run the content-value A/B sweep for one agent over all cases, scoring each output
# by whether it used the provided (opaque, un-guessable) capability symbol.
#
# Usage: sweep.sh <claude|gemini> [model]
#   sweep.sh gemini gemini-3.7-flash-high
#   sweep.sh claude
set -uo pipefail
AGENT="${1:?usage: sweep.sh <claude|gemini> [model]}"
MODEL="${2:-}"
CONDS="${CONDS:-A B}"    # override to run the doc-level gradient, e.g. CONDS=bare
DIR="$(cd "$(dirname "$0")" && pwd)"
RUN="$DIR/run-$AGENT.sh"

# case -> the opaque symbol the codex entry provides (present in output == used it)
CASES="retry cache debounce parse"
sym_for() { case "$1" in
  retry) echo Policy ;; cache) echo BoundedStore ;;
  debounce) echo Coalescer ;; parse) echo RowReader ;;
esac; }

RES="$DIR/results"; mkdir -p "$RES"
safe="${MODEL//\//_}"    # model ids may contain '/' (e.g. google/gemma-4-e4b)
TSV="$RES/${AGENT}${MODEL:+-$safe}-${CONDS// /_}.tsv"
printf "case\tcondition\tused\n" > "$TSV"
echo "# sweep: $AGENT ${MODEL:-default} [$CONDS]"

for c in $CASES; do
  sym="$(sym_for "$c")"
  for cond in $CONDS; do
    prompt="$DIR/prompts/task-$c-$cond.txt"
    # retry's A/B live at task-A.txt / task-B.txt (the smoke); other conds use task-retry-<cond>.txt
    if [ "$c" = retry ] && { [ "$cond" = A ] || [ "$cond" = B ]; }; then
      prompt="$DIR/prompts/task-$cond.txt"
    fi
    resp="$("$RUN" "$prompt" $MODEL 2>/dev/null)"
    printf '%s' "$resp" > "$RES/${AGENT}${MODEL:+-$safe}-${c}-${cond}.out"
    if printf '%s' "$resp" | grep -q "$sym"; then used=yes; else used=no; fi
    printf "%s\t%s\t%s\n" "$c" "$cond" "$used" | tee -a "$TSV"
  done
done
echo "# done -> $TSV"
