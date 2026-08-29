# test25 — a generative runtime that is not Apple-only

[RAD-0051](../../docs/knowledge/research/RAD-0051-a-jvm-generative-runtime.md) specifies this. The
summariser shells out to `mlx-lm`; MLX is Apple-only; so the mechanism behind every number that
component rests on cannot ship. And the pinned model is **16 GB**, inherited from what happened to
be on the machine when `test7` ran rather than established as necessary.

```
./run.sh a                                   # phase A - does a JVM process generate at all
uv run python sweep.py <model.gguf> --n 60   # phase C - what a model's size costs
```

**`run_model` is the only seam.** The summariser's prompt, its four properties, its verifier, its
adjudication and its degradation accounting are imported by `sweep.py` rather than reimplemented,
so the only thing that varies between runs is which model produced the text. Anything else would
be comparing two components and calling it a model comparison.

## Phase A — in-process generation works

`de.kherud:llama:4.2.0` (MIT), loaded from Maven Central, **no server and no network**. It extracts
its own native at startup and the jar carries seven of them — Linux x86_64/aarch64, macOS
aarch64/x86_64, Windows x86/x86_64, Android aarch64 — so a consumer resolving it gets a working
runtime with no install step.

| | |
|---|---|
| model | `gemma-3-270m-it-qat-Q4_0.gguf`, 230 MB |
| prompt eval | 1,639 tokens/s |
| generation | 377 tokens/s |
| per entry, real prompts | **0.6 s** |

The in-process option is the one worth having: a local HTTP server would be easier and would cost
the summariser's first property, turning *no network, no tools* from a structural fact into a
configuration promise.

## The binding vendors llama.cpp, and it lags

`de.kherud:llama:4.2.0` embeds **llama.cpp build 4916**, published 2025-06. It loads `gemma3` and
refuses `gemma4`:

```
error loading model architecture: unknown model architecture: 'gemma4'
```

**So the candidate set is whatever the binding's vendored llama.cpp knew at publish time, not what
llama.cpp supports today.** A new architecture means waiting for a binding release. That is a real
cost of the in-process route and it belongs in the runtime decision rather than being discovered
later — it is the same class of problem as MLX being Apple-only, one step less severe.

## Both bindings are frozen, and that is the finding

`com.github.tjake:jlama-core:0.8.4` (Apache-2.0) is the other candidate. Read from the checked-out
sources rather than inferred from the artefacts:

| | engine | model menu | last commit | pinned since |
|---|---|---|---|---|
| `de.kherud:llama:4.2.0` | JNI over vendored llama.cpp | `GIT_TAG b4916` | **2025-06-20** | **2025-03-18** |
| `com.github.tjake:jlama-core:0.8.4` | its own Java implementation per architecture | bert gemma gemma2 gpt2 granite llama mistral mixtral qwen2 | 2025-10-11 | — |

**Neither is tracking upstream, and `main` is no better than the release in either case.**
java-llama.cpp has pinned llama.cpp to one build for seventeen months and has had no commit in
fourteen. JLama is more recently touched — its last six commits are CLI, docker and build work —
and `main` carries exactly the same nine architectures as 0.8.4. That is why gemma-4 will not
load: not a release-cadence lag that waiting fixes, but a project that has stopped moving.

**So the in-process route means running a 2025-era inference engine against a 2025-era model
menu.** That is the real cost, and it is larger than "you may need to wait for a release".

### Why this is survivable anyway, and where it is not

**The summariser pins its model as part of its specification.** `test7` measured the component's
whole justification against one model, so substituting one produces a component whose behaviour is
unmeasured. A component that deliberately pins a model is not badly served by an engine that runs
that model and does not chase new ones — the freeze bites on *new architectures*, and this design
does not want new architectures, it wants one known one.

Where it does bite is **maintenance**: a native inference engine parses model files, and an
unmaintained one accumulates unfixed parsing bugs in a component whose entire job is handling
input nobody trusts. That is an argument about the engine's security posture rather than about
its features, and it is the one that should decide this.

**Third option, not yet costed:** vendor a current llama.cpp and bind it ourselves. Java 22's FFM
makes that far more tractable than the JNI glue java-llama.cpp had to write — but the codex
targets JVM 17, and this would be taking on an inference binding as a maintained dependency of
this project. Recorded so it is a decision rather than an omission.

## Two harness faults, and the second is a finding about the design

**Both were caught by reading output, not by reading a rate.**

### The prompt was never templated

An instruction-tuned model handed a bare string is not being instructed. It continues the text,
wanders into an imagined conversation and emits `Human:` turns, and the verifier then rejects the
result — which reads as *a model too small for the task* and is nothing of the kind.

| | untemplated | templated |
|---|---|---|
| Qwen2.5-0.5B, JLama | 85% degraded, 47.9 s/entry | **60% degraded, 9.9 s/entry** |
| gemma-3-270m, llama.cpp | 13.3% degraded | **16.7% degraded, 0.1 s/entry** |

Both bindings can apply the model's own template and neither does by default: JLama through
`promptSupport().builder()`, llama.cpp through `applyTemplate(params)` on messages. Any comparison
made before that is between two harnesses, not two models.

### A degradation rate cannot tell a good model from a broken harness

The first templating attempt was wrong in a different way, and produced this:

```
out:  import numpy as np          # for every entry, all 20 of them
```

