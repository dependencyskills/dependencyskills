# Retrieval at Scale

RAD-0019 · 2026-08-20 · v4
Keywords: does retrieval work over hundreds of entries; index recall without an agent; paraphrastic queries in the caller's words; lexical versus vector versus hybrid; why equal-weight RRF underperformed the better arm; choosing an encoder on recall and licence; why the 77% figure is a target rather than a pipeline measurement.

**v4 (2026-08-25) — the 77% is an upper bound on a target, not a measurement of the pipeline.**
Every entry scored in this record was **written by hand**. [RAD-0040](Research-RAD-0040-Does-Summarising-Improve-Retrieval)
ran the built summariser over `test5`'s 220 harvested entries with its 17 queries and this
encoder: raw documentation and machine-summarised entries **both** retrieve the correct answer
first **5 of 17**, with summarised trailing in the tail. So 77%
records what an index *could* reach if its entries were as good as hand-written ones — a
target worth having — and not what the pipeline that builds them achieves. Where this record
is cited as evidence that the semantic face is the retrieval mechanism, that inference no
longer follows from it. The Layer 2 result (the agent authoring its own query, 10/10) is
untouched: it was measured on the same hand-written index, and what it demonstrates is query
authoring rather than entry quality.

**v3 (2026-08-22) — the scale in the title is not the scale that was measured.** The headline
77% r@1 rests on a **220-entry** synthetic corpus. `experiments/test5` harvested one small real
project — 99 dependencies — and got **5,440 deduped entries**, an order of magnitude more. On
that corpus, holding everything else constant and varying only the number of distractors,
raw-text recall falls **29% → 6% → 0%** across 220 → 1,000 → 3,000 entries. Whether
caller's-words entries degrade as steeply is untested and must not be assumed. But the corpus
this record measured is far smaller than the one the pipeline produces, and the recommendation
below should be read with that in mind.

**Design; measured.** Specifies and reports both layers of retrieval-at-scale — Layer 1
(pure index recall, no agent) and Layer 2 (the agent authoring a query against the index
exposed as a tool, then selecting from the results).

