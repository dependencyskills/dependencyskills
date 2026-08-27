# Structure from Bytecode

RAD-0012 · 2026-08-17 · v2
Keywords: can the public API be recovered without source; parsing bytecode with ASM or javap; what a class file gives and what it loses; no parameter names, no doc comments; fully qualified supertypes; a fallback for a library with no sources jar; metalava and kotlin-metadata; when structure parsing earns its place.

**Licenses verified 2026-08-17** (carried from RAD-0009): **ASM** BSD-3-Clause,
**kotlin-metadata-jvm** and **metalava** Apache-2.0, checked against source. The
tools' capabilities are from general knowledge; confirm on the artifacts before
this is load-bearing. No new measurement.

**Split note.** Split out of
[RAD-0009](Research-RAD-0009-Reusing-Indexers-And-What-To-Index) on 2026-08-17. RAD-0009 is
now the *documentation* parse path (KDoc from `-sources.jar`). This record holds
**bytecode / structure** parsing as a *deferred option* — not the parse path for
the codex today, but the obvious lever if the doc path proves insufficient, and
the place to extend capabilities later.

## Question

A `classes.jar` carries the full public API surface — every class, signature,
annotation, hierarchy — and mature, permissively-licensed tools extract it
cheaply and at IDE scale (ASM, kotlin-metadata-jvm, metalava). Should the
harvester parse it?

## Trail

### What bytecode gives, and what it does not

- **ASM** reads a `.class` and yields signatures, hierarchy and annotations.
- **kotlin-metadata-jvm** reads the `@Metadata` on a class for the Kotlin-level
  API (nullability, properties, coroutines) that raw bytecode hides.
- **metalava** and **binary-compatibility-validator** emit signature dumps — an
  API-surface index already.

IDEs prove this runs over massive graphs, parsed once per library-version and
cached (RAD-0009). Feasibility is not in question.

But structure is *what exists*, not *what it is for*. The codex maps a **need**
to a library, and a need is a capability in a caller's words — which lives in the
**KDoc**, not the signature. Parsing the `-sources.jar` for KDoc yields the
declaration *alongside* its documentation for free (RAD-0009), so a separate
bytecode pass hands back structure the codex does not consume. That is why this
is deferred, not adopted.

### Where it would earn its place

Two hypotheses, both testable, neither settled:

1. **Reaching the undocumented tail.** A median library documents 33% of its
   public declarations (RAD-0011); a KDoc-only parse cannot see a capability in
   an *undocumented* function. But a public symbol *named* `retryWithBackoff` is
   a signal even with no prose. Bytecode gives every public symbol name — the
   question is whether name-only signals recover meaningful undocumented
   capabilities, or just add noise.
2. **Extending capabilities later.** Precise resolved signatures, type-aware
   disambiguation ("which overload," "does it return a `Flow`"), structural
   queries ("what implements this interface across my graph") — none is the v0
   codex's job, but each is a capability structure could add once the doc path
   works.

## Findings

**Reasoned.** Structural extraction is a solved, permissively-licensed,
IDE-demonstrated problem. It is simply not the *content* the codex is built from,
so it is out of the critical path — a capability to add, not a stage to build.

**Measured — test2 (2026-08-21; `experiments/test2/extract_bytecode.py`).** kaml harvested
both ways confirms hypothesis 1 with nuance. The **structure path** (`javap -public` on the
compiled `.jar`) recovers the full public surface — 63 classes + 444 methods vs the source
path's 233 clean language declarations — but **degraded**: no parameter names (types only —
losing the disambiguation signal param names carry, per RAD-0013), no docs, and *noisier*
(JVM-level bridge/synthetic/accessor methods inflate the count above the language-level
declarations). So a **source-less, doc-less library still participates in the codex** — its
capabilities surface as syntactic-face entries (usable, per RAD-0016's bare-signature
result) — as a **fallback**, not a replacement for source. The unexpected payoff: bytecode
supertype edges are **fully qualified**, so **resolve-in-index needs no import resolution** —
demonstrated end to end, `Yaml.decodeFromString` inheriting `StringFormat.decodeFromString`'s
doc from the graph, derived purely from bytecode. The composition graph (RAD-0011 /
resolve-in-index, RAD-0009) works across *every* harvest path — source or bytecode.

**Still open.** Bridge/synthetic/accessor noise wants filtering (Kotlin `@Metadata` maps
JVM methods back to language declarations and recovers parameter names — the way to lift the
bytecode path from "types only" toward parity with source); and the structure path's value
is bounded to libraries that ship *no* `-sources.jar`, so its priority tracks how common
that is.

## Recommendation

**Defer. Do not parse bytecode for v0.** Build the codex from KDoc (RAD-0009).
Hold structural extraction as an option, brought back only by evidence — the
undocumented-tail hypothesis, or a later capability that needs type or structure
the docs do not carry. The tooling and licenses are already cleared, so adopting
it later is an integration job, not new research.

## Connections

- [RAD-0009](Research-RAD-0009-Reusing-Indexers-And-What-To-Index) — the documentation
  parse path this was split from and complements.
- [RAD-0011](Research-RAD-0011-Existing-Documentation-Systems-As-Skill-Content) — the 33%
  coverage gap the undocumented-tail hypothesis targets.
- [ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar) — get; the `classes.jar`
  travels alongside the `-sources.jar` this defers in favour of.
