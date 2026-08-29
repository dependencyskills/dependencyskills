#!/usr/bin/env bash
# Builds libdscodex for one platform, with llama.cpp linked STATICALLY inside it.
#
# Static on purpose: a shared llama.cpp means shipping libllama plus five libggml-* backends per
# platform and getting their load order and rpaths right on three operating systems. One
# self-contained library per platform is the thing a jar can carry and a classloader can extract.
#
#   ./build.sh macos-aarch64        # host
#   ./build.sh macos-x86_64         # cross, via -arch
#   ./build.sh linux-aarch64        # cross, via docker
#   ./build.sh linux-x86_64         # cross, via docker
#   ./build.sh windows-x86_64       # cross, via mingw-w64
#
# LLAMA_CPP points at a checkout; it is not vendored here because it is 200 MB of upstream and
# this repository has no business holding a copy.
set -euo pipefail

HERE="$(cd "$(dirname "$0")" && pwd)"
LLAMA_CPP="${LLAMA_CPP:-$HOME/Workspace/llama.cpp}"
PLATFORM="${1:?usage: build.sh <platform>}"
SHARED_OUT="$HERE/../src/jvmMain/resources/dscodex/$PLATFORM"   # FFM dlopens this
STATIC_OUT="$HERE/../src/nativeMain/cinterop/libs/$PLATFORM"    # cinterop links this in
OUT="$SHARED_OUT"
LOGS="$HERE/build/$PLATFORM"       # not committed: cmake noise

[ -f "$LLAMA_CPP/include/llama.h" ] || { echo "no llama.cpp at $LLAMA_CPP" >&2; exit 1; }
mkdir -p "$SHARED_OUT" "$STATIC_OUT" "$LOGS"

# GGML_NATIVE=OFF everywhere, not only when cross-compiling. On it, ggml tunes to the BUILD
# machine's CPU - it picked `apple-m4` here and applied it to an x86_64 target - and a library
# shipped in a jar must run on the oldest CPU of its architecture, not the newest one we own.

# The static llama.cpp build, one tree per platform so they do not overwrite each other.
build_llama() {
  local tag="$1"; shift
  local dir="$LLAMA_CPP/build-static-$tag"
  if [ ! -f "$dir/src/libllama.a" ]; then
    cmake -B "$dir" -S "$LLAMA_CPP" \
      -DBUILD_SHARED_LIBS=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
      -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_CURL=OFF \
      -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DGGML_NATIVE=OFF "$@" > "$LOGS/cmake-configure.log" 2>&1
    cmake --build "$dir" --target llama -j 8 > "$LOGS/cmake-build.log" 2>&1
  fi
  echo "$dir"
}

# Every static archive llama.cpp produces - llama plus the ggml backends it split into.
archives() { find "$1" -name '*.a' | sort; }

