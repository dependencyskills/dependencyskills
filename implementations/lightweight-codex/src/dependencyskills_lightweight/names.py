"""A library's coordinate as a legal Agent Skills name, which is also the skill's directory.

A coordinate is a Maven `group:artifact:version`, or, for a package from another ecosystem,
`<ecosystem>:<name>:<version>` — `npm:@acme/text:1.0.0`. Neither a package name nor a version in
those ecosystems contains a colon, so every coordinate splits into three the same way, and the
first part says which ecosystem it is. A library is a coordinate without its version.
"""

import hashlib
import re

MAX_NAME = 64
NAME_RULE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
_NOT_LEGAL = re.compile(r"[^a-z0-9]+")

# Ecosystems whose packages are named `<ecosystem>:<name>`. Anything else is a Maven group.
ECOSYSTEMS = ("npm", "pypi", "golang", "cargo")

# The suffix a Kotlin Multiplatform library gives each platform module: `charts-jvm` is `charts`.
PLATFORM_SUFFIX = re.compile(
    r"-(?:jvm|android|js|wasm-js|wasm-wasi|metadata|iosarm64|iosx64|iossimulatorarm64|"
    r"macosarm64|macosx64|linuxx64|linuxarm64|mingwx64|tvos\w*|watchos\w*)$")


def skill_name(group, artifact):
    """The skill name for `group:artifact`, in three steps.

    1. `group:artifact`, lowercased, every run of anything else one hyphen.
    2. Over 64 characters: each group segment shrinks to its first and last letter, the artifact
       stays whole.
    3. Still over: cut to 55 characters and end in eight hex digits of a SHA-256 of
       `group:artifact`.

    One-way. Identical to `SkillPackaging.skillName` in the Gradle plugin, and tested against the
    same vectors; chosen by measurement in RAD-0075.
    """
    coordinate = f"{group}:{artifact}"
    name = _legal(coordinate)
    if len(name) <= MAX_NAME:
        return name
    segments = [s for s in _NOT_LEGAL.split(group.lower()) if s]
    compact = "-".join(s if len(s) < 2 else s[0] + s[-1] for s in segments)
    name = f"{compact}-{_legal(artifact)}".strip("-")
    if len(name) <= MAX_NAME:
        return name
    return name[:MAX_NAME - 9].rstrip("-") + "-" + hashlib.sha256(coordinate.encode()).hexdigest()[:8]


def _legal(text):
    return _NOT_LEGAL.sub("-", text.lower()).strip("-")


def parts(coordinate):
    """(group, artifact, version) of `group:artifact:version`; version is None when absent."""
    pieces = coordinate.split(":")
    return pieces[0], pieces[1], pieces[2] if len(pieces) > 2 else None


def ecosystem(coordinate):
    """"maven", or the ecosystem a package coordinate names."""
    first = coordinate.split(":", 1)[0]
    return first if first in ECOSYSTEMS else "maven"


def library(coordinate):
    """`group:artifact` without the version or a multiplatform platform suffix; `npm:<name>` for a package."""
    group, artifact, _ = parts(coordinate)
    if group in ECOSYSTEMS:
        return f"{group}:{artifact}"
    return f"{group}:{PLATFORM_SUFFIX.sub('', artifact)}"


def own_name(library):
    """The skill name a library's own guide is filed under: its coordinate, made a legal skill name.

    A Maven `group:artifact` as `skill_name` says. A package's name by the same rule, its namespace
    in the place of a group: npm's `@acme/text` is `acme-text`, as `acme:text` would be; an
    unscoped `text`, a PyPI project or a crate is its name alone; a Go module's path up to its last
    segment is the group, so `example.com/acme/text` is `example-com-acme-text`.
    """
    group, name, _ = parts(library)
    if group == "npm":
        scope, _, bare = name[1:].partition("/") if name.startswith("@") else ("", "", name)
        return skill_name(scope, bare)
    if group == "golang":
        namespace, _, last = name.rpartition("/")
        return skill_name(namespace, last)
    if group in ECOSYSTEMS:
        return skill_name("", name)
    return skill_name(group, name)


def version(coordinate):
    return parts(coordinate)[2]
