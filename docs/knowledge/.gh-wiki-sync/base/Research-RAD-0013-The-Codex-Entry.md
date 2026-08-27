# The Codex Entry

RAD-0013 · 2026-08-19 · v3
Keywords: what is the atomic unit an agent retrieves; a capability rather than a library or a symbol; the two-faced entry; syntactic face versus semantic face; the entry schema; why the retrieval key need not be what is displayed; semantic to find and exact to use; filtering by field.

**Reasoned, not measured.** This record defines a unit and the retrieval it
implies. The technique claims — that embedding a description gives meaning-match
retrieval, and that Apache Lucene does lexical + vector + field search in one
embedded engine — are from general knowledge and the tools reviewed in RAD-0009;
**verify Lucene's current vector support and license (Apache-2.0) before it is
load-bearing.** No new measurement.

## Question

What *is* a codex entry — the atomic unit an agent retrieves and uses? Parse
(RAD-0009) and store (RAD-0010) both depend on it, because it is the contract
between them, and it had been assumed to be "a per-library markdown blob" without
ever being defined. This record fixes it. It is how we are thinking about what we
need to build.

## Trail

### The unit is a capability — not a library, not a symbol

The codex maps a **need** to a **library + path**, and a need is a *capability*.
So the unit is per-capability:

- **Per-library** is too coarse — "ktor does HTTP-ish things" does not answer
  "which library for retry with backoff."
- **Per-symbol** is too fine — that is the IDE's index, and it is the
  "unbounded over time" failure. A library contributes its handful of
  caller-facing capabilities (Ktor ~10–20), not its 500 symbols.
- **Per-capability** sits between and matches the job.

The set is bounded by the **resolved graph** (one version per library) and
regenerated, so it does not accumulate versions.

### An entry has two faces

The codex does two jobs, and they need different content:

- **Find it** — match a *need* to a capability. This is a *meaning* match: a
  caller says "retry with backoff," the library says "resilience policies," and
  the entry has to connect them (RAD-0011). So the content is the **capability
  in the caller's words**, retrieved **semantically** (by embedding), not by
  keyword.
- **Use it** — once found, actually call it. This needs the **symbol, the
  signature, a sample** — **syntactic** content.

Finding is not using. An entry that only says *which library* leaves the agent
to guess the call. So every entry carries **both faces**.

**Measured (RAD-0016, 2026-08-19; v2).** A content-value A/B confirms and sharpens
this split. With the entry *inlined*, the **syntactic face drives use**: a *bare*
entry — symbol + signature, no prose — got an agent to use the capability in **7 of
8** cases, versus **0** with no entry. The **semantic prose did not drive use**; it
earned its keep only at the margin, where the signature could not establish fitness
for the task (and more for a cautious model than a trusting one). So: the **semantic
face is for discovery** (and disambiguating fitness); the **syntactic face is for
use**.

**Measured (RAD-0017, 2026-08-20).** The disambiguation half is now shown too. With the
*whole catalogue* in front of the agent — four real capabilities plus four opaque-named
distractors with near-identical signatures — the **syntactic face also disambiguates
where signatures differ** (a constructor parameter apart), but **fails where signatures
collide**: on two symbols with identical signatures, six subjects split
correct/reinvent/wrong/both (only 3/6 correct — every failure a local model), and one
*confidently picked the wrong capability*. Adding the **semantic face made all six
correct** and prevented the mis-pick. So the semantic face is not only for discovery-by-embedding but for
**disambiguation and safety** among look-alikes — a second, independent reason it belongs
in every entry. Search-at-scale retrieval (the semantic face as a *retrieval key* over an
index of hundreds) is still ahead.

### Both faces come from one source parse

`tree-sitter`/Dokka over the `-sources.jar` (RAD-0009) yields the **declaration**
— the syntactic signature — alongside its **KDoc** — the raw material for the
semantic capability description; a summarise pass turns the KDoc into the
caller-words capability. So the parse produces both faces from one pass, and the
deferred bytecode path (RAD-0012) is *not* needed for the syntactic face — the
source declaration already carries the signature.

### The schema

