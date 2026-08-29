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
real prose payloads — 7 that must be accepted, and 3 **known holes**.

**A verifier that passes everything is indistinguishable from no verifier, and that exact failure
has happened twice in this repository.** The self-test caught a live instance: a leading `\b` in the
path patterns meant `\b~/` and `\b/etc/` could never fire after a space, so every path check was
dead. Nothing else would have found it.

Model *failures* are counted separately from *degradations*. Conflating them would let a
misconfigured model read as unsummarisable documentation.

### The known holes are printed, not omitted

Every must-reject case is an **instruction**, and every rule is a **shape** rule. So the thing none
of them can see is prose that is well-formed, non-imperative and simply not about this symbol.

That is not a hypothetical. A mis-templated run in `test25` emitted `import numpy as np` for all 20
entries of a sample and scored **0% degraded** — verification had no objection, because nothing here
asks whether the sentence has anything to do with the capability. Listing the holes in the
self-test's own output is the cheapest defence against the failure this repository keeps
re-learning: a check that only exercises what it catches reads as complete when it is not.

Closing them needs a **relatedness** check rather than another regex, and that is a design decision
for #7 rather than a patch here.

## The backtick was a rule with a price and no return (2026-08-28)

`CODEISH` used to contain a bare backtick, so any candidate that wrapped an identifier in markdown
was rejected outright and the entry degraded to signature-only. A backtick is not an imperative and
not code; it is how a model marks a name it was handed.

The candidate is now **normalised before judgement** — backticks removed, and the normalised
sentence is what gets published, so nothing reaches the index that verification did not see.

What the rule actually cost, and the shape of it is the point:

| model | size | degraded before | degraded after |
|---|---|---|---|
| `gemma-3-270m-it-qat` Q4_0 | 230 MB | 10 of 60 | 2 of 60 |
| `Qwen2.5-0.5B-Instruct` | ~1 GB | 38 of 60 | 2 of 60 |
| `Qwen3-Coder-30B`, the pinned one | 16 GB | 6 of 220 | 4 of 220 |

**The cost tracked a writing habit, not a size.** Backticking identifiers is something a given
instruct model does or does not do — Qwen2.5-0.5B does it constantly, gemma-3-270m barely, and the
smaller of those two paid less. So the rule's price was unpredictable from anything about the
model except its prose style.

That is also why it survived: measured only on the pinned 30B, which does not have the habit, it
looks nearly free at 2 entries. The models RAD-0051 recommends running in-process are the ones that
paid for it, and none of them had been run through the component when the rule was written.

The 30B row is a re-score of the stored output in `summaries.json`; no model was run for it. The
four entries still rejected there are two imperatives, one 41-word sentence and one genuine markup
leak — all correct.

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

## Two indexes are better than either, and fusing them is worse than both

The rewritten text and the raw text fail on **different** questions, which suggests carrying both
rather than choosing. RAD-0013 already describes an entry as having a semantic face and a syntactic
one; nothing had scored an index that keeps both.

Two more things become measurable once the key and the shown text are separated. **A retrieval key
is embedded and never read by an agent; the shown text is what reaches it.** The quarantine
requirement binds the second, not the first — so a degraded entry can be *findable* on text it must
never *display*. That is not a loophole, it is the two-faced entry, and it means the safe state does
not have to be an unreachable one.

| index over the same 220 entries | top 1 | top 3 | top 5 | top 10 |
|---|---|---|---|---|
| raw harvested doc text | 5 of 17 | 6 of 17 | 8 of 17 | 13 of 17 |
| summarised | 5 of 17 | 7 of 17 | 8 of 17 | 10 of 17 |
| summarised, degraded entries keyed on raw text | 5 of 17 | 7 of 17 | 9 of 17 | 11 of 17 |
| **both faces — two vectors, best match wins** | **5 of 17** | **7 of 17** | **10 of 17** | **15 of 17** |
| both faces fused into one vector | 4 of 17 | 7 of 17 | 8 of 17 | 10 of 17 |

**The two-faced index is the best result anything has produced on this slice** — 15 of 17 within ten
against the raw baseline's 13, and 10 of 17 within five against 8. Nothing moves the top-1 figure,
which stays at 5 for every index that is not actively worse.

**Fusing the two texts into one key is worse than either alone at the head** — 4 of 17. The gain
needs two vectors, not one longer string. That is the same shape as RAD-0019's finding that an
equal-weight hybrid *hurt*: keeping the arms separate wins, averaging them drags hits down.

**It does not dominate per question, and that is the mechanism.** On no single question does the
two-faced index beat both single-face indexes — every entry gets two chances, so competitors improve
too and ranks shift relative to each other. What it does is **stop losing badly**: `DefaultHeadersConfig`
8 / 77 / **9**, `CallLoggingConfig` 8 / 46 / **9**, `Channel` 86 / 1 / **2**, `retryIf` 4 / 21 / **4**.
Each single-face index has a set of questions it fails catastrophically; those sets barely overlap,
and the two-faced index inherits the better face's ballpark on nearly all of them.

**Keying degraded entries on raw text works too, and is now a small effect** — 9 of 17 within five
against 8, 11 within ten against 10. Small only because narrowing the verifier already cut degraded
question targets from 5 to 1. It removes the failure mode rather than shrinking it, which is what
matters if the verifier ever tightens again.

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

**Find out what raw text is actually retrieving on.** If the `CallLoggingConfig` result generalises
— less informative text outranking more informative text because it repeats the symbol's own
vocabulary, feedback URL included — then part of the raw baseline is boilerplate rather than
documentation, and both the 29% and everything compared against it mean something narrower than they
appear to. Two examples is not a finding. It is a cheap thing to check, and it now matters more,
because the best index here **keeps** that raw face.

**Scale.** Every number on this page is 220 entries. `test5` measured raw recall falling 29% → 6% →
0% between 220 and 3,000. Whether the two-faced index degrades as steeply is the question that
decides whether any of this survives a real dependency graph, and nothing here touches it.

**The fabricated capability is untouched and now slightly more exposed.** Keeping a raw retrieval
key means prose written to win retrieval on merit still competes — `test6` measured a fabricated
library beating the true answer 4 of 17. The summariser never addressed that, and RAD-0037 §1
records it as open. This measurement does not make it worse, but it does mean the two-faced index
inherits it rather than escaping it.
