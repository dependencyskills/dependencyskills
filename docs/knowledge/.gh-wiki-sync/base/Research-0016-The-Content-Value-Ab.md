# The Content-Value A/B

RAD-0016 · 2026-08-19 · v2

**Design; measured.** This record specifies the experiment; a content-value sweep, a
doc-level gradient, a three-library external test, and a **local-model ladder** have all
run (2026-08-19/20; see Findings). Retrieval and selection remain.

**Pinned (all public).** Frontier models: **Claude Opus 4.8** (via Claude Code
subagents), **Gemini 3.7 Flash** and **Gemini 3.1 Pro** (via Antigravity `agy`).
Local models (deterministic decode): **gemma-3-270m**, **gemma-3-1b**, **gemma-4-e4b**
(4B), **openai/gpt-oss-20b** (via LM Studio); **Devstral-Small-2-24B-Instruct-2512-4bit**,
**Qwen3-Coder-30B-A3B-Instruct-4bit**, **NVIDIA-Nemotron-3-Nano-30B-A3B-4bit**, and
**Qwen3-32B-MLX-4bit** (via mlx-lm on Apple MLX), and **Llama-3.3-70B-Instruct** (GGUF
Q4_K_M, via LM Studio). Libraries: **kotlinx-datetime 0.7.0**,
**Arrow 2.0**, **kaml 0.104.0**. All are public products and versions, named and pinned
openly here for reproducibility. The only things ever withheld are **private /
unpublished** projects and libraries (first-party code on a maintainer's or tester's
machine) — public coordinates are always named.

## Question

Everything upstream — get, read, parse, store, query — is machinery. This is the
thesis test: **does the codex change what an agent does?** Given a coding need,
does an agent *find and use* an existing capability instead of reinventing it,
mis-selecting, or reaching for a stale one — and does the codex move that? If it
does not, the premise is wrong, and RAD-0010/0011 both name this as the cheapest
thing worth testing first.

## Trail

### The training-exposure confound, and why the synthetic fixture is the subject

The agent already knows popular libraries from training. Test the codex with a real
library and the control condition already succeeds — no lift, a false negative. The
project's own claim is that value runs *inversely to training exposure*, so the
measurement must use capabilities the model has **not** seen.

The `experiments/test0` fixture solves this by construction: `test0.l3.retryWithBackoff`
is **invented**, so exposure is provably **zero** — and zero *uniformly across every
model*, since no model can have trained on an API that does not exist. That is why a
synthetic subject is not a weakness here but the whole point: it pins the training
variable to zero, so any behavioural lift is cleanly the codex's.

### Content value first; retrieval later

Two questions hide in "does the codex help":

- **Content value** — *given* the entry, does the agent use the capability?
- **Retrieval value** — does the agent *find* the entry among hundreds?

The first A/B tests **content value only**: the entry is placed in front of the
agent, no search layer. That isolates "is the entry good enough to change
behaviour" — what test0's graded entries were built to answer — and needs none of
the embedding/Lucene machinery. Retrieval is a separate, later A/B (the selection
dimension).

### The conditions, and the two scenarios

Same task, same agent; the codex entry the only difference.

- **A (control):** the task, with the capability **available** but no codex entry.
- **B (treatment):** identical, **plus the codex entry** at doc level *L*.

But where the capability *lives* dominates whether the agent finds it, so **scenario
is a dimension**:

- **First-party** — the source is **in the project tree** (loose files the agent can
  read/grep). Control A here is RAD-0015's *proximity*: the code is right there — does
  the agent consult it, or ignore it and reinvent? The codex must beat proximity.
- **Third-party** — the capability is a **dependency** (a jar; source not browsable).
  Control A: the agent has the dep but cannot see the source — the classic "reinvents
  what it can't see." The codex is likely the *only* thing that surfaces it, so the
  predicted lift is larger.

test0's fixture serves both unchanged: loose source today (first-party), and packaged
as a jar dependency (third-party) — same capabilities, source no longer in the tree.

### The runner: developer tools, not model APIs

The A/B runs real coding agents in their real environments — **Claude Code**
subagents and the **Antigravity `agy`** CLI (headless `-p`) — not raw model API
calls. That is a methodology decision in its own right, recorded as
[ADR-0010](../adr/0010-measure-through-developer-tools.md): the agent-plus-tools loop
is what the codex actually lives in, so measuring it is more honest than a synthetic
API prompt — and it needs no API accounts.

**Model is therefore a dimension too.** The result matrix is `model × scenario ×
level × naming × condition`, and the headline is the **within-cell lift (B − A)**,
read for two things: does it hold **across models** (Claude and Gemini/Antigravity),
and is it **larger in the dependency scenario** than the first-party one? A lift that
holds across vendors is the strongest form of the result — it says the codex is the
vendor-neutral artifact the project claims, not a Claude quirk.

