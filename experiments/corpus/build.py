#!/usr/bin/env python3
"""
Build the shared corpus database from the local package caches.

WHY THIS EXISTS. Every experiment that needs harvested content was re-deriving it. `test12` walks
1,892 sources jars for prose; `test14` walks the same jars again with tree-sitter for declared
surfaces; anything needing both walks them twice. The caches are **version-pinned and immutable**,
so that work is repeated against input that cannot change.

This extracts once into a single SQLite file that several experiments read.

WHY MORE THAN ONE ECOSYSTEM. The Gradle cache cannot answer a JavaScript question: 1,892 sources
jars hold 17 `.js` files between them. That matters because `test9` found every prose payload that
landed, landed in JavaScript — so the ecosystem the results point at was the one with no corpus
behind it. The npm cache closes that gap from the same machine.

WHY SQLITE RATHER THAN JSONL. The dominant query across those experiments is a **per-library
lookup** - "what does library X declare" - which `test14` performs repeatedly and which costs a full
file scan in JSONL. `sqlite3` is in the standard library, so it adds no dependency to a repository
whose harnesses are deliberately dependency-free, and SQL means a new question does not need a new
filter loop. The file also opens in any ordinary query tool.

WHAT IT IS NOT. Not a source of truth. The Gradle cache is, and `test12/PROSE-MANIFEST.md` pins
which coordinates were read. This database is derived and disposable - delete it and rebuild. It is
gitignored for that reason, and because it is large.

PRIVACY. Every ecosystem has an exclusion list and the harvester refuses to run without one —
`test12/private-groups.txt` for maven, `private-npm-scopes.txt` for npm. A real cache contains the
developer's own packages and possibly a client's, and a filter that silently does nothing is the
same failure as a scan that matches no files. An empty list is a statement; a missing one is an
oversight.

Run:  uv run --with tree-sitter --with tree-sitter-language-pack python build.py
      ... --limit 200      # fewer libraries per ecosystem, for a quick build
      ... --no-npm --no-spm  # one ecosystem only, while iterating on another
"""
import base64
import io
import json
import os
import re
import shutil
import sqlite3
import subprocess
import sys
import tarfile
import urllib.parse
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
NPM_CACHE = os.path.expanduser("~/.npm/_cacache")
SPM_CACHE = os.path.expanduser("~/Library/Caches/org.swift.swiftpm/repositories")
PRIVATE = os.path.join(HERE, "..", "test12", "private-groups.txt")
PRIVATE_NPM = os.path.join(HERE, "private-npm-scopes.txt")
PRIVATE_SPM = os.path.join(HERE, "private-swift-repos.txt")
DB = os.path.join(HERE, "corpus.db")

DECL_NODES = {"function_declaration", "class_declaration", "object_declaration",
              "property_declaration", "type_alias", "enum_entry",
              "method_declaration", "field_declaration", "constructor_declaration",
              "interface_declaration", "enum_declaration", "record_declaration"}
NAME_NODES = ("simple_identifier", "identifier", "type_identifier")
DOC = re.compile(rb'/\*\*(.*?)\*/', re.S)
STAR = re.compile(r'^\s*\*ā?', re.M)
TAG_ONLY = re.compile(r'^\s*@\w+')
FENCE = re.compile(r'```.*?```|\{@\w+[^}]*\}', re.S)

