#!/usr/bin/env bash
#
# Run a static experiment arm inside a container with NO NETWORK.
#
# The arms this wraps read live attack code (AgentTrap's 91 malicious skills) or compile payloads.
# None of them needs a network, so `--network=none` turns "we did not attempt exfiltration" into
# "exfiltration was not possible". That is the difference worth having when the material is
# somebody else's malware.
#
# The corpus is mounted READ-ONLY. The repository is mounted read-only too; only a results
# directory is writable, so nothing a payload does can reach tracked files.
#
# Usage:
#   ./run-sandboxed.sh build
#   ./run-sandboxed.sh test10                       # rule catalogue, needs the harvested corpus
#   ./run-sandboxed.sh test8  <agenttrap-dir>       # linters vs the malicious corpus
#   ./run-sandboxed.sh test9  <lang...>             # compile + lint per language
#   ./run-sandboxed.sh shell  [agenttrap-dir]       # poke around, still with no network
#
# NOT covered here, and deliberately: the MLX local-model arms (need the host GPU), the `agy`
# frontier arms (need vendor egress and host credentials) and Claude subagents (run in-process).
# See ../SAFETY.md.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
IMAGE="dependencyskills-experiments"
CMD="${1:-}"; shift || true

case "$CMD" in
  build)
    docker build -t "$IMAGE" "$HERE"
    exit 0
    ;;
  "")
    sed -n '3,20p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac

docker image inspect "$IMAGE" >/dev/null 2>&1 || {
  echo "image not built — run: $0 build" >&2; exit 1; }

RESULTS="$(mktemp -d)"
MOUNTS=(-v "$REPO:/work:ro" -v "$RESULTS:/results")

# An attack corpus, if the arm needs one, always read-only.
CORPUS="${1:-}"
if [ -n "$CORPUS" ] && [ -d "$CORPUS" ]; then
  MOUNTS+=(-v "$(cd "$CORPUS" && pwd):/corpus:ro")
  shift
  CORPUS_ARG=/corpus
else
  CORPUS_ARG=""
fi

case "$CMD" in
  test8)  RUN="cd /work/experiments/test8 && python3 linter_vs_agenttrap.py ${CORPUS_ARG}" ;;
  test9)  RUN="cd /work/experiments/test9 && python3 stages_1_2.py ${*:-kotlin java js}" ;;
  test10) RUN="cd /work/experiments/test10 && python3 evaluate.py" ;;
  shell)  RUN="bash" ;;
  *)      echo "unknown arm: $CMD" >&2; exit 1 ;;
esac

echo "# running '$CMD' with --network=none; results land in $RESULTS"
# -it only when stdin is a terminal; it fails outright when run from a script or CI.
TTY=(); [ -t 0 ] && TTY=(-it)

docker run --rm ${TTY[@]+"${TTY[@]}"} \
  --network=none \
  --read-only --tmpfs /tmp:exec --tmpfs /home/runner \
  --cap-drop=ALL --security-opt no-new-privileges \
  --memory=4g --pids-limit=512 \
  "${MOUNTS[@]}" \
  "$IMAGE" bash -lc "$RUN"

echo "# results kept at $RESULTS"
