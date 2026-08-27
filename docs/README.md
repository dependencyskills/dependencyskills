# Docs

How this repository records what it knows. Durable knowledge lives under
`knowledge/`; the public argument and the generated machinery sit beside it
at this level.

## Layout

`knowledge/` is the knowledge base — decisions, research, retrospectives and
reference material, one section per kind. It is git-native for now — the
wiki is disabled while the repository is private, and will be enabled at
public release, at which point this tree becomes the two-way sync domain.
See [ADR-0008](knowledge/decisions/0008-adopt-knowledge-tree.md) for why it is
arranged this way.

- `knowledge/decisions/` — Architecture Decision Records. One hard-to-reverse
  choice each and, more importantly, what was tried and abandoned first.
  Append-only; read these before proposing a structure or layout change. The
  records here are the decisions that have **settled** — the shape of the
  repository, how this docs tree is organised, and which ecosystem
  conventions the project adopts. Decisions still being worked out — how a
  library skill travels, how the index works — live in Research until they
  lock, then graduate here.
- `knowledge/requirements/` — Product Requirements. What the product must do
  and for whom, as numbered narrative requirements a tracker story can cite.
  A PRD is where an ADR's settled choice and a research record's measurement
  turn into an obligation; the stories that verify it live in the tracker,
  and acceptance criteria are never written here.
- `knowledge/research/` — investigations: the question that prompted the
  work, the trail of options weighed, the findings, and a recommendation. A
  recommendation that hardens into a commitment graduates to an ADR.
- `knowledge/postmortems/` — retrospectives on approaches that shipped and
  failed. Worth more than a rejected-alternatives list because the failure
  is inspectable: v1 is inside published artifacts on Maven Central and
  anyone can download one and look. A convention proposal is more credible
  from someone who shipped one, watched it break, and published the account.
- `knowledge/case-studies/` — worked examples of the discoverability failure
  this project fixes, observed in real codebases: a capability that already
  exists goes unused and gets rebuilt because it is invisible from where the
  work happens. The same failure the codex attacks between libraries, caught
  inside a single repository where it is cheaper to measure.
- `knowledge/reference/` — the other projects, specifications and
  discussions in this space.
  [ADR-0007](knowledge/decisions/0007-conform-to-existing-conventions.md) commits
  this project to conforming to conventions it does not control, a standing
  tracking burden, and `reference/landscape.md` is where it is carried.

`WORKFLOW.md` and `dimensions.md` are generated machinery describing the
issue workflow — not knowledge, and not hand-edited.

## Conventions

Findings established by experiment carry the versions they were measured
against. They rot otherwise: the packaging behaviour this project exists to
work around has already changed once between major toolchain releases.

Section directories are terse and lowercase-hyphen (`adr/`, not
`architecture-decision-records/`); the spelled-out section name lives in
each section's `README.md`. Filing a new document means dropping it in the
right section with a zero-padded `NNNN-` prefix where the section is
numbered — see ADR-0008.
