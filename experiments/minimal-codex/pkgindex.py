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

And one addition from RAD-0073: a package's authored skill. A library can ship a
`skill-info.kt` or `skill-info.java` in any package, holding nothing but the
package line and a doc comment. Its text is kept AS WRITTEN — only the comment
markers are removed — searched with the rest of the package, and handed back
verbatim by `skill`. No rewrite and no screen, on purpose: see RAD-0074's
amendment for why delivery comes before protection.
"""

import os
import re
import sqlite3
import sys
import zipfile
from collections import defaultdict
from pathlib import Path

import codex
import libindex

DB = Path(os.environ.get("MINICODEX", Path.home() / ".minicodex")) / "packages.db"
TYPE_DECL = re.compile(r"\b(?:class|object|interface|record|enum)\s+([A-Za-z_]\w*)")

SCHEMA = """
CREATE TABLE IF NOT EXISTS package (
  id INTEGER PRIMARY KEY, coordinate TEXT, package TEXT, description TEXT,
  types TEXT, functions TEXT, member_count INTEGER, skill TEXT,
  UNIQUE (coordinate, package)
);
CREATE VIRTUAL TABLE IF NOT EXISTS package_fts USING fts5(
  package, description, types, functions, skill,
  content='package', content_rowid='id', tokenize='porter unicode61'
);
CREATE TRIGGER IF NOT EXISTS pkg_ai AFTER INSERT ON package BEGIN
  INSERT INTO package_fts(rowid, package, description, types, functions, skill)
  VALUES (new.id, new.package, new.description, new.types, new.functions, new.skill);
END;
"""


SKILL_FILE = re.compile(r"(?:^|/)skill-info\.(?:kt|java)$")


def skill_text(source):
    """The skill as its author wrote it: comment markers and the package line gone, nothing else.

    Lines, blank lines and wording are kept exactly, including anything that reads
    as an instruction — that is what a skill is.
    """
    lines = []
    for line in source.splitlines():
        if re.match(r"^\s*(?:/\*\*|\*/)\s*$", line) or re.match(r"^\s*package\s+[\w.]+;?\s*$", line):
            continue
        lines.append(re.sub(r"^\s*\*(?!/) ?", "", line))
    return "\n".join(lines).strip("\n")


def packages_of(jar):
    """(types, functions, skills) by package for one artifact, from its sources jar."""
    types = defaultdict(list)
    functions = defaultdict(list)
    skills = {}
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
            if SKILL_FILE.search(entry):
                # Matched by name, before extraction: a skill has no declaration, so
                # the extractor would bind its comment to nothing and drop it (#43).
                skills[package] = skill_text(source)
                continue
            for symbol, signature, doc, _ in codex.extract(source, lang):
                short = symbol.split(".")[-1]
                if TYPE_DECL.search(signature):
                    types[package].append(f"{short}. {doc}")
                elif symbol.count(".") == package.count(".") + 1:
                    # No container between the package and the name: a top-level
                    # declaration. These are what the type-only index could not see.
                    functions[package].append(f"{short}. {doc}")
    return types, functions, skills


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
            types, functions, skills = packages_of(jar)
        except (zipfile.BadZipFile, OSError):
            continue
        for package in set(types) | set(functions) | set(skills):
            t, f = types.get(package, []), functions.get(package, [])
            db.execute(
                "INSERT OR IGNORE INTO package"
                " (coordinate, package, description, types, functions, member_count, skill)"
                " VALUES (?, ?, ?, ?, ?, ?, ?)",
                (coordinate, package, description, " ".join(t), " ".join(f), len(t) + len(f),
                 skills.get(package)),
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
        "SELECT p.coordinate, p.package, p.member_count, p.skill IS NOT NULL, bm25(package_fts) s"
        " FROM package_fts JOIN package p ON p.id = package_fts.rowid"
        " WHERE package_fts MATCH ? ORDER BY s LIMIT ?",
        (" OR ".join(terms), limit),
    ).fetchall()


def skill(package, db):
    """Every distinct authored skill for a package, verbatim, with the coordinates carrying it.

    A multiplatform library publishes the same commonMain file in its root and every
    target's sources jar, so identical text is one skill with several sources — while
    two versions whose skills differ stay two answers.
    """
    by_text = {}
    for coordinate, text in db.execute(
        "SELECT coordinate, skill FROM package WHERE package = ? AND skill IS NOT NULL"
        " ORDER BY coordinate", (package,)):
        by_text.setdefault(text, []).append(coordinate)
    return [(coordinates, text) for text, coordinates in by_text.items()]


if __name__ == "__main__":
    if len(sys.argv) > 2 and sys.argv[1] == "skill":
        rows = skill(sys.argv[2], sqlite3.connect(DB))
        if not rows:
            print(f"no skill-info for {sys.argv[2]}")
        for coordinates, text in rows:
            print(f"--- {sys.argv[2]}  <- {', '.join(coordinates)}\n{text}\n")
    elif len(sys.argv) > 1 and sys.argv[1] == "build":
        db = build(int(sys.argv[2]) if len(sys.argv) > 2 else 150)
        n, mean, with_skill = db.execute(
            "SELECT COUNT(*), AVG(member_count), COUNT(skill) FROM package").fetchone()
        print(f"{n} packages indexed, mean {mean:.0f} members each, {with_skill} with a skill-info")
    else:
        db = sqlite3.connect(DB)
        for c, p, n, has_skill, s in search(" ".join(sys.argv[1:]), db):
            print(f"  {p}  ({n} members{', has a skill' if has_skill else ''})  <- {c}")
