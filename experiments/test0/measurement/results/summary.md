# test0 A/B — results

## First sweep: content value (4 capabilities × 2 models, 2026-08-19)

Condition **A** = no codex; **B** = a rich codex entry. Third-party scenario
(synthetic dependency, zero training exposure). Score = did the output use the
provided opaque symbol.

| capability | symbol | Claude A | Claude B | Gemini A | Gemini B |
|---|---|---|---|---|---|
| retry | `Policy` | reinvent | used | reinvent | used |
| cache | `BoundedStore` | reinvent | used | reinvent | used |
| debounce | `Coalescer` | reinvent | used | reinvent | used |
| parse | `RowReader` | reinvent | used | reinvent | used |

**Totals: A 0/8 used, B 8/8 used** — a complete flip on both tools. Every A control
reinvented with competent stdlib/coroutines code and *refused to guess* the unknown
API, naming the missing docs as what it lacked.

Claude arm: in-session subagents. Gemini arm: Antigravity `agy`, Gemini 3.7 Flash.
Raw outputs under `results/`. Interpretation and caveats: RAD-0016.

## Doc-level gradient (2026-08-19)

A **bare** entry (symbol + signature only, no capability prose) added between A
(nothing) and B-rich (full entry). Columns are Claude / Gemini.

| capability | A (none) | bare (sig only) | rich (full) |
|---|---|---|---|
| retry | reinvent / reinvent | used / used | used / used |
| cache | reinvent / reinvent | used / used | used / used |
| debounce | reinvent / reinvent | used / used | used / used |
| parse | reinvent / reinvent | **reinvent** / used | used / used |

**Use-rate: A 0/8 → bare 7/8 → rich 8/8.** The signature carries most of the use — a
bare entry already flips 7/8. The one gap is `parse`, where the task needed
quoted-field handling the signature does not guarantee: **Claude refused to assume it
(reinvented); Gemini-flash assumed it (used)**; the rich prose closed the gap. So the
**syntactic face drives use**, the **semantic prose matters at the margin** (fitness
the signature can't establish) and **model-dependently** (a cautious model needs it
more). See RAD-0016.

## External validity — real libraries (2026-08-19)

Three version-pinned axes, condition A (no codex) vs B (codex/harvested entry),
Claude subagents + Gemini 3.7 Flash.

| library | pin | Claude A | Claude B | Gemini A | Gemini B |
|---|---|---|---|---|---|
| kotlinx-datetime (drift) | 0.7.0 | correct | correct | correct | correct |
| Arrow (habit) | 2.0 | correct | correct | correct | correct |
| kaml (sparse, 4% docs) | 0.104.0 | correct | correct | correct | correct |

**Zero lift.** Both models produced the correct current API in condition A for all
three — `kotlin.time.Instant`, Arrow 2.0's `zipOrAccumulate`/`either`, kaml's
`Yaml.default.decodeFromString`. The codex changed nothing. The content-value lift is
real only where the model doesn't already know the API (synthetic capabilities;
first-party/private code; post-cutoff change). **Doc coverage ≠ training exposure**
(kaml: 4% docs, known cold via code). The drift null was a *cutoff
artifact*: re-run on an **older model (Gemini 3.1)**, condition A produced the stale
`kotlinx.datetime.Instant` and the codex corrected it to `kotlin.time.Instant` — the
**drift lift, demonstrated**. So the codex's value is the **gap** between the model's
knowledge and the classpath: ~zero when the model is current on the library, real
when it is stale (older/smaller model, post-cutoff change) or facing genuinely
unknown code. See RAD-0016.

## Local models — can the codex make a personal model usable? (2026-08-20)

The same content-value A/B, run against small models served locally through LM Studio
as a raw chat completion — so condition **A** is the task alone and **B** is the task
with the codex entry pasted in (the pure content axis, no retrieval confound).
Deterministic decode (temperature 0, seed 0). Ladder and load params: `local-models.md`.

### Synthetic content-value (used the opaque symbol?)

