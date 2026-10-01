#!/usr/bin/env bash
# A Kotlin/JS resource arrived in the published npm package at META-INF/skills/acme-text.md, which
# reads as a convention and is not one: META-INF was simply the directory the earlier harness put
# inside src/commonMain/resources. If the resources root is copied verbatim, then the path under
# resources is the path in the package — and a KMP library can place a resource at npm's own
# convention path, skills/<name>/SKILL.md, and have it arrive there.
#
# Four placements, one library, one build. Each carries its own sentinel.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/npm-resource-root.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }

d="$WORK/lib"; rm -rf "$d"
R="$d/src/commonMain/resources"
mkdir -p "$d/src/commonMain/kotlin/com/example/acme/text" \
         "$R/META-INF/skills" "$R/skills/acme-text" "$R/com/example/acme/text"

printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
cat > "$d/build.gradle.kts" <<'EOF'
plugins { kotlin("multiplatform") version "2.4.20"; `maven-publish` }
group = "com.example.acme"; version = "0.1.0"
repositories { mavenCentral() }
kotlin { js { browser(); binaries.library(); generateTypeScriptDefinitions() } }
publishing { repositories { maven { name = "local"; url = uri(layout.buildDirectory.dir("repo")) } } }
EOF
printf 'package com.example.acme.text\n\nfun normalize(s: String) = s\n' > "$d/src/commonMain/kotlin/com/example/acme/text/Normalizer.kt"

printf 'METAINF-SENTINEL\n'    > "$R/META-INF/skills/acme-text.md"
printf 'NPMSHAPE-SENTINEL\n'   > "$R/skills/acme-text/SKILL.md"
printf 'PKGROOT-SENTINEL\n'    > "$R/SKILL.md"
printf 'NAMESPACE-SENTINEL\n'  > "$R/com/example/acme/text/SKILL.md"

say "Gradle $("$GRADLE" --version 2>/dev/null | grep '^Gradle' | cut -d' ' -f2) · Kotlin 2.4.20"
say "Four files under src/commonMain/resources: META-INF/skills/, skills/acme-text/, the root, and the namespace path."
say ""

(cd "$d" && "$GRADLE" -q --no-configuration-cache jsBrowserProductionLibraryDistribution publishAllPublicationsToLocalRepository) > "$d/log" 2>&1
say "--- build exit $?"

report() { # label, directory
  local x found
  say "  $1"
  for x in METAINF NPMSHAPE PKGROOT NAMESPACE; do
    found="$(grep -rl "$x-SENTINEL" "$2" 2>/dev/null | sed "s#^$2/##" | sort | tr '\n' ' ')"
    say "    $(printf '%-10s' "$x") ${found:-ABSENT}"
  done
}

dist="$d/build/dist/js/productionLibrary"
[ -d "$dist" ] && report "the npm package a JavaScript consumer installs" "$dist"
[ -d "$dist" ] && say "    package.json files field: $(grep -o '\"files\"[^]]*]' "$dist/package.json" 2>/dev/null || echo 'none — npm would publish the whole directory')"
say ""
for a in $(find "$d/build/repo" -type f \( -name '*.klib' -o -name '*-sources.jar' \) 2>/dev/null | sort); do
  t="$(mktemp -d)"; unzip -qo "$a" -d "$t" 2>/dev/null; report "$(basename "$a")" "$t"; rm -rf "$t"
done
echo "work: $WORK"
