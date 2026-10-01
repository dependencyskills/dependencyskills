#!/usr/bin/env bash
# RAD-0073 test 3: can a harvester find a skill-info file by filename alone, where a
# consumer's package manager actually puts the dependency, and recover the skill
# text without a parser?
#
# Consumes the artifacts ../survival/run.sh and ../kmp/ published: set SURVIVAL to that
# run's WORK directory. Each consumer resolves the fixture the way its ecosystem does,
# into caches isolated under this run's WORK, and a filename-only search then looks
# for skill-info.* there. Extraction strips comment markers line by line and compares
# the result with the text that was written.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
SURVIVAL="${SURVIVAL:?set SURVIVAL to the WORK directory of a ../survival/run.sh run}"
KMP_REPO="$HERE/../kmp/build/repo"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/results.txt"
: > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }

EXPECTED='Skill: acme-text.

Normalize user input with normalize before comparing; never
hand-roll a case fold.

Wrong:   a.lowercase() == b.lowercase()
Correct: normalize(a) == normalize(b)'

# Comment-marker stripping, no parser: one rule set for every language.
extract() {
  python3 - "$1" <<'EOF'
import re, sys
out = []
for line in open(sys.argv[1], encoding="utf-8").read().splitlines():
    s = line.strip()
    if re.fullmatch(r'(package\s+[\w.]+;?|export\s*\{\s*\};?|"""|/\*\*|\*/|\*?\s*@packageDocumentation)', s):
        continue
    s = re.sub(r'^(///?!?|//|\*)\s?', '', s)
    out.append(s)
text = "\n".join(out).strip("\n")
# Normalizer/Kotlin fixtures name the function Normalizer.normalize; compare on the shared text.
print(text.replace("Normalizer.normalize", "normalize"))
EOF
}

# found <label> <dir> : filename search, then extraction check
found() {
  local label="$1" dir="$2" hits
  hits=$(find "$dir" -type f -name 'skill-info.*' 2>/dev/null | sort)
  if [ -z "$hits" ]; then say "    $(printf '%-40s' "$label") found: none"; return; fi
  while read -r h; do
    local rel="${h#"$dir"/}" ok
    local got; got="$(extract "$h")"
    if [ "$got" = "$EXPECTED" ]; then ok="text recovered exactly"
    elif ! grep -q 'hand-roll' "$h"; then ok="file present, carries no skill text"
    else ok="TEXT DIFFERS"; fi
    say "    $(printf '%-40s' "$label") found: $rel · $ok"
  done <<< "$hits"
}

# found_in_jars <label> <dir> : the same, for sources jars a JVM cache holds
found_in_jars() {
  local label="$1" dir="$2" x
  find "$dir" -type f -name '*-sources.jar' | sort | while read -r j; do
    x="$(mktemp -d)"; unzip -qo "$j" -d "$x"
    found "$label $(basename "$j")" "$x"
  done
}

# ---------------------------------------------------------------- Gradle consumer, the KMP library
gradle_consumer() {
  local d="$WORK/gradle-consumer" port=18473; mkdir -p "$d"
  # Served over HTTP, because Gradle reads a file:// repository in place and never copies it into its cache.
  (cd "$KMP_REPO" && exec python3 -m http.server "$port" --bind 127.0.0.1) > "$d/http.log" 2>&1 &
  local server=$!; sleep 1
  printf 'rootProject.name = "consumer"\n' > "$d/settings.gradle.kts"
  cat > "$d/build.gradle.kts" <<EOF
plugins { java }
repositories { maven { url = uri("http://127.0.0.1:$port/"); isAllowInsecureProtocol = true } }
dependencies { implementation("com.example.acme:acme-text:0.1.0") }
// What an IDE or the codex plugin does to get sources: resolve the sources artifact of each component.
tasks.register("fetchSources") {
  doLast {
    val ids = configurations.runtimeClasspath.get().incoming.resolutionResult.allComponents.map { it.id }
    val result = dependencies.createArtifactResolutionQuery()
      .forComponents(ids).withArtifacts(JvmLibrary::class.java, SourcesArtifact::class.java).execute()
    result.resolvedComponents.forEach { c ->
      c.getArtifacts(SourcesArtifact::class.java).forEach { a -> println("resolved " + a) }
    }
  }
}
EOF
  (cd "$d" && GRADLE_USER_HOME="$WORK/gradle-home" "$GRADLE" -q --no-configuration-cache fetchSources) > "$d/log" 2>&1
  local rc=$?; kill "$server" 2>/dev/null
  say "--- Gradle consumer of the KMP library over HTTP, ArtifactResolutionQuery for sources, isolated GRADLE_USER_HOME · exit $rc"
  sed 's/^/    /' "$d/log" | grep resolved | sed 's/ (.*//' | tee -a "$LOG"
  found_in_jars "gradle cache" "$WORK/gradle-home/caches/modules-2/files-2.1/com.example.acme"
}