| model | params | A | B |
|---|---|---|---|
| gemma-3-270m | 270M | 0/4 | **3/4** |
| gemma-3-1b | 1B | 0/4 | **4/4** |
| gemma-4-e4b | 4B | 0/4 | **4/4** |
| gpt-oss-20b | 20B (MoE) | 0/4 | **4/4** |
| devstral-small-2-24b | 24B (Mistral, code) | 0/4 | **4/4** |
| qwen3-coder-30b-a3b | 30B (MoE, code) | 0/4 | **4/4** |
| nemotron-3-nano-30b-a3b | 30B (MoE, NVIDIA) | 0/4 | **4/4** |
| qwen3-32b | 32B (Qwen, dense) | 0/4 | **4/4** |
| llama-3.3-70b | 70B (Llama, dense) | 0/4 | **4/4** |

The flip is **universal from ~1B up** — every local model used the provided capability
every time *with* the entry and reinvented it every time *without*. At 270M it is 3/4:
the one miss (retry) is the model emitting broken Kotlin (`import kotlinx-datetime`, a
`httpGet` that calls itself), not ignoring the entry. Below a capability floor the model
cannot act on any guidance, the codex included — the bottleneck there is the model, not
the index.

### External drift — real libraries (correct API vs stale/absent)

| model | datetime | Arrow | kaml |
|---|---|---|---|
| gemma-3-270m | unclear → **correct** | unclear | unclear |
| gemma-3-1b | unclear | unclear → **correct** | unclear |
| gemma-4-e4b | **stale → correct** | stale → unclear | correct |
| gpt-oss-20b | **stale → correct** | **stale → correct** | correct |
| devstral-small-2-24b | **stale → correct** | unclear → **correct** | correct |
| qwen3-coder-30b-a3b | **stale → correct** | **stale → correct** | **unclear → correct** |
| nemotron-3-nano-30b-a3b | unclear → **correct** | unclear → **correct** | unclear |
| qwen3-32b | **stale → correct** | stale → unclear | **unclear → correct** |
| llama-3.3-70b | **stale → correct** | **stale → correct** | **unclear → correct** |

This is the lift the frontier arm could **not** show. Current frontier agents produced
the correct API in condition A for all three libraries (zero lift). **Local models are
stale**, and the codex corrects them: gpt-oss-20b, unaided, wrote the *removed*
`kotlinx.datetime.Instant` and Arrow's *removed* `Validated`, and the entry corrected
both to the current `kotlin.time.Instant` / `zipOrAccumulate`. The `unclear` cells are
the model failing to emit valid API code at all — a competence limit, not the codex —
and cluster at 270M–1B, where real-library code is beyond the model regardless of the
entry. So the drift lift is the **capability-axis analogue** of the older-model result:
the smaller or weaker the model, the wider its gap to the classpath, and the more the
codex is worth. Arrow is the hard case even *with* the entry — the 4B was told to use
`zipOrAccumulate` and still hand-rolled an `Either`, because the idiom is beyond it;
gpt-oss-20b, capable enough, took the correction.

**Qwen3-Coder-30B-A3B is the flagship rung, and it corrects all three (3/3).** It is the
model a developer would actually run locally — a 30B MoE, *coding*-tuned — and it
*refutes* the tempting guess that code-tuning makes a local model already-current:
unaided it wrote the removed `kotlinx.datetime.Instant`, Arrow's removed `Valid`/`Invalid`
(the `Validated` constructors), and fumbled kaml — **stale or wrong on every one** — and
the entry corrected all three. So the codex's value on real libraries is not a
weak-model artifact; it holds for the strongest local coder tested, because its training
still predates or misses those specific API states. Served through **mlx-lm** (Apple's
MLX, `run-mlx.sh`) it ran at **~2 s per generation** — MoE (~3B active) on the native
Apple-silicon path — making it both the most capable *and* the fastest local rung.

**Devstral-Small-2-24B (Mistral) replicates this cross-family.** A different vendor's
code-tuned model, dense 24B, was likewise stale unaided — removed `kotlinx.datetime`,
and a hand-rolled Arrow `Either` that is neither the current `zipOrAccumulate` idiom nor
the old `Validated` — and the entry corrected both (kaml it already knew). So the
"code-tuned but stale, corrected by the entry" result is not a Qwen quirk: it holds for
two independent coding models from two vendors. (Dense 24B ran ~11 s/generation on MLX —
slower than the MoE rungs but still practical.)

