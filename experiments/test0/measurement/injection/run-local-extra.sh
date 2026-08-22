#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
# Endpoint pinned to LM Studio (:1234) because that is what produced the published
# results-*.json — the recorded `base` in each file. The harness itself now defaults to
# mlx_lm.server (:1235); re-running this under MLX is a different serving configuration
# and belongs in a new results file, not on top of these.
MODELS=(
  "llama-3.3-70b-instruct"
  "devstral-small-2-24b-instruct-2512"
  "qwen/qwen3.6-27b"
  "google/gemma-4-e4b"
)
for m in "${MODELS[@]}"; do
  echo "########## $m ##########"
  OPENAI_BASE_URL=http://localhost:1234/v1 MODEL="$m" N=3 TIMEOUT=600 python3 inject-eval.py 2>&1
  echo
done
echo "LOCAL-EXTRA DONE"
