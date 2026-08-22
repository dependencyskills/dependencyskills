#!/usr/bin/env bash
#
# Run one prompt through Antigravity (Gemini) headlessly, in an isolated workspace.
#
# Why the wrapper — three things `agy -p` needs that a plain call doesn't give it,
# each of which otherwise makes it hang or refuse:
#   1. a TTY          — `script(1)` supplies a pseudo-TTY; without one, agy produces
#                       no output and never returns.
#   2. a trusted project — `--new-project` establishes one for the session; agy
#                       blocks in an untrusted/unknown directory.
#   3. unattended permissions — `--dangerously-skip-permissions`, since print mode
#                       cannot answer the confirmation prompts it would otherwise raise.
# The workspace is a throwaway temp dir, so the agent cannot see this repository —
# which keeps the no-codex (control) condition honest.
#
# Usage: run-gemini.sh <prompt-file> [model]
set -euo pipefail
PROMPT="$(cat "${1:?usage: run-gemini.sh <prompt-file> [model]}")"
MODEL="${2:-gemini-3.1-pro-high}"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
cd "$WORK"
script -q /dev/null agy -p "$PROMPT" \
    --model "$MODEL" --new-project --dangerously-skip-permissions 2>/dev/null | tr -d '\r'
