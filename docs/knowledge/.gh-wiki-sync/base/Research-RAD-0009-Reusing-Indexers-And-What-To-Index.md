# Parsing the Documentation, and What to Index

RAD-0009 · 2026-08-19 · v6

**Licenses:** tree-sitter MIT, Dokka Apache-2.0 (Dokka verified 2026-08-17; the
rest to confirm on the artifacts). Capabilities are from general knowledge and
the tools reviewed 2026-08-17; verify behaviour before load-bearing. No new
measurement.

**v2 note.** v1 framed this as "reuse the IDE's *structural* indexers." That was
aimed at the wrong content: the codex is built from **documentation**, not the
API surface. The structural/bytecode path is split to
[RAD-0012](RAD-0012-structure-from-bytecode.md) as a deferred lever. This record is
now the **parse stage** — turning source into the two faces of a codex entry
([RAD-0013](RAD-0013-the-codex-entry.md)) — plus *what to index*. This is one layer:
parsing. Storage and retrieval are a different layer (Lucene, RAD-0010); Lucene
does not parse.

**v3 note.** Sharpened while choosing the test parser. **Dokka** is now named the
intended *production* Kotlin parser — its enriched model (inherited docs, resolved
symbols, `@sample` expansion) is content we want — with **tree-sitter** the
broad/simple, cross-ecosystem layer. The first spike **bakes the two off** on one
Kotlin library (raw vs enriched), which doubles as the content-value measurement.
Adds the **adapter** seam (parser model → entry) and clarifies the harvester need
not be a KMP project but must *understand* KMP projects. Swift and JS are the
next ecosystems; Kotlin is first.

**v4 (2026-08-19).** First empirical results folded in — the `experiments/test0`
spike ran a raw arm and an enriched arm over graded synthetic Kotlin (see
*Measured* under Findings). Headline: **doc level dominates, parser enrichment is
marginal on well-structured source.** Binary/structure parsing is split to its own
test suite (RAD-0012), not this doc-parse bake-off.

**v5 (2026-08-21).** The synthetic spike is replaced by real `-sources.jar`
(`experiments/test1`). The tree-sitter raw arm runs on real Maven artifacts across
five libraries and cross-checks the independent coverage tool; the same
`tree-sitter-language-pack` rig, pointed at real Swift / TypeScript / Python / Rust
source (Phase B), confirms the parse layer is genuinely **language-agnostic** and
that resolve-in-index generalises beyond Kotlin. See *Measured — test1* under
Findings.

**v6 (2026-08-21).** The Dokka arm (standalone, Dokka 1.9.20 on JDK 21) reframes
the parser question. Single-library Dokka realizes little inherited-doc
enrichment, because thin-library enrichment lives in the **cross-library graph**,
not the parser — demonstrated by `resolve_in_index.py` realizing inherited docs
from tree-sitter alone via a transitive graph join, no Dokka. Net: **bundle
both** (tree-sitter as the universal baseline, Dokka for Kotlin *resolution*),
and invest the enrichment effort in the composition graph rather than the parse
stage. See *Measured* under Findings.

## Question

The pipeline is **get → parse → store → query**. Get is decided (ADR-0009:
content comes from `-sources.jar`). This is **parse**: how is the source turned
into codex entries, and what is indexed?

## Trail

### Parse the documentation, not the structure

