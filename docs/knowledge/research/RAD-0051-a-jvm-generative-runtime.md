# What Can Actually Run the Summariser

RAD-0051 · 2026-08-27 · v2
Keywords: how does a JVM tool run a local language model; MLX is Apple-only; JLama versus llama.cpp versus DJL; running a model in-process instead of a server; does Ollama break the no-network property; how small can the summariser model be; is 30B required to rewrite a doc comment; what a generative runtime costs to ship; GGUF and safetensors on the JVM.
Measured against: `experiments/test25` — `de.kherud:llama:4.2.0` and `com.github.tjake:jlama-core:0.8.4` on an M5 Pro partly on battery, against `gemma-3-270m-it-qat` Q4_0 and `Qwen2.5-0.5B-Instruct`, 60 entries of `test5`'s corpus through the summariser's own prompt and verifier; 2026-08-28.

**v2 (2026-08-28) — the probe ran, and it moved the question.** In-process generation works and is the reason to prefer it: 0.1 s an entry against 5 s for a subprocess that reloads the model each time, which at one project's resolved graph is 25 minutes against 21 hours. Both published JVM bindings are frozen, and reading their source says why — one of them binds llama.cpp's *example server internals* rather than its stable C API. And the size sweep this record specified cannot answer the question it was written to ask: degradation rate does not vary with model size once one character is fixed in the verifier. The sections below are the original v1 argument; what the probe found is at the end.

## Question

The summariser rewrites every doc comment into one factual sentence before anything reaches an agent. It is the quarantine, and `test7` measured its whole justification — a planted credential stopped 0 of 3, the developer's task still completing 2 of 3.

It runs today by shelling out to `mlx-lm` against a 16 GB copy of `Qwen3-Coder-30B-A3B-Instruct-MLX-4bit`. **MLX is Apple-only**, so that mechanism cannot ship. And the size was inherited rather than chosen: 30B is what was on the machine when `test7` ran, and every number since has been anchored to it.

Two questions, and they are not independent:

1. **What can run a local model from a JVM tool, on Linux and Windows as well as a Mac?**
2. **How small can that model be before the component stops working?**

The first constrains the second — a candidate set is whatever the chosen runtime can load — which is why the runtime is settled first even though the size question is the one with more upside.

## Trail

### Why the size question is a retrieval question, not a safety one

This matters because it decides how the sweep is scored.

The summariser has four properties, and the fourth is that **output is verified and failure degrades to signature-only**. Verification is not trusted to be right, only to be conservative, and its failure lands on a state separately measured as safe — `test0` found a bare symbol and signature enough for an agent to *use* a capability, 7 of 8.

So a weaker model does not leak more. It fails verification more, and those failures become degraded entries. That is a **retrieval** cost, and it is already known to be measurable: an over-broad verifier rule degraded 16 of 220 entries and cost a full point of first-hit, and narrowing it halved the degradation.

This is the same shape as [RAD-0048](RAD-0048-where-the-encoder-size-cutoff-is.md), which asked where the encoder's size cutoff was and found a 33 MB model no worse than a 2,267 MB one at realistic corpus size. Nobody has asked the equivalent question of the generator.

### The runtime choice is not only packaging

A local model can be reached three ways, and they are not equivalent for this component.

**A local HTTP server** — Ollama, LM Studio, llamafile — is the easy answer and it costs the first property. *No network, no tools* is what makes a subverted summariser harmless, and it is a property of the deployment rather than the prompt. A localhost socket is still a socket: it is a listener other processes on the machine can talk to, it is a hop that can be pointed elsewhere by configuration, and "no network" becomes a claim about what the process happens to connect to rather than about what it can reach. It also makes the model someone else's installation, so the pinning that makes `test7`'s result meaningful becomes a request rather than a fact.

**Shelling out to a local binary** is what the experiment does. It keeps the model pinned and reachable only by this process, and it moves the platform problem into "which binary, on which platform" — which is exactly the problem MLX creates.

