#!/usr/bin/env bash
#
# Run one prompt through a model served by mlx_lm.server (Apple MLX), OpenAI-compatible.
# This is the fastest-on-Apple-Silicon path; otherwise identical in role to
# run-lmstudio.sh (raw chat completion — condition A is the task alone, B is the task
# with the codex entry pasted in).
#
# mlx_lm.server resolves the request's `model` field against what it loaded, so the
# payload model must match the served model exactly. That value is taken from
# $MLX_MODEL (a local model dir or HF id) rather than from the command line, so the
# friendly label sweep.sh passes as $2 — used only for result filenames — never has to
# be a filesystem path. $2 is therefore accepted and ignored here.
#
# Endpoint: $MLX_ENDPOINT (default http://localhost:1235/v1/chat/completions).
#
# Usage: MLX_MODEL=<dir-or-id> run-mlx.sh <prompt-file> [label-ignored]
set -euo pipefail
PROMPT="$(cat "${1:?usage: MLX_MODEL=<dir> run-mlx.sh <prompt-file> [label]}")"
MODEL="${MLX_MODEL:?set MLX_MODEL to the served model dir or id}"
ENDPOINT="${MLX_ENDPOINT:-http://localhost:1235/v1/chat/completions}"

# Optional system prompt via $MLX_SYSTEM — used for models with a reasoning toggle
# (e.g. NVIDIA Nemotron needs "detailed thinking off" or it reasons past the token
# budget and never emits an answer). Applied identically to A and B, so the A/B delta
# stays valid.
payload="$(PROMPT="$PROMPT" MODEL="$MODEL" SYS="${MLX_SYSTEM:-}" python3 -c '
import json, os
msgs = []
sys = os.environ.get("SYS", "")
if sys:
    msgs.append({"role": "system", "content": sys})
msgs.append({"role": "user", "content": os.environ["PROMPT"]})
print(json.dumps({
    "model": os.environ["MODEL"],
    "messages": msgs,
    "temperature": 0,
    "top_p": 1.0,
    "seed": 0,
    "max_tokens": 5120,
    "stream": False,
}))')"

curl -s --max-time 600 "$ENDPOINT" -H 'Content-Type: application/json' -d "$payload" \
  | python3 -c '
import sys, json, re
d = json.load(sys.stdin)
try:
    c = d["choices"][0]["message"]["content"]
except Exception:
    sys.stderr.write("bad response: " + json.dumps(d)[:400] + "\n"); sys.exit(1)
c = re.sub(r"<think>.*?</think>", "", c, flags=re.S)
print(c.strip())
'
