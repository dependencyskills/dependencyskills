# A Skill Derived From the Source

RAD-0082 · 2026-10-08 · v2 · open

Keywords: the library ships no skill; deriving a skill from a sources jar; an agent following librarian-skill-author on someone else's library; a small local model writing skills; Gemma 4 at a few billion parameters; derived like the summariser; provenance of a derived skill; invention as the first risk; grounding every claim; authored skills as ground truth; computed once per coordinate; where a derived skill is served from; never in the authored slot.

Measured against: stage 1 of the experiment, run 2026-10-08 on five libraries with a frontier agent as writer and as scorer; details under the run. Everything outside that section is cited from the records that measured it.

**v2 (2026-10-08) — stage 1 run.** A frontier agent, given only the published source, wrote grounded skills: 913 claims, none unsupported or contradicted, 26 overstated. It also found traps and defects the authored skills lack, and missed what the source cannot hold — intent and history. The stopping rule was not met.

## Question

Most libraries will never ship a skill. Our own four do now, written by an agent following [`librarian-skill-author`](../../../implementations/agent-skills/librarian-skill-author/) with each library's maintainer reviewing the result. **Can the same skill, or one of its sections, be derived from a library's published source by someone other than its authors — a frontier agent, or a small local model — and served, marked as derived, for the libraries that have none? And is it good enough to be worth serving?**

## Trail

### Why this is not the republishing this project rejected

[RAD-0076](RAD-0076-skills-republished-by-a-third-party.md) rejected a scheme that ships other projects' skills inside its own artifacts, and the lookup refuses any skill filed under a library's name by an artifact that is not that library. The objection was provenance: a third party's text served as the answer to "what is this library's skill" asserts an authorship that is false, and a banner annotates that falsehood without repairing it.

A derived skill is closer to the codex's summariser than to that scheme. The summariser already writes text about a library that the library's authors never wrote — one factual sentence per declaration, computed on the developer's side from the library's own source, served as the codex's rewrite and never as the library's words ([RAD-0025](RAD-0025-the-summariser-as-attack-surface.md)). A derived skill is the same move at a different grain: derived from the published source of the exact version resolved, computed by our tooling rather than shipped by a stranger, and served as derived. What RAD-0076 requires is kept: **it never occupies the authored slot.** Asking for a library's skill returns its authors' skill or nothing; a derived one is a separate answer, labelled with what wrote it, from what, and when.

### Why it is harder than the summariser

The summariser's output is narrow by construction: one sentence, describing one declaration, checkable against that declaration. A skill is long, imperative, and addressed to the agent — "use X, never Y" — and it carries its weight in exactly the sections that are hardest to ground: what a library is not for, what goes wrong, what moved.

[RAD-0074](RAD-0074-a-skill-built-from-the-documentation.md) worked through this for a skill built from harvested documentation, and its central finding carries over unchanged: **the danger is invention, not omission.** A derived skill that leaves out a trap is no worse than the source it came from; one that invents a trap is worse than nothing, because it arrives with a skill's authority. RAD-0074 parked synthesis before measuring it, on sequencing rather than verdict — delivery had to be settled before transformation. Delivery is now settled for authored skills, which is what reopens the question.

### What the trial already shows about written skills

The four libraries' skills were written by a frontier agent with every advantage a derived skill lacks: the repository and its history, the maintainer to ask, and a review afterwards. Even so, a review of fourteen of them found five wrong claims — a "nothing throws" that was false on two targets, a stated default that was wrong, stale version references, and two sentences telling the reader to add a dependency — and a missing warning that an exception crossing into Swift ends the process. Checking each claim against every source set later found more in one of them: a platform behaviour that had changed, and a JavaScript surface claimed that the library does not export (the lightweight-codex trial, 2026-09-30). **The baseline for a derived skill is therefore not "correct": it is "about as wrong as an author's first draft, without the author to catch it."**

The same trial measured what a description is worth: rewriting fourteen descriptions so that every capability is named as a task someone would search for moved blind needs found first from 57% to 65%, and in the top three from 82% to 91%. A description is the section a derived skill is most likely to get right — it is a summary of what the source exposes — and the one that matters most to search.

### Who writes it

**A frontier agent following `librarian-skill-author`,** given only what a consumer's machine has: the sources jar of the resolved version, and its POM or module metadata. No repository history, so the "what moved" section has only deprecation annotations and the version's own changelog if it ships one; no maintainer, so step 1's "draft it, show it to them" has no one to show. This is the ceiling: if it cannot write a grounded skill, nothing smaller will.

**A small local model,** around four billion parameters — Gemma 4 at its smaller sizes is the obvious candidate, run locally (MLX first on this machine). It cannot follow an agentic, many-file skill the way a frontier agent does, so it needs the work cut down: one package or one public type at a time, with a constrained output per piece and the assembly done by code. That is the summariser's shape again, and its runtime question was already measured — in-process generation took 25 minutes against 21 hours for one dependency graph ([RAD-0051](RAD-0051-a-jvm-generative-runtime.md)). If the small model is good enough at any section, the cost is per library version, once per machine — or once anywhere, since a derived skill of a public coordinate is the same object on every machine ([RAD-0052](RAD-0052-distributing-a-precomputed-codex.md)).