**In-process** keeps the model inside the tool's own process, where "no network" is structural. It is also the only option that makes the model a dependency the build resolves rather than a thing a developer installs first.

### What is on the JVM, read from the published artefacts

| | coordinate | licence | platforms shipped |
|---|---|---|---|
| **java-llama.cpp** | `de.kherud:llama:4.2.0` | MIT | Linux x86_64/aarch64, macOS aarch64/x86_64, Windows x86/x86_64, Android aarch64 — all in one jar |
| **JLama** | `com.github.tjake:jlama-core:0.8.4` | Apache-2.0 | `jlama-native` classifiers for linux-x86_64, osx-aarch_64, osx-x86_64, windows-x86_64 |
| **DJL** | `ai.djl:api:0.36.0` | Apache-2.0 | engine-dependent; already the pick for embeddings in RAD-0035 |

Both licences are compatible with this project. **java-llama.cpp bundles every native in the main jar**, which is the same shape as the tree-sitter dependency the harvester already carries and is the arrangement that costs a consumer nothing. JLama splits natives by classifier and leans on the Vector API, which the Lucene probe in RAD-0047 already needed `--add-modules jdk.incubator.vector` for — so that flag is not a new cost.

ONNX Runtime GenAI publishes no Java artefact to Central under the obvious coordinates; if it is wanted it has to come another way, and that is a reason to prefer the two that are already there.

**Model format follows the runtime.** llama.cpp wants GGUF; JLama wants safetensors. That decides which quantisations of a given model are reachable, so it is part of the runtime choice rather than a later detail.

### What this does not reopen

[RAD-0047](RAD-0047-a-jvm-embedding-runtime.md) settled the **embedding** runtime — DJL over ONNX Runtime, on CPU, reproducing the reference vectors at cosine 0.99999. Nothing here disturbs that, and the two should not be collapsed: an encoder is 78 MB jarred and a generator is three orders of magnitude larger. RAD-0047 says as much, and treating them as one question is how the small one gets held up by the large one.

## Findings

Established, by reading the published artefacts rather than by running anything.

- **Two maintained JVM inference libraries exist, both permissively licensed, both shipping natives for Linux, macOS and Windows.** The cross-platform objection to running a model in-process is not fatal.
- **`de.kherud:llama` carries every platform in one jar**, so a consumer resolving it gets a working runtime with no install step and no classifier selection.
- **MLX cannot ship**, and the current mechanism depends on it entirely.
- **The pinned model is 16 GB on disk**, and its size was inherited from what `test7` happened to run against rather than established as necessary.

Not measured. Everything that matters.

- Whether either library loads a model of the class this task needs, and at what speed on CPU.
- Whether a small model — 3B, 4B, 8B — satisfies the four properties, and what it costs in degradation and retrieval.
- Whether `test7`'s result holds on anything other than qwen3-coder-30b. The model is part of the specification precisely because this is unknown.
- Whether either library's tokenizer and chat templating reproduce what `mlx-lm` produced, which is the same reproduction risk RAD-0047 found and resolved for embeddings.

## Recommendation

**Run a probe, runtime first, in this order. Each step is allowed to end it.**

**1. Load anything at all, in-process, on this machine.** `de.kherud:llama` first, because it bundles every platform. Success is a JVM process generating from a small GGUF model with no server and no network. Failure here sends the whole question back to shelling out, and the answer becomes "which binary ships where".

**2. Reproduce the existing behaviour before changing the model.** Run the pinned 30B — or the nearest GGUF equivalent — through the JVM runtime and re-run the summariser's own self-test and its 220-entry slice. The success criterion is *reproducing a measurement*, not *loading a model*; RAD-0047 found a 25-point discrepancy at exactly this step, from pooling nobody had chosen. Do not skip it because the model is nominally the same.

**3. Only then sweep the size.** Hold the four properties and the verifier constant and vary the model down through what the runtime can load. Score two things per model: the **degradation rate**, and **retrieval on the 17 needs over the 220-entry slice**. `--reverify` already exists, so a changed verifier does not cost another round of model calls.

