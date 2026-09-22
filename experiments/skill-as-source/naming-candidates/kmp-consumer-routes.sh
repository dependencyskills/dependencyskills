#!/usr/bin/env bash
# One Kotlin Multiplatform library reaches consumers by several different routes, and only some of
# them carry source at all. A Kotlin consumer resolves Maven artifacts and gets Kotlin sources; a
# JavaScript consumer installs an npm package built by Kotlin/JS; a Swift consumer gets an
# XCFramework with Objective-C headers. This builds every route from one library and reports, for
# each, whether a SKILL.md placed in the source set arrives.
#
# The library carries four skills — commonMain, jvmMain, jsMain, iosArm64Main — so each route can
# be judged on what it should have carried.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/kmp-consumer-routes.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }

d="$WORK/lib"; rm -rf "$d"
for set in commonMain jvmMain jsMain iosArm64Main; do mkdir -p "$d/src/$set/kotlin/com/example/acme/text"; done
mkdir -p "$d/src/commonMain/resources/META-INF/skills"

cat > "$d/settings.gradle.kts" <<'EOF'
rootProject.name = "acme-text"
EOF
cat > "$d/build.gradle.kts" <<'EOF'
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins { kotlin("multiplatform") version "2.4.20"; `maven-publish` }
group = "com.example.acme"; version = "0.1.0"
repositories { mavenCentral() }

val xcf = XCFramework("AcmeText")

kotlin {
    jvm()
    js { browser(); binaries.library(); generateTypeScriptDefinitions() }
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs { browser(); binaries.library() }
    iosArm64 { binaries.framework { baseName = "AcmeText"; xcf.add(this) } }
    iosSimulatorArm64 { binaries.framework { baseName = "AcmeText"; xcf.add(this) } }
}
publishing { repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } } }
EOF

printf 'package com.example.acme.text\n\nfun normalize(s: String) = s\n' > "$d/src/commonMain/kotlin/com/example/acme/text/Normalizer.kt"
printf 'package com.example.acme.text\n\nfun fromFile(path: String) = path\n' > "$d/src/jvmMain/kotlin/com/example/acme/text/Jvm.kt"
printf 'package com.example.acme.text\n\nfun fromJs(s: String) = s\n' > "$d/src/jsMain/kotlin/com/example/acme/text/Js.kt"
printf 'package com.example.acme.text\n\nfun fromIos(s: String) = s\n' > "$d/src/iosArm64Main/kotlin/com/example/acme/text/Ios.kt"
for set in commonMain jvmMain jsMain iosArm64Main; do
  printf '# Skill: acme-text (%s)\n\nNever hand-roll a case fold; use normalize.\n' "$set" > "$d/src/$set/kotlin/com/example/acme/text/SKILL.md"
done
printf '# Skill: acme-text (commonMain resource)\n' > "$d/src/commonMain/resources/META-INF/skills/acme-text.md"

carries() { # label, directory or archive
  local x="$1" target="$2" found
  if [ -d "$target" ]; then
    found=$(cd "$target" && find . -iname 'SKILL*.md' -o -iname 'acme-text.md' | sed 's#^\./##' | sort | tr '\n' ' ')
  else
    local t; t="$(mktemp -d)"; unzip -qo "$target" -d "$t" 2>/dev/null
    found=$(cd "$t" && find . -iname 'SKILL*.md' -o -iname 'acme-text.md' | sed 's#^\./##' | sort | tr '\n' ' '); rm -rf "$t"
  fi
  say "    $(printf '%-44s' "$x") ${found:-nothing}"
}

say "Gradle $("$GRADLE" --version 2>/dev/null | grep '^Gradle' | cut -d' ' -f2) · Kotlin 2.4.20 · $(swift --version 2>&1 | head -1 | grep -o 'Swift version [0-9.]*')"
say "Skills placed in commonMain, jvmMain, jsMain and iosArm64Main, plus one as a commonMain resource."
say ""

(cd "$d" && "$GRADLE" -q --no-configuration-cache publishAllPublicationsToLocalRepository) > "$d/publish.log" 2>&1
say "--- Maven artifacts, what a Kotlin consumer resolves · exit $?"
for a in $(find "$d/build/repo" -type f \( -name '*.jar' -o -name '*.klib' \) | sort); do carries "$(basename "$a")" "$a"; done

(cd "$d" && "$GRADLE" -q --no-configuration-cache jsBrowserProductionLibraryDistribution) > "$d/js.log" 2>&1
say "--- Kotlin/JS npm package, what a JavaScript consumer installs · exit $?"
for dist in "$d/build/dist/js/productionLibrary" "$d/build/productionLibrary" "$d/build/js/packages/acme-text"; do
  [ -d "$dist" ] && { carries "$(basename "$dist")" "$dist"; say "        it contains: $(cd "$dist" && ls | tr '\n' ' ')"; }
done

(cd "$d" && "$GRADLE" -q --no-configuration-cache wasmJsBrowserProductionLibraryDistribution) > "$d/wasm.log" 2>&1
say "--- Kotlin/Wasm npm package · exit $?"
for dist in "$d/build/dist/wasmJs/productionLibrary"; do
  [ -d "$dist" ] && { carries "$(basename "$dist")" "$dist"; say "        it contains: $(cd "$dist" && ls | tr '\n' ' ')"; }
done

(cd "$d" && "$GRADLE" -q --no-configuration-cache assembleAcmeTextXCFramework) > "$d/xcf.log" 2>&1
say "--- XCFramework, what a Swift consumer gets through SPM or CocoaPods · exit $?"
xcf="$(find "$d/build/XCFrameworks" -maxdepth 2 -name '*.xcframework' 2>/dev/null | head -1)"
if [ -n "$xcf" ]; then
  carries "$(basename "$xcf")" "$xcf"
  say "        it contains: $(cd "$xcf" && find . -maxdepth 2 | sed 's#^\./##' | grep -v '^$' | head -6 | tr '\n' ' ')"
  say "        header sources: $(find "$xcf" -name '*.h' | wc -l | tr -d ' ') headers, $(find "$xcf" -name '*.kt' | wc -l | tr -d ' ') Kotlin files"
else
  say "    no XCFramework produced; see xcf.log"
  grep -iE 'what went wrong|error:' "$d/xcf.log" | head -3 | sed 's/^/        /' | tee -a "$LOG" >/dev/null
fi
echo "work: $WORK"
