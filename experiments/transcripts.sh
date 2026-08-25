#!/usr/bin/env bash
#
# Pack and unpack the recorded injection transcripts.
#
# WHY THEY ARE PACKED. These are the outputs of running injection payloads through agents, so each
# one quotes an attack repeatedly and adds the model's own reasoning about it. Left loose they were
# 456 KB of well-formed attack prose in plain text — and the first thing an agent pointed at this
# repository does is read every file in it.
#
# Compressing does not make them safe. It makes them **binary**, so a tool that indexes text skips
# them. That is the entire claim: it prevents accidental bulk ingestion, and stops nothing that
# intends to read them, because `tar xzf` is right there. We say so plainly because this project's
# own finding is that asking a reader nicely does not work, and a small structural barrier honestly
# described is worth more than a warning pretending to be one.
#
# It also protects the people working here. An agent that arrives without context — or one that has
# lost it — does not ingest 456 KB of payloads just by looking around.
#
# EXTRACTION GOES TO `.extracted/`, WHICH IS GITIGNORED. Unpacking in place would restore the files
# at their old tracked paths, where the next `git add -A` would put them straight back. Extracting
# somewhere git cannot see removes that possibility rather than relying on care.
#
# NOT PACKED: `test8/results-agenttrap-lint.json` holds rule identifiers rather than prose, and
# `test11` reads it as input.
#
# Usage:
#   ./transcripts.sh unpack    extract to .extracted/ — only if you actually need them
#   ./transcripts.sh list      show what is archived, without extracting
#   ./transcripts.sh clean     delete .extracted/ when you are done
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
OUT="$HERE/.extracted"
cd "$HERE"

case "${1:-}" in
  unpack)
    n=0; total=0
    while IFS= read -r a; do
      d="$(dirname "$a")"
      dest="$OUT/${d#./}"
      mkdir -p "$dest"
      tar xzf "$a" -C "$dest"
      c=$(tar tzf "$a" | wc -l | tr -d ' ')
      total=$((total + c)); n=$((n + 1))
      echo "  $c files -> $dest"
    done < <(find . -name 'transcripts.tar.gz')
    echo
    echo "# $total files from $n archives, extracted to experiments/.extracted/ (gitignored)."
    echo "# These are working injection payloads. Do not point an indexing agent at that directory,"
    echo "# and run ./transcripts.sh clean when you are finished with them."
    ;;
  list)
    while IFS= read -r a; do
      echo "  ${a#./} — $(tar tzf "$a" | wc -l | tr -d ' ') files"
    done < <(find . -name 'transcripts.tar.gz')
    ;;
  clean)
    rm -rf "$OUT"
    echo "  removed experiments/.extracted/"
    ;;
  *)
    sed -n '3,30p' "$0" | sed 's/^# \{0,1\}//'
    exit 1
    ;;
esac