**4. Re-run `test7`'s arm on the smallest model that survives step 3.** This is the one that cannot be skipped. Degradation and retrieval are cheap proxies; the component exists because of 0-of-3 harm with 2-of-3 tasks completing, and a smaller model that retrieves well and leaks is a worse outcome than no change at all.

**Prefer in-process over a local server, and say why in the record rather than in a commit message.** The no-network property is what makes this component safe against input it fails to notice, and a localhost server converts that from a structural fact into a configuration promise. If a server turns out to be the only workable answer, that is a finding worth stating plainly — it changes what the component can claim.

**What would change the answer.** A runtime that cannot reproduce step 2 at all; a smallest-viable model still large enough that shipping it is the dominant cost, in which case the question becomes distribution rather than size; or `test7`'s arm failing to hold below some size, which would make 30B load-bearing and worth recording as such instead of inherited.

## Connections

- [RAD-0047](RAD-0047-a-jvm-embedding-runtime.md) — the same probe for the encoder, and why the two runtimes are different problems
- [RAD-0048](RAD-0048-where-the-encoder-size-cutoff-is.md) — the size-sweep shape this borrows
- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) — what the summariser does and does not buy, and the degradation cost of an over-broad verifier
- [RAD-0035](RAD-0035-a-small-local-model-for-the-prose-gap.md) — DJL picked for embeddings, and the licence discipline reused here
- `experiments/summariser/` — the implementation, the four properties, and the verifier self-test


---

# v2 — what the probe found

## In-process is the answer, and throughput is why

The original framing weighed in-process against a local server on the *no-network* property. That was the wrong axis to lead with. The measured one is throughput, because the summariser is a batch job over a whole dependency graph.

| | in-process, 270M | subprocess per call |
|---|---|---|
| one small project, ~5,400 entries | **9 min** | 7.5 h |
| one resolved graph, 14,899 entries | **25 min** | 20.7 h |
| the corpus, 537,463 entries | **14.9 h** | 31 d |

A subprocess is not slow in itself; it pays **model load per entry**, and at 14,899 entries it pays it 14,899 times. In-process loads once and streams. That the summariser's only seam is `run_model(prompt) -> text` makes the swap a one-function change rather than a redesign.

The bottom row reaches past this record: precomputing a public codex for 1,798 libraries is about fifteen hours on one laptop, which is what makes [RAD-0052](RAD-0052-distributing-a-precomputed-codex.md)'s distribution argument affordable rather than aspirational.

*Measured one prompt at a time; llama.cpp batches and this did not, so it is a floor on throughput rather than a ceiling.*

## Why both bindings are frozen, from their source

| | engine | model menu | last commit | pinned since |
|---|---|---|---|---|
| `de.kherud:llama:4.2.0` | JNI over vendored llama.cpp | `GIT_TAG b4916` | 2025-06-20 | **2025-03-18** |
| `com.github.tjake:jlama-core:0.8.4` | its own Java implementation per architecture | gemma2, qwen2, llama, mistral, granite, mixtral, gpt2, bert | 2025-10-11 | — |

`main` is no better than the release in either case. The cause is visible in one line of `jllama.cpp`:

```cpp
#include "server.hpp"      // and 16 uses of server_context, server_slot, common_params
```

**It binds llama.cpp's example *server internals*, not `llama.h`.** That header carries no compatibility promise and is refactored continuously, so every upstream bump is a re-merge rather than a version change. Seventeen months of one pin is what that costs. It also means we have been running llama.cpp's slot scheduler inside the JVM without a server.

JLama writes its own implementation per architecture, which is a defensible reason to lag and not a reason to dismiss it — at the small end its menu covers Qwen2.5-0.5B, TinyLlama and gemma-2-2b. It also **segfaults on JDK 26** in its own ARM kernel (`gemm_bf16_128_arm`) and runs on 21.

