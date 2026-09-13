# The Package That Did Not Exist Yet

RAD-0071 · 2026-09-09

Keywords: llms.txt supply chain attack; slopsquatting; hallucinated package names registered by attackers; does the rewriter defend against this; fabricated capability in the wild; guidance files as a carrier; is the minimal version more exposed; POM description as an untrusted channel; data has become code; why a quarantine does not help here.

Measured against: nothing measured here. This record is an assessment of externally reported research against measurements this project already holds, chiefly [RAD-0006](RAD-0006-development-time-prompt-injection.md) v7 and `test6`. Sourcing is weaker than usual and is described below.

## Question

Reported research describes agents at large companies fetching an `llms.txt` guidance file, following its installation instructions, and executing attacker-controlled code. **Does this change this project's threat model, and does it argue against the deliberately unguarded minimal version explored in [RAD-0070](RAD-0070-the-smallest-thing-that-works.md)?**

## Trail

### What is claimed, and how well sourced it is

Three articles were supplied. One returned HTTP 403; a second came back truncated to little more than its headline. The specifics below therefore rest on a single secondary source, and **the primary research was not read**. Reported as claims:

- More than **8,500 `llms.txt` files** were analysed.
- They referenced **237+ packages that do not exist** across PyPI, npm and RubyGems.
- Researchers registered one of the missing names; a company server **downloaded and executed it about four minutes later**.
- A real campaign is described involving an authentication service whose `llms.txt` named a package that attackers subsequently created.

The framing offered is *"the line between plain text and executable code has completely disappeared"*.

### This project named the carrier already

RAD-0006 v4 lists where third-party text currently lands: *"a skill body loaded from a `SKILL.md`, a rules file, a fetched `llms.txt`, an MCP tool description."* Its conclusion is general and holds here without amendment — **any tool that loads third-party content into an agent's instruction context inherits the result** — as does its observation that progressive disclosure does not help, because it solves a context-budget problem rather than a trust one.

So the *carrier* is not news to this project.

### The mechanism is not injection, and that distinction is the finding

RAD-0006 measures **injected instructions**: text crafted to subvert an agent, placed to maximise its chance of being obeyed. Almost every control this project has — the rewriter quarantine, the imperative rule, the external-names rule, the classifier — is built against that.

**None of them engages what is described here.** In the reported attack:

- nothing is injected — the guidance file says *install this package*, which is what a guidance file is for;
- no author is compromised — the name was very likely never published, quite possibly hallucinated by the model that generated the file;
- nothing is malformed, imperative-in-a-suspicious-way, or naming a credential, host or path.

The content is honest about its author's intent. The attacker's contribution is **registering the name**.

That maps precisely onto the one gap this project already documented and declined to close. The `Summariser` says so in its own doc comment:

> A **fabricated capability** — honest-looking, non-imperative prose describing something a library does not do. `test6` measured a fabricated library beating the true answer 4 of 17. A rewriter has no purchase: nothing is malformed, so it faithfully rewrites a lie.

**This is that gap occurring in the wild, with package registration as the payoff.** The novelty is not the technique but the economics: the attacker does not need to compromise anything, only to notice a name nobody claimed.

### So the quarantine would not have stopped it, and its removal does not worsen this

The conclusion that matters for current work. A rewriter faithfully paraphrasing *"use the `acme-helper` package for retries"* produces a sentence that is just as actionable and just as wrong. **Removing the quarantine does not increase exposure to this attack, because the quarantine never covered it.**

That is a narrow claim and should not be stretched. Removing the quarantine plainly does increase exposure to the attacks RAD-0006 *did* measure. This record says only that the reported research is not evidence against the minimal version.

### What is genuinely new exposure, and it was introduced this week

RAD-0070's `libindex.py` reads the POM `<description>` and places it in what an agent sees, screened by nothing. That field is present on **94% of 1,477 cached POMs**, is free text controlled by the library's publisher, and is exactly the shape the reported attack exploits: a short machine-readable description that tooling is expected to trust.

