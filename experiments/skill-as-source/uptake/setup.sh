#!/usr/bin/env bash
# Builds everything one fixture of test 5 needs, once: publishes the fixture library,
# generates the pointer skill from its sources jar with the lightweight codex, and stages a
# consumer template and one overlay per arm under $WORK. Runs no agent.
#
# Layout, as a real consumer has it: the build resolves the library's BINARY jar from a
# repository outside the project, and the sources jar sits in a Gradle-cache-shaped
# directory elsewhere again. Nothing about the library is in the project tree. The first
# smoke runs put the whole repository inside the project, and an agent found the skill
# with one `find`.
#
# Usage: FIXTURE=<outcome|lookup|arrow|arrow-md> WORK=<dir> ./setup.sh
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO_ROOT="$(cd "$HERE/../../.." && pwd)"
WORK="${WORK:?set WORK to a directory for the builds and runs of this fixture}"
FIXTURE="${FIXTURE:?set FIXTURE to outcome, lookup, arrow or arrow-md}"
GRADLE="${GRADLE:-gradle}"
F="$HERE/fixtures/$FIXTURE"
mkdir -p "$WORK"
cp "$F/fixture.json" "$F/task.md" "$WORK/"
PACKAGE="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["package"])' "$F/fixture.json")"
ARTIFACT="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["artifact"])' "$F/fixture.json")"

EXTERNAL="$(python3 -c 'import json,sys; e=json.load(open(sys.argv[1])).get("external"); print(":".join([e["group"],e["artifact"],e["version"]]) if e else "")' "$F/fixture.json")"
rm -rf "$WORK/gradle-home" "$WORK/repo"
if [ -z "$EXTERNAL" ]; then
  # 1. The library, published with its skill-info in the sources jar.
  rm -rf "$WORK/library" && cp -R "$F/library" "$WORK/library"
  (cd "$WORK/library" && "$GRADLE" -q --no-configuration-cache publishAllPublicationsToLocalRepository)
  PUBLISHED="$WORK/library/build/repo"
  SOURCES="$PUBLISHED/com/example/acme/$ARTIFACT/0.1.0/$ARTIFACT-0.1.0-sources.jar"
  unzip -l "$SOURCES" | grep -q 'skill-info.kt' || { echo "skill-info.kt missing from the sources jar"; exit 1; }

  # 2. The binary repository the consumer resolves from: everything but the sources jar.
  cp -R "$PUBLISHED" "$WORK/repo"
  find "$WORK/repo" -name '*-sources.jar*' -delete
  CACHE="$WORK/gradle-home/caches/modules-2/files-2.1/com.example.acme/$ARTIFACT/0.1.0/0"
else
  # 1-2. A real library the consumer resolves from Maven Central, unchanged. Its published
  #      sources jar gains the fixture's skill-info.kt in the package it documents — what the
  #      library would ship if its authors adopted the convention. Only the pointer, hook and
  #      lint arms ever see that jar; the consumer's own build never does.
  IFS=: read -r GROUP EART VERSION <<< "$EXTERNAL"
  mkdir -p "$WORK/external"
  SOURCES="$WORK/external/$EART-$VERSION-sources.jar"
  curl -sfL "https://repo1.maven.org/maven2/${GROUP//.//}/$EART/$VERSION/$EART-$VERSION-sources.jar" -o "$SOURCES"
  PACKAGE_DIR="commonMain/${PACKAGE//.//}"
  unzip -l "$SOURCES" | grep -q " $PACKAGE_DIR/" || PACKAGE_DIR="${PACKAGE//.//}"
  SKILL_NAME="$(cd "$F/skill" && ls | head -1)"   # skill-info.kt, or SKILL.md — see RAD-0075
  STAGE="$(mktemp -d)" && mkdir -p "$STAGE/$PACKAGE_DIR" && cp "$F/skill/$SKILL_NAME" "$STAGE/$PACKAGE_DIR/"
  (cd "$STAGE" && zip -q "$SOURCES" "$PACKAGE_DIR/$SKILL_NAME") && rm -rf "$STAGE"
  unzip -l "$SOURCES" | grep -q "$PACKAGE_DIR/$SKILL_NAME" || { echo "$SKILL_NAME not added"; exit 1; }
  CACHE="$WORK/gradle-home/caches/modules-2/files-2.1/$GROUP/$EART/$VERSION/0"
