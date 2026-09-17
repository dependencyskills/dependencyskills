#!/usr/bin/env bash
# Does a skill held as a raw string constant reach the BINARY as well as the source, and what
# happens to it in documentation and under minification? The other two shapes — a doc comment
# and a SKILL.md — are text a compiler never emits, so they reach the sources jar and nothing
# else. This measures the third shape from RAD-0075:
#
#     val skill = """ ... """.trimIndent()
#
# Steps: publish a Kotlin Multiplatform library (JVM, JS, linuxX64) holding one, list every
# artifact that carries the text, render the docs with Dokka for a public and an internal
# declaration, then shrink a consumer with R8 — as an Android application build does — with and
# without a keep rule for the library.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/binary-reach.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }
MARKER="hand-roll a case fold"

library() { # dir, visibility of the declaration
  local d="$1"; rm -rf "$d"; mkdir -p "$d/src/commonMain/kotlin/com/example/acme/text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  cat > "$d/build.gradle.kts" <<EOF
plugins { kotlin("multiplatform") version "2.4.20"; \`maven-publish\`${3:-} }
group = "com.example.acme"; version = "0.1.0"
repositories { mavenCentral() }
kotlin {
    jvm(); js { nodejs() }; linuxX64()
    compilerOptions { allWarningsAsErrors.set(true); extraWarnings.set(true) }
}
publishing { repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } } }
EOF
  printf 'package com.example.acme.text\nfun normalize(s: String) = s\n' > "$d/src/commonMain/kotlin/com/example/acme/text/Normalizer.kt"
  cat > "$d/src/commonMain/kotlin/com/example/acme/text/skill.kt" <<EOF
package com.example.acme.text

$2 val skill = """
    # Skill: acme-text

    Never $MARKER; use \`normalize\`.
""".trimIndent()
EOF
}

# ---------------------------------------------------------------- which artifacts carry it
reach() {
  local d="$WORK/lib"; library "$d" ""
  (cd "$d" && "$GRADLE" -q --no-configuration-cache publishAllPublicationsToLocalRepository) > "$d/log" 2>&1
  say "--- Kotlin Multiplatform (jvm, js, linuxX64), skill as a raw string constant · exit $?"
  grep -E '^(w|e):' "$d/log" | sed 's/^/    /' | head -3 | tee -a "$LOG" >/dev/null
  for a in $(find "$d/build/repo" -type f \( -name '*.jar' -o -name '*.klib' \) | sort); do
    local x; x="$(mktemp -d)"; unzip -qo "$a" -d "$x" 2>/dev/null
    say "    $(printf '%-44s' "$(basename "$a")") files carrying the skill text: $(grep -rla "$MARKER" "$x" 2>/dev/null | wc -l | tr -d ' ')"
    rm -rf "$x"
  done
}

# ---------------------------------------------------------------- what Dokka renders
docs() {
  local d="$WORK/dokka"; library "$d" "" '
    id("org.jetbrains.dokka") version "2.2.0"'
  cat >> "$d/src/commonMain/kotlin/com/example/acme/text/skill.kt" <<EOF

internal val skillInternal = """
    # Skill: acme-text, the internal copy

    Never bend-roll a case fold; use \`normalize\`.
""".trimIndent()
EOF
  (cd "$d" && "$GRADLE" -q --no-configuration-cache dokkaGenerate) > "$d/log" 2>&1
  local html="$d/build/dokka/html"
  say "--- Dokka 2.2.0 · exit $?"
  say "    public declaration appears in       $(grep -rl '\bskill\b' "$html" 2>/dev/null | wc -l | tr -d ' ') files"
  say "    its skill text appears in           $(grep -rla "$MARKER" "$html" 2>/dev/null | wc -l | tr -d ' ') files"
  say "    the internal declaration appears in $(grep -rla 'skillInternal\|bend-roll' "$html" 2>/dev/null | wc -l | tr -d ' ') files"
}

# ---------------------------------------------------------------- R8, as an application build runs it
shrink() {
  local r8 android_jar stdlib d="$WORK/r8"
  r8="$(ls "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/cmdline-tools/latest/lib/r8.jar 2>/dev/null | head -1)"
  android_jar="$(ls "${ANDROID_HOME:-$HOME/Library/Android/sdk}"/platforms/*/android.jar 2>/dev/null | tail -1)"
  stdlib="$(find "${GRADLE_USER_HOME:-$HOME/.gradle}/caches/modules-2/files-2.1/org.jetbrains.kotlin/kotlin-stdlib" -name 'kotlin-stdlib-2.4.20.jar' 2>/dev/null | head -1)"
  if [ -z "$r8" ] || [ -z "$android_jar" ] || [ -z "$stdlib" ]; then
    say "--- R8: skipped (needs the Android SDK command-line tools and a cached kotlin-stdlib)"; return
  fi
  mkdir -p "$d/out" "$d/out-keep"
  local lib; lib="$(find "$WORK/lib/build/repo" -name 'acme-text-jvm-0.1.0.jar' | head -1)"
  printf 'package com.example.app\n\nimport com.example.acme.text.normalize\n\nfun main() {\n    println(normalize("x"))\n}\n' > "$d/Main.kt"
  kotlinc -cp "$lib" "$d/Main.kt" -d "$d/app.jar" > "$d/kotlinc.log" 2>&1
  # A consumer that calls the library and never reads the constant, keeping only its own entry point.
  printf -- '-keep class com.example.app.MainKt { public static void main(java.lang.String[]); }\n-dontwarn **\n' > "$d/rules.pro"
  printf -- '-keep class com.example.app.MainKt { public static void main(java.lang.String[]); }\n-keep class com.example.acme.text.** { *; }\n-dontwarn **\n' > "$d/rules-keep.pro"
  say "--- R8 $(java -cp "$r8" com.android.tools.r8.R8 --version 2>&1 | head -1 | awk '{print $2}'), release, min-api 24"
  r8_run() { # rules file, output dir, label
    java -cp "$r8" com.android.tools.r8.R8 --release --min-api 24 --lib "$android_jar" --lib "$stdlib" \
      --pg-conf "$d/$1" --output "$d/$2" "$d/app.jar" "$lib" > "$d/$2.log" 2>&1
    say "    $(printf '%-40s' "$3") skill text in classes.dex: $(strings "$d/$2/classes.dex" 2>/dev/null | grep -c "$MARKER")"
  }
  r8_run rules.pro out "no keep rule for the library"
  r8_run rules-keep.pro out-keep "with a keep rule for the library"
}

say "gradle $("$GRADLE" --version 2>/dev/null | grep '^Gradle' | cut -d' ' -f2) · kotlin 2.4.20 · $(kotlinc -version 2>&1 | tail -1 | cut -d' ' -f2-3)"
say ""
reach; docs; shrink
echo "work: $WORK"
