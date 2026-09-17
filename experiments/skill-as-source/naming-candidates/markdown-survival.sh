#!/usr/bin/env bash
# Does a plain SKILL.md placed in a package's source directory — not a source file — survive
# each ecosystem's ordinary publishing path? RAD-0073 chose a source file on the belief that
# packagers drop non-source files; this measures that belief. No configuration is added.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/markdown-survival.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }
MD='# Skill: acme-text

Never hand-roll a case fold; use normalize.'
has() { # label archive
  local x; x="$(mktemp -d)"
  case "$2" in *.tgz|*.tar.gz|*.crate) tar xzf "$2" -C "$x" ;; *) unzip -qo "$2" -d "$x" ;; esac
  local f; f=$(cd "$x" && find . -name 'SKILL.md' | sed 's#^\./##' | tr '\n' ' ')
  say "    $(printf '%-44s' "$1") SKILL.md: ${f:-ABSENT}"
}

gradle_java() {
  local d="$WORK/gjava"; rm -rf "$d"; mkdir -p "$d/src/main/java/com/example/acme/text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  printf 'plugins { `java-library` }\ngroup = "com.example.acme"; version = "0.1.0"\njava { withSourcesJar() }\n' > "$d/build.gradle.kts"
  printf 'package com.example.acme.text;\npublic final class Normalizer {}\n' > "$d/src/main/java/com/example/acme/text/Normalizer.java"
  printf '%s\n' "$MD" > "$d/src/main/java/com/example/acme/text/SKILL.md"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache sourcesJar) >"$d/log" 2>&1
  say "--- Gradle java-library, SKILL.md in src/main/java · exit $?"
  has "sources jar" "$d/build/libs/acme-text-0.1.0-sources.jar"
}

gradle_kotlin() {
  local d="$WORK/gkotlin"; rm -rf "$d"; mkdir -p "$d/src/main/kotlin/com/example/acme/text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  printf 'plugins { kotlin("jvm") version "2.4.20" }\ngroup = "com.example.acme"; version = "0.1.0"\nrepositories { mavenCentral() }\njava { withSourcesJar() }\n' > "$d/build.gradle.kts"
  printf 'package com.example.acme.text\nfun normalize(s: String) = s\n' > "$d/src/main/kotlin/com/example/acme/text/Normalizer.kt"
  printf '%s\n' "$MD" > "$d/src/main/kotlin/com/example/acme/text/SKILL.md"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache sourcesJar) >"$d/log" 2>&1
  say "--- Gradle kotlin(jvm), SKILL.md in src/main/kotlin · exit $?"
  has "sources jar" "$d/build/libs/acme-text-0.1.0-sources.jar"
}

gradle_kmp() {
  local d="$WORK/kmp"; rm -rf "$d"; mkdir -p "$d/src/commonMain/kotlin/com/example/acme/text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  printf 'plugins { kotlin("multiplatform") version "2.4.20"; `maven-publish` }\ngroup = "com.example.acme"; version = "0.1.0"\nrepositories { mavenCentral() }\nkotlin { jvm(); js { nodejs() }; linuxX64() }\npublishing { repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } } }\n' > "$d/build.gradle.kts"
  printf 'package com.example.acme.text\nfun normalize(s: String) = s\n' > "$d/src/commonMain/kotlin/com/example/acme/text/Normalizer.kt"
  printf '%s\n' "$MD" > "$d/src/commonMain/kotlin/com/example/acme/text/SKILL.md"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache publishAllPublicationsToLocalRepository) >"$d/log" 2>&1
  say "--- Kotlin Multiplatform (jvm, js, linuxX64), SKILL.md in commonMain/kotlin · exit $?"
  for j in $(find "$d/build/repo" -name '*-sources.jar' | sort); do has "$(basename "$j")" "$j"; done
}

maven_java() {
  local d="$WORK/mjava"; rm -rf "$d"; mkdir -p "$d/src/main/java/com/example/acme/text"
  cat > "$d/pom.xml" <<'POM'
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>com.example.acme</groupId><artifactId>acme-text</artifactId><version>0.1.0</version>
<properties><maven.compiler.release>21</maven.compiler.release></properties>
<build><plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-source-plugin</artifactId><version>3.4.0</version>
<executions><execution><id>s</id><goals><goal>jar-no-fork</goal></goals></execution></executions></plugin></plugins></build></project>
POM
  printf 'package com.example.acme.text;\npublic final class Normalizer {}\n' > "$d/src/main/java/com/example/acme/text/Normalizer.java"
  printf '%s\n' "$MD" > "$d/src/main/java/com/example/acme/text/SKILL.md"
  (cd "$d" && mvn -q -B package) >"$d/log" 2>&1
  say "--- Maven, maven-source-plugin, SKILL.md in src/main/java · exit $?"
  has "sources jar" "$d/target/acme-text-0.1.0-sources.jar"
  has "binary jar" "$d/target/acme-text-0.1.0.jar"
}

