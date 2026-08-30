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

# cinterop reads a header from its own source set, so there are two copies of the ABI. The build
# owns the second one rather than a person: a hand-maintained duplicate that drifts gives
# Kotlin/Native a contract the library no longer honours, and it compiles.
cp "$HERE/dscodex.h" "$HERE/../src/nativeMain/cinterop/dscodex.h"

# GGML_NATIVE=OFF everywhere, not only when cross-compiling. On it, ggml tunes to the BUILD
# machine's CPU - it picked `apple-m4` here and applied it to an x86_64 target - and a library
# shipped in a jar must run on the oldest CPU of its architecture, not the newest one we own.
#
# GGML_OPENMP=OFF for the same class of reason. It defaults ON, and whether it takes depends on
# the BUILD machine's compiler: Apple clang ships no OpenMP so macOS quietly built without it,
# while the Debian container's gcc has it and turned it on. The result was a linux libdscodex.so
# carrying 13 undefined omp_* symbols and no NEEDED entry to resolve them - a library that builds,
# ships, and fails at dlopen on a machine without libgomp. ggml uses its own threadpool instead,
# which is what a self-contained library in a jar has to do.

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
    # Cross-compiled with Kotlin/Native's OWN toolchain, not with a distribution's.
    #
    # This used to build in a debian container, which produced two artifacts that could never be
    # linked: the archive was compiled against that container's glibc and libstdc++, while
    # Kotlin/Native links with a sysroot it bundles itself - gcc 8.3.0 / glibc 2.19. The skew
    # showed up as undefined __libc_single_threaded and std::filesystem, and `linkDebugTestLinuxX64`
    # had never once passed. konan uses that bundled sysroot on EVERY host, Linux included, so no
    # runner and no base image fixes it. Building against the same sysroot the linker uses makes
    # the ABI match by construction rather than by choosing a distribution and hoping.
    #
    # It also lowers the floor for the shared library the JVM dlopens: glibc 2.19 rather than
    # whatever the build container happened to ship.
    case "$PLATFORM" in
      linux-x86_64)  TRIPLE=x86_64-unknown-linux-gnu ;;
      linux-aarch64) TRIPLE=aarch64-unknown-linux-gnu ;;
    esac
    KONAN_DEPS="${KONAN_DATA_DIR:-$HOME/.konan}/dependencies"
    # Newest of each, overridable. These appear only after Kotlin/Native has fetched them, so a
    # clean checkout must run a native compile first; say that rather than failing on a path.
    GCC_DIR="${DSC_GCC_DIR:-$(ls -d "$KONAN_DEPS/$TRIPLE-gcc-"*/ 2>/dev/null | sort -V | tail -1)}"
    LLVM_DIR="${DSC_LLVM_DIR:-$(ls -d "$KONAN_DEPS"/llvm-*/ 2>/dev/null | sort -V | tail -1)}"
    [ -n "$GCC_DIR" ] && [ -x "${LLVM_DIR}bin/clang" ] || {
      echo "no Kotlin/Native toolchain under $KONAN_DEPS for $TRIPLE." >&2
      echo "Run a native compile once (./gradlew :inference:compileKotlinLinuxX64) so it is fetched," >&2
      echo "or set DSC_GCC_DIR and DSC_LLVM_DIR." >&2
      exit 1
    }
    GCC_DIR="${GCC_DIR%/}"; LLVM_DIR="${LLVM_DIR%/}"
    SYSROOT="$GCC_DIR/$TRIPLE/sysroot"
    CROSS="--target=$TRIPLE --sysroot=$SYSROOT --gcc-toolchain=$GCC_DIR"
    CLANG="$LLVM_DIR/bin/clang"

    # CMake probes a toolchain by linking an executable, and nothing here can link a Linux
    # executable for it; probing as a static library skips that step. CMAKE_AR is deliberately
    # NOT set to llvm-ar: CMake drives it with a response file it does not expand, and every
    # archive comes out empty - which is why the objects are collected directly below.
    TC="$LOGS/konan-$PLATFORM.cmake"
    cat > "$TC" <<EOF