The Kotlin implementation does not read it. This is a carrier the minimal version *adds*, rather than an existing one it weakens — and it is the one thing in RAD-0070 that this research bears on directly.

Two adjacent surfaces are worth naming while the subject is open, neither currently active:

- **A library's own shipped skill.** `META-INF/ai-skills/*.ai-skill.md` is structurally an `llms.txt` living inside the artifact. [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) suggested a shipped skill should be preferred over a generated one; this research is an argument for qualifying that "plainly should". The v1 bundled-file approach was abandoned for unrelated reasons — see the postmortem in `spec/README.md` — so nothing is exposed today.
- **Documentation the POM points at.** [RAD-0067](RAD-0067-the-pom-points-at-documentation.md) measured those pointers. Following them would import the `llms.txt` problem wholesale.

### The exposure profile differs from the reported one, and not only in size

The reported victims are **company agents fetching published `llms.txt` files over the network**, at organisations with no view of what their agents install. The codex is a local index over dependencies a developer's own build already resolved and already executes at compile time. The library is on the machine either way; the codex describes what is there rather than instructing an agent to fetch something new.

**That is a genuine reduction and not an elimination.** A codex entry recommending a capability by name is still a name an agent may act on, and the store is machine-level and shared across projects.

## Findings

**Reported by others, not verified here.**

- 8,500+ `llms.txt` files analysed; 237+ references to non-existent packages across three ecosystems; roughly four minutes from registering a name to remote execution. One secondary source; the primary research was not read and two of the three supplied articles could not be retrieved.

**Established here, by argument against existing measurements.**

- **The reported mechanism is not prompt injection** and is engaged by none of this project's content-screening controls, because the content is honest.
- **It is the fabricated-capability case `test6` already measured at 4 of 17**, arriving in the wild with a payoff attached.
- **The rewriter quarantine would not have prevented it**, so removing it does not increase exposure to this class.
- **`libindex.py` introduces the POM `<description>` as an unscreened carrier**, present on 94% of cached POMs. New surface, added by RAD-0070's experiment rather than inherited.

## Recommendation

**This does not argue against the unguarded minimal version, and the current direction stands.** That is the maintainer's call, made after this record was put to them, and the reasoning supports it: the quarantine never covered this attack, so its removal costs nothing against this particular class. RAD-0070 continues as scoped — a personal tool, on one machine, with a stated threat model of none.

**Record the POM description as a known unscreened channel** in RAD-0070 rather than screening it. The value of the minimal version is finding out how small the thing gets when nothing is guarded; adding a screen to the first carrier that appears would forfeit exactly the measurement it exists to take.

**Do not let the exemption travel.** What is deliberate for a local personal tool is not deliberate for the product. If any of this ever moves out of `experiments/`, the POM description arrives with it, and the reasoning above is scoped to the former.

**What would change the answer.**

- If a shipped `META-INF/ai-skills` document is ever preferred over a generated one, this class becomes live in the product and RAD-0065's recommendation needs revisiting first.
- If the codex ever follows a pointer out of the POM to fetch documentation, it inherits the reported attack directly rather than by analogy.
- If the primary research turns out to describe a mechanism that *does* involve crafted instructions, the central distinction in this record is wrong and it should be re-read against RAD-0006 rather than beside it.

## Connections

- [RAD-0006](RAD-0006-development-time-prompt-injection.md) — the injection surface, which names `llms.txt` as a carrier and whose general conclusion holds here.
- [RAD-0070](RAD-0070-the-smallest-thing-that-works.md) — the unguarded minimal version this was assessed against.
- [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) — shipped skills, the nearest dormant equivalent of an `llms.txt`.
- [RAD-0067](RAD-0067-the-pom-points-at-documentation.md) — the pointers that would import the problem wholesale.
