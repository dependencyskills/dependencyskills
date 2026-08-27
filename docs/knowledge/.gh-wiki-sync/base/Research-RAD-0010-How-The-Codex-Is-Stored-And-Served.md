# How the Codex Is Stored and Served

RAD-0010 · 2026-08-17 · v3
Keywords: how is the codex stored and queried; SQLite FTS5 versus Lucene; lexical versus vector versus hybrid retrieval; why equal-weight RRF hurt; text as source of truth and the index as derived; serving over MCP; search and get; scoping results to a project.

**Reasoned, not measured.** This record argues about structure. The figures it
leans on come from RAD-0001 (measured 2026-08-13) and from adjacent
skill-routing and skill-collision research surveyed in the project's withdrawn
librarian/codex design; those external figures are flagged where used and
should be re-cited against their sources before they are load-bearing. The Lucene
capability and license claims come from general knowledge and the tools reviewed
in RAD-0009/0013; **verify before load-bearing.** No new measurement here.

**v2 note.** v1 leaned lexical-only (SQLite FTS5) and put vectors off as likely
over-engineering. [RAD-0013](Research-RAD-0013-The-Codex-Entry) then defined the entry and
showed *finding* a capability is a **meaning** match — "retry with backoff" must
reach "resilience policies" — which is exactly what lexical-only cannot do. So
retrieval is now **hybrid**, and the index engine is **Apache Lucene**
(lexical + vector + fields in one embedded library), superseding SQLite FTS. The
storage bones — text source of truth, generated split from authored, index as a
disposable derived cache — **carry over unchanged**; Lucene solves *storage and
retrieval*, not parsing (RAD-0009). This record is the store/serve layer only.

## Terminology this record fixes

Two terms have been used interchangeably and should not be:

- **Codex** — the processed catalogue of scraped library data: the need → library
  index an agent searches. It is *data*.
- **Librarian** — the skill that triggers at the right moment and knows how to
  consult the codex. It is the always-resident *nudge*, not the data.

The rest of this record is about the **codex**. The librarian is layer one and
is out of scope except where the two meet.

## Question

The codex is the last large undecided piece of a testable system. How is it
stored, retrieved, and served? Concretely: where do the entries live, what index
serves them, and where does the MCP idea fit — as a rival to those or as
something above them?

## Trail

### Storage: source of truth is text, index is derived

The codex has two kinds of content, and only one is generated. Most entries are
**generated** — the two-faced per-capability records of RAD-0013 (coordinate,
version, tier, the caller-words `capability`, `symbol`/`signature`/`sample`,
`not-for`, provenance). A minority is **hand-authored**: which of several
overlapping libraries *this* project reaches for, and why not the others. That
second part cannot be harvested — no library knows what else is on your classpath
(RAD-0007) — and it is the part that actually shortcuts the churn.

That split decides the format. The source of truth should be **text** — the
generated entries **serialized per-coordinate** (structured, but on disk as text)
plus a hand-edited local file — because text is git-native and diffable (a
library's entry changes visibly in a pull request), hand-editable (the
local-preference part lives here), regenerates cleanly, and is legible without
the index. A binary store as the *source* throws all of that away.

The operative structural move, which also answers a question the withdrawn design
left open — *where does local preference live so regeneration does not destroy
it?* — is to **separate generated from hand-authored**:

```
codex/
  generated/<coordinate>.*   the RAD-0013 entries, serialized; safe to delete and rebuild
  local.md                   preferences, negative guidance; never regenerated
  index/                     derived Lucene index (see below); gitignored
```

Regenerating the codex means blowing away `generated/` and the index and
rebuilding from source — a safe operation, which is a property worth designing
for. The entry *schema* is RAD-0013's; this record only says the generated tier
is serialized to text and the authored tier is hand-written.

### Retrieval: hybrid — semantic to find, exact to get, fields to scope

Scale is the whole point of the project, so it decides retrieval. A real graph
is 311 libraries (Now in Android) to 995 (a Next.js p90); reading the entire
codex back into context is the resident-cost problem the design exists to kill.

- **Small (a test, or under ~150 entries):** no index — the agent reads the codex
  whole. Simplest, and it works to a real size.
- **Large:** a derived index over the entries. RAD-0013 fixed what it must do —
  **all three of**: **semantic** search over the embedded `capability` (discover
  a need by meaning — the part v1 got wrong), **exact** lookup by
  `coordinate`/`symbol` (a precise get), and **field filters** on `tier`/`version`
  (scope before ranking). That is **hybrid** retrieval, not lexical-only and not
  vector-only.

The candidate-set shape v1 argued for **survives**: adjacent skill-routing
research reports retrieval is far better at *not losing* the winner (top-5
≈ 85–90%) than at picking it top-1 *(external figures — re-cite)*, so the index
returns 5–10 candidates and the agent reads and chooses. What changes is the
*mechanism*: semantic discovery replaces lexical-only, which also dissolves v1's
verb-saturation failure mode (meaning-match does not care that "create"/"get"
appear everywhere).

### The index engine: Lucene does the hybrid in one embedded library

**Apache Lucene (9+)** provides BM25 lexical search, **kNN dense-vector search
(HNSW)**, structured fields, filtering and boosting — in **one Apache-2.0
embedded library** (RAD-0013). It maps onto the entry directly: keyword fields
for `coordinate`/`symbol`/`tier`, a vector field for the embedded `capability`,
plain fields for `version`. It runs on the **JVM**, where the harvester already
runs (parsing `-sources.jar`), and it is **embedded** — a single index directory,
no separate vector-DB service. This replaces v1's SQLite FTS5, which could not do
the vector half. Lucene stays a **derived, disposable cache** rebuilt from the
text, exactly as SQLite would have — gitignored, never the source of truth. Its
Python (PyLucene) and .NET (Lucene.NET) ports mean the engine is not JVM-locked
if a non-JVM harvester or server ever needs it, though index-format portability
across the ports is not assumed. The embeddings themselves are generated
**outside** Lucene (a local or hosted model); Lucene stores and searches the
vectors.

