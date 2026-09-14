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
# Usage: run.sh <claude|agy> <none|pointer|instructions|hook|lint> <run-number>
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:?set WORK to the directory setup.sh staged}"
TOOL="${1:?tool}"; ARM="${2:?arm}"; N="${3:?run number}"
RUN="$WORK/runs/$TOOL-$ARM-$N"
if [ "$ARM" = hook ] && [ "$TOOL" != claude ]; then
  echo "the hook arm needs a post-edit hook that can add context; Antigravity's expects {}"; exit 2
fi

# The agent works in a fresh temp directory holding only its workspace and the binary
# repository the build resolves. It must not be nested under $WORK: an agent that walks up
# from its workspace there finds the library's own sources, the staged pointer and every
# other run — and Antigravity's trajectory logs showed exactly that in the first runs.
rm -rf "$RUN" && mkdir -p "$RUN"
ISO="$(mktemp -d)"
trap 'rm -rf "$ISO"' EXIT
if [ -d "$WORK/repo" ]; then cp -R "$WORK/repo" "$ISO/repo"; fi
cp -R "$WORK/template" "$ISO/ws"
cp -R "$WORK/arms/$ARM/." "$ISO/ws/"
python3 - "$ISO/ws/build.gradle.kts" "$WORK/repo" "$ISO/repo" <<'PY'
import sys; p = sys.argv[1]; text = open(p).read(); open(p, "w").write(text.replace(sys.argv[2], sys.argv[3]))
PY
if [ "$ARM" = lint ]; then printf '\napply(from = "gradle/skill-lint.gradle.kts")\n' >> "$ISO/ws/build.gradle.kts"; fi
(cd "$ISO/ws" && git init -q && git add -A && git -c user.name=fixture -c user.email=fixture@example.com commit -qm baseline)

TASK="$(cat "$WORK/task.md")"
{
  echo "tool=$TOOL arm=$ARM run=$N started=$(date -u +%FT%TZ)"
  if [ "$TOOL" = claude ]; then claude --version; else agy --version; fi
} > "$RUN/meta.txt"

cd "$ISO/ws"
case "$TOOL" in
  claude)
    claude -p "$TASK" --output-format stream-json --verbose \
      --permission-mode acceptEdits \
      --allowedTools "Read Edit Write Glob Grep Skill Bash(./gradlew:*)" \
      > "$RUN/transcript.jsonl" 2> "$RUN/stderr.txt" || true ;;
  agy)
    # stream-json records every tool call with its parameters (the file viewed, the command
    # run), though not file contents; plain print mode records only the final summary.
    script -q /dev/null agy -p "$TASK" --new-project --dangerously-skip-permissions \
      --output-format stream-json --log-file "$RUN/agy.log" \
      2> "$RUN/stderr.txt" | tr -d '\r' > "$RUN/transcript.jsonl" || true ;;
  *) echo "unknown tool $TOOL"; exit 2 ;;
esac

git add -A && git diff --cached > "$RUN/changes.diff"
cp -R "$ISO/ws" "$RUN/ws"
echo "isolated=$ISO" >> "$RUN/meta.txt"
echo "finished=$(date -u +%FT%TZ)" >> "$RUN/meta.txt"
echo "$RUN"
