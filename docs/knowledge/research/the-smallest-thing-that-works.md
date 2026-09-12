# The Smallest Thing That Works

RAD-0070 · 2026-09-06

Keywords: minimal codex; can a skill carry the tooling; python instead of kotlin; drop the summariser; do we still need embeddings; sqlite fts5 without lucene; pylucene in a skill; brute-force vector search at one developer's scale; what the security model was buying; personal tool versus product; zero-install dependency index; naive doc comment binding.

Measured against: this machine on 2026-09-06 — Python 3.14.7, SQLite 3.53.4 — and the prior measurements named inline, chiefly [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) (BGE-M3, 220-entry slice, 17 needs) and [RAD-0049](RAD-0049-the-lexical-baseline.md) (11,156 entries over 59 coordinates, the same 17 needs). The entry counts and summary statistics quoted from tonight are from a clean rebuild of four Kotlin libraries — 7,092 entries — indexed through the current pipeline.

## Question

The working codex is six Kotlin modules, a Gradle plugin, a resident HTTP service, a 241 MB generative model and a native inference shim. It works, and it is a great deal of machinery to ask somebody to install before they can find out whether the idea is any good.

**What is the smallest thing that still does the job, given that a skill can carry it and the security model is deliberately removed?** A skill is a directory of files an agent loads: no build step, no service to run, ideally nothing to install. That constraint is severe enough to decide most of the design, so the question is really which parts survive it — and the answer turns out to be already measured, in two records that disagree about how far the stripping can go.

## Trail

### What the security model was actually buying, and what goes with it

The summariser is not a retrieval component. It is a **quarantine**: every doc comment is rewritten by a local model so third-party prose never reaches an agent verbatim, and `test7` measured that arm stopping a planted credential leak while the developer's task still completed. Take the threat model away and nothing argues for it.

Three things fall out together, and only the first is a loss:

- The **quarantine** goes. Raw library documentation reaches the agent as written. That is the whole of what is being given up, and it should be said plainly rather than buried: the surface RAD-0006 measured is reopened by choice.
- The **verification rules** go for free. Every one of them — imperative, markup, external names — exists to police the rewriter's output. With no rewriter they guard nothing.
- The **classifier** goes with them, for the same reason.

That is 964 lines of Kotlin across two modules, plus the inference shim, plus the model, plus what is by measurement the dominant cost in the pipeline.

### Removing the summariser makes retrieval better, not worse

This is the part that would be a guess if it had not already been measured. [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) put raw documentation against machine-written summaries on matched entries, queries and encoder:

| | raw | machine-summarised |
|---|---|---|
| first hit | 5 of 17 | 5 of 17 |
| within ten | **13 of 17** | 10 of 17 |

**Neutral at the head and better in the tail.** The widely quoted 29% → 77% lift was a property of *who wrote the summary* — hand-written entries — not of summarising, and RAD-0040 withdrew the claim explicitly.

Tonight's pass adds a second, cruder reason. Of 7,543 summaries produced by the pinned 270M model, **7,372 — 97.7% — began with the identical four words**, *"The capability is to"*. At a 13-word median that is roughly a third of every embedded string being a constant shared across the corpus. Whatever that is doing to the vector space, it is not discrimination.

So the minimal version indexes the raw doc comment. It is cheaper, it is simpler, and on the only measurement available it retrieves better.

**What is genuinely lost:** RAD-0040 also found that an index carrying *both* faces beats either alone — 15 of 17 within ten, against raw's 13 — because the two fail on different questions and the failure sets barely overlap. The minimal version gives that up. Two of seventeen is the price, and it is a real price.

### Embeddings cannot go, and this is what bounds how small it gets

The tempting next step is to drop the vector index too and lean on SQLite's full-text search, which would leave a tool with no model dependency at all. [RAD-0049](RAD-0049-the-lexical-baseline.md) measured exactly that, over real harvested documentation:

| | |
|---|---|
| entries indexed | 11,156 |
| gold targets absent from the index | 0 |
| **recall@1** | **1 of 17** |
| **recall@10** | **2 of 17** |

Its own verdict is *"close to useless at this corpus size"*, and the shape of the failure explains why no amount of tuning rescues it. For *"wait for a burst of rapid events to settle before acting on the last one"* — the need for `debounce` — the top ten contains a test scheduler and a WebSocket ping. **There is no lexical bridge between how a problem is described and how a solution is named**, which is the entire reason the vector index exists. The two needs it did hit were the two where the caller happened to use the library's own vocabulary.

So a lexical-only build is not a smaller working version. It is a spine with nothing on it.

### But the corpus size is doing a lot of work in that number, and a skill's corpus is smaller

Worth holding onto rather than concluding from, because it is the one place the argument might turn. RAD-0019 measured lexical at **38% recall@1 and 58% by recall@10** over a 220-entry slice. RAD-0049 measured **1 of 17 and 2 of 17** over 11,156. Same method, same needs; two orders of magnitude apart in corpus, and the score collapses.

A machine-wide store over a whole dependency graph is the second case. **A skill-carried tool indexing one project's direct dependencies is much nearer the first** — a few hundred to a couple of thousand entries. Nobody has measured lexical at that size, and it is the single measurement that would decide whether the minimal version needs a model at all. It is an open question in this record, not a finding.

### Python reaches SQLite. It does not reach Lucene, and does not need to

The premise for choosing Python was that it can get at both the database and Lucene. Half of that holds.

**SQLite, yes, and better than expected.** Verified on this machine: Python 3.14.7 ships SQLite 3.53.4 with FTS5 compiled in. A full-text index over doc comments costs one `CREATE VIRTUAL TABLE` and no dependency whatsoever.

**Lucene, no.** PyLucene is a JCC-generated binding around the Java library: it needs a JVM, it is built rather than installed, and it publishes no wheels. Neither it nor `tree_sitter` is present on this machine, and PyLucene in particular is the opposite of something a skill can carry.

**And it is not needed, because Lucene is solving problems this corpus does not have.** At 7,092 entries and 1,024 dimensions the whole vector set is about 29 MB as float32, or roughly 7 MB quantised to int8. Ranking a query against it is one matrix multiply — sub-millisecond under numpy, and tolerable in the standard library's `array` module at a few thousand entries. Inverted indexes, segment merging and sharding are the wrong tools at this scale. **The vector index is the one component that gets simpler in Python rather than harder.**

### The model is the irreducible dependency

Something has to turn a need into a vector, and that is where the zero-install goal actually breaks. Three routes, none free:

- **Bundle a model** — `sentence-transformers` and its torch dependency. Hundreds of megabytes. Not carriable, and it makes the skill heavier than the thing it was simplifying.
- **A smaller runtime** — ONNX via `fastembed`, or similar. One wheel plus a model download. Carriable at a stretch; still an install step.
- **Call something already running** — an HTTP endpoint on the developer's own machine: MLX, Ollama, LM Studio. Nothing to install, one small client, and the skill degrades honestly when nothing answers.

The third is the only one consistent with the constraint, and it converts an install into a **runtime precondition**. That is the honest boundary of "carried in a skill": the files travel, the model does not.

### Parsing, and a failure rate this project has already measured

The current harvester parses Kotlin and Java with tree-sitter. In Python that is a pip dependency with wheels — real, but a dependency.

The naive alternative is about thirty lines: find a `/** … */` block, take the next non-blank line as the declaration it belongs to. It is wrong sometimes, and this project already knows roughly how often — the harvester carries a note that a comment binding to a symbol it does not describe happened **670 times in 681,000**. Around a tenth of a percent, for a personal tool, against a dependency. That is a defensible trade, though the number was measured for a different extractor and should not be quoted as if it were this one's.