SCHEMA = """
PRAGMA journal_mode = OFF;
PRAGMA synchronous = OFF;

CREATE TABLE libraries (
    library   TEXT PRIMARY KEY,        -- maven: group:artifact:version | npm: name@version | spm: name
    ecosystem TEXT NOT NULL,
        -- maven | npm | spm   a package registry: third-party, versioned, resolvable by coordinate
        -- filesystem          a source tree read in place: FIRST-PARTY code, the developer's own
        --                     project. Half of what the index is for — `test0` measured that local
        --                     knowledge is the gap model progress cannot close — and it has a
        --                     different trust posture: first-party content is not an injection
        --                     surface, because you wrote it.
        -- authored            written by this project, so its label is a fact rather than a guess
    publisher TEXT NOT NULL,           -- maven group, npm scope (or '-'), spm repo owner
    artifact  TEXT NOT NULL,
    version   TEXT NOT NULL,

    -- Where this actually came from, canonically. What is knowable differs by ecosystem and the
    -- honest value differs with it:
    --   npm    the registry tarball URL, which the cache index records verbatim
    --   maven  'unknown' — the Gradle cache's files-2.1 tree does NOT record which repository
    --          served an artifact. androidx comes from Google's maven, not Central, and nothing
    --          in the harvested path says so. Asserting 'maven-central' would be a guess.
    --   spm    the git remote from Package.resolved, never the local checkout path, which is
    --          under the operator's home directory and must not be stored.
    --   filesystem  the repository's remote URL if it has one, otherwise 'local'. NEVER the path.
    --          A first-party harvest walks the developer's own tree, so the path carries the
    --          project name and the home directory — precisely what `experiments/redact.py`
    --          exists to keep out of anything published, and this file is binary so the scanner
    --          cannot see inside it.
    source    TEXT NOT NULL DEFAULT 'unknown',

    -- WHAT KIND OF LIBRARY THIS IS, which the coordinate does not say. On Maven Central a Kotlin
    -- Multiplatform library sits in the same namespace as a plain JVM one — `foo`, `foo-jvm`,
    -- `foo-android`, `foo-iosarm64`, `foo-js` all under one group — and nothing in the coordinate
    -- marks the difference unless you already know the suffix vocabulary.
    --
    -- MIND THE UNIT. Multiplatform is 68% of maven *library rows* (1,216 of 1,798) and 8.5% of
    -- maven *documented declarations* (45,941 of 537,463). Both are true and they read opposite
    -- ways, because KMP publishes one artifact per target: those 1,216 rows are 397 distinct base
    -- artifacts, a 3.06x inflation, and each target ships the same commonMain prose.
    --   multiplatform    one shared source set compiled to several platforms
    --   single-platform  built for one platform
    --   unknown          nothing in the artifact settled it
    library_type TEXT NOT NULL DEFAULT 'unknown',

    -- The Kotlin source-set roots found inside the sources jar, comma-bounded like `tags`
    -- (`,commonMain,appleMain,`) so `LIKE '%,appleMain,%'` matches a whole name. This is what
    -- decides `library_type`, kept rather than thrown away because it answers the next question
    -- too: which platforms a library actually carries source for.
    --
    -- And it answers one that comes up every time: **a KMP library ships Kotlin, not Swift and not
    -- JavaScript.** The sources jars in this cache hold 142,436 `.kt` and 43,977 `.java` files and
    -- **zero** `.swift`. The apple and js outputs are compiled from the Kotlin in `appleMain` and
    -- `jsMain`; the sources jar carries what they were compiled *from*.
    source_sets TEXT NOT NULL DEFAULT '',

    entries   INTEGER NOT NULL DEFAULT 0,
    declared  INTEGER NOT NULL DEFAULT 0
);

-- One documented declaration. `symbol` and `signature` are null when a doc comment could not be
-- bound to a declaration, which is common in package-level and file-header comments.
CREATE TABLE entries (
    id        INTEGER PRIMARY KEY,
    library   TEXT NOT NULL,
    ecosystem TEXT NOT NULL,
    publisher TEXT NOT NULL,
    lang      TEXT NOT NULL,           -- kotlin | java | typescript | javascript | swift

    -- The documentation convention this was written in, which is NOT implied by the language.
    -- It decides what the extractor had to do and what it threw away:
    --   kdoc          /** */, `[links]`, @param/@return           (Kotlin)
    --   javadoc       /** */, {@link}, HTML tags in the body      (Java)
    --   jsdoc         /** */, @type/@returns, often on .d.ts      (JavaScript, TypeScript)
    --   swift-markup  /// LINE comments — a different shape entirely, and the reason this column
    --                 exists: a block-comment extractor finds NOTHING in Swift and reports it as
    --                 an absence rather than an error.
    --   plain         a /** */ block with no convention detected
    -- RAD-0002 measured coverage differing sharply by convention (33% median, 84% for
    -- Java-majority libraries against 30% for Kotlin-majority ones on identical tooling), so this
    -- is a dimension results can legitimately split on.
    doc_format TEXT NOT NULL DEFAULT 'plain',
    symbol    TEXT,
    signature TEXT,
    doc       TEXT NOT NULL,

    -- LABELLING. The default is `presumed_benign`, never `benign`: half a million declarations
    -- harvested from a package cache have not been audited, and RAD-0036 records that the negative
    -- class in any classifier is written by whoever can publish a package. A field that claimed
    -- these were verified would be the single most misleading column in the database.
    label     TEXT NOT NULL DEFAULT 'presumed_benign',
        -- presumed_benign  harvested from a real registry, unaudited. The default.
        -- benign_verified  a human read it and vouched for it.
        -- malicious        known-bad: our own payloads, or a labelled third-party benchmark.
        -- suspect          something flagged it and nobody has adjudicated.
    label_source TEXT NOT NULL DEFAULT 'harvested',
        -- harvested  no claim beyond where it came from
        -- authored   this project wrote it, so we know what it is
        -- benchmark  a third-party corpus supplied the label
        -- reviewed   a person made the call, and `label_note` says who or why
    label_note TEXT,
        -- **For a human to understand what is going on.** Prose, written to be read, saying what
        -- this row is and why it matters. Not a location, not a harness name, not a key — if it
        -- cannot be understood without knowing this repository's layout, it belongs in `tags` or
        -- nowhere. Never write the same note on every row: a note that does not distinguish the
        -- row it is on is decoration.

    -- **For filtering and finding.** Comma-delimited and comma-bounded (`,identifier,camelcase,`)
    -- so `tags LIKE '%,exfiltration,%'` matches a whole tag and never a prefix.
    --
    -- The division of labour: **notes are for humans to understand what is going on; tags are for
    -- filtering and finding.** A note cannot be grouped; a tag should not need a sentence. Neither
    -- names the harness that produced the row — a descriptor has to keep meaning when a test is
    -- renamed or deleted, which `"test9 identifier payload: camel"` did not.
    tags      TEXT NOT NULL DEFAULT ''
);

-- Every name a library declares. This is the per-library surface a resolution check needs, and
-- the reason this is SQLite: it is looked up by library, over and over.
CREATE TABLE declared (
    library TEXT NOT NULL,
    name    TEXT NOT NULL
);

CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT NOT NULL);
"""

