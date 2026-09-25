"""What a project may read: the scope its build reported, as a CycloneDX SBOM in its build directory."""

import json
import re
from pathlib import Path

from . import index as indexing
from .names import library, version

# Where the Gradle plugin writes the SBOM, relative to the root project (DependencySkillsPlugin.REPORT_FILE).
SBOM = Path("build") / "dependencyskills" / "bom.cdx.json"
_PURL = re.compile(r"^pkg:maven/([^/]+)/([^@/]+)@([^?#]+)")
DECLARED = "dependencyskills:declared"


def find_sbom(start):
    """(project directory, SBOM) for the nearest build at or above `start`, or None."""
    here = Path(start).resolve()
    for directory in [here, *here.parents]:
        if (directory / SBOM).is_file():
            return directory, directory / SBOM
    return None


def read_sbom(path):
    """(coordinates, declared): every Maven component, and those a version catalog declares that no module resolves."""
    coordinates, declared = set(), set()
    for component in json.loads(Path(path).read_text("utf-8")).get("components", []):
        found = _PURL.match(component.get("purl") or "")
        if found:
            coordinate = ":".join(found.groups())
            coordinates.add(coordinate)
            if any(p.get("name") == DECLARED for p in component.get("properties") or []):
                declared.add(coordinate)
    return coordinates, declared


def register(store, project, coordinates, declared=()):
    """Record exactly what a project may read.

    An EMPTY set is an empty scope — a project that resolved nothing reads nothing — never the whole
    store, which is the leak a scope exists to stop. The `""` row is what keeps an empty scope
    registered rather than absent.
    """
    project = str(Path(project).resolve())
    store.execute("DELETE FROM scope WHERE project = ?", (project,))
    store.executemany("INSERT OR IGNORE INTO scope (project, carrier) VALUES (?, ?)",
                      [(project, c) for c in (sorted(coordinates) or [""])])
    store.execute("DELETE FROM declared WHERE project = ?", (project,))
    store.executemany("INSERT OR IGNORE INTO declared (project, carrier) VALUES (?, ?)",
                      [(project, c) for c in declared])
    store.commit()
    return project


def scope_of(store, cwd):
    """(project, {(library, version)}) for the registered project containing `cwd`, the nearest if nested.

    (None, None) for a directory no registered project contains, which callers answer as "not set
    up" rather than as "nothing here".
    """
    cwd = Path(cwd).resolve()
    rows = store.execute("SELECT project, carrier FROM scope").fetchall()
    projects = [p for p in {p for p, _ in rows} if Path(p) == cwd or Path(p) in cwd.parents]
    if not projects:
        return None, None
    project = max(projects, key=len)
    return project, {(library(c), version(c)) for p, c in rows if p == project and c.count(":") >= 2}


def declared_libraries(store, project):
    return {library(c) for (c,) in store.execute("SELECT carrier FROM declared WHERE project = ?", (project,))}


def refresh(store, cwd):
    """Bring the project containing `cwd` up to date with its SBOM. Returns the project, or None.

    This is the whole of watching: one timestamp compared on each call. Unchanged, it costs one stat
    per development version in scope, since a library republished under the same version changes its
    jar and not the SBOM. Changed, the scope is replaced with exactly what the file lists and anything
    new is indexed. The scope comes only from this file, which the build writes and the agent cannot.
    A missing file — after `clean` — keeps the scope already stored: the last build is still true.
    """
    found = find_sbom(cwd)
    if not found:
        project, _ = scope_of(store, cwd)
        return project
    directory, path = found
    project = str(directory.resolve())
    stamp = str(path.stat().st_mtime_ns)
    if store.setting(f"sbom:{project}") == stamp:
        in_scope = {c for (c,) in store.execute("SELECT carrier FROM scope WHERE project = ?", (project,))}
        changed = indexing.stale(store, in_scope)
        if changed:
            indexing.index(store, changed)
        return project
    try:
        coordinates, declared = read_sbom(path)
    except (OSError, ValueError):
        return project if store.setting(f"sbom:{project}") else None   # half-written: keep the last good scope
    register(store, project, coordinates, declared)
    indexing.index(store, coordinates)
    store.set_setting(f"sbom:{project}", stamp)
    store.log("scope", project=project, coordinates=len(coordinates), declared=len(declared))
    return project