### One rule that is not security and should survive

The visibility rule reads as a safety feature and is not one: a `private` member is not a capability, and returning it wastes a result slot on something the caller cannot call. It is correctness.

Its two halves port very differently. The **bytecode oracle** — ASM plus Kotlin metadata, resolving what a consumer can actually reach — does not port to Python cheaply and would be dropped. The **explicit-keyword screen** added tonight is pure text, about twenty lines, and ports trivially.

They are not interchangeable, and tonight's rebuild says by how much. On four Kotlin libraries the oracle dropped **476** entries as unreachable that carry no keyword of their own — implicit visibility, and members nested inside internal types. A Python build with the keyword screen alone keeps all 476. That is the cost of dropping the oracle, and it is larger than the 281 the keyword screen catches.

### The open question, measured: shrinking the corpus does not rescue lexical

Written into this record as the highest-value experiment available, and then run the same evening — `experiments/minimal-codex/codex.py`, 230 lines, standard library only. It indexed 7,321 entries from two libraries in **1.1 seconds**, against minutes for the Kotlin pipeline over the same material, because there is no model in it.

Scoped to **one library at 450 entries** — the corpus a skill-carried tool would actually see, and the size this record hoped would rescue lexical — over five needs written the way a caller would ask:

| | recall@1 | recall@10 |
|---|---|---|
| one library, 450 entries | **0 of 5** | 3 of 5 |

**The tail improves at small corpus and the head does not.** 3 of 5 within ten is far better than RAD-0049's 2 of 17 over 11,156 entries, so corpus size is real. But **recall@1 is zero**, and an answer engine that never puts the right thing first is not a smaller working version of anything.

**The failure is RAD-0049's, reproduced exactly.** For *"a lock so only one coroutine at a time runs this section"* the top four are `tryLock`, `unlock`, `lock`, `isLocked` — the target is `Mutex` itself. For *"do something after a delay without blocking the thread"*: `invokeOnTimeout`, `scheduleResumeAfterDelay`, and only then `delay`. It navigates to the right neighbourhood and cannot choose within it. That is the same discrimination failure as #37 and #42, arriving from a third direction.

And over the full 7,321 entries, *"only build an expensive log message when debug logging is actually switched on"* returns `kotlin.math.log` three times. There is no lexical bridge between the words a problem is described in and the words a solution is named with, and no corpus size fixes that.

**A scope-collapsed scoring — ranking the declaring type rather than the member — looked promising on two needs and is not reported as a number here, because the scorer could not handle top-level functions and undercounts. Suggestive, unmeasured.**

### What a cache-only harvest misses

Two of the four libraries indexed had **no sources jar in the build cache for the requested version** — `okio:3.10.2` and `kotlinx-serialization-json:1.8.0` — though other versions of both were present. The Kotlin implementation does not hit this because it fetches what the cache lacks into its own staging directory.

So a strictly cache-only tool indexes whatever the developer's builds happened to download, which on this sample was half of what was asked for. A fetch from the public repository is about ten lines of `urllib` and adds no dependency, but it turns a purely local tool into one that reaches the network, and that is a design choice rather than an oversight to fix silently.

One correction to the trail above: this record asserted that sources are published on the coordinate the build resolves. That holds for `kotlinx-coroutines-core` and does **not** hold for `okio`, whose sources exist only under platform variants. The `-jvm` fallback is wanted on the sources path too, not only for classes.

### Answering with the library instead, and why the input is not there

The member-level result above points one way out: hand back the *library*, not the member. It removes the discrimination problem, it is what [RAD-0064](RAD-0064-the-skill-is-the-overview.md) argues for, and it is the shape that lets an agent reach library-level guidance without a directory of per-library skills in its context. `experiments/minimal-codex/libindex.py` builds exactly that — one document per library, assembled with no model from the POM `<description>` and the doc comments of the library's public *types*. 150 libraries in 3 seconds.

