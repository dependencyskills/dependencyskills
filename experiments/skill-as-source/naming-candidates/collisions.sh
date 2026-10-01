#!/usr/bin/env bash
# What happens when two skills land on the same path? A convention that names one file per
# package invites collisions wherever a build flattens several artifacts into one: a fat jar, an
# Android resource merge, or a single library whose source sets overlap. Each case here is built
# for real and the result reported, including the build failing, which is itself an answer.
#
# Needs the Android SDK for the resource-merge case; that case skips itself without one.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/collisions.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }

# ------------------------------------------------ one library, the file in two source sets
two_source_sets() {
  local d="$WORK/two-source-sets"; rm -rf "$d"
  mkdir -p "$d/src/main/kotlin/com/example/acme/text" "$d/src/main/java/com/example/acme/text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  printf 'plugins { kotlin("jvm") version "2.4.20" }\nrepositories { mavenCentral() }\njava { withSourcesJar() }\n' > "$d/build.gradle.kts"
  printf 'package com.example.acme.text\nfun normalize(s: String) = s\n' > "$d/src/main/kotlin/com/example/acme/text/Normalizer.kt"
  printf '# Skill from the kotlin source set\n' > "$d/src/main/kotlin/com/example/acme/text/SKILL.md"
  printf '# Skill from the java source set\n' > "$d/src/main/java/com/example/acme/text/SKILL.md"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache sourcesJar) > "$d/log" 2>&1
  local rc=$?
  say "--- One library, SKILL.md in both src/main/kotlin and src/main/java · exit $rc"
  if [ $rc -ne 0 ]; then
    grep -iE 'duplicate|entry|failed' "$d/log" | head -3 | sed 's/^/    /' | tee -a "$LOG" >/dev/null
  else
    local jar; jar="$(ls "$d"/build/libs/*-sources.jar)"
    say "    entries at that path: $(unzip -l "$jar" | grep -c 'com/example/acme/text/SKILL.md')"
    say "    the one that survived: $(unzip -p "$jar" com/example/acme/text/SKILL.md | head -1)"
  fi
}

# ------------------------------------------------ two libraries sharing a package, and a fat jar
split_package() {
  local d="$WORK/split-package"; rm -rf "$d"; mkdir -p "$d"
  for side in a b; do
    mkdir -p "$d/lib-$side/src/main/kotlin/com/example/acme/text" "$d/lib-$side/src/main/resources/META-INF/skills"
    printf 'rootProject.name = "acme-%s"\n' "$side" > "$d/lib-$side/settings.gradle.kts"
    printf 'plugins { kotlin("jvm") version "2.4.20" }\nrepositories { mavenCentral() }\njava { withSourcesJar() }\n' > "$d/lib-$side/build.gradle.kts"
    printf 'package com.example.acme.text\nfun normalize%s(s: String) = s\n' "$side" > "$d/lib-$side/src/main/kotlin/com/example/acme/text/Normalizer$side.kt"
    printf '# Skill from library %s\n' "$side" > "$d/lib-$side/src/main/kotlin/com/example/acme/text/SKILL.md"
    printf '# META-INF skill from library %s\n' "$side" > "$d/lib-$side/src/main/resources/META-INF/skills/acme-text.md"
    (cd "$d/lib-$side" && "$GRADLE" -q --no-configuration-cache jar sourcesJar) >> "$d/log" 2>&1
  done
  say "--- Two libraries sharing the package com.example.acme.text"
  say "    each publishes its own sources jar: $(ls "$d"/lib-*/build/libs/*-sources.jar | wc -l | tr -d ' ') jars, each with one SKILL.md — nothing merges them"
  # A fat jar is where they do meet.
  local f="$d/fat"; mkdir -p "$f"
  printf 'rootProject.name = "fat"\n' > "$f/settings.gradle.kts"
  cat > "$f/build.gradle.kts" <<EOF
plugins { base }
val jars = files("$d/lib-a/build/libs/acme-a.jar", "$d/lib-b/build/libs/acme-b.jar")
tasks.register<Jar>("fatJar") {
    archiveFileName.set("fat.jar")
    from(jars.map { zipTree(it) })
}
tasks.register<Jar>("fatJarFail") {
    archiveFileName.set("fat-fail.jar")
    duplicatesStrategy = DuplicatesStrategy.FAIL
    from(jars.map { zipTree(it) })
}
EOF
  (cd "$f" && "$GRADLE" -q --no-configuration-cache fatJar) > "$f/log" 2>&1
  say "    fat jar, Gradle's default duplicates strategy · exit $?"
  say "        entries at META-INF/skills/acme-text.md: $(unzip -l "$f/build/libs/fat.jar" | grep -c 'META-INF/skills/acme-text.md')"
  say "        the one that survived: $(unzip -p "$f/build/libs/fat.jar" META-INF/skills/acme-text.md | head -1)"
  (cd "$f" && "$GRADLE" -q --no-configuration-cache fatJarFail) > "$f/log-fail" 2>&1
  say "    fat jar with duplicatesStrategy = FAIL · exit $?"
  grep -iE 'duplicate' "$f/log-fail" | head -2 | sed 's/^/        /' | tee -a "$LOG" >/dev/null
}

