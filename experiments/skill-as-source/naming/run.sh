#!/usr/bin/env bash
# RAD-0073 test 2: does each toolchain accept a documentation-only source file,
# under which names, and does it emit anything or warn?
#
# Every case builds a tiny package holding one real type plus the skill file,
# with the strictest warning setting the toolchain offers, and reports the exit
# code, every warning or error line, and what the build emitted for the skill.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
LOG="$HERE/results.txt"
: > "$LOG"

say() { printf '%s\n' "$*" | tee -a "$LOG"; }

SKILL_BODY='Skill: acme-text.

Normalize user input with Normalizer.normalize before comparing; never
hand-roll a case fold.

Wrong:   a.lowercase() == b.lowercase()
Correct: Normalizer.normalize(a) == Normalizer.normalize(b)'

block() { # prefix, e.g. " * " or "/// "
  printf '%s\n' "$SKILL_BODY" | sed "s#^#$1#"
}

report() { # lang case exit logfile outputs
  say "--- $1 | $2 | exit $3"
  grep -iE 'warning|error|warn:' "$4" | sed 's/^/    /' | tee -a "$LOG"
  grep -iqE 'warning|error|warn:' "$4" || say "    (no warnings)"
  say "    emitted: ${5:-nothing}"
}

tool_versions() {
  say "javac:   $(javac -version 2>&1)"
  say "kotlinc: $(kotlinc -version 2>&1 | tail -1)"
  say "python:  $(python3 --version 2>&1)"
  say "node:    $(node --version 2>&1)"
  say "swift:   $(swift --version 2>&1 | head -1)"
  say "go:      $(go version 2>&1)"
  say "rustc:   $(rustc --version 2>&1) · $(cargo clippy --version 2>&1 | tail -1)"
  say "tsc:     $("$TSC" --version 2>&1)"
  say ""
}

