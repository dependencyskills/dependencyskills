# The summariser

Library documentation is rewritten into one factual sentence in a caller's words, locally, before
anything reaches an agent. It is the retrieval layer and the quarantine at the same time, and those
two jobs are the same mechanism rather than two components stacked.

```
uv run python summarise.py --self-test                    # verification, no model needed
uv run python summarise.py --sample 20                    # summarise 20 real entries
uv run --with mlx-embeddings python summarise_corpus.py   # phase 1 — the 220-entry slice
uv run --with mlx-embeddings python eval_recall.py        # phase 2 — does it retrieve?
```

## Why a rewriter and not a filter

**A filter has to be right; a rewriter does not.** Every doc comment is processed identically, so
there is no classification to get wrong. A payload this component fails to *notice* is still
rewritten, because noticing was never part of the mechanism.

That distinction is the whole reason this design survived when the detection work did not.
[`test8`](../test8/) measured detectors and they failed; surface reduction has worked every time it
was tried. This is surface reduction — the third-party text never reaches the agent, so its content
stops mattering.

[`test7`](../test7/) measured the arm directly: a tool-less paraphraser in front of the agent
stopped a planted credential leaking (**0 of 3**) while the developer's task still completed
(**2 of 3**) — the same result as sending the agent no prose at all.

## Four properties

Each answers a measured failure rather than a hypothetical one.

| | property | what it answers |
|---|---|---|
| 1 | **No network, no tools** | a summariser that cannot reach anything cannot be made to exfiltrate, whatever the input says. A property of the deployment, not of the prompt |
| 2 | **Input is data, never instruction** | RAD-0025 measured the summariser as an attack surface. Necessary — and `test6`/RAD-0006 both measured that framing alone is *not sufficient*, which is why it is one of four |
| 3 | **The output shape is constrained** | if the output cannot express an imperative, a subverted summariser still cannot emit one. RAD-0026's question applied to what we generate |
| 4 | **The output is verified, and failure falls back** | we control the generator, so checking our own output is cheap. Rejection degrades to **signature-only** — measured sufficient to *use* a capability (`test0`, 7 of 8) and as a working control (`test7`, 0 of 3 harm, 2 of 3 task) |

Property 4 follows from the posture the project settled on: **assume something gets through.**
Verification is not trusted to be right, only to be *conservative*, and its failure lands on a state
already measured as safe. The fallback is not a failure mode — it is the safe state.

## What it does not defend against

Everything above answers an injected **instruction**. It does nothing about a **fabricated
capability**: honest-looking, non-imperative prose describing something the library does not do,
competing for retrieval on merit. `test6` measured a fabricated library beating the true answer
**4 of 17**. A rewriter has no purchase here — nothing is malformed, so the summariser faithfully
rewrites a lie into a well-formed capability description.

The candidate answer is resolution — does the described capability exist on the declared surface —
which [`test14`](../test14/) priced for directives and which has never been tried on capability
claims. See RAD-0037 §1.

## The model is pinned, not defaulted

`test7`'s result — the one this component rests on — was measured on **qwen3-coder-30b**. Running
the same design on a different model produces a different component whose behaviour is unmeasured,
so the model is part of the specification. `SUMMARISER_MODEL` overrides it and the harness says
loudly that the run is no longer comparable.

A reasoning model's scratchpad is **discarded rather than parsed**. That scratchpad is where an
injected instruction would be reasoned *about*; only the committed answer is a candidate for the
index.

## Verification, and why the self-test exists

`--self-test` runs the verifier against 15 outputs that must be rejected — three of them `test9`'s
real prose payloads — and 3 that must be accepted.

**A verifier that passes everything is indistinguishable from no verifier, and that exact failure
has happened twice in this repository.** The self-test caught a live instance: a leading `\b` in the
path patterns meant `\b~/` and `\b/etc/` could never fire after a space, so every path check was
dead. Nothing else would have found it.

Model *failures* are counted separately from *degradations*. Conflating them would let a
misconfigured model read as unsummarisable documentation.

---

# Does it actually retrieve? (2026-08-25)

`summarise_corpus.py` + `eval_recall.py`, BGE-M3, 220 entries, 17 queries — `test5`'s slice, its
queries, its encoder, its `subset(seed=11)`. Everything held constant but the prose.

**The claim being checked.** `test5` measured how often the correct answer came back as the very
first hit: raw doc text **29%**, hand-written caller's-words entries **77%**, and the project has quoted that gap ever since as the reason the
summarise step *is* the product. It had one hole: **the 77% was written by a person.** Whether a
local model produces entries that retrieve like the hand-written ones had never been measured.