set(CMAKE_SYSTEM_NAME Linux)
set(CMAKE_C_COMPILER   $CLANG)
set(CMAKE_CXX_COMPILER $LLVM_DIR/bin/clang++)
set(CMAKE_C_COMPILER_TARGET   $TRIPLE)
set(CMAKE_CXX_COMPILER_TARGET $TRIPLE)
set(CMAKE_SYSROOT $SYSROOT)
set(CMAKE_C_FLAGS_INIT   "--gcc-toolchain=$GCC_DIR")
set(CMAKE_CXX_FLAGS_INIT "--gcc-toolchain=$GCC_DIR")
set(CMAKE_FIND_ROOT_PATH_MODE_PROGRAM NEVER)
set(CMAKE_FIND_ROOT_PATH_MODE_LIBRARY ONLY)
set(CMAKE_FIND_ROOT_PATH_MODE_INCLUDE ONLY)
set(CMAKE_TRY_COMPILE_TARGET_TYPE STATIC_LIBRARY)
EOF
    DIR="$LLAMA_CPP/build-static-$PLATFORM"
    cmake -B "$DIR" -S "$LLAMA_CPP" -DCMAKE_TOOLCHAIN_FILE="$TC" \
      -DBUILD_SHARED_LIBS=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
      -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_CURL=OFF \
      -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF \
      > "$LOGS/cmake-configure.log" 2>&1
    cmake --build "$DIR" --target llama -j 8 > "$LOGS/cmake-build.log" 2>&1

    # The objects, not the archives CMake wrote. CompilerId objects are CMake probing the
    # compiler and each defines main(); sweeping them in gives a duplicate main at final link.
    OBJS=$(find "$DIR" -name "*.o" -not -path "*CompilerId*" | sort)
    [ -n "$OBJS" ] || { echo "llama.cpp produced no objects; see $LOGS/cmake-build.log" >&2; exit 1; }

    $CLANG $CROSS -O2 -c -fPIC -I "$LLAMA_CPP/include" -I "$LLAMA_CPP/ggml/include" \
      -o "$LOGS/shim.o" "$HERE/dscodex_llama.c"
    rm -f "$STATIC_OUT/libdscodex.a"
    "$LLVM_DIR/bin/llvm-ar" qcs "$STATIC_OUT/libdscodex.a" "$LOGS/shim.o" $OBJS
    $CLANG $CROSS -fuse-ld="$LLVM_DIR/bin/ld.lld" -O2 -shared -fPIC \
      -I "$LLAMA_CPP/include" -I "$LLAMA_CPP/ggml/include" \
      -o "$SHARED_OUT/libdscodex.so" "$HERE/dscodex_llama.c" \
      -Wl,--start-group $OBJS -Wl,--end-group -lstdc++ -lstdc++fs -lm
    ;;

  windows-x86_64)
    # Cross-compiled in a container, with Debian mingw-w64 rather than whatever mingw is on PATH.
    #
    # The C runtime has to match the one Kotlin/Native links against, and that is not a detail
    # that can be left to the machine. konan bundles msys2 mingw gcc 9.2.0, whose libmsvcrt
    # defines `_fstat64`. Homebrew mingw is UCRT-era - 16.2.0 here - and its libstdc++ calls
    # `fstat64` instead, which nothing on konan side defines. Two C runtimes in one binary is a
    # bug even when it links, so the objects are built against msvcrt to begin with.
    #
    # -posix, not the win32 threading variant: llama.cpp uses std::thread, and libstdc++ only
    # provides it under the posix model. trixie rather than bookworm because llama.cpp reaches
    # for THREAD_POWER_THROTTLING_STATE, which arrived in the mingw-w64 12 headers.
    docker run --rm --platform linux/amd64 \
      -v "$LLAMA_CPP:/llama" -v "$HERE:/shim" \
      -v "$SHARED_OUT:/shared" -v "$STATIC_OUT:/static" -w /work \
      debian:trixie-slim bash -c '
        set -e
        apt-get update -qq && apt-get install -y -qq cmake g++-mingw-w64-x86-64-posix \
          gcc-mingw-w64-x86-64-posix binutils-mingw-w64 > /dev/null
        CC=x86_64-w64-mingw32-gcc-posix
        CXX=x86_64-w64-mingw32-g++-posix
        cmake -B /work/b -S /llama -DCMAKE_SYSTEM_NAME=Windows \
          -DCMAKE_C_COMPILER=$CC -DCMAKE_CXX_COMPILER=$CXX \
          -DCMAKE_RC_COMPILER=x86_64-w64-mingw32-windres \
          -DBUILD_SHARED_LIBS=OFF -DLLAMA_BUILD_TESTS=OFF -DLLAMA_BUILD_EXAMPLES=OFF \
          -DLLAMA_BUILD_SERVER=OFF -DLLAMA_BUILD_TOOLS=OFF -DLLAMA_CURL=OFF \
          -DCMAKE_POSITION_INDEPENDENT_CODE=ON -DGGML_NATIVE=OFF -DGGML_OPENMP=OFF > /dev/null
        cmake --build /work/b --target llama -j 8 > /dev/null

        $CC -O2 -shared -I /llama/include -I /llama/ggml/include \
          -o /shared/dscodex.dll /shim/dscodex_llama.c \
          -Wl,--start-group $(find /work/b -name "*.a" | sort) -Wl,--end-group \
          -lstdc++ -static-libgcc -static-libstdc++

        # From the objects, never by extracting the archives. `ar x` writes every member to its
        # basename and llama.cpp has two named llama.cpp.o - src/llama.cpp and src/models/llama.cpp
        # - so one overwrote the other and took llama_backend_init with it. mingw CMake writes
        # .obj rather than .o; matching only one silently finds nothing.
        $CC -O2 -c -I /llama/include -I /llama/ggml/include -o /tmp/shim.obj /shim/dscodex_llama.c
        OBJS=$(find /work/b \( -name "*.o" -o -name "*.obj" \) -not -path "*CompilerId*" | sort)
        [ -n "$OBJS" ] || { echo "llama.cpp produced no objects" >&2; exit 1; }

        # The archive carries its own libstdc++ and libgcc, and the mingwX64 target deliberately
        # passes no -lstdc++. konan would otherwise add its 9.2 copy alongside this one and the
        # link fails on hundreds of duplicate std:: symbols. One C++ runtime, the matching one.
        mkdir -p /tmp/cxxrt && cd /tmp/cxxrt
        for a in $($CXX -print-file-name=libstdc++.a) $($CC -print-file-name=libgcc.a) \
                 $($CC -print-file-name=libgcc_eh.a); do
          d=$(basename "$a" .a); mkdir -p "$d" && ( cd "$d" && x86_64-w64-mingw32-ar x "$a" )
        done
        rm -f /static/libdscodex.a
        # `q` appends; `r` would replace same-named members and undo the care taken above.
        x86_64-w64-mingw32-ar qcs /static/libdscodex.a /tmp/shim.obj $OBJS /tmp/cxxrt/*/*.o*
      '
    ;;

  *) echo "unknown platform: $PLATFORM" >&2; exit 2 ;;
esac

ls -la "$OUT"/*.dylib "$OUT"/*.so "$OUT"/*.dll 2>/dev/null || true
