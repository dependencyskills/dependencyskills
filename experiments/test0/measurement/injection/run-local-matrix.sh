#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
# Endpoint pinned to LM Studio (:1234) because that is what produced the published
# results-*.json — the recorded `base` in each file. The harness itself now defaults to
# mlx_lm.server (:1235); re-running this under MLX is a different serving configuration
# and belongs in a new results file, not on top of these.
MODELS=(
  "google/gemma-3-1b"
  "openai/gpt-oss-20b"
  "google/gemma-4-12b"
  "qwen3-coder-30b-a3b-instruct-mlx"
  "nvidia-nemotron-3-nano-30b-a3b-mlx"
  "llama-3.3-70b-instruct"
)
for m in "${MODELS[@]}"; do
  echo "########## $m ##########"
  OPENAI_BASE_URL=http://localhost:1234/v1 MODEL="$m" N=3 TIMEOUT=600 python3 inject-eval.py 2>&1
  echo
done
echo "ALL DONE"
