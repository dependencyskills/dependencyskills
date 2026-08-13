# Working in this repository

## What this is

A convention plus reference implementations of it — not a product. The
audience is other people implementing the same convention, so the spec is
the primary artifact and the code exists to demonstrate it.

Read `docs/adr/` before proposing structure or layout changes. Three
different splits of this repository were considered and rejected, and each
looks correct until it meets a concrete case. The reasoning is recorded
precisely so it does not get re-litigated.

## This repository is public

Everything committed here is world-readable and permanent — a public git
history cannot be unpublished, only added to. Write for a stranger who
found this repo, not for the maintainer.

**Never commit personal details.** No real names in prose or code comments,
no personal email addresses, no machine or hostnames, no absolute home
paths, no employer, client or private project names, no internal tracker
keys or instance URLs, and nothing about the maintainer's business, billing
or working habits. Verbatim quotes from a working conversation leak all of
that at once, and documents generated straight from a conversation — ADRs,
design notes, session summaries — are where it happens.

The exceptions are deliberate and narrow: the copyright line in `LICENSE`,
and maintainer attribution where a human has put it. Do not add to that
list without being asked, and do not strip what is on it.

**Document the pattern, not the person.** Where a decision came from
someone's specific situation, the reusable content is the pattern: this is
a common way developers work, here is why the obvious design fails against
it, here is how this one covers it.

**Examples use placeholders.** `acme`, `example.com`, `owner/repo`,
`com.example`. Never a real project, org or instance, even a public one — a
real name in an example reads as a live reference.

## Provenance

Auditability is a goal of this project, so the repository practises it.

- **Every commit is signed and signed off.** `git commit -S -s`. A
  contribution without a `Signed-off-by` line cannot be merged; see the
  [DCO](https://developercertificate.org/).
- **Merge commits only.** No squash, no rebase merging. Squash and rebase
  both create new commits, which discards the contributor's signature and
  the per-commit sign-off, collapses the reasoning for individual changes,
  and erases that a branch existed. Tidy history locally before opening a
  PR, where you can re-sign your own work; never on the merge button.
- **Findings established by experiment carry the versions they were
  measured against.** They rot otherwise — the packaging behaviour this
  project works around has already changed once between toolchain releases.
  Do not cite a result without saying what produced it.

## Conventions

- **Path segments are lowercase, digits and hyphens.** No camelCase in any
  directory or file path. macOS and Windows are case-insensitive and Linux
  is not, so a mixed-case path works locally and fails in CI, and git can
  end up tracking two entries differing only by case. Language identifiers
  are not paths — camelCase is correct in Kotlin and stays.
- **`poc/` suspends the usual standards, in both directions.** No
  acceptance criteria or coverage bar inside it; nothing leaves it by being
  copied. See `poc/README.md`.
- **Each implementation owns its build.** There is no root build. Do not
  add one.