# ---------------------------------------------------------------- Maven consumer
maven_consumer() {
  local repo="$WORK/m2-remote"; mkdir -p "$repo"
  local src="$SURVIVAL/maven-kotlin-extensions"
  (cd "$src" && mvn -q -B deploy -DaltDeploymentRepository=local::file://"$repo" -Dmaven.repo.local="$WORK/m2") > "$WORK/maven-deploy.log" 2>&1
  (mvn -q -B dependency:get -Dartifact=com.example.acme:acme-text:0.1.0:jar:sources \
     -DremoteRepositories=local::::file://"$repo" -Dmaven.repo.local="$WORK/m2-consumer") > "$WORK/maven-consumer.log" 2>&1
  say "--- Maven consumer, dependency:get of the sources classifier, isolated local repository · exit $?"
  found_in_jars "~/.m2 equivalent" "$WORK/m2-consumer/com/example/acme"
}

# ---------------------------------------------------------------- npm consumer
npm_consumer() {
  local d="$WORK/npm-consumer"; mkdir -p "$d"
  printf '{ "name": "consumer", "version": "1.0.0", "private": true }\n' > "$d/package.json"
  local tgz; tgz=$(ls "$SURVIVAL"/npm/*.tgz)
  (cd "$d" && npm install --silent "$tgz") > "$d/log" 2>&1
  say "--- npm consumer, npm install of the files:[dist] tarball · exit $?"
  found "node_modules" "$d/node_modules/@example/acme-text"
}

# ---------------------------------------------------------------- Python consumer
python_consumer() {
  local d="$WORK/py-consumer"; mkdir -p "$d"
  (cd "$d" && uv venv --quiet venv && uv pip install --quiet --python venv/bin/python "$SURVIVAL"/python-uv_build/dist/*.whl) > "$d/log" 2>&1
  say "--- Python consumer, uv pip install of the uv_build wheel · exit $?"
  found "site-packages" "$(ls -d "$d"/venv/lib/python*/site-packages/acme_text)"
}

# ---------------------------------------------------------------- Go consumer through a file-based module proxy
go_consumer() {
  local d="$WORK/go-consumer" proxy="$WORK/goproxy/example.com/acmetext/@v"; mkdir -p "$d" "$proxy"
  cp "$SURVIVAL/go/acmetext-v0.1.0.zip" "$proxy/v0.1.0.zip"
  cp "$SURVIVAL/go/mod/go.mod" "$proxy/v0.1.0.mod"
  printf '{"Version":"v0.1.0"}\n' > "$proxy/v0.1.0.info"; printf 'v0.1.0\n' > "$proxy/list"
  printf 'module example.com/consumer\n\ngo 1.27\n' > "$d/go.mod"
  printf 'package main\n\nimport "example.com/acmetext/acmetext"\n\nfunc main() { _ = acmetext.Normalize("x") }\n' > "$d/main.go"
  (cd "$d" && GOPROXY="file://$WORK/goproxy" GONOSUMDB='*' GONOSUMCHECK=1 GOFLAGS=-mod=mod GOSUMDB=off GOMODCACHE="$WORK/gomodcache" go get example.com/acmetext@v0.1.0 && GOPROXY=off GOSUMDB=off GOMODCACHE="$WORK/gomodcache" go build ./...) > "$d/log" 2>&1
  say "--- Go consumer, go get through a file:// proxy, isolated GOMODCACHE · exit $?"
  found "module cache" "$WORK/gomodcache/example.com"
  chmod -R u+w "$WORK/gomodcache" 2>/dev/null
}

# ---------------------------------------------------------------- Swift consumer through a git URL
swift_consumer() {
  local lib="$SURVIVAL/swift" d="$WORK/swift-consumer"; mkdir -p "$d/Sources/Consumer"
  (cd "$lib" && git tag -f 0.1.0 >/dev/null)
  cat > "$d/Package.swift" <<EOF
// swift-tools-version:6.0
import PackageDescription
let package = Package(name: "Consumer",
  dependencies: [.package(url: "file://$lib", from: "0.1.0")],
  targets: [.executableTarget(name: "Consumer", dependencies: [.product(name: "AcmeText", package: "swift")])])
EOF
  printf 'import AcmeText\nprint(normalize("x"))\n' > "$d/Sources/Consumer/main.swift"
  (cd "$d" && swift build) > "$d/log" 2>&1
  say "--- Swift consumer, git URL dependency · exit $?"
  found ".build/checkouts" "$d/.build/checkouts"
}

say "gradle: $("$GRADLE" --version 2>/dev/null | grep '^Gradle') · maven: $(mvn -v 2>/dev/null | head -1 | cut -d' ' -f3)"
say "npm $(npm --version) · $(uv --version) · $(go version | cut -d' ' -f3) · $(swift --version 2>&1 | head -1 | grep -o 'Swift version [0-9.]*')"
say "Rust not run: a registry download is the .crate extracted into ~/.cargo/registry/src, and ../survival showed the .crate carries the file."
say ""
gradle_consumer
maven_consumer
npm_consumer
python_consumer
go_consumer
swift_consumer
echo "work: $WORK"
