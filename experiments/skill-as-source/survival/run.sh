#!/usr/bin/env bash
# RAD-0073 test 1: does a documentation-only skill-info source file survive into
# what each ecosystem actually publishes?
#
# Every case builds a tiny library holding one real function plus skill-info.<ext>,
# packages it with the ecosystem's ordinary publishing tool — no configuration
# added for the skill — and lists the artifact. "file" is whether skill-info is
# present by name; "text" is whether the skill text is present anywhere inside.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
LOG="$HERE/results.txt"
GRADLE="${GRADLE:-gradle}"
: > "$LOG"

say() { printf '%s\n' "$*" | tee -a "$LOG"; }

SKILL_BODY='Skill: acme-text.

Normalize user input with normalize before comparing; never
hand-roll a case fold.

Wrong:   a.lowercase() == b.lowercase()
Correct: normalize(a) == normalize(b)'

block() { printf '%s\n' "$SKILL_BODY" | sed "s#^#$1#"; }

# inspect <label> <archive-or-dir> : unpack if needed, report file and text presence
inspect() {
  local label="$1" a="$2" x
  if [ -d "$a" ]; then x="$a"
  else
    x="$(mktemp -d)"
    case "$a" in
      *.tgz|*.tar.gz|*.crate) tar xzf "$a" -C "$x" ;;
      *) unzip -qo "$a" -d "$x" ;;
    esac
  fi
  local files text
  files=$(cd "$x" && find . -iname 'skill-info*' ! -path '*/__pycache__/*' | sed 's#^\./##' | sort | tr '\n' ' ')
  text=$(grep -rl 'hand-roll' "$x" 2>/dev/null | wc -l | tr -d ' ')
  say "    $(printf '%-44s' "$label") file: ${files:-ABSENT}  · files carrying text: $text"
}

versions() {
  say "gradle: $("$GRADLE" --version 2>/dev/null | grep '^Gradle')"
  say "node:   $(node --version) · npm $(npm --version) · bun $(bun --version)"
  say "uv:     $(uv --version)"
  say "go:     $(go version)"
  say "cargo:  $(cargo --version)"
  say "swift:  $(swift --version 2>&1 | head -1)"
  say ""
}

