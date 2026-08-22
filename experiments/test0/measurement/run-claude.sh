#!/usr/bin/env bash
#
# Run one prompt through Claude Code headlessly, in an isolated workspace.
#
# In this session the Claude arm is driven by in-session subagents; this script is
# the reproducible CLI equivalent for anyone else. `--print/-p` runs one prompt
# non-interactively and prints the response. The throwaway temp workspace keeps the
# no-codex (control) condition honest — the agent cannot see this repository.
#
# Usage: run-claude.sh <prompt-file> [model]
set -euo pipefail
PROMPT="$(cat "${1:?usage: run-claude.sh <prompt-file> [model]}")"
WORK="$(mktemp -d)"; trap 'rm -rf "$WORK"' EXIT
cd "$WORK"
if [ -n "${2:-}" ]; then
    claude -p "$PROMPT" --model "$2" 2>/dev/null
else
    claude -p "$PROMPT" 2>/dev/null
fi
