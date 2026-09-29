"""What a project may read: the scope its build reported, as a CycloneDX SBOM in its build directory —
or, in an npm, Python, Go or Cargo project, which needs no plugin, what the project declares at the
version installed (`ecosystems`)."""

import json
import re
from pathlib import Path

from . import ecosystems, index as indexing
from .packages import Package
from .names import library, version

# Where each build plugin writes the SBOM, relative to the root project: Gradle's build directory
# (DependencySkillsPlugin.REPORT_FILE) and Maven's (ConsumerMojo). The same file either way.
SBOMS = (Path("build") / "dependencyskills" / "bom.cdx.json", Path("target") / "dependencyskills" / "bom.cdx.json")
SBOM = SBOMS[0]
_PURL = re.compile(r"^pkg:maven/([^/]+)/([^@/]+)@([^?#]+)")
DECLARED = "dependencyskills:declared"
# The directory of the included build's project that supplies a library, where its skill is still source.
SOURCE = "dependencyskills:source"


def find_project(start):
    """("sbom", directory, SBOM) or (ecosystem module, directory, None) for the nearest project at or above
    `start`, or None.

    A build's report wins where one directory has both, since it is what that build resolved.
    """
    here = Path(start).resolve()
    for directory in [here, *here.parents]:
        for sbom in SBOMS:
            if (directory / sbom).is_file():
                return "sbom", directory, directory / sbom
        module = ecosystems.project_kind(directory)
        if module:
            return module, directory, None
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
    """(coordinates, declared, sources): every Maven component; those a version catalog declares that no module
    resolves; and, for a library a composite build's included project supplies, that project's directory."""
    coordinates, declared, sources = set(), set(), {}
    for component in json.loads(Path(path).read_text("utf-8")).get("components", []):
        found = _PURL.match(component.get("purl") or "")
        if found:
            coordinate = ":".join(found.groups())
            coordinates.add(coordinate)
            properties = component.get("properties") or []
            if any(p.get("name") == DECLARED for p in properties):
                declared.add(coordinate)
            for p in properties:
                if p.get("name") == SOURCE and p.get("value"):
                    sources[coordinate] = p["value"]
    return coordinates, declared, sources


def source_packages(sources):
    """A `Package` for each library an included build supplies, rooted where its source tree keeps `skills/`.

    Read from source because no jar of it exists: `includeBuild` compiles the project in place. A multiplatform
    project keeps its skill under `src/commonMain`, a JVM one under `src/main`, as the Gradle plugin packages it.
    """
    found = []
    for coordinate, directory in sorted(sources.items()):
        roots = [Path(directory) / "src" / s for s in ("commonMain", "main")]
        root = next((r for r in roots if (r / "skills").is_dir()), None)
        found.append(Package(coordinate, root, ""))
    return found


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
    if kind != "sbom":
        return refresh_packages(store, kind, directory)
    project = str(directory.resolve())
    stamp = str(path.stat().st_mtime_ns)
    if store.setting(f"sbom:{project}") == stamp:
        in_scope = {c for (c,) in store.execute("SELECT carrier FROM scope WHERE project = ?", (project,))}
        changed = indexing.stale(store, in_scope)
        if changed:
            indexing.index(store, changed)
        # An included build's skill is being written as the developer works: reread it when it changed.
        indexing.index_packages(store, source_packages(json.loads(store.setting(f"sources:{project}") or "{}")))
        return project
    try:
        coordinates, declared, sources = read_sbom(path)
    except (OSError, ValueError) as problem:
        # Half-written, or broken: keep the last good scope, and say so in every answer until it reads again.
        store.set_setting(f"sbom-error:{project}", f"{path} could not be read ({type(problem).__name__})")
        store.log("scope", project=project, error=str(problem)[:200])
        return project if store.setting(f"sbom:{project}") else None
    store.set_setting(f"sbom-error:{project}", None)
    register(store, project, coordinates, declared)
    indexing.index(store, set(coordinates) - set(sources))
    indexing.index_packages(store, source_packages(sources))
    store.set_setting(f"sources:{project}", json.dumps(sources))
    store.set_setting(f"sbom:{project}", stamp)
    store.log("scope", project=project, coordinates=len(coordinates), declared=len(declared))
    return project


def refresh_packages(store, module, directory):
    """Bring a source-shipping project up to date: its scope is what it declares, at the version installed.

    Reread only when what the ecosystem's package manager writes on install changed, so an unchanged
    project costs a few stats per call. The scope is written by the package manager, never by the agent.
    """
    project = str(directory.resolve())
    stamp = module.stamp(directory)
    if store.setting(f"{module.NAME}:{project}") == stamp:
        return project
    packages = module.installed(directory)
    register(store, project, [p.coordinate for p in packages])
    indexing.index_packages(store, packages)
    store.set_setting(f"{module.NAME}:{project}", stamp)
    store.log("scope", project=project, coordinates=len(packages), ecosystem=module.NAME)
    return project