***top 1 … top 10** — how many of the 17 queries got the correct answer back inside that many
hits. **Top 1** is the strict one: the right entry came back first. Higher is better everywhere.*

| index over the same 220 entries | top 1 | top 3 | top 5 | top 10 |
|---|---|---|---|---|
| raw harvested doc text | **5 of 17 (29%)** | 6 of 17 | 8 of 17 | 13 of 17 |
| summarised by this component | **4 of 17 (24%)** | 6 of 17 | 7 of 17 | 10 of 17 |
| *hand-written, 26 queries, synthetic corpus — a reference, not a row* | *20 of 26 (77%)* | | | |

**It does not reproduce.** A local model rewriting real documentation retrieves no better than the
raw documentation, and slightly worse overall. The 2.6× lift the project has been quoting is not a
property of *summarising*; it was a property of *who wrote the summary*.

Read the first row as: *of 17 developer questions, the raw index put the right answer first 5 times
and somewhere in the first ten 13 times.*

## Where the loss actually is

16 of 220 entries (7%) failed verification and degraded to signature-only. But **5 of the 17 query
targets degraded — 29%, four times the corpus rate.** Splitting on that separates two mechanisms
that were being averaged together:

*Diagnostic, not a headline — the subset is chosen by an outcome of the run.*

| the 12 queries whose target was **not** degraded | top 1 | top 3 | top 5 | top 10 |
|---|---|---|---|---|
| raw harvested doc text | 4 of 12 (33%) | 5 of 12 | 6 of 12 | 9 of 12 |
| summarised | 4 of 12 (33%) | 6 of 12 | 7 of 12 | 9 of 12 |

**The rewriter is neutral. The fallback is what costs.** Every one of the five degraded targets got
worse, three of them catastrophically — `CallLoggingConfig` fell from rank 8 to rank 139,
`DefaultHeadersConfig` from 8 to 52, `HttpTimeoutConfig` from 93 to 213. All three top-10 hits lost
between the two tables are degraded targets.

**So the safe state is safe to *use* and not safe to *find*.** `test0` measured signature-only as
sufficient for an agent to use a capability — **7 of 8** — but that measurement started with the
capability already in hand. Nobody asked whether it could be retrieved. It cannot: a signature has
no prose, and the query is prose. That is a real hole in property 4's reasoning and this is the
first measurement to expose it.

## What the rewriter does do

Neutral on average hides a wide spread. Among non-degraded targets, five improved and six worsened,
and the improvements are large and in a specific place — exactly the near-neighbour crowding
`test5` diagnosed, where Kotlin API prose is all written in one register:

*Rank of the correct answer — **lower is better**, 1 means it came back first.*

| | rank, raw | rank, summarised |
|---|---|---|
| `Channel` | 86 | **1** |
| `Semaphore` | 69 | **3** |
| `respondOutputStream` | 8 | **2** |
| `debounce` | 100 | 91 |
| `staticFiles` | 1 | 6 |
| `Mutex` | 1 | 5 |
| `CachingOptions` | 6 | 14 |

Rewriting rescues entries buried by register collision and disturbs entries that were already
found. That is a coherent mechanism rather than noise, and it suggests the useful comparison is not
raw-versus-summarised but **both**, which nothing here has measured.

## What this does not say

- **It does not say the summariser is not worth having.** Its measured job is *quarantine*, and
  `test7` measured that directly — 0 of 3 harm, 2 of 3 task. This measures the *other* claim made
  for it, and only that one.
- **17 queries is small.** The headline difference, 5 against 4, is **one query**. The top-10
  difference is three, and the diagnostic split explains those three mechanically, which is the
  only reason this is reported as a finding rather than as noise.
- **The raw baseline reproduces `test5` at top 1 (5 of 17) and top 10 (13 of 17)** and differs by
  two at top 3 and top 5 (6 against 8, 8 against 10). Most likely subset composition. Stated
  rather than smoothed.
- **One encoder, one model, one 220-entry corpus** — and `test5` already showed recall collapsing
  with corpus size, which nothing here re-tests.

## What it points at

**Verification is over-rejecting, and now at a known price.** 11 of the 16 degradations were
*"contains code or markup"* — the pattern matches backticks, braces, angle brackets, and the bare
words `fun` and `class`. A capability sentence reading *"Returns the class of the serializer"* is
rejected for containing the word `class`. Every rejection is a retrieval loss, so the cheapest
available improvement is not a better generator but a **less blunt verifier** — and one whose
fallback keeps prose instead of discarding it.
