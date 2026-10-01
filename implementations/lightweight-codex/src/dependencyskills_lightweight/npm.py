"""An npm project and the packages installed in it (RAD-0077, RAD-0079).

A package ships its skills in the tarball as `skills/<name>/SKILL.md` at its root, and installing unpacks
them into the project's `node_modules`. What the project may read is what its `package.json` declares —
dependencies, dev, optional and peer — at the version `node_modules` actually holds; a declared package not
installed yet is not in scope until it is, which is npm's equivalent of a library added and not yet built.
"""

import json
from pathlib import Path

from .packages import Package, mtimes

NAME = "npm"
# Files npm, pnpm and yarn rewrite when what is installed changes; their times say when to look again.
INSTALL_MARKERS = ("package.json", "package-lock.json", "npm-shrinkwrap.json", "yarn.lock", "pnpm-lock.yaml",
                   "node_modules/.package-lock.json", "node_modules/.modules.yaml", "node_modules/.yarn-state.yml")
DECLARING = ("dependencies", "devDependencies", "optionalDependencies", "peerDependencies")


def is_project(directory):
    """A directory with a package.json and something installed beside it."""
    return (Path(directory) / "package.json").is_file() and (Path(directory) / "node_modules").is_dir()


def stamp(directory):
    return mtimes(directory, INSTALL_MARKERS)


def _read_json(path):
    try:
        return json.loads(Path(path).read_text("utf-8"))
    except (OSError, ValueError):
        return None


def _package(directory):
    """The Package installed at `directory`, or None."""
    package = _read_json(Path(directory) / "package.json")
    if not package or not isinstance(package.get("name"), str) or not isinstance(package.get("version"), str):
        return None
    text = package.get("description")
    return Package(f"npm:{package['name']}:{package['version']}", Path(directory),
                   " ".join(text.split()) if isinstance(text, str) else "")


def installed(directory):
    """Every package the project declares and has installed."""
    manifest = _read_json(Path(directory) / "package.json") or {}
    names = set()
    for field in DECLARING:
        if isinstance(manifest.get(field), dict):
            names.update(manifest[field])
    found = [_package(Path(directory) / "node_modules" / name) for name in sorted(names)]
    return [p for p in found if p]


def everything(directory):
    """Every package at the top of `node_modules`, declared or not: what is on this machine for this project."""
    modules = Path(directory) / "node_modules"
    if not modules.is_dir():
        return []
    candidates = []
    for entry in modules.iterdir():
        if entry.name.startswith("@") and entry.is_dir():
            candidates += [child for child in entry.iterdir() if child.is_dir()]
        elif entry.is_dir() and not entry.name.startswith("."):
            candidates.append(entry)
    found = [_package(candidate) for candidate in sorted(candidates)]
    return [p for p in found if p]


def manifest(directory):
    """(library, version, extra) for the npm library whose root is `directory`, for its author; or None."""
    package = _read_json(Path(directory) / "package.json")
    if not package or not isinstance(package.get("name"), str):
        return None
    return f"npm:{package['name']}", package.get("version"), package
