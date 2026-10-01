#!/usr/bin/env bash
# npm authors keep skills in a directory beside the source: skills/<name>/SKILL.md, shipped by a
# files entry. The JVM equivalent would be a src/skills tree. Does it reach the published
# artifacts on its own, and what is the smallest build change that makes it?
#
# Each case reports where the file landed: the sources jar, which is what an agent reads, and the
# binary jar, where a resource would also travel.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/skills-source-dir.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }
SKILL='# Skill: acme-text

Never hand-roll a case fold; use normalize.'

where() { # label, dir holding built jars, glob for the sources jar, glob for the binary jar
  local src bin
  src="$(ls $2/$3 2>/dev/null | head -1)"; bin="$(ls $2/$4 2>/dev/null | head -1)"
  local s b
  s=$( [ -n "$src" ] && unzip -l "$src" 2>/dev/null | grep -oE '[^ ]*SKILL\.md' | head -1 || true )
  b=$( [ -n "$bin" ] && unzip -l "$bin" 2>/dev/null | grep -oE '[^ ]*SKILL\.md' | head -1 || true )
  say "    $(printf '%-46s' "$1") sources jar: ${s:-ABSENT}   binary jar: ${b:-ABSENT}"
}

gradle_case() { # label, extra build script lines, skill directory, work directory name
  local d="$WORK/$4"; rm -rf "$d"; mkdir -p "$d/src/main/kotlin/com/example/acme/text" "$d/$3"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  { printf 'plugins { kotlin("jvm") version "2.4.20" }\nrepositories { mavenCentral() }\njava { withSourcesJar() }\n'
    printf '%s\n' "$2"; } > "$d/build.gradle.kts"
  printf 'package com.example.acme.text\nfun normalize(s: String) = s\n' > "$d/src/main/kotlin/com/example/acme/text/Normalizer.kt"
  printf '%s\n' "$SKILL" > "$d/$3/SKILL.md"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache jar sourcesJar) > "$d/log" 2>&1 || say "    build failed: $(grep -m1 'What went wrong' -A2 "$d/log" | tail -1 | cut -c1-90)"
  where "$1" "$d/build/libs" '*-sources.jar' 'acme-text.jar'
}

say "Gradle $("$GRADLE" --version 2>/dev/null | grep '^Gradle' | cut -d' ' -f2), Kotlin 2.4.20"
say "The npm shape is a skills/ directory beside the source, so the first cases put it at src/skills — a sibling of src/main."
say ""
say "--- Gradle, Kotlin JVM"
gradle_case "src/skills, no build change" "" "src/skills/acme-text" plain
gradle_case "src/skills as a Kotlin source directory" 'kotlin.sourceSets["main"].kotlin.srcDir("src/skills")' "src/skills/acme-text" ktsrc
gradle_case "src/skills as a resource directory" 'sourceSets["main"].resources.srcDir("src/skills")' "src/skills/acme-text" res
gradle_case "src/skills added to sourcesJar only" 'tasks.named<Jar>("sourcesJar") { from("src/skills") { into("skills") } }' "src/skills/acme-text" srcjar
gradle_case "src/main/skills, no build change" "" "src/main/skills/acme-text" plainmain
gradle_case "beside the code, for comparison" "" "src/main/kotlin/com/example/acme/text" inline

# ---------------------------------------------------------------- Kotlin Multiplatform
kmp_case() {
  local d="$WORK/kmp"; rm -rf "$d"; mkdir -p "$d/src/commonMain/kotlin/com/example/acme/text" "$d/src/skills/acme-text"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  cat > "$d/build.gradle.kts" <<'EOF'
plugins { kotlin("multiplatform") version "2.4.20"; `maven-publish` }
group = "com.example.acme"; version = "0.1.0"
repositories { mavenCentral() }
kotlin { jvm(); linuxX64() }
publishing { repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } } }
EOF
  printf 'package com.example.acme.text\nfun normalize(s: String) = s\n' > "$d/src/commonMain/kotlin/com/example/acme/text/Normalizer.kt"
  printf '%s\n' "$SKILL" > "$d/src/skills/acme-text/SKILL.md"
  (cd "$d" && "$GRADLE" -q --no-configuration-cache publishAllPublicationsToLocalRepository) > "$d/log" 2>&1
  say "--- Kotlin Multiplatform, skill at src/skills/acme-text/SKILL.md, no build change · exit $?"
  for a in $(find "$d/build/repo" -name '*-sources.jar' | sort); do
    say "    $(printf '%-46s' "$(basename "$a")") $(unzip -l "$a" | grep -oE '[^ ]*SKILL\.md' | head -1 || echo ABSENT)"
  done
}

# ---------------------------------------------------------------- Maven
maven_case() { # label, extra <build> xml, skill dir
  local d="$WORK/$3"; rm -rf "$d"; mkdir -p "$d/src/main/java/com/example/acme/text" "$d/$4"
  cat > "$d/pom.xml" <<POM
<project xmlns="http://maven.apache.org/POM/4.0.0"><modelVersion>4.0.0</modelVersion>
<groupId>com.example.acme</groupId><artifactId>acme-text</artifactId><version>0.1.0</version>
<properties><maven.compiler.release>21</maven.compiler.release></properties>
<build>
  $2
  <plugins><plugin><groupId>org.apache.maven.plugins</groupId><artifactId>maven-source-plugin</artifactId><version>3.4.0</version>
  <executions><execution><id>s</id><goals><goal>jar-no-fork</goal></goals></execution></executions></plugin></plugins>
</build></project>
POM
  printf 'package com.example.acme.text;\npublic final class Normalizer {}\n' > "$d/src/main/java/com/example/acme/text/Normalizer.java"
  printf '%s\n' "$SKILL" > "$d/$4/SKILL.md"
  (cd "$d" && mvn -q -B package) > "$d/log" 2>&1 || say "    build failed"
  where "$1" "$d/target" '*-sources.jar' 'acme-text-0.1.0.jar'
}

kmp_case
say "--- Maven"
maven_case "src/skills, no build change" "" mvn-plain "src/skills/acme-text"
maven_case "src/skills declared as a resource directory" '<resources><resource><directory>src/skills</directory><targetPath>skills</targetPath></resource></resources>' mvn-res "src/skills/acme-text"
echo "work: $WORK"
