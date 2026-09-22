#!/usr/bin/env bash
# An XCFramework is the one consumer route measured to carry no skill at all: not the source file,
# not the resource. It is also the route a Swift consumer actually gets, through SPM or CocoaPods.
# So before writing it off, try every mechanism that could put text inside one, and find out what
# the bundle can carry.
#
# Eight routes, each with its own sentinel so the result says which one landed:
#
#   SOURCEFILE   a SKILL.md beside the Kotlin in commonMain and iosArm64Main
#   RESOURCE     a file under src/commonMain/resources
#   NATIVERES    the same under src/iosMain/resources
#   RAWSTRING    an exported `val skill = """..."""`, reachable through the Objective-C export
#   HIDDENSTR    the same constant, internal, so nothing exports it
#   KDOC         a doc comment on an exported declaration, with -Xexport-kdoc
#   BUNDLECOPY   a SKILL.md written into the .framework bundle
#   EXTRAHEADER  a second header written into the bundle's Headers directory
#   PLIST        a key appended to the bundle's Info.plist
#
# The last three are written from a doLast on the link task, which is where a build plugin would
# do it — after the linker has generated the binary, the umbrella header, the module map and the
# Info.plist, and before the XCFramework is assembled from the bundles. Staging the same writes
# from a shell between two Gradle invocations does not measure the same thing: changing the link
# task's output directory makes it out of date, so the next invocation relinks and regenerates
# every file it owns, silently discarding an edit to the Info.plist while leaving foreign files
# alone.
#
# Kotlin/Native holds string literals as UTF-16, so `strings` does not see them and the binary is
# searched byte by byte instead.
#
# A second library, identical but for the compiler flag, answers whether the header route needs a
# build change at all.
set -u
HERE="$(cd "$(dirname "$0")" && pwd)"
WORK="${WORK:-$(mktemp -d)}"
GRADLE="${GRADLE:-gradle}"
LOG="$HERE/xcframework-routes.txt"; : > "$LOG"
say() { printf '%s\n' "$*" | tee -a "$LOG"; }

library() { # dir, extra kotlin{} lines
  local d="$1"; rm -rf "$d"
  mkdir -p "$d/src/commonMain/kotlin/com/example/acme/text" "$d/src/iosMain/kotlin/com/example/acme/text" \
           "$d/src/iosArm64Main/kotlin/com/example/acme/text" \
           "$d/src/commonMain/resources/skills" "$d/src/iosMain/resources/skills"
  printf 'rootProject.name = "acme-text"\n' > "$d/settings.gradle.kts"
  cat > "$d/build.gradle.kts" <<EOF
import org.jetbrains.kotlin.gradle.plugin.mpp.apple.XCFramework

plugins { kotlin("multiplatform") version "2.4.20" }
group = "com.example.acme"; version = "0.1.0"
repositories { mavenCentral() }

val xcf = XCFramework("AcmeText")

kotlin {
    iosArm64 { binaries.framework { baseName = "AcmeText"; xcf.add(this) } }
    iosSimulatorArm64 { binaries.framework { baseName = "AcmeText"; xcf.add(this) } }
$2
}

// What a build plugin would do: write into the bundle once the linker has finished with it.
// The link task registers the directory that HOLDS the bundle, not the bundle, so look inside it.
tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinNativeLink>().configureEach {
    doLast {
        val bundles = outputs.files.files.flatMap { f ->
            if (f.name.endsWith(".framework")) listOf(f)
            else f.listFiles().orEmpty().filter { it.name.endsWith(".framework") && it.isDirectory }
        }
        logger.lifecycle("skill plugin hook: \${bundles.size} bundle(s) in \${outputs.files.files.joinToString { it.name }}")
        bundles.forEach { fw ->
            File(fw, "SKILL.md").writeText(
                "# Skill: acme-text\n\nBUNDLECOPY-SENTINEL. Never hand-roll a case fold; call Normalizer.normalize.\n")
            File(fw, "Headers/AcmeTextSkill.h").writeText(
                "// EXTRAHEADER-SENTINEL. Never hand-roll a case fold; call Normalizer.normalize.\n")
            val plist = File(fw, "Info.plist")
            plist.writeText(plist.readText().replace("</dict>",
                "\t<key>AcmeSkill</key>\n\t<string>PLIST-SENTINEL never hand-roll a case fold</string>\n</dict>"))
        }
    }
}
EOF
  cat > "$d/src/commonMain/kotlin/com/example/acme/text/Normalizer.kt" <<'EOF'
package com.example.acme.text

/**
 * Text normalization for acme.
 *
 * KDOC-SENTINEL. Never hand-roll a case fold; call [normalize], which is locale-independent.
 */
object Normalizer {
    fun normalize(s: String): String = s
}
EOF
  cat > "$d/src/commonMain/kotlin/com/example/acme/text/skill.kt" <<'EOF'
package com.example.acme.text

val skill: String = """
    # Skill: acme-text

    RAWSTRING-SENTINEL. Never hand-roll a case fold; call Normalizer.normalize.
""".trimIndent()

internal val hiddenSkill: String = """
    # Skill: acme-text

    HIDDENSTR-SENTINEL. Never hand-roll a case fold; call Normalizer.normalize.
""".trimIndent()
EOF
  printf 'SOURCEFILE-SENTINEL (commonMain)\n' > "$d/src/commonMain/kotlin/com/example/acme/text/SKILL.md"
  printf 'SOURCEFILE-SENTINEL (iosArm64Main)\n' > "$d/src/iosArm64Main/kotlin/com/example/acme/text/SKILL.md"
  printf 'RESOURCE-SENTINEL\n' > "$d/src/commonMain/resources/skills/acme-text.md"
  printf 'NATIVERES-SENTINEL\n' > "$d/src/iosMain/resources/skills/acme-text.md"
}

