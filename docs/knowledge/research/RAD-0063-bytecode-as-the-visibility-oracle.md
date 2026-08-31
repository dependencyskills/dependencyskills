# Bytecode as the Visibility Oracle

RAD-0063 · 2026-08-31
Keywords: should private members be indexed; should lambdas be skipped; do Kotlin lambdas belong to the public API; why does a private field outrank the class; can the parser tell package-private from implicitly public; access flags as ground truth; scan bytecode first to decide what to index; is the main jar on disk; aar and klib packaging; what fraction of a corpus is not callable.
Measured against: `commons-text:1.12.0` indexed end to end through the service — 1,447 entries, `gemma-3-270m-it-qat-Q4_0` as summariser, `bge-small-en-v1.5` as encoder — `kotlin-stdlib:2.3.21` harvested (6,414 entries, no model needed), and one real `~/.gradle/caches/modules-2/files-2.1` of 3,836 cached sources jars, macOS arm64, 2026-08-31.

## Question

> **Should members a developer cannot call be indexed at all — and if not, what decides?**

The first library ever indexed through the service surfaced the problem immediately. Asked *"substitute variables in a template string"*, the top result was:

```
org.apache.commons.text.StringSubstitutor.enableSubstitutionInVariables
  private boolean enableSubstitutionInVariables
```

Right library, right class, **private field**. It outranked `StringSubstitutor` itself and its `replace` methods. A capability that cannot be invoked is not a capability.

The proposal this record exists to weigh: **scan the bytecode first and let it say what to index.**

## Trail

### How much of a corpus is this

Measured over the 1,447 entries harvested from one mid-sized library:

| | entries | share |
|---|---|---|
| `public` | 960 | 66% |
| `private` | 292 | 20% |
| `protected` | 30 | 2% |
| no visibility keyword | 165 | 11% |

So **roughly a third of the corpus is not part of the API a consumer can call** — each one summarised by a model call, stored, embedded, and competing in every retrieval.

### Why the parser cannot settle it

The 165 with no keyword are not one thing. Sampled:

```
T build()                                     ← implicitly public: interface member
boolean test(int codePoint)                   ← implicitly public, or package-private
final class StrBuilderReader extends Reader   ← package-private
```

The same absence of a modifier means public on an interface and package-private on a class. Deciding requires resolving the enclosing type, which is a different job from parsing a file — and there are worse cases: Java package-private has no keyword at all, and Kotlin `internal` compiles to **public with a mangled name**, so it is invisible to a consumer while looking public in source and public in bytecode.

### Why bytecode is the better oracle

A class file carries access flags the compiler emitted — `ACC_PUBLIC`, `ACC_PROTECTED`, `ACC_PRIVATE`, plus `ACC_SYNTHETIC` and `ACC_BRIDGE`. That is not an interpretation of the source; it is what a consumer links against, which is exactly the question being asked.

It also answers things the source cannot: synthetic accessors, bridge methods, and the pairs Kotlin generates for properties and default arguments — all of which are entries today and none of which a developer types.

### Whether the bytecode is there

The proposal is only cheap if the main artefact is already on disk. Measured across 3,836 cached sources jars, a naive `<artifact>-sources.jar` → `<artifact>.jar` lookup finds it **43% of the time** — which sounds disqualifying and is mostly an artefact of the lookup. What is actually beside the other 2,154:

| | count | carries bytecode |
|---|---|---|
| `.klib` | 1,199 | no — Kotlin/Native, no JVM classes exist |
| `.jar` under another base name | 981 | yes |
| `.aar` | 545 | yes, inside `classes.jar` |
| `.pom` / `.module` / `.zip` | 473 | metadata |

So bytecode is reachable for most JVM libraries, but **the packaging is not uniform** and a single filename convention will not find it. A Kotlin/Native artefact has no bytecode at all, and for those this question does not arise — there is nothing to filter with.

### Lambdas: the rule is visibility, not lambda-ness

"Skip lambdas too" is the obvious next filter and it is a trap, which measuring caught.

