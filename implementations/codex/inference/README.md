# inference

In-process text generation over llama.cpp. Load a model once, generate many times, in the calling process — no daemon, no subprocess, no network.

## Why in-process

The summariser is a batch job over a whole dependency graph, and a generator that reloads its model per call pays that cost every time. Measured on one real resolved graph of 14,899 documented declarations:

| | in-process | subprocess per call |
|---|---|---|
| one small project, ~5,400 entries | **9 min** | 7.5 h |
| one resolved graph, 14,899 entries | **25 min** | 20.7 h |
| the whole corpus, 537,463 entries | **14.9 h** | 31 d |

A subprocess is not slow in itself; it pays model load per entry, and at 14,899 entries it pays it 14,899 times.

It also keeps the property the summariser rests on. *No network, no tools* is what makes that component safe against input it fails to notice, and in-process it is structural rather than a promise about what a local daemon happens to be configured to reach.

## Why a C shim rather than a direct binding

`native/dscodex_llama.c` exposes four functions. Nothing but pointers, ints and bytes crosses into the JVM.

```c
void *dsc_load(const char *path, int n_ctx, int n_gpu_layers);
int   dsc_apply_template(void *h, const char *user, char *out, int out_len);
int   dsc_generate(void *h, const char *prompt, char *out, int out_len, int max_tokens);
void  dsc_free(void *h);
```

llama.cpp's own entry points take parameter structs **by value**, and those structs gain fields between releases — `llama_model_params` grew `load_mode`, `tensor_read_lazy`, `no_host`, `no_alloc` and `load_mtp` between the build the published JVM binding pins and today. Describing them from Java means a hand-maintained memory layout per llama.cpp version, and a mismatch there is not a compile error: it is silent memory corruption. The shim absorbs that, so an upstream change breaks the compilation of a hundred lines of C we own.

**This is also the difference from `de.kherud:llama`.** That binding includes `server.hpp` and uses llama.cpp's example-server internals in sixteen places. That header carries no compatibility promise and is refactored continuously, which is why it has been pinned to one build since March 2025 and why its project went quiet three months later. Binding `llama.h` — the actual public C API — is a smaller and far more stable surface.

## Why Kotlin Multiplatform

**One codebase, every target, in one pass.** The server — and a UI over it — builds for every
platform from the same source, with no per-platform choice for anyone to get wrong. That is the
reason this is a multiplatform module and not a JVM library with a jar full of shared objects.

The two halves reach the same four symbols from the same header, so they cannot drift into two
descriptions of one contract:

| target | how it reaches the shim | what a consumer gets |
|---|---|---|
| `jvm()` | FFM, by symbol name, from a library extracted at first use | classes plus one platform's native jar |
| `macosArm64`, `linuxX64`, `linuxArm64`, `mingwX64` | cinterop, **statically linked into the binary** | one executable, nothing to extract |

The native targets are the interesting half. There is no library to unpack, no path to search and
no jar to open: llama.cpp is simply part of the executable. That makes a native CLI or a native
server a packaging exercise rather than an open question — and it is why `expect`/`actual` here is
the idiom doing its job rather than a workaround.

*`macosX64` is declared and Kotlin now deprecates it — Intel Macs are past the point of being
worth a target, and an Intel Mac cannot fall back to the arm64 build the way an Apple-silicon Mac
runs Intel code. It is a candidate to drop rather than to keep building.*

### What a JVM consumer downloads

The natives are published one artefact per platform rather than bundled together, so nobody takes
five to use one.

| | download |
|---|---|
| `inference-jvm`, classes only | **13 KB** |
| plus `macos-aarch64` | 1.7 MB |
| plus `linux-x86_64` | 2.3 MB |
| *previously, one fat jar* | *29 MB* |

Selection is by classifier rather than by Gradle attributes, deliberately: attribute-based
selection only fires when the *consumer's* configuration carries `OperatingSystemFamily` and
`MachineArchitecture`, which an ordinary JVM consumer's does not — so it resolves ambiguously, or
picks one and fails at `dlopen`, which moves the failure from build time to run time.

