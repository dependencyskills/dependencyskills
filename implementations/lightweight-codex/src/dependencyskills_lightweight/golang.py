"""A Go module and the modules it requires, from the module cache (RAD-0075, RAD-0079).

A module ships its skills as `skills/<name>/SKILL.md` at the module root; the module zip carries every
file, so after `go mod download` — or any build — they are on disk in the module cache, at
`<GOMODCACHE>/<escaped path>@<escaped version>/`. What the project may read is what `go.mod` requires
directly, at the version it records; a `replace` to a local directory is followed, as npm's `file:` is.
Go modules carry no description, so a module without a skill is found by its path alone.
"""

import os
import re
from pathlib import Path

from .packages import Package, mtimes

NAME = "golang"
_BLOCK = re.compile(r"(?ms)^(require|replace)\s*\((.*?)^\)")
_LINE = re.compile(r"(?m)^(require|replace)\s+(?!\()(.+)$")


def module_cache():
    if os.environ.get("GOMODCACHE"):
        return Path(os.environ["GOMODCACHE"])
    gopath = os.environ.get("GOPATH", "").split(os.pathsep)[0]
    return (Path(gopath) if gopath else Path.home() / "go") / "pkg" / "mod"


def escape(text):
    """The module cache's case-folding escape: every uppercase letter is `!` and the letter in lowercase."""
    return re.sub(r"[A-Z]", lambda m: "!" + m.group(0).lower(), text)


def is_project(directory):
    return (Path(directory) / "go.mod").is_file()


def stamp(directory):
    return mtimes(directory, ("go.mod", "go.sum", "go.work", "go.work.sum"))


def _directives(directory):
    """(requires, replaces): requires as (path, version, indirect); replaces as {path: (new path, version or None)}."""
    try:
        text = (Path(directory) / "go.mod").read_text("utf-8")
    except OSError:
        return [], {}
    entries = [(kind, line) for kind, body in _BLOCK.findall(text) for line in body.splitlines()]
    entries += _LINE.findall(text)
    requires, replaces = [], {}
    for kind, line in entries:
        content, _, comment = line.partition("//")
        words = content.split()
        if kind == "require" and len(words) >= 2:
            requires.append((words[0], words[1], "indirect" in comment))
        elif kind == "replace" and "=>" in words:
            arrow = words.index("=>")
            target = words[arrow + 1:]
            if target:
                replaces[words[0]] = (target[0], target[1] if len(target) > 1 else None)
    return requires, replaces


def _package(directory, path, version, replaces):
    new_path, new_version = replaces.get(path, (path, version))
    if new_version is None:   # a local directory
        root = (Path(directory) / new_path).resolve()
    else:
        root = module_cache() / f"{escape(new_path)}@{escape(new_version)}"
    if not root.is_dir():
        return None
    return Package(f"golang:{path}:{version}", root, "")


def installed(directory):
    requires, replaces = _directives(directory)
    found = [_package(directory, p, v, replaces) for p, v, indirect in requires if not indirect]
    return [p for p in found if p]


def everything(directory):
    """Every module go.mod records, direct or indirect: what is on this machine for this project."""
    requires, replaces = _directives(directory)
    found = [_package(directory, p, v, replaces) for p, v, _ in requires]
    return [p for p in found if p]


def manifest(directory):
    """(library, version, extra) for the module whose root is `directory`, for its author; or None.

    A Go module's version is its tag, which the source does not state, so there is none to compare with.
    """
    try:
        text = (Path(directory) / "go.mod").read_text("utf-8")
    except OSError:
        return None
    found = re.search(r"(?m)^module\s+(\S+)", text)
    return (f"golang:{found.group(1)}", None, {}) if found else None