### What a derived skill would be allowed to say

Not everything a skill holds can be derived honestly. A cut by how checkable each section is:

| Section | Source | Groundable? |
|---|---|---|
| description — what it does, as searchable tasks | public declarations, their doc comments | yes: each capability names the declarations it rests on |
| how it is meant to be used | doc comments, samples shipped in the jar | partly: an example is checkable by compiling it |
| what it is not for | rarely stated anywhere in source | mostly no — the invention risk |
| what goes wrong | `@Throws`, thrown exceptions, `require`/`check` calls, platform `actual`s that differ | partly: the code states some traps outright |
| what moved | deprecation annotations and `ReplaceWith`, a symbol diff against an earlier version | yes, and with no model at all |
| Swift / JavaScript surface | `@Throws`, `@JsExport`, `@ObjCName`, `internal` visibility | yes: annotations, not prose |

That suggests a derived skill narrower than an authored one by design: the grounded rows only, every claim citing the declaration it rests on, and the ungroundable sections left out rather than guessed.

### Attack surface

The generator reads attacker-controlled prose — doc comments are the library publisher's text — and writes text an agent will act on. That is RAD-0025's summariser problem with a larger output: a skill is free-form and imperative where a summary sentence is short and descriptive, so an instruction carried through is more at home in it. The classifier that already screens harvested prose would screen the input; whether a derived skill also needs its output screened, or should be restricted to the declarative rows above, is part of the experiment.

### Stage 1, run: a frontier agent, five libraries

**Measured against:** 2026-10-08. Writers and scorers were the same frontier model, each a fresh agent with no memory of the others. Inputs were the published sources jars of `io.github.aughtone:types` 4.1.0, `io.github.aughtone:format-readable` 4.1.0, `io.github.aughtone:charts` 0.0.3 and `io.github.aughtone.normalize:common` 0.0.5 — every platform's jar merged, the shipped skill removed — and the npm package `dotenv` 17.4.2 with its `skills/` removed. The writer had the source and [`librarian-skill-author`](../../../implementations/agent-skills/librarian-skill-author/) and nothing else; every claim had to cite a source line. The scorer checked every claim against the cited line and the code around it, traced each trap by hand, and compared the result with the skill the library's own authors ship. For `dotenv` the scorer also ran the package. The derived skills and score sheets are in [`experiments/derived-skills/`](../../../experiments/derived-skills/).

| Library | Claims | Supported (ok) | Overstated (harm) | Cannot tell | Unsupported (harm) | Contradicted (harm) |
|---|---|---|---|---|---|---|
| types | 350 | 338 | 11 | 1 | 0 | 0 |
| format-readable | 165 | 161 | 1 | 3 | 0 | 0 |
| charts | 143 | 133 | 7 | 3 | 0 | 0 |
| normalize-common | 124 | 123 | 1 | 0 | 0 | 0 |
| dotenv | 131 | 125 | 6 | 0 | 0 | 0 |
| **all five** | **913** | **880** | **26** | **7** | **0** | **0** |

*Supported* counts a cited claim the source bears out and an uncited inference the source confirms. *Overstated* is a claim the source supports in part and that says more, or more generally, than the source shows. *Cannot tell* rests on platform or language behaviour the source does not show. One overstated claim, in charts, contains a sentence that is false: it says the default labels do not use the library's rounding, and two of them do.

**Against the stopping rule set beforehand:** 3.6% of claims were not fully supported, against a limit of one in ten; no trap or boundary was contradicted in any library, against a limit of one library. Stage 1 does not stop the approach.

**Traps and boundaries**, the sections that matter most: 136 across the five skills, 116 supported in full, the other 20 holding in substance with one overstated or undecidable part, none contradicted.

**Against the authored skills.** Their 92 traps and boundaries: 67 found, 4 partly, 21 missed. The misses are almost all what the source cannot hold — what the authors meant the library not to be for, advice about handling secrets, how a framework is packaged for Swift. History is the clearest gap: of 23 version-history facts in one authored skill the derived one recovered 1, and of 7 in another, 3. In the other direction the derived skills state 72 traps the authored ones lack, none contradicted and most confirmed outright. Where the two disagreed on something the source could settle, it supported the derived skill 13 times and the authored one twice.

**What it found besides.** Reading the whole source to cite it turned up things nobody had asked for: defects in the libraries — a number formatter that puts a grouping comma after a minus sign, a pie chart that draws one degree with animation disabled, a duration that prints "0 seconds" for ninety — doc comments that contradict the code beside them, and claims in the authored skills that the source does not bear out, including a remedy that leads straight into one of those defects. These are hand traces, not runs, except in dotenv.

**What it cost.** Five to twelve minutes and 150–330 thousand tokens to write one skill, and about as much again to score it.

