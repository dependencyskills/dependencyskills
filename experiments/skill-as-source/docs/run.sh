#!/usr/bin/env bash
# RAD-0073 test 4: does a skill-info file leak into the documentation a person reads?
#
# Generates each ecosystem's standard API documentation for the fixtures ../survival/run.sh
# built (set SURVIVAL to that run's WORK) and the KMP fixture in ../kmp/, and reports
# whether the skill text, or the skill-info name, appears anywhere in the rendered output.
# Where a language has an existing package-documentation slot, the same text is also put
# there, to show what reusing the slot would do.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
SURVIVAL="${SURVIVAL:?set SURVIVAL to the WORK directory of a ../survival/run.sh run}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/results.txt"
: > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }

# rendered <label> <dir> : does the rendered output carry the skill text or the file name?
rendered() {
  local text name
  text=$(grep -rl 'hand-roll' "$2" 2>/dev/null | wc -l | tr -d ' ')
  name=$(grep -rlE 'skill-info|skill_info' "$2" 2>/dev/null | wc -l | tr -d ' ')
  local verdict="stays out"
  [ "$text" -gt 0 ] && verdict="SKILL TEXT RENDERED"
  [ "$text" -eq 0 ] && [ "$name" -gt 0 ] && verdict="text out, file NAME appears"
  say "    $(printf '%-52s' "$1") $verdict (files with text: $text, with name: $name)"
}

SKILL_BODY='Skill: acme-text.

Normalize user input with normalize before comparing; never
hand-roll a case fold.

Wrong:   a.lowercase() == b.lowercase()
Correct: normalize(a) == normalize(b)'
block() { printf '%s\n' "$SKILL_BODY" | sed "s#^#$1#"; }

# ---------------------------------------------------------------- Javadoc
javadoc_case() {
  local d="$WORK/javadoc"; rm -rf "$d"; mkdir -p "$d"; cp -R "$SURVIVAL/java/src" "$d/src"
  local pkg="$d/src/main/java/com/example/acme/text"
  javadoc -quiet -d "$d/out-dedicated" -sourcepath "$d/src/main/java" com.example.acme.text > "$d/log" 2>&1
  say "--- Javadoc $(javac -version 2>&1 | cut -d' ' -f2)"
  rendered "skill-info.java" "$d/out-dedicated"
  { echo '/**'; block ' * '; echo ' */'; echo 'package com.example.acme.text;'; } > "$pkg/package-info.java"
  rm "$pkg/skill-info.java"
  javadoc -quiet -d "$d/out-reuse" -sourcepath "$d/src/main/java" com.example.acme.text >> "$d/log" 2>&1
  rendered "the same text in package-info.java (reuse)" "$d/out-reuse"
}