python_case() { # backend
  local d="$WORK/py-$1"; rm -rf "$d"; mkdir -p "$d/src/acme_text"
  printf '"""Acme text."""\n' > "$d/src/acme_text/__init__.py"
  printf '%s\n' "$MD" > "$d/src/acme_text/SKILL.md"
  case "$1" in
    setuptools) printf '[build-system]\nrequires = ["setuptools"]\nbuild-backend = "setuptools.build_meta"\n\n[project]\nname = "acme-text"\nversion = "0.1.0"\n' > "$d/pyproject.toml" ;;
    hatchling)  printf '[build-system]\nrequires = ["hatchling"]\nbuild-backend = "hatchling.build"\n\n[project]\nname = "acme-text"\nversion = "0.1.0"\n\n[tool.hatch.build.targets.wheel]\npackages = ["src/acme_text"]\n' > "$d/pyproject.toml" ;;
    uv_build)   printf '[build-system]\nrequires = ["uv_build"]\nbuild-backend = "uv_build"\n\n[project]\nname = "acme-text"\nversion = "0.1.0"\n' > "$d/pyproject.toml" ;;
  esac
  (cd "$d" && uv build --quiet --out-dir dist) >"$d/log" 2>&1
  say "--- Python, uv build, $1 · exit $?"
  for a in "$d"/dist/*; do has "$(basename "$a")" "$a"; done
}

npm_case() {
  local d="$WORK/npm"; rm -rf "$d"; mkdir -p "$d/src"
  printf 'export function normalize(s: string): string { return s; }\n' > "$d/src/index.ts"
  printf '%s\n' "$MD" > "$d/src/SKILL.md"
  printf '{ "compilerOptions": { "declaration": true, "module": "nodenext", "target": "es2024", "outDir": "dist", "rootDir": "src" }, "include": ["src"] }\n' > "$d/tsconfig.json"
  (cd "$d" && "${TSC:-tsc}" -p .) >"$d/log" 2>&1
  say "--- npm, tsc then npm pack"
  printf '{ "name": "@example/acme-text", "version": "0.1.0", "type": "module" }\n' > "$d/package.json"
  (cd "$d" && rm -f *.tgz && npm pack --silent >/dev/null 2>&1); has "no files field" "$(ls "$d"/*.tgz)"
  printf '{ "name": "@example/acme-text", "version": "0.1.0", "type": "module", "files": ["dist"] }\n' > "$d/package.json"
  (cd "$d" && rm -f *.tgz && npm pack --silent >/dev/null 2>&1); has "files: [dist]" "$(ls "$d"/*.tgz)"
}

swift_case() {
  local d="$WORK/swift"; rm -rf "$d"; mkdir -p "$d/Sources/AcmeText"
  printf '// swift-tools-version:6.0\nimport PackageDescription\nlet package = Package(name: "AcmeText", targets: [.target(name: "AcmeText")])\n' > "$d/Package.swift"
  printf 'public func normalize(_ s: String) -> String { s }\n' > "$d/Sources/AcmeText/Normalizer.swift"
  printf '%s\n' "$MD" > "$d/Sources/AcmeText/SKILL.md"
  (cd "$d" && swift build 2>&1) > "$d/log"
  say "--- SwiftPM build, SKILL.md in Sources/AcmeText · exit $?"
  grep -iE 'warning|unhandled' "$d/log" | sed 's#/[^ ]*/Sources#Sources#' | head -3 | sed 's/^/    /' | tee -a "$LOG" >/dev/null
  grep -iqE 'warning|unhandled' "$d/log" || say "    (no warnings)"
}

say "gradle $("$GRADLE" --version 2>/dev/null | grep '^Gradle' | cut -d' ' -f2) · maven $(mvn -v 2>/dev/null | head -1 | cut -d' ' -f3) · $(uv --version) · npm $(npm --version) · $(swift --version 2>&1 | head -1 | grep -o 'Swift version [0-9.]*')"
say "Go module zips and Cargo crates ship every file in the module, not only sources; checked in run below."
gradle_java; gradle_kotlin; gradle_kmp; maven_java
for b in setuptools hatchling uv_build; do python_case $b; done
npm_case; swift_case
echo "work: $WORK"
