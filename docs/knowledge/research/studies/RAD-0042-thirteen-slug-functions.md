# Thirteen Slug Functions

RAD-0042 · 2026-08-21 · v1

A worked example of an agent-and-human failure mode: **a solved problem gets
re-solved repeatedly because the existing solution is undiscoverable from where
the work happens.** This is the codex's thesis at intra-repository scale — the
same discoverability gap this project attacks between libraries, caught inside a
single codebase where it is cheaper to measure.

Setting: a Kotlin Multiplatform monorepo — mobile client, desktop admin UI, a
CLI, and a Ktor server. All counts below are from `grep` over tracked sources
(build output excluded) and the timeline from `git log --diff-filter=A` plus
`-S` pickaxe searches. Names, paths and identifiers are anonymised; the numbers
are as measured.

---

## The short version

A module named `capabilities/slugs` was created in **week 0** with a working
implementation and a 50-line test suite. It was declared in `settings.gradle.kts`,
so it configured and its tests ran in CI on every build.

**No code outside the module ever referenced it.** Not once, in eleven weeks.

In that same period the exact regex literal `[^a-z0-9]+` was written **13 times
across 6 files**, in four different output formats, in every tier of the stack.

The duplication was found by accident, during an unrelated audit of which
capability modules could be extracted into a shared library. The module stood
out for having no consumers to migrate.

---

## Rules this case study supports

1. **An unreferenced library module is a defect, not dead weight.** It is the
   strongest early signal available that a concept is about to be reinvented,
   and it is trivially detectable.
2. **Before writing a normalisation, formatting, or ID-generation helper, search
   the whole repository for the *idiom*, not the *name*.** Names diverge across
   subsystems; the distinctive literal does not.
3. **When a shared utility is introduced, wire a consumer to it in the same
   change.** A module with no dependency edge is invisible to every tool an
   author uses inside a consumer.
4. **Name the module after the concept, and cover the concept's whole scope.** A
   partial solution gets found and then extended by hand, which is where the
   bugs went.

---

## Timeline

| When | Event |
|---|---|
| **Week 0** | `client/capabilities/slugs` created — `SlugGenerator` + 50-line test suite. Never added as a dependency of anything, then or later. |
| **Week 6** | Module relocated to `capabilities/slugs` during a consolidation. **The same day**, an inline slug appears in `DetailScreen.kt` (mobile) and another in the CLI. |
| **Week 6** | `AdminListViewModel.kt` builds a library path from a title slug plus a slugified identifier — reinventing the *slug-plus-unique-suffix* pattern, not just the slug. |
| **Week 11** | `ResourceKey.kt` written from scratch: 17 lines, careful KDoc, six unit tests. A duplicate of the module's function with a different separator. |

Eleven weeks. Thirteen reimplementations. Zero references to the module.

---

## Where the copies live

| Tier | Sites | Format | Notes |
|---|---:|---|---|
| server utility | 1 | `snake_case` | The best implementation — Unicode-aware, maps special characters to names |
| admin UI | 4 | mixed | One shared helper plus three inline copies that don't use it |
| CLI | 7 | hyphen | Six near-identical copies **in one file**, plus a filesystem variant |
| mobile | 2 | hyphen | One replaces spaces only — punctuation leaks into identifiers |

### The duplication was not harmless

Two sites independently reinvented "slug + unique suffix" and **both corrupted
the suffix**:

```kotlin
// AdminListViewModel.kt and Cli.kt
val nanoIdSlug = record.nanoId.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')
```

The identifier is a NanoID: case-sensitive over a 64-symbol alphabet including
`-` and `_`. Lowercasing collapses it to 38 symbols — lossy, not reversible, and
distinct identifiers can now produce the same string. These become directory
names.

**The suffix existed to guarantee uniqueness. Reimplemented by hand, it
reintroduced exactly the collision it was there to prevent.**

