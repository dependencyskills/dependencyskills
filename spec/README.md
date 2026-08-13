# The specification

Normative. Everything under `implementations/` and `integrations/` is an
implementation of what is written here, and `conformance/` is how that is
demonstrated rather than asserted.

Three documents, three audiences:

- **`publishing.md`** — how a skill is published so that a consumer can
  find it without unpacking an archive. Library authors implement this.
- **`content.md`** — what a skill should actually say. The Agent Skills
  spec defines `name` and `description` and stops; this is the unclaimed
  ground, and the index is only as good as the prose it is built from.
- **`discovery.md`** — how a consuming project indexes what it harvested
  and chooses among hundreds of candidates. This is the hard part.

The spec versions with the implementations in this repo, so nobody reads
spec v2 against a plugin built for v1. That is the reason this is a
monorepo.

None of these are written yet.
