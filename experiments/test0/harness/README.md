# test0-harness

Runs the [`fixtures`](../fixtures) bake-off. **The tests are the
experiment**: each asserts what a parser arm extracts from a given doc level, so
the findings live as passing/failing assertions rather than prose.

## Run

```
./gradlew test
```

JDK 21 toolchain (enough for the PSI and Dokka arms); bump to 22+ when the
tree-sitter arm lands. Gradle is the reused 9.7.0 wrapper.

## The arms

| Arm | How | Role | Status |
|---|---|---|---|
| **PSI (raw)** | `kotlin-compiler-embeddable` PSI | raw KDoc, no native deps | **done** |
| **Enriched** | PSI resolved within the source set | inherited docs + `@sample` expansion (stands in for Dokka) | **done** |
| **real Dokka** | Dokka analysis | production-fidelity enrichment | deferred |
| **tree-sitter** | jtreesitter + grammar | cross-language (for test1) | later |

## Now

`FixtureWiringTest` proves the harness sees the fixture across all four levels and
that L3's custom tags are present. Once a parser produces `Entry` values
(RAD-0013), these assertions move onto the entries themselves — capability
quality per level, the inheritance delta, `@suppress` exclusion, and what each arm
does with an unknown tag.

## Connections

- [fixtures](../fixtures) — the graded source this parses.
- RAD-0009 (parse bake-off), RAD-0011 (content value / custom tags), RAD-0013
  (the `Entry` shape).