**It does not work, and it fails differently from the member-level index.**

| need | top three |
|---|---|
| parse and generate json | `google-api-services-androidpublisher`, `google-api-services-storage`, `snakeyaml-engine-kmp` |
| make http requests to a web server | `mongodb-driver-core`, `opentelemetry-instrumentation-api`, `guava` |
| read and write files and streams of bytes | `mailapi`, `snakeyaml-engine-kmp`, `bson` |

**The failure is size bias, and it is measurable.** Across ten unrelated needs the libraries reaching a top-three slot average **166 types against a corpus mean of 73**. A document assembled by concatenating a few hundred type descriptions matches almost any query, so breadth beats relevance and general-purpose libraries become universal attractors. More text per library makes this worse, not better.

**And the authored library-level text mostly is not there.** Three sources checked on this machine:

- **An authored skill in the artifact: 0 of the first 400 cached binary jars.** The `META-INF/ai-skills/` convention RAD-0065 documented is this project's own; nothing in the wild ships one.
- **Package-level documentation: absent.** Neither `kotlin-stdlib` nor `kotlinx-coroutines-core` carries `package-info`, `package.html` or `module-info` in its sources jar.
- **The POM description: authored, and thin.** 1,395 of 1,477 cached POMs (94%) carry a non-empty `<description>` — but only **34 of the 150 libraries whose sources are actually cached** had one, because the coordinates that carry sources skew to platform variants, and the ones that do read like *"JSR305 Annotations for Findbugs"*.

**So the discriminating text a library-level answer needs does not exist in the artifacts.** It is not a retrieval problem and no ranking change reaches it. Something has to write a short overview that says what this library is *for* and what it is *not* for — and the only two candidates are a human, who demonstrably does not, and a model, which is the component being removed.

**That is not the contradiction it looks like.** The objection to a model is that it puts a second agent in the loop; per-entry summarisation also made it the dominant runtime cost. Generating **one overview per library at index time** is neither: it is a single call per coordinate, paid once, producing an artifact the consuming agent reads directly with no model between it and the answer. RAD-0064 reached the same place from the specification. The measurements here say the input for the model-free version simply is not published.

### The package is a better unit than either, and a peer agent found it

Put to the Gemini sharing this station — the consuming agent this is built for — the library-level failure produced a useful disagreement. Its position: knowing the library and its *primary abstraction* is most of discovery, member overloads are noise while discovering, and grouping by package while indexing public top-level functions beside types would reach declarations like `delay` without any model. `experiments/minimal-codex/pkgindex.py` tests exactly that: 965 packages over 150 artifacts, mean 24 members each, built in 4 seconds.

**Indexing top-level functions is a straightforward win.** On `kotlinx-coroutines-core` it captured **189 top-level functions against 65 types**. `delay` was invisible to a type-only index and became the one clean rank-1 hit. That failure was structural, not a ranking artefact, and nothing about corpus size or scoring would have found it.

**Package granularity roughly triples the defensible answers.** Over ten unrelated needs, 5 or 6 land somewhere a caller could act on, against 1 or 2 at library level — `com.google.common.io` for byte streams, `com.google.common.util.concurrent` for concurrency, `coil3.memory` for caching with expiry, where the library index had returned a mail API and two Google API client libraries.

**The stated reason for that improvement was wrong, and the correction is the interesting part.** The prediction here was that packages are more uniform in size and would therefore break the size bias. They do not: top hits average **78 members against a corpus mean of 24**, proportionally slightly *worse* than the library-level 166-against-73. What actually changed is that **a package is a topic and a library is a vendor** — a large package that wins on length still wins within one subject, so it is more often right. Size bias was never the disease; topical incoherence was.

**It is still not enough, and it fails on the case the peer named.** For *"a lock so only one coroutine at a time runs this section"* the top hits are `kotlinx.coroutines` and `kotlinx.coroutines.flow`; `kotlinx.coroutines.sync`, where `Mutex` lives, is absent from the top three. A large root package swamps a small specific one. Over five coroutine needs: **1 of 5 at rank 1, 3 of 5 with the right package in the top three.**