On the **Java** corpus it looks free. Nothing in `commons-text`'s 1,447 entries is a lambda at all — `lambda`, `access$` and `$$` each match zero symbols and signatures — because the harvester parses source and extracts *declarations*, and a lambda expression is not one.

On **Kotlin** it is the opposite. Harvesting `kotlin-stdlib:2.3.21` gives 6,414 entries, of which **1,464 have a lambda in their signature — 23% — and 1,451 of those are public**:

```
public inline fun <T> T.apply(block: T.() -> Unit): T
public inline fun assert(value: Boolean, lazyMessage: () -> Any)
public inline fun check(value: Boolean, lazyMessage: () -> Any)
```

A rule that skipped lambdas would delete a quarter of the standard library's public API, including the functions a Kotlin developer reaches for most. Only **13** of the 1,464 are non-public.

So the criterion is not whether a lambda is present. It is **whether the enclosing declaration is callable**, and a lambda inherits that answer — a lambda in a public function's signature is API and stays; a lambda body compiled to a synthetic method is not and goes. `ACC_SYNTHETIC` is exactly that distinction, which is another thing the access flags decide and the source cannot.

### The awkward interaction

[#28](https://github.com/dependencyskills/dependencyskills/issues/28) already specifies *public and protected only* for bytecode indexing. The sources path does not hold that rule, so the same library indexed two ways would offer different APIs depending on which path ran. Whatever is decided here should be one rule applied in both places rather than two rules that happen to agree.

## Findings

**Measured**

- 32% of one real library's harvested entries — 457 of 1,447 — are private, or carry no visibility keyword.
- A private field outranked its own class for a plain-language query, on the vector path.
- A naive sources-to-main-jar filename mapping locates bytecode for 43% of 3,836 cached sources jars; most of the remainder is `.aar` or a differently-named `.jar`, both of which carry classes.
- **23% of `kotlin-stdlib`'s entries carry a lambda in their signature, and 99% of those are public.** Skipping lambdas as a class would remove a quarter of the public API of the standard library; the Java corpus, where no lambda appears at all, gives the opposite and misleading impression.

**Argued, not verified**

- Source parsing cannot determine effective visibility without resolving the enclosing type, and cannot see Kotlin `internal` at all.
- Access flags are the authoritative answer because they are what a consumer links against.
- Dropping a third of the corpus shortens a pass by roughly a third, since cost is per entry. Not measured; the pass measured here was 195 seconds for 1,447 entries.

## Recommendation

**Not a commitment.** Two things should be measured before this is built, and the second could overturn it.

1. **Which entries a bytecode pre-scan would actually drop**, run over the same library, compared against the visibility keywords already in the store. That validates the oracle against a case where the answer is known.
2. **What dropping them does to retrieval.** Every retrieval number this project rests on was measured over a corpus that included these entries. Removing a third of it changes the denominator and possibly the ranking, and "the results got better" has to be shown rather than assumed. It is plausible that a private member's prose is sometimes the best description of a capability its public wrapper documents poorly.

If both hold, the shape is: read the main artefact where it exists, treat its access flags as the filter, and index only what a consumer could call — with the same rule applied on the sources path and the bytecode path, per #28.

**One rule, stated once:** the unit of decision is the visibility of the enclosing declaration. Members inherit it, and lambdas inherit it. Anything phrased as "skip X" — private members, lambdas, synthetics — should be checked against that sentence before it is built, because two of the three would have been wrong on one language or the other.

**What would change the answer:** if the retrieval measurement shows non-public entries carrying real answers, the control moves from *do not index* to *do not rank* — keep them, and exclude them from results rather than from the store.

## Connections

- [#30](https://github.com/dependencyskills/dependencyskills/issues/30) — the observation that opened this.
- [#28](https://github.com/dependencyskills/dependencyskills/issues/28) — bytecode indexing, which already specifies public and protected only.
- [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md) — why the sources jar is the content, and therefore why bytecode here is a filter rather than a source.
- [RAD-0062](RAD-0062-screening-an-identifier-that-cannot-be-rewritten.md) — the other question about what an entry is allowed to carry.
