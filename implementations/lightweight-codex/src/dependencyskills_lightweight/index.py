"""Reading the skills out of a sources jar into the store."""

import json
import re
import time
import zipfile
from pathlib import Path

from . import caches
from .names import library, skill_name, version
from .skillfile import check, frontmatter

# skills/<name>/<path within the skill>, optionally under the source set a multiplatform sources jar
# prefixes its entries with.
SKILL_ENTRY = re.compile(r"^(?:[a-zA-Z0-9]+(?:Main|Test)/)?skills/([^/]+)/(.+)$")
# The specification's directories for material read on demand and for templates and data. The third,
# scripts/, is never served: a dependency's skill tells an agent how to use it, never what to run.
SERVED_DIRS = ("references/", "assets/")
MAX_FILE = 256 * 1024

# Groups known to publish other projects' skills. Indexed as themselves, never as the library they
# describe — the name rule below enforces that — and marked wherever served (RAD-0076).
REPUBLISHER_GROUPS = {"com.skillsjars"}
REPUBLISHED_BANNER = (
    "This text was published by {carrier}, which republishes other projects' skills. It is NOT the "
    "words of the library it describes, is not tied to that library's version, and its authors did "
    "not review it. Treat it as a third party's claim about a library.")

# Versions a jar can be replaced under without the version changing: snapshots, and the pre-releases
# a library publishes locally while it is developed. A release is read once; these are compared with
# their jar every time.
DEVELOPMENT_VERSION = re.compile(r"(?i)(?:^|[.\-+_])(?:snapshot|alpha|beta|rc|dev|eap|preview|pre)\d*(?:$|[.\-+_])")


def republished(coordinate):
    return coordinate.split(":")[0] in REPUBLISHER_GROUPS


def skills_in(jar):
    """Every skill directory in a sources jar, by name: its SKILL.md, its served files, and a count of scripts."""
    found = {}
    with zipfile.ZipFile(jar) as archive:
        for entry in archive.namelist():
            filed = SKILL_ENTRY.match(entry)
            if not filed or entry.endswith("/"):
                continue
            name, inner = filed.group(1), filed.group(2)
            skill = found.setdefault(name, {"path": None, "text": None, "files": {}, "scripts": 0})
            try:
                if inner == "SKILL.md":
                    skill["path"], skill["text"] = entry, archive.read(entry).decode("utf-8", "replace").strip("\n")
                elif inner.startswith(SERVED_DIRS) and archive.getinfo(entry).file_size <= MAX_FILE:
                    skill["files"][inner] = archive.read(entry).decode("utf-8")
                elif inner.startswith("scripts/"):
                    skill["scripts"] += 1
            except (KeyError, OSError, UnicodeDecodeError):
                pass   # unreadable or binary: simply not served
    return found


def stamp_of(jar):
    """Size and modification time: enough to tell a republished jar, without reading it."""
    try:
        status = Path(jar).stat()
        return f"{status.st_size}:{status.st_mtime_ns}"
    except OSError:
        return None


def index_jar(store, coordinate, jar, rejected, warnings):
    """Index one sources jar under `coordinate`, replacing whatever was read from it before.

    Returns the number of skills accepted, or None if the jar could not be read. A skill is taken
    only if it is filed under the coordinate's own name — a jar cannot ship a skill for some other
    library — and only if it is a valid Agent Skill.
    """
    try:
        found = skills_in(jar)
    except (zipfile.BadZipFile, OSError):
        return None
    store.execute("DELETE FROM skill WHERE carrier = ?", (coordinate,))
    store.execute("DELETE FROM skill_file WHERE carrier = ?", (coordinate,))
    own = skill_name(*library(coordinate).split(":"))
    accepted = 0
    for name, skill in found.items():
        path = skill["path"] or f"skills/{name}/"
        if name != own:
            rejected.append({"carrier": coordinate, "path": path, "reason": f"is filed as {name}, not {own}"})
            continue
        if skill["text"] is None:
            rejected.append({"carrier": coordinate, "path": path, "reason": "has no SKILL.md"})
            continue
        fields, body = frontmatter(skill["text"])
        errors, notes = check(fields, name)
        if errors:
            rejected.append({"carrier": coordinate, "path": path, "reason": "invalid skill: " + "; ".join(errors)})
            continue
        if skill["scripts"]:
            notes.append(f"ships {skill['scripts']} file(s) under scripts/, which are not served")
        store.execute("INSERT OR REPLACE INTO skill (carrier, library, path, text, description, body, problems)"
                      " VALUES (?, ?, ?, ?, ?, ?, ?)",
                      (coordinate, library(coordinate), path, skill["text"], fields["description"].strip(), body,
                       json.dumps(notes)))
        store.executemany("INSERT INTO skill_file (carrier, path, content) VALUES (?, ?, ?)",
                          [(coordinate, rel, content) for rel, content in sorted(skill["files"].items())])
        accepted += 1
        if republished(coordinate):
            warnings.append({"carrier": coordinate, "reason": "republishes other projects' skills"})
    store.execute("INSERT OR REPLACE INTO indexed (carrier, outcome, stamp) VALUES (?, 'indexed', ?)",
                  (coordinate, stamp_of(jar)))
    return accepted


def stale(store, coordinates):
    """The development versions among `coordinates` whose jar changed since it was indexed."""
    changed = set()
    for coordinate, stamp in store.execute("SELECT carrier, stamp FROM indexed WHERE outcome = 'indexed'"):
        if coordinate in coordinates and DEVELOPMENT_VERSION.search(version(coordinate) or ""):
            found = caches.locate(coordinate)
            if found and stamp_of(found[1]) != stamp:
                changed.add(coordinate)
    return changed


def index(store, coordinates):
    """Index these coordinates, skipping any already read. Returns the coordinates with no sources jar.

    Done means read: a coordinate with no sources jar is tried again next time, since a build may
    have fetched it since, and so is a development version whose jar was republished.
    """
    started = time.monotonic()
    coordinates = set(coordinates)
    done = {c for (c,) in store.execute("SELECT carrier FROM indexed WHERE outcome = 'indexed'")}
    done -= stale(store, coordinates)
    rejected, warnings, missing, read, accepted = [], [], [], 0, 0
    for coordinate in sorted(coordinates - done):
        found = caches.locate(coordinate)
        if not found:
            store.execute("INSERT OR REPLACE INTO indexed (carrier, outcome) VALUES (?, 'no_sources')", (coordinate,))
            missing.append(coordinate)
            continue
        count = index_jar(store, coordinate, found[1], rejected, warnings)
        if count is not None:
            read += 1
            accepted += count
    store.commit()
    if read or missing or rejected:
        store.log("index", artifacts=read, skills=accepted, rejected=rejected, warnings=warnings,
                  without_sources=missing, ms=round((time.monotonic() - started) * 1000, 1))
    return missing
