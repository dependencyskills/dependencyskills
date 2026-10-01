"""Reading a library's skills into the store: out of a sources jar, or out of an installed package's directory."""

import json
import re
import time
import zipfile
from pathlib import Path

from . import caches
from .packages import package_stamp, skills_in as package_skills
from .names import ecosystem, library, own_name, version
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

    Returns the number of skills accepted, or None if the jar could not be read.
    """
    try:
        found = skills_in(jar)
    except (zipfile.BadZipFile, OSError):
        return None
    return _accept(store, coordinate, found, stamp_of(jar), rejected, warnings)


def index_directory(store, coordinate, root, rejected, warnings):
    """Index one installed package under `coordinate`, from `root/skills`, replacing whatever was read before."""
    try:
        found = package_skills(root, SERVED_DIRS, MAX_FILE)
    except OSError:
        return None
    return _accept(store, coordinate, found, package_stamp(root), rejected, warnings)


def second_order(coordinate, path):
    """Whether the skill at `path` is one a package ships under a name its author chose, rather than its own."""
    return f"/{own_name(library(coordinate))}/" not in f"/{path}"


def _accept(store, coordinate, found, stamp, rejected, warnings):
    """Store the skills a library ships that may be served, and say why each other one was not.

    **On the JVM a skill is taken only if it is filed under the coordinate's own name**: a jar cannot
    ship a skill for some other library, which is what keeps a republisher from filing one under
    someone else's coordinate (RAD-0076). **A package from another ecosystem may ship several**, under
    names its author chose — npm's existing practice (RAD-0077) — so its own-named skill is first-order
    and every other valid one is second-order: kept, attributed to the package that carries it and to
    no other library, and served after the first (RAD-0079). Its files are stored under its name, so two
    skills in one package cannot collide.
    """
    store.execute("DELETE FROM skill WHERE carrier = ?", (coordinate,))
    store.execute("DELETE FROM skill_file WHERE carrier = ?", (coordinate,))
    own = own_name(library(coordinate))
    jvm = ecosystem(coordinate) == "maven"
    accepted = 0
    for name, skill in found.items():
        path = skill["path"] or f"skills/{name}/"
        if name != own and jvm:
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
        prefix = "" if name == own else f"{name}/"
        store.executemany("INSERT INTO skill_file (carrier, path, content) VALUES (?, ?, ?)",
                          [(coordinate, prefix + rel, content) for rel, content in sorted(skill["files"].items())])
        accepted += 1
        if republished(coordinate):
            warnings.append({"carrier": coordinate, "reason": "republishes other projects' skills"})
    store.execute("INSERT OR REPLACE INTO indexed (carrier, outcome, stamp) VALUES (?, 'indexed', ?)",
                  (coordinate, stamp))
    return accepted


def index_packages(store, packages):
    """Index installed packages, each a `packages.Package`, skipping any whose directory is as it was read.

    A registry version never changes, but a local or linked package does, in place; comparing one stamp
    per package is what lets a rewritten one be read again.
    """
    started = time.monotonic()
    known = dict(store.execute("SELECT carrier, stamp FROM indexed WHERE outcome = 'indexed'"))
    rejected, warnings, read, accepted = [], [], 0, 0
    for package in sorted(packages):
        if known.get(package.coordinate) == package_stamp(package.root):
            continue
        count = index_directory(store, package.coordinate, package.root, rejected, warnings)
        if count is not None:
            read += 1
            accepted += count
    store.commit()
    if read or rejected:
        store.log("index", packages=read, skills=accepted, rejected=rejected, warnings=warnings,
                  ms=round((time.monotonic() - started) * 1000, 1))


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