### Serving: the file, then MCP as the interface over it

The two serving shapes are not rivals; they stack.

- **As a linked file:** the librarian points the agent at the codex, which it
  reads (whole, when small; via the index, when large). Linking to an index beat
  inlining it "at a fraction of the tokens" in the llms.txt agent benchmark
  *(external figure — re-cite)*.
- **As an MCP server:** two tools — `codex.search(need) → candidates` (the
  **semantic** query over Lucene) and `codex.get(coordinate|symbol) → entry` (the
  **exact** get). That is Catalog → Inspect → Execute, the shape RAD-0008 found
  the field converging on, and it composes with the file design: MCP queries the
  index and hands back entries.

An MCP tool the agent knows exists is itself a trigger surface, so it does part
of the librarian's job. It does **not** replace the librarian, though: RAD-0003
established that the resident trigger is irreducible, because a pull interface
cannot fire on *reinvention* — the case where the agent never thinks to ask.
MCP is the query layer; the always-resident nudge stays.

## Findings

**Measured (RAD-0019, 2026-08-20) — the fusion policy is revised to vector-primary.**
A Layer-1 recall eval over 220 entries with 26 *paraphrastic* queries (the caller's need
in words that avoid the entry's vocabulary) found the **vector arm decisively best**
(recall@1 77% vs lexical 38%; lexical plateaus at 58% by r@10), and — the surprise —
**equal-RRF hybrid *hurt*** (r@1 13/26, below vector's 20/26): fusing the strong vector
arm with a near-useless lexical arm drags the good hits down, and even 2:1 vector-weighting
does not beat vector alone. So the hybrid store stands (Lucene holds BM25 + HNSW in one
artifact), but the default **fusion is vector-primary, not equal RRF** — lexical is a
supplement for queries carrying exact high-signal terms (a symbol, "LRU"), i.e. fusion
should be **query-adaptive**. The encoder is load-bearing: a strong, permissively-licensed
one (**BGE-M3, MIT**) is the pick; weak encoders under-retrieve.

**Measured (from RAD-0001).** The codex must hold hundreds to ~1,000 entries for
a real graph, at a resident cost of 20k–139k tokens if read whole — which is why
retrieval, not whole-file reading, is mandatory past a small size.

**Reported in adjacent research, to re-cite.** Retrieval favours recall over
precision (top-5 ≈ 85–90%, top-1 low), which argues for returning a candidate set
rather than a single answer; linking an index beats inlining it on tokens.

**Reasoned.**
- Text is the right source of truth and a binary store is not, because of git
  diffing, hand-editing of local preference, and clean regeneration; the
  generated entries (RAD-0013) are serialized to text, the index is derived.
- Separating generated from hand-authored is what makes regeneration safe and
  local preference durable — the property the withdrawn design flagged as unsolved.
- Retrieval must be **hybrid** (RAD-0013), which corrects v1's lexical-only lean;
  **Lucene** serves all three modes in one embedded JVM library and supersedes
  SQLite FTS.
- The index is a derived, disposable cache, not the source.
- MCP is the serving interface, not an alternative structure, and does not retire
  the resident trigger.
- Lucene solves storage and retrieval; it does **not** parse — parsing is
  RAD-0009, a separate layer.

## Recommendation

**Source of truth: text.** The RAD-0013 entries serialized under
`codex/generated/`, plus a never-regenerated `codex/local.md` for preference and
negative guidance.

**Retrieval: hybrid, staged by scale.** Read the whole codex while it is small;
build a derived **Lucene** index — semantic over `capability`, exact over
`coordinate`/`symbol`, filtered by `tier`/`version` — and return 5–10 candidates
once it is not. This supersedes v1's SQLite-FTS lexical-only recommendation.

**Serving: MCP over the index, once past the test.** `search` (semantic) and
`get` (exact) tools; the librarian skill remains the resident trigger regardless.

**Build it in stages:**
- **v0 (the test):** one text codex, read whole, a librarian skill pointing at
  it. No index, no MCP. This is what the content-value spike needs and nothing more.
- **v1:** `generated/` + `local.md` → **Lucene hybrid** index → local MCP server.
  Text is truth, Lucene is the query cache, MCP is the door.
- **v2:** the same index prebuilt and shipped — the central corpus of RAD-0003.

**What would change the answer.** If the hand-built v0 codex does not change agent
behaviour at all, none of this structure matters and the project's premise is
wrong — the cheapest thing to test first. Lucene's vector support and license are
to be confirmed before it is load-bearing; if it does not fit, the hybrid
requirement stands and the engine is swapped, not the design.

## Connections

- [RAD-0013](Research-RAD-0013-The-Codex-Entry) — the entry this stores and the hybrid
  retrieval and Lucene candidate this record adopts; the source of the v2 change.
- [RAD-0009](Research-RAD-0009-Reusing-Indexers-And-What-To-Index) — the parse that fills
  `generated/`; a separate layer Lucene does not cover.
- **RAD-0001** — the scale (311–995 entries, 20k–139k tokens) that makes
  retrieval mandatory rather than optional.
- **RAD-0003** — the local MCP server as the serving interface, and the
  irreducibility of the resident trigger that MCP does not replace.
- **RAD-0007** — the hand-authored, un-harvestable local preference that
  `local.md` exists to hold.
- [RAD-0014](Research-RAD-0014-Build-Vs-Reuse) — whether to assemble this store from Lucene
  or adopt an end-to-end pipeline that bundles one.