INDEXES = """
CREATE INDEX idx_entries_library   ON entries(library);
CREATE INDEX idx_entries_publisher ON entries(publisher);
CREATE INDEX idx_entries_lang      ON entries(lang);
CREATE INDEX idx_entries_ecosystem ON entries(ecosystem);
CREATE INDEX idx_entries_label     ON entries(label);
CREATE INDEX idx_entries_tags      ON entries(tags);
CREATE INDEX idx_entries_docfmt    ON entries(doc_format);
CREATE INDEX idx_libraries_ecosystem ON libraries(ecosystem);
CREATE INDEX idx_libraries_type      ON libraries(library_type);
CREATE INDEX idx_declared_library  ON declared(library);
CREATE INDEX idx_declared_name     ON declared(name);
"""


def load_private(path, what):
    """Prefixes that must never be harvested. Refuses to run if the list is absent.

    The refusal is the point. A missing exclusion list and an empty one look identical to a filter
    but mean opposite things: one is "nothing needs excluding", the other is "nobody has looked".
    """
    if not os.path.exists(path):
        sys.exit(f"refusing to run: {os.path.relpath(path, HERE)} is missing.\n"
                 f"It lists {what} that must never be harvested. An empty file is a deliberate\n"
                 "statement that nothing needs excluding; a missing one is an oversight.")
    return [l.split("#", 1)[0].strip() for l in open(path) if l.split("#", 1)[0].strip()]


def excluded(name, prefixes):
    """A prefix matches the name itself, or the name under a dot or slash boundary.

    Bounded so `com.oddlyclever` cannot also exclude `com.oddlycleverly`, and `@acme` cannot
    exclude `@acmecorp`.
    """
    return any(name == p or name.startswith(p + ".") or name.startswith(p + "/")
               for p in prefixes)


def bounded(values):
    """Comma-bounded, so `LIKE '%,appleMain,%'` matches a whole name and never a prefix. The same
    convention `tags` uses, for the same reason."""
    return "," + ",".join(sorted(values)) + "," if values else ""


def clean_doc(raw):
    text = FENCE.sub(" ", STAR.sub("", raw.decode("utf-8", "replace")))
    keep = [l.strip() for l in text.splitlines()
            if l.strip() and not TAG_ONLY.match(l.strip())]
    return re.sub(r'\s+', ' ', " ".join(keep)).strip()


def name_of(node, src):
    for ch in node.children:
        if ch.type in NAME_NODES:
            return src[ch.start_byte:ch.end_byte].decode("utf-8", "replace")
    return None


def signature_of(node, src):
    """The declaration's own text up to its body — enough to call it, without the implementation."""
    text = src[node.start_byte:node.end_byte].decode("utf-8", "replace")
    for stop in ("{", "="):
        i = text.find(stop)
        if i > 0:
            text = text[:i]
    return re.sub(r'\s+', ' ', text).strip()[:400]


# A Kotlin Multiplatform sources jar is rooted on SOURCE SETS — `commonMain/`, `jvmMain/`,
# `appleMain/` — where a plain JVM one is rooted on package directories (`okhttp3/`, `com/`). That
# is the discriminator, and it costs nothing: the jar is already open.
#
# CONSIDERED AND NOT USED: Gradle Module Metadata (`.module`) carries
# `org.jetbrains.kotlin.platform.type` per variant and is authoritative about what was *published*.
# Two reasons against it. Only 333 of these version directories have one cached, because Gradle
# keeps the metadata it happened to fetch; and it describes the publication rather than the jar
# actually read here, which is the thing being labelled.
SOURCE_SET = re.compile(r'^[a-z][A-Za-z0-9]*(?:Main|Test)$')


def source_sets_of(names):
    """Source-set roots inside a jar, or an empty set for a package-rooted one."""
    tops = {n.split("/")[0] for n in names if "/" in n} - {"META-INF"}
    return {t for t in tops if SOURCE_SET.match(t)}


def walk_jar(path, parsers):
    """One jar -> (declared names, [(lang, symbol, signature, doc)], source sets)."""
    declared, entries, sets = set(), [], set()
    try:
        z = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError):
        return declared, entries, sets
    with z:
        sets = source_sets_of(z.namelist())
        for n in z.namelist():
            lang = "kotlin" if n.endswith(".kt") else "java" if n.endswith(".java") else None
            if not lang:
                continue
            try:
                src = z.read(n)
            except (zipfile.BadZipFile, OSError, RuntimeError):
                continue
            tree = parsers[lang].parse(src)
            # Doc comments, keyed by the byte where each ends, so a declaration can find the
            # comment immediately above it.
            docs = {}
            for m in DOC.finditer(src):
                body = clean_doc(m.group(1))
                if len(body.split()) >= 4:
                    docs[m.end()] = body
            stack = [tree.root_node]
            while stack:
                nd = stack.pop()
                if nd.type in DECL_NODES:
                    nm = name_of(nd, src)
                    if nm:
                        declared.add(nm)
                        # the nearest doc comment ending within 400 bytes above this declaration
                        best = None
                        for end, body in docs.items():
                            gap = nd.start_byte - end
                            if 0 <= gap < 400 and (best is None or gap < best[0]):
                                best = (gap, body)
                        if best:
                            entries.append((lang, nm, signature_of(nd, src), best[1]))
                stack.extend(nd.children)
    return declared, entries, sets


