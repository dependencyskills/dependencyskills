# test1 — the real-parser bake-off (tree-sitter vs Dokka), and the other languages

Where **test0** ran the doc-parse comparison on a *synthetic* Kotlin fixture with PSI
stand-ins, **test1** runs it on **real Maven `-sources.jar`s**. Jars are fetched on demand
from Maven Central and **read in place** (RAD-0015) — never stored in this repo; the
coordinate is the reference.

## Phase A — Kotlin, real source

**Arm 1 — tree-sitter (raw).** `extract_treesitter.py` pulls a `-sources.jar`, parses every
`.kt` with `tree-sitter-kotlin`, and emits `(symbol, signature, KDoc)` entries plus two
*enrichment-ceiling* signals per entry: `is_override` (an inherited-doc candidate) and
`has_sample` (a resolved-`@sample` candidate).

```bash
uv run --with tree-sitter --with tree-sitter-language-pack \
  python extract_treesitter.py <group:artifact[:version]> [more...] [--json <dir>]
```

Result across a KDoc-coverage spread (kaml 3% → kotlinx-cli 72%): the raw arm extracts
cleanly at scale and its coverage cross-checks `../cost-model/scripts/kdoc-coverage.py`
within a few points. The **enrichment ceiling** is large and realizable but *conditional on
library shape* — undocumented-`override` ceilings of 144 (kaml), 372 (coroutines), 354
(datetime) and 259 `@sample`s (datetime), vs **0** for kotlin-retry. This reverses test0's
synthetic "enrichment marginal": on real source Dokka's inherited-doc / `@sample` payoff is
substantial exactly where own-coverage is worst. See RAD-0009 (v5).

**Arm 2 — Dokka (enriched).** `dokka-arm/` is a standalone Gradle project (Gradle + Dokka
1.9.20, GFM output) that documents an extracted `-sources.jar`. Run on **JDK 21** (Kotlin
analysis breaks on JDK 26):

```bash
cd dokka-arm
# populate src/main/kotlin by extracting a -sources.jar (strip the sourceset prefix),
# and declare that library's deps in build.gradle.kts, then:
JAVA_HOME=$(/usr/libexec/java_home -v 21) ./gradlew dokkaGfm   # output: build/dokka/gfm/
```

> Extracting to `src/` is an experiment shortcut for Dokka's Gradle-plugin `File`-based
> API — **not** a requirement. Dokka runs on the Kotlin/IntelliJ analysis, which reads a
> `-sources.jar` **in place** via `CoreJarFileSystem`; production routes the Dokka path
> through JetBrains VFS (RAD-0015), no extraction — as the tree-sitter arm already does.

**Finding — the enrichment lever is the graph, not the parser.** On kaml, single-library
Dokka realizes ≈ own-docs only: the overriding `Yaml.decodeFromString` gets **no** inherited
doc, because its supertype (`StringFormat`, kotlinx.serialization) is on the *classpath* as a
compiled jar, not a source root. Adding kotlinx.serialization's **source** to the same run
flips it — `decodeFromString` then **inherits** the full doc + `@throws` + a resolved type
link. So the big thin-library enrichment (the 144 ceiling) is realized **only when the
supertype source is in scope**, i.e. as a **cross-library link across the harvested graph**
(RAD-0011's composition graph), not by any per-library parse. Dokka's real per-library value
is *resolution* (types, `@sample`, internal inheritance). See RAD-0009 (v6).

## Resolve in the index (no Dokka)

`resolve_in_index.py` demonstrates the consequence: realize cross-library inherited docs as
a **graph join in the index**, from per-library tree-sitter extraction alone.

```bash
uv run --with tree-sitter --with tree-sitter-language-pack python resolve_in_index.py
```

It harvests kaml and kotlinx.serialization *independently* (each entry carrying its
`override → supertype` edge, supertype names resolved to FQNs via the file's imports), then
walks an undocumented override's supertype chain **transitively** through the harvested
entries until it finds a documented member. From a two-library index it realizes **16**
inheritances — including the exact `Yaml.decodeFromString ← StringFormat.decodeFromString`
that multi-source Dokka produced — and the count scales with harvest coverage. So the
pipeline is **parse = local extraction (read in place, any parser); enrich = a graph join
in the index** (RAD-0010 / RAD-0011). Dokka's cross-library role is replaced entirely.

## Phase B — the other languages

`extract_polyglot.py` runs the same tree-sitter rig against real published source in three
ecosystems, fetched on demand (npm / PyPI / crates.io) and read in place:

```bash
uv run --with tree-sitter --with tree-sitter-language-pack python extract_polyglot.py
```

One `walk` with per-language declaration-kinds and a **doc-comment rule** handles three
*different* conventions — Python **docstrings** (a string as the first body statement, not a
comment), TypeScript **JSDoc** (`/** */`), Rust **`///`** line docs. Results: click 332
decls / 56%, commander 183 / 73%, anyhow 73 pub / 23% own-docs. The **language-agnostic
parse behind one contract** holds outside Kotlin, and because resolution is index-side each
language needs only *local* extraction — no per-language resolver. `resolve_in_index` also
generalises: Rust `impl Trait for T` methods inherit their docs from the trait, the same
cross-entry link as Kotlin `override`s. **Swift** completes the set
(`apple/swift-argument-parser`, fetched from GitHub — SwiftPM has no tarball registry — 515
decls, 33%). Five languages, four doc conventions, one rig. See RAD-0009 (v6).
