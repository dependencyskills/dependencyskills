#!/usr/bin/env python3
"""Does lexical retrieval work when the unit of the answer is the LIBRARY?

RAD-0070 measured lexical over members at recall@1 of 0 of 5 and found it
landing in the right declaring scope while failing to pick within it. If the
thing handed back is a library rather than a member, that failure stops
mattering — this is the test of whether that is true.

One document per library, assembled with no model at all from two authored
sources: the POM <description>, and the doc comments of the library's public
TYPES (not its members). Standard library only.
"""

import re
import sqlite3
import sys
import zipfile
from pathlib import Path

import codex  # the member-level harvester, reused for its extractor

CACHE = Path.home() / ".gradle/caches/modules-2/files-2.1"
DB = Path.home() / ".minicodex/libraries.db"

DESC = re.compile(r"<description>(.*?)</description>", re.S)
NAME = re.compile(r"<name>(.*?)</name>", re.S)
TYPE_DECL = re.compile(r"\b(?:class|object|interface|record|enum)\s+([A-Za-z_]\w*)")

SCHEMA = """
CREATE TABLE IF NOT EXISTS library (
  id INTEGER PRIMARY KEY, coordinate TEXT UNIQUE, name TEXT, description TEXT,
  types TEXT, type_count INTEGER
);
CREATE VIRTUAL TABLE IF NOT EXISTS library_fts USING fts5(
  coordinate, name, description, types,
  content='library', content_rowid='id', tokenize='porter unicode61'
);
CREATE TRIGGER IF NOT EXISTS lib_ai AFTER INSERT ON library BEGIN
  INSERT INTO library_fts(rowid, coordinate, name, description, types)
  VALUES (new.id, new.coordinate, new.name, new.description, new.types);
END;
"""


def pom_text(version_dir, artifact, version):
    for hashed in version_dir.iterdir():
        pom = hashed / f"{artifact}-{version}.pom"
        if pom.is_file():
            try:
                return pom.read_text("utf-8", "replace")
            except OSError:
                return ""
    return ""


def type_docs(jar, cap=400):
    """Doc comments attached to TYPE declarations only. The library's shape,
    not its API surface — a few hundred words instead of a few thousand."""
    out = []
    with zipfile.ZipFile(jar) as zf:
        for entry in zf.namelist():
            if not (entry.endswith(".kt") or entry.endswith(".java")):
                continue
            lang = "kotlin" if entry.endswith(".kt") else "java"
            try:
                source = zf.read(entry).decode("utf-8", "replace")
            except (KeyError, OSError):
                continue
            for symbol, signature, doc, _ in codex.extract(source, lang):
                if TYPE_DECL.search(signature):
                    out.append(f"{symbol.split('.')[-1]}. {doc}")
                    if len(out) >= cap:
                        return out
    return out


def discover(limit):
    """Every cached coordinate whose sources jar is present."""
    found = []
    for jar in CACHE.rglob("*-sources.jar"):
        try:
            version_dir = jar.parent.parent
            artifact, version = version_dir.parent.name, version_dir.name
            group = version_dir.parent.parent.name
        except (OSError, IndexError):
            continue
        if jar.name != f"{artifact}-{version}-sources.jar":
            continue
        found.append((f"{group}:{artifact}:{version}", version_dir, artifact, version, jar))
        if len(found) >= limit:
            break
    return found


def build(limit):
    DB.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB)
    db.executescript(SCHEMA)
    built = 0
    for coordinate, version_dir, artifact, version, jar in discover(limit):
        if db.execute("SELECT 1 FROM library WHERE coordinate = ?", (coordinate,)).fetchone():
            continue
        pom = pom_text(version_dir, artifact, version)
        d = DESC.search(pom)
        n = NAME.search(pom)
        description = " ".join(d.group(1).split()) if d else ""
        name = " ".join(n.group(1).split()) if n else artifact
        try:
            types = type_docs(jar)
        except (zipfile.BadZipFile, OSError):
            continue
        db.execute(
            "INSERT OR IGNORE INTO library (coordinate, name, description, types, type_count)"
            " VALUES (?, ?, ?, ?, ?)",
            (coordinate, name, description, " ".join(types), len(types)),
        )
        built += 1
        if built % 25 == 0:
            print(f"  {built} libraries...", flush=True)
    db.commit()
    return db, built


def search(need, db, limit=5):
    terms = [w.lower() for w in codex.WORD.findall(need) if w.lower() not in codex.STOP]
    if not terms:
        return []
    return db.execute(
        "SELECT l.coordinate, l.description, l.type_count, bm25(library_fts) s"
        " FROM library_fts JOIN library l ON l.id = library_fts.rowid"
        " WHERE library_fts MATCH ? ORDER BY s LIMIT ?",
        (" OR ".join(terms), limit),
    ).fetchall()


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "build":
        n = int(sys.argv[2]) if len(sys.argv) > 2 else 150
        db, built = build(n)
        total = db.execute("SELECT COUNT(*) FROM library").fetchone()[0]
        withdesc = db.execute(
            "SELECT COUNT(*) FROM library WHERE description <> ''").fetchone()[0]
        print(f"{total} libraries indexed ({built} new), {withdesc} with a POM description")
    else:
        db = sqlite3.connect(DB)
        for row in search(" ".join(sys.argv[1:]), db):
            print(f"\n{row[0]}   ({row[2]} types)\n  {row[1][:140]}")
