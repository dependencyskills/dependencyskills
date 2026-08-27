# How the Source Is Read

RAD-0015 · 2026-08-19 · v1
Keywords: how do we read source that lives inside an archive; extract everything or read in place; the archive-everything trap; the IntelliJ lazy-VFS precedent; NIO FileSystemProvider over zip; Commons VFS; a language-agnostic read layer; remote and git backends; loose first-party source.

**Reasoned, not measured.** This record argues about an access strategy. The VFS
reuse is grounded — JetBrains' VFS docs, `KotlinCoreEnvironment`'s use of
`CoreApplicationEnvironment.jarFileSystem`, and the JDK `jdk.zipfs` module, all
checked 2026-08-19 — as is Apache Commons VFS (Apache-2.0, layered + remote
providers, actively maintained, checked 2026-08-19); its full provider list is to
verify against its `filesystems` doc. The I/O and disk costs this reasons about are
**not yet measured** (named below), and whether Dokka's *source-input API* accepts a
jar/VFS root rather than a directory is **still to confirm**.

## Question

Get is settled — content comes from the `-sources.jar` (ADR-0009). Parse is
chosen — tree-sitter and Dokka (RAD-0009). Between them sits an unnamed stage:
**how do the bytes get from the archive to the parser?** Do we explode every
`-sources.jar` to disk, stream entries in place, or read only what we need? And
the same stage has a second caller — a project's **own loose source** on disk —
so the real question is how the harvester *reads source at all*, archived or not.

## Trail

### Extract-all is the archive-everything trap at scale

The obvious path — unzip every `-sources.jar` to a temp source tree and parse the
lot — is simple and wrong at scale. A real graph is 311–995 libraries (RAD-0001).
Coverage is a median **33%** (RAD-0011), and a library yields **~10–20**
caller-facing capabilities from hundreds of symbols (RAD-0013) — so an extract-all
pass writes the whole of every archive to disk to parse two-thirds of it into
nothing. This is precisely the JVM "archive everything, read little" cost the
project keeps running into; paying it once per library-version, across a thousand
libraries, to discard most of the result is the strategy to avoid.

### Three strategies

- **Extract-all** — explode to disk, parse. Simple; maximal I/O and disk; most
  work wasted.
- **Read-in-place** — stream entries directly from the jar (a zip/NIO filesystem),
  no extraction. tree-sitter parses *text handed to it*, so an entry read from the
  archive goes straight to the parser with nothing unpacked. Low disk, reads still
  cover the whole archive.
- **Selective read** — read only the entries that will yield an entry. Lowest I/O,
  but needs a cheap way to know *which* entries matter before reading them (below).

### The IntelliJ precedent: on-demand, not extraction

IntelliJ does not explode every `-sources.jar` — the intuition is correct. Its
**Virtual File System** presents a file uniformly whether it is on disk or *inside
an archive*, and its snapshot "stores only those files which have been requested
at least once through the VFS API," i.e. **on-demand reading**. Sources are
attached lazily and pulled per-file when you navigate to a symbol; what IntelliJ
indexes *eagerly* is the **compiled class structure** (PSI stubs), while source is
read on demand and cached by file. IDE-scale library handling is a lazy,
non-extracting read over the archive — the selective strategy, with the compiled
surface as the map of what to read.

### The read layer abstracts loose and archived source

The harvester has two source callers, and they are the same operation:

- **dependencies** — source lives in a `-sources.jar` (entries in an archive);
- **first-party** — source lives loose in the working tree (files on disk).

So the stage is a **read layer** with one interface — *give me the source for this
coordinate/module* — over two backends, archive and loose. Reading loose source is
not a wasteful special case of extract-all; it *is* the first-party path, reused.
IntelliJ's VFS makes exactly this abstraction (disk, archive, remote — one API).

### The read layer is language-agnostic — reuse a VFS

Reading source is *path → bytes*, and bytes do not care what language they are. So
the read layer is not per-ecosystem at all — it is **one shared stage** under every
parser. Only the *container format* varies, and there are few of those even though
the ecosystems are many:

| Container | Ecosystems | Read via |
|---|---|---|
| ZIP archive | Kotlin/JVM `-sources.jar`, Python wheel | JDK `jdk.zipfs` (`FileSystems.newFileSystem`) |
| TAR archive | npm `.tgz`, Python sdist | a tar `FileSystemProvider` (add-on; not in the JDK) |
| loose tree | Go module cache, Swift SPM checkout, unpacked `node_modules` | the default filesystem |
| remote | open git repo (the `<scm>` route), docs host | HTTP-backed VFS, or clone-to-loose, or the host's raw-file API |

Java's `java.nio.file.FileSystemProvider` SPI is exactly this abstraction — one
`Path` API, a provider per container — so a jar, a wheel and a Go checkout are read
by the *same* code. The IntelliJ **core VFS** (`CoreJarFileSystem`, local, jrt) is
the same idea in the frontend Dokka runs on. **So the VFS generalises to any
source, Swift included** — and Swift is among the *easiest*: SPM ships source loose
on disk (RAD-0011), a default-filesystem read with no archive at all, plus a DocC
catalog and SymbolGraph JSON that are likewise just files.

