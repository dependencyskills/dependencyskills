#!/usr/bin/env bash
# Does a SKILL.md sitting in a package reach the documentation a person reads? RAD-0073 measured
# this for the source-file shape, where six of eight generators ignored it and pdoc rendered it as
# a module. This is the same question for markdown, which is the criterion the naming decision
# turns on: a skill that lands on the library's own documentation page is a skill in the wrong
# place. Each generator runs the standard way over a package holding one real declaration and a
# SKILL.md beside it; the output is searched for the skill's text.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/markdown-in-docs.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }
MARKER="hand-roll a case fold"
MD="# Skill: acme-text

Never $MARKER; use normalize."

rendered() { # label, output dir, a string proving the real docs rendered
  local text name control
  text=$(grep -rla "$MARKER" "$2" 2>/dev/null | wc -l | tr -d ' ')
  name=$(grep -rla 'SKILL' "$2" 2>/dev/null | wc -l | tr -d ' ')
  control=$(grep -rlai "$3" "$2" 2>/dev/null | wc -l | tr -d ' ')
  local verdict="stays out"
  [ "$text" -gt 0 ] && verdict="SKILL TEXT RENDERED"
  [ "$text" -eq 0 ] && [ "$name" -gt 0 ] && verdict="text out, the name appears"
  [ "$control" -eq 0 ] && verdict="$verdict — BUT the real docs did not render either"
  say "    $(printf '%-34s' "$1") $verdict (files with text: $text, with the name: $name, control: $control)"
}

javadoc_case() {
  local d="$WORK/javadoc"; rm -rf "$d"; mkdir -p "$d/src/com/example/acme/text" "$d/out"
  printf 'package com.example.acme.text;\n\n/** Normalizes text. */\npublic final class Normalizer {\n  private Normalizer() {}\n  /** @param s input @return normalized */\n  public static String normalize(String s) { return s; }\n}\n' > "$d/src/com/example/acme/text/Normalizer.java"
  printf '%s\n' "$MD" > "$d/src/com/example/acme/text/SKILL.md"
  javadoc -quiet -d "$d/out" -sourcepath "$d/src" com.example.acme.text > "$d/log" 2>&1
  say "--- Javadoc $(javac -version 2>&1 | cut -d' ' -f2)"
  rendered "SKILL.md in the package" "$d/out" "Normalizes text"
}

dokka_case() {
  local d="$WORK/dokka"; rm -rf "$d"; mkdir -p "$d/src/main/kotlin/com/example/acme/text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  printf 'plugins { kotlin("jvm") version "2.4.20"; id("org.jetbrains.dokka") version "2.2.0" }\nrepositories { mavenCentral() }\n' > "$d/build.gradle.kts"
  printf 'package com.example.acme.text\n\n/** Normalizes text for comparison. */\nfun normalize(s: String) = s\n' > "$d/src/main/kotlin/com/example/acme/text/Normalizer.kt"
  printf '%s\n' "$MD" > "$d/src/main/kotlin/com/example/acme/text/SKILL.md"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache dokkaGenerate) > "$d/log" 2>&1
  say "--- Dokka 2.2.0 · exit $?"
  rendered "SKILL.md in the source directory" "$d/build/dokka/html" "Normalizes text"
}

godoc_case() {
  local d="$WORK/go"; rm -rf "$d"; mkdir -p "$d/acmetext" "$d/out"
  printf 'module example.com/acmetext\n\ngo 1.27\n' > "$d/go.mod"
  printf '// Package acmetext normalizes text.\npackage acmetext\n\n// Normalize returns s normalized.\nfunc Normalize(s string) string { return s }\n' > "$d/acmetext/normalize.go"
  printf '%s\n' "$MD" > "$d/acmetext/SKILL.md"
  (cd "$d" && go doc -all ./acmetext) > "$d/out/godoc.txt" 2>&1
  say "--- go doc -all $(go version | cut -d' ' -f3)"
  rendered "SKILL.md beside the package" "$d/out" "normalizes text"
}

