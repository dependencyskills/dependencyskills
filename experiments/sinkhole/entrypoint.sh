#!/bin/sh
#
# Redirect every inbound TCP port to the single listener, then start the recorder.
#
# The redirect is here rather than in the experiment container for two reasons. The experiment
# container keeps `--cap-drop=ALL`, which forbids iptables; and nothing in its configuration
# suggests it is being observed, so a payload that checks its own environment before acting finds
# an ordinary machine with a working network.
set -eu

PORT="${SINKHOLE_PORT:-8888}"

# Everything except the listener itself and DNS, which need to arrive unmolested.
iptables -t nat -A PREROUTING -p tcp --dport "$PORT" -j RETURN
iptables -t nat -A PREROUTING -p tcp -j REDIRECT --to-port "$PORT"

exec python3 /opt/sinkhole.py
