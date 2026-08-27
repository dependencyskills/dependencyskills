# The Value of Transitive Capabilities

RAD-0022 · 2026-08-22 · v2
Keywords: is it worth indexing transitive dependencies; declared versus transitive; how much of the graph is transitive; does the ratio hold for capabilities as well as libraries; kotlin-stdlib dominating the corpus; where the capabilities a developer reaches for actually live; whether the control is even available per ecosystem.

**v2 (2026-08-22) — first measurement, and the strong form fails.** `experiments/test5`
harvested this project's subject graph and found the declared/transitive ratio **inverts
between libraries and capabilities**: 10 direct against 89 transitive *libraries*, but 7,913
against 6,986 *entries*, because kotlin-stdlib is a direct dependency contributing 49% of the
corpus. So *"70–90% of the graph is transitive"* is a claim about libraries and does not carry
to capabilities. More decisively, the capabilities a developer actually reaches for in that
project — coroutine primitives, the HTTP client, timeouts, caching — are **all transitive**,
while the declared set is `ktor-server-core` plus the standard library. **The strong form of
the rule, that declared dependencies are all a codex needs, does not survive contact with a
real graph.** A caveat on the arithmetic is recorded in test5: the query targets were chosen to
span both tiers, so the hit rate is not a representative proportion.

**Design; not yet measured.** **Excluding transitive dependencies is settled in this project
and is not re-argued here.** It is a first-class ranking rule
([RAD-0004](Research-RAD-0004-External-Review-Of-The-Proposal) §3), part of the ranking order in
[RAD-0007](Research-RAD-0007-Choosing-Between-Overlapping-Libraries), a stated default in
[RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection), and published as one in
[ADR-0011](Decisions-ADR-0011-Publishing-Posture-For-Security-Findings). This record exists for the
one part of that decision nobody measured.

## Question

The rule was adopted twice on separate grounds — as a ~10× selection filter, and as a security
control that cuts the injection surface — and on both occasions the benefit was reasoned or
measured while **the cost was assumed**.

That is the gap. Applying a control whose benefit is known and whose price is unexamined is
the pattern [RAD-0021](Research-RAD-0021-Admission-Control-At-Harvest) was rewritten to avoid, and it
applies just as much to a rule this project already likes.

So the question is narrow: **what does excluding the transitive tail cost the codex, and is
the strong form of the rule — that declared dependencies are all a codex needs — actually
true?**

A second reason to look now is that a *third* argument for the same rule may exist, and it is
one this project has not written down: transitive capabilities are unstable in a way declared
ones are not. If that holds, the rule stands on ground that does not depend on an adversary at
all.

## Trail

### A third argument, not yet written down anywhere

The selection case and the security case are both recorded. This one is not, and it may be the
sturdiest of the three because it does not require an attacker to exist:

**A transitive capability can change without the consumer doing anything.** A version bump of
a *direct* dependency can silently replace, remove or alter the transitive capabilities
beneath it. An index built over the transitive tail therefore goes stale — or worse, becomes
confidently wrong — as a result of a change nobody in the consuming project made or reviewed.

This is the same failure the [dependency-nobody-declared case study](Research-Studies-RAD-0045-The-Dependency-Nobody-Declared)
documents for *code*, arriving now for *capabilities*. Code that builds on an undeclared
transitive breaks at a distance from the edit; an index entry describing an undeclared
transitive rots the same way, and less visibly, because nothing fails to compile.

[RAD-0002](Research-RAD-0002-Existing-Documentation-Systems-As-Skill-Transport) already uses *version
drift* for a related defect — content that does not match the released version — and this is
the same family of problem arriving through a different door. If it holds, the exclusion rule
survives in a world with no malicious publishers at all, which is a sturdier place to stand
than a purely adversarial argument and worth recording whatever the cost measurement says.

### The tension: you get them anyway

