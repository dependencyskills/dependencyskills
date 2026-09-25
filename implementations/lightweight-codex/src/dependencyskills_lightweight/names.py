"""A library's coordinate as a legal Agent Skills name, which is also the skill's directory."""

import hashlib
import re

MAX_NAME = 64
NAME_RULE = re.compile(r"^[a-z0-9]+(?:-[a-z0-9]+)*$")
_NOT_LEGAL = re.compile(r"[^a-z0-9]+")

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


def library(coordinate):
    """`group:artifact` without the version or a multiplatform platform suffix."""
    group, artifact, _ = parts(coordinate)
    return f"{group}:{PLATFORM_SUFFIX.sub('', artifact)}"


def version(coordinate):
    return parts(coordinate)[2]
