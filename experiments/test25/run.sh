#!/bin/sh
# Fetches the runtime from Maven Central into ./lib on first use, then runs a phase.
set -e
HERE=$(cd "$(dirname "$0")" && pwd)
LIB="$HERE/lib"
LLAMA_VERSION=4.2.0
mkdir -p "$LIB"
[ -f "$LIB/llama-$LLAMA_VERSION.jar" ] || curl -sSLo "$LIB/llama-$LLAMA_VERSION.jar" \
  "https://repo1.maven.org/maven2/de/kherud/llama/$LLAMA_VERSION/llama-$LLAMA_VERSION.jar"
[ -f "$LIB/annotations-24.0.1.jar" ] || curl -sSLo "$LIB/annotations-24.0.1.jar" \
  "https://repo1.maven.org/maven2/org/jetbrains/annotations/24.0.1/annotations-24.0.1.jar"
CP="$LIB/llama-$LLAMA_VERSION.jar:$LIB/annotations-24.0.1.jar"

case "${1:-a}" in
  jgen) JCP=$(find "$HERE/lib-jlama" -name '*.jar' | tr '\n' ':')
     JAVA_BIN="${JLAMA_JAVA:-java}"
     JAVAC_BIN="$(dirname "$JAVA_BIN")/javac"
     "$JAVAC_BIN" --add-modules jdk.incubator.vector -cp "$JCP" -d "$HERE/classes-jlama" "$HERE/JlamaGenerate.java"
     shift
     # JLama is built and tested against JDK 21; JLAMA_JAVA overrides when a newer default JVM
     # crashes in its native kernels.
     exec "$JAVA_BIN" --add-modules jdk.incubator.vector \
       --enable-native-access=ALL-UNNAMED \
       -cp "$JCP$HERE/classes-jlama" JlamaGenerate "$@" ;;
  gen) javac -cp "$CP" -d "$HERE/classes" "$HERE/Generate.java"
     shift
     exec java --enable-native-access=ALL-UNNAMED -cp "$CP:$HERE/classes" Generate "$@" ;;
  a) javac -cp "$CP" -d "$HERE/classes" "$HERE/Probe.java"
     shift || true
     exec java --enable-native-access=ALL-UNNAMED -cp "$CP:$HERE/classes" Probe "$@" ;;
  *) echo "unknown phase: $1" >&2; exit 2 ;;
esac