# ---------------------------------------------------------------- Java
java_case() { # name placement(before|after)
  local d="$WORK/java-$1-$2"; mkdir -p "$d/src/acme/text" "$d/out"
  printf 'package acme.text;\npublic final class Normalizer { public static String normalize(String s) { return s; } }\n' > "$d/src/acme/text/Normalizer.java"
  local f="$d/src/acme/text/$1.java"
  if [ "$2" = before ]; then { echo '/**'; block ' * '; echo ' */'; echo 'package acme.text;'; } > "$f"
  else { echo 'package acme.text;'; echo; echo '/**'; block ' * '; echo ' */'; } > "$f"; fi
  (cd "$d" && javac -Xlint:all -d out src/acme/text/*.java) > "$d/log" 2>&1; local rc=$?
  report java "$1.java, doc $2 package" $rc "$d/log" "$(cd "$d/out" && find . -name '*.class' ! -name 'Normalizer.class' | tr '\n' ' ')"
}

# ---------------------------------------------------------------- Kotlin
kotlin_case() { # name placement
  local d="$WORK/kotlin-$1-$2"; mkdir -p "$d/src/acme/text" "$d/out"
  printf 'package acme.text\nobject Normalizer { fun normalize(s: String) = s }\n' > "$d/src/acme/text/Normalizer.kt"
  local f="$d/src/acme/text/$1.kt"
  if [ "$2" = before ]; then { echo '/**'; block ' * '; echo ' */'; echo 'package acme.text'; } > "$f"
  else { echo 'package acme.text'; echo; echo '/**'; block ' * '; echo ' */'; } > "$f"; fi
  (cd "$d" && kotlinc -Wextra -d out src/acme/text/*.kt) > "$d/log" 2>&1; local rc=$?
  report kotlin "$1.kt, doc $2 package" $rc "$d/log" "$(cd "$d/out" && find . -name '*.class' ! -name 'Normalizer*.class' | tr '\n' ' ')"
}

# ---------------------------------------------------------------- Python
python_case() { # name
  local d="$WORK/python-$1"; mkdir -p "$d/acme_text"
  printf '"""Acme text."""\n' > "$d/acme_text/__init__.py"
  printf 'def normalize(s):\n    return s\n' > "$d/acme_text/normalizer.py"
  { echo '"""'; block ''; echo '"""'; } > "$d/acme_text/$1.py"
  (cd "$d" && python3 -W error -m compileall -q acme_text \
     && python3 -W error -c "import importlib, sys; m = importlib.import_module('acme_text.$1'); print('import ok, docstring', len(m.__doc__), 'chars')") > "$d/log" 2>&1; local rc=$?
  cat "$d/log" | grep -v '^import ok' > "$d/log.w"
  report python "$1.py" $rc "$d/log.w" "$(grep '^import ok' "$d/log"; cd "$d" && find . -name "$1*.pyc" | tr '\n' ' ')"
  [ $rc -ne 0 ] && sed 's/^/    /' "$d/log" | tail -2 | tee -a "$LOG" >/dev/null
}

# ---------------------------------------------------------------- TypeScript (node type stripping; no tsc)
ts_case() { # name
  local d="$WORK/ts-$1"; mkdir -p "$d/src"
  printf 'export function normalize(s: string): string { return s; }\n' > "$d/src/normalizer.ts"
  { echo '/**'; block ' * '; echo ' * @packageDocumentation'; echo ' */'; echo 'export {};'; } > "$d/src/$1.ts"
  printf 'import "./src/%s.ts";\nconsole.log("import ok");\n' "$1" > "$d/main.ts"
  (cd "$d" && node --no-warnings=ExperimentalWarning main.ts) > "$d/log" 2>&1; local rc=$?
  grep -v '^import ok' "$d/log" > "$d/log.w"
  report "ts (node strip, no tsc)" "$1.ts" $rc "$d/log.w" "$(grep '^import ok' "$d/log")"
}

# ---------------------------------------------------------------- TypeScript (tsc)
TSC="${TSC:-tsc}"
tsc_case() { # name form(module|script)
  command -v "$TSC" >/dev/null || { say "--- tsc | $1.ts | not installed (set TSC=path/to/tsc)"; return; }
  local d="$WORK/tsc-$1-$2"; mkdir -p "$d/src"
  printf '{ "compilerOptions": { "strict": true, "noUnusedLocals": true, "noUnusedParameters": true, "declaration": true, "module": "nodenext", "target": "es2024", "outDir": "dist", "rootDir": "src" }, "include": ["src"] }\n' > "$d/tsconfig.json"
  printf '{ "type": "module" }\n' > "$d/package.json"
  printf 'export function normalize(s: string): string { return s; }\n' > "$d/src/index.ts"
  { echo '/**'; block ' * '; echo ' * @packageDocumentation'; echo ' */'; [ "$2" = module ] && echo 'export {};'; } > "$d/src/$1.ts"
  (cd "$d" && "$TSC" -p .) > "$d/log" 2>&1; local rc=$?
  local out; out=$(cd "$d/dist" 2>/dev/null && for f in "$1".js "$1".d.ts; do [ -f "$f" ] && printf '%s(skill text %s) ' "$f" "$(grep -c hand-roll "$f")"; done)
  report tsc "$1.ts, $2 (comment $( [ "$2" = module ] && echo 'plus export {}' || echo 'only'))" $rc "$d/log" "$out"
}

# ---------------------------------------------------------------- Swift (SwiftPM)
swift_case() { # name
  local d="$WORK/swift-$1"; mkdir -p "$d/Sources/AcmeText"
  cat > "$d/Package.swift" <<'EOF'
// swift-tools-version:6.0
import PackageDescription
let package = Package(name: "AcmeText", targets: [.target(name: "AcmeText")])
EOF
  printf 'public enum Normalizer { public static func normalize(_ s: String) -> String { s } }\n' > "$d/Sources/AcmeText/Normalizer.swift"
  block '/// ' > "$d/Sources/AcmeText/$1.swift"
  (cd "$d" && swift build -Xswiftc -warnings-as-errors 2>&1) > "$d/log"; local rc=$?
  grep -vE 'Compiling|Building|Build complete|Emitting|Planning|Write|Fetching|Computing' "$d/log" > "$d/log.w"
  report swift "$1.swift" $rc "$d/log.w" "$(cd "$d/.build" 2>/dev/null && find . -iname "$1*.o" | head -3 | tr '\n' ' ')"
}

# ---------------------------------------------------------------- Go
go_case() { # name placement(before|after)
  local d="$WORK/go-$1-$2"; mkdir -p "$d/acmetext"
  printf 'module example.com/acmetext\n\ngo 1.27\n' > "$d/go.mod"
  printf '// Package acmetext normalizes text.\npackage acmetext\n\n// Normalize returns s normalized.\nfunc Normalize(s string) string { return s }\n' > "$d/acmetext/normalize.go"
  local f="$d/acmetext/$1.go"
  if [ "$2" = before ]; then { block '// '; echo 'package acmetext'; } > "$f"
  else { echo 'package acmetext'; echo; block '// '; } > "$f"; fi
  (cd "$d" && go build ./... && go vet ./...) > "$d/log" 2>&1; local rc=$?
  local files; files=$(cd "$d" && go list -f 'compiled={{.GoFiles}} ignored={{.IgnoredGoFiles}}' ./acmetext 2>&1)
  local pkgdoc; pkgdoc=$(cd "$d" && go doc ./acmetext 2>&1 | grep -c 'hand-roll')
  report go "$1.go, comment $2 package clause" $rc "$d/log" "$files · skill text in 'go doc' package output: $pkgdoc"
}

# ---------------------------------------------------------------- Rust
rust_case() { # file reached(mod|none)
  local d="$WORK/rust-$1-$2"; mkdir -p "$d/src"
  printf '[package]\nname = "acme-text"\nversion = "0.1.0"\nedition = "2024"\ndescription = "fixture"\nlicense = "MIT"\n' > "$d/Cargo.toml"
  printf '//! Acme text.\n\n/// Normalize returns s normalized.\n#[must_use]\npub fn normalize(s: &str) -> &str { s }\n' > "$d/src/lib.rs"
  [ "$2" = mod ] && printf 'mod %s;\n' "$1" >> "$d/src/lib.rs"
  block '//! ' > "$d/src/$1.rs"
  (cd "$d" && cargo build --quiet && cargo clippy --quiet -- -W clippy::pedantic) > "$d/log" 2>&1; local rc=$?
  local shipped; shipped=$(cd "$d" && cargo package --list --allow-dirty 2>/dev/null | grep -c "src/$1.rs")
  report rust "src/$1.rs, $( [ "$2" = mod ] && echo "reached by mod $1;" || echo "no mod declaration")" $rc "$d/log" "in cargo package --list: $shipped"
}

# ---------------------------------------------------------------- case-insensitive filesystem
collision_case() {
  local d="$WORK/collision"; mkdir -p "$d"
  echo real > "$d/Skill.kt"; echo doc > "$d/skill.kt"
  say "--- filesystem | Skill.kt then skill.kt in one directory"
  say "    files present: $(ls "$d" | tr '\n' ' ')· Skill.kt now holds: $(cat "$d/Skill.kt")"
}

tool_versions
say "work dir: kept under \$WORK for inspection"; say ""
for n in skill skill-info package-info; do for p in before after; do java_case $n $p; done; done
for n in skill skill-info; do for p in before after; do kotlin_case $n $p; done; done
for n in skill skill-info _skill; do python_case $n; done
for n in skill skill-info; do ts_case $n; done
for n in skill skill-info; do for f in module script; do tsc_case $n $f; done; done
for n in skill skill-info; do swift_case $n; done
for n in skill skill-info skillinfo skill_info _skill; do for p in before after; do go_case $n $p; done; done
rust_case skill_info mod; rust_case skill_info none; rust_case skill-info none
collision_case
echo "work: $WORK"
