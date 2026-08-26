# Does Summarising Actually Improve Retrieval?

RAD-0040 · 2026-08-25 · v1

Keywords: does the summariser improve recall; 29% vs 77%; recall at 1 on harvested docs;
machine-written vs hand-written index entries; signature-only fallback and retrieval; is summarise
load-bearing; retrieval-neutral rewriter; safe state that cannot be found; why the summarise step is
the product.

Measured against: BGE-M3 (`mlx-community/bge-m3-mlx-fp16`), Qwen3-Coder-30B-A3B-Instruct-MLX-4bit,
`test5`'s 220-entry harvested slice (`subset(seed=11)`) and its 17 queries, 2026-08-25.

**Opened because the number the product argument rests on had never been produced by the product.**
[RAD-0019](0019-retrieval-at-scale.md) measured recall over an index whose entries were **written by
hand**, and [`test5`](../../../experiments/test5/README.md) measured raw harvested documentation on
the same rig. The two numbers — the correct answer coming back first **77%** of the time against
**29%** — became the standing argument that the summarise step *is* the product rather than an
optimisation of it, and [RAD-0014](0014-build-vs-reuse.md) named *summarise* as something this
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

**Assumed, and worth separating.**

- That the neutrality holds at larger corpus sizes. `test5` measured raw recall collapsing from 29%
  to 0% between 220 and 5,440 entries. Whether summarised entries degrade as steeply is **not
  measured here and must not be assumed** — it is the same warning RAD-0019 carries, and this
  measurement inherits it rather than answering it.
- That a different local model would behave the same. One model, pinned deliberately.

**Not touched by this.** The summariser's *quarantine* result stands: [`test7`](../../../experiments/test7/README.md)
measured a tool-less paraphraser stopping a planted credential (0 of 3) while the task still
completed (2 of 3). That is what the component is for, and this measures a different claim made for
it.

### The finding that is not about retrieval

**A safe state that cannot be retrieved is not a safe state for an index.**

[`test0`](../../../experiments/test0/README.md) measured signature-only as sufficient for an agent
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
   about the target, not about the mechanism that would reach it. `CANON.md`, the site's experiments
   page, RAD-0014 and RAD-0019 all carry the inference and need the correction.
2. **Make the fallback keep prose.** Narrowing the rule was done here and recovered the head.
   What remains is the fallback itself: a rejected entry still degrades to a signature, which
   cannot be retrieved at all. Degrading to *stripped* rather than *discarded* prose removes the
   failure mode instead of shrinking it — a design change with a security argument attached, not a
   tuning change, and so not made here.
3. **Find out what raw text retrieves on.** If the `CallLoggingConfig` result generalises, part of
   the raw baseline is boilerplate rather than documentation. Cheap to check and it would narrow
   what every number compared against that baseline means.
4. **Measure the union.** Neither index wins; they fail on different entries, and the failure modes
   are complementary by construction — raw prose loses to register collision, rewritten prose loses
   the incidental vocabulary that was retrieving well. Indexing both faces of an entry is what
   [RAD-0013](0013-the-codex-entry.md) already describes, and it has never been scored.

**What would change the answer.** A verifier that degrades rather than discards, re-scored on this
same slice. A larger query set — 17 is too few for the headline difference to carry weight on its
own, and it is only the mechanism behind the fallback split that makes this reportable. And the
scale question, which nothing here touches.
