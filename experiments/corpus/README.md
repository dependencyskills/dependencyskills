# The shared corpus database

Every experiment that needed harvested content was re-deriving it. `test12` walks 1,892 sources
jars for prose; `test14` walks the same jars again with tree-sitter for declared surfaces; anything
needing both walks them twice. The Gradle cache is **version-pinned and immutable**, so that work is
repeated against input that cannot change.

This extracts once into one SQLite file that several experiments read — and that opens in any
ordinary query tool.

```
uv run --with tree-sitter --with tree-sitter-language-pack python build.py
```

Three caches are harvested: the **Gradle module cache** for Kotlin and Java, the **npm cache** for
JavaScript and TypeScript, and the **SwiftPM cache** for Swift. `--no-maven`, `--no-npm` and
`--no-spm` narrow the build, `--limit N` caps each.

## Why SQLite and not JSONL

The deciding factor is the access pattern rather than the format. The dominant query across the
experiments that need this is a **per-library lookup** — *what does library X declare* — which
`test14` performs repeatedly and which costs a full file scan in JSONL. That is the one experiment
with an open measurement, and the one most likely to be re-run.

The rest follows: `sqlite3` is in the **standard library**, so it adds no dependency to a repository
whose harnesses are deliberately dependency-free; it is a single file, gitignored exactly as
`prose-corpus.jsonl` is; and a new question needs a SQL clause rather than a new filter loop.

**The counter-argument, recorded because it is a good one.** This project's own architecture holds
that text is the source of truth and an index is derived and disposable — [RAD-0010](../../docs/knowledge/research/0010-how-the-codex-is-stored-and-served.md)
keeps Lucene rebuilt from text and never authoritative. The consistent version of that here would be
JSONL as the record with SQLite built from it. That is right for the **product**. For an experiment
corpus rebuilt in minutes from an immutable cache, the extra layer buys nothing: the cache *is* the
source of truth, and [`test12/PROSE-MANIFEST.md`](../test12/PROSE-MANIFEST.md) already pins which
coordinates were read.

So this database is **derived and disposable**. Delete it and rebuild.

## Schema

| table | |
|---|---|
| `libraries` | `library` (maven `group:artifact:version`, npm `name@version`, spm `owner/repo@version`), `ecosystem`, `publisher`, `artifact`, `version`, `source`, `library_type`, `source_sets`, and per-library entry and declared counts |
| `entries` | one documented declaration — `library`, `ecosystem`, `publisher`, `lang`, `doc_format`, `symbol`, `signature`, `doc`, and the label columns below |
| `declared` | every name a library declares: `library`, `name`. This is the per-library surface a resolution check needs, and the reason this is SQLite |
| `meta` | what the build was, and its counts, **per ecosystem as well as in total** — a single total hides a harvest that quietly collected nothing |

Indexed on `entries(library)`, `entries(publisher)`, `entries(lang)`, `entries(ecosystem)`,
`entries(label)`, `entries(tags)`, `entries(doc_format)`, `declared(library)` and `declared(name)`.

`source` is where a library actually came from, and the honest answer differs by ecosystem: for npm
it is the **registry tarball URL**, which the cache index records verbatim; for spm it is the **git
remote**, read from the bare clone's config; for maven it is `unknown`, because the Gradle cache's
`files-2.1` tree does not record which repository served an artifact — androidx comes from Google's
maven rather than Central and nothing in the path says so. Writing `maven-central` there would be a
guess.

`doc_format` records the documentation convention, which the language does not imply: `kdoc`,
`javadoc`, `jsdoc`, `swift-markup`, `plain`. It exists because a block-comment extractor finds
**nothing** in Swift's `///` line comments and reports that as an absence rather than an error.

