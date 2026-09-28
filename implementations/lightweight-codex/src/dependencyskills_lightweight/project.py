"""What a project may read: the scope its build reported, as a CycloneDX SBOM in its build directory —
or, in an npm project, which needs no plugin, what its package.json declares and node_modules holds."""

import json
import re
from pathlib import Path

from . import index as indexing, npm
from .names import library, version

# Where each build plugin writes the SBOM, relative to the root project: Gradle's build directory
# (DependencySkillsPlugin.REPORT_FILE) and Maven's (ConsumerMojo). The same file either way.
SBOMS = (Path("build") / "dependencyskills" / "bom.cdx.json", Path("target") / "dependencyskills" / "bom.cdx.json")
SBOM = SBOMS[0]
_PURL = re.compile(r"^pkg:maven/([^/]+)/([^@/]+)@([^?#]+)")
DECLARED = "dependencyskills:declared"


def find_project(start):
    """("sbom", directory, SBOM) or ("npm", directory, None) for the nearest project at or above `start`, or None.

    A build's report wins where one directory has both, since it is what that build resolved.
    """
    here = Path(start).resolve()
    for directory in [here, *here.parents]:
        for sbom in SBOMS:
            if (directory / sbom).is_file():
                return "sbom", directory, directory / sbom
        if npm.is_project(directory):
            return "npm", directory, None
    return None


def find_sbom(start):
    """(project directory, SBOM) for the nearest build at or above `start`, or None."""
    here = Path(start).resolve()
    for directory in [here, *here.parents]:
        for sbom in SBOMS:
            if (directory / sbom).is_file():
                return directory, directory / sbom
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
    found = find_project(cwd)
    if not found:
        project, _ = scope_of(store, cwd)
        return project
    kind, directory, path = found
    if kind == "npm":
        return refresh_npm(store, directory)
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


def refresh_npm(store, directory):
    """Bring an npm project up to date: its scope is what package.json declares at the version installed.

    Reread only when an install marker changed — npm, pnpm and yarn each rewrite one — so an unchanged
    project costs a few stats per call. The scope is written by the package manager, never by the agent.
    """
    project = str(directory.resolve())
    stamp = npm.stamp(directory)
    if store.setting(f"npm:{project}") == stamp:
        return project
    packages = npm.installed(directory)
    register(store, project, packages)
    indexing.index_packages(store, packages)
    store.set_setting(f"npm:{project}", stamp)
    store.log("scope", project=project, coordinates=len(packages), ecosystem="npm")
    return project

