#!/usr/bin/env bash
set -uo pipefail
cd "$(dirname "$0")"
for m in gemini-3.7-flash-high gemini-3.1-pro-high; do
  echo "########## $m (agy) ##########"
  python3 run-agy-injection.py "$m" 2
  echo
done
echo "AGY DONE"