# ------------------------------------------------ an Android application merging two libraries' res/raw
android_merge() {
  local sdk="${ANDROID_HOME:-$HOME/Library/Android/sdk}"
  [ -d "$sdk" ] || { say "--- Android resource merge: skipped (no Android SDK)"; return; }
  local d="$WORK/android"; rm -rf "$d"; mkdir -p "$d/app/src/main/java/com/example/app"
  cat > "$d/settings.gradle.kts" <<'EOF'
pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement { repositories { google(); mavenCentral() } }
rootProject.name = "merge"
include(":app", ":liba", ":libb")
EOF
  # AGP 9 builds Kotlin itself; the old kotlin("android") plugin is refused.
  printf 'plugins {\n    id("com.android.application") version "9.3.2" apply false\n    id("com.android.library") version "9.3.2" apply false\n}\n' > "$d/build.gradle.kts"
  for side in a b; do
    mkdir -p "$d/lib$side/src/main/res/raw" "$d/lib$side/src/main/java/com/example/lib$side"
    cat > "$d/lib$side/build.gradle.kts" <<EOF
plugins { id("com.android.library") }
android { namespace = "com.example.lib$side"; compileSdk = 37; defaultConfig { minSdk = 24 } }
EOF
    printf '# Skill from library %s\n' "$side" > "$d/lib$side/src/main/res/raw/skill_acme_text.md"
    printf 'package com.example.lib%s\n\nfun hello%s() = "%s"\n' "$side" "$side" "$side" > "$d/lib$side/src/main/java/com/example/lib$side/Hello.kt"
  done
  cat > "$d/app/build.gradle.kts" <<'EOF'
plugins { id("com.android.application") }
android {
    namespace = "com.example.app"; compileSdk = 37
    defaultConfig { applicationId = "com.example.app"; minSdk = 24 }
}
dependencies { implementation(project(":liba")); implementation(project(":libb")) }
EOF
  printf '<?xml version="1.0" encoding="utf-8"?>\n<manifest xmlns:android="http://schemas.android.com/apk/res/android"/>\n' > "$d/app/src/main/AndroidManifest.xml"
  (cd "$d" && ANDROID_HOME="$sdk" "$GRADLE" -q --no-configuration-cache :app:assembleDebug) > "$d/log" 2>&1
  local rc=$?
  say "--- An Android application depending on two libraries, each with res/raw/skill_acme_text.md · exit $rc"
  if [ $rc -ne 0 ]; then
    grep -iE 'duplicate|resource|error' "$d/log" | head -4 | sed 's/^/    /' | tee -a "$LOG" >/dev/null
  else
    local apk; apk="$(find "$d/app/build/outputs" -name '*.apk' | head -1)"
    say "    the build succeeded; entries in the APK: $(unzip -l "$apk" 2>/dev/null | grep -c 'res/raw/skill_acme_text')"
  fi
}

say "gradle $("$GRADLE" --version 2>/dev/null | grep '^Gradle' | cut -d' ' -f2) · kotlin 2.4.20 · AGP 9.3.2"
say ""
two_source_sets; split_package; android_merge
echo "work: $WORK"
