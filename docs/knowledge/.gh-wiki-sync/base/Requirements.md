# Product Requirements

What the product must do, and for whom. Each record states a problem in a user's terms, the outcomes that would count as solving it, the requirements that follow, and the tracker stories that verify them.

A requirement here is narrative and numbered, so a story can cite it. **Acceptance criteria are not written here** — done-ness belongs to the tracker, on the story that carries it, and a checklist duplicated into a document is a checklist that goes stale without anyone noticing. The Stories table is a list of tracker IDs and the requirements each one covers; it is the only place this section touches work.

This is also not where a choice is argued. A requirement says what must be true; an [architecture decision](../decisions/README.md) says which of several ways it is done and what was rejected, and a [research record](../research/README.md) says what was measured to get there. A PRD cites both rather than restating them.

| PRD | Title | What it requires |
|---|---|---|
| [0001](0001-the-dependency-codex.md) | The Dependency Codex | A local, machine-shared index of what the dependencies a project resolved can already do — harvested from what libraries already publish, queried by need, served where an agent works |
| [0002](0002-the-trust-boundary.md) | The Trust Boundary | That library prose never reaches an agent verbatim through this tool — rewriting rather than detection, degradation rather than silence, and an honest account of what none of it catches |

The two are one product and are split because they answer different questions. 0001 is the discoverability problem: an agent cannot see the classpath. 0002 is the exposure the first one concentrates: third-party prose already reaches agents unchecked everywhere, and an index is the first place there is a door to put a lock on. Where they state the same fact — the per-project scope filter is the case — **0001 owns it**, and 0002 records the second, independently sufficient reason it is there.
