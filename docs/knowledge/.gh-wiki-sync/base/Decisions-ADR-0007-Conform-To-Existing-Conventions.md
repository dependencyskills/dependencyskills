# Adopt each ecosystem's convention where it works — reject what we can't reconcile, propose only what none has

ADR-0007 · 2026-08-12 (revised 2026-08-16) · Status: accepted · v2

## Context

The project spans several package ecosystems, and the temptation is to
define one layout and emit it everywhere. That would be wrong, and would
fail for reasons that are social before they are technical.

**The ecosystems are not equally empty, and none is truly empty.** npm has
several conventions in circulation — [library-skills.io](https://library-skills.io/create/)
uses `.agents/skills/<name>/SKILL.md`, while
[mise](https://github.com/jdx/mise/discussions/9479),
[Vercel's skills CLI](https://github.com/vercel-labs/skills) and
[skills-npm](https://github.com/antfu/skills-npm) use a plain
`skills/<name>/SKILL.md`, and an agent-skills RFC now proposes a manifest
field. Swift is earlier but already ships a curated DocC catalog inside the
package. Maven was thought to have nothing; it does — 93–98% of artifacts
publish `-sources.jar`, and the one in-jar mechanism that exists (SkillsJars,
`META-INF/skills/…`) is the very approach this project already measured as
broken on Android and Kotlin Multiplatform.

**Arriving with our own layout in an ecosystem that already has one is rude
and self-defeating.** It ignores existing work, splits an already unsettled
space further, and gives maintainers a reason to dismiss the part that is
genuinely new. Conformance is the default.

**But adopting is not unconditional.** A convention can exist and still be
one we cannot use: the in-jar bundling that drops out of an AAR, the
scan-a-directory-and-make-everything-resident loading model that has no
aggregate budget. Where a standard carries a pitfall we cannot reconcile,
adopting it anyway would ship a known defect. So the default is conform, and
the exception — reject — must be earned with evidence, not preference.

**The standards are still being developed.** The format converged and a
major vendor (Microsoft's Agent Framework) adopted it unchanged; npm is
mid-argument; SPM and others are earlier. A decision made against a snapshot
rots. The goal is to move *with* these standards as they form — adopt what
works, feed back what we learn, and not fork.

**The two halves have different standing.** The transport half is
ecosystem-specific and, outside the JVM's early confusion, mostly has an
answer already. The index half — the resident-description budget, the
loading model — is ecosystem-agnostic and unclaimed everywhere. Only that
half is ours to propose.

## Decision

**Adopt an existing standard where it works. Where a standard carries a
pitfall we cannot reconcile, do not adopt that mechanism — and record the
measured reason. Where a standard is still forming, track it and feed into
it rather than fork. Propose only the part no ecosystem has claimed.**

| Channel / layer | What exists | What we do |
|---|---|---|
| The skill **format** (`SKILL.md`, name/description, scripts/references/assets) | converged; adopted unchanged by a major vendor | **adopt as-is** — inventing an alternative is the v1 mistake |
| The **loading model** (scan a directory, make everything resident) | shipped everywhere | **reject** — O(n) resident cost, no aggregate budget; propose the index instead |
| **Maven / JVM transport** | `-sources.jar` ships on 93–98% of artifacts | **conform** — Maven is not the empty ecosystem we thought; harvest the documentation that already travels. The in-jar mechanism that exists is **rejected** (measured broken on AAR/KMP). The specific transport is the regenerated packaging ADR's to fix. |
| **npm** | several layouts, unsettled; a manifest-field RFC forming | **conform and track** — emit into what it converges on; be ready to emit more than one |
| **SPM** | a DocC catalog convention exists | **conform** — the catalogue is the model, not a target to lead |
| The **index** (librarian + resident-budget answer) | nobody has it | **propose everywhere** — the one thing we ask anyone to adopt as new |

**The packager is required to know the target ecosystem's convention.** An
emit step is not a copy into a path of our choosing; it places content where
that ecosystem's consumers already look, and updates whatever manifest that
ecosystem requires.

**Where an ecosystem's convention is contested, emit more than one layout
rather than picking a winner.** A duplicated markdown file costs kilobytes;
being wrong about which convention wins costs adoption.

**The index is proposed everywhere, and it sits on top.** It reads harvested
content regardless of which layout or documentation system produced it. This
is the part with no prior art in any ecosystem.

## Consequences

- `spec/publishing.md` is a **per-ecosystem conformance document**, not one
  layout: here is what this ecosystem does, here is where its documentation
  already lives, here is the manifest edit it needs. Almost none of it
  proposes; it records.
- We take on a **standing tracking burden**. Conventions we do not control
  move — a manifest-field RFC may settle npm, a format revision may land —
  and an emit step written against a convention that has shifted is worse
  than none. `spec/` records which revision of each ecosystem's convention
  an implementation was written against; `landscape.md` tracks the field.
- Rejecting a mechanism requires evidence, not preference. The in-jar route
  and the resident loading model are rejected on measurements this project
  holds (AGP/AAR exclusion, KT-46493, the cost model). That is what makes the
  rejection defensible to the ecosystems that ship them.
- The project's claim shrinks and gets stronger. Not "here is how library
  skills should work"; instead "here is the one gap nobody filled — the
  resident-description budget — and here is the layer that makes everyone
  else's work usable at scale."

## A note on where agents actually are

Emission order should follow where a consumer can use the result today, not
where it is architecturally tidy. Agent tooling is unevenly distributed:
IntelliJ and the JetBrains IDEs have agent support Kotlin and JVM developers
already use; Xcode's is thinner, so a Swift consumer is more likely working
in an editor outside Apple's toolchain, if at all. An emit step for a channel
whose consumers have no agent to read the result is correct and premature at
the same time. This is an argument for ordering, not exclusion.

## References

- [landscape.md](Reference-Landscape) — who is doing what, and where; the tracking burden this decision takes on has to live somewhere
- [RAD-0002](Research-RAD-0002-Existing-Documentation-Systems-As-Skill-Transport) — the documentation systems that already ship; conformance-over-invention applied to content, not just packaging
- [RAD-0008](Research-RAD-0008-The-Field-As-It-Stands) — the field as it stands: the format adopted unchanged, the in-jar approach and why it does not survive Android/KMP, the manifest-field RFC
- The **index** (librarian + codex) is decided separately; its ADR is being regenerated as the design settles.
- [Agent Skills specification](https://agentskills.io/specification)