### Scoring

Two layers per output: an objective **symbol-match** (did it call the expected
symbol?) and an **LLM judge** (used correctly / reinvented / partial). `agy`'s
`--json-schema` gives structured output on the Gemini side; a subagent judges on the
Claude side.

### Breadth is a prerequisite

The fixture has **one** capability today (retry). One capability is far too few
tasks to read a lift from — the numbers would be noise. A meaningful run needs
**~4–6 capabilities** across the same ladder, so **test0 breadth comes before the
full A/B**. A single-cell smoke test (below) validates the runner before that
investment.

## Findings

**Reasoned (design).**

- The **synthetic subject** pins training exposure to zero, uniformly across models —
  the design's load-bearing move.
- **Content value first** (inline entry), retrieval later.
- **Scenario** (first-party vs third-party) is a first-class dimension; so is **model**.
- The runner uses **developer tools** (ADR-0010), making model a dimension and the
  result agent-agnostic.

**Measured — first sweep, 4 capabilities × 2 models (2026-08-19).** Four invented,
opaque-named capabilities (retry `Policy`, LRU cache `BoundedStore`, debounce
`Coalescer`, delimited parse `RowReader`), third-party scenario, condition A (no
codex) vs B (a rich codex entry), on a Claude subagent and Antigravity `agy`
(Gemini 3.7 Flash). Result: **without the codex 0/8 used the capability; with it,
8/8** — a complete flip, identical on **both** models across all four capabilities.
Every control (A) reinvented with competent stdlib/coroutines code and *refused to
guess* the unknown API, naming the missing docs/API as what it lacked — the
project's premise, from the baseline, replicated eight times. (Runner detail: the
Claude arm ran as in-session subagents; `agy -p` needed a pseudo-TTY via `script`,
`--new-project`, and `--dangerously-skip-permissions` — reusable run scripts and the
gotcha are in `experiments/test0/measurement/`.)

**Measured — doc-level gradient, bare vs rich (2026-08-19).** The same four
capabilities with a **bare** entry (symbol + signature only, no capability prose)
added between A (nothing) and B-rich (full entry). Use-rate: **A 0/8 → bare 7/8 →
rich 8/8.** Two findings:

- **The signature carries most of the use.** A bare entry — no "what it's for"
  prose, just the symbol and signature inlined — already flips 7/8 (vs 0/8 with
  nothing). For *content* value (the entry in front of the agent), the **syntactic
  face does the work**; rich prose is not what makes the agent reach for the capability.
- **The prose earns its keep at the margin, and model-dependently.** The one bare gap
  was `parse`: the task needed quoted-field handling the bare
  `RowReader(delimiter)`/`read(text)` signature does not *guarantee*. **Claude refused
  to assume it and reinvented; Gemini-flash assumed it and used it.** The rich entry's
  capability line ("respects quoted fields") closed the gap (8/8). So the semantic
  prose matters where the signature cannot establish fitness for the task — and a
  *cautious* model needs it more than a *trusting* one, which uses a bare entry even
  when it may not fit.

This sharpens the two-faced entry (RAD-0013): the **syntactic face drives use**; the
**semantic face drives discovery and disambiguation-of-fitness**, not use per se.

**Measured — external validity, real libraries (2026-08-19).** Three version-pinned
axes: **kotlinx-datetime 0.7.0** (drift — the `Instant` move), **Arrow 2.0** (habit —
the removed `Validated`), **kaml 0.104.0** (sparse — 4% KDoc, entry *harvested* from
the real `-sources.jar`). Condition A vs B, Claude subagents and Gemini 3.7 Flash.
**Result: zero lift on the current
models (recovered on an older one — see the drift bullet).** Both current models, all
three libraries, condition A **already produced the correct current API** — `kotlin.time.Instant` (not stale
`kotlinx.datetime.Instant`), Arrow 2.0's `zipOrAccumulate`/`either` (not the removed
`Validated`), kaml's `Yaml.default.decodeFromString`. The codex changed nothing.

**This is the decisive, deflationary finding, and the boundary of the thesis.** The
content-value lift test0 showed on *synthetic* capabilities (0→8/8) **does not
transfer to real, well-known libraries**: current models — even a flash-tier one —
already know their APIs from training. Three lessons:

- **Doc coverage ≠ training exposure.** kaml has 4% KDoc, yet both models knew its API
  cold — learned from *code*, not docs. Sparse docs did not make it unknown.
