# ADR-0009: Library content is got from `-sources.jar`, not a bespoke artifact

Date: 2026-08-17 · Status: accepted · v1

## Context

The withdrawn sidecar decision (the `-skills.zip` classifier artifact, now in
`_to_delete/premature-adrs/`) rested on the premise that nothing suitable already
travels with a JVM library, so a new artifact had to be published.

[RAD-0002](../research/0002-existing-documentation-systems-as-skill-transport.md)
measured that the premise is false:

- **`-sources.jar` is published by 93–98%** of libraries — 95.3% on Maven
  Central, 93.3% on Google Maven, **98% of Android AARs**, and by the root module
  of every Kotlin Multiplatform library checked. `-javadoc.jar` is 92.4% on
  Central but **6.7% on Google Maven**, so it is not a viable carrier for Android.
- The publisher ask is already met: the sources jar is produced by a one-line
  opt-in that most publishing plugins take **by default**. Adoption costs the
  ecosystem nothing new.
- The **git repository is in the published metadata** — `<scm>` present in 90% of
  Central and 82% of Google Maven POMs — giving a third, retroactive route that
  needs no artifact at all.
- The ability to **read** these carriers at scale is not hypothetical: IDEs parse
  the API surface and doc comments out of jars over massive dependency graphs and
  cache per version
  ([RAD-0009](../research/0009-reusing-indexers-and-what-to-index.md)).

The system is built up in stages — **get, parse, store, query**. This decision
settles **get**. Whether the content that travels is rich enough to be worth
harvesting is a separate, still-open question
([RAD-0011](../research/0011-existing-documentation-systems-as-skill-content.md)).

## Decision

**Library content is obtained from what already travels with the library, not
from a new artifact:**

- **`-sources.jar`** — the primary carrier. Near-universal, tied to the resolved
  version by construction, the only route carrying a cryptographic tie between
  content and release.
- **The first-party source tree** — for a project's own modules, where there is
  no transport problem at all, and where reinvention is worst.
- **The git repository named in `<scm>`** — an additive, retroactive route,
  specified separately in
  [RAD-0005](../research/0005-a-git-hosted-codex.md).

**No bespoke `-skills.zip` is published. The sidecar is abandoned.** `-javadoc.jar`
is not used, being effectively absent on Google Maven.

## Consequences

- **Zero new publisher action.** Nearly every library already ships sources,
  most without deciding to, because the plugin does it. A newly invented
  classifier would start at zero adoption and be argued for library by library.
- **The sidecar's one known blocker becomes irrelevant.** `addVariantsFromConfiguration`
  threw on a KMP root module; `-sources.jar` is already published there by the
  standard plugin. The blocker is not solved — it is routed around.
- **One mechanism covers first-party and third-party.** The sidecar covered only
  third-party; reading source covers a team's own modules too.
- **No permanence risk.** A published artifact cannot be withdrawn; committing
  libraries to a classifier this project invented was a real risk. `-sources.jar`
  carries none — they already publish it.
- **What is given up, and it is real.** A sources jar carries no *declaration*
  that skill-shaped guidance is present, so "fetchable before I depend on it"
  weakens to "fetchable, then inspected." And it is the whole source, which must
  be **parsed** rather than read — an integration cost handed to the parse stage
  (RAD-0009), not a blocker, since the tooling exists and runs at IDE scale.
- **This settles get, and only get.** Parse (RAD-0009), store
  ([RAD-0010](../research/0010-how-the-codex-is-stored-and-served.md)), query
  ([RAD-0003](../research/0003-central-capability-server.md)), and content value
  (RAD-0011) remain downstream and open.

## Rejected

- **The bespoke `-skills.zip` sidecar** (the withdrawn packaging decision). It
  was well-reasoned and it works, but it duplicates an artifact already at 93–98%
  adoption, requires publisher action that sources does not, and repeats the v1
  mistake of inventing a container the ecosystem does not know how to load. Full
  reasoning, and the design recorded rather than deleted, is in RAD-0002.
- **`-javadoc.jar` as the carrier.** Near-universal on Central, effectively
  absent (6.7%) on Google Maven — dead for Android on arrival.

## References

- [RAD-0002](../research/0002-existing-documentation-systems-as-skill-transport.md)
  — the transport measurement this decision is drawn from.
- [RAD-0009](../research/0009-reusing-indexers-and-what-to-index.md) — the parse
  stage: the tooling that reads these carriers, demonstrated at IDE scale.
- [RAD-0005](../research/0005-a-git-hosted-codex.md) — the git-repository route.
- [RAD-0011](../research/0011-existing-documentation-systems-as-skill-content.md)
  — whether what travels is rich enough to be worth harvesting (open).
- [ADR-0007](0007-conform-to-existing-conventions.md) — conform to what exists;
  `-sources.jar` is a stronger instance than a new classifier.
