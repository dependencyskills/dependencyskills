# test4 — how much attacker-controlled text does each language's harvest path deliver?

RAD-0006 v4 measured that library prose redirects agents, but every payload in that work was
**hand-authored**: it never passed through a parser. That makes those results
parser-independent — they say how an agent treats harvested prose, and transfer across
ecosystems — but they say nothing about whether **harvesting** is riskier in one language than
another. Doc conventions differ enormously in how much free text they invite, and the parser
is a *filter*: what reaches an entry depends on whether it keeps the summary sentence or the
whole comment.

```bash
uv run --with tree-sitter --with tree-sitter-language-pack python harvest_surface.py
uv run --with tree-sitter --with tree-sitter-language-pack python harvest_surface.py --skip-b  # offline part only
```

Both parts run the **real test1 extractors**, imported rather than reimplemented, so this
measures the production-candidate parse path and not a mock of it.

## A. Delivery fidelity — does the payload survive the parser?

One semantically identical payload (an authority claim, a symbol to call, an exfiltration URL,
and an example code block) written in each language's native doc convention — `poisoned/`.

| language | doc chars | instruction | symbol | URL | code block | attacker-controlled |
|---|---:|---|---|---|---|---:|
| Kotlin (KDoc) | 446 | yes | yes | yes | yes | 67% |
| TypeScript (JSDoc) | 443 | yes | yes | yes | yes | 67% |
| Python (docstring) | 443 | yes | yes | yes | yes | 68% |
| Rust (`///`) | 388 | yes | yes | yes | yes | 62% |
| Swift (`///`) | 438 | yes | yes | yes | yes | 67% |

**Every parser is a passthrough. 5/5 languages deliver the payload perfectly intact** —
instruction, symbol, URL and example code — with roughly two-thirds of the harvested text
being attacker-controlled. Nothing is stripped, truncated or normalised, in any language.

That is the headline: **there is currently no filtering anywhere in the parse stage**, so the
parse stage is available as an enforcement point and is not yet doing any enforcing. Every
constraint RAD-0006 proposes (length caps, URL stripping, directive stripping) would live here.

## B. Surface size — how much free text does each convention carry?

The same extractors over real published libraries. **Domain is held constant — every library
is a CLI argument parser** — so differences are attributable to the language's doc convention
rather than to what the library does.

| language | library | docs | median | p90 | max | w/ code | w/ URL |
|---|---|---:|---:|---:|---:|---:|---:|
| Kotlin | kotlinx-cli-jvm:0.3.6 | 77 | 119 | 419 | 1702 | 0% | 0% |
| TypeScript | commander@15.0.0 | 134 | **89** | 369 | 1099 | 11% | 0% |
| Python | click==8.4.2 | 186 | **288** | 1076 | 5673 | 0% | 2% |
| Rust | clap_builder:4.6.6 | 536 | 176 | **1590** | 5696 | **42%** | 1% |
| Swift | swift-argument-parser@1.8.2 | 168 | 144 | 544 | 3337 | 5% | 1% |

**The prior speculation was wrong, and this is the point of measuring.** RAD-0006 v4 guessed
TypeScript would be the most exposed, on the reasoning that JSDoc invites long comments full
of `@example` blocks and markup. In a real library it is the **least** exposed of the five —
median 89 characters, the tersest convention measured.

What the data actually says:

- **Python carries the largest typical comment** (median 288, over 3× TypeScript). A docstring
  is an ordinary string literal with no comment syntax to escape and no convention pulling
  toward brevity, and it shows.
- **Rust has by far the heaviest tail and the most embedded code** — p90 of 1590 characters and
  **42% of doc comments containing a code block**, an order of magnitude more than any other
  language here. That is doctest culture: Rust doc examples are compiled and executed by
  `cargo test`, so the convention actively rewards putting runnable code in prose. It is the
  richest carrier of attacker-controlled *structured* content in the set.
- **Kotlin, Swift and TypeScript cluster low** (median 119/144/89) with little embedded code.

Ranking by typical volume: **Python > Rust > Swift > Kotlin > TypeScript.**
Ranking by tail and by embedded code: **Rust first, decisively.**

## What this does and does not establish

- It establishes that **the parse stage filters nothing today**, in every language, which is
  the actionable finding.
- It establishes that **doc-comment volume varies by roughly 3× across conventions** even with
  the library's job held constant, so a per-language budget is a meaningful control.
- It does **not** establish that a bigger comment is a more dangerous one. Volume is a proxy
  for attack surface, not a measure of exploitability; a two-line comment carried every payload
  in RAD-0006 perfectly well. The useful reading is that Rust and Python paths give an attacker
  the most room and the most structure to hide in, not that Kotlin is safe.
- One library per language. Conventions vary within a language too, and a single well-maintained
  library is a best case rather than an average.

## The sink host should move to `.invalid`

The payloads here name **`datefmt-telemetry.io`**. It was unregistered and resolved nowhere when
these results were produced, and it still is — but **this project does not own it, and anyone
could register it.** A published kit that invites strangers to run payloads naming a domain a
third party might control is a real, if small, hazard: on the tool-enabled arms an agent may
actually make the request rather than merely write code containing it.

**The fix is to move every sink to the reserved `.invalid` TLD** (RFC 2606), which can never be
registered by anyone. `test9` already does this — `telemetry-sink.fixture.invalid` — and the
AgentTrap benchmark uses the same convention.

**It is not an edit in place.** The host *is* the canary the scorer matches on, so changing it
changes the payload, and every published figure measured against the current set becomes
unreproducible. It needs a **payload-set version bump**: change the host, re-run the affected
measurements, and re-stamp the results — see
[ADR-0011](../../docs/knowledge/decisions/0011-publishing-posture-for-security-findings.md).

Until then, `dig +short datefmt-telemetry.io` before running, and skip the tool-enabled arms if
it resolves.