case "$PLATFORM" in
  macos-aarch64|macos-x86_64)
    ARCH="${PLATFORM#macos-}"
    [ "$ARCH" = "aarch64" ] && ARCH=arm64
    # Metal off: it needs a .metallib shipped beside the library, and the summariser runs on CPU
    # by default anyway. Accelerate is a system framework and always present, so BLAS stays.
    DIR=$(build_llama "$PLATFORM" -DCMAKE_OSX_ARCHITECTURES="$ARCH" -DGGML_METAL=OFF)
    cc -O2 -shared -fPIC -arch "$ARCH" \
      -I "$LLAMA_CPP/include" -I "$LLAMA_CPP/ggml/include" \
      -o "$SHARED_OUT/libdscodex.dylib" "$HERE/dscodex_llama.c" \
      $(archives "$DIR") -lc++ -framework Foundation -framework Accelerate
    # The static archive for the Kotlin/Native targets. cinterop links it INTO the consumer's
    # binary, where FFM dlopens the shared one - two linking models, two forms.
    cc -O2 -c -fPIC -arch "$ARCH" -I "$LLAMA_CPP/include" -I "$LLAMA_CPP/ggml/include" \
      -o "$LOGS/dscodex_llama.o" "$HERE/dscodex_llama.c"
    libtool -static -o "$STATIC_OUT/libdscodex.a" "$LOGS/dscodex_llama.o" $(archives "$DIR") 2>/dev/null
    ;;

  linux-aarch64|linux-x86_64)
    DOCKER_ARCH="${PLATFORM#linux-}"
    [ "$DOCKER_ARCH" = "x86_64" ] && DOCKER_ARCH=amd64
    [ "$DOCKER_ARCH" = "aarch64" ] && DOCKER_ARCH=arm64
    docker run --rm --platform "linux/$DOCKER_ARCH" \
      -v "$LLAMA_CPP:/llama" -v "$HERE:/shim" \
      -v "$SHARED_OUT:/shared" -v "$STATIC_OUT:/static" -w /work \
      debian:bookworm-slim bash -c '
        set -e
        apt-get update -qq && apt-get install -y -qq build-essential cmake git > /dev/null
        cmake -B /work/b -S /llama -DBUILD_SHARED_LIBS=OFF -DLLAMA_BUILD_TESTS=OFF \
          -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_TOOLS=OFF \
          -DLLAMA_CURL=OFF -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DGGML_NATIVE=OFF > /dev/null
        cmake --build /work/b --target llama -j 8 > /dev/null
        cc -O2 -shared -fPIC -I /llama/include -I /llama/ggml/include \
           -o /shared/libdscodex.so /shim/dscodex_llama.c \
           -Wl,--start-group $(find /work/b -name "*.a" | sort) -Wl,--end-group -lstdc++ -lm
        cc -O2 -c -fPIC -I /llama/include -I /llama/ggml/include -o /tmp/shim.o /shim/dscodex_llama.c
        mkdir -p /tmp/ar && cd /tmp/ar && for a in $(find /work/b -name "*.a"); do ar x "$a"; done
        ar rcs /static/libdscodex.a /tmp/shim.o /tmp/ar/*.o
      '
    ;;

  windows-x86_64)
    command -v x86_64-w64-mingw32-gcc > /dev/null || { echo "mingw-w64 not installed" >&2; exit 1; }
    DIR=$(build_llama "$PLATFORM" \
      -DCMAKE_SYSTEM_NAME=Windows \
      -DCMAKE_C_COMPILER=x86_64-w64-mingw32-gcc \
      -DCMAKE_CXX_COMPILER=x86_64-w64-mingw32-g++ \
      -DCMAKE_RC_COMPILER=x86_64-w64-mingw32-windres)
    x86_64-w64-mingw32-gcc -O2 -shared \
      -I "$LLAMA_CPP/include" -I "$LLAMA_CPP/ggml/include" \
      -o "$SHARED_OUT/dscodex.dll" "$HERE/dscodex_llama.c" \
      -Wl,--start-group $(archives "$DIR") -Wl,--end-group \
      -lstdc++ -static-libgcc -static-libstdc++
    # And the archive for the mingwX64 Kotlin/Native target. Objects are extracted into a scratch
    # directory because `ar` merges archives only via their members, not archive-to-archive.
    x86_64-w64-mingw32-gcc -O2 -c -I "$LLAMA_CPP/include" -I "$LLAMA_CPP/ggml/include" \
      -o "$LOGS/dscodex_llama.o" "$HERE/dscodex_llama.c"
    rm -rf "$LOGS/ar" && mkdir -p "$LOGS/ar"
    ( cd "$LOGS/ar" && for a in $(archives "$DIR"); do x86_64-w64-mingw32-ar x "$a"; done )
    # mingw archives hold `.obj` members where gcc's hold `.o`, so the glob has to cover both or
    # it silently matches nothing and `ar rcs` builds an archive of one object.
    x86_64-w64-mingw32-ar rcs "$STATIC_OUT/libdscodex.a" "$LOGS/dscodex_llama.o" "$LOGS"/ar/*.o*
    ;;

  *) echo "unknown platform: $PLATFORM" >&2; exit 2 ;;
esac

ls -la "$OUT"/*.dylib "$OUT"/*.so "$OUT"/*.dll 2>/dev/null || true