rustdoc_case() {
  local d="$WORK/rust"; rm -rf "$d"; mkdir -p "$d/src"
  printf '[package]\nname = "acme-text"\nversion = "0.1.0"\nedition = "2024"\ndescription = "fixture"\nlicense = "MIT"\n' > "$d/Cargo.toml"
  printf '//! Acme text.\n\n/// Returns s normalized.\n#[must_use]\npub fn normalize(s: &str) -> &str { s }\n' > "$d/src/lib.rs"
  printf '%s\n' "$MD" > "$d/src/SKILL.md"
  (cd "$d" && cargo doc --quiet --no-deps) > "$d/log" 2>&1
  say "--- rustdoc, cargo doc $(cargo --version | cut -d' ' -f2) · exit $?"
  rendered "SKILL.md in src/" "$d/target/doc/acme_text" "returns s normalized"
}

python_case() {
  local d="$WORK/python"; rm -rf "$d"; mkdir -p "$d/src/acme_text" "$d/pydoc" "$d/pdoc"
  printf '"""Acme text."""\n' > "$d/src/acme_text/__init__.py"
  printf 'def normalize(s):\n    """Return s normalized."""\n    return s\n' > "$d/src/acme_text/normalizer.py"
  printf '%s\n' "$MD" > "$d/src/acme_text/SKILL.md"
  (cd "$d/src" && python3 -m pydoc acme_text) > "$d/pydoc/acme_text.txt" 2>&1
  say "--- pydoc $(python3 --version | cut -d' ' -f2), pdoc $(uvx pdoc --version 2>/dev/null | head -1 | cut -d' ' -f2)"
  rendered "pydoc acme_text" "$d/pydoc" "acme text"
  (cd "$d/src" && uvx pdoc acme_text -o "$d/pdoc") > "$d/pdoc.log" 2>&1
  rendered "pdoc acme_text (HTML)" "$d/pdoc" "acme text"
}

typedoc_case() {
  local d="$WORK/typedoc"; rm -rf "$d"; mkdir -p "$d/src"
  printf '{ "name": "@example/acme-text", "version": "0.1.0", "type": "module" }\n' > "$d/package.json"
  printf '{ "compilerOptions": { "strict": true, "declaration": true, "module": "nodenext", "target": "es2024", "outDir": "dist", "rootDir": "src" }, "include": ["src"] }\n' > "$d/tsconfig.json"
  printf '/** Returns s normalized. */\nexport function normalize(s: string): string { return s; }\n' > "$d/src/index.ts"
  printf '%s\n' "$MD" > "$d/src/SKILL.md"
  (cd "$d" && npx --yes typedoc@0.28.20 --entryPoints src/index.ts --out out --skipErrorChecking) > "$d/log" 2>&1
  say "--- TypeDoc 0.28.20 · exit $?"
  rendered "SKILL.md in src/" "$d/out" "returns s normalized"
}

docc_case() {
  local d="$WORK/swift"; rm -rf "$d"; mkdir -p "$d/Sources/AcmeText"
  cat > "$d/Package.swift" <<'EOF'
// swift-tools-version:6.0
import PackageDescription
let package = Package(name: "AcmeText",
  products: [.library(name: "AcmeText", targets: ["AcmeText"])],
  dependencies: [.package(url: "https://github.com/swiftlang/swift-docc-plugin", from: "1.5.0")],
  targets: [.target(name: "AcmeText", exclude: ["SKILL.md"])])
EOF
  printf '/// Returns s normalized.\npublic func normalize(_ s: String) -> String { s }\n' > "$d/Sources/AcmeText/Normalizer.swift"
  printf '%s\n' "$MD" > "$d/Sources/AcmeText/SKILL.md"
  (cd "$d" && swift package --allow-writing-to-directory out generate-documentation --target AcmeText --output-path out) > "$d/log" 2>&1
  say "--- DocC, swift-docc-plugin 1.5.0, SKILL.md excluded in Package.swift · exit $?"
  rendered "SKILL.md in the target" "$d/out" "returns s normalized"
}

say "Each generator renders a package holding one documented declaration and a SKILL.md beside it."
say "The control column counts files carrying the real doc comment: 0 there means the run proves nothing."
say ""
javadoc_case; dokka_case; godoc_case; rustdoc_case; python_case; typedoc_case; docc_case
echo "work: $WORK"