# ---------------------------------------------------------------- Java, Gradle java-library + maven-publish
java_gradle() {
  local d="$WORK/java"; mkdir -p "$d/src/main/java/com/example/acme/text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  cat > "$d/build.gradle.kts" <<'EOF'
plugins { `java-library`; `maven-publish` }
group = "com.example.acme"; version = "0.1.0"
java { withSourcesJar(); withJavadocJar() }
repositories { mavenCentral() }
publishing {
  publications { create<MavenPublication>("lib") { from(components["java"]) } }
  repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } }
}
EOF
  printf 'package com.example.acme.text;\n\n/** Normalizes text. */\npublic final class Normalizer {\n  private Normalizer() {}\n  /** @param s input @return normalized */\n  public static String normalize(String s) { return s; }\n}\n' > "$d/src/main/java/com/example/acme/text/Normalizer.java"
  { echo 'package com.example.acme.text;'; echo; echo '/**'; block ' * '; echo ' */'; } > "$d/src/main/java/com/example/acme/text/skill-info.java"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache publishAllPublicationsToLocalRepository) > "$d/log" 2>&1
  say "--- Java · Gradle java-library, maven-publish · exit $?"
  for a in "$d"/build/repo/com/example/acme/acme-text/0.1.0/*.jar; do inspect "$(basename "$a")" "$a"; done
}

# ---------------------------------------------------------------- Kotlin/JVM, Gradle
kotlin_jvm_gradle() {
  local d="$WORK/kotlin-jvm"; mkdir -p "$d/src/main/kotlin/com/example/acme/text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  cat > "$d/build.gradle.kts" <<'EOF'
plugins { kotlin("jvm") version "2.4.20"; `maven-publish` }
group = "com.example.acme"; version = "0.1.0"
java { withSourcesJar() }
repositories { mavenCentral() }
publishing {
  publications { create<MavenPublication>("lib") { from(components["java"]) } }
  repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } }
}
EOF
  printf 'package com.example.acme.text\n\nfun normalize(s: String): String = s\n' > "$d/src/main/kotlin/com/example/acme/text/Normalizer.kt"
  { echo '/**'; block ' * '; echo ' */'; echo 'package com.example.acme.text'; } > "$d/src/main/kotlin/com/example/acme/text/skill-info.kt"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache publishAllPublicationsToLocalRepository) > "$d/log" 2>&1
  say "--- Kotlin/JVM · Gradle kotlin(jvm), maven-publish · exit $?"
  for a in "$d"/build/repo/com/example/acme/acme-text/0.1.0/*.jar; do inspect "$(basename "$a")" "$a"; done
}

# ---------------------------------------------------------------- TypeScript, npm
npm_case() {
  local d="$WORK/npm"; mkdir -p "$d/src"
  printf 'export function normalize(s: string): string { return s; }\nexport * from "./skill-info.js";\n' > "$d/src/index.ts"
  { echo '/**'; block ' * '; echo ' * @packageDocumentation'; echo ' */'; echo 'export {};'; } > "$d/src/skill-info.ts"
  printf '{ "compilerOptions": { "strict": true, "declaration": true, "module": "nodenext", "target": "es2024", "outDir": "dist", "rootDir": "src" }, "include": ["src"] }\n' > "$d/tsconfig.json"
  (cd "$d" && "${TSC:-tsc}" -p .) > "$d/log" 2>&1
  say "--- TypeScript · tsc then npm pack · exit $?"

  printf '{ "name": "@example/acme-text", "version": "0.1.0", "type": "module", "main": "dist/index.js", "types": "dist/index.d.ts" }\n' > "$d/package.json"
  (cd "$d" && rm -f *.tgz && npm pack --silent >/dev/null 2>&1); inspect "no files field (everything)" "$(ls "$d"/*.tgz)"

  printf '{ "name": "@example/acme-text", "version": "0.1.0", "type": "module", "main": "dist/index.js", "types": "dist/index.d.ts", "files": ["dist"] }\n' > "$d/package.json"
  (cd "$d" && rm -f *.tgz && npm pack --silent >/dev/null 2>&1); inspect "files: [dist] (the common shape)" "$(ls "$d"/*.tgz)"

  # Bundled and minified, as a great many npm packages ship.
  mkdir -p "$d/bundle"
  (cd "$d" && bun build src/index.ts --outfile bundle/index.js --minify) >> "$d/log" 2>&1
  inspect "bun build --minify (bundle)" "$d/bundle"
  # A skill file nothing imports, bundled: is it even reached?
  printf 'export function normalize(s: string): string { return s; }\n' > "$d/src/index.ts"
  rm -rf "$d/bundle2"; mkdir -p "$d/bundle2"
  (cd "$d" && bun build src/index.ts --outfile bundle2/index.js) >> "$d/log" 2>&1
  inspect "bun build, skill-info not imported" "$d/bundle2"
}

# ---------------------------------------------------------------- Python, sdist + wheel per backend
python_case() { # backend
  local d="$WORK/python-$1"; mkdir -p "$d/src/acme_text"
  printf '"""Acme text."""\n' > "$d/src/acme_text/__init__.py"
  printf 'def normalize(s):\n    return s\n' > "$d/src/acme_text/normalizer.py"
  { echo '"""'; block ''; echo '"""'; } > "$d/src/acme_text/skill-info.py"
  case "$1" in
    setuptools) printf '[build-system]\nrequires = ["setuptools"]\nbuild-backend = "setuptools.build_meta"\n\n[project]\nname = "acme-text"\nversion = "0.1.0"\n' > "$d/pyproject.toml" ;;
    hatchling)  printf '[build-system]\nrequires = ["hatchling"]\nbuild-backend = "hatchling.build"\n\n[project]\nname = "acme-text"\nversion = "0.1.0"\n\n[tool.hatch.build.targets.wheel]\npackages = ["src/acme_text"]\n' > "$d/pyproject.toml" ;;
    uv_build)   printf '[build-system]\nrequires = ["uv_build"]\nbuild-backend = "uv_build"\n\n[project]\nname = "acme-text"\nversion = "0.1.0"\n' > "$d/pyproject.toml" ;;
  esac
  (cd "$d" && uv build --quiet --out-dir dist) > "$d/log" 2>&1
  say "--- Python · uv build, $1 backend · exit $?"
  for a in "$d"/dist/*; do inspect "$(basename "$a")" "$a"; done
  if [ "$1" = setuptools ]; then
    (cd "$d" && rm -rf venv && uv venv --quiet venv && uv pip install --quiet --python venv/bin/python dist/*.whl) >> "$d/log" 2>&1
    inspect "installed into a venv (site-packages)" "$(ls -d "$d"/venv/lib/python*/site-packages/acme_text)"
  fi
}

