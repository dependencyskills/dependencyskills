# Does Summarising Actually Improve Retrieval?

RAD-0040 · 2026-08-25 · v1

Keywords: does the summariser improve recall; 29% vs 77%; recall at 1 on harvested docs;
machine-written vs hand-written index entries; signature-only fallback and retrieval; is summarise
load-bearing; retrieval-neutral rewriter; safe state that cannot be found; why the summarise step is
the product.

Measured against: BGE-M3 (`mlx-community/bge-m3-mlx-fp16`), Qwen3-Coder-30B-A3B-Instruct-MLX-4bit,
`test5`'s 220-entry harvested slice (`subset(seed=11)`) and its 17 queries, 2026-08-25.

**Opened because the number the product argument rests on had never been produced by the product.**
[RAD-0019](Research-RAD-0019-Retrieval-At-Scale) measured recall over an index whose entries were **written by
hand**, and [`test5`](https://github.com/dependencyskills/dependencyskills/blob/HEAD/experiments/test5/README.md) measured raw harvested documentation on
the same rig. The two numbers — the correct answer coming back first **77%** of the time against
**29%** — became the standing argument that the summarise step *is* the product rather than an
optimisation of it, and [RAD-0014](Research-RAD-0014-Build-Vs-Reuse) named *summarise* as something this
project must build largely on that basis.

Nothing in that chain involved a machine writing a summary.

> **Does a local model rewriting real documentation retrieve like the hand-written entries, like
> the raw documentation, or somewhere between?**

## Trail

### The comparison had to be controlled, which meant reusing test5's rig rather than rebuilding it

The published 29% and 77% differ in more than one variable: 17 queries against 26, harvested entries
against synthetic ones, real API prose against prose written for the experiment. Any of those could
carry the gap. So the only defensible measurement holds everything constant and changes the prose
alone — same 220 entries, same 17 queries, same encoder, same `subset(seed=11)` construction, two
indexes over identical rows.

That makes the 77% a **reference point rather than a row in the table**, and it is reported that way.
It was measured on a different query set over a different corpus, and no arithmetic makes it
comparable.

`test5`'s subset construction was imported rather than reimplemented, and its key-building function
was **copied rather than imported** — so a later edit in `test5` cannot silently move a published
baseline out from under this result.

### The raw baseline reproduces, mostly

The rebuilt raw index scores **5 of 17** first-hit and **13 of 17** within ten, matching `test5`'s
published row exactly at both ends. It differs by two in the middle: 6 against 8 within three, 8
against 10 within five. Most likely subset composition. Recorded rather than smoothed, because a
baseline that reproduces at three of four points is worth more when the fourth is stated.

### The first result was measuring the verifier, and the second run proved it

Summarised: **4 of 17** first-hit against raw's 5, and **10 of 17** within ten against 13. Worse, on
a 17-query sample where the headline difference is a single query.

A single query is noise. What made this a finding rather than noise was splitting on the
verification fallback. 16 of 220 entries (7%) failed verification and degraded to signature-only —
but **5 of the 17 query targets degraded, 29%, four times the corpus rate.** Query targets are the
interesting entries, and interesting entries have richer documentation with more code in it.

Scoring only the 12 queries whose target survived verification:

| the 12 non-degraded targets | first hit | within ten |
|---|---|---|
| raw harvested doc text | 4 of 12 | 9 of 12 |
| summarised | 4 of 12 | 9 of 12 |

Identical. Every one of the three lost within-ten hits is a degraded target, and every degraded
target got worse — `CallLoggingConfig` from rank 8 to 139, `DefaultHeadersConfig` from 8 to 52,
`HttpTimeoutConfig` from 93 to 213.

This is a diagnostic, not a headline: the subset is chosen by an outcome of the run. It is reported
as one.

### Narrowing one verifier rule recovered the head, and the tail stayed lost

11 of the 16 degradations were the *"contains code or markup"* rule, which matched the bare words
`fun` and `class` — so *"Returns the class of the serializer"* was discarded as markup. Narrowing it
to match a **declaration** rather than a word (`fun name(`, `class Name`) and re-running:

| | degraded | query targets degraded | first hit | within ten |
|---|---|---|---|---|
| raw baseline | — | — | 5 of 17 | 13 of 17 |
| summarised, before narrowing | 16 of 220 (7%) | 5 of 17 | 4 of 17 | 10 of 17 |
| summarised, after narrowing | 6 of 220 (3%) | 1 of 17 | **5 of 17** | 10 of 17 |

The head is now an exact tie and the tail gap is unchanged at three, with only one target degraded —
so the residual is **the rewriting itself**, not the fallback. The first run's split was right about
the mechanism it identified and wrong to imply that mechanism was the whole loss.

The model's output is now stored with each entry, so a changed verifier can be re-judged without
re-generating. That is not housekeeping: an improvement costing 220 model calls to evaluate does not
get evaluated, and this one turned out to be worth a first-hit.

### The residual losses are the opposite way round from what would be expected

Among the questions, five improved and seven worsened. The gains are large and land on entries
buried by register collision — `Channel` 86 → 1, `Semaphore` 69 → 3, `respondOutputStream` 8 → 2.

The losses are strange. `CallLoggingConfig`'s raw documentation reads, in full, *"A configuration
for the CallLogging plugin"* plus a *Report a problem* link. Its summary reads *"a Ktor DSL
configuration class that sets up logging for incoming HTTP calls in a Ktor server application."*
Against the question *"record what requests came in and how they were answered"*, the **less
informative text retrieves better** — rank 8 against 46. `DefaultHeadersConfig` behaves the same way,
8 against 77.

*Hypothesis, unverified and not established by two examples:* the raw key repeats the plugin's own
vocabulary several times, including inside the feedback URL's fully-qualified name, and repetition
sharpens the embedding; the summary says it once inside a longer sentence carrying more competing
content. If that holds, part of what the raw baseline retrieves on is **boilerplate rather than
documentation**, which would narrow what the 29% means and what anything compared against it means.

### Neutral on average is not neutral per query, and the spread has a shape

Among non-degraded targets five improved, six worsened, two held. The improvements are large and
they land in a specific place — `Channel` 86 → 1, `Semaphore` 69 → 3, `respondOutputStream` 8 → 2.
Those are precisely the failures `test5` diagnosed as **near-neighbour crowding**, where Kotlin API
prose is written in one register and everything resembles everything. The regressions are entries
that were already being found: `staticFiles` 1 → 6, `Mutex` 1 → 5.

So rewriting **rescues entries buried by register collision and disturbs entries that were already
surfacing.** That is a mechanism, not scatter, and it points somewhere neither index goes.

### What the verifier is actually rejecting

11 of the 16 degradations were the *"contains code or markup"* rule, which matches backticks,
braces, angle brackets and the bare words `fun` and `class`. A sentence reading *"Returns the class
of the serializer"* is rejected for the word `class`. That rule was written to keep markup out of an
index entry and it is also throwing away well-formed capability descriptions.

### Separating the key from the shown text answered the last two questions

A retrieval key is embedded and never read by an agent; the shown text is what reaches it. The
quarantine requirement binds the second, not the first — so an entry can be **findable on text it
must never display**. That is RAD-0013's two-faced entry, and it makes two previously-assumed things
measurable on this slice at the cost of embedding alone.

| index over the same 220 entries | first hit | top 3 | top 5 | within ten |
|---|---|---|---|---|
| raw harvested doc text | 5 of 17 | 6 of 17 | 8 of 17 | 13 of 17 |
| summarised | 5 of 17 | 7 of 17 | 8 of 17 | 10 of 17 |
| summarised, degraded entries keyed on raw text | 5 of 17 | 7 of 17 | 9 of 17 | 11 of 17 |
| **both faces — two vectors, best match wins** | **5 of 17** | **7 of 17** | **10 of 17** | **15 of 17** |
| both faces fused into one vector | 4 of 17 | 7 of 17 | 8 of 17 | 10 of 17 |

The two-faced index is the best result anything has produced on this slice. Nothing moves the
first-hit figure, which sits at 5 for every index that is not actively worse.

**Fusing the two texts into one key is worse than either alone at the head.** The gain needs two
vectors, not one longer string — the same shape as this project's earlier finding that an
equal-weight hybrid *hurt*, where keeping the arms separate won and averaging them dragged hits down.

**It does not dominate per question.** On no single question does the two-faced index beat both
single-face indexes: every entry gets two chances, so competitors improve too and ranks move
relative to each other. What it does is stop losing badly — `DefaultHeadersConfig` 8 / 77 / **9**,
`CallLoggingConfig` 8 / 46 / **9**, `Channel` 86 / 1 / **2**, `retryIf` 4 / 21 / **4**. Each
single-face index fails a different set of questions catastrophically, the sets barely overlap, and
the two-faced index inherits the better face's ballpark on nearly all of them.

## Findings

**Measured.**

- **The 29% → 77% gap does not reproduce with a machine summariser.** On matched entries, queries
  and encoder, raw and summarised both retrieve **5 of 17** first-hit. The lift was a property of
  *who wrote the summary*, not of *summarising*.
- **The rewriter is neutral at the head and loses the tail** — 10 of 17 within ten against raw's 13,
  with only one target degraded, so the gap is the rewriting rather than the fallback.
- **An over-broad verifier rule was worth a first-hit and four of five degraded targets.** 11 of 16
  degradations came from *contains code or markup* matching the bare words `fun` and `class`;
  narrowing it to match a declaration cut degradation from 7% to 3% and query-target degradation
  from 5 of 17 to 1 of 17. Every rejection is a retrieval loss, so an over-broad verifier is not a
  free safety margin.
- **The rewriter's effect is bimodal and directional.** Large gains on entries buried by register
  collision; losses concentrated on configuration types.
- **An index carrying both faces beats either alone** — 15 of 17 within ten against raw's 13 and
  summarised's 10, and 10 of 17 within five against 8 for both. It wins by not losing badly rather
  than by dominating: the two faces fail on different questions and the failure sets barely overlap.
- **Fusing the two texts into one vector is worse than either alone at the head** (4 of 17). Two
  vectors, not one longer string.
- **A degraded entry can keep a retrieval key it never shows**, which recovers some of what the
  fallback costs — 9 of 17 within five against 8. Small now only because narrowing the verifier
  already took degraded question targets from 5 to 1.

**Assumed, and worth separating.**

- That the neutrality holds at larger corpus sizes. `test5` measured raw recall collapsing from 29%
  to 0% between 220 and 5,440 entries. Whether summarised entries degrade as steeply is **not
  measured here and must not be assumed** — it is the same warning RAD-0019 carries, and this
  measurement inherits it rather than answering it.
- That a different local model would behave the same. One model, pinned deliberately.

**Not touched by this.** The summariser's *quarantine* result stands: [`test7`](https://github.com/dependencyskills/dependencyskills/blob/HEAD/experiments/test7/README.md)
measured a tool-less paraphraser stopping a planted credential (0 of 3) while the task still
completed (2 of 3). That is what the component is for, and this measures a different claim made for
it.

### The finding that is not about retrieval

**A safe state that cannot be retrieved is not a safe state for an index.**

[`test0`](https://github.com/dependencyskills/dependencyskills/blob/HEAD/experiments/test0/README.md) measured signature-only as sufficient for an agent
to *use* a capability — 7 of 8 — and `test7` measured it as a working control. Both measurements
started with the capability **already in hand**. Neither asked whether it could be found. It cannot:
a signature carries no prose, and the query is prose.

The summariser's fourth property — *verification fails safe* — was reasoned from those two results.
The reasoning is sound about harm and silently wrong about reachability, and the gap held for as
long as it did because the two properties were measured by different experiments that never met.

## Recommendation

**Not a commitment. Three things follow, in cost order.**

1. **Stop quoting 29% → 77% as evidence that summarising improves retrieval.** It is evidence that
   *hand-written caller's-words entries* retrieve better than raw documentation, which is a claim
   about the target, not about the mechanism that would reach it. `DOC-0001-canon.md`, the site's experiments
   page, RAD-0014 and RAD-0019 all carry the inference and need the correction.
2. **Index both faces, with two vectors.** Measured here and it is the strongest configuration on
   this slice. This is the one recommendation that hardens: it is what [RAD-0013](Research-RAD-0013-The-Codex-Entry)
   already describes, it needs no new component, and the alternative that looks cheaper — fusing
   the texts into a single key — is measurably worse.
3. **Let a rejected entry keep a key it never shows.** The agent still receives only the signature;
   only the vector changes, and a vector decides which entry surfaces rather than what text is read.
   This removes the *safe but unfindable* failure mode instead of shrinking it.
4. **Find out what raw text retrieves on.** If the `CallLoggingConfig` result generalises, part of
   the raw baseline is boilerplate rather than documentation. It matters more now, because the
   recommended index keeps that face.

**What would change the answer.** A larger query set — 17 is too few for a one-hit difference to
carry weight, which is why the reportable results here are the ones with a mechanism behind them
rather than a margin. And **scale**, which nothing here touches: `test5` measured raw recall falling
29% → 6% → 0% between 220 and 3,000 entries, and whether the two-faced index degrades as steeply
decides whether any of this survives a real dependency graph.

**One thing it does not fix.** Keeping a raw retrieval key means prose written to win retrieval on
merit still competes. `test6` measured a fabricated library beating the true answer 4 of 17, the
summariser never addressed it, and RAD-0037 §1 records it as open. The two-faced index inherits that
exposure rather than escaping it.
