#!/usr/bin/env bash
# For every artifact the KMP build published, report whether the skill file is
# present by name, whether its text is present anywhere inside, and — for the
# binaries — whether the compiler produced anything from it.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$HERE/build/repo"
OUT="$HERE/results.txt"
TMP="$(mktemp -d)"

{
  echo "kotlin: $(grep -o 'multiplatform") version "[^"]*"' "$HERE/build.gradle.kts")"
  echo "agp:    $(grep -o 'library") version "[^"]*"' "$HERE/build.gradle.kts")"
  echo
  printf '%-58s %-10s %-10s %s\n' artifact "file" "text" "entries mentioning skill"
  find "$REPO" -type f \( -name '*.jar' -o -name '*.klib' -o -name '*.aar' \) | sort | while read -r a; do
    rel="${a#"$REPO"/}"; name="$(basename "$rel")"
    x="$TMP/$name"; mkdir -p "$x"; (cd "$x" && unzip -qo "$a")
    file=$(find "$x" -iname 'skill-info*' | wc -l | tr -d ' ')
    text=$(grep -rl 'hand-roll' "$x" 2>/dev/null | wc -l | tr -d ' ')
    mentions=$(grep -rlai 'skill-info\|skill_info\|Skill_info' "$x" 2>/dev/null | sed "s#^$x/##" | tr '\n' ' ')
    printf '%-58s %-10s %-10s %s\n' "$name" "$file" "$text" "${mentions:-none}"
  done
} | tee "$OUT"
rm -rf "$TMP"
