# Documentation-Comment Systems by Ecosystem

Reference material: the in-source documentation system each ecosystem uses, its
tag vocabulary, and the signals a harvester cares about. It feeds the parse and
content layers (RAD-0009, RAD-0011) and the entry definition (RAD-0013).

**Provenance.** The **source URLs are the authority** — treat them as the pointer,
this page as an index. KDoc and PHP/phpDocumentor were read from their pages 2026-08-19; the rest are from
those pages plus general knowledge and are **to verify before load-bearing**. The
**Swift/DocC** entry is a pointer only — its markup and directive set span many
pages and **needs a deeper crawl**. The Go page failed to fetch on 2026-08-19, so
its entry is from general knowledge against the URL.

## Two signals to read across all of them

Before the per-ecosystem detail, the two cross-cutting things a harvester extracts:

**"Not part of the public API / hide from docs" — an author-intent signal.** Every
ecosystem has a way to say *this exists but is not the documented surface*. It is
the doc-layer twin of `api` vs `implementation` (RAD-0007), and the harvester
should respect it — exclude or down-rank.

| Ecosystem | "hide from docs / not public" |
|---|---|
| Kotlin (KDoc) | **`@suppress`** — excludes from generated docs; "not part of the official API… but still visible externally" |
| Java (Javadoc) | `@hidden` (Java 9+) |
| TypeScript/JS | `@internal` (with `--stripInternal`); `@private` |
| Swift (DocC) | access level (`private`/`internal`); DocC exclusion |
| Go | **unexported** — a lowercase identifier is not public doc |
| Python | leading underscore, and `__all__` |
| PHP (PHPDoc) | `@ignore` (exclude from output); `@internal` (and `@api` marks the public surface) |

**Standard vs custom tags.** Whether an author can add a tag the system does not
define (RAD-0011's load-bearing question). KDoc, Javadoc, Go and Python: fixed set
or none. TSDoc: **first-class custom tags** (`tsdoc.json`). DocC: the extension
point is **an article**, not a tag. PHP (phpDocumentor): an **unrecognised tag is
shown as description text** — neither dropped nor errored, one concrete answer to
the unknown-tag question KDoc leaves open.

## Kotlin — KDoc (+ Dokka)

Source: <https://kotlinlang.org/docs/kotlin-doc.html> — read 2026-08-19.

Block tags: `@param`, `@return`, `@constructor`, `@receiver`, `@property`,
`@throws`, `@exception`, `@sample` (embeds a function body as the example),
`@see`, `@author`, `@since`, **`@suppress`**. Inline: `[element]`,
`[label][element]`, `[qualified.name]` links; Markdown for the rest.

Harvest notes: **`@suppress`** = exclude from the codex (or mark non-official) — the
author's explicit "not the official API" (the reason the user flagged it). `@since`
→ the entry's version-introduced field (availability, drift). `@sample`/`@see` are
usage and relationship. **No custom-tag mechanism is documented** — the page says
nothing about unknown tags, which is RAD-0011's open, load-bearing question (what
Dokka *does* with one is the test0 L3 experiment). Java's `@Deprecated`/KDoc has no
`@deprecated` tag — Kotlin uses the **`@Deprecated(..., replaceWith = …)`
annotation**, the machine-readable drift marker.

## Java — Javadoc

Source (as provided): <https://www.jetbrains.com/help/idea/javadocs.html> — IntelliJ's
authoring/rendering help. The canonical tag spec is Oracle's Javadoc reference;
confirm tag details there.

Tags: `@param`, `@return`, `@throws` / `@exception`, `@see`, `@since`,
`@deprecated`, `@author`, `@version`, `@serial`/`@serialField`, `@hidden` (Java 9+),
and inline `{@link}`, `{@linkplain}`, `{@code}`, `{@literal}`, `{@value}`,
`{@inheritDoc}`. Fixed set; no custom-tag mechanism (custom `-tag` doclet options
aside).

Harvest notes: Javadoc coverage is culturally high (RAD-0011: 84% on Java-majority
libraries). `@deprecated` is a **prose** drift marker here (unlike Kotlin's
annotation). `@hidden` is the exclude signal. `{@inheritDoc}` and plain inheritance
mean a rich parser resolves docs an override omits (the test0 inheritance case).

## TypeScript / JavaScript — JSDoc (and TSDoc)

Source: <https://www.typescriptlang.org/docs/handbook/jsdoc-supported-types.html> —
the JSDoc subset TypeScript understands.

Tags (TS-supported JSDoc): `@param`, `@returns`/`@return`, `@type`, `@typedef`,
`@callback`, `@template`, `@enum`, `@deprecated`, `@see`, `@example`,
`@public`/`@private`/`@protected`, `@readonly`, `@override`, `@satisfies`,
`@overload`, `@internal`. **TSDoc** is the stricter standard layered on top, with
release tags `@public`/`@alpha`/`@beta`/`@internal` and **first-class custom tags**
via `tsdoc.json` (`tagDefinitions`, shareable through `extends`) — the model on the
custom-tag axis (RAD-0011).

