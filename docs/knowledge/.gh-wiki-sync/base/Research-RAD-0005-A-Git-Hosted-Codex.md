# A Git-Hosted Codex

RAD-0005 · 2026-08-14 · v1
Keywords: can library guidance be fetched from the git repository instead of the artifact; scm in the POM; finding the right version by tag; integrity and checksums without a repository; borrowing the packument; llms.txt at a package root; what a git route cannot do.

**Measured against:** measurements taken 2026-08-13 and recorded in
[RAD-0002](Research-RAD-0002-Existing-Documentation-Systems-As-Skill-Transport) — `<scm>`
presence and content across 86 POMs, `-sources.jar` availability across 215
coordinates on two repositories, and signature/checksum availability across a
170-coordinate Central sample. No new measurement for this record.

## Question

[RAD-0002](Research-RAD-0002-Existing-Documentation-Systems-As-Skill-Transport) established
that the source repository is reachable from a coordinate: `<scm>` is present
in 90% of Maven Central POMs and 82% of Google Maven POMs, and every URL found
resolved to a fetchable git host. That opens a route to library guidance that
needs no repository artifact at all.

It arrived there as a by-product of asking a different question, and the design
outgrew the record. This one asks it directly: **what would a git-hosted codex
have to specify to be a real mechanism rather than an appealing idea?**

Four sub-questions, each of which had an objection raised against it and an
answer proposed:

1. How does a consumer find the entry for the version it actually resolved,
   when the repository's default branch describes unreleased work?
2. How does it know the entry has not been altered, given git refs are
   mutable and nothing is checksummed the way a Maven artifact is?
3. What does it fetch, and how many requests does that take?
4. Where does the file live in ecosystems that are not the JVM?

## Trail

### Why the route is worth specifying at all

Three properties, none of which any artifact-based mechanism has.

**It is retroactive.** A library that last released in 2023 can add guidance
tomorrow, in one commit, without cutting a release. Given that adoption is the
project's hardest problem — and that a published artifact is permanent and
cannot be amended — this is the single strongest argument for the route.

**It requires no publishing change.** No classifier, no Gradle Module Metadata
variant, no AGP packaging behaviour, and in particular none of the KMP
root-module machinery that a previous proposal recorded as "the only piece of
this that is currently stuck".

**It composes.** A repository file can point elsewhere — at a docs site, a
CDN, a capability server — which makes it an additional source alongside
harvested `-sources.jar` content rather than a competitor to it.

### 1. Finding the right version

The objection: reading a well-known path on the default branch returns
whatever is there now, which may describe API that has not shipped. Measured,
the metadata does not rescue this — `<scm><tag>` identifies the released
version in **2%** of POMs (2 of 86), 81% carry `<scm>` with no `<tag>` at all,
and **0 of 83** sampled jar manifests record a commit.

The answer is that this file is *designed for this system*, so its layout is
ours to define rather than inherited. Two mechanisms, and they compose:

- **Version-addressed content.** `<well-known-dir>/<version>/…` — the default
  branch accumulates one entry per release and never rewrites one. An agent
  holding `1.4.2` reads the `1.4.2` path; unreleased work is at a different
  path and therefore invisible.
- **Version or range declared in a manifest**, so a library with two hundred
  patch releases declares `1.4.x` once rather than shipping two hundred files.

Neither reads "now", which is what drift was.

### 2. Integrity

The objection: git is mutable, so an entry could be altered after the fact.

**Git is already a content-addressed store.** A blob's object ID is a hash of
its contents; a commit ID covers the whole tree. What is mutable is a *ref*.
Addressing `main:<path>` reads through a mutable pointer; addressing a blob by
object ID reads something that cannot change without changing its name. The
absence of a checksum was an artefact of how we were addressing, not of the
medium.

That leaves anchoring — where the object ID is recorded so it cannot be
swapped. Three options, increasing cost:

1. **Anchor in the artifact repository.** A POM property, or a line in the
   sources jar, carrying the manifest's object ID. The immutable side vouches
   for the mutable one. Smallest possible publisher change, but not zero.
2. **Chain the entries.** Each version's entry records the previous entry's
   object ID; rewriting an old one breaks the chain and is detectable by
   anyone who fetched earlier. The transparency-log pattern in miniature, with
   no service to operate.
3. **Sign the manifest**, which is nearly free: **99% of the Central sample
   carries a `.asc` PGP signature** (169/170) and 100% publish checksums.
   Central requires it, so the publisher already holds a key and already signs
   at release. A detached signature over the manifest attests authorship, not
   merely integrity, and covers every entry the manifest names by object ID —
   one signature for the whole graph.

### 3. What is fetched — borrow the packument

A path convention alone forces a consumer to guess whether an entry exists.
That is probing, and a previous proposal already rejected probing on measured
grounds: ~148s for a 374-dependency graph, with misses bypassing the CDN.

The shape to borrow is the one the JVM already uses for exactly this problem,
and which npm uses better. A POM does not make a consumer probe; it declares.
npm's packument is one document per package listing every version with its
metadata and integrity hashes, and the proposal's own conclusion —
*"declare it in metadata where metadata exists … treat probing as a fallback
of last resort"* — was drawn about artifacts and transfers unchanged.

