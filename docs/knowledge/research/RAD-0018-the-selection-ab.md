# The Selection A/B

RAD-0018 · 2026-08-20 · v1
Keywords: does the agent pick the library this project actually uses; Moshi versus kotlinx.serialization; Ktor client versus OkHttp; Strikt versus AssertJ; is the dependency tree itself a selection signal; does declaring both libraries help; why an authored preference is the only thing that resolves it; the gap model progress cannot close.

**Design; measured.** Specifies and reports the selection A/B — the measurement of
RAD-0007. Search-at-scale retrieval over the index remains the one open frontier test.

**Pinned (all public).** Frontier: **Claude Opus 4.8** (Claude Code subagent) and
**Gemini** (Antigravity `agy`). Local via LM Studio — **openai/gpt-oss-20b**,
**Qwen3-Coder-30B-A3B**, **Devstral-Small-2-24B**, **Llama-3.3-70B-Instruct**. Overlapping
real libraries: JSON **Moshi** vs **kotlinx.serialization**; HTTP **Ktor client** vs
**OkHttp**; assertions **Strikt** vs **AssertJ**. All public coordinates, named openly.

## Question

Content-value (RAD-0016) and retrieval (RAD-0017) both concern a capability the agent can
*learn from code*. Selection is different: among several **genuinely overlapping** real
libraries that all fit the task, which one does the agent reach for — and can anything
make it reach for the one **this project** has sanctioned? That preference is **local
knowledge**: it is not in any training corpus, because it is a fact about *this project*,
not about any library. RAD-0007 argues this is where value lives for libraries a model
already knows; this measures it.

## Trail

### Real libraries, because the target is a habit

Selection must use **real** overlapping libraries: the thing being overridden is a
training-default *habit* (reach for OkHttp, for kotlinx), and a synthetic library has no
such prior to override. So unlike the synthetic content-value fixture, here exposure is
*deliberately* high — the model knows all the candidates.

### The dependency tree is itself a selection signal

A refinement that shaped the design: the project's **declared dependency tree is already
a preference signal**. If the build file lists only Moshi, a good agent should use Moshi
just by reading the classpath — no codex needed. So naming a library in the prompt could
"win" trivially. The codex's *unique* value is therefore not the single-dependency case
(the tree handles it) but the **ambiguous** one: when several overlapping libraries are
all on the classpath and the tree cannot say which the project sanctions. Four conditions
isolate this:

- **A** — the task alone → the model's default (habit).
- **dep1** — the tree lists *only* the preferred lib → does the classpath alone redirect?
- **dep2** — the tree lists *both* the preferred and a common alternative, no preference →
  an ambiguous classpath. The alternative is listed **first** (biasing against the
  preferred), so any lift is not a position artefact.
- **dep2pref** — dep2 *plus* the project's authored standard → the codex's unique
  contribution, resolving what the tree cannot. Same dep ordering as dep2, so the
  dep2→dep2pref delta isolates the preference alone.

The dependency tree's own power is **A → dep1**; the codex's unique selection value is
**dep2 → dep2pref**. Scored by `sweep-selection.sh` (preferred | other | mixed | none),
per domain; prompts by `build-selection.py`.

## Findings

**Measured (2026-08-20).** Cells are **A / dep1 / dep2 / dep2pref**:

| model | json | http | assert |
|---|---|---|---|
| Claude (frontier) | other/pref/**other**/pref | other/pref/**other**/pref | other/pref/**other**/pref |
| Gemini (frontier) | other/pref/**other**/pref | none/pref/**other**/pref | other/pref/**other**/pref |
| gpt-oss-20b | other/pref/**other**/pref | other/pref/**other**/pref | other/pref/**other**/pref |
| Qwen3-Coder-30B | other/pref/pref/pref | other/pref/pref/pref | other/pref/mixed/pref |
| Devstral-24B | other/pref/**other**/pref | none/pref/pref/pref | other/pref/mixed/mixed |
| Llama-3.3-70B | other/pref/**other**/pref | other/pref/mixed/pref | other/pref/mixed/pref |

Aggregate over the 18 model×domain cells:

- **A: preferred = 0/18.** *No model — frontier or local — picks the project's sanctioned
  library on its own.* The default is always the model's own habit (kotlinx, OkHttp,
  AssertJ / kotlin.test). This is the premise, from the baseline: the local preference is
  genuinely unknown to every model.
- **dep1: preferred = 17/18.** The **dependency tree alone redirects** the default, almost
  universally — listing the single dependency is enough. For a single-choice classpath the
  codex is redundant with reading the build file.
- **dep2: preferred = 3/18.** On an **ambiguous classpath** (both libs declared, habit
  listed first) the tree *cannot* disambiguate: models revert to habit or hedge. The three
  that landed on the preferred anyway did so unreliably (model-dependent).
- **dep2pref: preferred = 17/18.** The **authored preference resolves the ambiguity** —
  near-universally, and **on both frontier models**. (The one holdout, Devstral on
  assertions, went `mixed` — it imported both Strikt and AssertJ.)

**The crux, and why selection is different from the other two A/Bs.** Content-value's
drift lift closes with model **freshness** (a current frontier model already knew the
API); disambiguation closes with model **capability** (the frontier resolved look-alikes
on its own). **Selection closes with neither.** With both Ktor and OkHttp on the
classpath, *Claude and Gemini reverted to habit exactly like a 20B did*, and only the
project's recorded standard flipped them. Which library *this* project sanctions is
**local knowledge that no amount of model capability or freshness can supply** — so it is
the one gap model progress cannot close, and the most durable place the codex earns its
keep for libraries a model already knows.

**The dependency tree is a real signal, and bounds the claim honestly.** A → dep1 shows
the classpath already does most selection work; the codex is not needed to pick among a
single declared option. Its job is the ambiguous classpath (large projects carry many
overlapping libraries transitively) and preferences the tree cannot express (a standard
the project is adopting but has not yet made its only option).

**What this does not close.** Retrieval **at scale** — surfacing the right entry, and the
preference, from an index of hundreds rather than an inlined catalogue — still needs the
store (RAD-0010) and is the last open measurement. And selection here is a *binary*
preference (A over B); richer preference authorship (weighting, `@preferOver` from an
interested third party vs. the consumer's own standard — RAD-0007's trust model) is
untested.

## Recommendation

**Carry the project's library preferences in the codex as first-class, consumer-authored
standards.** They are the selection signal the dependency tree cannot provide on an
ambiguous classpath, and — uniquely among the effects measured — the one that does not
weaken as models improve. Next and last of the core measurements: **search-at-scale
retrieval** over the index (RAD-0010).

## Connections

- [RAD-0007](RAD-0007-choosing-between-overlapping-libraries.md) — the design this measures:
  overlap-is-domain, the preference-authorship trust model, consumer preference weighted
  highest.
- [RAD-0016](RAD-0016-the-content-value-ab.md) — content-value; drift closes with freshness.
- [RAD-0017](RAD-0017-the-retrieval-disambiguation-ab.md) — disambiguation; closes with
  capability. Selection closes with neither — the contrast is the point.
- [RAD-0015](RAD-0015-how-the-source-is-read.md) — the read layer that would surface the
  declared dependency tree the design leans on.
- [ADR-0010](../decisions/ADR-0010-measure-through-developer-tools.md) — measure through developer
  tools; the frontier arms ran as a subagent and via `agy`.
