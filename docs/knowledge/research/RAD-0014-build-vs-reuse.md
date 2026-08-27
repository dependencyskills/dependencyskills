# Build vs Reuse: the Codex Pipeline

RAD-0014 · 2026-08-19 · v4
Keywords: which pipeline layers do we build and which do we reuse; tree-sitter, Dokka, Lucene, Commons VFS; assemble from libraries or adopt a whole pipeline; CocoIndex and Roo-Code; why Glean is a template not a dependency; why Mahout is the wrong layer; what is genuinely ours to build.

**Reasoned, not measured.** This record decides, per pipeline layer, whether to
reuse an existing tool or build. The tool capabilities and licenses come from
general knowledge and the tools reviewed 2026-08-17/19 (tree-sitter, Dokka,
Lucene, Commons VFS, CocoIndex, Roo-Code, Glean, Mahout); **verify each on its
artifact before it is load-bearing.** No new measurement.

**v2 note.** Adds the **read** layer, named after this record was first written
(RAD-0015) — another reuse (Commons VFS / the Kotlin core VFS).

**v3 (2026-08-22) — summarise is measured as load-bearing.** This record named **summarise**
and **curate** as the two layers to build rather than reuse, on reasoning.
`experiments/test5` now measures the first: at matched corpus size, entries built from **raw
harvested doc text retrieve at 29% r@1 against 77% for entries written in a caller's words**
(same encoder, same queries, 220 entries either way). Without the summarise step retrieval is
roughly a third as good, which puts it on the product's critical path rather than in its
backlog.

**v4 (2026-08-25) — the retrieval half of v3 is withdrawn.** The 77% was measured on entries
**written by hand**. [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) ran the built
summariser over the same 220 entries, the same 17 queries and the same encoder: raw and
summarised **both** retrieve the correct answer first **5 of 17**, and summarised trails in the
tail. The rewriter is retrieval-neutral at the head, and the 2.6× lift this note attributed to
*summarising* belonged to *who wrote the summary*.

Summarise stays on the critical path, on a different and still-measured basis: it is the
**quarantine** (`test7` — 0 of 3 harm, 2 of 3 task), and quarantine was always the stronger
claim. What is withdrawn is the argument that it is also the retrieval mechanism. Nothing in
the build-vs-reuse verdict changes — this layer is still built rather than reused — but the
reason recorded here was half wrong and the half that failed is the one most often quoted.

## Question

The pipeline is **get → read → parse → (summarise) → embed → store → query**. Most of it
is a solved problem *somewhere* — IDEs parse, search engines index, RAG stacks
embed. So the decision is not "how do we build each stage" but, per stage,
**reuse or build** — and, above that, **assemble from layers or adopt one
end-to-end pipeline** that already bundles them. Getting this wrong in either
direction is expensive: building what exists wastes the project, and adopting a
substrate that does the *wrong* job locks in a mismatch.

## Trail

### The layers, and who already solves them

| Layer | Reuse candidate | Verdict |
|---|---|---|
| **get** | Maven/Gradle resolution; `-sources.jar` | **reuse** — ADR-0009, settled |
| **read** | Commons VFS (bytes/remote); Kotlin core VFS (Dokka) | **reuse** — RAD-0015 |
| **parse** | tree-sitter (broad), Dokka (Kotlin) | **reuse** — RAD-0009 |
| **summarise** (KDoc → caller-words capability) | — | **build** — the new work |
| **embed** | a local or hosted embedding model | **reuse** — commodity |
| **store + index + retrieval** | Lucene (hybrid, embedded, JVM) | **reuse** — RAD-0010 of 0013 |
| **curate** (tier, local preference, `not-for`) | — | **build** — the value |

Two layers are genuinely ours, and they are the same two the whole project rests
on: **summarise** (turning shipped docs into a capability in the caller's words —
RAD-0011/0013) and **curate** (which library *this* project reaches for, and why
not the others — RAD-0007). Neither is something a parser or an index does. Every
other layer is reuse. That is the point: the thesis is "the content already
ships," so the machinery to move it should already exist too — the project's
originality is in the two judgement layers, not in re-solving parsing or search.

### The fork above the layers: assemble, or adopt a pipeline

Two projects package several of these layers together, and the real architectural
choice is whether to take one of them whole:

- **CocoIndex** — tree-sitter parse + embeddings + a store (SQLite/LMDB/Qdrant),
  wired as one incremental indexing pipeline; reported ~70% token savings feeding
  Claude Code. It is the closest thing to "our pipeline, already assembled." But
  it indexes **raw code for semantic code-search** — it has no *summarise* step
  and no *curate* step, which are exactly our two build layers. So it is a
  **substrate at best**, not the product: adopt it and we still bolt the two
  judgement layers on, and inherit its store choice instead of Lucene's hybrid.