# ---------------------------------------------------------------- Go, module zip as the proxy serves it
go_case() {
  local d="$WORK/go"; mkdir -p "$d/mod/acmetext" "$d/zipper"
  printf 'module example.com/acmetext\n\ngo 1.27\n' > "$d/mod/go.mod"
  printf '// Package acmetext normalizes text.\npackage acmetext\n\n// Normalize returns s normalized.\nfunc Normalize(s string) string { return s }\n' > "$d/mod/acmetext/normalize.go"
  { echo 'package acmetext'; echo; block '// '; } > "$d/mod/acmetext/skill-info.go"
  { echo 'package acmetext'; echo; block '// '; } > "$d/mod/acmetext/_skill-info-underscore.go"
  cat > "$d/zipper/main.go" <<'EOF'
// Builds a module zip exactly as a module proxy does, using golang.org/x/mod/zip.
package main

import (
	"os"

	"golang.org/x/mod/module"
	"golang.org/x/mod/zip"
)

func main() {
	f, err := os.Create(os.Args[2])
	if err != nil { panic(err) }
	defer f.Close()
	if err := zip.CreateFromDir(f, module.Version{Path: "example.com/acmetext", Version: "v0.1.0"}, os.Args[1]); err != nil { panic(err) }
}
EOF
  (cd "$d/zipper" && go mod init zipper >/dev/null 2>&1; go get golang.org/x/mod@latest >/dev/null 2>&1; go run . ../mod ../acmetext-v0.1.0.zip) > "$d/log" 2>&1
  say "--- Go · module zip via golang.org/x/mod/zip · exit $?"
  inspect "acmetext@v0.1.0.zip" "$d/acmetext-v0.1.0.zip"
  say "    underscore file in the zip: $(unzip -l "$d/acmetext-v0.1.0.zip" | grep -c '_skill-info-underscore.go')"
  # A consumer that vendors: go mod vendor copies only what the build uses.
  mkdir -p "$d/consumer"
  printf 'module example.com/consumer\n\ngo 1.27\n\nrequire example.com/acmetext v0.1.0\n\nreplace example.com/acmetext => ../mod\n' > "$d/consumer/go.mod"
  printf 'package main\n\nimport "example.com/acmetext/acmetext"\n\nfunc main() { _ = acmetext.Normalize("x") }\n' > "$d/consumer/main.go"
  (cd "$d/consumer" && rm -rf vendor && go mod vendor) >> "$d/log" 2>&1
  inspect "consumer go mod vendor" "$d/consumer/vendor"
  say "    underscore file vendored: $(find "$d/consumer/vendor" -name '_skill-info-underscore.go' | wc -l | tr -d ' ')"
}

# ---------------------------------------------------------------- Rust, crate
rust_case() {
  local d="$WORK/rust"; mkdir -p "$d/src"
  printf '[package]\nname = "acme-text"\nversion = "0.1.0"\nedition = "2024"\ndescription = "fixture"\nlicense = "MIT"\n' > "$d/Cargo.toml"
  printf '//! Acme text.\n\n/// Returns s normalized.\n#[must_use]\npub fn normalize(s: &str) -> &str { s }\n' > "$d/src/lib.rs"
  block '//! ' > "$d/src/skill-info.rs"
  (cd "$d" && git init -q && git add -A && git -c user.name=fixture -c user.email=fixture@example.com commit -qm fixture && cargo package --quiet) > "$d/log" 2>&1
  say "--- Rust · cargo package · exit $?"
  inspect "acme-text-0.1.0.crate" "$d/target/package/acme-text-0.1.0.crate"
}

# ---------------------------------------------------------------- Swift, source archive
swift_case() {
  local d="$WORK/swift"; mkdir -p "$d/Sources/AcmeText"
  printf '// swift-tools-version:6.0\nimport PackageDescription\nlet package = Package(name: "AcmeText", products: [.library(name: "AcmeText", targets: ["AcmeText"])], targets: [.target(name: "AcmeText")])\n' > "$d/Package.swift"
  printf 'public func normalize(_ s: String) -> String { s }\n' > "$d/Sources/AcmeText/Normalizer.swift"
  block '/// ' > "$d/Sources/AcmeText/skill-info.swift"
  (cd "$d" && git init -q && git add -A && git -c user.name=fixture -c user.email=fixture@example.com commit -qm fixture && swift package archive-source --output AcmeText.zip) > "$d/log" 2>&1
  say "--- Swift · swift package archive-source · exit $?"
  inspect "AcmeText.zip" "$d/AcmeText.zip"
}

versions
java_gradle
kotlin_jvm_gradle
npm_case
for b in setuptools hatchling uv_build; do python_case $b; done
go_case
rust_case
swift_case
echo "work: $WORK"