# ---------------------------------------------------------------- Dokka, on the KMP fixture
dokka_case() {
  local d="$WORK/dokka"; rm -rf "$d"; mkdir -p "$d"
  cp -R "$HERE/../kmp/src" "$HERE/../kmp/settings.gradle.kts" "$d/"
  perl -pe 's/`maven-publish`/`maven-publish`\n    id("org.jetbrains.dokka") version "2.2.0"/' "$HERE/../kmp/build.gradle.kts" > "$d/build.gradle.kts"
  (cd "$d" && ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" "$GRADLE" -q --no-configuration-cache dokkaGenerate) > "$d/log" 2>&1
  say "--- Dokka 2.2.0, HTML, Kotlin Multiplatform fixture · exit $?"
  rendered "skill-info.kt in commonMain" "$d/build/dokka/html"
}

# ---------------------------------------------------------------- go doc
godoc_case() { # placement
  local d="$WORK/godoc-$1"; rm -rf "$d"; cp -R "$SURVIVAL/go/mod" "$d"; rm -f "$d"/acmetext/_*.go
  if [ "$1" = before ]; then { block '// '; echo 'package acmetext'; } > "$d/acmetext/skill-info.go"; fi
  mkdir -p "$d/out"; (cd "$d" && go doc -all ./acmetext) > "$d/out/godoc.txt" 2>&1
  [ "$1" = after ] && say "--- go doc -all $(go version | cut -d' ' -f3)"
  rendered "skill-info.go, comment $1 the package clause" "$d/out"
}

# ---------------------------------------------------------------- rustdoc
rustdoc_case() {
  local d="$WORK/rustdoc"; rm -rf "$d"; mkdir -p "$d"; cp -R "$SURVIVAL/rust/src" "$SURVIVAL/rust/Cargo.toml" "$d/"
  (cd "$d" && cargo doc --quiet --no-deps) > "$d/log" 2>&1
  say "--- rustdoc, cargo doc $(cargo --version | cut -d' ' -f2) · exit $?"
  rendered "src/skill-info.rs, reached by no mod" "$d/target/doc/acme_text"
  printf '#[path = "skill-info.rs"]\nmod skill_info;\n' >> "$d/src/lib.rs"
  (cd "$d" && rm -rf target/doc && cargo doc --quiet --no-deps --document-private-items) >> "$d/log" 2>&1
  rendered "reached by #[path] mod, --document-private-items" "$d/target/doc/acme_text"
}

# ---------------------------------------------------------------- pydoc and pdoc
python_case() {
  local d="$WORK/pydoc"; rm -rf "$d"; mkdir -p "$d/pydoc" "$d/pdoc"; cp -R "$SURVIVAL/python-uv_build/src" "$d/src"
  (cd "$d/src" && python3 -m pydoc acme_text) > "$d/pydoc/acme_text.txt" 2>&1
  say "--- pydoc $(python3 --version | cut -d' ' -f2), pdoc $(uvx pdoc --version 2>/dev/null | head -1 | cut -d' ' -f2)"
  rendered "pydoc acme_text" "$d/pydoc"
  (cd "$d/src" && uvx pdoc acme_text -o "$d/pdoc") > "$d/pdoc.log" 2>&1
  rendered "pdoc acme_text (HTML)" "$d/pdoc"
}

# ---------------------------------------------------------------- TypeDoc
typedoc_case() { # reexport|standalone
  local d="$WORK/typedoc-$1"; rm -rf "$d"; mkdir -p "$d"
  cp -R "$SURVIVAL/npm/src" "$SURVIVAL/npm/tsconfig.json" "$d/"
  printf '{ "name": "@example/acme-text", "version": "0.1.0", "type": "module" }\n' > "$d/package.json"
  if [ "$1" = reexport ]; then
    printf '/** Returns s normalized. */\nexport function normalize(s: string): string { return s; }\nexport * from "./skill-info.js";\n' > "$d/src/index.ts"
  else
    printf '/** Returns s normalized. */\nexport function normalize(s: string): string { return s; }\n' > "$d/src/index.ts"
  fi
  (cd "$d" && npx --yes typedoc@0.28.20 --entryPoints src/index.ts --out out --skipErrorChecking) > "$d/log" 2>&1
  [ "$1" = reexport ] && say "--- TypeDoc 0.28.20, entry point src/index.ts"
  rendered "skill-info.ts, $( [ "$1" = reexport ] && echo 're-exported from the entry point' || echo 'not imported by the entry point')" "$d/out"
  grep -i 'warning' "$d/log" | sed 's/^/      /' | head -3 | tee -a "$LOG"
}

# ---------------------------------------------------------------- DocC
docc_case() {
  local d="$WORK/docc"; rm -rf "$d"; mkdir -p "$d"; cp -R "$SURVIVAL/swift/Sources" "$d/"
  cat > "$d/Package.swift" <<'EOF'
// swift-tools-version:6.0
import PackageDescription
let package = Package(name: "AcmeText",
  products: [.library(name: "AcmeText", targets: ["AcmeText"])],
  dependencies: [.package(url: "https://github.com/swiftlang/swift-docc-plugin", from: "1.5.0")],
  targets: [.target(name: "AcmeText")])
EOF
  (cd "$d" && swift package --allow-writing-to-directory out generate-documentation --target AcmeText --output-path out) > "$d/log" 2>&1
  say "--- DocC, swift-docc-plugin 1.5.0 · exit $?"
  rendered "skill-info.swift" "$d/out"
}

javadoc_case
dokka_case
godoc_case after; godoc_case before
rustdoc_case
python_case
typedoc_case reexport; typedoc_case standalone
docc_case
echo "work: $WORK"