Harvest notes: `@internal` + `--stripInternal` is the exclude signal. `@deprecated`
is the drift marker. Custom tags are declarable, so the designed tier is native
here.

## Swift / Objective-C — DocC  *(pointer — needs crawling)*

Source: <https://www.swift.org/documentation/docc> — landing page only.

DocC compiles `///` (and `/** */`) symbol markup **together with a documentation
catalog** (a `.docc` directory of Markdown articles, extensions and resources in
the package source tree — RAD-0011 measured this on `swift-composable-architecture`).
Symbol markup uses Markdown with sections (`- Parameters:`, `- Returns:`,
`- Throws:`) rather than a large `@tag` vocabulary; the author-extension point is
**an article**, not a tag. Availability/visibility via `@available` and access
levels.

**This entry is incomplete on purpose** — DocC's markup, directives (`@Metadata`,
`@Options`, `@TutorialsView`, etc.) and catalog structure are spread across many
pages and should be **crawled** into a fuller reference before the Swift parser is
built.

## Go — doc comments

Source: <https://go.dev/doc/comment> — *(fetch failed 2026-08-19; from general
knowledge, verify against the page)*.

**No tag vocabulary at all** — everything is prose convention: a doc comment
**begins with the identifier's name**; a `Deprecated:` paragraph marks deprecation;
**doc links** `[Name]`/`[pkg.Name]`; headings and lists by formatting convention;
`Example` functions in `_test.go` files are compiled and run as executable samples;
`doc.go` carries package-level docs. The exclude signal is **export status** — a
lowercase (unexported) identifier is simply not public documentation.

Harvest notes: highest measured coverage of any ecosystem (RAD-0011: ~100%) with
*no* tags — the counter-example to the custom-tag framing. `Deprecated:` is the
drift marker; `Example` is `@sample` but build-checked.

## Python — docstrings (PEP 257)

Source: <https://peps.python.org/pep-0257/> — docstring conventions.

**No tag system.** PEP 257 fixes conventions (module/class/function docstrings,
one-line vs multi-line, `"""` triple-quoted). Structure is carried by **docstring
dialects** — reStructuredText/Sphinx (`:param:`, `:returns:`), Google style, NumPy
style — which are conventions, not a language feature. Non-public is marked by a
**leading underscore** and by `__all__`.

Harvest notes: docstrings ship on ~97% of distributions (RAD-0011), but the
structure is dialect-dependent, so a parser must handle several docstring styles
rather than one tag set.

## PHP — PHPDoc (phpDocumentor)

Source: <https://manual.phpdoc.org/HTMLSmartyConverter/HandS/phpDocumentor/tutorial_tags.pkg.html>
— read 2026-08-19 (a phpDocumentor 1.x tutorial; PhpStorm uses PHPDoc). Modern
phpDocumentor 3 extends the set.

DocBlocks (`/** */`). Block tags include `@param`, `@return`, `@var`, `@property`,
`@method`, `@deprecated`, `@see`, `@since`, `@version`, `@author`,
`@package`/`@subpackage`, `@uses`, `@todo`, `@example`, `@license`, `@link`,
`@internal`, `@ignore`, and structural markers (`@abstract`, `@final`, `@static`,
`@access`). Inline: `{@link}`, `{@inheritdoc}`, `{@internal}`, `{@source}`,
`{@example}`. Modern phpDocumentor adds **`@api`** to mark the public surface
alongside `@internal`.

Harvest notes: **`@ignore`** excludes a DocBlock from output and **`@internal`**
marks internal use — the hide signals; `@api` (modern) marks the public API, the
positive twin. `@deprecated` is the drift marker, `@since` the version. And an
**unrecognised tag is displayed as description text** rather than dropped or
errored — a concrete unknown-tag behaviour, unlike KDoc's undocumented one.

## Connections

- [RAD-0011](../research/0011-existing-documentation-systems-as-skill-content.md) —
  doc systems as skill content; the custom-tag question `@suppress`/`@since` and
  this catalogue feed.
- [RAD-0009](../research/0009-reusing-indexers-and-what-to-index.md) — the
  per-ecosystem parsers that read these tags.
- [RAD-0013](../research/0013-the-codex-entry.md) — entry fields these populate
  (`@since` → version, `@suppress` → exclude, `@deprecated`/`replaceWith` → drift).
- [RAD-0007](../research/0007-choosing-between-overlapping-libraries.md) —
  `@suppress` as an author-intent signal alongside `api`/`implementation`.
