#!/usr/bin/env bash
# One run of test 5: copy the consumer template, lay one arm over it, give the task to one
# agent tool headless, and keep what it wrote and what it did.
#
# Permissions follow ../../test0/measurement/:
#   claude  `claude -p` with edits accepted and an allowlist of file tools, the Skill tool and
#           the Gradle wrapper. The allowlist ADDS to the user's own permission settings, it does
#           not replace them: in the first smoke runs the agent also ran unzip, find and python
#           and wrote to its own scratch directory, all granted at user level. The workspace
#           holds only fixture files, so that is containment by content, not by permission.
#   agy     `agy -p --new-project --dangerously-skip-permissions` under script(1), exactly as
#           run-gemini.sh runs it: print mode cannot answer its confirmations, and it hangs
#           without a TTY. The workspace is a throwaway copy under $WORK.
#
# Usage: [MODEL=<id> LABEL=<name>] run.sh <claude|agy> <none|pointer|instructions|hook|lint|lintpost> <run-number>
#   MODEL picks a model other than the tool's default; LABEL names the run directory in its
#   place (no hyphens), so runs on an older model score as a tool of their own.
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?set WORK to the directory setup.sh staged}"
TOOL="${1:?tool}"; ARM="${2:?arm}"; N="${3:?run number}"
LABEL="${LABEL:-$TOOL}"
case "$LABEL" in *-*) echo "LABEL must not contain a hyphen"; exit 2 ;; esac
MODEL_ARGS=(); if [ -n "${MODEL:-}" ]; then MODEL_ARGS=(--model "$MODEL"); fi
RUN="$WORK/runs/$LABEL-$ARM-$N"
if [ "$ARM" = hook ] && [ "$TOOL" != claude ]; then
  echo "the hook arm needs a post-edit hook that can add context; Antigravity's expects {}"; exit 2
fi

# The agent works in a fresh temp directory holding only its workspace and the binary
# repository the build resolves. It must not be nested under $WORK: an agent that walks up
# from its workspace there finds the library's own sources, the staged pointer and every
# other run — and Antigravity's trajectory logs showed exactly that in the first runs.
rm -rf "$RUN" && mkdir -p "$RUN"
ISO="$(mktemp -d)"
LINTDIR=""
trap 'rm -rf "$ISO" ${LINTDIR:+"$LINTDIR"}' EXIT
if [ -d "$WORK/repo" ]; then cp -R "$WORK/repo" "$ISO/repo"; fi
cp -R "$WORK/template" "$ISO/ws"
if [ "$ARM" = lintpost ]; then
  # Nothing goes in the workspace. The skill and the init script live in a second temp dir
  # that is not a neighbour of the workspace, and the wrapper passes the script to Gradle.
  LINTDIR="$(mktemp -d)"
  cp "$WORK/arms/lintpost/"*.md "$LINTDIR/"
  SKILLFILE="$(ls "$LINTDIR"/*.md)"
  python3 - "$WORK/arms/lintpost/skill-lint.init.gradle.kts" "$LINTDIR/skill-lint.init.gradle.kts" "$SKILLFILE" <<'PY'
import sys; open(sys.argv[2], "w").write(open(sys.argv[1]).read().replace("@SKILL@", sys.argv[3]))
PY
  python3 - "$ISO/ws/gradlew" "$LINTDIR/skill-lint.init.gradle.kts" <<'PY'
import sys; p = sys.argv[1]; lines = open(p).read().split("\n")
lines.insert(1, f'set -- --init-script "{sys.argv[2]}" "$@"')
open(p, "w").write("\n".join(lines))
PY
elif [ "$ARM" = hook ]; then
  # Only the settings file goes in the workspace. The hook script and the skill it hands over
  # live in a separate temp dir: in the first hook runs the agent browsed .claude/ and read
  # the staged skill before writing, which measured the file and not the hook.
  LINTDIR="$(mktemp -d)"
  cp "$WORK/arms/hook/.claude/hooks/dependency-skill.py" "$LINTDIR/"
  cp -R "$WORK/arms/hook/.claude/hooks/skills" "$LINTDIR/skills"
  mkdir -p "$ISO/ws/.claude"
  python3 - "$LINTDIR/dependency-skill.py" > "$ISO/ws/.claude/settings.json" <<'PY'
import json, sys
print(json.dumps({"hooks": {"PostToolUse": [{"matcher": "Edit|Write|MultiEdit",
    "hooks": [{"type": "command", "command": f'python3 "{sys.argv[1]}"'}]}]}}, indent=2))
PY
else
  cp -R "$WORK/arms/$ARM/." "$ISO/ws/"
fi
python3 - "$ISO/ws/build.gradle.kts" "$WORK/repo" "$ISO/repo" <<'PY'
import sys; p = sys.argv[1]; text = open(p).read(); open(p, "w").write(text.replace(sys.argv[2], sys.argv[3]))
PY
if [ "$ARM" = lint ]; then printf '\napply(from = "gradle/skill-lint.gradle.kts")\n' >> "$ISO/ws/build.gradle.kts"; fi
(cd "$ISO/ws" && git init -q && git add -A && git -c user.name=fixture -c user.email=fixture@example.com commit -qm baseline)

TASK="$(cat "$WORK/task.md")"
{
  echo "tool=$TOOL label=$LABEL model=${MODEL:-default} arm=$ARM run=$N started=$(date -u +%FT%TZ)"
  if [ "$TOOL" = claude ]; then claude --version; else agy --version; fi
} > "$RUN/meta.txt"

cd "$ISO/ws"
case "$TOOL" in
  claude)
    claude -p "$TASK" ${MODEL_ARGS[@]+"${MODEL_ARGS[@]}"} --output-format stream-json --verbose --include-hook-events \
      --permission-mode acceptEdits \
      --allowedTools "Read Edit Write Glob Grep Skill Bash(./gradlew:*)" \
      > "$RUN/transcript.jsonl" 2> "$RUN/stderr.txt" || true ;;
  agy)
    # stream-json records every tool call with its parameters (the file viewed, the command
    # run), though not file contents; plain print mode records only the final summary.
    script -q /dev/null agy -p "$TASK" ${MODEL_ARGS[@]+"${MODEL_ARGS[@]}"} --new-project --dangerously-skip-permissions --print-timeout 20m \
      --output-format stream-json --log-file "$RUN/agy.log" \
      2> "$RUN/stderr.txt" | tr -d '\r' > "$RUN/transcript.jsonl" || true ;;
  *) echo "unknown tool $TOOL"; exit 2 ;;
esac

git add -A && git diff --cached > "$RUN/changes.diff"
cp -R "$ISO/ws" "$RUN/ws"
if [ "$ARM" = hook ] && [ -f "$LINTDIR/.seen" ]; then cp "$LINTDIR/.seen" "$RUN/hook-seen"; fi
echo "isolated=$ISO" >> "$RUN/meta.txt"
echo "finished=$(date -u +%FT%TZ)" >> "$RUN/meta.txt"
echo "$RUN"