A codex entry has two faces (RAD-0013): a **semantic** capability (what it is
for, in caller's words) and a **syntactic** usage (signature, sample). Both come
from the **documentation-bearing source** — the KDoc gives the capability, the
declaration it sits on gives the signature. Bytecode gives only the structure and
no docs, so it is not the parse path (RAD-0012 holds it as a deferred option for
the *undocumented* tail).

### Many parsers, one contract — the ADR-0007 shape applied to parsing

There is no single parser. A Node project and a KMP project have different source
and different tools, so the parse stage is **per-ecosystem behind a common
contract**: each parser emits `(declaration, doc-comment)` pairs, and a summarise
step turns those into entries. The tools reviewed 2026-08-17 sort into two roles:

- **Tree-sitter** — the broad, simple layer. An incremental, error-tolerant
  *syntactic* AST with grammars for many languages, one framework and a grammar
  per ecosystem — "embrace many parsers" without N toolchains. It parses source
  *text*, so it needs no compilation or classpath. Syntactic only: it gives the
  declaration and the raw doc-comment, no type resolution, no inherited docs, no
  `@sample` expansion.
- **Dokka** — the intended **production Kotlin parser**. It runs Kotlin analysis
  to build an enriched documentation model: resolved symbols, inherited and
  overridden docs, `@sample` expansion, resolved KDoc links. That richer content
  is what we want in the codex; the cost is that it needs the classpath and a
  plugin to emit the model. Heavier than tree-sitter, and Kotlin-only, but the
  quality is the point.

These two are not a paper decision. Tree-sitter is raw and cheap, Dokka is
enriched and heavier, and the gap between raw and enriched KDoc *is* the
content-value question — so the first test runs **both** on the same Kotlin
library and compares (the bake-off, below).

Other ecosystems bring their own parser, on the same broad-plus-rich pattern:
**Swift** (SwiftSyntax / SymbolGraph) and **JS/TS** (the TypeScript compiler API,
`.d.ts`) are the next targets after Kotlin; `go/doc`, Pyright/`.pyi` and Eclipse
CDT (C/C++) follow. Meta's **Glean** is the industrial template for the whole
shape — per-language indexers producing facts into a common schema — worth mining
for the contract even if not run.

### The adapter decouples the parser from the entry

Each parser emits its *own* model — tree-sitter a syntax tree, Dokka a
documentation model — so an **adapter per parser** maps that model onto the one
RAD-0013 entry (and thus Lucene's fields). The parser is swappable behind its
adapter: choosing tree-sitter or Dokka, or adding Swift, changes an adapter, not
the entry schema or the index. Feeding Dokka's richer model to Lucene is an
adapter concern, not an entry-schema change.

### The first test bakes the two off

The v0 spike parses each library **twice** — tree-sitter (raw KDoc) and Dokka
(enriched) — into entries, and compares what each surfaces and how each drives an
agent. Run it across a **spread of Kotlin libraries spanning the coverage
spectrum** — some with little or no KDoc, some richly documented — not one point,
because the interesting cases are at the ends: the thin ones test whether Dokka's
inherited docs (and, later, the RAD-0012 symbol-name signal) recover anything from
near-nothing, and the rich ones test whether enrichment adds anything over raw.
The sample is grounded, not guessed: RAD-0011 already measured KDoc coverage
across a **90-coordinate sample** (`experiments/cost-model/data/`), so libraries can be
picked by known coverage. One spread settles two things: which parser to carry
forward for Kotlin, and how much the enrichment is worth — the content-value
question RAD-0011 poses. Kotlin is first because it is the easiest surface to
validate by eye; Swift and JS follow.

### AST, not line-based

Real indexers (IntelliJ stubs, tree-sitter, Sourcegraph) parse to an AST, because
a regex "comment immediately before a declaration" breaks on annotations,
multiline signatures and nested types — RAD-0002's own line-based coverage
measure *undercounts* for exactly that reason. So the parse is **AST-based**:
tree-sitter for a fast syntactic tree, Dokka where resolved semantics are worth
the weight.

### KMP widens the consumers, not the parse

A KMP library's source is `commonMain` Kotlin, so its parse *is* the Kotlin
parse (Dokka / tree-sitter-kotlin). The breadth KMP forces is in the *targets
that consume it* (JVM/JS/Native/Wasm), not in parsing its source. So **start with
Kotlin** and it covers KMP; the wide parser range is for the *other* ecosystems.

The harvester itself **need not be a KMP — or even a Kotlin — project**; it must
*understand* KMP projects: read their common and platform source sets and their
`expect`/`actual` declarations. Understanding the shape is the requirement, not
adopting it.

### The summarise step is ours

Extracting `(declaration, KDoc)` is reuse. Turning a library's KDoc — ~59.5k
tokens for one library (RAD-0002/0011) — into its handful of caller-words
capability entries is **not** something a parser does; it is an **LLM pass**, and
it is the genuinely new work of the parse stage. It fills the semantic face; the
declaration fills the syntactic face.

### What to index: everything, ranked — not a gate

The extractors make it possible to index *any* importable library. Ignoring
libraries without a deliberate skill would cut clutter and reward adoption — but
it excludes exactly the obscure libraries where a skill helps most (value runs
inversely to training exposure), and reintroduces the cold-start problem the
"docs already ship" insight escaped. The reconciliation: **index the whole graph,
treat a deliberate skill as a preferred, higher-ranked tier** (designed vs
discovered, RAD-0011), and filter the discovered tier on a quality signal. Ignore
is a *ranking threshold*, not an inclusion gate. Open until the clutter-vs-
coverage question is measured.

## Findings

**Reasoned.**

- The parse path is the **documentation**-bearing source, not bytecode; both
  faces of an entry come from one source parse.
- It is **multi-parser, per-ecosystem, behind a common contract** — tree-sitter
  the broad/simple layer, **Dokka the intended production Kotlin parser** (for its
  enriched model), others per ecosystem, each mapped to the entry by an
  **adapter**. AST-based, not line-based.
- The harvester need not be a KMP project; it must *understand* KMP source.
- The **summarise** step (KDoc → capability entries) is the new, LLM-driven part;
  the rest is reuse.
- **Scope:** index the whole graph, rank designed over discovered; "ignore" is a
  threshold, not a gate.

**Measured — test0, first pass** (synthetic fixture, `experiments/test0`; a raw
PSI arm and an enriched arm — PSI resolved within the source set, standing in for
Dokka — over graded Kotlin L0–L3; 18 entries).

- **Doc level dominates; enrichment is marginal.** The entire raw→enriched delta is
  **1 recovered capability** (an undocumented `override` inheriting its supertype's
  KDoc) **plus 4 `@sample` expansions**. Everything else — capability text, triggers,
  category, `@since`, tier — is fixed by *what the author wrote*, identical across
  arms. The L0→L3 gap (none → a line → prose + `@since` → custom tags / designed
  tier) is where the signal is; parser sophistication is a rounding error beside it
  on well-structured source.

**Measured — test1 Phase A, real `-sources.jar` (tree-sitter arm + enrichment ceiling,
2026-08-21; `experiments/test1`).** The tree-sitter raw arm now runs on real Maven
`-sources.jar`s (fetched on demand, read in place — RAD-0015), across a six-library
KDoc-coverage spread (kaml 3% → kotlinx-cli 72%). Two findings:

- **The raw arm is validated and cross-checks the coverage tool.** tree-sitter extracts
  clean `(symbol, signature, KDoc)` entries from 42 to 1,100+ declarations, and its
  AST-based coverage tracks `kdoc-coverage.py`'s independent *line-based* figure within a
  few points on every library — two methods agreeing confirms both. The same package
  (`tree-sitter-language-pack`) also carries Swift / TS / Python / Rust grammars, so this
  rig is **Phase B (other languages) ready**.
- **On real source the enrichment ceiling is large and realizable — reversing the
  synthetic "marginal."** test0's 1+4 delta was an artifact of a fixture with no real
  hierarchies and no `@sample`s. Real libraries show a big **inherited-doc ceiling** —
  undocumented `override`s Dokka could fill from a documented supertype: kaml **144** of
  233 (its supertype kotlinx.serialization is 37% documented, its core interfaces the
  best-documented part, so much of the 144 is realizable), coroutines 372, datetime 354 —
  plus **`@sample` density** (datetime **259**). But it is **zero** for a small,
  well-documented library (kotlin-retry, 0/42). So enrichment is **not marginal in
  general — it is conditional on library shape**, largest exactly where own-coverage is
  lowest (thin + heavy interface inheritance) and for `@sample`-heavy libraries. This
  settles the parser question toward **bundling both**: tree-sitter universal, Dokka added
  for the high-ceiling Kotlin libraries, with the ceiling metric identifying which.

- **The realized-delta run (Dokka arm) reframes it: thin-library enrichment lives in the
  *graph*, not the parser.** Dokka was run standalone (Gradle + Dokka 1.9.20 on JDK 21,
  GFM output; `experiments/test1/dokka-arm`) on kaml. **Single-library** Dokka realizes
  ≈ own-docs only (~18) — the 144 override ceiling stays ~0, because kaml's supertypes
  (kotlinx.serialization interfaces) are on the *classpath* as compiled jars, not source
  roots, so their KDoc cannot be inherited: `Yaml.decodeFromString` renders as
  `open override fun` with **no doc**. Adding kotlinx.serialization's **source** to the
  same run flips it — `decodeFromString` then **inherits** StringFormat's full doc, its
  `@throws`, and a resolved type link. So the large inherited-doc enrichment for thin
  libraries is **realized only when the supertype source is in scope** — i.e. as a
  **cross-library link across the harvested graph** (RAD-0011's composition graph,
  RAD-0007's authoritative edges), *not* by any per-library parse. Dokka's genuine
  incremental value over tree-sitter is therefore **resolution** (resolved types,
  `@sample` inlining, *internal* inheritance); the headline thin-library enrichment is a
  codex-graph concern that any parser feeds. Net: **bundle both** (tree-sitter baseline +
  Dokka for Kotlin resolution), but invest the enrichment effort in **cross-library entry
  linking**, which dominates the parser choice.

- **Demonstrated: resolve the inheritance *in the index*, from tree-sitter alone — no
  Dokka.** `resolve_in_index.py` harvests kaml and kotlinx.serialization **independently**
  (tree-sitter, in place), each entry carrying its `override → supertype` edge (supertype
  names resolved to FQNs via the file's imports), then a **transitive graph join** walks an
  undocumented override's supertype chain through the harvested entries until it finds a
  documented member and inherits the doc. From a *two-library* index it realized **16**
  cross-library inheritances — including the exact `Yaml.decodeFromString ←
  StringFormat.decodeFromString` case the multi-source Dokka run produced, and
  `serialize`/`deserialize` reached transitively through `KSerializer`. The count scales
  with harvest coverage (the 144 ceiling includes supertypes from libraries not in the
  two-lib index — snakeyaml, stdlib `AutoCloseable`). So the pipeline splits cleanly:
  **parse = local extraction (any parser, read in place via VFS — RAD-0015); enrich =
  a graph join in the index (RAD-0010 / RAD-0011's composition graph).** Dokka's
  cross-library role is fully replaced; its residual value is *local* resolution
  (overload-exact targets, `@sample` bodies, resolved type spellings).

**Measured — test1 Phase B, the other languages (2026-08-21; `extract_polyglot.py`).** The
same tree-sitter rig, pointed at real published source in three ecosystems (fetched on
demand from npm / PyPI / crates.io, read in place), extracts clean `(symbol, signature,
doc-comment)` entries across **three different doc conventions**: Python **docstrings** (a
string as the first body statement — *not* a comment), TypeScript **JSDoc** (`/** */`
blocks, on `.d.ts`), and Rust **`///`** line docs. Results: click 332 decls / 56%,
commander 183 / 73%, anyhow 73 pub / 23% own-docs. Two findings: (1) the **language-agnostic
parse behind a common contract is real** — one `walk` with per-language declaration-kinds
and a doc-comment rule covers all three, and because resolution is index-side each language
needs only *local* extraction, no resolver; (2) **resolve-in-index generalises** — Rust
`impl Trait for T` methods are undocumented locally because their docs live on the trait,
the identical cross-entry inheritance link as Kotlin `override`s. **Swift** completed the
set (`apple/swift-argument-parser`, fetched from GitHub since SwiftPM has no tarball
registry — 515 decls, 33%, `///` docs). So **five languages across four doc conventions**
(block `/** */` for Kotlin/TS, Python docstring, `///` line for Rust/Swift) all extract
behind one contract with the same rig — the language-agnostic parse claim validated end
to end.
- **The callable can be undocumented while the capability sits on its container.**
  The opaque `Policy.apply` is blank at every level because the KDoc is on the class
  `Policy`, not the method. A codex must **associate a class's doc with its primary
  callable** (or index the capability at the class), or it misses the opaque-API case.
- **Sample helpers leak in** — `@sample` targets surface as their own entries; a
  harvester must exclude the samples set.
- *Caveat:* synthetic source, simple hierarchy — internal validity only; the
  `@sample` bodies sit in the same tree, which a real `-sources.jar` may not carry
  (RAD-0011), so the 4 expansions are optimistic.

**To measure.**

- **Real tree-sitter vs Dokka on a real `-sources.jar`** — the first pass above used
  PSI stand-ins on synthetic source and found enrichment marginal there; the real
  parsers on a real library, resolving across binary deps and deep hierarchies, is
  what actually settles the Kotlin parser choice.
- Whether the summarise pass reliably produces good caller-words capabilities
  from thin (33%, RAD-0011) KDoc — the content-value question, the v0 spike.
- The clutter-vs-coverage question for the discovered tail (build it both ways,
  compare selection accuracy).

## Recommendation

**Parse the documentation-bearing source, per ecosystem, behind a common
contract, each parser mapped to the entry by an adapter.** Tree-sitter is the
broad/simple layer; **Dokka is the intended production Kotlin parser** for its
enriched model. **Start with Kotlin** (it covers KMP, and is easiest to validate)
and **test tree-sitter and Dokka head to head** there before committing.
AST-based. The harvester must understand KMP source, but need not be KMP itself.

**Own the summarise step** — KDoc → two-faced capability entries (RAD-0013). That
is the new work; parsing and storage are reuse.

**Index the whole graph, rank tiered.** Do not gate on a deliberate skill; leave
the exact threshold open pending the clutter-vs-coverage measurement.

**Keep the layers separate.** This is parse only. Storage, the codex, and
retrieval are Lucene (RAD-0010, RAD-0013) — a different layer Lucene does not
reach into; parsing is not solved by the index.

## Connections

- [RAD-0013](RAD-0013-the-codex-entry.md) — the two-faced entry this parse produces.
- [RAD-0010](RAD-0010-how-the-codex-is-stored-and-served.md) — the storage/retrieval
  layer (Lucene) this feeds; a distinct layer.
- [RAD-0012](RAD-0012-structure-from-bytecode.md) — the bytecode/structure path split
  off, deferred.
- [RAD-0011](RAD-0011-existing-documentation-systems-as-skill-content.md) — the
  content the summarise step works from, and its coverage limit.
- [RAD-0002](RAD-0002-existing-documentation-systems-as-skill-transport.md) — get;
  `-sources.jar` is the carrier this parses.
- [RAD-0014](RAD-0014-build-vs-reuse.md) — build-vs-reuse across the pipeline; parse
  is a reuse layer.
