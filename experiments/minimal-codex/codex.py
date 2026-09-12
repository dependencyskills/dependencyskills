#!/usr/bin/env python3
"""The smallest thing that works — RAD-0070, built to find out whether it does.

Standard library only, on purpose. If this scores well enough at one project's
corpus size, the embedding model in RAD-0070's design is unnecessary and the
whole tool is stdlib. That is the open question this exists to settle.

Deliberately absent, and NOT an oversight: the summariser, the classifier, the
verification rules and the bytecode visibility oracle. Raw third-party
documentation reaches the caller verbatim. See RAD-0070 for what that costs.
"""

import os
import re
import sqlite3
import sys
import zipfile
from pathlib import Path

STORE = Path(os.environ.get("MINICODEX", Path.home() / ".minicodex")) / "codex.db"

# ---------------------------------------------------------------- the store

SCHEMA = """
CREATE TABLE IF NOT EXISTS entry (
  id         INTEGER PRIMARY KEY,
  coordinate TEXT NOT NULL,
  symbol     TEXT NOT NULL,
  signature  TEXT NOT NULL,
  doc        TEXT NOT NULL,
  lang       TEXT NOT NULL,
  UNIQUE (coordinate, symbol, signature)
);
CREATE VIRTUAL TABLE IF NOT EXISTS entry_fts USING fts5(
  symbol_text, doc, content='entry', content_rowid='id', tokenize='porter unicode61'
);
CREATE TRIGGER IF NOT EXISTS entry_ai AFTER INSERT ON entry BEGIN
  INSERT INTO entry_fts(rowid, symbol_text, doc) VALUES (new.id, new.symbol, new.doc);
END;
CREATE TRIGGER IF NOT EXISTS entry_ad AFTER DELETE ON entry BEGIN
  INSERT INTO entry_fts(entry_fts, rowid, symbol_text, doc)
  VALUES ('delete', old.id, old.symbol, old.doc);
END;
"""


def store():
    STORE.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(STORE)
    db.executescript(SCHEMA)
    return db


# ------------------------------------------------------------- finding a jar

GRADLE_CACHE = "caches/modules-2/files-2.1"


def sources_jar(coordinate):
    """The -sources.jar the build system already downloaded, or None.

    Sources are published on the coordinate the build resolves, including a
    multiplatform common module — unlike the classes jar, which lives under the
    `-jvm` sibling. That asymmetry cost the Kotlin implementation 281 leaked
    entries; it does not arise here because nothing reads bytecode.
    """
    parts = coordinate.split(":")
    if len(parts) != 3:
        return None
    group, artifact, version = parts
    home = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")

    def look(name):
        version_dir = home / GRADLE_CACHE / group / name / version
        if not version_dir.is_dir():
            return None
        wanted = f"{name}-{version}-sources.jar"
        for hashed in version_dir.iterdir():
            candidate = hashed / wanted
            if candidate.is_file():
                return candidate
        return None

    # Some multiplatform libraries publish sources on the common coordinate
    # (kotlinx-coroutines-core does) and some only on the platform variants
    # (okio does not). Measured, not assumed — so try the sibling too.
    return look(artifact) or (None if artifact.endswith("-jvm") else look(artifact + "-jvm"))


# --------------------------------------------------------------- extraction

DOC = re.compile(r"/\*\*(.*?)\*/", re.DOTALL)
PACKAGE = re.compile(r"^\s*package\s+([\w.]+)", re.MULTILINE)
SKIP = re.compile(r"^\s*(?:@|//|/\*|\*|$)")

# Enough of a declaration to name it. Not a parser: the first keyword that
# introduces something callable, then the identifier after it.
KEYWORDS = "fun|val|var|class|object|interface|enum|annotation|typealias|record"

NAMED = re.compile(
    r"\b(?:fun|val|var|class|object|interface|enum\s+class|annotation\s+class|"
    r"typealias|record|enum|@interface)\s+"
    r"(?:<[^>]*>\s*)?"          # a generic parameter list before the name
    r"(?:[\w.<>?\[\]]+\.)?"      # an extension receiver
    # Never capture another keyword as the name. Kotlin's `fun interface Foo`
    # otherwise yields the symbol `<package>.interface`, which is not a
    # declaration and pollutes any grouping done on the symbol — measured at
    # 25 of 13,866 entries before this guard.
    rf"(?!(?:{KEYWORDS})\b)"
    r"([A-Za-z_]\w*)"
)
CONTAINER = re.compile(r"\b(?:class|object|interface|record|enum)\s+([A-Za-z_]\w*)")

MODIFIERS = {
    "public", "protected", "abstract", "final", "open", "sealed", "data", "inline",
    "value", "expect", "actual", "override", "suspend", "external", "tailrec",
    "operator", "infix", "const", "lateinit", "inner", "companion", "annotation",
    "enum", "static", "synchronized", "native", "strictfp", "transient", "volatile",
    "default", "vararg", "crossinline", "noinline", "reified",
}
LEADING_ANNOTATIONS = re.compile(r"^(?:@[\w.]+(?:\([^)]*\))?\s+)*")


