# ADR-0008: One knowledge tree under docs/knowledge

Date: 2026-08-13 · Status: accepted · v1

## Context

Documentation had accumulated as a flat `docs/` — `adr/`, `postmortems/`,
a single `proposal.md`, a `landscape.md` — plus a finished research log
stranded in `outbox/`, which is a gitignored drop for artifacts written
*for someone else* (bug reports, letters), not a home for project
knowledge. Two forces pressed on this.

**A finished investigation had nowhere to land.** The cost-model RAD was
complete and in RAD shape, but the flat layout had no research section, so
it sat in `outbox/` with relative links written for a `docs/` home it never
reached.

**Every new document type reopened "where does this go."** The project's
own documentation tooling (the project-docs and to-rad skills) assumes a
single `docs/knowledge/` tree with typed sections — decisions, research,
retrospectives, reference — optionally mirrored to a tracker knowledge
base. The flat layout matched none of it, so the tooling's affordances had
no tree to point at and each addition was an ad-hoc placement call.

Two constraints shaped the answer:

- **`AGENTS.md` mandates lowercase-hyphen path segments.** The project-docs
  taxonomy names sections spelled-out with spaces and capitals
  ("Architecture Decision Records") — a convention that exists to make
  *YouTrack article titles* readable, and one that a directory tree here
  cannot follow.
- **The GitHub wiki is disabled for now.** The repository is still private
  while it is prepared for public release; the wiki will be enabled then.
  Until that point, per the project-docs GitHub binding, `docs/knowledge/`
  is git-native with no mirror — and when the wiki is enabled this same tree
  becomes the two-way sync domain, so it is laid out for the mirror it will
  have, not only the git tree it is today.

Three alternatives were weighed and rejected.

**Keep the flat `docs/` and file the RAD as `docs/rad/` beside
`docs/adr/`.** The cheapest option: the RAD's `../adr/` and `../landscape`
links keep working and no references move. Rejected because it extends an
ad-hoc layout rather than adopting the scheme the project's own tooling
already assumes. Every future document type — reference material, guides, a
spec that wants a knowledge home — would reopen the same placement
question, and the two-way-sync affordance would never have a tree to bind
to. The one-time cost of repointing references buys a layout that stops the
question recurring.

**Adopt the taxonomy's spelled-out section directory names.** Rejected: it
violates the lowercase-hyphen rule. When the wiki is enabled it derives page
names from these paths, so section pages read from the terse directory form;
the human-readable name is kept where a reader meets it — each section
`README.md` H1, which becomes the section page.

**Move `proposal.md` into the knowledge tree.** Rejected: the proposal is
the public-facing argument, written for a stranger who finds the
repository. It belongs with the front door, not inside the internal
knowledge base.

## Decision

**One knowledge tree at `docs/knowledge/`, git-native, with lowercase-hyphen
section directories:** `adr/`, `research/`, `postmortems/`, `reference/`.
Each section's `README.md` carries the spelled-out section name as its H1
and states what belongs there; the terse directory name satisfies
`AGENTS.md`, the README H1 carries the human-readable name.

- Decisions move to `docs/knowledge/adr/` (filenames unchanged, numbering
  continues from where it stood).
- Research — RADs and proof-of-concept write-ups — to
  `docs/knowledge/research/`.
- Retrospectives to `docs/knowledge/postmortems/`.
- Someone-else's-facts material (`landscape.md`) to
  `docs/knowledge/reference/`.

With no sync to assign identifiers, **local numbering conventions stand in**:
RADs and ADRs keep a zero-padded `NNNN-` filename prefix and an identifier
line in the body, matching the existing ADR convention.

**`proposal.md` stays at the `docs/` root** as the public artifact, and
`docs/README.md` remains the git-native front door. The installer-generated
`WORKFLOW.md` and `dimensions.md` stay at the `docs/` root as machinery.

**`spec/` is left as a top-level primary artifact.** Folding it into a
`docs/knowledge/specifications/` section is a separate decision, not taken
here.

## Consequences

- A new document has one obvious home, and the project-docs / to-rad tooling
  now operates against the tree it assumes rather than against nothing.
- Every inbound reference to `docs/adr/`, `docs/postmortems/` and
  `docs/landscape.md` had to be repointed in a single pass — the root
  `README.md`, `spec/`, and the internal cross-links between these
  documents. A contributor's muscle memory for `docs/adr/` is now wrong;
  this ADR and the rewritten `docs/README.md` are where they find the new
  layout.
- Section directory names diverge from the project-docs taxonomy's
  spelled-out convention **by design**. The mapping — terse hyphenated
  directory, spelled-out README H1 — is recorded here so it is not "fixed"
  back later.
- The wiki is expected to be enabled at public release, at which point
  `docs/knowledge/` becomes the two-way sync domain with no reshaping. This
  is the reason the whole tree was adopted now rather than filing the one
  research log into an ad-hoc `docs/rad/`. Turning the mirror on will be its
  own operational step, not a reversal of this decision.
- No status frontmatter, matching project-docs: lifecycle state lives on the
  metadata line and in the tracker, never in YAML.
