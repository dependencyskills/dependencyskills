"""What every source-shipping ecosystem has in common: a package installed on disk, with its skills beside it.

npm, PyPI, Go and Cargo install a package as its source, so a `skills/` directory its author shipped is
on disk the moment the dependency is — in `node_modules`, a virtual environment's `site-packages`, Go's
module cache, Cargo's registry sources. None needs a build plugin to report what the project uses or to
fetch the skills (RAD-0079). Each ecosystem's module says how to find its projects and their packages;
this is the part that is the same for all of them.

An ecosystem module provides:

- `NAME` — the prefix of its coordinates, `npm` in `npm:<name>:<version>`.
- `is_project(directory)` — whether `directory` is the root of one of its projects, with something installed.
- `stamp(directory)` — one string that changes when what is installed does.
- `installed(directory)` — a `Package` for each dependency the project declares and has installed.
- `everything(directory)` — a `Package` for everything installed for the project, declared or not.
"""

from collections import namedtuple
from pathlib import Path

MAX_FILE = 256 * 1024

# `root` is the directory whose `skills/` holds the package's skills, or None when it ships none that can
# be found; `description` is what the package says it is for, from its own metadata.
Package = namedtuple("Package", "coordinate root description")


def package_stamp(root):
    """Enough to tell a package reinstalled or rewritten in place — a local or linked one — without reading it."""
    if root is None:
        return "none"
    try:
        skills = Path(root) / "skills"
        newest = max((p.stat().st_mtime_ns for p in skills.rglob("*") if p.is_file()), default=0) \
            if skills.is_dir() else 0
        return f"{Path(root).stat().st_mtime_ns}:{newest}"
    except OSError:
        return None


def skills_in(root, served_dirs, max_file=MAX_FILE):
    """Every skill directory under `root/skills`, by name, in the shape `index.skills_in` returns for a jar."""
    found = {}
    if root is None:
        return found
    base = Path(root) / "skills"
    if not base.is_dir():
        return found
    for skill_dir in sorted(p for p in base.iterdir() if p.is_dir()):
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


def mtimes(directory, markers):
    """The modification times of `markers` under `directory`, as one string; a missing one is `-`."""
    times = []
    for marker in markers:
        try:
            times.append(str((Path(directory) / marker).stat().st_mtime_ns))
        except OSError:
            times.append("-")
    return ":".join(times)
