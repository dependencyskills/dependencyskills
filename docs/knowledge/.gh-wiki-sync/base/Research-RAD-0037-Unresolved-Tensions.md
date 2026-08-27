# Unresolved Tensions in the Design

RAD-0037 · 2026-08-25 · v1
Keywords: where do our own findings contradict each other; unresolved tensions; retrieval needs what security cannot sanitise; excluding the transitive tail reintroduces the problem; the users who gain most are the most exposed; the summariser answers instructions but not fabrications; contradictions an ADR cannot carry.

**Opened because the contradictions were distributed.** Thirty-six research records and a dozen
experiments each settle a question locally, and several of those local answers pull against each
other. Read one at a time they are all sound; read together, some of them are in conflict.

This record collects the conflicts. It resolves none of them, which is the point — RADs are where
undecided things are allowed to live, and an ADR cannot carry a contradiction.

**Source.** Most of these were surfaced by an external structured review of the corpus by a
frontier model (Gemini), asked to find tensions rather than to summarise. Several are restatements
of things this project already knew separately; the value was in putting them side by side. Two
were not known, and are marked.

## Question

> **Where do this project's own findings contradict each other, and what does each contradiction
> cost?**

## Trail

### 1. Retrieval needs the thing security cannot sanitise

Vector recall depends on **rich natural-language capability prose in the caller's vocabulary**:
77% recall@1 against 29% for raw doc text and effectively 0% for signatures alone
([RAD-0019](Research-RAD-0019-Retrieval-At-Scale), `test5`). Natural-language prose is also the one channel
no control has closed — `test13` and `test14` priced both structural candidates and rejected both,
and form constraints reach identifiers only ([RAD-0033](Research-RAD-0033-Do-Form-Constraints-Compose)).

**The retrieval system and the threat model are contesting the same field.**

The summariser is this project's answer, and it is a good one for the *instruction* case: it
rewrites rather than filters, so a payload it fails to notice is still discarded
([RAD-0024](Research-RAD-0024-Does-The-Pipeline-Filter-Injection), and the built component neutralised 3 of 3
injected docs).

**But it does not answer the fabrication case, and that gap is not covered anywhere.** A malicious
library can author *honest-looking, non-imperative* capability prose that is simply false, and
compete for retrieval on merit. `test6` measured a fabricated library beating the true answer **4 of
17**. A rewriter has no defence here: there is nothing malformed to discard, and the summariser will
faithfully rewrite a lie into a well-formed capability description.

**Not previously collected.** The summariser was carried as the answer to the prose channel; it is
the answer to one half of it.

### 2. Excluding the transitive tail reintroduces the problem the project exists to solve

Declared-only indexing is the strongest measured control — 2,123 publishers dropped across 13 real
projects, roughly a tenfold cut in ingested prose
([RAD-0004](Research-RAD-0004-External-Review-Of-The-Proposal), [RAD-0022](Research-RAD-0022-The-Value-Of-Transitive-Capabilities)).

But **86–99% of the importable JVM surface is transitive** (102 declared against 311 importable in
one real project; 16 against 112 in another), and 100% in flat ecosystems like npm and pip
([RAD-0001](Research-RAD-0001-Cost-Of-A-Skill-Per-Dependency)). Excluding it hides most of what an agent can
actually call — so the agent hand-rolls a `Mutex`, a Jackson module, a commons helper. **That is
the reinvention failure the project was founded on** ([postmortems/v1](Research-Postmortems-RAD-0046-V1-Bundled-Flat-Files)).

Admit the tail and the untrusted surface grows tenfold. Exclude it and the product's core value
shrinks by the same order. Neither branch has been costed against the other.

### 3. The most durable value depends on the practice the project rejected

The move from v1 to v3 was justified by **human curation not scaling**
([ADR-0004](Decisions-ADR-0004-Librarian-And-Codex)). Yet **selection** — choosing between overlapping
libraries on an ambiguous classpath — is identified as the one value model progress cannot erode,
and it **depends entirely on hand-authored local preference**
([RAD-0018](Research-RAD-0018-The-Selection-Ab)). Unaided, models resolve ambiguity 3 of 18.

The project automated the third-party documentation burden and moved a curation burden to the
consumer's own repository. That may be the right trade — a preference file is small and changes
rarely, where a skill per dependency is neither — but it is a trade, and it has not been argued as
one.

### 4. Information-flow control cannot see the attack that needs no preconditions

The defensive design targets exfiltration: credential staging, filesystem and network sinks
([RAD-0020](Research-RAD-0020-Information-Flow-Control)). But **46% of published attacks require no
precondition at all** and corrupt logic silently, while the `.env` vector this project's harness is
built on is **4%** ([RAD-0031](Research-RAD-0031-Which-Vectors-Reach-A-Real-Project)).

