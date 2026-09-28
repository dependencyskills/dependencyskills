"""An npm project and the packages installed in it (RAD-0077, RAD-0079).

npm needs no build plugin: a package ships its skills in the tarball as `skills/<name>/SKILL.md`, and
installing unpacks them into the project's `node_modules`, so they are on disk the moment the
dependency is. What the project may read is what its `package.json` declares — dependencies, dev,
optional and peer — at the version `node_modules` actually holds; a declared package not installed
yet is not in scope until it is, which is npm's equivalent of a library added and not yet built.
"""

import json
from pathlib import Path

# Files npm, pnpm and yarn rewrite when what is installed changes; their times say when to look again.
INSTALL_MARKERS = ("package.json", "package-lock.json", "npm-shrinkwrap.json", "yarn.lock", "pnpm-lock.yaml",
                   "node_modules/.package-lock.json", "node_modules/.modules.yaml", "node_modules/.yarn-state.yml")
DECLARING = ("dependencies", "devDependencies", "optionalDependencies", "peerDependencies")
MAX_FILE = 256 * 1024


def is_project(directory):
    """A directory with a package.json and something installed beside it."""
    return (directory / "package.json").is_file() and (directory / "node_modules").is_dir()


def stamp(project):
    """When what is installed last changed, as one string; unchanged means nothing to reread."""
    times = []
    for marker in INSTALL_MARKERS:
        try:
            times.append(str((project / marker).stat().st_mtime_ns))
        except OSError:
            times.append("-")
    return ":".join(times)


def _read_json(path):
    try:
        return json.loads(Path(path).read_text("utf-8"))
    except (OSError, ValueError):
        return None


def package_dir(project, name):
    return project / "node_modules" / name


def installed(project):
    """{coordinate: package directory} for every package the project declares and has installed."""
    manifest = _read_json(project / "package.json") or {}
    names = set()
    for field in DECLARING:
        if isinstance(manifest.get(field), dict):
            names.update(manifest[field])
    found = {}
    for name in sorted(names):
        directory = package_dir(project, name)
        package = _read_json(directory / "package.json")
        if package and isinstance(package.get("version"), str):
            found[f"npm:{name}:{package['version']}"] = directory
    return found


def everything_installed(project):
    """{coordinate: package directory} for every package at the top of `node_modules`, declared or not —
    what is on this machine for this project, which is what search_libraries may describe."""
    modules = project / "node_modules"
    found = {}
    if not modules.is_dir():
        return found
    candidates = []
    for entry in modules.iterdir():
        if entry.name.startswith("@") and entry.is_dir():
            candidates += [child for child in entry.iterdir() if child.is_dir()]
        elif entry.is_dir() and not entry.name.startswith("."):
            candidates.append(entry)
    for directory in candidates:
        package = _read_json(directory / "package.json")
        if package and isinstance(package.get("name"), str) and isinstance(package.get("version"), str):
            found[f"npm:{package['name']}:{package['version']}"] = directory
    return found


def description(directory):
    package = _read_json(directory / "package.json") or {}
    text = package.get("description")
    return " ".join(text.split()) if isinstance(text, str) else ""


def package_stamp(directory):
    """Enough to tell a package reinstalled or rewritten in place — a local or linked one — without reading it."""
    try:
        status = (directory / "package.json").stat()
        skills = directory / "skills"
        newest = max((p.stat().st_mtime_ns for p in skills.rglob("*") if p.is_file()), default=0) \
            if skills.is_dir() else 0
        return f"{status.st_size}:{status.st_mtime_ns}:{newest}"
    except OSError:
        return None


def skills_in(directory, served_dirs, max_file=MAX_FILE):
    """Every skill directory a package ships, by name, in the shape `index.skills_in` returns for a jar."""
    found = {}
    root = directory / "skills"
    if not root.is_dir():
        return found
    for skill_dir in sorted(p for p in root.iterdir() if p.is_dir()):
        skill = {"path": f"skills/{skill_dir.name}/SKILL.md", "text": None, "files": {}, "scripts": 0}
        for file in sorted(p for p in skill_dir.rglob("*") if p.is_file()):
            inner = file.relative_to(skill_dir).as_posix()
            try:
                if inner == "SKILL.md":
                    skill["text"] = file.read_text("utf-8", "replace").strip("\n")
                elif inner.startswith(served_dirs) and file.stat().st_size <= max_file:
                    skill["files"][inner] = file.read_text("utf-8")
                elif inner.startswith("scripts/"):
                    skill["scripts"] += 1
            except (OSError, UnicodeDecodeError):
                pass   # unreadable or binary: simply not served
        found[skill_dir.name] = skill
    return found