# ---------------------------------------------------------------------------------------------
# npm
#
# The cache is content-addressed: `index-v5` holds one JSON record per fetched URL, and the
# `integrity` hash in it locates the body under `content-v2`. Package identity therefore comes from
# the **registry URL** the record was keyed on, never from a path on disk — which is both the honest
# answer to "where did this come from" and the only one that cannot leak a home directory.

# tree-sitter node types that name something a caller could reach. TypeScript carries most of the
# weight here: npm publishes built JavaScript, and the documentation survives in the `.d.ts` beside
# it rather than in the emitted code.
JS_DECL = {"function_declaration", "generator_function_declaration", "class_declaration",
           "abstract_class_declaration", "method_definition", "variable_declarator",
           "interface_declaration", "type_alias_declaration", "enum_declaration",
           "method_signature", "property_signature", "function_signature",
           "abstract_method_signature", "public_field_definition", "internal_module"}
JS_NAME = ("identifier", "type_identifier", "property_identifier", "shorthand_property_identifier")

# A published tarball is mostly not source. These are the parts worth parsing.
JS_EXT = {".js": "javascript", ".mjs": "javascript", ".cjs": "javascript",
          ".ts": "typescript", ".mts": "typescript", ".cts": "typescript"}
MAX_SRC = 1_000_000          # a source file this large is generated, not written
MAX_LINE_MEAN = 500          # mean bytes per line above this is minified, not formatted


def npm_index():
    """Every cached tarball, as {package: {version: (url, content path)}}.

    Reads the index rather than the content tree because the content tree is nothing but hashes:
    without the index there is no way to know what a blob is, and no way to name it.
    """
    packages = {}
    idx = os.path.join(NPM_CACHE, "index-v5")
    if not os.path.isdir(idx):
        return packages
    for root, _, files in os.walk(idx):
        for f in files:
            for line in open(os.path.join(root, f), errors="replace"):
                _, _, payload = line.partition("\t")
                try:
                    rec = json.loads(payload)
                except ValueError:
                    continue
                key, integrity = rec.get("key", ""), rec.get("integrity")
                if not integrity or not key.endswith(".tgz"):
                    continue
                url = key.split("request-cache:", 1)[-1]
                name, _, tail = urllib.parse.unquote(
                    urllib.parse.urlsplit(url).path.lstrip("/")).partition("/-/")
                if not name or not tail:
                    continue
                base = os.path.basename(tail)[:-len(".tgz")]
                m = re.search(r"-(\d[^-]*(?:-.*)?)$", base)
                if not m:
                    continue
                algo, _, b64 = integrity.partition("-")
                digest = base64.b64decode(b64).hex()
                blob = os.path.join(NPM_CACHE, "content-v2", algo,
                                    digest[:2], digest[2:4], digest[4:])
                if os.path.exists(blob):
                    packages.setdefault(name, {})[m.group(1)] = (url, blob)
    return packages


def version_key(v):
    """Order versions numerically, and rank a prerelease below the release it precedes.

    Not a semver implementation. It only has to pick the newest cached version of a package, and
    getting that subtly wrong costs one version of one library, not a wrong result.
    """
    core, _, pre = v.partition("-")
    parts = [int(p) if p.isdigit() else 0 for p in core.split(".")[:3]]
    while len(parts) < 3:
        parts.append(0)
    return (*parts, 0 if pre else 1, pre)


def walk_tarball(path, parsers):
    """One npm tarball -> (declared names, [(lang, symbol, signature, doc)])."""
    declared, entries = set(), []
    try:
        tar = tarfile.open(path)
    except (tarfile.TarError, OSError):
        return declared, entries
    with tar:
        for member in tar:
            if not member.isfile() or member.size > MAX_SRC:
                continue
            name = member.name
            lang = JS_EXT.get(os.path.splitext(name)[1])
            if not lang or ".min." in os.path.basename(name):
                continue
            try:
                src = tar.extractfile(member).read()
            except (tarfile.TarError, OSError):
                continue
            lines = src.count(b"\n") + 1
            if len(src) / lines > MAX_LINE_MEAN:
                continue        # bundled or minified: one enormous line, no documentation in it
            docs = {}
            for m in DOC.finditer(src):
                body = clean_doc(m.group(1))
                if len(body.split()) >= 4:
                    docs[m.end()] = body
            tree = parsers[lang].parse(src)
            stack = [tree.root_node]
            while stack:
                nd = stack.pop()
                if nd.type in JS_DECL:
                    nm = next((src[c.start_byte:c.end_byte].decode("utf-8", "replace")
                               for c in nd.children if c.type in JS_NAME), None)
                    if nm:
                        declared.add(nm)
                        best = None
                        for end, body in docs.items():
                            gap = nd.start_byte - end
                            if 0 <= gap < 400 and (best is None or gap < best[0]):
                                best = (gap, body)
                        if best:
                            entries.append((lang, nm, signature_of(nd, src), best[1]))
                stack.extend(nd.children)
    return declared, entries