scan() { # directory, prefix to strip
  local x found
  for x in SOURCEFILE RESOURCE NATIVERES RAWSTRING HIDDENSTR KDOC BUNDLECOPY EXTRAHEADER PLIST; do
    found="$(grep -rl "$x-SENTINEL" "$1" 2>/dev/null | sed "s#^$1/##" | sort | tr '\n' ' ')"
    say "    $(printf '%-12s' "$x") ${found:-ABSENT}"
  done
}

bytes() { # binary
  python3 - "$1" <<'PY'
import sys
b = open(sys.argv[1], 'rb').read()
for s in ("SOURCEFILE", "RESOURCE", "NATIVERES", "RAWSTRING", "HIDDENSTR", "KDOC", "BUNDLECOPY"):
    a, u = b.count(s.encode()), b.count(s.encode('utf-16-le'))
    print(f"    {s:<12} ascii {a}, utf-16 {u}")
print(f"    binary is {len(b)} bytes")
PY
}

say "Gradle $("$GRADLE" --version 2>/dev/null | grep '^Gradle' | cut -d' ' -f2) · Kotlin 2.4.20 · $(xcodebuild -version 2>/dev/null | head -1) · $(swift --version 2>&1 | grep -o 'Swift version [0-9.]*')"
say ""

# ---------------------------------------------------------------- the library, with -Xexport-kdoc
d="$WORK/lib"
library "$d" '    compilerOptions { freeCompilerArgs.add("-Xexport-kdoc") }'
say "--- link the release frameworks"
(cd "$d" && "$GRADLE" --no-configuration-cache linkReleaseFrameworkIosArm64 linkReleaseFrameworkIosSimulatorArm64) > "$d/link.log" 2>&1
say "    exit $?"
fw="$d/build/bin/iosArm64/releaseFramework/AcmeText.framework"
if [ ! -d "$fw" ]; then
  say "    no framework linked"; grep -iE 'what went wrong' -A4 "$d/link.log" | head -8 | sed 's/^/    /' | tee -a "$LOG" >/dev/null
  echo "work: $WORK"; exit 1
fi
grep -m2 'skill plugin hook' "$d/link.log" | sed 's/^/    /' | tee -a "$LOG" >/dev/null
say "    the bundle as it leaves the linker and its plugin hook:"
say "        $(cd "$fw" && find . | sed 's#^\./##' | grep -v '^$' | sort | tr '\n' ' ')"
say ""
say "--- what the linked .framework carries, by route"
scan "$fw"
say ""

# ---------------------------------------------------------------- assemble and measure again
say "--- assemble the XCFramework"
(cd "$d" && "$GRADLE" -q --no-configuration-cache assembleAcmeTextXCFramework) > "$d/xcf.log" 2>&1
say "    exit $?"
xcf="$(find "$d/build/XCFrameworks" -maxdepth 2 -name '*.xcframework' 2>/dev/null | head -1)"
if [ -z "$xcf" ]; then
  say "    no XCFramework produced"; grep -iE 'what went wrong|error:' "$d/xcf.log" | head -3 | sed 's/^/    /' | tee -a "$LOG" >/dev/null
  echo "work: $WORK"; exit 1
fi
say "    $(cd "$xcf" && find . -maxdepth 2 | sed 's#^\./##' | grep -v '^$' | sort | tr '\n' ' ')"
say "    one slice in full: $(cd "$xcf/ios-arm64/AcmeText.framework" && find . | sed 's#^\./##' | grep -v '^$' | sort | tr '\n' ' ')"
say ""
say "--- what the assembled XCFramework carries, by route"
scan "$xcf"
say ""
say "--- inside the ios-arm64 binary, which grep does not read: Kotlin/Native holds literals as UTF-16"
bytes "$xcf/ios-arm64/AcmeText.framework/AcmeText" | tee -a "$LOG"
say ""
say "--- the Info.plist key, as delivered"
/usr/libexec/PlistBuddy -c 'Print :AcmeSkill' "$xcf/ios-arm64/AcmeText.framework/Info.plist" 2>&1 | head -1 | sed 's/^/    /' | tee -a "$LOG" >/dev/null
say ""
say "--- the KDoc in the generated umbrella header"
grep -n -A4 'Text normalization for acme' "$xcf/ios-arm64/AcmeText.framework/Headers/AcmeText.h" | head -8 | sed 's/^/    /' | tee -a "$LOG" >/dev/null
say "    the raw-string constant is declared as:"
grep -n 'NSString \*skill' "$xcf/ios-arm64/AcmeText.framework/Headers/AcmeText.h" | head -2 | sed 's/^/        /' | tee -a "$LOG" >/dev/null
say ""
say "--- the module map, which decides whether the extra header is importable"
sed 's/^/    /' "$xcf/ios-arm64/AcmeText.framework/Modules/module.modulemap" | tee -a "$LOG" >/dev/null
say ""

# ---------------------------------------------------------------- is the compiler flag required?
say "--- the same library without -Xexport-kdoc"
d2="$WORK/nokdoc"; library "$d2" ""
(cd "$d2" && "$GRADLE" -q --no-configuration-cache linkReleaseFrameworkIosArm64) > "$d2/link.log" 2>&1
say "    exit $?"
h2="$d2/build/bin/iosArm64/releaseFramework/AcmeText.framework/Headers/AcmeText.h"
say "    KDOC in the header: $(grep -c 'KDOC-SENTINEL' "$h2" 2>/dev/null | tr -d ' ') occurrences, header is $(wc -l < "$h2" 2>/dev/null | tr -d ' ') lines"
echo "work: $WORK"