fi

# 3. The sources jar where a Gradle cache would hold it, and the pointer generated from it.
mkdir -p "$CACHE" && cp "$SOURCES" "$CACHE/"
rm -rf "$WORK/codex" "$WORK/pointer"
GRADLE_USER_HOME="$WORK/gradle-home" MINICODEX="$WORK/codex" python3 "$REPO_ROOT/experiments/minimal-codex/pkgindex.py" build >/dev/null
GRADLE_USER_HOME="$WORK/gradle-home" MINICODEX="$WORK/codex" python3 "$REPO_ROOT/experiments/minimal-codex/pkgindex.py" pointer "$WORK/pointer"
REFERENCE="$WORK/pointer/dependency-skills/references/$PACKAGE.md"
[ -f "$REFERENCE" ] || { echo "pointer has no reference for $PACKAGE"; exit 1; }
if command -v uvx >/dev/null; then uvx --from skills-ref agentskills validate "$WORK/pointer/dependency-skills"; fi

# 4. The consumer template every run starts from.
rm -rf "$WORK/template" && cp -R "$F/consumer" "$WORK/template"
python3 - "$WORK/template/build.gradle.kts" "$WORK/repo" <<'EOF'
import sys; p = sys.argv[1]; text = open(p).read(); open(p, "w").write(text.replace("@REPO@", sys.argv[2]))
EOF
cp "$REPO_ROOT/implementations/codex/gradlew" "$WORK/template/"
mkdir -p "$WORK/template/gradle" && cp -R "$REPO_ROOT/implementations/codex/gradle/wrapper" "$WORK/template/gradle/"
(cd "$WORK/template" && ./gradlew -q --no-configuration-cache compileKotlin)
rm -rf "$WORK/template/.gradle" "$WORK/template/build" "$WORK/template/.kotlin"
if find "$WORK/template" -name '*sources.jar' | grep -q .; then echo "a sources jar leaked into the template"; exit 1; fi

# 5. The arm overlays. Every arm that carries the skill carries the same file, byte for byte.
A="$WORK/arms"; rm -rf "$A"; mkdir -p "$A/none"
mkdir -p "$A/pointer/.claude/skills" "$A/pointer/.agents/skills"
cp -R "$WORK/pointer/dependency-skills" "$A/pointer/.claude/skills/"
cp -R "$WORK/pointer/dependency-skills" "$A/pointer/.agents/skills/"
cp -R "$A/pointer" "$A/instructions" && cp "$HERE"/arms/instructions/*.md "$A/instructions/"
cp -R "$HERE/arms/hook" "$A/hook" && mkdir -p "$A/hook/.claude/hooks/skills" && cp "$REFERENCE" "$A/hook/.claude/hooks/skills/"
mkdir -p "$A/lint/.dependency-skills" "$A/lint/gradle" && cp "$REFERENCE" "$A/lint/.dependency-skills/"
python3 - "$HERE/arms/lint-template/skill-lint.gradle.kts" "$A/lint/gradle/skill-lint.gradle.kts" "$WORK/fixture.json" <<'EOF'
import json, sys
f = json.load(open(sys.argv[3]))
text = open(sys.argv[1]).read().replace("@MISUSE@", f["misuse"]).replace("@LINT@", f["lint"]).replace("@PACKAGE@", f["package"])
open(sys.argv[2], "w").write(text)
EOF
mkdir -p "$A/lintpost" && cp "$REFERENCE" "$A/lintpost/" && cp "$HERE/arms/lintpost-template/skill-lint.init.gradle.kts" "$A/lintpost/"
python3 - "$A/lintpost/skill-lint.init.gradle.kts" "$WORK/fixture.json" <<'EOF2'
import json, sys
f = json.load(open(sys.argv[2])); p = sys.argv[1]
text = open(p).read().replace("@MISUSE@", f["misuse"]).replace("@LINT@", f["lint"])
open(p, "w").write(text)
EOF2
echo "staged $FIXTURE: template, and arms none, pointer, instructions, hook, lint, lintpost"