Three granularities measured on the same corpus and the same method:

| unit | outcome |
|---|---|
| member | recall@1 **0 of 5** at 450 entries |
| library | 1–2 of 10 defensible; size-biased 2.3× |
| package, with top-level functions | 5–6 of 10 defensible; 1 of 5 at rank 1 on hard needs |

### Two stages, and the stdlib-only version comes back

The swamping problem turned out not to be a normalisation bug. In `kotlinx-coroutines-core` the package holding the answer, `kotlinx.coroutines.sync`, has **two members**; `kotlinx.coroutines.flow` has 124. No length normalisation rescues a two-entry document, because the right answer has almost no text.

**But the member index already knows.** For the same need, member-level retrieval returns a top ten of which **seven are inside `kotlinx.coroutines.sync.Mutex`** and an eighth elsewhere in that package — while the package-document index cannot get that package into its top three at all. Member-level retrieval is reliably good at landing in the right neighbourhood and bad only at choosing within it, which RAD-0049 measured independently.

So: **retrieve members, then aggregate their hits up to the declaring package.** It uses the strength and discards the weakness, and a small package stops being penalised for having little text because its score comes from member hits rather than document length.

**The first version of this failed, and the failure is the useful part.** Counting hitting members at depth 40 scored **0 of 5** — it reintroduced the size bias exactly, because the largest package collects the most hits in a deep result set. Counting only works shallow.

**Weighting each hit by reciprocal rank fixes it and removes the tuning knob:**

| aggregation | depth 10 | depth 25 |
|---|---|---|
| count of hitting members | 4 of 5 | **2 of 5** |
| summed reciprocal rank | **4 of 5** | **4 of 5** |
| normalised by package size | 3 of 5 | 1 of 5 |

Reciprocal rank holds at both depths; count collapses; dividing by package size over-corrects and lets tiny packages win on one weak hit. Four granularities now, same corpus and method:

| approach | rank-1 |
|---|---|
| member | 0 of 5 |
| package document | 1 of 5 |
| **member hits aggregated to package, reciprocal-rank weighted** | **4 of 5** |

**Held deliberately short of a finding.** This is five hand-written needs against one library, where RAD-0049 used seventeen over fifty-nine coordinates. It is the first thing measured all evening that suggests a standard-library-only build might actually answer rather than merely generate candidates, and that is exactly why it should be re-run against the real needs corpus before anyone believes it.

### The two-stage result did not survive the real corpus

The section above was written as suggestive and asked to be re-run before anyone believed it. It has been, against RAD-0049's own seventeen needs and its pinned fifty-nine coordinates — all fifty-nine had sources cached, and the minimal harvester indexed **13,866 entries in 1.4 seconds**.

| | member-level | two-stage, package, reciprocal rank |
|---|---|---|
| rank-1 | **2 of 17** | **3 of 17** |
| within ten / within three | 2 of 17 | 5 of 17 |

**The member-level column reproduces RAD-0049** — 2 of 17 against its 1 of 17 — which is the control that says this harvester and that one are measuring the same thing. **The two-stage column is 3 of 17, against the 4 of 5 measured on five hand-written needs over one library.**

**That is not a weaker version of the earlier result; it is a different one.** The most likely reason is that the five needs were written by the same agent that knew the targets, and phrased in vocabulary close to them — which RAD-0049 had already identified as the single condition under which lexical succeeds. A self-authored need set does not test retrieval, it tests whether the author can paraphrase.

**So the embedding precondition stands**, and the recommendation below is unchanged. Two stages help — 2 to 3 at rank 1, 2 to 5 in the head — and do not come close to closing the gap.

