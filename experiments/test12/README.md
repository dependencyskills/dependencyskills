# test12 — a wide prose corpus, from what is already on the machine

Every corpus this project measured against came from **59 coordinates published by three
organisations**. [RAD-0036](../../docs/knowledge/research/0036-can-the-corpus-be-poisoned.md)
recorded why that is a problem: three publishers is not a definition of *normal library
documentation*, it is one house style. Documentation voice — length, whether preconditions are
stated at all, how imperative a comment is willing to be — is set by a team's review culture and
does not converge the way linted identifier form does.

> **Can a corpus wide enough to define "normal" be built without fetching anything?**

Yes. A developer's Gradle cache is everything they have ever built against, and IDE source-download
has been fetching `-sources.jar` alongside it for years.

## Result

| | before | after |
|---|---|---|
| publishers | 3 | **274** |
| libraries | 59 | **883** |
| documentation | — | **235,627 comments, 7.6M words** |
| languages | Kotlin | Kotlin 49,428 + **Java 186,199** |

Run: `python3 cache_harvest.py` (`--dry-run` for counts only)

**82.5%** of cached versions carry a `-sources.jar`, across 290 of 353 publishers. Harvesting drops
**360,401 duplicate comments — 61%**, independently reproducing `test5`'s 63% duplicate finding on
a completely different corpus.

It earned its keep immediately: `test13` had a rule that cost **4%** on a narrow sample and
**29.8%** here, and would have been adopted on the strength of the narrow number.

## What this is, and is not

This is a **prose** corpus. It deliberately does **not** bind documentation to resolved symbols the
way `test5/harvest.py` does, because the question is what real documentation *sounds like*, not
what it documents. Consequences:

- no tree-sitter and no network — doc comments are `/** ... */` in both Kotlin and Java, so a
  scanner suffices, and it picks up the Java sources the Kotlin-only path skipped;
- **rules that need a declared surface cannot be priced on it** (`test13`'s surviving signal is
  blocked on exactly this);
- it is a different *population* from `test5`'s corpus — that one is declared dependencies of
  sample projects, this is everything ever built against, including transitive tails and old
  versions. Do not read a surface-reduction number off this one.

## What it cannot supply

Measured rather than assumed, because it changes what the corpus is good for:

| ecosystem | available here | why |
|---|---|---|
| Kotlin, Java | **274 publishers** | `-sources.jar` ships with almost everything |
| **JavaScript** | **17 `.js` files in 1,892 jars** | KMP publishes per-target artifacts (242 JS-target, 192 iosarm64), but every sources jar contains **Kotlin source sets**. The JS exists only as compiled output |
| **Swift** | **zero** | same reason |

Since `test9` found **every prose payload that landed, landed in JavaScript**, that gap matters.
The JS corpus has to come from npm's cache instead — 2,016 distinct packages, recoverable from
`~/.npm/_cacache` where identity comes from the registry URL rather than from a filesystem path,
which also keeps private project names out of the harvest.

## Privacy is enforced, not requested

A real cache contains the developer's own packages and possibly a client's. Three defences:

1. the harvester **refuses to run** without `private-groups.txt` — a missing filter is an oversight,
   an empty one is a deliberate statement. Same reasoning as the leak scanner's vacuous-pass guard;
2. the corpus and the exclusion list are **both gitignored**; only `PROSE-MANIFEST.md` is
   publishable, after review;
3. the manifest records publishers, so it must be read before it is committed.