Separately, every ASCII-only variant returns the **empty string** for Cyrillic,
Greek or CJK input — the character class matches everything. Only the server
implementation avoids this, and only because it happens to use a Unicode-aware
character test rather than a regex.

---

## Why it happened

Five mechanisms, roughly ordered by contribution. Only the last is about care.

### 1. No dependency edge means no discoverability

The module was on no consuming module's classpath. Nothing an author does
*inside a consumer* would surface it — not import completion, not go-to-symbol,
not a grep scoped to the module being edited, not the dependency tree. It was
invisible exactly where it needed to be visible.

**For an agent specifically:** context-gathering is usually scoped to the module
under edit and its declared dependencies. An orphan module falls outside that
scope by construction. No amount of reading the local package will reveal it.
This is the intra-repo shadow of the cross-library problem: an agent cannot use
what its working context never puts in front of it.

### 2. The cost asymmetry favours retyping

The inline version is three lines. Searching a large monorepo for an existing
utility costs more than typing it, and the searcher cannot know in advance
whether the search will succeed. Below roughly five lines, reuse loses to
convenience unless discovery is free.

### 3. One concept, four names

The same idea is called `slug`, `resourceKey`, `handle`, and `nanoId` depending
on the tier. Keyword search fails across a vocabulary split: an author searching
the term their own subsystem uses finds nothing and reasonably concludes nothing
exists.

### 4. Copy-propagation is a different failure from invention

Six of the thirteen sites are in a single file. That is one decision cloned five
times, not six independent inventions — a within-file pattern that spreads by
proximity. It needs a different remedy from cross-module discovery, and
conflating the two overstates how often the concept was genuinely re-derived.
**Count files, not occurrences, when measuring reinvention.**

### 5. The module's name did not cover its own scope

Two sites needed a slug *with a unique suffix*. The module offered only the slug
half; the random-identifier half lived in a different layer entirely. An author
who found the module would still have had to invent the combination — and both
who did got it wrong.

---

## The finding that matters most

`ResourceKey.kt` is the **most carefully written** slug function in the
repository. It has a KDoc explaining the convention and the rule for when to
auto-fill it. It has six unit tests covering digits, punctuation runs, trimming
and the empty case.

It is still a duplicate.

**Diligence did not prevent this and could not have.** Any intervention has to
operate on discoverability, not on care. Instructions of the form "check whether
this already exists" fail against mechanisms 1–3 regardless of how
conscientiously they are followed.

---

## Tests that would have caught it

Ordered by how early each fires. Adapt the file-walking to your build; the logic
is the point.

### 1. Orphan module detector — fires at week 0

The earliest possible signal, eleven weeks before the first duplicate.

```kotlin
@Test
fun `every module has a dependent`() {
    val declared = settingsFile.readText()
        .lines()
        .mapNotNull { INCLUDE.find(it)?.groupValues?.get(1) }
        .toSet() - ENTRY_POINTS          // apps, servers, things nothing depends on

    val referenced = buildFiles
        .flatMap { f -> DEPENDENCY.findAll(f.readText()).map { it.groupValues[1] } }
        .toSet()

    assertEquals(
        emptySet(), declared - referenced,
        "orphan modules — unreachable from any consumer, and likely to be reinvented"
    )
}
```

### 2. Duplicate literal census — fires at week 6

A distinctive literal in more than one module is duplication with a fingerprint.
`[^a-z0-9]+` is specific enough that two occurrences are never coincidence. Use a
budget file so counts ratchet downward only.

```kotlin
@Test
fun `no distinctive literal spreads across modules`() {
    val budget = budgetFile.readLines().associate { line ->
        val (literal, max) = line.split(" = "); literal to max.toInt()
    }

    WATCHED_LITERALS.forEach { literal ->
        val hits = sourceFiles.count { literal in it.readText() }
        val allowed = budget[literal] ?: 1
        assertTrue(hits <= allowed, "'$literal' now in $hits files, budget $allowed")
    }
}
```

