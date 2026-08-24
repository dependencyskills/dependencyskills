# Experiments

Code written to find something out, not to ship. Spikes, prototypes and
proofs of concept all land here — the difference between them is only
whether anyone means to keep it.

**Inside this directory the usual standards are deliberately off.** No
acceptance criteria, no test coverage bar, no architectural conformance. A
proof of concept held to production standards stops being cheap, which
removes the only reason to write one.

**Nothing leaves by being copied.** Code moves out as work written against
the project's standards, with the proof of concept as a reference rather
than a source.

Each one gets a directory and a README opening with the question it exists
to answer. When the question is answered, the finding is written up in
`docs/` and the code can go.

## The series

These are ordered by **the decision each one informs**, not by when it ran. A test that does not
change a decision about the tool is not worth running; several below are listed with what they
failed to settle, which is the same information.

### 1 · Does the tool need to exist?

| | question | decision it produced |
|---|---|---|
| [cost-model](cost-model/) | what does one skill per dependency cost across real graphs? | **yes** — 20k–139k tokens resident, no cheap floor. An index is required, not optional |

### 2 · Where does the content come from?

| | question | decision it produced |
|---|---|---|
| [test1](test1/) | can five languages be read from real `-sources.jar`? | **tree-sitter per ecosystem**; the enrichment lever is the graph, not the parser |
| [test2](test2/) | can bytecode carry the undocumented tail? | **yes, degraded** — kept as a fallback for source-less libraries |
| [test5](test5/) | does a codex built from a *real* harvest retrieve? | **summarise is load-bearing** — raw doc text is 0/17 at real scale |

### 3 · What goes into the index, and what is left out?

Every row here is a **surface reduction**, and this is the category with an unbroken record.

| | question | decision it produced |
|---|---|---|
| [test5](test5/) | how much of a real harvest is duplicate? | **63%** — dedup is a required harvest step |
| [test5](test5/) | what does the transitive tail cost and buy? | declared-only drops **2,123 publishers** across 13 real projects |
| [test5](test5/) | how much of a real corpus is unusable? | **297 entries** deprecated to `HIDDEN`/`ERROR` — exclude them |

### 4 · Does any of it change what an agent does?

| | question | decision it produced |
|---|---|---|
| [test0](test0/) | does a codex from graded docs change behaviour? | yes — and a bare signature is enough to *use* a capability (7 of 8) |
| [test0/measurement](test0/measurement/) | content value, disambiguation, selection, retrieval at scale | agents pick the right library **0 of 18** unaided; winning retrieval ≈ winning the decision |

### 5 · Can it be attacked, and through what?

| | question | decision it produced |
|---|---|---|
| [test4](test4/) | does the harvest path filter anything? | **no** — 5 of 5 languages deliver payloads intact |
| [test6](test6/) | can the summariser be attacked? | yes — and a fabricated library beats the true answer 4 of 17 |
| [test7](test7/) | is the *identifier* a free-text channel? | **yes** — and agents act on it, 8 of 12 on the loud form |
| [test9](test9/) | does the answer differ by language? | **yes** — every prose payload that landed, landed in JavaScript; Swift is undefended but unexposed |

### 6 · What actually defends it?

| | question | decision it produced |
|---|---|---|
| [test3](test3/) | can docs be grounded against the shipping library? | **no** — withdrawn, RAD-0021 |
| [test7](test7/) | does enforcing at the sink work? | harm 0 of 3, **task also 0 of 3** — granularity is a requirement |
| [test7](test7/) | do cheaper controls work? | **quarantined paraphrase** and **signature-only** each prevented harm and kept the task |
| [test8](test8/) | does linting work on attacks we did not write? | **no** — 16 points of separation, misses 62 of 91 |
| [test8](test8/) | do detectors compose? | **no** — union adds 0, consensus is *worse*; they are nested, not independent |
| [test10](test10/) | does *configuring* the linters close what their defaults miss? | **on identifiers yes** — the whole catalogue costs 0.221% of a real corpus; on prose, nothing |

## The through-line

**Surface reduction has worked every time it was measured. Detection has failed every time.**

Sections 3 and 6 are the same shape of intervention and land on opposite sides of that line, and
nothing measured so far contradicts it. The controls carried forward into the tool are therefore
the ones that remove a channel — declared-only, dedup, deprecation exclusion, quarantined
paraphrase, signature-only display — and not the ones that try to recognise an attack.

## What is not settled

- ~~Whether language changes the answer.~~ **Answered by `test9`:** JavaScript is the exposed
  language and prose is how it is reached. Swift carries every identifier payload with no linter
  objecting, and yet nothing landed there — *undefended* and *exposed* are different properties.
- **Whether the surviving controls compose.** Each was measured alone. Sequential filtering and
  parallel agreement are untested, and the detector result above says composition cannot be
  assumed.
- ~~Whether the linters' *defaults* were the problem.~~ **Answered by `test10`:** custom rules
  close the identifier channel for about a fifth of a percent of real content, and leave prose
  untouched. The remaining identifier gap is a policy dial with a published cost curve, not a
  missing technique.
- **Agent-laundered first-party content** (RAD-0029) — measured once, untested since, and inside
  the boundary declared-only indexing draws.

## Standards note

An experiment may carry a runnable harness — the tests *are* the experiment; they do not belong
in `implementations/`, which is working code. A runnable experiment is added to the public site
once its harness works; a scaffold-only test is not listed there.