**It scored 0% degraded.** Every one passed verification.

That is not a near miss. The verifier checks *shape* — imperative mood, second person, length,
whether the text carries a declaration — and `import numpy as np` has none of those. It never
checks whether the sentence has anything to do with the documentation it claims to describe. The
rule that would have caught it is *contains code or markup*, and it was deliberately narrowed from
matching bare words to matching a declaration, because the broad version threw away 16 of 220 real
entries. The narrowing was right and this is what it costs.

**So the sweep needs a faithfulness axis and degradation alone will not do.** RAD-0051 argued that
a smaller model fails verification more rather than leaking more, and that argument survives — but
it is now clear that a model can fail *without* failing verification, by being confidently
irrelevant. That is the shape `test6` measured as dangerous, arrived at from the inside.

It also bears on [#7](https://github.com/dependencyskills/dependencyskills/issues/7)'s acceptance
criterion that the component ship with a self-test proving verification rejects known-bad output.
The self-test covers 15 must-reject cases, all of them *instructions*. None of them is fluent,
well-shaped, unrelated prose.

## Phase C — and one character in the verifier

60 entries of `test5`'s corpus, the summariser's own prompt and verifier, the model's own chat
template. Timings are from a laptop partly on battery and are indicative rather than comparable.

| model | size | runtime | degraded | with backticks stripped | per entry |
|---|---|---|---|---|---|
| `gemma-3-270m-it-qat` Q4_0 | **230 MB** | llama.cpp | 10 of 60 (16.7%) | **2 of 60 (3.3%)** | 0.1 s |
| `Qwen2.5-0.5B-Instruct` | ~1 GB | JLama | 38 of 60 (63.3%) | **2 of 60 (3.3%)** | 9.9 s |
| *`Qwen3-Coder-30B` via mlx-lm, for reference* | *16 GB* | mlx-lm | *6 of 220 (3%)* | — | — |

### The rate was measuring a markdown convention

The degradation reasons, over every entry rather than the ten printed:

| | gemma-3-270m | Qwen2.5-0.5B |
|---|---|---|
| contains code or markup | 8 of 10 | **36 of 38** |
| addresses a reader: 'you' | 2 | 2 |

Both models fail the same rule, at wildly different rates, and the cause is in the pattern:

```python
CODEISH = re.compile(r'[`{}<>|]|=>|;|\bfun\s+\w+\s*\(|\bclass\s+[A-Z]\w*')
```

**A bare backtick is in the character class.** Qwen habitually wraps identifiers in backticks
because it writes markdown-flavoured prose; gemma-3-270m does not. That one character is the
entire difference between 16.7% and 63.3%, and it is not detecting anything: a backtick is not an
imperative and not code. The rest of the pattern — braces, angle brackets, `=>`, `;`, a `fun` or
`class` declaration — is doing real work. This part is doing formatting work while wearing a
safety rule's name.

**Stripping backticks before verification collapses both models onto the same number.**

| | degraded | reasons |
|---|---|---|
| gemma-3-270m, 230 MB | 2 of 60 (3.3%) | 2 × `addresses a reader: 'you'` |
| Qwen2.5-0.5B, ~1 GB | 2 of 60 (3.3%) | 2 × `addresses a reader: 'you'` |
| *30B reference* | *3%* | — |

Identical rate, identical failure mode, across a 4× size difference and two different runtimes —
and level with a model seventy times larger. At n=60 two rejections is a small number and the
agreement could be partly luck, but the direction is not in doubt: **the apparent size effect was
the backtick, and there is no size effect left to see on this axis.**

That is a result about the measurement, not about the models. Degradation rate cannot rank these
models because it does not vary with them. Whatever separates a 230 MB model from a 16 GB one for
this task, it is not the rate at which their output fails verification — so the sweep RAD-0051
specified cannot answer the question it was written to answer, and the faithfulness and retrieval
axes are not a refinement but the whole measurement.

**So the size question was partly a verifier question.** RAD-0051 asked how small a model can be
before the component stops working. On this evidence a large part of what looked like small-model
failure was the component rejecting faithful output for using backticks — and the fix is in the
verifier, not the model. That belongs in
[#7](https://github.com/dependencyskills/dependencyskills/issues/7) whatever else is decided.

**What 270 million parameters produces**, templated:

> `Apache5` — *"The capability is to use the Apache HTTP client to create a client with the engine."*
> `customizeRequest` — *"The capability is to customize a [RequestConfig.Builder] in the specified [block]."*

Faithful, and formulaic: nearly every output opens *"The capability is to…"*. Whether that costs
retrieval is a question for the recall half and is not answered by reading them.

**JLama is far slower here** — 9.9 s against 0.1 s — and the comparison is not clean: JLama is
loading a raw Hugging Face model and quantising at load, where it publishes pre-quantised `-JQ4`
variants that are likely the intended path. Worth one retry before the number is quoted.

## Still to run

- **Phase B** — the same model through `mlx-lm` and through this runtime, to establish the runtime
  reproduces rather than merely runs. RAD-0047 found a 25-point discrepancy at exactly this step.
- **Phase C** — the ladder. Needs GGUF models in architectures build 4916 supports.
- **Phase D** — `test7`'s arm on the smallest survivor. Cannot be skipped: degradation and
  retrieval are proxies, and the component exists because of 0-of-3 harm with 2-of-3 tasks.