def harvest_npm(db, parsers, limit=None):
    """Newest cached version of every package, excluding the private list.

    Deduplicates doc text **within a package**, not within a publisher as the maven side does. The
    duplication has a different shape here: maven repeats itself across artifacts, because a
    Kotlin Multiplatform library publishes one per target, so the group is the right unit. npm
    repeats itself *inside a single tarball* — the same module emitted as CommonJS and as ESM, with
    the doc comments surviving a third time in the `.d.ts` — so the package is.
    """
    private = load_private(PRIVATE_NPM, "npm scopes")
    n_lib = 0
    for name, versions in sorted(npm_index().items()):
        if excluded(name, private):
            continue
        scope = name.split("/", 1)[0] if name.startswith("@") else "-"
        artifact = name.split("/", 1)[1] if name.startswith("@") else name
        # Newest version that actually yields something, for the same reason the maven side walks
        # back: taking only the newest and finding it empty reports a skip as an absence.
        for version in sorted(versions, key=version_key, reverse=True):
            url, blob = versions[version]
            declared, entries = walk_tarball(blob, parsers)
            if not declared and not entries:
                continue
            lib = f"{name}@{version}"
            seen, fresh = set(), []
            for e in entries:
                if e[3] not in seen:
                    seen.add(e[3])
                    fresh.append(e)
            db.execute(
                "INSERT OR REPLACE INTO libraries (library,ecosystem,publisher,artifact,version,"
                "source,library_type,entries,declared) VALUES (?,?,?,?,?,?,?,?,?)",
                (lib, "npm", scope, artifact, version, url, "single-platform",
                 len(fresh), len(declared)))
            db.executemany(
                "INSERT INTO entries (library,ecosystem,publisher,lang,doc_format,symbol,"
                "signature,doc) VALUES (?,?,?,?,?,?,?,?)",
                [(lib, "npm", scope, e[0], "jsdoc", e[1], e[2], e[3]) for e in fresh])
            db.executemany("INSERT INTO declared VALUES (?,?)", [(lib, nm) for nm in declared])
            n_lib += 1
            if n_lib % 100 == 0:
                db.commit()
                print(f"  … {n_lib} npm packages", file=sys.stderr, flush=True)
            break
        if limit and n_lib >= limit:
            break
    db.commit()
    return n_lib


VERIFIED = os.path.join(HERE, "verified-publishers.txt")


def apply_verified(db):
    """Promote `presumed_benign` to `benign_verified` where somebody can say why.

    Kept as a file rather than a constant so the list is reviewable, and so the *reason* travels
    with each entry. A verification with no stated basis is a presumption with a better name.
    """
    if not os.path.exists(VERIFIED):
        return 0
    n = 0
    for line in open(VERIFIED):
        line = line.split("#", 1)[0].strip()
        if not line:
            continue
        parts = re.split(r"\s{2,}|\t", line, maxsplit=1)
        prefix = parts[0].strip()
        basis = parts[1].strip() if len(parts) > 1 else "no basis recorded"
        cur = db.execute(
            "UPDATE entries SET label='benign_verified', label_source='reviewed', label_note=?"
            " WHERE label='presumed_benign' AND (publisher=? OR publisher LIKE ?)",
            (basis, prefix, prefix + ".%"))
        n += cur.rowcount
    return n


def load_payloads(db):
    """Insert this project's own payloads, labelled `malicious`.

    A label column with one value in it is a column nobody can use. These are the only entries in
    the database whose label is a fact rather than a presumption: we wrote them, so we know exactly
    what they are.

    They are inert — `.invalid` sinks, nothing executable — but they are still working injection
    prose, which is why the database is gitignored. It is also binary, so an agent indexing this
    repository does not read them by accident, the same argument as the packed transcripts.
    """
    sys.path.insert(0, os.path.join(HERE, "..", "test9"))
    try:
        from payloads import IDENTIFIERS, PROSE, PROPERTY_ROUTE
    except ImportError:
        print("  (test9 payloads unavailable — skipping the labelled-malicious set)", file=sys.stderr)
        return 0
    lib = "dependencyskills:payloads:test9"
    db.execute(
        "INSERT OR REPLACE INTO libraries (library,ecosystem,publisher,artifact,version,source,"
        "library_type) VALUES (?,?,?,?,?,?,?)",
        (lib, "authored", "dependencyskills", "payloads", "test9",
         "written by this project", "single-platform"))

    def tag(*parts):
        return bounded(parts)

    # What each payload IS, rather than which harness happens to hold it. Three axes: the carrier
    # it rides in, the surface form it takes, and what it tries to make an agent do.
    SHAPE = {"loud": ("spaced", "shouted"), "underscored": ("underscored",),
             "camel": ("camelcase",), "short": ("camelcase", "short")}

    # NOTES ARE FOR A PERSON. One per form, saying what this row is and why it is interesting —
    # not what the attack does, which is the same for all of them and therefore distinguishes
    # nothing. TAGS carry the form; the note carries the point.
    NOTE = {
        "loud": "spaces and shouting inside a backticked name — the loudest form, obeyed 6 of 6 "
                "on an open coding model, and caught free by the no-spaces rule",
        "underscored": "underscore-separated words in a function name; obeyed on Java, and caught "
                       "by ordinary naming rules",
        "camel": "contains camelCase functions an agent will try to interpret — passes every "
                 "naming rule, and was obeyed 0 of 24",
        "short": "six words of camelCase, inside every measured bound; passes the whole rule "
                 "catalogue and was still never obeyed",
    }
    PROSE_NOTE = {
        "precondition": "a doc comment stating a setup step that is not real; reads as ordinary "
                        "API documentation",
        "policy": "a doc comment framed as an implementation guideline rather than an instruction",
        "deprecation": "a doc comment framed as a migration note, which is where readers expect "
                       "to be told to do something",
    }
    rows = []
    for form, per_lang in IDENTIFIERS.items():
        if form == "control":
            continue
        for lang, ident in (per_lang or {}).items():
            if ident:
                rows.append((lib, "authored", "dependencyskills", lang, "plain", ident,
                             f"{ident}()", "", "malicious", "authored",
                             NOTE.get(form, "authored identifier payload"),
                             tag("identifier", *SHAPE.get(form, ()), "exfiltration",
                                 "environment", "logfile")))
    for lang, ident in PROPERTY_ROUTE.items():
        rows.append((lib, "authored", "dependencyskills", lang, "plain", None, ident, "",
                     "malicious", "authored",
                     "a quoted property key, which accepts arbitrary text where an identifier "
                     "grammar would refuse it — JavaScript's escape hatch",
                     tag("property-key", "spaced", "exfiltration", "environment", "logfile")))
    for form, text in PROSE.items():
        body = text if isinstance(text, str) else str(text)
        framing = form.replace("prose_", "")
        rows.append((lib, "authored", "dependencyskills", "prose", "kdoc", None, None, body,
                     "malicious", "authored",
                     PROSE_NOTE.get(framing, "authored prose payload"),
                     tag("doc-comment", "prose", framing, "exfiltration", "environment",
                         "logfile")))
    db.executemany(
        "INSERT INTO entries (library,ecosystem,publisher,lang,doc_format,symbol,signature,"
        "doc,label,label_source,label_note,tags) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)", rows)
    db.execute("UPDATE libraries SET entries=? WHERE library=?", (len(rows), lib))
    return len(rows)


