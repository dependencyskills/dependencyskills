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

***top 1 … top 10** — how many of the 17 questions got the correct answer back inside that many
hits. **Top 1** is the strict one: the right entry came back first. Higher is better everywhere.*

| index over the same 220 entries | top 1 | top 3 | top 5 | top 10 |
|---|---|---|---|---|
| raw harvested doc text | **5 of 17 (29%)** | 6 of 17 | 8 of 17 | 13 of 17 |
| summarised by this component | **5 of 17 (29%)** | 7 of 17 | 8 of 17 | 10 of 17 |
| *hand-written, 26 questions, synthetic corpus — a reference, not a row* | *20 of 26 (77%)* | | | |

Read the first row as: *of 17 developer questions, the raw index put the right answer first 5 times
and somewhere in the first ten 13 times.*

**It does not reproduce.** A local model rewriting real documentation retrieves **the same** as the
raw documentation at the head and worse in the tail. The 2.6× lift the project has been quoting is
not a property of *summarising*; it was a property of *who wrote the summary*.

## It took two runs, and the first one was measuring the verifier

The first run scored **4 of 17** first-hit against raw's 5, with **16 of 220 entries degraded** to
signature-only and — the number that mattered — **5 of the 17 question targets** among them, four
times the corpus rate. Splitting on that separated two mechanisms that were being averaged: on the
12 surviving targets the two indexes tied exactly, so the rewriter was neutral and the whole visible
loss was the fallback.

**11 of those 16 degradations came from one over-broad rule.** *Contains code or markup* matched the
bare words `fun` and `class`, so *"Returns the class of the serializer"* was thrown away as markup.
Narrowing it to match a **declaration** rather than a word — `fun name(` and `class Name` — halved
the degradation rate and moved the headline back to a tie:

| | degraded | question targets degraded | top 1 |
|---|---|---|---|
| before narrowing | 16 of 220 (7%) | **5 of 17** | 4 of 17 |
| after | 6 of 220 (3%) | **1 of 17** | 5 of 17 |

Every rejection is a retrieval loss, so an over-broad verifier is not a free safety margin. The
model output is now stored with each entry so `--reverify` can re-judge it against a changed
verifier without paying for 220 model calls again; that is why the second run cost nothing.

## What is left after the fix, and it is not the fallback

Top 10 is still **10 against 13**, with only one target degraded. That residual is the rewriter
itself, and it is not evenly spread. Among the questions, five improved and seven worsened:

*Rank of the correct answer — **lower is better**, 1 means it came back first.*

| | rank, raw | rank, summarised |
|---|---|---|
| `Channel` | 86 | **1** |
| `Semaphore` | 69 | **3** |
| `respondOutputStream` | 8 | **2** |
| `retry` | 2 | **1** |
| `debounce` | 100 | 89 |
| `Mutex` | 1 | 5 |
| `CachingOptions` | 6 | 13 |
| `CallLoggingConfig` | 8 | 46 |
| `DefaultHeadersConfig` | 8 | 77 |

The gains land exactly where `test5` diagnosed **near-neighbour crowding** — Kotlin API prose is
written in one register, so everything resembles everything, and rewriting breaks the tie.

The losses are stranger, and worth stating rather than explaining away. `CallLoggingConfig`'s raw
documentation reads, in full, *"A configuration for the CallLogging plugin"* plus a *Report a
problem* link. The summary reads *"a Ktor DSL configuration class that sets up logging for incoming
HTTP calls in a Ktor server application."* Against the question *"record what requests came in and
how they were answered"* the **less informative text retrieves better** — rank 8 against 46.

*Unverified hypothesis:* the raw key carries the plugin's own vocabulary several times over,
including inside the feedback URL's fully-qualified name, and repetition sharpens the embedding.
The summary says it once, in a longer sentence with more competing content. If that is right, some
of what raw text retrieves on is **boilerplate**, not documentation — which would be worth knowing
and is not established by two examples.

## What this does not say

- **It does not say the summariser is not worth having.** Its measured job is *quarantine*, and
  `test7` measured that directly — 0 of 3 harm, 2 of 3 task. This measures the *other* claim made
  for it, and only that one.
- **17 questions is small.** After the verifier fix the head is an exact tie, so the finding rests
  on the **top-10 gap of three** and on the per-question pattern behind it, not on a headline
  difference. The first run's 5-against-4 was one question and would not have been reportable on
  its own.
- **It does not say raw text is better.** It says the two are the same at the head and raw wins the
  tail, on one corpus, with a residual whose mechanism is a hypothesis.
- **The raw baseline reproduces `test5` at top 1 (5 of 17) and top 10 (13 of 17)** and differs by
  two at top 3 and top 5 (6 against 8, 8 against 10). Most likely subset composition. Stated
  rather than smoothed.
- **One encoder, one model, one 220-entry corpus** — and `test5` already showed recall collapsing
  with corpus size, which nothing here re-tests.

## What it points at

**Index both faces, and score it.** Neither index wins. They fail on different questions and the
failure modes look complementary by construction — raw prose loses to register collision, rewritten
prose loses whatever the raw text was repeating. [RAD-0013](../../docs/knowledge/decisions/) already
describes an entry as having a semantic face and a syntactic one; nothing has ever scored an index
carrying both. That is the obvious next measurement and it is cheap, because both key sets already
exist in this directory.

**Find out what raw text is actually retrieving on.** If the `CallLoggingConfig` result generalises
— less informative text outranking more informative text because it repeats the symbol's own
vocabulary — then part of the raw baseline is boilerplate rather than documentation, and both the
29% and everything compared against it mean something narrower than they appear to. Two examples is
not a finding. It is a cheap thing to check.

**The fallback still discards prose.** Narrowing the verifier cut degradation from 7% to 3%, but a
rejected entry still falls back to a signature, which cannot be retrieved at all. Degrading to
*stripped* prose rather than *discarded* prose would remove the failure mode instead of shrinking
it — and that is a design change with a security argument attached, not a tuning change.
