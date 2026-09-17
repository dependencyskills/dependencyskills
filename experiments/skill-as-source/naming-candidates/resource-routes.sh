#!/usr/bin/env bash
# Where does a skill shipped as a RESOURCE actually arrive? The v1 approach put skills at
# META-INF/ai-skills/ in the binary jar and they never reached a sources jar (RAD-0065). This
# publishes one Kotlin Multiplatform library (JVM, Android, JS, linuxX64) carrying the same
# skill through every resource route at once — jvmMain resources, commonMain resources, and
# Android's res/raw — and lists which artifact each one lands in.
#
# Needs the Android SDK (ANDROID_HOME, or the default location) for the Android target.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/resource-routes.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }

d="$WORK/lib"; rm -rf "$d"
mkdir -p "$d/src/commonMain/kotlin/com/example/acme/text" \
         "$d/src/commonMain/resources/META-INF/skills" \
         "$d/src/jvmMain/resources/META-INF/skills" \
         "$d/src/androidMain/res/raw"
cat > "$d/settings.gradle.kts" <<'EOF'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "acme-text"
EOF
cat > "$d/build.gradle.kts" <<'EOF'
plugins {
    kotlin("multiplatform") version "2.4.20"
    id("com.android.kotlin.multiplatform.library") version "9.3.2"
    `maven-publish`
}
group = "com.example.acme"; version = "0.1.0"
kotlin {
    jvm()
    android { namespace = "com.example.acme.text"; compileSdk = 37; minSdk = 24; androidResources.enable = true }
    js { nodejs() }
    linuxX64()
}
publishing { repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } } }
EOF
printf 'package com.example.acme.text\nfun normalize(s: String) = s\n' > "$d/src/commonMain/kotlin/com/example/acme/text/Normalizer.kt"
printf '# Skill: acme-text (META-INF, jvmMain)\n' > "$d/src/jvmMain/resources/META-INF/skills/acme-text.md"
printf '# Skill: acme-text (META-INF, commonMain)\n' > "$d/src/commonMain/resources/META-INF/skills/acme-text.md"
printf '# Skill: acme-text (android res/raw)\n' > "$d/src/androidMain/res/raw/skill_acme_text.md"

(cd "$d" && ANDROID_HOME="${ANDROID_HOME:-$HOME/Library/Android/sdk}" "$GRADLE" -q --no-configuration-cache publishAllPublicationsToLocalRepository) > "$d/log" 2>&1
say "--- Kotlin Multiplatform (jvm, android, js, linuxX64), skill placed on every resource route · exit $?"
grep -E '^(w|e):|FAILURE' "$d/log" | sed 's/^/    /' | head -3 | tee -a "$LOG" >/dev/null
say ""
say "    artifact                                       what it carries"
for a in $(find "$d/build/repo" -type f \( -name '*.jar' -o -name '*.klib' -o -name '*.aar' \) | sort); do
  x="$(mktemp -d)"; unzip -qo "$a" -d "$x" 2>/dev/null
  # An AAR keeps the JVM classes and their resources in a nested classes.jar.
  if [ -f "$x/classes.jar" ]; then unzip -qo "$x/classes.jar" -d "$x/classes" 2>/dev/null; fi
  found=$(cd "$x" && find . \( -path '*META-INF/skills/*' -o -path '*res/raw/skill*' \) | sed 's#^\./##' | sed 's#^classes/#classes.jar!/#' | sort | tr '\n' ' ')
  say "    $(printf '%-46s' "$(basename "$a")") ${found:-nothing}"
  rm -rf "$x"
done
say ""
say "Which source set a META-INF entry came from is in the file's own first line."
echo "work: $WORK"
