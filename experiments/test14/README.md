# test14 — pricing the last prose rule

`test13` killed one prose signal and left one standing: legitimate directives in real published
library documentation name **an API the library declares** (63%), and the injected ones named
nothing declared (0%). That is `test10`'s resolution check applied to prose rather than identifiers,
which put it in the category with an unbroken record here — resolving against a declared surface —
rather than the category that has failed every time.

**An unpriced rule is not a result.** RAD-0021 was withdrawn on exactly this number, `test10` prices
every rule it ships, and `test13` watched a rule go from 4% to 29.8% when the population widened.

> **How often does a directive in real library documentation point at something the library never
> declares?**

Run: `uv run --with tree-sitter --with tree-sitter-language-pack python price_resolution.py`
(`--limit N` for a quick pass). Reads the local Gradle cache. No network, nothing executed.

## Result — the rule is 8× too expensive

**1,688 libraries, 209,527 distinct declared names, 232,781 distinct doc comments.**

| outcome | of directives | of all docs |
|---|---|---|
| contains a directive verb | 100% | 23.24% |
| — no code reference at all | **73.8%** | 17.17% |
| — resolves in its own library | 13.6% | 3.17% |
| — resolves in **another** library | 5.1% | 1.18% |
| — **resolves nowhere → flagged** | 7.5% | **1.73%** |

**Cost: 1.732%, against `test10`'s entire catalogue at 0.221%.** Roughly one real doc comment in
every 58 would be rejected. The rule does not meet the bar this project has set for every other
control it carries.

So **both** prose candidates are now priced and both fail:

| rule | cost | |
|---|---|---|
| mentions something external (`test13`) | 29.8% | dead |
| names an API the library declares | **1.73%** | dead |

**The prose gap remains open, and it is now open with its two best candidates measured rather than
assumed.**

## The ceiling was lower than it looked

**73.8% of directive-bearing documentation contains no code reference at all.** A resolution rule
cannot say anything about those, so even a perfect version of it would only ever apply to a quarter
of the directives it is aimed at. That was not visible before the corpus existed.

## What the taxonomy bought

Reporting *why* resolution failed, rather than only how often, changed the reading twice.

**Cross-library references are real but do not rescue the rule.** Raising the universe from 150
libraries to 1,688 moved cross-library resolution from 1.7% to **5.1%** of directives — so the
concern that a legitimate directive naming an *inherited* or *cross-library* symbol would score as
an injection was correct, and it is worth about three points. The cost still lands 8× over budget.
That also means a Dokka-derived surface, which `test1` measured as recovering little that a
transitive graph join does not, would not close a gap of this size — and would reach only the
Kotlin fifth of this corpus.

**Duplication was not distorting it.** KMP publishes one artifact per target, so the same doc
appears in `annotation`, `annotation-iosarm64`, `annotation-js` and so on. Deduplicating dropped
591,398 comments to 232,781 — **61% duplicate, independently reproducing `test5`'s 63%** — and moved
the cost only from 1.807% to 1.732%.

## Three fixes, and where tuning would have started

Printing the flagged samples caught two parser bugs that were inflating the number, and it is worth
recording that the first number was wrong:

| | cost | what was wrong |
|---|---|---|
| first run | 6.125% | bare **PascalCase** treated as a code reference — English capitalises the first word of every sentence, so `Cancel the subscription…` offered `Cancel` as a symbol |
| after | 2.609% | language keywords in backticks — "pass in `null`", "made `final`" — counted as API |
| after | 1.991% | code fences and `{@inheritDoc}` counted as directives |
| full corpus, deduped | **1.732%** | — |

Each fix is defensible on its own terms: prose produces PascalCase and never camelCase, a keyword
is not library API, and an example is not a directive. **But three consecutive fixes that each
lower the number is where tuning begins**, and the honest position is that 1.73% is a floor
obtained with the parser tuned in the rule's favour, not a neutral estimate. It still fails.

## The asymmetry that matters more than the number

The cost was measured against **232,781 real doc comments**. The benefit was measured against
**3 payloads**, all variants of one attack.

That asymmetry is the actual state of the prose problem: the false-positive rate of any prose rule
can be measured precisely and cheaply, and its catch rate cannot be measured at all until there is
a payload set with the breadth the corpus now has. **Any future prose rule will hit the same wall**,
so the useful next step is not another rule — it is a payload set wide enough to price one against.
