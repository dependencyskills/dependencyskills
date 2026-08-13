# Filing Conventions

Structure is not prescribed - it is whatever the knowledge base shows,
arranged by humans in YouTrack, mirrored down by the sync. What follows
are conventions and starting points, not a required tree.

## Repo layout around the mirror

```
docs/
├── README.md       Git-native front door - explains this system (never synced)
├── knowledge/      THE MIRROR - two-way sync domain, whole project KB
│   └── .yt-sync/       sync state + merge bases (commit; never hand-edit)
├── stories/        GENERATED issue snapshot (story-reconcile) - never synced
├── outbox/         Outbound artifacts (third-party bug reports, letters) -
│                   written FOR someone else, not project knowledge
└── _archive/       Retired files - kept out of the mirror on purpose
```

Pure agent instructions stay at the repo root (`AGENTS.md`,
`WIRING.md`). Indexes and doc-system notes the agent maintains live at
the `docs/` root. None of that syncs.

`docs/design/` is a **companion tree to `docs/knowledge/`**, not a section
inside it: design records plus the images that make them worth reading.
It stays git-native because the sync carries `.md` only - a record pushed
to the KB would publish its prose and drop every sample as a dead link.

    docs/knowledge/   synced knowledge, text
    docs/design/      design records + samples, git-native
    docs/stories/     generated issue snapshot
    docs/            machinery: indexes, doc-system notes

So the KB's **Design & Accessibility** section holds design *direction* -
principles, accessibility standards, system-level guidance a reader wants
alongside the other knowledge. Anything with a picture in it belongs in
`docs/design/`, and the two cross-reference.

## Suggested starting sections

Sections are just top-level articles; create the ones the project needs
and let the owners rearrange freely. Names are spelled out for humans -
"Architecture Decision Records", never "adr"; "Quality Assurance",
never "QA"; accessibility (AX) belongs with Design.

| Section | What belongs there |
|---|---|
| Architecture Decision Records | One hard-to-reverse choice each; append-only history |
| Product Requirements | PRD narratives + Stories tables of tracker IDs (never AC) |
| Specifications | How a thing IS - architecture, component specs; update in place |
| Design & Accessibility | Design *direction* and accessibility standards - text-only. Records with mockups live in `docs/design/`, not here (see below) |
| Research | Investigations - question, trail, findings |
| Reference | External facts: vendors, prospects, regulations, domain material - and the **Domain Glossary** (the project's canonical terms; `AGENTS.md` at the repo root points at it so agents find it without a path) |
| Developer Guides | How-to - onboarding, environment, CI |
| Quality Assurance | Durable test plans and protocols (QA *runs* are issues) |
| Mandates & Compliance | Legal/regulatory rules the work must satisfy |
| Support | Support knowledge, runbooks, customer-facing material |

Every section directory's `README.md` is the section article's body:
one or two sentences on what lives there and who reads it, so both KB
readers and filing agents get the same guidance. Write one whenever you
create a section.

**Subsystems (monorepos):** split WITHIN a section by subsystem
subdirectory, named exactly after the project's Subsystem field values
("CMS Server" → a "CMS Server" child article), lazily - only where a
system actually has documents. The KB then reads "Architecture Decision
Records → CMS Server" and the board field uses the same vocabulary.

## Organizing by audience instead

The sections above are organized by **document type**, which suits a
project whose readers are all engineers. A project serving several
audiences can organize the top level by **who reads it** instead, with the
type-based sections nesting inside:

| Tier | Section name | Holds | Written for |
|---|---|---|---|
| PD | Product Development | ADRs, RADs, PRDs, specs, engineering standards, reference registers with full technical detail - schemas, types, costs, ingestion rules | engineers |
| PM | Product Management | capability catalogues, coverage and cost views, roadmap and sequencing - "what we can build, what it costs, in what order" | product |
| BD | Business Development | differentiators, benefit-led overviews, positioning, pitch framing | go-to-market |

Note this **re-parents** rather than renames: Architecture Decision Records
does not map *to* Product Development, it moves *inside* it. When adopting
this shape over existing sections, map to what is already there and move
it - do not create near-duplicates beside it.

### The audience-replication convention

When a piece of knowledge matters to more than one audience - a register, a
capability, a standard - author **separate, audience-tailored versions**,
one per relevant tier. Not one document with sections for each reader:
nobody reads past their own part, and the version that matters to them ends
up buried in a document written for someone else.

- **PD** carries the full technical detail.
- **PM** carries the capability, cost and sequencing view.
- **BD** carries the differentiator and the pitch.
- **Every version cross-links the others.** That is what keeps them from
  silently diverging, and it is how a reader who needs more depth finds it.

Each tier may legitimately hold material the others do not - competitive
positioning was never in the PD version and does not belong there. Where
they state the same fact, **PD owns it**: correct it there first, then
carry the correction outward.

Not everything needs replicating. A thing that changes nothing for anyone
outside engineering has one version, in PD, and that is the normal case.

## Distinctions that matter

- **spec vs adr**: a spec describes how a thing IS; an ADR records why a
  choice was made. Specs update in place; ADRs are append-only.
- **research vs reference**: research is your investigation (trail and
  conclusion); reference is someone else's facts kept close.
- **vendors vs prospects** (both under Reference): a vendor you use or
  integrate today; a prospect you may approach - the dossier is
  knowledge, the act of reaching out is an issue. Prospects graduate to
  vendors when they become active.
- **Quality Assurance vs story QA**: per-story Gherkin lives in the
  story's `## QA` section; the KB section holds what spans stories.
- **Mandate vs procedure**: the rule ("PIPEDA requires consent") and the
  project's response ("how we check for it") are separate documents that
  cite each other. Both live in the KB; they just sit in different
  sections with different audiences.

## No status frontmatter

Docs carry at most `title`/`date` frontmatter. `id:`, `status:`,
`type:`, `parent:` blocks are retired - that state lives in the
tracker, and syncing it is what caused drift in the legacy system.

## Adopting over a legacy docs tree

1. Decide the section layout in YouTrack (create the top-level articles,
   or accept the suggestions above).
2. Move legacy knowledge files into `docs/knowledge/` under the matching
   section directories, then run the first sync with `--force` - each
   file is adopted and pushed up as a new article. (Alternatively:
   paste content into YouTrack by hand and let a clean first sync pull
   everything down.)
3. Work-tracking legacy docs (`ac/`, `gap/`, statused QA runs,
   handoffs) are NOT knowledge - hand those to the story-reconcile
   skill; that migration has its own approval gate.
4. Strip any `<!-- GENERATED -->` banners left over from the retired
   one-way publisher; the sync then pushes the clean copies up.
