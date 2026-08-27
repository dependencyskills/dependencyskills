# Deduplication Under an Incremental, Scoped Store

RAD-0041 · 2026-08-27 · v1

Keywords: where should deduplication happen; dedupe at harvest or at index build; duplicate doc comments across KMP targets; 63% duplicate harvest; incremental harvesting into a shared store; is harvest a pure function; reproducible store; scoped query loses deduplicated entries; tree-sitter native library in a Gradle plugin; Dokka per-library build; why not dedupe by publisher.

Measured against: the figures cited are from `experiments/test5` (2026-08-22), `experiments/test12`, and `experiments/corpus/build.py` as of 2026-08-26. Nothing new was measured for this record.

**Opened while triaging [#2](https://github.com/dependencyskills/dependencyskills/issues/2).** The harvest rules were settled in a batch context — `experiments/corpus/build.py` walks every cached artifact in one pass, holding a global `seen_docs` set. [ADR-0012](../decisions/0012-a-shared-machine-level-index-store.md) then made the store **incremental** (one jar at a time, whenever a build resolves something new) and **scoped** (a query only sees the coordinates the asking project resolved). Both changes were made for other reasons, and neither was checked against the dedup rule.

> **Does deduplication still belong at harvest, and does the answer change what parses the source?**

## Trail

### What the rule is, and why it exists

`test5` measured a real harvest as **63% duplicate** — 9,459 of 14,899 entries repeated a fully-qualified symbol already present — and `test12` reproduced 61% independently. The cause is structural rather than sloppy publishing: Kotlin Multiplatform ships one artifact per target, so `runtime-desktop`, `runtime-js` and `runtime-wasm-js` carry the same `commonMain` doc comment.

The reason it matters is **retrieval, not storage**. `test5`'s size sweep held targets constant and varied only distractor count: raw-text recall fell **29% → 6% → 0%** across 220, 1,000 and 3,000 entries. Duplicates are distractors. Storage is cheap; a crowded index is not.

`build.py` deduplicates on `(publisher, doc)` for maven and `(package, doc)` for npm — different units because the duplication has different shapes, which the file's comments explain.

### Incremental harvesting makes the current rule order-dependent

A global `seen_docs` set works in a single pass. Harvesting one jar at a time does not have one.

Whichever artifact is harvested **first** keeps the doc; later ones drop it. In a batch run the order is fixed by a sorted directory walk. In an incremental store the order is whatever a developer's builds happened to resolve, which differs per machine and changes if the store is deleted and rebuilt.

That directly contradicts an acceptance criterion already written into #1: *"deleting the store and rebuilding produces the same content."* Under incremental per-publisher dedup, it does not.

### Worse: scoped queries make harvest-time dedup lose entries

This is the finding that reframes the question, and it was not visible before the store became scoped.

Suppose `runtime-desktop` and `runtime-js` share a doc comment. Harvest-time dedup keeps one — say `runtime-desktop` — and drops the other. Now a project that depends **only** on `runtime-js` runs a query. The scope filter admits only coordinates that project resolved. `runtime-desktop` is not among them, and `runtime-js`'s copy was never stored.

**The entry is invisible to a project that legitimately depends on it.** Deduplication at harvest is sound only when every consumer sees the whole corpus, which was true of the experiments and is deliberately false of the product.

The failure is silent, which makes it worse: the query returns fewer results rather than an error, and this project has repeatedly found that a skip which looks like an absence is the hardest kind of defect to notice.

### So the unit was never really the publisher

Stepping back: the duplication is between **targets of one library**, not between unrelated artifacts that happen to share prose. `runtime-desktop` and `runtime-js` are the same library. Using the publisher as the key was a serviceable approximation in a batch harvest, where over-merging cost nothing because everything was in one corpus.

Under a scoped store, the honest unit is narrower — the **KMP module**, of which the per-target artifacts are publications. Whether that is derivable from a coordinate alone is unverified; artifact-name suffixes (`-jvm`, `-iosarm64`, `-js`) are a heuristic, and Gradle module metadata carries the real answer but is only cached for a minority of artifacts, as `experiments/corpus/` found while classifying `library_type`.

### Where the parser question actually sits

Reopening dedup does not reopen the parser choice, and the record on that is stronger than a triage summary made it sound.

[RAD-0009](0009-reusing-indexers-and-what-to-index.md) v6 settled it with a measurement: standalone Dokka on kaml realised **≈18** inherited docs against a ceiling of **144**, because the supertypes were on the classpath as compiled jars rather than source roots. Adding the supertype's *source* flipped it. Tree-sitter alone then reproduced the same result — `resolve_in_index.py` harvested two libraries independently and realised **16** cross-library inheritances via a transitive graph join, including the exact case Dokka needed both sources loaded to produce.

The conclusion is about **where enrichment happens**: parse is local extraction; enrich is a graph join in the index. Dokka's cross-library value is fully replaced, and what remains is local — resolved type spellings, overload-exact targets, `@sample` bodies.

What *is* new, and what RAD-0009 had no reason to weigh, is that the parser now has to ship **inside a Gradle plugin**. Tree-sitter is a native library, so the plugin must carry or fetch binaries for macOS, Linux and Windows, on both architectures. Dokka avoids that and needs a Gradle build per library instead, which is unworkable across a few hundred dependencies. Neither cost was in scope when the parser was chosen.

## Findings

**Reasoned, not measured.** Nothing here was re-run; these follow from existing measurements meeting two decisions made after them.

- **Deduplicating at harvest is order-dependent under an incremental store**, and breaks the reproducibility criterion #1 already carries.
- **Deduplicating at harvest loses entries under a scoped query.** An entry dropped because a sibling target carried the same prose is invisible to a project that depends only on the sibling that lost. This is a correctness defect, not an efficiency one, and it fails silently.
- **The publisher was always an approximation of the unit.** The real unit is the KMP module whose targets are separate publications. Whether that is recoverable from a coordinate is unverified.
- **The 63% figure is a retrieval concern, not a storage one.** `test5` measured recall collapsing with corpus size; nothing measured a storage cost worth avoiding.
- **The parser choice is not reopened by this.** RAD-0009 v6's measurement stands. The new consideration is distribution — a native library inside a Gradle plugin — which is a packaging question rather than a parsing one.

## Recommendation

**Not a commitment.**

1. **Store every entry; deduplicate when building the derived index.** This keeps harvest a pure function of one jar — testable, re-runnable, order-independent — and puts the merge where the corpus is known and the scope is applied. It also matches [RAD-0010](0010-how-the-codex-is-stored-and-served.md)'s existing split: text is the source of truth, the index is derived and disposable. The cost is a store roughly 2.7× larger, which for SQLite is not obviously a cost at all.

2. **Deduplicate within the query's scope, not globally.** Whatever the unit, the merge has to happen after the coordinate filter, or the defect above returns in a different place.

3. **Establish the real unit before relying on it.** Whether the per-target artifacts of one KMP module are identifiable from a coordinate is unverified and cheap to check — `experiments/corpus/` already classifies `library_type` and records `source_sets`, so the data to answer it is on disk.

4. **Tree-sitter, unchanged.** Nothing here disturbs RAD-0009 v6, and this record does not reopen the parser choice.

   Its packaging is a separate, smaller question: tree-sitter is a native library, so the plugin ships or fetches binaries per platform. That decides *how it is delivered*, not *whether it is the parser*, and the ground is well trodden — the Kotlin Multiplatform JS plugin downloads an entire Node.js runtime into `~/.gradle/nodejs`. Worth deciding before #2 ships, not before #2 starts.

**What would change this.** A measurement showing storage size actually costs something at realistic scale. Or finding that the KMP module is not recoverable from a coordinate, which would make a narrower dedup unit unavailable and force the choice between over-merging and not merging at all.

**What would not.** The packaging of the parser. If shipping a native library in a plugin turns out to be awkward, the answer is a different delivery mechanism for tree-sitter, not a different parser — RAD-0009 v6's measurement does not depend on how the binary arrives.
