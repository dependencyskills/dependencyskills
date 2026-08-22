---
title: Thirteen slug functions
description: One working module, no consumers, and the same three-line idiom retyped thirteen times across every tier of a real codebase.
---

The codex exists because a capability an agent needs is usually undiscoverable
from where the agent is working. That is easiest to dismiss as an
inter-*library* problem — someone else's code, on Maven, that you have never
heard of. This case study is the same failure inside a single repository, where
every fact is measurable and none of it is exotic. The full record, with the
four tests that would have caught it, is in the repository under
`docs/knowledge/case-studies/`.

Setting: a Kotlin Multiplatform monorepo — mobile client, desktop admin UI, a
CLI, and a Ktor server. Counts are from `grep` over tracked sources; the
timeline from `git log`. Identifiers are anonymised; the numbers are as measured.

## The short version

A module named `capabilities/slugs` was created with a working implementation
and a 50-line test suite. It was declared in the build, so it configured and its
tests ran in CI on every build.

**No code outside the module ever referenced it — not once, in eleven weeks.**

In that same period the exact regex literal `[^a-z0-9]+` was written **13 times
across 6 files**, in four different output formats, in every tier of the stack.
It was found by accident, during an audit of which modules could be extracted
into a shared library. The module stood out for having no consumers to migrate.

## The duplication was not harmless

Two sites independently reinvented "slug plus a unique suffix," and **both
corrupted the suffix**:

```kotlin
val nanoIdSlug = record.nanoId.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
```

The identifier is a NanoID: case-sensitive over a 64-symbol alphabet that
includes `-` and `_`. Lowercasing collapses it to 38 symbols — lossy, and
distinct identifiers can now collide. These strings became directory names. **The
suffix existed to guarantee uniqueness; reimplemented by hand, it reintroduced
exactly the collision it was there to prevent.**

Separately, every ASCII-only copy returns the **empty string** for Cyrillic,
Greek or CJK input — the character class matches everything. Only the server
copy escaped this, and only because it happened to use a Unicode-aware test
rather than a regex.

## Why it happened

Five mechanisms. Only the last is about care.

**No dependency edge means no discoverability.** The module was on no consumer's
classpath. Nothing an author does *inside a consumer* would surface it — not
import completion, not go-to-symbol, not the dependency tree. For an agent this
is decisive: context-gathering is scoped to the module under edit and its
declared dependencies, and an orphan module falls outside that scope by
construction. No amount of reading the local package reveals it.

**The cost asymmetry favours retyping.** The inline version is three lines.
Searching a large monorepo costs more than typing it, and you cannot know in
advance whether the search will succeed. Below about five lines, reuse loses to
convenience unless discovery is free.

**One concept, four names.** The same idea was called `slug`, `resourceKey`,
`handle`, and `nanoId` across the tiers. An author who searched the term their
own subsystem used found nothing and reasonably concluded nothing existed.

**Copy-propagation is not invention.** Six of the thirteen sites are in a single
file — one decision cloned five times, spreading by proximity. Count files, not
occurrences, when measuring reinvention.

**The module's scope was too narrow to reuse.** Two sites needed a slug *with a
unique suffix*. The module offered only the slug half; the random-identifier
half lived in a different layer. Whoever found the module still had to invent the
combination — and both who did got it wrong.

## The finding that matters most

The most *carefully written* slug function in the repository — full KDoc, six
unit tests covering digits, punctuation, trimming and the empty case — is still
a duplicate. **Diligence did not prevent this and could not have.** Instructions
of the form "check whether this already exists" fail against the mechanisms
above no matter how conscientiously they are followed. The intervention has to
operate on discoverability, not on care.

## The durable intervention is not a test

1. **Put the capability where the work happens.** A dependency edge makes the
   solution visible to completion, symbol search, and every agent's default
   context. Everything else is a workaround for its absence.
2. **Name the module for the concept, and cover the whole concept.** Partial
   solutions get found and then hand-extended — which is where the collision
   bugs came from.
3. **Put the search string in the docs.** A README line reading *"if you are
   about to write `[^a-z0-9]`, you want this"* matches what an author actually
   types.
4. **Then add the tests**, as a ratchet against regression — not as the primary
   defence.

This is the codex's thesis in one repository. Discoverability from where the
work happens is the whole point: at repository scale here, and at ecosystem
scale in [the findings](/findings/), the fix is the same — make what already
exists findable at the moment of need, in the words the searcher actually uses.