This sharpens the layer boundary: **read is language-agnostic and built once; parse
stays per-ecosystem** (RAD-0009). The adapter maps each *parser's* model to the
entry; the VFS maps each *ecosystem's container* to uniform bytes. Both are reuse,
not build.

### The backends extend to remote sources

A VFS abstracts *on disk, in an archive, and over HTTP* alike — IntelliJ's spans
all three. So a source the harvester does not hold locally is just another backend
behind the same read interface: an **open git repo** named by the `<scm>`
coordinate (the additive transport route beyond `-sources.jar`), or a docs host.
The interface — *source for a coordinate/module* — does not change; only the
provider does. Practically, a streaming HTTP-VFS reads individual files, while a
whole git repo is usually read by **clone-to-loose** (then it is the loose-tree
backend) or the host's **raw-file API** — same read layer either way. This is also
how the central corpus (RAD-0003) reaches across repositories it never had on
disk, and it makes the `<scm>` route reachable without a new stage.

### Commons VFS is the batteries-included reader

**Apache Commons VFS** (Apache-2.0, actively maintained) is an off-the-shelf
realisation of this layer: one `FileObject` API over local, **zip/jar, tar,
gzip**, ram/res, and **remote — HTTP/S, FTP/S, SFTP, WebDAV** — plus **layered**
filesystems (read a jar *inside* an http URL through one API). It covers every
backend in the table above — archive, tar, loose and remote — in a single
dependency, so the bytes side of the read layer is almost entirely reuse; the JDK
`jdk.zipfs` is the minimal-dependency alternative when only local archives matter.

The one seam: Commons VFS hands back **bytes** (`FileObject` → `getInputStream()`),
which is exactly what tree-sitter and remote fetching want — but **Dokka needs
IntelliJ `VirtualFile`s**, not `FileObject`s, so it cannot take a Commons VFS
handle directly. So the read layer may sit behind one or two backing engines — see
next.

### Leaning to the JetBrains VFS, with options open

With both sources vendored in the workspace (intellij-community and commons-vfs), a
look favours the **JetBrains VFS**: it can serve *both* parse paths from one engine
— it hands Dokka the `VirtualFile`s it needs, and a `VirtualFile` also yields an
`InputStream` for tree-sitter — so the two-engine split collapses to one, and it
carries the on-demand/selective read and the uniform local/jar/jrt/http
abstraction this record wants. **Commons VFS stays an option**: simpler, standalone,
pure-bytes, a clean fit where PSI integration is not needed (a non-JVM path, a
remote-only fetch). And having both source trees keeps a third route open — **port
or roll our own**: lift the `CoreJarFileSystem` / on-demand-read parts out of the
IntelliJ core without the full IDE `PersistentFS` coupling. The open question is
where that coupling boundary sits — how much of the JetBrains VFS runs headless
as-is versus needs extracting — and the vendored source lets us read the answer
rather than guess it.

### Loose first-party source is a first-class input, and the cleanest value case

Reading loose source "isn't a complete loss," because a project's own source is a
codex input in its own right. The **selection failure** — the agent not reaching
for what already exists — is usually argued about libraries the model was not
trained on, but it applies to code the agent has direct, unrestricted access to.
First-party source is the purest case: no training-exposure gap, no transport, no
archive — the file is open in the tree — and it is *still* reinvented, because
nothing triggered the agent to consult it. **Proximity is not consultation.**

That makes first-party source the strongest v0 demonstration: point the codex at a
project's own source and measure whether the agent reaches for existing code
instead of rewriting it. It removes every confound the third-party case carries
and needs no archive reading at all — plausibly the *first* spike, ahead of the
`-sources.jar` path.

### Selective read wants a map — which is where bytecode returns

Selective read needs to know which source files carry a caller-facing capability
before reading them all. The cheap map is the **compiled surface**: enumerate the
public API from the `classes.jar` (RAD-0012's structural tools, IDE-proven, run
once per library-version) and pull from the `-sources.jar` only the entries backing
documented public symbols. This brings RAD-0012 back not for *content* but as a
**read-planner** — the same role IntelliJ's eager stub index plays against its
lazy source reads. It stays deferred for v0 (read-in-place is enough to start);
it is the lever if full reads prove too costly.

### Dokka reads through the same VFS

The parser choice interacts less than first feared. tree-sitter takes bytes, so it
composes with `jdk.zipfs` directly. Dokka runs on the Kotlin frontend, which reads
source through the **core VFS** (`CoreJarFileSystem`, per `KotlinCoreEnvironment`)
— jar entries in place, no extraction — so Dokka most likely does *not* force
exploding archives; the VFS it needs comes with it. What remains to confirm is
whether Dokka's *source-input API* accepts a jar/VFS root or insists on a
directory; if it insists, a thin core-VFS source root closes the gap. The read cost
is a smaller mark against Dokka than the bake-off first assumed.

## Findings