- **The drift null was a cutoff artifact — and reversing it confirms the value.** Both
  *current* models had learned the 0.7.0 `Instant` move. Re-run on an **older model
  (Gemini 3.1)**: condition A produced the **stale** `kotlinx.datetime.Instant`, and
  the codex entry **corrected it to `kotlin.time.Instant`** — the drift lift,
  demonstrated. So version/drift value is **real**, gated on the model being stale
  relative to the pinned version (an older/smaller model, a lagging one, or a change
  after the model's cutoff). *(Demonstrated on one vendor — an older, stale Claude was
  not reachable in-session, so cross-vendor confirmation is pending.)*
- **Content value is confined to what the model genuinely does not know:**
  first-party / private / internal code (absent from any corpus — RAD-0015's
  emphasis), genuinely new or post-cutoff libraries, and weaker/older models. For a
  public dependency a current model already knows, the codex adds nothing *to using it*.

**Measured — local models, the ladder (2026-08-20).** The predicted "weaker/older
models" case, made concrete: the same A/B run against four small models served locally
through LM Studio as a raw chat completion — so A is the task alone and B is the task
with the entry pasted in, the pure content axis with no retrieval confound (runner
`experiments/test0/measurement/run-lmstudio.sh`; ladder and load params in
`local-models.md`). Two results:

- **The synthetic flip is universal from ~1B up.** Use-rate B: gemma-3-270m **3/4**,
  gemma-3-1b **4/4**, gemma-4-e4b (4B) **4/4**, gpt-oss-20b **4/4**, Devstral-24B **4/4**,
  Qwen3-Coder-30B **4/4**, Nemotron-3-Nano-30B **4/4**, Qwen3-32B **4/4**, Llama-3.3-70B
  **4/4** — against **0/4** in A for every one. The codex redirects a *local* model
  exactly as it does a frontier one. The single 270M miss is the model emitting broken
  Kotlin, not ignoring the entry: below a competence floor the bottleneck is the
  **model**, not the index — the codex can only help a model able to act on it.
- **The drift lift the frontier arm could not show, local models show plainly.** On the
  real libraries where *current* frontier models had zero lift, the local models are
  **stale**, and the entry corrects them: gpt-oss-20b, unaided, wrote the removed
  `kotlinx.datetime.Instant` *and* Arrow's removed `Validated`, and the entry corrected
  **both** (`stale → correct`); the 4B corrected on datetime. The failures that remain
  are competence, not the codex — 270M/1B cannot emit valid real-library code either way
  (scored `unclear`), and Arrow's `zipOrAccumulate` idiom defeated the 4B *even when
  told*. So the drift result generalises from the **time axis** (an older Gemini) to the
  **capability axis** (a smaller model): the codex is worth the **gap between the model's
  knowledge and the classpath**, and a small local model has the widest gap.
- **Code-tuning does not close the gap — the flagship rung corrects all three.**
  Qwen3-Coder-30B-A3B, the model a developer would actually run (a 30B MoE, *coding*-tuned),
  was **stale or wrong on every real library** unaided — removed `kotlinx.datetime`,
  Arrow's removed `Valid`/`Invalid`, a fumbled kaml — and the entry corrected **3/3**
  (`stale/unclear → correct`). So the real-library value is not a weak-model artifact; it
  holds for the strongest local coder tested, whose training still predates or misses
  those specific API states. It is also the fastest rung: MoE (~3B active) on Apple MLX
  via mlx-lm, ~2 s per generation. **Devstral-Small-2-24B (Mistral) replicates it
  cross-family** — a second vendor's code-tuned model, dense 24B, stale unaided on
  datetime and Arrow and corrected on both (kaml it already knew) — so the finding is not
  a Qwen quirk but holds across two independent coding models from two vendors.
  **NVIDIA-Nemotron-3-Nano-30B (a *third* vendor, a general MoE) corrects datetime and
  Arrow too** — stale/JDK-defaulting unaided, current with the entry — extending it
  beyond code-tuned models. It carries an operational caveat worth its own note: in its
  default reasoning mode via mlx-lm it reasoned past the token budget and returned *empty*
  on the harder tasks; its `detailed thinking off` system prompt (applied equally to A and
  B) was needed for any usable output — a reminder that a personal model may need its
  reasoning toggle set explicitly before it produces anything at all.