`library_type` is `multiplatform`, `single-platform` or `unknown` — see
[Multiplatform is most of the maven side](#multiplatform-is-most-of-the-maven-side) below.
`source_sets` holds the Kotlin source-set roots, comma-bounded like `tags`.

```sql
-- the query this exists for
SELECT name FROM declared WHERE library = 'io.ktor:ktor-client-core-jvm:3.5.2';

-- prose from one ecosystem, one publisher
SELECT symbol, doc FROM entries WHERE lang = 'java' AND publisher = 'io.grpc';

-- the JavaScript surface test9's results point at
SELECT symbol, signature, doc FROM entries WHERE ecosystem = 'npm' AND lang = 'typescript';

-- documented declarations per publisher
SELECT publisher, count(*) FROM entries GROUP BY publisher ORDER BY 2 DESC;

-- prose from libraries that carry an apple target, which the coordinate does not tell you
SELECT e.symbol, e.doc FROM entries e JOIN libraries l USING (library)
 WHERE l.source_sets LIKE '%,appleMain,%';
```

## Multiplatform is most of the maven side

**1,216 of the 1,798 harvested maven libraries are Kotlin Multiplatform — 68%.** Nothing in a
coordinate says so. On Maven Central a KMP library sits in the same namespace as a plain JVM one —
`foo`, `foo-jvm`, `foo-android`, `foo-iosarm64`, `foo-js`, all under one group — and the difference
is invisible unless you already know the suffix vocabulary. A population that large is too big to
average over silently, so it is recorded.

**Mind the unit — it reverses the answer.**

| counting | multiplatform | single-platform |
|---|---|---|
| maven library rows | **1,216 (68%)** | 582 |
| distinct base artifacts | **397** | 582 |
| documented declarations | 45,941 (**8.5%**) | 491,522 (**91.5%**) |
| distinct third-party publishers | 46 | 125 |

Both readings are true. KMP dominates the row count because it **publishes one artifact per
target** — those 1,216 rows are 397 base artifacts, a 3.06× inflation, and each target ships the
same `commonMain` prose, which the `(publisher, doc)` dedup then collapses. So the *content* of the
maven side is overwhelmingly Java from single-platform libraries: 469,922 of 537,463 entries.

Any result quoted as a percentage of this corpus has to say which unit it counted.

The discriminator costs nothing, because the jar is already open: **a KMP sources jar is rooted on
source sets** (`commonMain/`, `jvmMain/`, `appleMain/`) where a plain JVM one is rooted on package
directories (`okhttp3/`, `com/`). `source_sets` keeps what was found rather than throwing it away
after the verdict, because it answers the next question too — *which platforms does this actually
carry source for*.

*Considered and not used:* Gradle Module Metadata (`.module`) carries
`org.jetbrains.kotlin.platform.type` per variant and is authoritative about what was **published**.
Two reasons against. Only 333 of these version directories have one cached, since Gradle keeps the
metadata it happened to fetch; and it describes the publication rather than the jar actually read
here, which is the thing being labelled.

**A KMP library ships Kotlin — not Swift, and not JavaScript.** The sources jars in this cache hold
142,436 `.kt`, 43,977 `.java`, and **zero `.swift`**. The apple and js outputs are compiled from the Kotlin
in `appleMain` and `jsMain`; the sources jar carries what they were compiled *from*. That is worth
stating because the opposite is a natural assumption, and it settles what harvesting a KMP library
can and cannot supply.

npm and SwiftPM are recorded as `single-platform` rather than `unknown`. That is a positive
statement, not a default: an npm package is JavaScript for JavaScript runtimes and a SwiftPM package
is Swift for Apple platforms. Shipping CommonJS and ESM side by side is two module formats of one
language, which is not the same thing as one source set compiled to several platforms.

## Labelling

Every entry carries a `label`, a `label_source` and a `label_note`, because this corpus exists to
run experiments on and a corpus with no ground truth cannot support one.

| label | means |
|---|---|
| **`presumed_benign`** | harvested from a real registry, unaudited — **the default and the working assumption** |
| `benign_verified` | somebody can say *why* it is fine, and `label_note` says what the basis was |
| `malicious` | known-bad: this project's own payloads, or a labelled third-party benchmark |
| `suspect` | something flagged it and nobody has adjudicated |

The default is deliberately not `benign`. Half a million declarations harvested from a package cache
have not been audited, and [RAD-0036](../../docs/knowledge/research/0036-can-the-corpus-be-poisoned.md)
records that the negative class in any classifier is written by whoever can publish a package. A
column asserting these were verified would be the most misleading thing in the database. *Assume
they are fine; do not claim they were checked.*

**Notes are for humans to understand what is going on; tags are for filtering and finding.** `label_note` is prose explaining *why* a label
was applied, written to be read — *"the maintainer's own published libraries — authorship, not
reputation"*. `tags` is comma-bounded structured text for `WHERE` clauses. Neither names the harness
that produced the row, because a descriptor has to keep meaning when a test is renamed.

`label_source` records how the label was arrived at — `harvested` (no claim beyond provenance),
`authored` (we wrote it, so it is a fact), `benchmark` (a third party supplied it), `reviewed` (a
person decided).

**Promotion to `benign_verified`** is driven by `verified-publishers.txt`, one prefix per line with
the basis written beside it, so the *reason* travels into `label_note`. Be sparing with it: the
default already assumes these are fine, and this column claims something narrower. "It is a big
company" is a reputation rather than a reason, and the xz backdoor was two years of patient work
inside exactly that assumption. The one entry there today is the maintainer's own published
libraries — **authorship, not reputation**, which is the same kind of certainty that makes the
payloads' label a fact.

**The payloads live in the database too.** They are inert (`.invalid` sinks, nothing executable) but
they are working injection prose, which is one more reason the file is gitignored — and being
**binary**, it is not read by an agent indexing the repository, the same argument as the packed
transcripts.

```sql
SELECT label, label_source, count(*) FROM entries GROUP BY 1, 2;
SELECT doc FROM entries WHERE label = 'malicious' AND lang = 'prose';
```

## Using it from a harness

The harnesses that predate this database each re-derive what it now holds — `test13` and `test14`
walk the cache for declared surfaces, `test17` reads `test12`'s prose file. None has been
repointed, deliberately: their published numbers were measured against what they built themselves,
and quietly swapping the input would make those numbers unreproducible without saying so.

New work should read the database. The two queries that replace most of that machinery:

```python
import sqlite3
db = sqlite3.connect("experiments/corpus/corpus.db")

# a library's declared surface — the lookup this exists for
surface = {r[0] for r in db.execute(
    "SELECT name FROM declared WHERE library = ?", (lib,))}

# documented declarations, filtered however the question needs
db.execute("""SELECT library, symbol, signature, doc FROM entries
              WHERE label = 'presumed_benign' AND doc_format = 'javadoc'""")
```

## Ecosystems

| `ecosystem` | what it is |
|---|---|
| `maven`, `npm`, `spm` | a package registry — third-party, versioned, resolvable by coordinate |
| **`filesystem`** | a source tree read in place: **first-party code, the developer's own project** |
| `authored` | written by this project, so its label is a fact rather than a presumption |

`filesystem` is the half the corpus does not yet hold, and it is not a minor one. `test0` measured
that agents pick the right library **0 of 18** unaided and that *local* knowledge — which module
already does this, which library this project prefers — is the gap model progress cannot close.
[Thirteen slug functions](../../site/src/content/docs/case-studies/thirteen-slug-functions.md) is a
first-party problem, not a dependency problem.

It also has a **different trust posture**. Third-party prose is an injection surface; first-party
code is not, because you wrote it. So the security work that dominates this repository applies to
the registry ecosystems and largely not to this one — with the exception RAD-0029 records, where an
agent launders third-party text *into* first-party source and it is trusted on re-harvest.

**The privacy constraint is stricter here than anywhere else.** A first-party harvest walks the
developer's own tree, so paths carry project names and the home directory. `source` must be the
repository's remote URL or `local` — **never a path** — and `private-groups.txt` has to gain a
filesystem equivalent before any such harvest runs. The database is binary, so
`experiments/redact.py` cannot see inside it to catch a mistake.

## Where the content comes from

**The Gradle module cache** (`~/.gradle/caches/modules-2/files-2.1`). Sources jars, walked for
`/** */` blocks bound to the nearest declaration. Kotlin and Java.

**The SwiftPM cache** (`~/Library/Caches/org.swift.swiftpm/repositories`). A directory of **bare git
clones**, read through `git archive` at the newest release tag — a bare clone has no working tree
and materialising one would put sources on disk for nothing. This is deliberately the *cache* rather
than Xcode's DerivedData checkouts, whose paths carry **project names**: identity comes from the git
remote in the clone's config, which is a public URL and nothing else.

Swift is the reason `doc_format` was added before any Swift was harvested. It documents with `///`
**line** comments, so a run of consecutive lines is one doc comment and a block-comment extractor
aimed at it finds nothing — reporting a missing extractor as an absent convention. Both forms are
read. Tests, examples and benchmarks are skipped, because a git checkout ships them and a published
jar or tarball does not, and the three ecosystems should mean the same thing by *a library's
surface*.

**The npm cache** (`~/.npm/_cacache`). Content-addressed: `index-v5` holds one JSON record per
fetched URL and the `integrity` hash in it locates the tarball body under `content-v2`. Package
identity therefore comes from the **registry URL the record was keyed on**, never from a path on
disk — which is both the honest answer to *where did this come from* and the only one that cannot
leak a home directory.

Most of what npm ships is not source. The harvester parses `.js`/`.mjs`/`.cjs` and `.ts`/`.mts`/
`.cts`, and skips anything over 1 MB or averaging more than 500 bytes per line — bundled and
minified files, which are one enormous line with no documentation in them. **TypeScript carries most
of the weight**: npm publishes built JavaScript, and the documentation survives in the `.d.ts`
beside it rather than in the emitted code.

## What it holds, and what it does not

**Both faces of an entry.** The syntactic one (`symbol`, `signature`) and the semantic one (`doc`),
which is the shape the codex itself uses — and which no previous harness kept together. `test12`
extracts prose without symbols on purpose; `test14` extracts surfaces without prose.

**Deduplicated, at the unit where the duplication actually happens.** Maven dedupes by
`(publisher, doc)`: KMP publishes one artifact per target, so the same doc comment appears in
`runtime-desktop`, `runtime-js`, `runtime-wasm-js` and so on — `test5` measured a real harvest as
63% duplicate and `test12` reproduced 61% independently. npm dedupes **within the package**, because
its duplication is a different shape: the repetition is *inside one tarball*, the same module
emitted as CommonJS and as ESM with the doc comments surviving a third time in the `.d.ts`.

**Newest version per artifact only.** Older versions are near-duplicate prose and inflate every
count. On both sides the harvester walks back to the newest version that actually yields something,
rather than taking the newest and reporting an empty one as an absence — checking only the newest
silently dropped 42 maven artifacts, `androidx.compose.ui` and `androidx.core:core` among them.

**Swift is small and worth having anyway.** 23 repositories, 20 of them the transitive closure of
`firebase-ios-sdk` and `GoogleSignIn-iOS`. It is a thin ecosystem on this machine for a structural
reason: shared logic arrives from **Kotlin Multiplatform** through maven rather than through SwiftPM,
and the corpus already holds 418 maven libraries carrying an apple target. What SwiftPM adds is not
missing API surface so much as a **second documentation convention** — and the only real Swift in
the corpus, since the KMP libraries that serve Apple platforms carry Kotlin.

**Licence headers, tagged rather than dropped.** A file-header comment sits within 400 bytes of the
first declaration below it, so it binds to a symbol it does not describe — 670 rows in 681,000. They
carry the `license-header` tag and a note saying so. Tagging beats filtering for the reason this
repository keeps re-learning: a silent filter and an empty result look identical, and anything
measuring retrieval on this corpus should exclude them *deliberately*.

```sql
SELECT * FROM entries WHERE tags NOT LIKE '%,license-header,%';
```

**npm's residual duplication is 8%, maven's is 59%** — 143,520 npm rows hold 131,797 distinct doc
texts against 537,463 maven rows holding 217,966. That is the dedup unit showing through rather than
a defect: npm dedupes within the package, which catches the CommonJS/ESM/`.d.ts` triplication that
is nearly all of its repetition, while maven dedupes within the publisher and leaves the same doc
comment standing across unrelated groups.

**No dependency edges.** A resolution check wants a library's own surface *plus its actual
dependencies*, and this has only the first. See [RAD-0039](../../docs/knowledge/research/0039-where-the-dependency-graph-comes-from.md).

## Whose cache this is

**A local package cache holds what its owner builds, and nothing else.** That is the whole reason it
is a usable corpus — it is real resolved dependencies rather than a curated list — and it is also
the limit. This one is Kotlin-Multiplatform-heavy: 46 of its 165 third-party maven publishers are
multiplatform, which is a plausible ratio for a codebase built that way and not the ratio a random
sample of Maven Central would give. The npm side is heavy on build tooling and framework internals,
because that is what a site and a set of harnesses pull. SwiftPM is 23 repositories, 20 of them one
vendor's transitive closure.

None of that is a defect in the cache. It is what a cache *is*, and any harvest of one inherits it.

This is a **convenience sample**, and naming it is not a disclaimer to be waved past. It bounds what
the corpus can support:

- **Fine for questions about surface and shape** — what a doc comment looks like, how much prose a
  real library ships, whether a payload survives extraction. `test12` and `test5` are that kind of
  question, and their findings reproduced against independently-derived numbers.
- **Not a basis for a population claim.** "*x% of libraries do y*" needs a sample drawn to support
  it, and this one was drawn by whatever the project it belongs to happened to depend on.
- **Publisher concentration is the sharpest edge.** androidx, JetBrains and Google account for 777
  of 1,798 maven libraries. A result that turns out to be a house style of one of them would look
  like a property of the ecosystem from inside this corpus.

The `library_type`, `publisher` and `ecosystem` columns exist partly so a result can be split rather
than averaged, and a split that changes the answer is the cheapest available check on this.

## Privacy

Every ecosystem has an exclusion list and the harvester **refuses to run without one** —
`test12/private-groups.txt` for maven, `private-npm-scopes.txt` for npm, `private-swift-repos.txt`
for SwiftPM. All three are gitignored,
because the thing they name is private work. A real cache contains the developer's own packages and
possibly a client's, and a filter that silently does nothing is the same failure as a scan that
matches no files. An empty list is a deliberate statement; a missing one is an oversight, and that
distinction is the whole reason the refusal exists.

Prefix matching is bounded at a `.` or `/`, so `com.oddlyclever` cannot also exclude
`com.oddlycleverly` and `@acme` cannot swallow `@acmecorp`.

The database is gitignored; only counts are ever published.