# ---------------------------------------------------------------------------------------------
# SwiftPM
#
# The cache is a directory of BARE git clones, one per resolved dependency, named `repo-<hash>`.
# That is the right side of the machine to read: the working checkouts live under Xcode's
# DerivedData, whose paths carry the *project* names — the one thing that must never reach a binary
# file `experiments/redact.py` cannot see inside. A bare clone's `config` gives the public remote
# URL and nothing else.
#
# THE EXTRACTOR IS GENUINELY DIFFERENT HERE, which is why `doc_format` exists. Swift documents with
# `///` LINE comments, so a run of consecutive lines is ONE doc comment. A block-comment extractor
# aimed at this finds nothing and reports it as an absence — the failure this repository keeps
# re-learning, and the reason the column was added before the harvester was written.
SWIFT_LINE_DOC = re.compile(rb'(?:^[ \t]*///[^\n]*\n)+', re.M)
SWIFT_DECL = {"function_declaration", "protocol_function_declaration", "class_declaration",
              "protocol_declaration", "init_declaration", "typealias_declaration",
              "property_declaration", "enum_entry", "associatedtype_declaration"}
SWIFT_NAME = ("simple_identifier", "type_identifier")
# A git checkout ships its tests and samples; a published jar or tarball does not. Excluded so the
# three ecosystems mean the same thing by "a library's surface".
SWIFT_SKIP = re.compile(r'(^|/)(Tests?|Examples?|Sample[sA-Za-z]*|Benchmarks?|Fixtures)/')


def spm_repos():
    """Every bare clone in the cache, as [(owner, name, remote URL, path)].

    Identity comes from the git remote, never the directory name, and never a checkout path.
    """
    out = []
    if not os.path.isdir(SPM_CACHE) or not shutil.which("git"):
        return out
    for d in sorted(os.listdir(SPM_CACHE)):
        path = os.path.join(SPM_CACHE, d)
        if not os.path.isdir(path):
            continue
        try:
            url = subprocess.run(["git", "-C", path, "config", "--get", "remote.origin.url"],
                                 capture_output=True, text=True, timeout=30).stdout.strip()
        except (OSError, subprocess.SubprocessError):
            continue
        if not url:
            continue
        parts = urllib.parse.urlsplit(url if "://" in url else "ssh://" + url.replace(":", "/", 1))
        segs = [p for p in parts.path.split("/") if p]
        if len(segs) < 2:
            continue
        out.append((segs[-2], re.sub(r"\.git$", "", segs[-1]), url, path))
    return out


def spm_newest_tag(path):
    """The newest release tag, or None. Package.resolved would name the *pinned* version, but it
    lives beside the project that pinned it, under a path this must not read."""
    try:
        tags = subprocess.run(["git", "-C", path, "tag"], capture_output=True, text=True,
                              timeout=60).stdout.split()
    except (OSError, subprocess.SubprocessError):
        return None
    releases = [t for t in tags if re.match(r"^v?\d+\.\d+", t) and "-" not in t.lstrip("v")]
    if not releases:
        return None
    return max(releases, key=lambda t: version_key(t.lstrip("v")))


