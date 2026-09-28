"""A Python project and the distributions installed in its virtual environment (RAD-0075, RAD-0079).

A distribution installs its import package into `site-packages`, so a skill its author shipped travels
inside that package: `src/acme_text/skills/acme-text/SKILL.md` in the source tree is
`site-packages/acme_text/skills/acme-text/SKILL.md` once installed. hatchling and uv_build include it as
they are; setuptools needs one `package-data` line (RAD-0075). The distribution's `RECORD` says which
directory is its own, so a skill is attributed to the distribution that installed it and to no other.

What the project may read is what it declares — `[project]` dependencies and extras, dependency groups,
Poetry's tables, `requirements*.txt` — at the version installed in the project's own environment:
`.venv`, `venv` or `env` beside it, or the one `VIRTUAL_ENV` names.
"""

import os
import re
import tomllib
from pathlib import Path

from .packages import Package, mtimes

NAME = "pypi"
ENVIRONMENTS = (".venv", "venv", "env")
MARKERS = ("pyproject.toml", "uv.lock", "poetry.lock", "pdm.lock", "requirements.txt", "setup.cfg", "setup.py")
_REQUIREMENT = re.compile(r"^\s*([A-Za-z0-9][A-Za-z0-9._-]*)")
_SKILL = re.compile(r"^(.+)/skills/[^/]+/SKILL\.md$")


def normalise(name):
    """PEP 503: a project name compared case-blind, with runs of `-`, `_` and `.` as one hyphen."""
    return re.sub(r"[-_.]+", "-", name).lower()


def environment(directory):
    """The project's own `site-packages`, or None."""
    candidates = [Path(directory) / name for name in ENVIRONMENTS]
    if os.environ.get("VIRTUAL_ENV"):
        candidates.append(Path(os.environ["VIRTUAL_ENV"]))
    for venv in candidates:
        if (venv / "pyvenv.cfg").is_file():
            found = sorted(venv.glob("lib/python*/site-packages")) + sorted(venv.glob("Lib/site-packages"))
            if found:
                return found[-1]
    return None


def is_project(directory):
    directory = Path(directory)
    declares = any((directory / m).is_file() for m in ("pyproject.toml", "setup.py", "setup.cfg")) \
        or any(directory.glob("requirements*.txt"))
    return declares and environment(directory) is not None


def stamp(directory):
    site = environment(directory)
    return mtimes(directory, MARKERS) + ":" + (str(site.stat().st_mtime_ns) if site else "-")


def _toml(path):
    try:
        with open(path, "rb") as file:
            return tomllib.load(file)
    except (OSError, tomllib.TOMLDecodeError):
        return {}


def declared(directory):
    """The normalised names of every distribution the project declares, in any of the usual places."""
    directory = Path(directory)
    pyproject = _toml(directory / "pyproject.toml")
    requirements = list(pyproject.get("project", {}).get("dependencies", []))
    for extra in pyproject.get("project", {}).get("optional-dependencies", {}).values():
        requirements += extra
    for group in pyproject.get("dependency-groups", {}).values():
        requirements += [r for r in group if isinstance(r, str)]
    requirements += pyproject.get("tool", {}).get("uv", {}).get("dev-dependencies", [])
    poetry = pyproject.get("tool", {}).get("poetry", {})
    names = set(poetry.get("dependencies", {})) - {"python"}
    for group in poetry.get("group", {}).values():
        names |= set(group.get("dependencies", {}))
    for file in directory.glob("requirements*.txt"):
        try:
            requirements += [line for line in file.read_text("utf-8").splitlines() if not line.strip().startswith(("#", "-"))]
        except OSError:
            pass
    for requirement in requirements:
        found = _REQUIREMENT.match(str(requirement))
        if found:
            names.add(found.group(1))
    return {normalise(n) for n in names}


def _metadata(dist_info):
    fields = {}
    try:
        for line in (dist_info / "METADATA").read_text("utf-8", "replace").splitlines():
            if not line.strip():
                break   # the headers end at the first blank line; the long description follows
            key, _, value = line.partition(":")
            fields.setdefault(key.strip().lower(), value.strip())
    except OSError:
        pass
    return fields


def _skill_root(site, dist_info):
    """The directory whose `skills/` holds this distribution's skills, from its RECORD; None if it ships none.

    Only inside the distribution's own package: a `skills/` directory at the top of `site-packages` would be
    shared by every distribution that put one there, and no skill in it could be attributed to one of them.
    """
    try:
        lines = (dist_info / "RECORD").read_text("utf-8", "replace").splitlines()
    except OSError:
        return None
    roots = sorted({m.group(1) for m in (_SKILL.match(line.split(",")[0]) for line in lines) if m})
    roots = [r for r in roots if not r.startswith("..") and ".dist-info" not in r]
    return site / roots[0] if roots else None


def _distributions(directory):
    site = environment(directory)
    if site is None:
        return []
    found = []
    for dist_info in sorted(site.glob("*.dist-info")):
        fields = _metadata(dist_info)
        if fields.get("name") and fields.get("version"):
            found.append((normalise(fields["name"]), Package(f"pypi:{normalise(fields['name'])}:{fields['version']}",
                                                             _skill_root(site, dist_info), fields.get("summary", ""))))
    return found


def installed(directory):
    names = declared(directory)
    return [package for name, package in _distributions(directory) if name in names]


def everything(directory):
    return [package for _, package in _distributions(directory)]


def manifest(directory):
    """(library, version, extra) for the Python library whose root is `directory`, for its author; or None."""
    pyproject = _toml(Path(directory) / "pyproject.toml")
    name = pyproject.get("project", {}).get("name")
    if not isinstance(name, str):
        return None
    return f"pypi:{normalise(name)}", pyproject.get("project", {}).get("version"), pyproject


def skill_root(directory, name):
    """Where an author's skill goes: inside the import package, which is what a wheel installs."""
    package = name.replace("-", "_")
    for candidate in (Path("src") / package, Path(package)):
        if (Path(directory) / candidate).is_dir():
            return candidate
    return Path("src") / package
