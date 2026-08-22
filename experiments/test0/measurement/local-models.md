# Local-model arm — the ladder and how it is loaded

The Claude and Gemini arms answer "does the codex change a *frontier* agent's
behaviour." This arm asks the sharper question for a personal setup: **can the codex
make a small, locally-run model behave well enough to actually use?** The thesis
predicts it helps *most* here — the codex is worth the gap between what the model
knows and what is on the classpath, and a small local model has the widest gap.

The instrument is a raw chat completion against an OpenAI-compatible endpoint
(`run-lmstudio.sh` for LM Studio, `run-mlx.sh` for mlx-lm), not an agent. That is
deliberate: the prompts are self-contained ("you cannot browse its source"), so
condition A is the task alone and condition B is the task with the codex entry pasted
in — the pure content-value axis, with no retrieval or filesystem confound.

**Test machine.** Apple **M5 Pro, 64 GB** unified memory, **macOS 26.5.2**. This is
load-bearing for the "practical ceiling" claims below — which models are too slow, which
fit, and where the ladder tops out are all properties of *this* box. A larger machine
would reach further up; a smaller one (an 18 GB M3, say) tops out far lower.

## The ladder (as run)

Nine rungs spanning **270M–70B (~260×)** across six model families (Google, OpenAI,
Mistral, Qwen, NVIDIA, Meta/Llama). The plan evolved with what the machine could
actually do: micro rungs were added at the bottom to find the *floor* (does the codex
still help a model too small to write valid code?), the dense mid rungs (gemma-4-12b,
qwen3.6-27b) were **dropped** — reasoning models generating ~100 s per answer, too slow
to sweep — and a fast, coding-tuned MoE (Qwen3-Coder-30B) was added as the capable top,
served through mlx-lm. The large *dense* rungs (32B/70B) exceeded reliable local download
(throttled at the source), so the ladder tops out at 30B: the practical ceiling of a
64 GB Apple-silicon laptop.

| Rung  | Model                              | Params        | Runtime  | Role |
|-------|------------------------------------|---------------|----------|------|
| floor | `google/gemma-3-270m`              | 270M          | LM Studio| the competence floor — can the codex help at all? |
| micro | `google/gemma-3-1b`                | 1B            | LM Studio| smallest model that acts on the entry cleanly |
| small | `google/gemma-4-e4b`               | 4B            | LM Studio| widest useful gap |
| mid   | `openai/gpt-oss-20b`               | 20B (MoE ~3B) | LM Studio| capable, fast MoE rung |
| top   | `Devstral-Small-2-24B-Instruct-2512-4bit` | 24B    | mlx-lm   | Mistral's coder — cross-family check |
| top   | `Qwen3-Coder-30B-A3B-Instruct-4bit`| 30B (MoE ~3B) | mlx-lm   | best local coder — is a code-tuned model already current? |
| top   | `NVIDIA-Nemotron-3-Nano-30B-A3B-4bit` | 30B (MoE ~3B)| mlx-lm  | a general (non-code) MoE from a third vendor |
| top   | `Qwen3-32B-MLX-4bit`               | 32B (dense)   | mlx-lm   | dense 32B — does scaling up close the gap? (needs `/no_think`) |
| top   | `Llama-3.3-70B-Instruct` (GGUF Q4_K_M) | 70B (dense) | LM Studio | the ceiling — largest this machine runs |

**Two runtimes, one harness.** LM Studio and mlx-lm both expose the same
OpenAI-compatible API, so the runners are interchangeable: `run-lmstudio.sh` hits
`:1234`, `run-mlx.sh` hits `mlx_lm.server` on `:1235` (Apple's MLX — the fastest path on
this hardware, and what the 30B coder ran on). Any runner also works against Ollama or
`llama-server` by pointing `LMS_ENDPOINT`/`MLX_ENDPOINT` at them.

## Load parameters (identical across rungs)

Chosen so that any A-vs-B difference is attributable to the injected codex content and
not to sampling noise or a distorted output distribution.

| Parameter        | Value | Why |
|------------------|-------|-----|
| temperature      | 0     | greedy / deterministic — removes run-to-run variance from the A/B |
| seed             | 0     | pins any residual nondeterminism |
| top_p            | 1.0   | neutral — no nucleus truncation |
| top_k            | 0 (off) | neutral |
| repeat penalty   | 1.0   | neutral — do not distort the base distribution |
| context length   | 8192  | prompts are small; generous headroom for the codex entry + code |
| max output tokens| 5120  | several of these are reasoning models — the answer shares the budget with a `reasoning_content` channel, so it needs headroom or the code gets truncated (`finish_reason: length`) |
| GPU offload      | max   | every rung fits in 64 GB unified memory |
| flash attention  | on    | — |

Temperature, seed, top_p and max tokens are set per request by the runner; context
length and GPU offload are set at load time. mlx-lm serves a single loaded model and
largely ignores the request's `model` field, so under it the label is only a filename.

## Running it

LM Studio rungs:

```bash
LMS=~/.lmstudio/bin/lms
"$LMS" server start
"$LMS" load openai/gpt-oss-20b --context-length 8192 --gpu max -y
./sweep.sh          lmstudio openai/gpt-oss-20b     # synthetic content-value A/B
./sweep-external.sh lmstudio openai/gpt-oss-20b     # real-library drift A/B
"$LMS" unload openai/gpt-oss-20b
# likewise: gemma-3-270m, gemma-3-1b, gemma-4-e4b
```

mlx-lm rung (Apple MLX — fastest here; reuses the weights LM Studio already downloaded):

```bash
MODELDIR=~/.lmstudio/models/lmstudio-community/Qwen3-Coder-30B-A3B-Instruct-MLX-4bit
mlx_lm.server --model "$MODELDIR" --host 127.0.0.1 --port 1235 &   # unload LM Studio models first

export MLX_MODEL="$MODELDIR"                        # payload model (a path); label stays clean
./sweep.sh          mlx qwen3-coder-30b-a3b
./sweep-external.sh mlx qwen3-coder-30b-a3b
```

**Reasoning toggle.** Some models reason by default and will run to the token cap
without emitting an answer (`finish_reason: length`, empty content) — NVIDIA Nemotron
does this via mlx-lm. Set its toggle with `MLX_SYSTEM`, applied identically to A and B so
the delta stays valid:

```bash
export MLX_SYSTEM="/no_think detailed thinking off"   # e.g. for NVIDIA Nemotron
./sweep.sh          mlx nemotron-3-nano-30b-a3b
./sweep-external.sh mlx nemotron-3-nano-30b-a3b
```

Synthetic sweep scoring is by opaque-symbol presence (`sweep.sh`, shared with the
agent arms). Real-library scoring is correct-API presence (`sweep-external.sh`),
since the point there is drift — did the model reach for the current API or a stale
one. Results land in `results/`.
