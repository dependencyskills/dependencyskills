# Layer 2 — the agent loop (pilot)

Layer 1 (`eval-retrieval.py`) measured **index recall**: the caller's need, verbatim,
embedded and ranked against the corpus. Layer 2 measures the **full loop** — an agent
is given the need, authors its *own* query against the index (exposed as the
`search-codex.py` tool), reads the candidates, and selects a symbol. Blind to the
target. Run as independent Claude Opus 4.8 subagents through developer tooling
(ADR-0010), the way RAD-0018 ran.

## Method

- Tool: `search-codex.py` — vector-primary (BGE-M3, MIT, in-process), returns top-10
  candidates as JSON (semantic face only: `capability` + `triggers`, never the target
  flag). Corpus vectors cached by `embed-corpus.py`.
- 10-query pilot spanning all three Layer-1 difficulty bands: 4 that Layer-1 vector
  already got at #1, 3 in the recoverable tail (#3–#6), and the **3 hard misses** Layer-1
  vector put beyond top-10.
- Each agent: one need, may author up to ~4 refined queries, picks one symbol. No target
  disclosed.

## Result — 10/10

| Need (target) | L1 vector rank | L2 chosen | ✓ | agent's authored query (abridged) |
|---|---|---|---|---|
| Policy | #1 | Policy | ✓ | "retry … with exponential backoff between attempts" |
| Breaker | #1 | Breaker | ✓ | "circuit breaker trip open … fail fast" |
| Money | #1 | Money | ✓ | "exact decimal arithmetic for money … no floating point" |
| CursorPager | #1 | CursorPager | ✓ | "cursor-based pagination … avoiding duplicates across pages" |
| Coalescer | #6 | Coalescer | ✓ | "delay reacting to rapid input until typing stops, latest value" |
| RolloutGate | #3 | RolloutGate | ✓ | "gradual percentage rollout … sticky bucketing" |
| DistLock | #5 | DistLock | ✓ | "distributed lock … auto-expiry if holder dies" |
| **BloomFilter** | **#27** | BloomFilter | ✓ | "probabilistic set membership … allowing false positives" |
| **StateMachine** | **#62** | StateMachine | ✓ | "enforce allowed state transitions, reject illegal" |
| **EventBus** | **#120** | EventBus | ✓ | "publish/subscribe in-process events … no direct references" |

Every agent: **1 tool call, high confidence.** L2 100% vs L1 vector 77% r@1 / 88% r@10.

## What this shows

- **Query authoring is the Layer-2 value.** The agent maps the caller's plain-words need
  to the capability's *technical* vocabulary — the exact gap the paraphrastic queries were
  built to expose. That authored query retrieves the target at #1 even where the verbatim
  need ranked #27–#120. The full loop **beats** raw recall.
- **Selection was barely stressed.** Because authoring alone lifted the target to #1, the
  "pick the right one from a noisy top-10" mechanism was rarely the deciding step. Both are
  Layer-2 levers; here authoring dominated.

## Boundary / honesty

- **N=10 pilot, single model** (Claude Opus 4.8 subagents), one attempt each. Full 26 +
  local/Gemini cross-model is the scale follow-on, matching Layer 1's own pilot→scale path.
- **The corpus is well-known CS patterns**, so the agent already *knows* the canonical
  vocabulary to author (bloom filter, state machine, pub/sub). For genuinely novel or
  *local* capabilities — the codex's distinctive value (RAD-0016) — the agent may not know
  what to search for, and query authoring gets harder. That is the case to test next, and
  the boundary this pilot does not cross.
- Synthetic corpus; a harvested one (RAD-0011) is the external-validity check.