Writing a subtly wrong calculation, or omitting a validation branch, is an **ordinary text edit** —
not a privileged sink. No lattice, sandbox or tool gate inspects it, because nothing dangerous is
being *called*. The controls this project carries forward address delivery, and the largest attack
class does not need delivery to anywhere.

### 5. The users who gain most are the users most exposed

Content value is concentrated in **local open-weight models**: frontier models showed no lift on
well-known public libraries, while local models were stale on every one and the index corrected
them ([RAD-0016](Research-RAD-0016-The-Content-Value-Ab)).

Those same local instruction-tuned coding models are **the most injectable measured** — one complied
8 of 9 times *despite* explicit untrusted-data framing, where framing protected several frontier
agents completely ([RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection)).

**So the risk profile is inverted against the value profile.** A team self-hosting a coding model
for cost or privacy reasons gets the most from the codex and has the least protection from it.

**Not previously stated as a single finding**, though both halves were measured.

### 6. Index-side resolution is validated only where a compiler already ran

Offloading documentation inheritance from the parser to a **transitive graph join in the index**
([RAD-0009](Research-RAD-0009-Reusing-Indexers-And-What-To-Index), [RAD-0012](Research-RAD-0012-Structure-From-Bytecode))
was validated on JVM bytecode, where `.class` files carry fully-qualified supertypes.

Dynamically-typed and macro-heavy ecosystems have no equivalent. Tree-sitter alone cannot link
inherited types across package boundaries in Python, JavaScript, TypeScript generics or Rust traits
without invoking a real language server. **The graph join is a JVM result presented as a general
one.**

### 7. Classifier corpus skew — since addressed

The review noted the negative corpus was 77.7% from ten libraries across three publishers, so any
prose classifier would separate on **publisher house style** rather than intent.

This one is closed: `test12` rebuilt the corpus at **274 publishers and 883 libraries**, and
`test17` measured the effect directly — global scoring rejected unseen publishers 2.0× over budget,
per-library normalisation brought it to 1.3×. The concern was correct and is now measured rather
than feared.

## Findings

**Reasoned. Nothing in this record is a new measurement**; every number is re-cited from the record
named beside it.

- **Two tensions were not previously collected**: the summariser answers injected *instructions* and
  not *fabricated capabilities* (§1), and the value/vulnerability profiles are inverted against each
  other (§5).
- **Four are known but uncosted**: the transitive dilemma (§2), the curation trade (§3), the
  tampering blind spot (§4), and the JVM-only graph join (§6). Each is recorded somewhere; none has
  had its two branches priced against each other.
- **One is closed** (§7), by work done after the review was written.

## Recommendation

**Nothing here is a decision, and none of these should be resolved by argument.**

1. **Treat §1 as a gap in the summariser rather than a flaw in the design.** The component still
   does what it was built for. It needs a companion answer for fabricated capabilities, and the
   obvious candidate — does the described capability *resolve* against the declared surface — is
   the check `test14` priced at 1.73% for directives and has never been tried on capability claims.
2. **Cost §2 before building the harvester's default.** The choice of declared-only is currently
   made on the security side alone; the reinvention cost of hiding 86–99% of the callable surface
   has never been measured, and `test0`'s rig can measure it.
3. **State §5 in the published material.** It is the most decision-relevant thing here for anyone
   adopting this, and it is currently split across two records that a reader would have to join.
4. **Scope §6 honestly** wherever the graph join is described, since it is presented as
   cross-ecosystem and is validated on one.

**What would change the answer.** §1 and §2 are the two that could alter the architecture rather
than the documentation. If fabricated capabilities cannot be resolved against a declared surface,
the semantic face of the entry needs a different defence entirely — and that is close to the
project's central design.

## Connections

- [RAD-0019](Research-RAD-0019-Retrieval-At-Scale), [RAD-0024](Research-RAD-0024-Does-The-Pipeline-Filter-Injection), [RAD-0025](Research-RAD-0025-The-Summariser-As-Attack-Surface) — §1
- [RAD-0001](Research-RAD-0001-Cost-Of-A-Skill-Per-Dependency), [RAD-0022](Research-RAD-0022-The-Value-Of-Transitive-Capabilities) — §2
- [RAD-0018](Research-RAD-0018-The-Selection-Ab), [ADR-0004](Decisions-ADR-0004-Librarian-And-Codex) — §3
- [RAD-0020](Research-RAD-0020-Information-Flow-Control), [RAD-0031](Research-RAD-0031-Which-Vectors-Reach-A-Real-Project) — §4
- [RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection), [RAD-0016](Research-RAD-0016-The-Content-Value-Ab) — §5
- [RAD-0009](Research-RAD-0009-Reusing-Indexers-And-What-To-Index), [RAD-0012](Research-RAD-0012-Structure-From-Bytecode) — §6
- [RAD-0035](Research-RAD-0035-A-Small-Local-Model-For-The-Prose-Gap), [RAD-0036](Research-RAD-0036-Can-The-Corpus-Be-Poisoned) — §7