**One incidental cost, not previously measured.** Two of the seventeen gold targets are absent from the index entirely, where RAD-0049 reported none missing. That is the naive regex extractor failing to see declarations tree-sitter catches, and it is a price of dropping the parser that this record had costed only by reference to somebody else's error rate.

## Findings

**Measured.**

- **Raw documentation retrieves at least as well as machine summaries** — 5 of 17 first-hit for both, and 13 of 17 within ten for raw against 10 of 17 summarised (RAD-0040). The generative half is not carrying retrieval.
- **Both faces together beat either alone**, 15 of 17 within ten (RAD-0040). Dropping the summariser costs two of seventeen in the tail.
- **Lexical-only over raw documentation collapses at scale** — recall@1 1 of 17, recall@10 2 of 17 over 11,156 entries (RAD-0049) — against 38% and 58% over 220 entries (RAD-0019).
- **Python 3.14.7 ships SQLite 3.53.4 with FTS5**; neither PyLucene nor `tree_sitter` is present, and PyLucene needs a JVM and a build rather than an install.
- **97.7% of 7,543 generated summaries opened with the same four words** on the pinned 270M model.
- **The bytecode oracle drops 476 entries the keyword screen does not**, on the four-library corpus rebuilt tonight.
- **Lexical at one library's scale — 450 entries — scores recall@1 of 0 of 5 and recall@10 of 3 of 5.** The corpus-size hypothesis in this record's own recommendation is answered: small helps the tail and does nothing for the head.
- **A standard-library harvester indexed 7,321 entries in 1.1 seconds**, and extracted more from `kotlin-stdlib` than the Kotlin pipeline did — 6,871 against 6,303 — because nothing screens what the compiled artifact would have judged unreachable.
- **Sources are not always published on the coordinate the build resolves**: present for `kotlinx-coroutines-core`, absent for `okio`, whose sources exist only under platform variants.
- **A cache-only harvest found no sources for two of four requested coordinates**, at the versions asked for.
- **No authored library skill exists in the wild**: 0 of the first 400 cached binary jars carry `META-INF/ai-skills/`.
- **No package-level documentation** in the `kotlin-stdlib` or `kotlinx-coroutines-core` sources jars.
- **94% of cached POMs carry a `<description>`, but only 34 of 150 libraries with cached sources do**, and those are one-line labels.
- **A library-level lexical index assembled from POM description plus type docs is size-biased**: top-three hits average 166 types against a corpus mean of 73, and answer canonical needs wrongly.
- **Indexing public top-level functions beside types captures 189 against 65 on `kotlinx-coroutines-core`**, and is the only reason `delay` is findable at all.
- **Package granularity gives 5–6 defensible answers of 10 against the library index's 1–2**, while remaining size-biased at 78 members per top hit against a corpus mean of 24 — so the gain is topical coherence, not uniformity.
- **A large root package swamps a small specific one**: `kotlinx.coroutines.sync` never reaches the top three for a mutex need, though `Mutex` is the answer.

- **Two-stage retrieval scores 3 of 17 at rank 1 and 5 of 17 within three on the real corpus**, against member-level's 2 of 17 — having scored 4 of 5 on five self-authored needs over one library. The toy result did not replicate and should not be quoted.
- **Aggregating by raw count is depth-sensitive and reintroduces size bias**: 4 of 5 at depth 10, 2 of 5 at depth 25. Reciprocal-rank weighting holds at 4 of 5 at both.

**Assumed, not measured.**

- That brute-force ranking is fast enough at one developer's corpus size. The 29 MB figure is arithmetic from entry count and dimension, not a timing.
- That a naive doc-binding extractor mis-binds at a rate near the 670-in-681,000 the current harvester records. That number belongs to a different extractor.
- That an embedding endpoint can be assumed present on the machine. It is a precondition, and no measurement here says how often it holds.
- ~~That the two-stage result survives a real corpus.~~ **Measured: it does not.** 3 of 17 against 4 of 5. A need set written by the agent that knows the targets measures paraphrase, not retrieval.

