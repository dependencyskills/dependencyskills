# The POM Points at Documentation, But Not at the Artifact

RAD-0067 · 2026-09-01
Keywords: can we use a library's own documentation; does the POM say where the docs are; scm and url fields in a pom; fetch the README from GitHub; is the POM description a summary; one README for many artifacts; does a README say what a library is not for; where do migration notes live; version skew between a README and a resolved version; is fetched documentation trustworthy; network dependency in a local-first tool.
Measured against: 756 POM files in one real Gradle module cache; README, CHANGELOG and migration-document probes against eight widely used repositories reached from those POMs; macOS arm64, 2026-09-01.

## Question

RAD-0064 concluded that the deliverable is an overview and that generation is the dominant cost. RAD-0065 then found that where a library ships a hand-written skill it is short, well-shaped, and better than anything a per-entry summarizer produces.

Which raises the obvious question: **the library already has documentation, and the POM says where it is.** A README explains what a library is for and shows how to use it — the two things the specification asks for first. Can that be used as the source, instead of, or ahead of, generating one?

## Trail

### The pointer is real, and better populated than expected

Across 756 POMs in a real cache:

| field | present | share |
|---|---|---|
| `<url>` (absolute) | 720 | 95% |
| `<description>` | 724 | 96% |
| `<scm>` | 622 | 82% |
| resolves to a `github.com` URL | 471 | 62% |

The remaining hosts are the ones you would guess — an ASF project site, Android's source browser and developer site, the Kotlin site, a few Sonatype stubs. So a pointer to *something* exists almost always, and a pointer to a repository host exists for most.

**The POM's own `<description>` is not a candidate.** It is present 96% of the time and has a **median of 7 words**, 15 at the 90th percentile. It is a label — "Apache Commons Text library" — not a summary. Nothing in the POM is content; the POM is only ever an address.

### What the address points at is the wrong unit

This is the finding that decides the shape of any design here.

Of the 470 POMs resolving to GitHub, they point at **72 distinct repositories**. **94% of artifacts share a repository — and therefore a README — with at least one other artifact**, averaging about six and a half artifacts per README. In this cache one repository is named by 267 separate POMs.

A README describes a **project**. A dependency is an **artifact**. `kotlinx-serialization-json` and `kotlinx-serialization-core` resolve to the same document, which describes neither of them specifically; a consumer who has taken one and not the other gets text about both. The unit the store is keyed by — a coordinate — has no document of its own most of the time.

This does not sink the idea. It fixes its scope: fetched documentation can supply **project-level orientation**, and cannot supply per-artifact detail. Something else has to do that, which is what the harvest is for.

### What a README actually contains

Eight repositories sampled, counting the specification's fields rather than words alone.

| | words | code blocks | "not for" signals | version/migration signals |
|---|---|---|---|---|
| range | 187 – 1,058 | 0 – 14 | **0 – 1** | **0 – 2** |

READMEs are compact — comparable in length to the hand-written skills in RAD-0065 — and most carry real code examples written by the author. On the specification's first two fields, *what it solves* and *how it is meant to be used*, they are good, and they are free.

On the two fields the specification ranks highest they are close to silent. Negative boundary — what the library is *not* for — barely registers, which is the same gap RAD-0065 found in hand-written skills and the specification itself predicts is most often missing. Version-specific movement — renames, relocations, the thing an agent is most confidently wrong about — is essentially absent from README text.

**That content exists, just not there.** Of four repositories probed, three carry a `CHANGELOG.md` or `CHANGES.md` and two carry a dedicated migration or compatibility document. The *what moved* field has a natural source; it is simply a different file.

One of the eight READMEs could not be fetched at the conventional path at all. A URL that resolves is not the same as a document where you expected it.

### Two problems that are not about content

**Version skew, which is the serious one.** A README is fetched from a branch, and a branch describes the current state. A build resolves version 2.1.0 and gets documentation for whatever is on `main`. The specification is explicit that version-matched provenance is what makes a correction credible — an agent holding a stale prior is displaced only by a contradiction it can trust. Documentation fetched from a moving branch and attached to a pinned version produces confident, well-sourced, wrong answers, which is worse than no answer. A tag matching the resolved version is the only defensible fetch, and tag naming is not standardized.

**Trust.** A sources jar is the artifact you already resolved, checksummed, and are compiling against. A README fetched live is mutable, unsigned, and controlled by whoever controls the repository — and it would be flowing into a store that agents read as fact. This is a materially larger injection surface than anything RAD-0060 and RAD-0062 have had to screen so far, because it is prose written to be read by an agent, arriving from the network, at generation time. It is not disqualifying, but it moves the trust boundary and cannot be bolted on afterwards.

There is also a plainer objection: the codex is local-first and binds to loopback by default. Making generation depend on the network changes what the tool is.

## Findings

**Measured.**

- 95% of POMs carry an absolute `<url>`; 82% carry `<scm>`; 62% resolve to a GitHub URL.
- The POM `<description>` is a label, not a summary — median 7 words.
- 470 artifacts point at 72 repositories; **94% of artifacts share a README with another artifact**.
- Sampled READMEs run 187–1,058 words with code examples, and carry almost no negative-boundary or version-movement content.
- Changelogs and migration documents exist in most of the sampled repositories, and hold the version-movement content READMEs lack.

**Asserted.**

- That a project-level README improves a generated artifact-level overview more than it misleads it. Plausible and untested; the granularity mismatch cuts both ways.

## Recommendation

**Use fetched documentation as *a* source, never as *the* source, and never as the only one for a given field.**

A workable division, in the specification's own terms:

| field | best source |
|---|---|
| what it solves | README, project level |
| how it is meant to be used | README code examples, project level |
| invariants and traps | harvest — this is where prose in the sources jar earns its keep |
| what moved | CHANGELOG or migration document, **matched to the resolved version** |
| what it is *not* for | no reliable source anywhere; still the hard one |

**Conditions, all of which are load-bearing.**

1. **Fetch by tag matching the resolved version, or do not fetch.** A branch README attached to a pinned artifact is the failure the specification exists to prevent.
2. **Attribute it.** A skill assembled partly from fetched text must record which parts came from where, so a later correction knows what to distrust.
3. **Treat it as untrusted input at generation time**, screened like any other prose entering the store, and decide that before building it rather than after.
4. **Never require the network.** Fetched documentation is an enrichment; a machine that cannot reach GitHub must still produce a skill.

**What would change the answer.** If a comparison shows generated overviews are no better with the README than without — plausible, given 94% of them describe a different unit than the artifact — then the whole fetch path is cost and risk for nothing, and the harvest alone is the answer. That comparison needs the use record from #33 to be meaningful, because "better" here means an agent got further, not that the text read nicely.

## Connections

- [RAD-0064](RAD-0064-the-skill-is-the-overview.md) — why an overview is the deliverable at all.
- [RAD-0065](RAD-0065-what-v1-skill-authors-wrote-unprompted.md) — hand-written skills, which show the same negative-boundary gap.
- [RAD-0062](RAD-0062-screening-an-identifier-that-cannot-be-rewritten.md) and [RAD-0060](RAD-0060-known-bad-as-a-first-gate.md) — the screening a network fetch would have to pass.
- [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md) — the transport this would sit beside, not replace.