```kotlin
implementation("org.dependencyskills.codex:inference:0.1.0")
runtimeOnly("org.dependencyskills.codex:inference:0.1.0:macos-aarch64")
```

This project ships a Gradle plugin, so that second line can be added for the host rather than
typed. A native consumer needs neither line.

## Building the native

```
cmake -B build-shared -DBUILD_SHARED_LIBS=ON -DLLAMA_BUILD_TESTS=OFF \
      -DLLAMA_BUILD_EXAMPLES=OFF -DLLAMA_BUILD_SERVER=OFF -DLLAMA_CURL=OFF   # in llama.cpp
cmake --build build-shared -j 8

cc -O2 -shared -fPIC -I <llama.cpp>/include -I <llama.cpp>/ggml/include \
   -o native/build/libdscodex.dylib native/dscodex_llama.c \
   -L <llama.cpp>/build-shared/bin -lllama -Wl,-rpath,<llama.cpp>/build-shared/bin
```

**Not yet packaged, and this is the outstanding work.** The library is found through `dscodex.native.dir`, then `java.library.path`, then the platform's search — and the `dylib` currently carries an rpath into a llama.cpp checkout. Producing `libdscodex` and `libllama` for macOS arm64/x86, Linux x86/arm64 and Windows in CI, and packaging them into the jar the way `tree-sitter` does, is the real cost of this module. The binding was a weekend; the native build and release is the commitment.

## Using it

```kotlin
openGenerator(modelPath, contextTokens = 2048).use { generator ->
    generator.generate(prompt, maxTokens = 200)
}
```

The chat template is applied inside `generate`. That is not a convenience: an instruction-tuned model handed a bare string is not being instructed — it continues the text, wanders into an invented conversation and emits turns of its own, which a verifier downstream then rejects. Measured, that reads as a model too small for the task and is nothing of the kind.

A model with no chat template is not an error. It is a base model, and the bare prompt is the right thing to send it.

## Measured

`gemma-3-270m-it-qat` Q4_0, 230 MB, CPU only, on an M5 Pro:

| | |
|---|---|
| model load | ~0.5 s |
| generation, after load | **14 ms** for ten tokens |
| a summariser entry, end to end | **0.1 s** |

Failure is refused rather than crashed on: a missing file, and a file that is not a GGUF, both raise `GeneratorUnavailableException`. The index is an aid, and a component that cannot load its model must not take a build with it.


## Where this pauses, and what to read before picking it up

The JVM path works end to end and is published correctly. The native targets compile, link and
run on `macosArm64`; the rest are built but unexercised. Several things below were got wrong once
already, so the references are recorded against the question each one bears on rather than as a
reading list.

| open question | reference |
|---|---|
| Whether `nativeMain` is the right shared source set, and what the default hierarchy already gives us — cinterop commonization had to be turned on by hand, which suggests the layout is fighting the template | [The multiplatform hierarchy](https://kotlinlang.org/docs/multiplatform/multiplatform-hierarchy.html) |
| How a native **executable** is produced, which is what a native CLI or server needs and what the native targets exist for | [Build native binaries](https://kotlinlang.org/docs/multiplatform/multiplatform-build-native-binaries.html) |
| Packaging that executable for distribution, including with a UI over it | [Compose native distribution](https://kotlinlang.org/docs/multiplatform/compose-native-distribution.html) |
| What each target's publication actually contains, and where resources and natives are expected to live | [Discover a multiplatform project](https://kotlinlang.org/docs/multiplatform/multiplatform-discover-project.html) |

### Known-incomplete

- `linux-x86_64` has no static archive, so `linuxX64` will not link as a Kotlin/Native target. Its
  shared library exists and its JVM path is fine.
- Only `macosArm64` has been linked and run. The other four are built and untested.
- Every native here was produced by running `native/build.sh` on one laptop. Until CI builds them,
  the matrix is reproducible only in the sense that the script exists.
