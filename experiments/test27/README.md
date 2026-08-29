# test27 — where a known-bad signature list starts hitting real documentation

**Question:** if a list of known injection payloads were used as a cheap gate *before* the classifier, what would it catch and what would it cost?

```
python3 signatures_vs_real_docs.py <path-to>/codex.db
```

**Measured against:** `test9`'s 7 prose and doc-opening payloads, against 11,155 real doc comments harvested by this project from the 59 pinned coordinates of `../test5/CORPUS-MANIFEST.md`; 2026-08-29. AgentTrap's 91 cases are **not** included — that corpus is not vendored here (see `SECURITY.md`), so only payloads this project authored were available.

## Result

Overlap is 4-word shingles of a signature found in a document, so a threshold is "how much of a known payload has to survive rewording to still be caught".

| | flagged, of 11,155 real docs |
|---|---:|
| exact substring | 0 |
| overlap ≥ 50% | 0 |
| overlap ≥ 30% | 0 |
| overlap ≥ 10% | **0** |

The highest overlap any real doc comment reaches against any signature is **0%**. Not "below threshold" — real library documentation and these payloads share no 4-word sequence at all.

## The positive control, which is the half that matters

A null result from a harness that is silently not analysing is not a null result — RAD-0028's lesson.

| | overlap |
|---|---:|
| a signature against itself | 100% |
| planted verbatim in a real doc | 100% |
| the same payload, two phrases reworded | 55% |
| **the same meaning, fully paraphrased** | **0%** |
| a real doc comment, untouched | 0% |

## What it says

**Precision is free.** There is enormous headroom: any threshold between 10% and 50% touches no real documentation. The usual objection to a signature filter — that it will refuse honest prose — does not materialise here.

**Recall is the entire problem.** A paraphrase carrying identical meaning scores the same as unrelated documentation. The mechanism distinguishes *text reuse*, not intent, which is what a virus signature does and why the analogy holds — including at its limit.

**So it catches copy-paste and light editing, and nothing else.** That is worth having and worth being honest about: it is a gate against the unimaginative, not a defence.

**And both this and the classifier work for the same reason** — attack prose and API documentation are different registers. A payload written in the idiom of library documentation would defeat both, and neither mechanism addresses it.