- **Roo-Code** — the same shape (tree-sitter + embeddings + Qdrant) but embedded
  in an editor extension; even more clearly a code-search feature, not a reusable
  substrate.

Against that, **assemble from layers** — tree-sitter/Dokka + our summarise +
embed + Lucene + our curate — keeps the store as Lucene's *hybrid* (lexical +
vector + fields, RAD-0013), which the entry needs and CocoIndex's vector-only
search does not give, and keeps each layer swappable. The cost is integration we
own rather than inherit.

### Glean is the template, not a dependency

Meta's **Glean** is the industrial version of this shape — per-language indexers
producing facts into a common schema, queried uniformly. We are unlikely to *run*
Glean (it is heavy, Meta-scale infrastructure), but its **architecture is the
template**: the `(declaration, doc)` contract of RAD-0009 and the entry schema of
RAD-0013 are our "common schema," and per-ecosystem parsers are its producers.
Borrow the shape; do not take the dependency.

### Mahout is the wrong layer, and is gone anyway

Apache **Mahout** came up as a Lucene-family option. It is not one for us on two
counts. **Wrong layer:** Mahout was ML — clustering, classification,
collaborative-filtering recommendation, distributed linear algebra on
Spark/Hadoop — which is not the *search/retrieval* the codex needs; Lucene covers
retrieval and there is no distributed-ML stage in the pipeline. **Abandoned:** the
project has since pivoted to quantum computing ("Qumat") and the classic ML is no
longer its focus. The recommendation flavour it hinted at is real but light — a
central-corpus co-occurrence signal ("projects that depend on X also depend on Y")
and overlap clustering of capability embeddings (RAD-0007) — and both are computed
directly on data we already hold (dependency graphs; the vectors Lucene stores),
not with a distributed-ML framework. **Rejected.**

## Findings

**Reasoned.**

- The pipeline is **mostly reuse**; exactly two layers are ours — **summarise**
  and **curate** — and they are the project's actual contribution.
- The end-to-end pipelines (CocoIndex, Roo-Code) do **code-search**, not
  capability-curation: they lack both our build layers and lock in a vector-only
  store. Substrate at best, not the product.
- **Assemble from layers** keeps Lucene's hybrid store (which the entry requires)
  and keeps every layer swappable, at the cost of owning the integration.
- **Glean** is the schema/architecture template, not a dependency.
- **Mahout** is rejected — wrong layer (ML, not search) and abandoned (pivoted to
  quantum).

**To verify / measure.**

- CocoIndex's incremental-indexing internals — worth mining even if not adopted;
  confirm it is genuinely vector-only before ruling it out as a store.
- Licenses on the artifacts (tree-sitter MIT, Dokka/Lucene Apache-2.0, CocoIndex)
  before any is load-bearing.
- Whether assembling Lucene + our parse actually beats standing CocoIndex up as a
  spike substrate on time-to-first-result — a build-order question, not an
  architecture one.

## Recommendation

**Reuse at every layer except the two that are ours.** Get (ADR-0009), parse
(tree-sitter/Dokka, RAD-0009), embed (a commodity model), and store/retrieve
(Lucene, RAD-0010/0013) are reuse. **Build** the **summarise** step (docs →
caller-words capability) and the **curate** step (tier + local preference +
`not-for`) — that is where the project's value is.

**Assemble from layers; do not adopt an end-to-end pipeline as the product.**
CocoIndex/Roo-Code are code-search, missing both judgement layers and locking in a
vector-only store; keep Lucene's hybrid and keep the layers swappable. Mine
CocoIndex's incremental indexing for ideas, and consider it only as a throwaway
spike substrate if it gets to a first result faster — never as the architecture.

**Borrow Glean's schema shape; reject Mahout.** The common-schema/per-language-
producer pattern is the template; Mahout is the wrong layer and abandoned.

## Connections

- [RAD-0009](RAD-0009-reusing-indexers-and-what-to-index.md) — the parse layer's
  reuse decision (tree-sitter/Dokka).
- [RAD-0015](RAD-0015-how-the-source-is-read.md) — the read layer's reuse decision
  (Commons VFS / the Kotlin core VFS).
- [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) — the store layer's reuse
  decision (Lucene), which this record's fork could override with a pipeline.
- [RAD-0013](RAD-0013-the-codex-entry.md) — the entry whose hybrid requirement rules
  out the vector-only pipelines as the store.
- [RAD-0011](RAD-0011-existing-documentation-systems-as-skill-content.md) — the
  summarise layer, one of the two build layers.
- [RAD-0007](RAD-0007-choosing-between-overlapping-libraries.md) — the curate layer,
  the other build layer.
- [RAD-0008](RAD-0008-the-field-as-it-stands.md) — the field survey these tools were
  weighed against.
