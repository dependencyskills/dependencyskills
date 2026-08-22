# Spike: what does a shipped skill actually cost an agent?

**Question.** If every dependency in a real project shipped an agent skill,
how much context would be spent before any work began?

Everyone proposing a convention answers where the file goes. Nobody has
published this number. The nearest thing to an answer in the field is
"be selective — only add skills for packages your agent struggles with",
which concedes that the budget is unmanaged without measuring it.

The number matters because the whole index design rests on it. If a real
project has forty dependencies, the index is over-engineering. If it has
four hundred, every directory-scan convention in circulation has a ceiling
nobody has mentioned.

## Method

Three steps, each separately checkable.

1. **Count dependencies in real projects.** Gradle knows the resolved graph
   exactly, so this part is measured rather than estimated. KMP makes it
   more interesting: the count differs per target, and what an agent sees
   is the union.
2. **Measure what a skill description actually costs.** Not the spec's
   1024-character limit — real descriptions from real skills. Sample: real
   published skill suites, v1 skill files, anything else to hand.
3. **Multiply, and say plainly which parts are measured.**

## Subjects

Deliberately spanning the range rather than picking a flattering one.

| Project | Why |
|---|---|
| `aughtone-types` | A small KMP library. Bottom of the scale. |
| private-1 | An application, early. Few dependencies by choice, not by size. |
| private-2 | An application, large and mature. Top of the scale. |
| other `aughtone-*` libraries | Fills in between, all KMP. |

All four are one author's work in one ecosystem, which is not a sample. To
say anything about *typical* dependency counts, the set needs public
projects from ecosystems where the directory conventions already exist —
the ones whose proposals this cost applies to. See `subjects.md`.

## What would make the answer wrong

Written before running anything, so the result cannot quietly redefine the
question.

- **Tokens are not characters.** There is no public tokeniser for the model
  this matters most for, so the scripts report characters and convert with
  a stated ratio. A cost model that pretends to token-level precision is
  less credible than one that shows its assumption.
- **Not every dependency would ship a skill**, and probably very few will
  for years. The honest framing is a ceiling: this is what it costs if the
  convention succeeds, which is the case its designers should be planning
  for.
- **The direct/transitive line is genuinely grey.** A project declares a
  handful of dependencies and resolves hundreds, but plenty of transitive
  ones are called directly by your own code — and those are exactly the ones
  you would want a skill for. The graph cannot tell you which. So both ends
  are recorded and neither is presented as *the* number: direct is the
  floor, all resolved is the ceiling, and the honest claim is a range.
  Measuring the middle needs an import scan, which is approximate and not
  done yet.
- **Descriptions from one author are not a sample.** The suite measured
  here was written by one person with one house style. Treat the
  distribution as indicative, and say so.

## Result

See [findings.md](findings.md). Short version: a description costs about
110 tokens, and a large application's *direct* dependencies alone come to
around 41,000 — more than the figure this design was built around. The
ceiling is an order of magnitude worse and exceeds most context windows
outright.

## Status

Spike. No tests, no error handling beyond what makes it run. When the
question is answered the finding goes to `docs/` and this directory can go.
