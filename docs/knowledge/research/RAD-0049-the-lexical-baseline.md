# What Lexical Search Alone Retrieves

RAD-0049 · 2026-08-27 · v1
Keywords: how good is lexical search over library documentation; do we need embeddings; what does BM25 get us; SQLite FTS5 recall on doc comments; baseline for the vector index; why keyword search fails on a need written in prose; recall at one and ten over a real dependency graph; is full-text search enough.
Measured against: SQLite 3.53.4 (FTS5, `porter unicode61`), 59 pinned Maven coordinates from `experiments/test5/CORPUS-MANIFEST.md`, 11,156 content-addressed entries harvested by `tree-sitter-sources-jar/1`, the 17 needs of `experiments/test5/queries.json`, 2026-08-27.

## Question

Before building the expensive half of the retrieval design — embeddings, a two-faced index, a summariser — is the cheap half enough on its own?

The question is not rhetorical. `test0` measured that a bare symbol and signature are enough for an agent to *use* a capability, 7 of 8, so a lexical index over real harvested documentation is not obviously worthless. And whatever it scores becomes the number the vector index has to beat; a retrieval design with no baseline cannot tell an improvement from a change.

## Trail

**The index is SQLite FTS5 in the same file as the store**, not a second engine. That choice was made for the scope boundary rather than for convenience: a query is restricted to a per-project set of coordinates, and in one file that restriction is a join in the same statement that ranks. With the text in a second store you either duplicate the coordinate data into it or run two stages across two stores — and a boundary enforced in two places is where it leaks.

**The symbol is indexed as words, not as an identifier.** The tokenizer sees `respondOutputStream` as one term, so nothing written in prose could ever reach it. Splitting on case boundaries and dots, and keeping the original alongside the split, is what makes an identifier reachable at all. The `porter` tokenizer stems, so a doc saying "retrying" answers a need saying "retry". BM25 weights the symbol column three times the documentation, on the grounds that the symbol is short and is the thing being looked for.

**A need is ORed, not ANDed.** A need is a sentence; requiring every word of a sentence to appear in one doc comment returns nothing for almost any question worth asking. Every term is quoted, which in FTS5 makes it a literal — so a need containing `NOT` or an unbalanced quote is searched for rather than executed.

**The corpus and the needs are the ones already used**, unchanged: 59 coordinates of one real Ktor server project's resolved dependencies, and 17 needs written as a developer would ask them. That is deliberate — it puts this number beside the ones RAD-0040 and RAD-0048 measured with embeddings against the same corpus, rather than beside nothing.

**A guard was added before the number was believed.** All 17 gold targets were checked to be present in the index. Had one been missing, the recall figure would have been a measurement of the harvester wearing the search's name, and the two failures are indistinguishable from outside.

## Findings

Measured.

| | |
|---|---|
| entries indexed | 11,156 |
| gold targets absent from the index | 0 |
| **recall@1** | **1 of 17** |
| **recall@10** | **2 of 17** |
| something from the target's declaring scope, in the first ten | 5 of 17 |
| query over 59 coordinates, median | 85 ms |
| query over 1 coordinate, median | 7 ms |

**Lexical search over raw documentation is close to useless at this corpus size, and the shape of the failure is more interesting than the score.** The two hits are `kotlin.text.Regex`, whose need names regular expressions almost by their own vocabulary, and `respondRedirect`, whose need says "point whoever asked at the new address" against a doc that says "redirect". Both are cases where the caller happened to use the library's own words.

**It lands next door.** For "a lock so only one coroutine at a time runs this section" the top hits are `Mutex.tryLock` and `Mutex.unlock` — the target is `Mutex` itself. For "retry a failed request with a growing delay" the entire top five is `HttpRequestRetryConfig.*`, including `exponentialDelay`; the target is `retryIf`. Five of 17 needs put something from the target's own declaring scope in the first ten while only two put the target there. **The failure is discrimination among near-identical members, not navigation to the right region.** That is a different problem from the one an embedding is usually reached for, and it is worth carrying into the next slice.

**Where the caller's words and the library's words do not overlap, it returns noise.** "Wait for a burst of rapid events to settle before acting on the last one" — the need for `debounce` — returns a test scheduler and a WebSocket ping. Nothing in the top ten is about rate limiting. There is no lexical bridge between how a problem is described and how a solution is named, which is the whole reason the vector index exists.

**Query cost is dominated by the number of terms in the need and the size of the scope**, since an ORed sentence matches most of the corpus and every match is then checked against the coordinate set. 85 ms over 59 coordinates is comfortable behind a tool call and would not be comfortable behind a keystroke. Restricting the scope to one coordinate costs 7 ms, so the cost is roughly linear in how much of the store a project can see.

Assumed, not measured.

- That the result would degrade further on a larger graph. `test5` measured raw documentation collapsing toward zero by 3,000 entries and this is 11,156, so the curve is consistent with it — but the shape past this size is not measured here.
- That BM25 weighting is near its useful limit. Only one weighting was tried, chosen by argument rather than by sweep.

## Recommendation

**Keep it, and do not try to rescue it.** It is the spine — a need in words goes in, ranked entries come out, scoped to the project, three outcomes distinguishable — and the spine works end to end. Tuning it further would be spending on the half already known to be cheap.

**The number to beat is recall@10 = 2 of 17.** RAD-0040 measured both faces on 220 entries at 15 of 17; the honest comparison for #6 is against this corpus at this size, not against that one.

**Carry the near-miss finding into the two-faced index.** Lexical search navigates to the right class and then cannot choose among its members. If the vector index has the same weakness, the two will fail together and the ensemble will not save either — and that is a question worth asking of the measurement rather than discovering afterwards.

**Retain the lexical index rather than replacing it.** It is nearly free, it is exact where the caller does use the library's own vocabulary, and one of its two hits — `Regex` — is a case where a vector index has no particular advantage. Whether the two combine usefully is #6's question, and this record does not prejudge it.

## Connections

- [ADR-0012](../decisions/ADR-0012-a-shared-machine-level-index-store.md) — the shared store this queries, and why the scope restriction is a containment boundary rather than a filter
- [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) — recommends Lucene for the hybrid; this slice excludes vectors, and the scope join is why FTS5 serves it
- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) — the two-faced measurement this is the baseline for
- [RAD-0019](RAD-0019-retrieval-at-scale.md) — the recall@k shape reused here
