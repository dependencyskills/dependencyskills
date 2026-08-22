# ADR-0011: Publish security findings, but as observations rather than verdicts

Date: 2026-08-21 · Status: accepted · v1

The injection measurement ([RAD-0006](../research/0006-development-time-prompt-injection.md))
produced working attack payloads, a demonstrated credential-exfiltration technique against a
tool-enabled agent, and per-model compliance rates for named commercial models. This
repository is public and its history is permanent, so how that gets published is a decision
with consequences, not a formatting choice.

## Context

**This is not classic vulnerability disclosure, and treating it as such would be a category
error.** Prompt injection is a known, unfixed *class* — OWASP's first-ranked LLM risk, public
since 2022 — not a zero-day. There is no vendor with a patch to ship and no embargo window
that would mean anything. What the research adds is *measurement*: rates, which mitigations
fail, and how failure varies across agents.

**The uplift-versus-defence calculus is lopsided.** "Put an instruction in a doc comment" is
not a technique anyone needs this project to invent; the payloads are the obvious ones and an
attacker reaches them unaided in minutes. Against that near-zero uplift sits high, specific
defensive value: the people who need this are the ones building documentation-harvesting into
agents *right now* — this project among them. The measurement is what converts "we should
probably be careful" into "here is the rate, here is what fails, here is what to do instead."

**The strongest fact about the position is that the finding is against our own design.** This
is not an exposé of someone else's product. The project proposed harvesting library
documentation into agent context, stress-tested that proposal, and found a live hazard in it.
Publishing the hazard alongside the mitigation is the whole of the obligation.

**The real risk is evidentiary, not ethical.** A durable public claim that a named commercial
model "cannot be trusted", resting on N=2–3 with one prompt template in one task domain, is a
strong negative assertion on thin data. If the method has a flaw — a chat-template quirk, a
sampling artifact, a scoring heuristic — the project has published a defamatory claim caused
by its own bug. That risk is asymmetric: the cost of over-claiming lands on a third party.

**Findings about agents rot faster than findings about artifacts.** The repository already
requires measured findings to carry the versions they were measured against
([ADR-0007](0007-conform-to-existing-conventions.md), `docs/README.md`). A "do not trust X"
line that outlives the release it was measured on becomes misinformation rather than evidence.

## Decision

**Publish the research, including payloads and per-model results, subject to four rules.**

1. **Report observations, not verdicts.** Per-model results are stated as what was measured —
   *"in this test, on this date, at this N, model X complied in 8 of 9 cells"* — never as a
   trust judgement about the model. Conclusions that generalise are stated about
   *architecture* ("the system channel defeats data-framing across vendors"), which every
   model tested supports, rather than about any single vendor, which N does not.
2. **Payloads stay demonstrative, never optimised.** Published payloads demonstrate the
   *class*: inert canaries, an endpoint that was unregistered and resolved nowhere when the
   tests ran, sandboxed fake secrets. (That endpoint is not *owned* by this project, which is
   a known weakness: an unowned name can be registered by someone else, and the published kit
   invites strangers to run payloads naming it. Contributors are told to check it before
   running; the fix is to move it to a controlled domain, which changes the payload and so
   waits for a payload-set version bump rather than an edit in place.) The
   bright line is that this project does not iterate payloads to maximise bypass rates and
   publish the winners — that is building a toolkit, not measuring a phenomenon. The
   contributor kit is framed and maintained as a **defensive test suite**.
3. **Version-stamp every result and say the rot out loud.** Each row carries the agent
   version and date, with an explicit note that it is not a claim about any later release.
4. **Attack and mitigation publish together.** The architectural control is normative in the
   specification in the same release that carries the measurement. A finding published without
   its fix is a stunt.

**Notify as a courtesy, not as an embargo.** Named vendors are told that per-model results
naming them are being published, and are offered a right of reply — but **publication does not
wait on them**. There is nothing to patch, so a holding period would buy nobody anything; the
notice goes out around publication rather than in advance of it, and the letters say so
plainly so that no vendor is misled into believing it has an embargo window. Corrections are
accepted and published at any time afterwards, which is what the right of reply actually buys.

**Effort is bounded by the vendor's own channel.** The obligation is to *offer* notice, not to
chase a vendor through a bug-bounty funnel. Contacts are taken from
`/.well-known/security.txt`; where a vendor publishes no reachable security contact, a public
channel (a repository issue, a model-page discussion) is an acceptable substitute, and where
no reasonable channel exists at all the notice may be skipped and that fact recorded.

The disclosure target that matters more is the **adjacent projects** already shipping
documentation-harvesting into agents: they can act on this, and model vendors largely cannot.

## Consequences

- **Per-model tables read as measurements.** They lose rhetorical force and gain durability;
  a reader can check the transcripts and disagree, which is the standard the postmortems
  already set.
- **The load-bearing public claim is architectural.** "Library content must never reach the
  instruction channel, and the transitive tail is off by default" rests on every model tested
  and does not decay when a vendor retrains.
- **A negative result about our own proposal is published prominently.** This costs the
  project rhetorically and is the reason to trust the rest of it.
- **Contributors inherit the posture.** `CONTRIBUTING.md` carries the same rules — inert
  payloads, honest reporting of compliance, version stamps, no credentials in transcripts.
- **Some findings will be held rather than sharpened.** Where N cannot support a claim, the
  claim waits for data instead of being softened into insinuation.

## Connections

- [RAD-0006](../research/0006-development-time-prompt-injection.md) — the research this
  governs, and the mitigations it recommends.
- [ADR-0010](0010-measure-through-developer-tools.md) — measuring through real agent tools is
  what makes per-agent results meaningful, and what makes them version-bound.
- [RAD-0008](../research/0008-the-field-as-it-stands.md) — the practice of naming projects,
  not individuals, when a critique lands; the same restraint applies to naming models.
