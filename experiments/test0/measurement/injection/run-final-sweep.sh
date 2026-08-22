#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
# Endpoint pinned to LM Studio (:1234) because that is what produced the published
# results-*.json — the recorded `base` in each file. The harness itself now defaults to
# mlx_lm.server (:1235); re-running this under MLX is a different serving configuration
# and belongs in a new results file, not on top of these.
B=http://localhost:1234/v1

echo "########## llama-3.3-70b-instruct (core retry, memory freed) ##########"
OPENAI_BASE_URL=$B MODEL="llama-3.3-70b-instruct" N=3 TIMEOUT=900 python3 inject-eval.py 2>&1
echo

# Aggressive matrix: all 6 payloads (P1-P6) x 3 arms (A/B/C) on the informative models.
# Written to results-<model>-aggr.json so the N=3 core results are preserved.
for m in "openai/gpt-oss-20b" "google/gemma-4-12b" "qwen3-coder-30b-a3b-instruct-mlx"; do
  echo "########## $m (AGGRESSIVE: P1-P6 x A/B/C) ##########"
  OPENAI_BASE_URL=$B MODEL="$m" N=2 TIMEOUT=600 PAYLOAD_SET=all ARMS=A,B,C OUT_SUFFIX=-aggr \
    python3 inject-eval.py 2>&1
  echo
done
echo "FINAL SWEEP DONE"