## Recommendation

**Not a commitment.** This records what the smallest working version looks like and what it gives up; whether to build it is a separate call.

**Amended after measurement: the unit of the index should be the package, not the member and not the library.** Members cannot be discriminated, libraries are vendors rather than topics, and packages are the only one of the three that is a subject. Index public top-level declarations beside types, or declarations like `delay` are invisible.

**The minimal version is five pieces and one precondition.**

1. **Store** — SQLite, one `entry` table plus an FTS5 virtual table. Standard library, no dependency.
2. **Harvest** — read the `-sources.jar` from the build system's cache with `zipfile`, pull doc-comment/declaration pairs, screen anything opening with an explicit `private` or `internal`.
3. **Embed** — POST to a local embedding endpoint the developer already runs, and say so plainly when nothing answers.
4. **Search** — a dot product over a stored matrix for the semantic arm, FTS5 for exact vocabulary matches. No Lucene, no vector database.
5. **Serve** — a stdio MCP server, or no server at all: a script the skill invokes directly.

**Index the raw doc comment, not a summary.** It is better on the only measurement available and removes the largest component, the model, the native shim and the dominant runtime cost in one move.

**The POM description is an unscreened carrier, and it stays that way deliberately.** [RAD-0071](the-package-that-did-not-exist-yet.md) assesses reported research in which agents followed installation guidance in published `llms.txt` files and executed attacker-registered packages — a mechanism that is not prompt injection, engages none of this project's content controls, and is the fabricated-capability case `test6` already measured at 4 of 17. The rewriter quarantine would not have stopped it, so removing the quarantine costs nothing against that class. What `libindex.py` *adds* is the POM `<description>` — publisher-controlled free text, present on 94% of cached POMs, screened by nothing — which is the same shape the attack exploits. Left unscreened on purpose: the point of this exercise is to find out how small the thing gets when nothing is guarded, and screening the first carrier that appears would forfeit the measurement. That exemption is scoped to a personal tool inside `experiments/` and must not travel with the code if it leaves.

**Say what was given up, in the skill itself.** Third-party documentation reaches the agent verbatim. That is a deliberate, stated trade for a personal tool on one developer's machine, and it is the difference between this and the product — not a simplification of it.

**What would change the answer.**

- ~~If lexical holds up at a skill's corpus size, the model precondition disappears.~~ **Measured and answered the same evening: it does not.** 0 of 5 at recall@1 over 450 entries. The embedding precondition stands, and a stdlib-only build is a candidate generator rather than an answer engine.
- **The remaining way out is to change what an answer is.** Lexical reliably lands in the right declaring scope while failing to pick the member — so if the response unit were the library or the type, as [RAD-0064](RAD-0064-the-skill-is-the-overview.md) argues and #38 asks, the lexical arm might be sufficient after all. That is now the highest-value measurement, and it needs a scorer that handles top-level declarations, which the one used here did not.
- If the 476 entries the oracle catches turn out to matter in use, the keyword screen alone is not enough and the Python build needs a way to read bytecode after all.

## Connections

- [RAD-0040](RAD-0040-does-summarising-improve-retrieval.md) — the measurement that lets the summariser go.
- [RAD-0049](RAD-0049-the-lexical-baseline.md) — the measurement that says embeddings stay.
- [RAD-0019](RAD-0019-retrieval-at-scale.md) — lexical at 220 entries, the corpus-size counterweight to RAD-0049.
- [RAD-0064](RAD-0064-the-skill-is-the-overview.md) — the argument that the index is substrate rather than the product, which this record is the cheapest possible expression of.
- [RAD-0006](RAD-0006-development-time-prompt-injection.md) — the surface deliberately reopened here.
- [RAD-0071](the-package-that-did-not-exist-yet.md) — an attack the quarantine never covered, and the POM description as a carrier this record introduces.