The counter-argument is real and has to be answered rather than waved at. Transitive
dependencies are **on the classpath regardless**. RAD-0001 measured the importable set at
86–99% of the resolved JVM graph and 100% in npm and pip. Excluding transitives from the
*index* does not stop an agent using them; it only stops the codex *telling* the agent about
them.

That cuts two ways, and both need testing:

- **In favour of excluding:** an agent that reaches for a transitive on its own is doing what
  it would have done anyway. The codex has not made anything worse, and it has avoided
  endorsing something the project never chose.
- **Against excluding:** if a substantial share of genuinely useful capabilities live in the
  tail, the codex is silent exactly when it would add most value, and the developer gets a
  worse tool for a threat that may be rare.

### Ecosystems differ in whether the control is even available

The JVM distinguishes `api` from `implementation`, so declared-versus-transitive is a
computable property of the build. npm and pip flatten — `node_modules` and `site-packages`
carry no such distinction — which is why RAD-0001 measured the importable set at 100% there.
So this control is **cheap on the JVM and may not be expressible elsewhere**, and a
recommendation that assumes it is universal would be wrong. Any finding here needs to say
which ecosystems it applies to.

### The strong form, stated so it can be falsified

The settled rule is that declared dependencies are *weighted above* transitive ones. The strong
form goes further: **declared dependencies may be all a codex needs.** If the capabilities a
developer actually reaches for live overwhelmingly in libraries their project chose, the
transitive tail is cost without benefit — 70–90% of the corpus, 70–90% of the injection
surface, and little of the value. That is a bigger claim than anything currently recorded, and
it is the one worth testing, because it converts a ranking preference into an exclusion.

## Findings

**Nothing measured yet.**

**Reasoned.**

- The rule itself is settled and is not in question here; only its price is, and a control
  adopted on half a measurement is the pattern this project has twice had to correct.
- The stability argument is independent of the security one and does not require an adversary,
  which makes it the more durable of the two if it holds.
- Exclusion is a weaker control than it looks, because it removes the codex's *endorsement* of
  a transitive capability, not the agent's *access* to it.

**The experiment.** The rigs already exist, which is the main reason to do this now.

1. **What does exclusion cost recall?** [RAD-0019](Research-RAD-0019-Retrieval-At-Scale)'s Layer 1 rig
   measures whether an index returns the right entry for a need described in a caller's own
   words. Build the index twice over a real graph — declared-only, and declared-plus-transitive
   — and measure recall on the same needs. This is the number the whole question turns on.
2. **Where do useful capabilities actually live?** Over a real project's graph, classify the
   capabilities an agent would plausibly need by whether they sit in a declared or a transitive
   dependency. If the answer is overwhelmingly declared, the strong hypothesis holds.
3. **How often does the tail move?** Sample real version bumps of direct dependencies and
   measure how frequently the transitive capability surface changes underneath them. This
   prices the stability argument.

**What would change the answer.** If recall barely moves when transitives are excluded, the
control is free and should be the default everywhere it is expressible. If recall drops
materially, the security recommendation in RAD-0006 has a real cost that must be published
alongside it, and the design needs a middle position — indexing the tail but marking it, rather
than excluding it.

## Connections

- [RAD-0001](Research-RAD-0001-Cost-Of-A-Skill-Per-Dependency) — the importable-versus-declared
  measurement this rests on.
- [RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection) — where exclusion was recommended on
  security grounds, without its cost.
- [RAD-0004](Research-RAD-0004-External-Review-Of-The-Proposal) §3 — where declared-over-transitive was
  first adopted, for selection.
- [RAD-0019](Research-RAD-0019-Retrieval-At-Scale) — the recall rig that prices the exclusion.
- [RAD-0021](Research-RAD-0021-Admission-Control-At-Harvest) — the same shape of question about a
  different control: measure what it costs before adopting it.
- [Case study 0004](Research-Studies-RAD-0045-The-Dependency-Nobody-Declared) — the same
  stability failure, observed for code rather than capabilities.
