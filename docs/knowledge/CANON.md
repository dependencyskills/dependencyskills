# Canon

The design records — the ADRs and RADs — version independently; each carries
its own `vN` on its metadata line, bumped when the record is meaningfully
revised or restarted. This file is the **checkpoint**: a coherent snapshot of
the whole set at a point in time, pinning the combination of versions.

The checkpoint is a **fixed value for the whole current generation of work** — it
does not bump as that work proceeds from one stage to the next. It moves only
when a line of work in it is found not to work and is **started over**, at which
point the whole canon moves together to the next value. Individual records still
bump their own `vN` as they are revised within a generation; the checkpoint does
not.

Reference material (the glossary, the landscape) is living, and postmortems and
case studies are historical; none is part of the versioned canon.

## Current checkpoint: 0.0.3 · 2026-08-17

| Record | Version |
|---|---|
| ADR-0003 — Library agent-skills as repository artifacts (superseded by ADR-0009) | v2 |
| ADR-0004 — A librarian skill and a harvested codex | v2 |
| ADR-0005 — One repository, split by whether a human is driving | v2 |
| ADR-0007 — Adopt each ecosystem's convention where it works | v2 |
| ADR-0008 — One knowledge tree under docs/knowledge | v1 |
| ADR-0009 — Library content is got from `-sources.jar` | v1 |
| ADR-0010 — Measure agent behaviour through developer tools | v1 |
| RAD-0001 — Cost of a Skill Per Dependency | v1 |
| RAD-0002 — Existing Documentation Systems as Skill Transport | v2 |
| RAD-0003 — A Central Capability Server for Library Discovery | v1 |
| RAD-0004 — External Review of the Publishing Proposal | v1 |
| RAD-0005 — A Git-Hosted Codex | v1 |
| RAD-0006 — Development-Time Prompt Injection | v1 |
| RAD-0007 — Choosing Between Overlapping Libraries | v3 |
| RAD-0008 — The Field as It Stands | v1 |
| RAD-0009 — Parsing the Documentation, and What to Index | v6 |
| RAD-0010 — How the Codex Is Stored and Served | v3 |
| RAD-0011 — Existing Documentation Systems as Skill Content | v2 |
| RAD-0012 — Structure from Bytecode | v2 |
| RAD-0013 — The Codex Entry | v3 |
| RAD-0014 — Build vs Reuse: the Codex Pipeline | v2 |
| RAD-0015 — How the Source Is Read | v1 |
| RAD-0016 — The Content-Value A/B | v2 |
| RAD-0017 — The Retrieval / Disambiguation A/B | v1 |
| RAD-0018 — The Selection A/B | v1 |
| RAD-0019 — Retrieval at Scale (Layer 1: index recall) | v1 |

## Changelog