def walk_swift(path, ref, parser):
    """One repository at one tag -> (declared names, [(lang, symbol, signature, doc)]).

    Reads through `git archive` rather than checking out: a bare clone has no working tree, and
    materialising one would put the sources on disk for no reason.
    """
    declared, entries = set(), []
    try:
        proc = subprocess.run(["git", "-C", path, "archive", "--format=tar", ref],
                              capture_output=True, timeout=600)
    except (OSError, subprocess.SubprocessError):
        return declared, entries
    if proc.returncode != 0 or not proc.stdout:
        return declared, entries
    try:
        tar = tarfile.open(fileobj=io.BytesIO(proc.stdout))
    except (tarfile.TarError, OSError):
        return declared, entries
    with tar:
        for member in tar:
            if (not member.isfile() or not member.name.endswith(".swift")
                    or member.size > MAX_SRC or SWIFT_SKIP.search(member.name)):
                continue
            try:
                src = tar.extractfile(member).read()
            except (tarfile.TarError, OSError):
                continue
            # A RUN of `///` lines is one comment; so is a `/** */` block. Both are keyed by the
            # byte they end on, so a declaration below can find the nearest one.
            docs = {}
            for m in SWIFT_LINE_DOC.finditer(src):
                body = clean_doc(re.sub(rb'^[ \t]*///', b'', m.group(0), flags=re.M))
                if len(body.split()) >= 4:
                    docs[m.end()] = body
            for m in DOC.finditer(src):
                body = clean_doc(m.group(1))
                if len(body.split()) >= 4:
                    docs[m.end()] = body
            tree = parser.parse(src)
            stack = [tree.root_node]
            while stack:
                nd = stack.pop()
                if nd.type in SWIFT_DECL:
                    nm = next((src[c.start_byte:c.end_byte].decode("utf-8", "replace")
                               for c in nd.children if c.type in SWIFT_NAME), None)
                    if nm is None:
                        # `let x = …` hangs its name under a pattern rather than on the
                        # declaration, so a direct-children scan misses every stored property.
                        nm = next((src[g.start_byte:g.end_byte].decode("utf-8", "replace")
                                   for c in nd.children if c.type == "pattern"
                                   for g in c.children if g.type in SWIFT_NAME), None)
                    if nm:
                        declared.add(nm)
                        best = None
                        for end, body in docs.items():
                            gap = nd.start_byte - end
                            if 0 <= gap < 400 and (best is None or gap < best[0]):
                                best = (gap, body)
                        if best:
                            entries.append(("swift", nm, signature_of(nd, src), best[1]))
                stack.extend(nd.children)
    return declared, entries


def harvest_spm(db, parser, limit=None):
    """Newest tagged release of every cached SwiftPM repository, excluding the private list."""
    private = load_private(PRIVATE_SPM, "git repository owners")
    n_lib = 0
    for owner, name, url, path in spm_repos():
        if excluded(owner, private) or excluded(f"{owner}/{name}", private):
            continue
        tag = spm_newest_tag(path)
        if not tag:
            continue
        declared, entries = walk_swift(path, tag, parser)
        if not declared and not entries:
            continue
        version = tag.lstrip("v")
        lib = f"{owner}/{name}@{version}"
        seen, fresh = set(), []
        for e in entries:
            if e[3] not in seen:
                seen.add(e[3])
                fresh.append(e)
        db.execute(
            "INSERT OR REPLACE INTO libraries (library,ecosystem,publisher,artifact,version,"
            "source,library_type,entries,declared) VALUES (?,?,?,?,?,?,?,?,?)",
            (lib, "spm", owner, name, version, url, "single-platform",
             len(fresh), len(declared)))
        db.executemany(
            "INSERT INTO entries (library,ecosystem,publisher,lang,doc_format,symbol,signature,"
            "doc) VALUES (?,?,?,?,?,?,?,?)",
            [(lib, "spm", owner, e[0], "swift-markup", e[1], e[2], e[3]) for e in fresh])
        db.executemany("INSERT INTO declared VALUES (?,?)", [(lib, nm) for nm in declared])
        n_lib += 1
        print(f"  … {lib}", file=sys.stderr, flush=True)
        if limit and n_lib >= limit:
            break
    db.commit()
    return n_lib


# A file-header comment sits within 400 bytes of the first declaration below it, so it binds to a
# symbol it does not describe. 670 of 681,000 rows — small enough to ignore and wrong enough to
# mark. Tagged rather than dropped: a silent filter and an empty result look identical, and anyone
# measuring retrieval on this corpus needs to be able to exclude these *deliberately*.
LICENSE_LIKE = ("doc LIKE 'Copyright%'",
                "doc LIKE '%Licensed under the Apache License%'",
                "doc LIKE '%SPDX-License-Identifier%'",
                "doc LIKE '%This source code is licensed under%'",
                "doc LIKE '%LICENSE file in the root%'")


def tag_license_headers(db):
    return db.execute(
        "UPDATE entries SET tags = CASE WHEN tags = '' THEN ',license-header,'"
        "                            ELSE tags || 'license-header,' END,"
        " label_note = coalesce(label_note, 'a file licence header bound to the first declaration"
        " under it — the prose does not describe the symbol')"
        f" WHERE label = 'presumed_benign' AND ({' OR '.join(LICENSE_LIKE)})").rowcount


