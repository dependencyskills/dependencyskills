#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
for m in gemini-3.7-flash-high gpt-oss-120b-medium; do
  echo "########## $m (agy — P1-P6 x A/B/C) ##########"
  PAYLOAD_SET=all ARMS=A,B,C OUT_SUFFIX=-aggr python3 run-agy-injection.py "$m" 2 2>&1
  echo
done
echo "AGY FULL DONE"