**0.0.3 · 2026-08-17 — current.** The documentation-transport generation. After
the `-skills.zip` sidecar was measured to duplicate what libraries already ship,
the packaging line was started over around getting content from existing
carriers. This checkpoint covers the whole current design and stays fixed as it
proceeds: the premature ADR-0006 withdrawn and ADR-0007
revised (→ v2); the knowledge tree, glossary and public site; **get decided**
(ADR-0009 — content from `-sources.jar`, sidecar abandoned) with RAD-0002 split
into transport (v2) and content (RAD-0011); and the **get → parse → store →
query** build-up. The codex entry was then defined (RAD-0013 — two-faced,
per-capability, retrieved hybrid), which pulled the parse and store records to v2:
RAD-0009 is now the documentation **parse** layer (tree-sitter/Dokka per
ecosystem, the summarise step as the new work), RAD-0010's retrieval moved from
lexical-only to **hybrid over Lucene**, and RAD-0014 settled **build-vs-reuse**
across the pipeline (reuse every layer but summarise and curate; assemble from
Lucene rather than adopt a code-search pipeline; Glean as template, Mahout
rejected). Choosing the test parser then sharpened RAD-0009 to v3: Dokka named
the intended production Kotlin parser, tree-sitter the broad/simple layer, the two
baked off on one Kotlin library as the first spike, an adapter seam between parser
and entry, and the harvester required to *understand* KMP without being KMP. RAD-0015 then
named the **read** stage between get and parse — read the archive in place rather
than extract it (the IntelliJ lazy-VFS precedent), a **language-agnostic** read
layer that reuses a VFS (NIO `FileSystemProvider` over zip/tar/loose containers,
the Kotlin core VFS on the Dokka path) across every ecosystem, remote backends
(git/HTTP) behind the same interface, and first-party loose source as a first-class
input and the cleanest v0 value case. Naming read folded back into the
build-vs-reuse map (RAD-0014 → v2, Commons VFS / Kotlin core VFS as another reuse).
Working these stages does not bump the checkpoint. Building the first spike
(`experiments/test0`, the parse bake-off) then prompted a structure fix: ADR-0005
→ v2 renamed `poc/` to `experiments/` and drew the line that `implementations/` is
working code only, not test harnesses; each experiment is self-contained (data +
runnable harness). RAD-0007 → v2 then made the overlap/selection record
self-contained: it now owns the overlap-is-domain position and the relationship
taxonomy directly (cross-linking ADR-0004), and adds the
**preference-authorship trust model** — self-referential and neutral author tags
trusted, `@category`/`@triggers` as sorting signals (the free one down-weighted),
an interested `@preferOver` a weak nudge that never excludes, and consumer
preference weighted highest. A grep then showed **ADR-0004** is cited across seven
records as the foundational two-layer design, so it was **re-minted** (v2) rather
than stripped — restored from the cull, its two-layer decision graduated on the
research (RAD-0003/0010/0013) and the field's independent arrivals (RAD-0008), its
content-generation specifics flagged as superseded by the documentation-transport
pivot. The same sweep restored **ADR-0003** (the sidecar transport) marked
**superseded by ADR-0009** — kept, not deleted, because RAD-0005/0008 and the v1
postmortem lean on why the sidecar was tried and dropped (the ADR log is
append-only); ADR-0006 has no inbound references and stays withdrawn. Then the
first spike produced data: `experiments/test0` ran the raw/enriched bake-off over
graded synthetic Kotlin and RAD-0009 → v4 captured it — **doc level dominates,
parser enrichment marginal** — with binary/structure parsing split to its own
future suite (test2, RAD-0012). A follow-on — do `@sample` bodies even travel in
the `-sources.jar`? — took RAD-0011 → v2: the reference travels, the sample source
set usually does not, and a *resolved* sample (Dokka) beats raw text because it
sorts the body into the library's own calls, calls to *other* codex libraries (an
authoritative composition edge — RAD-0007), and scaffolding, nudging the codex
toward a graph. Attention then turned to the thesis test itself: RAD-0016 specified
the **content-value A/B** (does the codex change what an agent does) — synthetic
subject to pin training exposure to zero, content-value before retrieval, a
first-party-vs-third-party scenario split, and a `model × scenario × level × naming`
matrix — and ADR-0010 settled that it runs through **developer tools** (Claude Code
subagents, Antigravity `agy`), not model APIs, so the result is agent-agnostic and
needs no API accounts. The first real run then landed: across four synthetic
capabilities × two models, **without the codex 0/8 used the capability, with it 8/8**
— content value confirmed, cross-model. A doc-level gradient added the sharper
finding — a *bare* entry (signature only) already flips 7/8, so the **syntactic face
drives use** while the semantic prose matters at the margin and model-dependently
(RAD-0013 → v2). The external test then ran and **nulled**: across three real
libraries (kotlinx-datetime, Arrow, kaml) both models already produced the correct
current API without the codex, so the content-value lift does **not** transfer to
public dependencies a current model knows — its value is bounded to novel/private
code, post-cutoff drift, and weaker models (doc coverage ≠ training exposure; kaml
at 4% docs was known cold from code). An older-model probe (Gemini 3.1) then
**recovered the drift lift**: without the codex it wrote the stale `kotlinx.datetime`
API, with it the correct `kotlin.time` one — so the codex's value is the **gap
between the model's knowledge and the classpath** (≈zero for a current model, real
for a stale one, a post-cutoff change, or genuinely unknown/private code). A
**local-model ladder** (RAD-0016 → v2) then carried the drift result from the time
axis to the **capability axis**: the same A/B against nine local models spanning
**270M–70B** (LM Studio and mlx-lm) flipped the synthetic capabilities 0→3/4 at 270M and
0→4/4 from 1B up, and on the real libraries where current frontier models showed *zero*
lift the local models were **stale** — the flagship rung, a code-tuned Qwen3-Coder-30B a
developer would actually run, wrote the removed `kotlinx.datetime`, Arrow's removed
`Validated`, and a fumbled kaml, and the entry corrected **all three** — refuting the
guess that code-tuning makes a local model already-current, and replicated cross-family
by Mistral's Devstral-24B and NVIDIA's Nemotron-3-Nano-30B (both stale on datetime and
Arrow, corrected on both — three vendors, coding and general models alike). Scaling
did not close the gap anywhere in the local range: a dense Qwen3-32B and even a dense
**Llama-3.3-70B** — the largest the machine runs — were *still stale* on the real
libraries unaided (the 70B on all three, corrected 3/3), so the frontier null does not
reproduce at local scale and the codex's value tracks the model's **training gap to the
classpath, not its size**. Attention then turned to the next question — **retrieval**
(RAD-0017, new): the whole catalogue (four real capabilities + four opaque-named
distractors with near-identical signatures) put in front of the agent, Rbare (signatures
only) vs Rrich (full entries). Where a distractor's signature differed by a constructor
parameter the bare catalogue disambiguated alone, but where signatures **collided**
(`RowReader` vs `Shredder`, identical) Rbare was unreliable — across six subjects (two
frontier, four local) it split correct/reinvent/wrong/both with every failure a local
model, one *confidently picking the wrong capability* — and the **semantic prose made all
correct**. This completes RAD-0013's two-faced entry as
measured (syntactic face = use and same-signature disambiguation; semantic face =
disambiguation where signatures collide, and prevention of active mis-selection).
**Selection** then landed too (RAD-0018, new; RAD-0007 → v3 measured): among genuinely
overlapping real libraries, condition A (task alone) picked the project's sanctioned
library **0/18** model×domain cells — no model, frontier or local, knows the local
preference. Listing it as the *single* dependency redirected almost universally (dep1
17/18 — the **dependency tree is itself a selection signal**), but an *ambiguous*
classpath (both libs declared) failed (dep2 3/18) and only the **authored preference
resolved it** (dep2pref 17/18) — including on both frontier models. This is the crux:
unlike drift (closes with model freshness) and disambiguation (closes with model
capability), **selection closes with neither** — which library *this* project sanctions is
local knowledge no model can have, the one gap model progress cannot close. Then **retrieval at scale**
landed as **Layer 1 — index recall** (RAD-0019, new; RAD-0010 → v3 measured): a pure
recall eval (no agent) over **220 synthetic entries in adversarial clusters** with **26
paraphrastic queries** (the caller's need in words that avoid the entry's vocabulary),
embedded in-process via mlx-embeddings (fully open — LM Studio dropped for licence
clarity). The **vector arm was decisively best** (recall@1 77% vs lexical 38%, which
plateaus at 58%) — the semantic face works as a *retrieval key*, RAD-0011's thesis at full
strength — and the surprise: **equal-RRF hybrid *hurt*** (13/26 < vector's 20/26), fusing a
strong vector arm with a near-useless lexical one drags hits down, so RAD-0010's fusion
policy is revised to **vector-primary / query-adaptive**, not equal RRF. Encoder is
load-bearing: **BGE-M3 (MIT)** chosen on recall and licence. Remaining: **Layer 2** (the
agent authoring a query against the index via MCP — RAD-0003), then porting the rig to the
JVM/Lucene substrate and swapping in a harvested corpus. Attention then moved to the
**parse** stage the behaviour work had been standing in for: **test1 Phase A** (RAD-0009 →
v5 measured; `experiments/test1`) ran the real **tree-sitter** arm on real Maven
`-sources.jar`s (fetched on demand, read in place — RAD-0015) across a six-library KDoc
spread (kaml 3% → kotlinx-cli 72%). The raw arm extracts clean entries and its coverage
cross-checks `kdoc-coverage.py` within points; and — reversing test0's *synthetic*
"enrichment marginal" — the **enrichment ceiling on real source is large and realizable
but conditional on library shape**: undocumented-`override` inherited-doc ceilings of 144
(kaml, supertype 37% documented), 372 (coroutines), 354 (datetime) and 259 `@sample`s
(datetime), yet **zero** for a small well-documented library (kotlin-retry) — so the
answer is **bundle both parsers** (tree-sitter universal + Dokka for high-ceiling Kotlin
libraries). Then the **Dokka (enriched) arm** (RAD-0009 → v6; standalone Gradle+Dokka on
kaml) reframed it: single-library Dokka realizes ≈ own-docs only — the overriding
`decodeFromString` gets **no** inherited doc because its supertype (kotlinx.serialization)
is a classpath jar, not a source root — but adding that **source** to the run makes it
**inherit** the full doc, `@throws`, and a resolved link. So the large thin-library
enrichment is realized **only across the harvested graph** (a cross-library link —
RAD-0011's composition graph), *not* by any per-library parse: **the enrichment lever is
the codex graph, not the parser**; Dokka's real per-library value is *resolution* (types,
`@sample`, internal inheritance). This was then **demonstrated** (`resolve_in_index.py`):
harvesting kaml and kotlinx.serialization *independently* with tree-sitter (each entry
carrying its `override → supertype` edge) and a **transitive graph join** in the index
realized **16** cross-library inherited docs from a two-library index — no Dokka, no
multi-source parse — the exact `Yaml.decodeFromString ← StringFormat.decodeFromString` case
included, scaling with harvest coverage. So the pipeline splits cleanly: **parse = local
extraction (read in place via VFS — RAD-0015); enrich = a graph join in the index**
(RAD-0010 / RAD-0011). **Phase B — the other languages** then validated the
language-agnostic claim outside Kotlin (`extract_polyglot.py`): the same tree-sitter rig,
against real npm / PyPI / crates.io source (fetched on demand, read in place), extracts
clean `(symbol, signature, doc-comment)` entries across three *different* doc conventions —
Python **docstrings**, TypeScript **JSDoc**, Rust **`///`** (click 56%, commander 73%,
anyhow 23% pub own-docs) — one `walk` with per-language declaration-kinds and a doc rule,
no per-language resolver (resolution being index-side), and **resolve-in-index generalises**
(Rust trait-impl inherited docs = the Kotlin `override` link). **Swift** completed the set
(`apple/swift-argument-parser` from GitHub, 515 decls, `///` docs) — **five languages
across four doc conventions, one rig**, the language-agnostic parse claim validated end to
end. Finally **test2 — structure from bytecode** (RAD-0012 → v2 measured; `experiments/test2`)
closed the test sequence: kaml harvested from its compiled `.jar` via `javap` recovers the
public surface (63 classes + 444 methods) but **degraded** — no parameter names, no docs,
JVM-level noise — so a source-less, doc-less library still participates in the codex as a
**fallback** (syntactic-face entries, usable per RAD-0016), with the payoff that bytecode
supertype edges are **fully qualified**, so resolve-in-index needs no import resolution
(demonstrated: `Yaml.decodeFromString` inheriting `StringFormat`'s doc from the graph,
bytecode-only). **The composition graph works across every harvest path — source or
bytecode.** The **experiment sequence (test0/1/2) is complete**; only tool-building remains
— the **retrieval Layer 2** agent loop (MCP), then implementing the harvester/index itself.

**0.0.1.** The sidecar generation — the `-skills.zip` proposal and the first cost
model, tracked informally on the older records as "attempt v3" — restarted from
once the measurement moved the position.
