# Is Per-CPU Tuning Worth It for the Shipped Native?

RAD-0053 · 2026-08-28 · v2
Keywords: should we ship one arm64 binary or two; does i8mm help the summariser; GGML_CPU_ALL_VARIANTS on macOS; runtime CPU dispatch in ggml; building on the oldest supported hardware; M1 versus M5; will the native run on an older CPU; how to verify a binary is not over-tuned.
Measured against: the `macos-aarch64` artefact built on Apple M5 Pro 2026-08-28, run on an Apple M1 Pro the same day; llama.cpp b19cbe9.

**v2 (2026-08-28) — step 1 is done and it passed.** The arm64 artefact built on newer silicon loads, resolves its symbols and generates on an M1 Pro, producing output byte-identical to the M5 run. The compatibility question is closed on hardware; the tuning question below is still open and still deferred. The llama.cpp facts below were read from `ggml/src/CMakeLists.txt` at commit b19cbe9, and the instruction counts from the `macos-aarch64` artefact built on 2026-08-28.

## Question

`implementations/codex/inference` ships one static native per platform. Every arm64 Mac gets the same binary, built to a baseline that omits everything an M1 cannot execute.

**Is leaving that performance on the floor worth it, or should the arm64 artefact be split by CPU generation?** And the question behind it: how do we know a shipped native runs on the oldest hardware it claims to support, without owning one of each?

The second question is the more important one. The first build made here was tuned to `apple-m4` by ggml's own CPU detection, and would have failed on any older Apple silicon. It was caught by reading a configure log.

## Trail

### Upstream offers runtime dispatch, and not on macOS

`GGML_CPU_ALL_VARIANTS` compiles several CPU backends and selects one at load time. The variant list is real and detailed:

```
armv8.0_1
armv8.2_1    DOTPROD
armv8.2_2    DOTPROD FP16_VECTOR_ARITHMETIC
armv8.2_3    DOTPROD FP16_VECTOR_ARITHMETIC SVE
armv8.6_1    DOTPROD FP16_VECTOR_ARITHMETIC SVE MATMUL_INT8
armv9.2_1    DOTPROD FP16_VECTOR_ARITHMETIC SVE MATMUL_INT8 SME
```

**But the ARM list is gated to Linux and Android.** The x86 list — `sse42` through `sapphirerapids` — is not gated by operating system. So the answer may differ by architecture: x86 targets can plausibly get dispatch for free where arm64 macOS cannot.

It also requires `GGML_BACKEND_DL`, which requires `BUILD_SHARED_LIBS`. That is exactly the arrangement the static build exists to avoid: one self-contained library per platform rather than `libllama` plus five `libggml-*` whose load order and rpaths have to be right on three operating systems. So on macOS, "optimise for both" means **two artefacts and a selection mechanism we own**, not a flag.

### The delta is one extension, and it is on the wrong side of the workload

M1 is ARMv8.5 with DOTPROD and FP16 vector arithmetic; M2 and later add I8MM and BF16. The shipped binary already uses `sdot` 1,047 times, so DOTPROD is in. **The only thing a newer-CPU build would add is `MATMUL_INT8`.**

That accelerates batched matrix multiply, which is prompt processing. Measured on this workload:

| | |
|---|---|
| prompt eval | 34 ms / 56 tokens (1,639 tok/s) |
| generation | 319 ms / 120 tokens (377 tok/s) |

**Generation is roughly 90% of the time**, and the extension we would split the build for helps the other 10%. Against 0.1 s an entry and 25 minutes for a full resolved graph, even a generous 30% on prompt processing saves a few minutes — in exchange for two binaries, a selection path, and a second thing CI must build and test.

*This is reasoning from where the time goes, not a measurement of i8mm. It is exactly the kind of assumption that has been wrong repeatedly in this project, which is why the recommendation below measures it rather than settling it here.*

### Verifying an artefact is not over-tuned

Static evidence available without the hardware, and it was strong enough to retire the M1 question as an active risk:

| check | result |
|---|---|
| CPU flags used by the build | none — *"Checking for ARM features using flags:"* is empty |
| I8MM instructions (`smmla`, `ummla`, `usmmla`, `usdot`, `sudot`) | **0** |
| BF16 instructions (`bfdot`, `bfmmla`, `bfcvt`, `bfcvtn`) | **0** |
| baseline in use (`sdot`, `fcvt`, `fmla`) | 1,047 / 698 / 2,183 |

A binary cannot execute an instruction it does not contain, so this is close to conclusive. It is also a check somebody has to remember to run, against a detection mechanism upstream can change.

**Building on the oldest supported hardware makes it structurally impossible instead.** The `apple-m4` tuning could not have existed on an M1. That is the standard discipline for native distribution and it is cheaper than auditing disassembly whenever llama.cpp changes how it probes a CPU.

*Virtualisation does not substitute. A macOS VM or container on Apple silicon inherits the host's CPU and reports the host's features; only older silicon answers the question.*

## Findings

Established by reading upstream and the artefact, not by running anything:

- **ggml's ARM runtime dispatch is unavailable on macOS**, and needs shared libraries we deliberately do not ship.
- **x86 dispatch is not OS-gated**, so `linux-x86_64` and `windows-x86_64` may be able to have it where arm64 macOS cannot.
- **The arm64 tuning delta is `MATMUL_INT8` and nothing else** that a compiler emits unprompted.
- **The shipped artefact contains no instruction an M1 lacks**, and was built with no CPU-specific flag.

Not measured:

- What `MATMUL_INT8` is actually worth on this workload, on this hardware.
- Whether the arm64 artefact runs on an M1. Strongly implied, not observed.
- Whether x86 dispatch composes with a static build at all, or forces shared libraries there too.

## Recommendation

**Ship one baseline arm64 build for now.** The measured shape of the workload does not justify two artefacts, and the alternative costs a selection mechanism this project would own and maintain.

**Do this when the M1 host is available, in order.**

1. ~~Run the existing suite on it.~~ **Done.** Tested through a `dlopen` driver rather than the
   JVM suite, because the machine had JDK 21 and this module needs 22+ for final FFM — and the
   question was about the native code, not the binding. Installing a JDK to answer it would have
   changed the machine to test something the C driver tests directly.
2. **Make it the build host for `macos-aarch64`.** Over-tuning then cannot be introduced, rather than being caught by inspection. It does not cover `macos-x86_64`, which cross-compiles — and which may not be worth shipping at all.
3. **Then measure the tuning delta**, and only then: two dylibs over the same 60 entries on newer silicon, one baseline and one with `MATMUL_INT8`, same model, same prompts. If the difference is under about 10% end to end, this record is closed for good.
4. **Ask the x86 question separately.** Whether `GGML_CPU_ALL_VARIANTS` can serve `linux-x86_64` and `windows-x86_64` without forcing shared libraries is a different question with a possibly different answer, and the x86 feature spread — SSE4.2 to AVX-512 — is far wider than arm64's.

**What would change the answer.** A larger model moving the time back toward prompt processing; ARM server hardware entering scope, where SVE and SME are real and the variant list is worth having; or the summariser moving from one comment at a time to batched prompts, which is exactly the shape `MATMUL_INT8` accelerates.

## Connections

- [RAD-0051](RAD-0051-a-jvm-generative-runtime.md) v2 — why the native exists, why in-process, and the throughput numbers this trades against
- `implementations/codex/inference/README.md` — the build, the static-linking decision, and how the natives are packaged