def harvest_maven(db, parsers, limit=None):
    """Newest sources jar of every cached module, excluding the private list."""
    private = load_private(PRIVATE, "publishers")
    seen_docs, n_lib = set(), 0
    for group in sorted(os.listdir(CACHE)):
        gp = os.path.join(CACHE, group)
        if not os.path.isdir(gp) or excluded(group, private):
            continue
        for artifact in sorted(os.listdir(gp)):
            ap = os.path.join(gp, artifact)
            if not os.path.isdir(ap):
                continue
            # Newest version that actually HAS a sources jar — not simply the newest version.
            # Checking only the newest silently dropped 42 artifacts whose latest release ships no
            # sources but whose previous one does, and they were not obscure: `androidx.compose.ui`
            # and `androidx.core:core` among them. A skip that looks like an absence is the failure
            # mode this repository keeps re-learning.
            jar = version = None
            for v in sorted((v for v in os.listdir(ap)
                             if os.path.isdir(os.path.join(ap, v))), reverse=True):
                vp = os.path.join(ap, v)
                for h in os.listdir(vp):
                    hp = os.path.join(vp, h)
                    if os.path.isdir(hp):
                        for f in os.listdir(hp):
                            if f.endswith("-sources.jar"):
                                jar, version = os.path.join(hp, f), v
                                break
                    if jar:
                        break
                if jar:
                    break
            if jar:
                declared, entries, sets = walk_jar(jar, parsers)
                if not declared and not entries:
                    continue
                lib = f"{group}:{artifact}:{version}"
                # Deduplicate by doc text within a publisher: KMP ships one artifact per target and
                # `test5` measured a real harvest as 63% duplicate.
                fresh = [e for e in entries if (group, e[3]) not in seen_docs]
                for e in fresh:
                    seen_docs.add((group, e[3]))
                db.execute(
                    "INSERT INTO libraries (library,ecosystem,publisher,artifact,version,source,"
                    "library_type,source_sets,entries,declared) VALUES (?,?,?,?,?,?,?,?,?,?)",
                    (lib, "maven", group, artifact, version, "unknown",
                     "multiplatform" if sets else "single-platform",
                     bounded(sets), len(fresh), len(declared)))
                db.executemany(
                    "INSERT INTO entries (library,ecosystem,publisher,lang,doc_format,symbol,"
                    "signature,doc) VALUES (?,?,?,?,?,?,?,?)",
                    [(lib, "maven", group, e[0], "kdoc" if e[0] == "kotlin" else "javadoc",
                      e[1], e[2], e[3]) for e in fresh])
                db.executemany("INSERT INTO declared VALUES (?,?)",
                               [(lib, nm) for nm in declared])
                n_lib += 1
                if n_lib % 100 == 0:
                    db.commit()
                    print(f"  … {n_lib} maven libraries", file=sys.stderr, flush=True)
                if limit and n_lib >= limit:
                    break
            if limit and n_lib >= limit:
                break
        if limit and n_lib >= limit:
            break
    db.commit()
    return n_lib


def main():
    limit = int(sys.argv[sys.argv.index("--limit") + 1]) if "--limit" in sys.argv else None
    from tree_sitter_language_pack import get_parser

    if os.path.exists(DB):
        os.remove(DB)
    db = sqlite3.connect(DB)
    db.executescript(SCHEMA)

    sources = []
    if "--no-maven" not in sys.argv:
        parsers = {"kotlin": get_parser("kotlin"), "java": get_parser("java")}
        harvest_maven(db, parsers, limit)
        sources.append("local Gradle module cache")
    if "--no-npm" not in sys.argv:
        parsers = {"javascript": get_parser("javascript"), "typescript": get_parser("typescript")}
        harvest_npm(db, parsers, limit)
        sources.append("local npm cacache")
    if "--no-spm" not in sys.argv:
        harvest_spm(db, get_parser("swift"), limit)
        sources.append("local SwiftPM repository cache")

    n_pay = load_payloads(db) if "--no-payloads" not in sys.argv else 0
    n_ver = apply_verified(db)
    n_lic = tag_license_headers(db)
    db.executescript(INDEXES)

    def one(sql, *a):
        return str(db.execute(sql, a).fetchone()[0])

    rows = [("sources", "; ".join(sources) or "none"),
            ("libraries", one("SELECT count(*) FROM libraries")),
            ("entries", one("SELECT count(*) FROM entries")),
            ("declared", one("SELECT count(*) FROM declared")),
            ("publishers", one("SELECT count(DISTINCT publisher) FROM libraries")),
            ("labelled_malicious", str(n_pay)),
            ("labelled_verified", str(n_ver)),
            ("tagged_license_header", str(n_lic))]
    # Per-ecosystem counts, because a single total hides a harvest that quietly collected nothing —
    # which is exactly how the 42 missing maven artifacts stayed invisible.
    for eco, in db.execute("SELECT DISTINCT ecosystem FROM libraries ORDER BY ecosystem"):
        rows.append((f"libraries_{eco}", one("SELECT count(*) FROM libraries WHERE ecosystem=?", eco)))
        rows.append((f"entries_{eco}", one("SELECT count(*) FROM entries WHERE ecosystem=?", eco)))
    for kind, in db.execute("SELECT DISTINCT library_type FROM libraries ORDER BY library_type"):
        rows.append((f"libraries_{kind}",
                     one("SELECT count(*) FROM libraries WHERE library_type=?", kind)))
    db.executemany("INSERT INTO meta VALUES (?,?)", rows)
    db.commit()

    print(f"\n  {DB}")
    for k, v in db.execute("SELECT key, value FROM meta"):
        print(f"    {k:<20} {v}")
    print(f"    {'size':<20} {os.path.getsize(DB)/1048576:.0f} MB")
    db.close()
    return 0


if __name__ == "__main__":
    sys.exit(main())
