# test2 — structure from bytecode

When a library ships a compiled `.jar` but **no `-sources.jar`**, can the bytecode's public
API structure recover usable capability entries? (RAD-0012's hypothesis 1.) Kept separate
from test0/test1 so the doc-path data stays clean.

`extract_bytecode.py` harvests kaml both ways and compares: the **source path** (tree-sitter
on `-sources.jar`, reused from `../test1`) against the **structure path** (`javap -public` on
the compiled `.jar`).

```bash
uv run --with tree-sitter --with tree-sitter-language-pack python extract_bytecode.py
```

**Result.** The structure path recovers the full public surface (63 classes + 444 methods vs
the source path's 233 clean declarations) but **degraded**: no parameter names (types only),
no docs, and noisier (JVM-level bridge/synthetic/accessor methods). So a source-less,
doc-less library still participates in the codex as a **fallback** — syntactic-face entries,
usable per RAD-0016's bare-signature result. The payoff: bytecode supertype edges are
**fully qualified**, so resolve-in-index needs no import resolution — proven end to end,
`Yaml.decodeFromString` inheriting `StringFormat.decodeFromString`'s doc from the graph,
derived purely from bytecode. The composition graph works across **every** harvest path.
See RAD-0012 (v2).

Uses `javap` (any JDK). Next step to lift it toward source parity: read Kotlin `@Metadata`
to map JVM methods back to language declarations and recover parameter names.