def declares_non_public(signature):
    """Whether the declaration says, in a word, that nobody outside can reach it.

    Ported from the Kotlin harvester. Only the run of modifiers that OPENS the
    declaration counts: `public class Uuid private constructor(...)` is public
    API whose constructor is not, and a rule that searched the whole string
    would delete the class.

    This is the cheap half of the visibility rule. The other half reads the
    compiled artifact and catches declarations that are unreachable without
    saying so — 476 of them on the corpus RAD-0070 measured. Those are kept
    here, knowingly.
    """
    for token in LEADING_ANNOTATIONS.sub("", signature.strip()).split():
        if token in ("private", "internal"):
            return True
        if token not in MODIFIERS:
            return False
    return False


def clean(block):
    """A doc comment's prose, with its leading asterisks and tags removed."""
    lines = []
    for line in block.splitlines():
        line = re.sub(r"^\s*\*+", "", line).strip()
        if line.startswith("@"):      # @param, @return, @throws — not the description
            break
        lines.append(line)
    return " ".join(w for w in " ".join(lines).split() if w).strip()


def extract(source, lang):
    """Doc-comment/declaration pairs from one file.

    A comment binds to the next non-blank line that is not itself an annotation
    or another comment. That is the naive rule, and the Kotlin harvester records
    it mis-binding 670 times in 681,000 — about a tenth of a percent, which is
    the price of not carrying a parser.
    """
    found = PACKAGE.search(source)
    package = found.group(1) if found else ""
    out = []
    container = None
    for match in DOC.finditer(source):
        body = clean(match.group(1))
        if len(body.split()) < 5:      # too short to retrieve on
            continue
        rest = source[match.end():]
        signature = ""
        for line in rest.splitlines():
            if SKIP.match(line):
                continue
            signature = line.strip().rstrip("{").strip()
            break
        if not signature:
            continue
        if declares_non_public(signature):
            continue
        named = NAMED.search(signature)
        if not named:
            continue
        name = named.group(1)
        held = CONTAINER.search(signature)
        if held:
            container = name
            symbol = f"{package}.{name}" if package else name
        else:
            parts = [p for p in (package, container, name) if p]
            symbol = ".".join(parts)
        out.append((symbol, signature, body, lang))
    return out


LANGS = {".kt": "kotlin", ".java": "java"}


def harvest(coordinate, db):
    jar = sources_jar(coordinate)
    if jar is None:
        return 0, "no sources jar in the build cache"
    with zipfile.ZipFile(jar) as zf:
        for name in zf.namelist():
            lang = LANGS.get(next((e for e in LANGS if name.endswith(e)), ""), None)
            if lang is None:
                continue
            try:
                source = zf.read(name).decode("utf-8", "replace")
            except (KeyError, OSError):
                continue
            for symbol, signature, doc, lang_ in extract(source, lang):
                try:
                    db.execute(
                        "INSERT OR IGNORE INTO entry (coordinate, symbol, signature, doc, lang)"
                        " VALUES (?, ?, ?, ?, ?)",
                        (coordinate, symbol, signature, doc, lang_),
                    )
                except sqlite3.Error:
                    continue
    db.commit()
    n = db.execute("SELECT COUNT(*) FROM entry WHERE coordinate = ?", (coordinate,)).fetchone()[0]
    return n, jar.name


# ------------------------------------------------------------------- search

WORD = re.compile(r"[A-Za-z][A-Za-z0-9]+")
STOP = {
    "the", "a", "an", "and", "or", "of", "to", "in", "on", "for", "with", "that",
    "this", "it", "is", "are", "be", "as", "at", "by", "from", "so", "only",
    "when", "how", "do", "i", "want", "need", "get", "one", "all", "into",
}


def search(need, db, limit=10, coordinates=None):
    terms = [w.lower() for w in WORD.findall(need) if w.lower() not in STOP]
    if not terms:
        return []
    query = " OR ".join(terms)
    sql = (
        "SELECT e.symbol, e.signature, e.doc, e.coordinate, bm25(entry_fts) AS score"
        " FROM entry_fts JOIN entry e ON e.id = entry_fts.rowid"
        " WHERE entry_fts MATCH ?"
    )
    args = [query]
    if coordinates:
        sql += " AND e.coordinate IN (%s)" % ",".join("?" * len(coordinates))
        args += list(coordinates)
    sql += " ORDER BY score LIMIT ?"
    args.append(limit)
    return db.execute(sql, args).fetchall()


# --------------------------------------------------------------------- main

def main(argv):
    if len(argv) < 2:
        print(__doc__.strip().splitlines()[0])
        print("\nusage: codex.py index <coordinate>... | search <need> | stats")
        return 2
    db = store()
    command = argv[1]
    if command == "index":
        for coordinate in argv[2:]:
            n, note = harvest(coordinate, db)
            print(f"{coordinate}: {n} entries ({note})")
    elif command == "search":
        rows = search(" ".join(argv[2:]), db)
        if not rows:
            print("nothing matched")
        for symbol, signature, doc, coordinate, score in rows:
            print(f"\n{symbol}\n  {signature}\n  {doc[:160]}\n  {coordinate}")
    elif command == "stats":
        total = db.execute("SELECT COUNT(*) FROM entry").fetchone()[0]
        print(f"{total} entries at {STORE}")
        for row in db.execute(
            "SELECT coordinate, COUNT(*) c FROM entry GROUP BY coordinate ORDER BY c DESC"
        ):
            print(f"  {row[1]:6}  {row[0]}")
    else:
        print(f"unknown command: {command}")
        return 2
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv))