So: **one manifest at one well-known path**, fetched once, declaring what
exists.

```
<well-known-path>/index.json
```

Minimum contents per entry: the version or range it applies to, where the
content is, and its integrity. Optionally: what kind of content it is
(capability guidance, migration notes, samples), and whether it is *designed*
or *discovered* — a distinction RAD-0002 makes and which a consumer needs in
order to weigh it.

This is also not novel. `llms.txt`, recorded in `docs/knowledge/reference/landscape.md` as prior
art for splitting an index from full text, is this pattern at a website root.
This is the same pattern at a package root, which makes it a conformance
argument rather than a new format.

### 4. Where it lives per ecosystem

The manifest is one document with one name; only its location differs. That is
ADR-0007's shape — same content, per-ecosystem placement — and it means the
per-ecosystem question shrinks to a single line of specification each rather
than a packaging design each.

Unresolved, and the substance of what a spec would have to settle: whether the
JVM location is derived from `<scm>` (repository root plus a well-known path),
whether npm's belongs in `package.json` (which pnpm RFC #13422 argues is the
right place for exactly this kind of signal), whether Go's is at the module
root, and whether SwiftPM needs one at all given a package *is* a git
coordinate and its `.docc` catalog already ships.

### What this route cannot do

**It is not a substitute for `-sources.jar`.** The artifact is the only route
with a cryptographic tie between content and a released version obtained as a
side effect of resolution. Anything version-exact and safety-relevant belongs
there. The git route's verification is opt-in: a consumer must fetch and check
rather than receiving verification free.

**It depends on a third-party host.** Availability, rate limits and
disappearance are all real, and are the npm-install-from-git failure mode. A
sensible design caches and pins, which is the same answer Maven gives.

**It does not solve discovery of what a library is for.** It is transport and
addressing. What goes *in* the entry is a content question, and the
cross-library "why not the other one" remains local knowledge that no
library's repository can hold.

## Findings

**Measured** (all from RAD-0002).

- `<scm>` present in 90% of Central POMs, 82% of Google Maven; every URL found
  resolves to a fetchable host — `github.com` dominating Central,
  `android.googlesource.com` all of Google Maven.
- `<scm><tag>` identifies the released version in 2% of POMs; 0 of 83 jar
  manifests record a commit. Version location cannot come from existing
  metadata.
- 99% of Central coordinates carry a PGP signature; 100% carry checksums. The
  signing capability the integrity design needs is already universal.

**Reasoned.**

- That version-addressed paths and range-declaring manifests eliminate drift.
  This follows from the definitions, but has not been built.
- That one signed manifest is cheaper and stronger than per-entry checksums.
- That `llms.txt` and the packument make this a conformance argument rather
  than an invention.

**Unverified and consequential.**

- Whether repository hosts tolerate the request pattern. A consumer resolving
  300 dependencies fetching 300 manifests from `github.com` is a different
  traffic profile from a CDN-fronted artifact repository, and GitHub's
  unauthenticated rate limits are low. **This is the most likely thing to sink
  the route**, and it is measurable.
- Whether a monorepo publishing many coordinates from one repository needs
  per-artifact scoping in the manifest, or whether one manifest per repository
  is the right granularity.
- What happens when `<scm>` is stale — the library moved hosts, the
  organisation was renamed, the repository was deleted. Unmeasured.

## Recommendation

**Specify it, as an additional route rather than a replacement.** The
retroactive property is worth having and no artifact mechanism can offer it.
The two objections that looked structural — drift and integrity — are both
consequences of inheriting git's defaults, and both dissolve once the file is
designed rather than discovered.

**The spec has to settle, at minimum:**

1. The manifest's location per ecosystem, and how it is derived from a
   coordinate.
2. Its schema: version or range, content location, integrity, content kind,
   and designed-versus-discovered.
3. The integrity story: object-ID addressing, signature over the manifest, and
   whether an artifact-side anchor is required or optional.
4. Caching and pinning, so a build is reproducible and a host outage is not a
   build failure.
5. Failure behaviour: what an agent does with a stale `<scm>`, a missing
   manifest, or a signature that does not verify. Silence and guessing are
   both wrong.

**Measure the request pattern before committing.** Fetching one manifest per
dependency against GitHub's unauthenticated rate limits is the concrete risk,
and it is the same class of objection that killed 404-probing. It can be
tested against a real graph in an afternoon.

**Keep it clearly second to `-sources.jar` for version-exact content.**

## Connections

- [RAD-0002](Research-RAD-0002-Existing-Documentation-Systems-As-Skill-Transport) — the
  measurements this rests on, and the designed-versus-discovered distinction.
- [RAD-0003](Research-RAD-0003-Central-Capability-Server) — a manifest can point at a
  capability server; the two compose.
- [RAD-0006](Research-RAD-0006-Development-Time-Prompt-Injection) — a fetched, mutable,
  third-party-hosted instruction source is squarely in that threat model.
- ADR-0003 — the
  artifact route this is additional to.
- [ADR-0007](Decisions-ADR-0007-Conform-To-Existing-Conventions) — one document,
  per-ecosystem placement.
- [docs/landscape.md](Reference-Landscape) — `llms.txt` and pnpm RFC #13422, both
  prior art for the manifest shape and its placement.
