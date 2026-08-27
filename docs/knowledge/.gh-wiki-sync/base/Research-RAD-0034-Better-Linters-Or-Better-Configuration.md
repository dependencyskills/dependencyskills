# Better Linters, or Better Configuration?

RAD-0034 · 2026-08-24 · v1
Keywords: do we need a better linter or better configuration; SwiftLint has no function-naming rule; does another Swift tool do better; can stock linters catch an identifier payload; enforcing the bounds by configuration in all four ecosystems; why the configured rule catches what stock rules miss.

**Opened by a gap `test9` measured and closed by testing rather than reasoning.** Swift carried
every identifier payload and SwiftLint objected to none of them, because it ships no
function-naming rule. The obvious question was whether a different Swift tool would do better.
**It is the wrong question:** all four ecosystems already enforce the bounds by configuration, and
the configured rule catches the payload every stock linter misses.

## Question

`test9` stages 1–2, over Kotlin, Java, Swift and JavaScript with a fixed payload set:

| language | `loud` | `underscored` | `camel` | prose |
|---|---|---|---|---|
| Kotlin | caught | caught | **missed** | missed |
| Java | *cannot carry* | caught | **missed** | missed |
| **Swift** | *cannot carry* | **missed** | **missed** | missed |
| JavaScript | property key only | caught | **missed** | missed |

Swift is the weak position, and `camel` — the form two frontier models obeyed — is missed
everywhere. **Is that a tooling problem or a configuration problem?**

## Trail

### Measured: every one of the four enforces the bounds by configuration alone

No plugin, no custom code, no change of tool.

| language | tool already in use | mechanism | catches `underscored` | catches `camel` |
|---|---|---|---|---|
| Swift | SwiftLint | `custom_rules`, regex | **yes** | **yes** |
| Java | Checkstyle | `MethodName` with a length-bounded pattern | **yes** | **yes** |
| JavaScript | ESLint | `id-match` with the same pattern | **yes** | **yes** |
| Kotlin | detekt | `FunctionNaming` + `FunctionMaxLength` | yes | **yes** (rule ships disabled) |

**The Swift gap is a default-configuration gap, not a capability gap.** SwiftLint's `custom_rules`
takes arbitrary regex with a severity, which is enough to express both the casing rule it lacks
and the word-count bound [RAD-0030](Research-RAD-0030-A-Conventions-Filter-From-Real-Corpora) measured.

### The finding that matters more than the Swift fix

**A length-bounded name pattern catches `camel` in all four languages.** That is the payload that
passed every stock linter, that Gemini 3.1 Pro accepted 2 of 3, and that Claude Haiku 4.5 accepted
2 of 3. It is also the *portable* form — no backticks, no spaces, legal everywhere — which
[RAD-0027](Research-RAD-0027-The-Identifier-As-A-Free-Text-Channel) v4 identified as the more dangerous
attack precisely because the linters passed it.

So the gap `test9` exposed is closable in every ecosystem, today, with configuration. That is a
better outcome than a new tool and a much better one than a new detector, and it is
[ADR-0007](Decisions-ADR-0007-Conform-To-Existing-Conventions) working as intended: conform to the
tool the ecosystem already runs, and add the measured bound to it.

### What this does not settle, and it is the expensive half

**The false-rejection cost of these configurations is unmeasured**, and there is already a
warning sign. The ESLint pattern used in the probe flagged the class name `C` alongside both
payloads — a pattern tuned for methods applied to every identifier. A rule that rejects real
library content is worse than no rule, and RAD-0030's whole discipline is that the bound must be
priced against a real corpus before it is proposed.

The bound has been priced for Kotlin — a word-count limit at the measured p99 costs 0.47% on
14,899 real entries. **It has not been priced for Swift, Java or JavaScript at all**, because
this project has no harvested corpus in those ecosystems.

### The alternatives, recorded so the question is closed

Considered and not pursued, because configuration suffices:

- **swift-format** (Apple) — a formatter with lint mode; would need evaluating, and adds a second
  tool where SwiftLint is already present.
- **PMD, Error Prone** for Java — heavier analysers aimed at correctness, not naming.
- **ESLint plugins** — `unicorn` and similar carry naming rules, but `id-match` is core and needs
  no dependency.

Adding a tool costs adoption friction in a consumer's build. Adding a rule to the tool they run
costs a config block, which is why this record recommends against the swap.

## Findings

**Measured (2026-08-24).** SwiftLint `custom_rules`, Checkstyle `MethodName`, ESLint `id-match`
and detekt `FunctionMaxLength` each catch both the `underscored` and `camel` payloads using
configuration alone. Swift's stock silence is a default-configuration gap.

**Reasoned.**

- The right unit of intervention is a **configuration per ecosystem**, not a tool per ecosystem.
- A length-bounded naming pattern is the single most valuable rule found, because it is the only
  thing measured that catches the portable, lint-invisible form.
- This closes a detection gap, not the channel: prose is untouched, and
  [RAD-0031](Research-RAD-0031-Which-Vectors-Reach-A-Real-Project)'s 46% precondition-free class is
  untouched.
- **The cost is unpriced in three of the four languages**, and one probe already produced a false
  rejection on a legitimate class name.

**What to find out, in order.**

1. **Price each configuration against real libraries.** Kotlin has a corpus; Swift, Java and JS do
   not. Harvest one per ecosystem and measure the false-rejection rate before proposing any of
   this. This is RAD-0030 question 2 and it gates everything else.
2. **Does the configured rule change stage 3?** `test9`'s obedience column is still empty. A rule
   that catches `camel` matters only if `camel` reaches an agent that acts on it.
3. **Do the four configurations agree?** If a bound tuned per ecosystem produces different
   thresholds, that is four filters to maintain rather than one, and the cost quadruples.
4. **Is `swift-format` worth evaluating anyway?** Only if 1 shows SwiftLint's regex rules
   misfiring on real Swift.

## Connections

- [RAD-0030](Research-RAD-0030-A-Conventions-Filter-From-Real-Corpora) — the measured bounds these
  configurations encode, and the pricing discipline question 1 enforces.
- [RAD-0027](Research-RAD-0027-The-Identifier-As-A-Free-Text-Channel) — the `camel` payload this closes, and
  why it was the dangerous one.
- [ADR-0007](Decisions-ADR-0007-Conform-To-Existing-Conventions) — conform to the ecosystem's tool
  rather than mint another; this is that principle producing a concrete answer.
- [RAD-0028](Research-RAD-0028-Sast-Tooling-As-A-Detection-Layer) — the earlier survey, which asked whether
  a *different class* of tool helped and found it did not.
