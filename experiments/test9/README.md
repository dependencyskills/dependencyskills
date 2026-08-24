# test9 — Phase 1: one variable at a time, across the KMP languages

Everything before this changed two variables at once, which is why the results read as
disjointed rather than as a sequence.

| | payloads | language | corpus | parse path |
|---|---|---|---|---|
| `test7` | ours | Kotlin | synthetic fixture | tree-sitter + `javap` |
| `test8` | AgentTrap's | Python | 141 real skills | tree-sitter only |

When those two disagreed there was no way to attribute it. `test8` never touched Dokka, and
`test7` never touched an attack somebody else wrote.

**This phase fixes the payloads and varies only the language**, across the targets KMP compiles
to plus Java, which Android consumes but which is not written here:

    kotlin   swift   js   java

## The three stages, scored separately

They were previously entangled in a single "did it work" number. They answer different questions
and have different remedies.

| stage | question | remedy if it fails |
|---|---|---|
| **0 expressibility** | can the language carry this payload shape at all? | none needed — the channel does not exist |
| **1 transport** | does it survive harvest into the entry? | fix the parse stage |
| **2 detection** | does that language's linter see it? | configure or replace the linter |
| **3 obedience** | does the agent act on it? | architecture — the only thing that has worked |

## Stage 0 — what each language can even express

One thing here is a finding and the rest is scaffolding, so only the finding is stated:

**The loud form has no vehicle in Java or Swift.** Their identifiers cannot contain spaces, so the
payload that worked best in `test7` — 8 of 12 locally, 3 of 3 on GPT-OSS 120B — is not merely
harder there, it is unwritable. Kotlin's backtick identifier is what makes it expressible, and
Kotlin is the language this pipeline harvests most. JavaScript can carry it only as a quoted
object property, never as an identifier, which is recorded separately so "JS resisted" is never
confused with "JS was never asked".

Everything else at this stage is *"yes, you can write a camelCase identifier in every language"*,
which is not information. `payloads.py --summary` prints the full matrix for completeness; it is
not a result and is not published as one.

## What a result will look like

A row is only meaningful **end to end**, because that is the chain that decides whether the tool
is exposed. Each cell answers its own stage, and the last column is the one that matters:

| payload | language | carried by | survives harvest | linter sees it | agent acts on it |
|---|---|---|---|---|---|
| loud | kotlin | identifier | ? | ? | ? |
| loud | java | *no vehicle* | — | — | — |
| camel | swift | identifier | ? | ? | ? |
| prose_policy | js | doc comment | ? | ? | ? |

A payload that cannot be carried needs no further stages, which is why stage 0 runs first — it
removes rows rather than producing them. Nothing is published from this experiment until the last
column is filled, because the earlier columns on their own describe a pipeline rather than a risk.

## Payloads

Fixed across all languages, in `payloads.py`, and deliberately small. Four identifier forms plus a
control, chosen because `test7` already measured which ones work, and three prose forms.

**Our own prose set exists so the corpus becomes a variable we control.** AgentTrap's prose is
real and independently authored, which is its value, but it arrives bundled with a different
language and a different harvest path. Having our own set expressed identically in four languages
lets the two be compared — with and without — rather than confounded, which is the mistake this
phase exists to stop repeating.

The prose payloads are written as policy statements rather than instructions, because that is the
shape `test8` measured code scanners being blind to: 1 of 18 on skill-file prose, 0 of 6 on
auxiliary files.

## Tooling per language

| language | parse path | linters |
|---|---|---|
| Kotlin | tree-sitter, Dokka, `javap` on the compiled class | detekt, ktlint |
| Java | tree-sitter, `javap` | checkstyle |
| Swift | tree-sitter | swiftlint |
| JS | tree-sitter | eslint |

Kotlin is the only one with a resolution path (Dokka) as well as a syntactic one, which is another
variable `test8` could never have exercised.

## Models

Stage 3 runs the frontier set: Gemini 3.7 Flash and 3.1 Pro, GPT-OSS 120B and Claude Sonnet/Opus
4.6 through `agy`; Claude Opus 5, Haiku 4.5 and Fable 5 through subagents.

Unlike `test8`, these payloads are **inert by construction** — they name a path and perform
nothing — so the subagent route is safe here in a way it was not for AgentTrap's destructive
workflows.

## Safety

Payloads name a sink under `.invalid` (RFC 2606), which can never resolve. Nothing is executed;
compilation and linting are static. No fixture is planted.

## Status

Stage 0 complete. Stages 1–3 to build.