This is the sharpest form of the thesis for a personal setup: a genuinely runnable
local model — from a few billion parameters up to the best local coder — made to *use
real, current APIs* by an entry it can read. **Practical ceiling of the test machine:**
on a 64 GB Apple-silicon laptop, dense reasoning models generate ~100 s per answer; the
tractable capable rungs are MoE models on the MLX path (gpt-oss-20b, Qwen3-Coder-30B —
seconds per answer via mlx-lm), the dense 24B/32B/70B slower (~11 s to minutes). The large
downloads that out-crawled the LM Studio fetch were recovered with **`hf` + Xet
high-performance** (Qwen3-32B via mlx-lm, Llama-3.3-70B GGUF via LM Studio). The ladder
tops out at **70B — the largest this box can run — and the scale question is answered:
the lift does *not* vanish.** Llama-3.3-70B, the biggest and most capable local rung, was
*still stale* on all three real libraries unaided (removed `kotlinx.datetime.Clock/Instant`,
a stale Arrow approach, a fumbled kaml) and the codex corrected **3/3**. So the frontier
null does not reproduce anywhere in the **270M–70B** local range: every model tested was
stale on these version-pinned libraries and the entry made it current. The codex's value
tracks the model's **training gap to the classpath, not its parameter count** — a large
local model is not automatically current on a specific library version; the frontier
agents showed zero lift only because they had already learned these exact APIs (freshness,
not size).

**What this does not close.** It tested *using a known API* (content value, inlined).
It did **not** test **selection** — which of several overlapping libraries this
project reaches for, at which version, and why not the others (RAD-0007) — which is
*local* knowledge no model has, however well it knows each library's API. Selection
and retrieval are the open frontier where value may still live for known libraries.

**Further potential — the codex as training data, not only a runtime index.** This
experiment showed models learn library APIs from *code* (kaml at 4% docs, known
cold). That cuts both ways: a clean, current, **version-matched** codex is exactly
the high-signal corpus that scattered, stale, incidental code is not. So the same
harvest→entry pipeline has a **second consumer — model training** — where feeding the
codex in would **shrink the knowledge gap at its source** (fewer stale-API defaults,
real coverage of thin-doc libraries). It also reframes the contamination worry: for
the codex itself, leakage into training is the *point*. Runtime retrieval closes
*today's* gap for the private/stale/novel cases; training closes *tomorrow's* gap for
everyone. Speculative and a separate line of work — noted here because it follows
directly from the "learned from code" finding.

**What this establishes, and what it does not.** It establishes **content value**: a
codex entry, once placed in front of the agent, reliably redirects it from
reinventing to using an existing capability — robustly and vendor-neutrally. It does
**not** yet establish (a) **retrieval** — the entry was inlined, not found among many
(the selection dimension); or (b) **external validity** — synthetic capabilities have
zero training exposure *by design*, so a real, half-known library might already
succeed in condition A (a smaller lift). The result is the necessary condition met
cleanly, not the whole thesis.

**To measure.**

- **Retrieval value** — the entry *found among many*, not inlined (the selection A/B).
- **Real libraries — external validity, three axes, run in sequence.** Does the lift
  survive when the baseline already half-knows the API?
  - **kotlinx-datetime** — version drift (the `Instant` move to the stdlib at 0.7.0):
    tests version-matching.
  - **Arrow** — ingrained deprecated patterns (`Validated`, old DSL) surviving despite
    "knowing" 2.0.
  - **kaml** — sparsely documented, **measured at 4% KDoc coverage** (11 of 290
    declarations), so the harvested entry is near-pure signature: the realistic
    thin-entry case, and a direct test of the gradient's "syntactic face carries use."

  Coverage measured with the new `experiments/cost-model/scripts/kdoc-coverage.py`,
  which also reproduced RAD-0011's ~35% for kotlinx-datetime (method validated).

**Predicted (from test0 v4, to be confirmed or refuted).**

- Lift rises with doc level; **L0 ≈ A** (no capability text to match); **L2/L3 ≫ A**.
- Opaque names depress use at low levels (the name gives nothing, the doc must carry it).
- Lift **larger in third-party** than first-party (proximity partly substitutes for
  the codex).
- Lift **holds across both models** if the thesis is right.

## Recommendation

**Build the content-value A/B as specced** — synthetic subject, content-first, A1
codex-vs-proximity, `model × scenario × level × naming`, developer-tool runner,
symbol-match + judge scoring. **Smoke-test the runner on one cell first;** add
**test0 breadth** before a full run. Retrieval value is a later, separate A/B.

**What would change the answer.** If even the third-party, rich-doc cell shows **no
lift**, the premise is in trouble — the codex does not change behaviour and the
project's core claim fails. That is the point of running it.

## Connections

- [ADR-0010](../adr/0010-measure-through-developer-tools.md) — the runner decision
  (developer tools, not API) this depends on.
- RAD-0009 v4 — the test0 first pass whose data sets the predictions.
- [RAD-0011](0011-existing-documentation-systems-as-skill-content.md) — the
  content-value question this operationalises.
- [RAD-0015](0015-how-the-source-is-read.md) — first-party proximity and the read
  layer that packages the third-party scenario.
- [RAD-0007](0007-choosing-between-overlapping-libraries.md) — the selection failure a
  later retrieval A/B would target.
- [RAD-0001](0001-cost-of-a-skill-per-dependency.md) — the cost the codex must justify.