| Field | Face | Purpose |
|---|---|---|
| `coordinate` | — | `org.jetbrains.ktor:ktor-client-core` |
| `version` | — | the resolved version harvested |
| `tier` | — | `designed` \| `discovered` (RAD-0011) |
| `capability` | **semantic** | the need in caller's words — *"retry a failed HTTP request with backoff"* — **embedded**, matched on |
| `symbol` | syntactic | `io.ktor.client.plugins.HttpRequestRetry` — where it is |
| `signature` | syntactic | the declaration — how to call it |
| `sample` | syntactic | a usage example (from `@sample` or synthesised) |
| `not-for` | semantic | the negative boundary — *"not rate limiting; use …"* |
| `source` | — | provenance: KDoc @ path, or authored |

(The embedding of `capability` is derived and lives in the index, not the source
entry.)

### Retrieving it: semantic to find, exact to use, filtered by field

Three retrieval modes, and we need all three:

- **Semantic** — embed the `capability`, match a need by meaning. This is the
  discovery step, and it is why *lexical-only is wrong* (RAD-0010's earlier
  lean): "retry with backoff" must find "resilience policies."
- **Exact** — look up by `coordinate`/`symbol` for a precise get.
- **Field-filtered** — scope by `tier`, `version`, prod/dev before ranking.

That is **hybrid** — lexical + vector + structured — not vector-only (which is
all CocoIndex and Roo-Code do, over raw code) and not lexical-only.

### Lucene does the hybrid in one embedded engine

Apache **Lucene (9+)** provides BM25 lexical search, **kNN dense-vector search
(HNSW)**, structured fields, filtering and boosting — in **one Apache-2.0
embedded library**. It maps onto the entry directly: keyword fields for
`coordinate`/`symbol`/`tier`, a vector field for the embedded `capability`, plain
fields for `version`. It resolves the lexical-vs-vector either/or; it runs on the
**JVM**, which is where the harvester already runs (parsing `-sources.jar`); and
it is **embedded** — a single index directory, no separate vector-DB service.
SQLite-like operations, Elasticsearch-like capability. This is the leading index
candidate and it **supersedes RAD-0010's SQLite-FTS-lexical recommendation**.

The embeddings themselves are generated outside Lucene (a local or hosted model,
as in CocoIndex/Roo-Code); Lucene stores and searches the vectors.

## Findings

**Reasoned.**

- The entry is **per-capability**, bounded by the resolved graph, regenerated.
- It is **two-faced**: a semantic `capability` (embedded, to find) and syntactic
  `symbol`/`signature`/`sample` (to use). Both come from one source parse.
- Retrieval is **hybrid** — semantic to discover, exact to get, field-filtered
  to scope — which corrects RAD-0010's lexical-only lean.
- **Lucene** is a strong single-engine fit: hybrid search, JVM, embedded,
  Apache-2.0.

**To verify / measure.**

- Lucene's current vector-search support and license, before it is load-bearing.
- Whether the summarise pass reliably turns thin KDoc into good caller-words
  capabilities — the content-value question (RAD-0011), and the thing the v0
  spike measures.

## Recommendation

**Define the codex entry as the two-faced per-capability record above.** It is
the contract the parse and store stages meet at.

**Retrieve hybrid.** Embed `capability` for semantic discovery; exact lookup for
get; field filters for tier/version scoping.

**Adopt Lucene as the leading index candidate** — one embedded JVM engine for all
three modes — pending a license and vector-support check. This supersedes
RAD-0010's SQLite-FTS recommendation; RAD-0010 is revised to it (v2).

## Connections

- [RAD-0009](Research-RAD-0009-Reusing-Indexers-And-What-To-Index) — the parse that
  produces both faces (tree-sitter + Dokka).
- [RAD-0010](Research-RAD-0010-How-The-Codex-Is-Stored-And-Served) — storage and serving;
  its retrieval half is superseded here (hybrid/Lucene, not SQLite-FTS) and
  revised to it in v2.
- [RAD-0003](Research-RAD-0003-Central-Capability-Server) — the query layer that serves
  these entries; `search` is semantic, `get` is exact.
- [RAD-0011](Research-RAD-0011-Existing-Documentation-Systems-As-Skill-Content) — the
  summarise step that fills the semantic face, and the coverage that limits it.
- [RAD-0007](Research-RAD-0007-Choosing-Between-Overlapping-Libraries) — the local
  preference and `not-for` that no harvest supplies.
- [RAD-0012](Research-RAD-0012-Structure-From-Bytecode) — the deferred bytecode path, *not*
  needed for the syntactic face.