## Binding `llama.h` ourselves, now costed

This record's v1 left this as "not yet costed". It is smaller than it looks, and the existing binding's own dimensions are the evidence: 853 lines of C++, 15 JNI entry points, 2,584 lines of Java.

**Binding the stable C API is easier than what they did, not harder.** What this component needs from `llama.h` is small — load a model, apply the chat template, tokenize, decode greedily to EOS, detokenize. Temperature zero, no sampling zoo, no grammars, no slots. With FFM and `jextract` there is no glue to hand-write; a few hundred lines of Kotlin over generated bindings covers it. It needs JVM 22+, which is available to everything except the Gradle plugin.

**The real cost is the natives, not the binding**: building llama.cpp for macOS arm64/x86, Linux x86/arm64 and Windows and publishing them. Desktop only — nothing that runs a local model belongs in an Android or iOS artefact.

**Kotlin Multiplatform is not the shape for this yet.** `cinterop` serves Kotlin/Native targets and does nothing for the JVM target, so a KMP binding would carry two binding layers. Every consumer today is JVM, and a plain JVM jar with bundled natives is what `tree-sitter` and `de.kherud:llama` both already do. KMP earns its place only if a native CLI is wanted, where JVM startup dominates a one-shot query — the natives are shared between the two paths even though the binding layer is not.

So the option list is three, and the middle one is ruled out:

| | |
|---|---|
| use a published binding | frozen at 2025 architectures; survivable because the component pins its model, but the native parser is unmaintained |
| ~~fork and bump~~ | inherits the `server.hpp` coupling — the treadmill that stopped them |
| **bind `llama.h` ourselves** | small stable surface, generated bindings; the cost is the multi-platform native build |

## The size sweep this record specified cannot rank models

v1 said a smaller model fails verification more rather than leaking more, so degradation rate would price it. **It does not.**

| model | size | degraded | with output backticks normalised |
|---|---|---|---|
| `gemma-3-270m-it-qat` | 230 MB | 16.7% | **3.3%** — 2 of 60, both `you` |
| `Qwen2.5-0.5B-Instruct` | ~1 GB | 63.3% | **3.3%** — 2 of 60, both `you` |
| *`Qwen3-Coder-30B`, reference* | *16 GB* | *3%* | — |

The verifier's `CODEISH` pattern contains a bare backtick. Qwen wraps identifiers in backticks because it writes markdown-flavoured prose; gemma does not. **That one character was the entire apparent size effect.** Normalise it and a 230 MB model, a 1 GB model and a 16 GB model are indistinguishable on this axis.

Cleaning the *input* does not help — rendering the KDoc markdown away left degradation at 18–20%, so the markup is the model's own style rather than an echo. The fix is to normalise the output before verifying, not to reject the entry.

**So degradation rate measures conformance to the verifier's shape rules, not capability**, and the sweep must be rebuilt on faithfulness and retrieval. Two demonstrations of why, both from this probe:

- A mis-templated run emitted `import numpy as np` for every entry and scored **0% degraded**. The verifier checks shape and never checks relatedness.
- An instruction-tuned model handed a bare prompt is not being instructed. Untemplated, JLama scored 85%; templated, 60%. Every number taken before templating compared harnesses rather than models.

## Recommendation, revised

**In-process, and the throughput numbers are the argument.** The no-network property still favours it over someone else's daemon, but it is no longer the leading reason.

**Rebuild the sweep on faithfulness and retrieval.** Degradation is a floor check, not a score. `summarise_corpus.py` over the 220-entry slice, then `eval_recall.py`, is the shape — the same one RAD-0040 used.

**Fix the verifier before measuring anything else.** Normalising backticks out of the candidate before verification recovers 8 of 60 entries on one model and 36 of 60 on another, for no safety change: a backtick is not an imperative and not code. Every rejection is a retrieval loss.

**Cost the natives before choosing between a published binding and our own.** The binding is a weekend; the multi-platform build and release is the commitment, and it is the only part that decides.
