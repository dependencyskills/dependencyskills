# Case Studies

Worked examples of the failure this project exists to fix, observed in the
field: a capability that already exists goes unused — and gets rebuilt — because
it is undiscoverable from where the work happens. Each one traces a real
incident to the mechanism behind it, then names the reusable rule.

They sit beside the [postmortems](../postmortems/README.md) but are a different
kind of evidence. A postmortem inspects *this project's* own shipped-and-failed
artifact, still downloadable on Maven Central. A case study inspects a
discoverability failure elsewhere — sometimes in a private working codebase,
anonymised to placeholders with the mechanisms and counts kept as measured;
sometimes in a public library whose behaviour anyone can inspect today, cited to
source. Either way the point is the same failure the codex attacks between
libraries, caught somewhere it is cheaper to see.

Between them the studies map the axes on which the right answer goes missing:
**space** (findable from where you work), **time** (fresh enough), **selection**
(which of several), and **provenance** (actually yours to build on). Each closes
differently, and two of them do not close with a bigger model at all.

Like postmortems and reference, case studies are historical field material —
living evidence, not part of the versioned [canon](../CANON.md).

## Index

| | | |
|---|---|---|
| [0001](0001-thirteen-slug-functions.md) | Thirteen Slug Functions | One working module, no consumers, and the same three-line idiom retyped thirteen times across every tier — discoverability in *space* |
| [0002](0002-the-datetime-instant-move.md) | The Datetime `Instant` That Moved | A model's prior stays fixed on `kotlinx.datetime.Clock` after the types moved to the stdlib — discoverability in *time* (freshness) |
| [0003](0003-the-legacy-library-everyone-remembers.md) | The Legacy Library Everyone Remembers | An agent reaches for Moment.js in new code; "which library" splits into a fact the codex closes and a *selection* gap nothing does |
| [0004](0004-the-dependency-nobody-declared.md) | The Dependency Nobody Declared | Most of what compiles was never declared, and an agent can't tell — the importable-set measurement felt as a *provenance* problem |