**NVIDIA-Nemotron-3-Nano-30B corrects 2/3, with a tooling gotcha worth recording.** In
its *default* (thinking-on) mode via mlx-lm it reasoned past the 5120-token budget and
emitted **no answer at all** (`finish_reason: length`, empty content) on the harder
tasks — its synthetic still flipped 0→4/4 (short answers fit) but external `arrow`/`kaml`
came back empty. Its documented **`detailed thinking off`** system prompt fixed it
(bounded, `finish_reason: stop`); applied equally to A and B so the delta holds. With
thinking off it corrects **datetime and Arrow** (both `unclear → correct`; unaided it
reached for JDK `java.time` on datetime), and near-misses kaml (`Yaml().default` for
`Yaml.default`). The lesson generalises: some local models need their reasoning toggle
set explicitly or they never produce usable output — a real operational detail for a
personal setup.

**Qwen3-32B (dense) begins to answer the "does the lift vanish at scale?" question — and
so far it does not.** At 32B — the largest rung yet, and *not* code-tuned — the model was
**still stale** unaided (`kotlinx.datetime.Instant`; Arrow imports), and the codex
corrected datetime and kaml cleanly. Arrow was a *mixed* partial: with the entry it
imported the correct `zipOrAccumulate`/`either` **and** the removed `Validated` together
(scored `unclear`) — the entry surfaced the right idiom but the stale habit bled through.
The headline stands: **scaling to 32B did not make a local model current on these
libraries.** (Dense 32B + thinking mode needed `/no_think` and ran ~14 s/generation;
downloaded via `hf` with Xet high-performance, much faster than the LM Studio fetch.)

### Practical ceiling of the test machine

Run on a 64 GB Apple-silicon laptop. **Dense** reasoning models (12B/27B and up) generate
~100 s per answer here (a large reasoning budget × dense compute), too slow to sweep at
scale; **MoE** models on the native MLX path run at small-model speed and are the
tractable capable rung — gpt-oss-20b and Qwen3-Coder-30B-A3B (both ~3B active) swept in
seconds via **mlx-lm**, which was markedly faster than the dense builds; the dense rungs
(24B, 32B) run at ~11–14 s/generation, the dense 70B slower still. The nine rungs above
span **270M–70B (~260×)** — the top of what this machine can run. The large downloads
that once out-crawled the LM Studio fetch were recovered with **`hf` + Xet
high-performance** (Qwen3-32B via mlx-lm, Llama-3.3-70B GGUF via LM Studio) — much faster
than the throttled LM Studio path.

**The scale question is now answered: the lift does *not* vanish, even at 70B.** The
biggest, most capable local model this box can run — **Llama-3.3-70B** — was *still
stale* on all three real libraries unaided (removed `kotlinx.datetime.Clock/Instant`, a
stale Arrow approach, a fumbled kaml) and the codex corrected **3/3**. So the frontier
null does **not** reproduce at local scale anywhere in the 270M–70B range: every local
model tested, from a 270M up to a dense 70B, was stale on these version-pinned libraries,
and the entry made it current. The codex's value tracks the model's **training gap to the
classpath**, not its parameter count — a big local model is not automatically current on
a specific library version. (The frontier agents showed zero lift only because they had
*already* learned these exact APIs — a property of their freshness, not their size.)

## Retrieval — disambiguation A/B (2026-08-20)

The first cut of *retrieval* (not the one relevant entry inlined, but the **whole
catalogue** in front of the agent): 4 real capabilities + **4 opaque-named distractors**
with near-identical signatures, and per task the agent must pick the **correct** symbol.
**Rbare** = signatures only (syntactic face); **Rrich** = full entries adding
capability / not-for / category / triggers (semantic face). Verdict per cell: correct |
wrong (picked the distractor) | both | reinvent. Prompts `build-retrieval.py`, scoring
`sweep-retrieval.sh`. (This is disambiguation *within a presented set* — search-at-scale
over hundreds via the index is still ahead.)

Cells are **Rbare → Rrich**:

| model | retry | cache | debounce | parse *(identical sigs)* |
|---|---|---|---|---|
| Claude (frontier) | correct→correct | correct→correct | correct→correct | correct→correct |
| Gemini (frontier, agy) | correct→correct | correct→correct | correct→correct | correct→correct |
| gpt-oss-20b | correct→correct | correct→correct | correct→correct | **reinvent→correct** |
| qwen3-coder-30b | correct→correct | correct→correct | correct→correct | **wrong→correct** |
| devstral-24b | correct→correct | correct→correct | correct→correct | correct→correct |
| llama-3.3-70b | correct→correct | correct→correct | **both→correct** | **both→correct** |

