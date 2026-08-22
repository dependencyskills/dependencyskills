# test0 — the content-value spike

A **synthetic** Kotlin source set with documentation graded across controlled
levels. It is not a real library, on purpose: a real library confounds doc level,
name obviousness, inheritance and `@sample` all at once, so it cannot isolate the
one thing this spike measures. Here we **hold the capability constant and vary one
axis at a time.**

## The question

Does a codex built from real, often-thin documentation change agent behaviour —
does the agent *find and reuse* an existing capability instead of reinventing it?
And how much does documentation richness matter to that? (RAD-0011's open
content-value question; RAD-0015's argument that first-party loose source is the
cleanest v0 test, with no archive and no training-exposure confound.)

## The level ladder

The same capability appears at each level; only the docs change.

| Level | Docs | Tier (RAD-0011) |
|---|---|---|
| **L0** | none — bare signature | discovered (the undocumented tail) |
| **L1** | one-line KDoc | discovered (the realistic ~33% median) |
| **L2** | rich KDoc — purpose, when-to-use, a trap, `@sample`, `@see` | discovered (ceiling of standard tags) |
| **L3** | L2 **plus custom tags** — author guidance KDoc has no tag for | **designed** |

## Controlled axes (besides doc level)

- **Name obviousness.** Each capability in a **transparent** form
  (`retryWithBackoff` — the symbol alone signals intent) and an **opaque** form
  (`Policy.apply` — the name gives nothing away, so the doc must carry the
  meaning). Crossing doc-level × name isolates whether KDoc adds value *beyond the
  name* — the RAD-0012 symbol-name question at L0.
- **Inheritance** (`inherit/`). A documented interface with an *undocumented*
  override, to test whether Dokka's inherited-doc resolution recovers meaning
  tree-sitter cannot see.
- **`@sample`.** Present on L2/L3, to test Dokka's sample expansion.

## L3 custom-tag vocabulary, and its trust weight

The tags map straight onto the RAD-0013 entry faces, so L3 tests *authoring the
entry directly*. They are weighted by **who is speaking** — an author is an
interested party for some claims but not others:

| Signal | Author | Trust | Weight in ranking |
|---|---|---|---|
| graph-derived (importable, `api`/`implementation`, module reach) | none — computed | high | **default** |
| `@capability`, `@notFor` | library, **self-referential** | fine — about itself | normal |
| `@category` (from a **fixed** set) | library or curator | fine — a controlled bucket | **grouping / filter** |
| `@triggers` (free words) | library, **attention terms** | **gameable** (keyword-stuffing) | retrieval + sorting; only atop a semantic match |
| `@similar X` | library, **neutral relationship** | fine — relates, does not rank | feeds **clustering**, not ranking |
| `@preferOver X` | library, **interested** | low — could disparage rivals | **heavily down-weighted; never excludes** |
| "prefer X over Y here" | **consumer** (local preference) | highest — disinterested | strong |

`@preferOver` is included to exercise the mechanism, not because a library's
ranking of its rivals is trusted — cross-library preference belongs to the
consumer (RAD-0007). `@similar` is the neutral way to get cross-library
relationship data from an author without any library excluding another.
`@category` slots the capability into a **fixed, governed taxonomy** (the crates.io
model — a curated category list — not npm's free-form keywords), giving stable,
nameable groups for overlap detection; `@similar` adds a finer symbol-to-symbol
link on top. `@triggers` is a free list of caller-words that should surface the
capability — the author seeding retrieval for the **selection** failure directly,
and a decent sorting signal alongside `@category`. It is the most useful of these
and the most abusable: free words are exactly the gameable, noisy signal npm
keywords are. Two things keep it honest — it is designed-tier and
**down-weighted**, and in **hybrid** retrieval a trigger only helps when it
co-occurs with a real **semantic** match, so a stuffed irrelevant trigger surfaces
nothing (the embedding checks the keyword). Whether author triggers beat embedding
the `capability` alone is itself something test0 can measure.

## Standard tags are harvested for free

Some entry content comes from **standard** KDoc tags/annotations — no custom tag,
so no risk of Dokka dropping them: `@since` (the version a capability was
introduced — availability against the resolved version, and drift), `@sample`,
`@see`, `@throws`, and Kotlin's `@Deprecated(..., replaceWith = ReplaceWith(...))`
annotation — the machine-readable **drift marker with its replacement**, exactly
the "use X instead" case (RAD-0007 / RAD-0011). L2 carries `@since`; a
`@Deprecated`/`replaceWith` case is worth adding as its own drift fixture.

## What it feeds: the parser bake-off

Each level is parsed **twice** — tree-sitter (raw KDoc) and Dokka (enriched) —
into RAD-0013 entries (RAD-0009). The fixture settles, in one run:

- **which parser** to carry forward for Kotlin, and how much Dokka's enrichment is
  worth over raw text (visible at the L2/L3 and inheritance cases);
- **what Dokka does with an unknown tag** — RAD-0011 flagged this as *unverified
  and load-bearing*; L3 is that experiment. tree-sitter preserves an unknown tag
  as raw comment text for free, so the whole question is Dokka's behaviour
  (render / drop / warn / fail).

## The measurement

A set of **needs in a caller's words** (e.g. *"retry a failed call with
increasing delays"*). For each, score the agent **with** the codex vs **without**
it on: does it *find* the right capability, *use* it correctly, and *avoid
reinventing* it. Slice by doc level and by name obviousness. The thesis predicts:
codex lifts find/use over baseline; the lift grows with doc level but is
non-trivial at L1; L0 + opaque name is the worst case, where only structure
(RAD-0012) could help.

## Run

```
cd harness && ./gradlew test
```

Runs on a JDK 21 toolchain (standalone Kotlin cannot yet parse JDK 26's version).
The tests are the experiment: they assert what each parser arm extracts per level.

## Layout

```
test0/
  README.md    this design doc
  fixtures/    the graded source (data)
    test0/l0 l1 l2 l3   the same capability, one package per doc level
    test0/inherit       documented interface + undocumented override
    test0/samples       @sample targets for L2/L3
  harness/     the runnable module — the parser arms and the tests-as-experiment
```

Package-per-level (`test0.l0` … `test0.l3`) so identical symbols coexist in one
parse/Dokka run, with the level carried in the coordinate. The harness reads the
fixtures via its `test0.dir` property.

## Status

**Green:** the fixture is seeded (retry-with-backoff at L0–L3, transparent
(`retryWithBackoff`) and opaque (`Policy`), plus the inheritance and sample cases),
and `harness/` runs **both bake-off arms** — a **raw** PSI arm and an **enriched**
arm that inherits supertype docs and expands `@sample`. The deltas are live
assertions: L0 yields no capability; L2 prose becomes the capability with `@since`;
L3 surfaces the custom tags; and the enriched arm recovers the inherited doc the
raw arm is blind to. **Next:** real-Dokka fidelity (RAD-0009), then more
capabilities across the ladder.

## Connections

- RAD-0011 — the content-value question and the designed/discovered tiers; the
  custom-tag experiment L3 settles.
- RAD-0009 — the tree-sitter vs Dokka bake-off this fixture drives.
- RAD-0013 — the two-faced entry the tags map onto.
- RAD-0007 — the selection/preference signals and the trust weighting above.
- RAD-0015 — first-party loose source as the cleanest v0; the read layer.
- RAD-0012 — the symbol-name signal the L0 + opaque case probes.