**Grounded.** IDE-scale source handling (IntelliJ VFS) is lazy, on-demand,
non-extracting. The abstraction is reusable, not bespoke: the JDK `jdk.zipfs`
provider reads a jar/wheel as a `Path` filesystem in place, and
`KotlinCoreEnvironment` reads jars headless via `CoreApplicationEnvironment`'s
`jarFileSystem` — the same core VFS Dokka runs on.

**Reasoned.**

- Extract-all pays whole-archive I/O and disk to discard ~two-thirds; wrong at
  1,000-library scale.
- The stage is a **language-agnostic read layer** — path→bytes over a few container
  formats (zip, tar, loose tree) shared across many ecosystems, one `Path` API via
  `FileSystemProvider`; only parse stays per-ecosystem.
- The backends extend to **remote** (HTTP/git): the same interface reaches an open
  git repo (the `<scm>` route) or docs host, so cross-repo harvest (RAD-0003) needs
  no new stage — clone-to-loose or a raw-file API behind the same read layer.
- **Apache Commons VFS** (Apache-2.0) supplies the bytes/remote side in one library
  (local/zip/jar/tar/gz/http/sftp/webdav/layered); the Dokka side needs IntelliJ
  `VirtualFile`s, not `FileObject`s.
- On inspection the **JetBrains VFS** leads — one engine for both parse paths (a
  `VirtualFile` feeds Dokka and yields bytes for tree-sitter) plus on-demand reads;
  Commons VFS is the simpler standalone option; both sources are vendored, so
  porting a lean reader (CoreJarFileSystem, minus PersistentFS) is a live route.
- **Loose first-party source is a first-class input**, justified by the selection
  failure (proximity ≠ consultation), and is the cleanest v0 value case.
- **Selective read** is the IDE strategy; it needs a map, which is RAD-0012's
  structural enumeration acting as a read-planner (deferred, the lever if needed).
- Dokka reads jars through the core VFS without extracting, so the extraction worry
  is largely dissolved; only whether its source-input API takes a jar/VFS root
  remains to confirm.

**To measure.**

- I/O and disk of extract-all vs read-in-place across a real graph (hundreds of
  `-sources.jar`).
- Whether Dokka's source-input API accepts a jar/VFS root or needs a directory (and
  if so, the cost of a core-VFS source-root shim).
- Whether selective read (structural map → pull only documented entries)
  meaningfully cuts work over read-in-place, or is premature optimisation for v0.
- Where the JetBrains VFS's headless-coupling boundary sits (the core env vs the
  full PersistentFS) — readable from the vendored intellij-community source.

## Recommendation

**Read in place; do not extract — reuse a VFS.** Lean to the **JetBrains (IntelliJ
core) VFS** as the primary engine: it serves both parse paths (a `VirtualFile`
feeds Dokka and yields bytes for tree-sitter) and gives on-demand/selective reads,
so one engine covers the layer. Keep **Commons VFS** as the simpler standalone
option (local/zip/jar/tar/gz and remote HTTP/S/SFTP/WebDAV in one Apache-2.0
library) where PSI integration is not needed; the JDK `jdk.zipfs` is the minimal
local-archive fallback. With both sources vendored, **porting a lean reader**
(CoreJarFileSystem + on-demand, without PersistentFS) is a live route. Do not build
a bespoke reader per ecosystem, and do not boot the full IDE PersistentFS; settle
the headless-coupling boundary against the vendored source. Confirm Dokka's
source-input API accepts a jar/VFS root; if not, a thin core-VFS source root closes
it.

**Make it one language-agnostic read layer.** A single interface — *source for a
coordinate/module* — over container-format providers (archive and loose), shared
across ecosystems rather than rebuilt per language. First-party loose source is a
first-class codex input; Swift and the rest read through the same layer.

**Start the value spike on first-party loose source.** It removes every confound
and needs no archive handling — the cleanest test of whether the codex defeats the
selection failure, and plausibly the first thing to build.

**Hold selective read for when reads prove costly.** Read-in-place is enough for
v0. If full reads are too expensive at scale, enumerate the public surface from the
`classes.jar` (RAD-0012) as a read-planner and pull only the source entries that
matter — the IntelliJ eager-index/lazy-read shape.

## Connections

- [ADR-0009](../decisions/ADR-0009-transport-is-sources-jar.md) — get; this is the read
  stage immediately downstream of it.
- [RAD-0009](RAD-0009-reusing-indexers-and-what-to-index.md) — parse; tree-sitter
  takes bytes, Dokka may force extraction — a cost to weigh in the bake-off.
- [RAD-0012](RAD-0012-structure-from-bytecode.md) — the structural enumeration that
  becomes the read-planner for selective reads.
- [RAD-0013](RAD-0013-the-codex-entry.md) — the entry the read ultimately feeds; the
  ~10–20-per-library count that makes full reads wasteful.
- [RAD-0011](RAD-0011-existing-documentation-systems-as-skill-content.md) — the 33%
  coverage that makes two-thirds of an extract-all pass yield nothing.
- [RAD-0007](RAD-0007-choosing-between-overlapping-libraries.md) — the selection
  failure that first-party indexing addresses even with the source in hand.
