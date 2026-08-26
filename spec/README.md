# The specification

Normative. Everything under `implementations/` and `integrations/` is an
implementation of what is written here, and `conformance/` is how that is
demonstrated rather than asserted.

Three documents, three audiences:

- **`publishing.md`** — where the skill goes in each ecosystem. Not one
  layout: a per-ecosystem conformance document. We conform to conventions
  that already exist and propose one only for Maven repositories, which
  have none and cannot have the directory convention the others share. See
  [ADR-0007](../docs/knowledge/decisions/0007-conform-to-existing-conventions.md).
- **`content.md`** — what a skill should actually say. The Agent Skills
  spec defines `name` and `description` and stops; this is the unclaimed
  ground, and the index is only as good as the prose it is built from.
- **`discovery.md`** — how a consuming project indexes what it harvested
  and chooses among hundreds of candidates. This is the hard part.

The spec versions with the implementations in this repo, so nobody reads
spec v2 against a plugin built for v1. That is the reason this is a
monorepo.

None of these are written yet.

## Which attempt is this

The design has been rebuilt twice. Documents throughout this repository say
which attempt they describe, because material about v1 and v2 is kept
deliberately — the failures are the argument — and a reader must never
mistake it for the live design.

| | Shape | Status |
|---|---|---|
| **v1** | `META-INF/ai-skills/*.ai-skill.md` bundled in the artifact, plus a README prompt to make an agent look | shipped, failed — see [the postmortem](../docs/knowledge/postmortems/v1-bundled-flat-files.md) |
| **v2** | A real `SKILL.md` at the canonical path inside the archive, announced by a `MANIFEST.MF` attribute | abandoned before release — the attribute cannot reach an AAR |
| **v3** | A sidecar artifact beside the jar, declared as a module metadata variant. Nothing reads the archive. | **current** |

**These are attempt numbers, not spec versions.** They record how the design
got here. When `spec/` is published normatively it starts its own version
series from the beginning — a specification whose first release is called v3
would be confusing to everyone who was not in the room.
