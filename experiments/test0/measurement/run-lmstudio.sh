#!/usr/bin/env bash
#
# Run one prompt through a local model served by LM Studio's OpenAI-compatible
# endpoint (default http://localhost:1234).
#
# Unlike the Claude/Gemini arms — agents that can read a workspace — this is a raw
# chat completion. That makes it the *cleanest* instrument for the content-value
# axis (RAD-0016): condition A is the task prompt alone, condition B is the same
# task with the codex entry pasted in. There is no filesystem, so no retrieval
# confound — the only thing that varies between A and B is the injected content.
#
# Determinism: temperature 0 + a fixed seed, with neutral top_p / penalties, so any
# A-vs-B difference is the injected codex content and not sampling noise. Any <think>
# reasoning channel is stripped before returning, so scoring sees the model's final
# answer — matching what the agent arms emit.
#
# The model must already be loaded (see local-models.md for the load params); this
# script does not load it. Override the endpoint with LMS_ENDPOINT.
#
# Usage: run-lmstudio.sh <prompt-file> [model]
set -euo pipefail
PROMPT="$(cat "${1:?usage: run-lmstudio.sh <prompt-file> [model]}")"
MODEL="${2:-google/gemma-4-e4b}"
ENDPOINT="${LMS_ENDPOINT:-http://localhost:1234/v1/chat/completions}"

payload="$(PROMPT="$PROMPT" MODEL="$MODEL" python3 -c '
import json, os
print(json.dumps({
    "model": os.environ["MODEL"],
    "messages": [{"role": "user", "content": os.environ["PROMPT"]}],
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
c = re.sub(r"<think>.*?</think>", "", c, flags=re.S)  # drop any reasoning channel
print(c.strip())
'