**v2 (2026-08-21).** Adds the **Layer 2** measurement (`experiments/test0/measurement/
retrieval-scale/layer2-agent-loop.md`): a 10-query pilot, blind Claude Opus 4.8 subagents
driving `search-codex.py` through developer tooling (ADR-0010). **10/10** — the agent loop
recovered every case, including all three Layer-1 hard misses (#27/#62/#120), because the
agent **authors its own query**, translating the caller's plain words into the capability's
technical vocabulary. The full loop **beats** raw recall (100% vs 77% r@1). See the Layer 2
findings below.

**Pinned (all public).** Encoders compared (via mlx-embeddings, Apache-2.0, in-process on
Apple MLX): **BAAI BGE-M3** (`mlx-community/bge-m3-mlx-fp16`, MIT), **intfloat
multilingual-e5-large** (MIT), **all-MiniLM-L6-v2** (Apache); nomic-embed-text-v1.5 (an
early run, via LM Studio, since dropped for licence clarity). Corpus: 220 synthetic
capability entries, 26 paraphrastic queries (`retrieval-scale/`), invented so training
exposure is nil.

## Question

Content-value, disambiguation and selection all placed candidate entries *in front of*
the agent. Retrieval-at-scale asks the prior question: when the right capability is one of
**hundreds** in an index, does a search return it for a caller who describes their need in
**their own words**? This is where the entry's **semantic face** must work as a *retrieval
key* — the half RAD-0013 predicted and RAD-0011 rests on ("capability in the caller's
words") but that had never been measured.

## Trail

### Layer 1 before Layer 2

Two questions hide here: does the **index** return the right entry (recall — an IR
question, no agent needed), and does an **agent** author a good query and use the result
(the full loop, needing the MCP tool). Layer 1 measures recall alone — cheap,
deterministic, no model calls — and validates the index before the MCP build.

### The corpus is adversarial, and the queries are paraphrastic

Random-noise distractors make recall trivially ~100% and teach nothing. So the 220 entries
are built as **adversarial clusters**: ~22 target capabilities each surrounded by 4–5
semantically-near siblings (a bounded-LRU cache next to a TTL cache, a write-through cache,
a memoizer…), plus broad noise. Crucially the **26 queries are paraphrastic** — the
caller's need in plain words that *deliberately avoid the entry's own trigger vocabulary*
("occasionally fails for no lasting reason; attempt it a few more times with growing
pauses" — never "retry", never "backoff"). That is the honest test of semantic retrieval:
match by meaning, not word overlap.

### The rig

Semantic face only (`capability` + `triggers`) is the retrieval key — never the opaque
symbol. Three rankings: **vector** (cosine over the embedded semantic face), **lexical**
(BM25 over the same text), **hybrid** (Reciprocal Rank Fusion), plus a **vector-weighted**
hybrid. Embeddings run **in-process** via mlx-embeddings (the production-shaped, fully-open
choice — no LM Studio, no server); retrieval math is pure Python. Scored by recall@k over
the whole corpus. `build-corpus.py`, `eval-retrieval.py`.

## Findings

**Measured — encoder matters, and a strong open one wins (N=58 pilot).** Vector recall@1
across encoders: MiniLM-4bit 4/8, e5-large 5/8, nomic 6/8, **BGE-M3 7/8**. At small N the
weak encoders clustered and looked "within noise"; a genuinely strong one (BGE-M3, MIT)
separated and got the hardest paraphrastic case to #1. **BGE-M3 is the pick** — top recall
*and* the cleanest licence (MIT, unrestricted commercial). (Encoder shortlist and licence
audit contributed by a second model cross-check — NV-Embed ruled out on CC-BY-NC.)

**Measured — at scale, vector decisively beats lexical (N=220, 26 queries, BGE-M3).**

| method | r@1 | r@3 | r@5 | r@10 |
|---|---|---|---|---|
| vector | **20 of 26 (77%)** | 21 of 26 | 22 of 26 | 23 of 26 (88%) |
| lexical | 10 of 26 (38%) | 14 of 26 | 15 of 26 | 15 of 26 (58%) |
| hybrid (equal RRF) | 13 of 26 | 14 of 26 | 15 of 26 | 18 of 26 |
| hybrid + vector-weighted (2:1) | 14 of 26 | 16 of 26 | 20 of 26 | 22 of 26 |

- **The semantic face is the retrieval mechanism for caller's-words queries.** Vector 77%
  r@1 vs lexical 38%; lexical plateaus at 58% by r@10. Keyword search cannot find a
  capability described in words that avoid the library's vocabulary — exactly the realistic
  case. This is RAD-0011's thesis at full strength, and the strongest evidence yet for the
  vector index (RAD-0010).
- **Naive equal-RRF hybrid *hurts*.** 13/26 r@1, **below vector's 20/26** — fusing the
  strong vector arm with the near-useless lexical arm drags the good hits down. Weighting
  the vector arm 2:1 recovers most of it (up to 20/26 by r@5) but **still does not beat
  vector alone.** So "hybrid ≥ either arm" is **false** for this regime.
- **A genuine hard tail.** 3/26 queries miss top-10 even on vector — the densest adversarial
  clusters, where an oblique need sits among near-identical siblings. A realistic ceiling.

**What this revises.** RAD-0010 assumed a hybrid keyword+vector store as the default. Layer
1 sharpens that: for **caller's-words (semantic) retrieval with a strong encoder, the design
is vector-primary**, not equal fusion — lexical earns its keep only for queries carrying
exact high-signal terms (a known symbol, "LRU"), where BM25's exact match beats the
embedding. The store still holds both (BM25 is cheap and Lucene indexes it anyway); the
change is in **fusion policy** — weight the lexical arm up only when the query has
high-IDF exact terms, rather than fusing equally. A query-adaptive fusion is the follow-on.

**Measured — Layer 2, the agent loop (2026-08-21; 10-query pilot, blind Claude Opus 4.8
subagents; `retrieval-scale/layer2-agent-loop.md`).** Where Layer 1 fed the caller's need
verbatim, Layer 2 gives an agent the need and lets it **author its own query** against
`search-codex.py` (the index as a tool, RAD-0003), read the top-10, and select — blind to
the target. Pilot spanning all three Layer-1 difficulty bands: **10/10 correct, one tool
call each, high confidence** — including all three cases Layer-1 vector missed beyond top-10
(BloomFilter #27, StateMachine #62, EventBus #120). The mechanism: the agent maps the
caller's deliberately-vocabulary-free need onto the capability's technical terms ("have I
probably seen this id, false yes ok" → *probabilistic set membership, false positives*),
and that authored query retrieves the target at #1. **The full loop beats raw recall** (100%
vs vector 77% r@1 / 88% r@10) — query authoring closes the semantic tail; selection-from-
candidates was barely the deciding step because authoring alone lifted the target to #1.

**What this does not close.** The Layer-2 corpus is **well-known CS patterns**, so the agent
already knows the canonical vocabulary to author. For genuinely novel or *local* capabilities
— the codex's distinctive value (RAD-0016) — the agent may not know what to search for, and
query authoring gets harder; that is the boundary this pilot does not cross and the next
Layer-2 test. Also unscaled: N=10, single model, one attempt each (full 26 + local/Gemini is
the follow-on), and the corpus is synthetic — a harvested one (RAD-0011) is the external
-validity check.

## Recommendation

**Vector-primary retrieval over a strong, permissively-licensed encoder (BGE-M3), with
lexical as a term-match supplement, not an equal-RRF partner.** Keep both arms in the store
(Lucene holds BM25 + HNSW in one artifact — RAD-0010) but make fusion query-adaptive. Layer
2 (the agent loop) is now measured and **passed** (10/10 pilot; the agent's authored query
beats verbatim recall). Next: scale Layer 2 (full 26 + cross-model), test it against *novel/
local* capabilities where the agent does not already know the vocabulary, port the rig to the
JVM/Lucene substrate, and swap the synthetic corpus for a harvested one.

## Connections

- [RAD-0011](Research-RAD-0011-Existing-Documentation-Systems-As-Skill-Content) — "capability in the
  caller's words"; this measures it as retrieval.
- [RAD-0013](Research-RAD-0013-The-Codex-Entry) — the two-faced entry; the semantic face now shown to
  work as a *retrieval key*, not only a disambiguation cue.
- [RAD-0010](Research-RAD-0010-How-The-Codex-Is-Stored-And-Served) — the hybrid store; this revises
  the fusion policy to vector-primary.
- [RAD-0017](Research-RAD-0017-The-Retrieval-Disambiguation-Ab) — disambiguation among inlined
  candidates; this is the retrieval step that precedes it.
- [RAD-0003](Research-RAD-0003-Central-Capability-Server) — the MCP query server Layer 2 needs.
