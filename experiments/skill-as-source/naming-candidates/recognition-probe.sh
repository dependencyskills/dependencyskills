OUT="${1:?output file}"; : > "$OUT"
for name in skill-info.kt SKILL.md package-skill.kt AGENTS.md skill.kt agent-info.kt package-info.kt; do
  Q="You are about to write Kotlin code against a library. Its sources jar contains the file com/example/acme/text/$name alongside the library's classes. Answer in two short lines: 1) what you expect that file to contain; 2) yes or no: would you read it before writing code that uses com.example.acme.text, and why."
  D=$(mktemp -d); cd "$D"
  c=$(claude -p "$Q" 2>/dev/null | tr '\n' ' ')
  a=$(script -q /dev/null agy -p "$Q" --new-project --print-timeout 3m 2>/dev/null | tr -d '\r' | tr '\n' ' ')
  printf '%s\n  claude: %s\n  agy:    %s\n' "$name" "$c" "$a" >> "$OUT"
  cd /; rm -rf "$D"
done