**The semantic face's payoff concentrates exactly where the syntactic face
underdetermines.** Where the distractor differs by a constructor parameter (retry
`times/factor` vs `perSecond`; cache `maxEntries` vs `ttlMs`; debounce `quietMs` vs
`intervalMs`), the **bare** catalogue disambiguates alone — no prose needed (all correct
in Rbare bar one). Where the signatures are **identical** (parse: `RowReader` and
`Shredder` are both `(delimiter: Char)` / `read(text): List<List<String>>`), Rbare is
**unreliable** — across six subjects it split correct / reinvent / wrong / both (only
3/6 correct, and **every failure was a local model**) — and the **semantic prose made all
six correct**.

Two sharper points:
- **Bare can mis-select, not just hedge.** qwen3-coder-30b, on identical signatures,
  *confidently picked the wrong capability* (`Shredder`, the naive splitter) — worse than
  reinventing, since it would ship wrong behaviour; llama grabbed *both*. The `not for:
  quoted fields` line corrected them. The semantic face is what prevents an active wrong
  pick, not merely a hedge.
- **Model-dependent, same shape as content-value.** Both frontier models (Claude *and*
  Gemini) and one strong local (Devstral) resolved even the identical-signature case via
  name priors; the other locals needed the prose — the disambiguation lift is a
  weaker/local-model phenomenon, just as content-value's drift lift was. (Caveat: the opaque names are not perfectly neutral — `Shredder`
  *sounds* naive next to `RowReader` — so bare disambiguation can ride on name connotation
  too; the prose is the reliable disambiguator regardless.)

Together with content-value this completes **RAD-0013's two-faced entry, measured**: the
**syntactic face drives use** and disambiguates where signatures differ; the **semantic
face is required for disambiguation where signatures collide, and prevents active
mis-selection**. See RAD-0017.

## Selection A/B — which overlapping library the project prefers (2026-08-20)

Real overlapping libraries (JSON **Moshi** vs kotlinx.serialization; HTTP **Ktor** vs
OkHttp; assertions **Strikt** vs AssertJ), where the point is overriding a training-default
*habit*. Four conditions isolate the **dependency tree's** own power from the **codex's**:
**A** = task alone; **dep1** = tree lists only the preferred lib; **dep2** = tree lists
*both* (habit first — ambiguous classpath); **dep2pref** = dep2 + the project's authored
standard. Prompts `build-selection.py`, scoring `sweep-selection.sh`. Cells **A / dep1 /
dep2 / dep2pref**:

| model | json | http | assert |
|---|---|---|---|
| Claude (frontier) | other/pref/**other**/pref | other/pref/**other**/pref | other/pref/**other**/pref |
| Gemini (frontier) | other/pref/**other**/pref | none/pref/**other**/pref | other/pref/**other**/pref |
| gpt-oss-20b | other/pref/**other**/pref | other/pref/**other**/pref | other/pref/**other**/pref |
| qwen3-coder-30b | other/pref/pref/pref | other/pref/pref/pref | other/pref/mixed/pref |
| devstral-24b | other/pref/**other**/pref | none/pref/pref/pref | other/pref/mixed/mixed |
| llama-3.3-70b | other/pref/**other**/pref | other/pref/mixed/pref | other/pref/mixed/pref |

Aggregate over 18 model×domain cells: **A preferred 0/18** (no model picks the project's
lib on its own) → **dep1 17/18** (the dependency tree redirects) → **dep2 3/18** (the
ambiguous classpath fails) → **dep2pref 17/18** (the authored preference resolves it).

**Selection is the gap model progress cannot close.** Content-value's drift lift closes
with model *freshness*; disambiguation closes with model *capability*. Selection closes
with **neither** — on an ambiguous classpath **both frontier models reverted to habit
exactly like a 20B did**, and only the project's recorded standard flipped them. Which
library *this* project sanctions is **local knowledge no model can have, however capable
or current** — the most durable place the codex earns its keep. The dependency tree does
the single-choice selection (A→dep1); the codex's unique job is the ambiguous classpath
(dep2→dep2pref) that the tree cannot resolve. See RAD-0018.