### 3. Idiom fence — prevents regression

Once a capability owns a concept, ban the raw idiom outside it. This is what
keeps the fix fixed; without it the fourteenth copy arrives after consolidation
and nobody notices.

```kotlin
@Test
fun `slug normalisation lives only in the nanoid capability`() {
    val offenders = sourceFiles
        .filterNot { it.path.contains("capabilities/nanoid") }
        .filter { SLUG_IDIOM.containsMatchIn(it.readText()) }

    assertEquals(
        emptyList(), offenders,
        "Hand-rolled slug found. Use Slug.of() or Slug.unique() " +
            "from com.example.capabilities.nanoid instead."
    )
}
```

**The message must name the replacement.** A failure nobody can act on gets
suppressed.

### 4. Concept-name registry — catches vocabulary drift

The weakest and the most interesting. A glossary maps a concept's aliases to its
owning module; the test asserts that public declarations matching an alias live
in that module. This is the only one that catches *the fourth name for an
existing idea*, which is what defeated search here.

```kotlin
// glossary.txt:  slug|resourceKey|handle|permalink = capabilities/nanoid

@Test
fun `registered concepts are declared only by their owner`() {
    glossary.forEach { (aliases, owner) ->
        val strays = publicDeclarations()
            .filter { decl -> aliases.any { it in decl.name.lowercase() } }
            .filterNot { it.module == owner }

        assertEquals(emptyList(), strays, "concept owned by $owner declared elsewhere")
    }
}
```

---

## What these tests will not catch

State this plainly wherever the tests are adopted — a check trusted beyond its
reach is worse than no check.

- **Semantically identical, textually different.** An author who writes a
  character loop instead of a regex produces the same function and trips
  nothing. Tests 2 and 3 are fingerprint matchers, not semantic ones.
- **The fifth name.** The registry knows only the aliases someone remembered to
  add. Coverage decays as fast as vocabulary grows.
- **Justified divergence.** The filesystem-path variant in this repo is
  *correctly* different — it preserves case for a reason. A fence that cannot
  express exceptions gets suppressed wholesale, and a suppressed test is a
  deleted test.
- **The cost asymmetry itself.** No test makes searching cheaper. Tests convert
  a silent failure into a loud one *after* the fact; they do not change the
  economics that caused it.

---

## The durable intervention is not a test

Ranked by expected effect:

1. **Put the capability on the consumer's classpath.** Everything else is a
   workaround for its absence. A dependency edge makes the solution visible to
   completion, symbol search, and every agent's default context-gathering.
2. **Name the module for the concept, and cover the whole concept.** Partial
   solutions get found and then hand-extended, which is where the collision bugs
   came from.
3. **Put the search string in the docs.** A line in the module KDoc or README
   reading *"if you are about to write `[^a-z0-9]`, you want this"* does more
   than any of the four checks above, because it matches what an author actually
   types.
4. **Then add the tests**, as a ratchet against regression rather than as the
   primary defence.

This is why the codex is a searchable index over what libraries already ship,
not a pile of documents an agent has to know to go looking for. Discoverability
from where the work happens is the whole point — at repository scale here, and
at ecosystem scale in the [findings](../RAD-0001-cost-of-a-skill-per-dependency.md).

---

## Reusable heuristics

Extracted for direct use in an agent's instructions:

- Before writing any string-normalisation, ID-generation, or formatting helper,
  grep the repository for the **distinctive literal** you are about to write —
  not the function name you would give it.
- If a helper you need is 3–5 lines, that is exactly the size most likely to
  already exist and most likely to be retyped. Search harder at that size, not
  less.
- When you find an existing utility that covers *part* of what you need, extend
  the utility rather than composing around it locally. The two bugs in this case
  study are both local compositions around a partial solution.
- When adding a shared module, add a consumer in the same change, or it is
  invisible.
- A module in the build with no dependents is a bug report, not a to-do.
