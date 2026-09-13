#!/usr/bin/env python3
"""Retrieval at PACKAGE granularity, with top-level functions indexed too.

Suggested by the Gemini sharing this station, in answer to the library-level
negative result: knowing the library and its primary abstraction is most of
discovery, and grouping by file or package would bridge the gap for top-level
functions like `delay` without any model.

Two changes from libindex.py, which failed:
  - the unit is a PACKAGE, not a library. Packages are far more uniform in size
    than libraries, which is the direct attack on the size bias that made
    `guava` and `mongodb-driver-core` universal attractors.
  - top-level functions are indexed beside types, so `delay`, `withTimeout` and
    `awaitAll` are findable rather than invisible.
"""

import re
import sqlite3
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

import codex
import libindex

DB = Path.home() / ".minicodex/packages.db"
TYPE_DECL = re.compile(r"\b(?:class|object|interface|record|enum)\s+([A-Za-z_]\w*)")

SCHEMA = """
CREATE TABLE IF NOT EXISTS package (
  id INTEGER PRIMARY KEY, coordinate TEXT, package TEXT, description TEXT,
  types TEXT, functions TEXT, member_count INTEGER,
  UNIQUE (coordinate, package)
);
CREATE VIRTUAL TABLE IF NOT EXISTS package_fts USING fts5(
  package, description, types, functions,
  content='package', content_rowid='id', tokenize='porter unicode61'
);
CREATE TRIGGER IF NOT EXISTS pkg_ai AFTER INSERT ON package BEGIN
  INSERT INTO package_fts(rowid, package, description, types, functions)
  VALUES (new.id, new.package, new.description, new.types, new.functions);
END;
"""


def packages_of(jar):
    """(package, types, functions) for one artifact, from its sources jar."""
    types = defaultdict(list)
    functions = defaultdict(list)
    with zipfile.ZipFile(jar) as zf:
        for entry in zf.namelist():
            if not (entry.endswith(".kt") or entry.endswith(".java")):
                continue
            lang = "kotlin" if entry.endswith(".kt") else "java"
            try:
                source = zf.read(entry).decode("utf-8", "replace")
            except (KeyError, OSError):
                continue
            found = codex.PACKAGE.search(source)
            package = found.group(1) if found else ""
            if not package:
                continue
            for symbol, signature, doc, _ in codex.extract(source, lang):
                short = symbol.split(".")[-1]
                if TYPE_DECL.search(signature):
                    types[package].append(f"{short}. {doc}")
                elif symbol.count(".") == package.count(".") + 1:
                    # No container between the package and the name: a top-level
                    # declaration. These are what the type-only index could not see.
                    functions[package].append(f"{short}. {doc}")
    return types, functions


def build(limit):
    DB.parent.mkdir(parents=True, exist_ok=True)
    db = sqlite3.connect(DB)
    db.executescript(SCHEMA)
    seen = 0
    for coordinate, version_dir, artifact, version, jar in libindex.discover(limit):
        pom = libindex.pom_text(version_dir, artifact, version)
        d = libindex.DESC.search(pom)
        description = " ".join(d.group(1).split()) if d else ""
        try:
            types, functions = packages_of(jar)
        except (zipfile.BadZipFile, OSError):
            continue
        for package in set(types) | set(functions):
            t, f = types.get(package, []), functions.get(package, [])
            db.execute(
                "INSERT OR IGNORE INTO package"
                " (coordinate, package, description, types, functions, member_count)"
                " VALUES (?, ?, ?, ?, ?, ?)",
                (coordinate, package, description, " ".join(t), " ".join(f), len(t) + len(f)),
            )
        seen += 1
        if seen % 25 == 0:
            print(f"  {seen} artifacts...", flush=True)
    db.commit()
    return db


def search(need, db, limit=5):
    terms = [w.lower() for w in codex.WORD.findall(need) if w.lower() not in codex.STOP]
    if not terms:
        return []
    return db.execute(
        "SELECT p.coordinate, p.package, p.member_count, bm25(package_fts) s"
        " FROM package_fts JOIN package p ON p.id = package_fts.rowid"
        " WHERE package_fts MATCH ? ORDER BY s LIMIT ?",
        (" OR ".join(terms), limit),
    ).fetchall()


if __name__ == "__main__":
    if len(sys.argv) > 1 and sys.argv[1] == "build":
        db = build(int(sys.argv[2]) if len(sys.argv) > 2 else 150)
        n, mean = db.execute(
            "SELECT COUNT(*), AVG(member_count) FROM package").fetchone()
        print(f"{n} packages indexed, mean {mean:.0f} members each")
    else:
        db = sqlite3.connect(DB)
        for c, p, n, s in search(" ".join(sys.argv[1:]), db):
            print(f"  {p}  ({n} members)  <- {c}")