**Run afterwards, for one library.** On 2026-10-10 the agent maintaining format-readable ran every reported claim on the JVM against the 4.1.0 code. All reproduced, the three scored *cannot tell* included — ten million formats as "1", `NaN` throws, and the two declarations are ambiguous — and running turned up four more defects of the same kind. So for that library the hand traces held, and the same-model scoring did not hide an error.

**What this does not show.** The scorer is the same model as the writer, so a shared blind spot scores as agreement; a different model, or running the code, would be a stronger check. Traps were traced by hand in the Kotlin libraries, not run, except format-readable's afterwards. Two of the five writers saw that an authored skill existed — a file name in a listing, never its contents — before the layout was fixed. And five libraries by one publisher and one npm package are a small, friendly sample: clean source, doc comments, no hostile prose.

## Findings

Measured here, 2026-10-08 (frontier agent, five libraries, same-model scorer):

- From published source alone, 880 of 913 claims were supported, 26 overstated, 7 undecidable from the source, none unsupported or contradicted.
- No trap or boundary was contradicted in any of the five; 116 of 136 were supported in full.
- Against the authored skills' 92 traps and boundaries: 67 found, 4 partly, 21 missed, the misses being intent, advice and packaging; version history was largely unrecoverable from the source.
- The derived skills added 72 traps the authored ones lack, none contradicted; on 15 decidable disagreements the source supported the derived skill 13 times.
- Writing one skill took 5–12 minutes and 150–330 thousand tokens.

Measured (cited):

- Agent-written skills with full repository context and a maintainer contained five wrong claims across fourteen skills on review, and more on a per-source-set recheck (trial, 2026-09-30).
- Descriptions rewritten to name every capability as a searchable task moved findability from 57% to 65% first and 82% to 91% in the top three over ninety blind needs (trial, 2026-09-30).
- In-process local generation over one dependency graph took 25 minutes against 21 hours ([RAD-0051](RAD-0051-a-jvm-generative-runtime.md)).

Argued, not measured:

- Invention is the first risk, and the sections an agent relies on most are the hardest to ground ([RAD-0074](RAD-0074-a-skill-built-from-the-documentation.md)).
- A derived skill is provenance-honest only if it never answers for the authored one, and is labelled with what derived it, from which coordinate and version.
- A small model cannot follow the authoring skill as an agent does; it needs the work cut into per-package pieces with constrained output.

Not known: whether any small model grounds its claims well enough to serve; whether a scorer of a different model, or running the code, would find what this one passed; whether the result holds for libraries with poor or hostile documentation; whether a derived skill changes what an agent writes; whether the description alone — the most groundable section — carries most of the value.

## Recommendation

**Not a commitment.** Worth an experiment, in stages, each able to stop the next.

1. **The ceiling, on libraries with ground truth — run, and it holds.** See the run above. Before leaning on it: score again with a different model or by running the code, and try libraries that are not ours.

   As framed: A frontier agent derives skills for our own four libraries and a few npm packages that ship authored skills, from the sources jar or package alone — no repository, no maintainer. Compare each against the authored skill and its review: claims grounded, claims invented, traps found, traps missed.
2. **Score grounding first, as RAD-0074 set out.** Every claim cites a declaration; count the claims the citation does not support. Decide the stopping threshold before running — an invented trap is the failure that matters, whatever else reads well. **Set before the first run, 2026-10-08:** a writer stops if more than one in ten of its factual claims is not supported by the source, or if it states a trap or a boundary the source contradicts in more than one of the five libraries tried.
3. **The small model on the same libraries,** cut per package, restricted to the groundable rows. Measure grounding, then cost per library version.
4. **Findability,** with the existing harness: blind needs against derived descriptions, beside the authored ones.
5. **Uptake last,** and only if 1–4 hold: does a derived skill, served as derived, change what an agent writes — the test-5 shape.

Where it would be served from is the codex, beside the summariser's output and under the same labelling, never through the lookup's authored path. That keeps the lighter half exactly as it is: the authors' own text, for libraries the project chose.

**What would change the answer.** A high invention rate at stage 1 ends it: if a frontier agent cannot ground a skill from the source, a small model will not. Grounded skills that add nothing beyond what the doc comments already say end it too — then the codex's existing harvest is the middle rung and no model is needed. And a library's authors shipping their own skill always wins: a derived skill is for the gap, and should be dropped the version an authored one appears.

## Related

- [RAD-0074](RAD-0074-a-skill-built-from-the-documentation.md) — synthesis from documentation, parked; invention first.
- [RAD-0076](RAD-0076-skills-republished-by-a-third-party.md) — why a third party's skill never answers for the library's.
- [RAD-0025](RAD-0025-the-summariser-as-attack-surface.md) — the summariser reads attacker-controlled text and writes durable content.
- [RAD-0051](RAD-0051-a-jvm-generative-runtime.md), [RAD-0052](RAD-0052-distributing-a-precomputed-codex.md) — what runs a local model, and computing once per public coordinate.
- [RAD-0081](RAD-0081-tools-carried-in-the-librarian-skill.md) — how the lighter half reaches an agent.
