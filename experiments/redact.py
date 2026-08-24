#!/usr/bin/env python3
"""
Shared redaction for anything a harness writes to disk.

**This exists because it already went wrong.** GPT-OSS 120B, asked to plan a file-organising task,
substituted the operator's real home directory into its answer, and that answer was written
verbatim into a results file destined for a public repository. Model output is not ours and cannot
be predicted; it must be scrubbed on the way out, not audited afterwards.

A second instance had nothing to do with models: `npm init`, run once inside a scratch directory,
defaulted the package name to the full absolute path - username, directory layout and session id -
and that reached a public commit.

So the rule is: **anything written to a tracked path goes through `clean()` first.**

Usage:

    from redact import clean
    json.dump(clean(results), open(path, "w"), indent=1)

`clean` walks dicts, lists and strings, so a whole result structure can be passed at once.
"""
import os
import re

_HOME = os.path.expanduser("~")
_USER = os.path.basename(_HOME)

# Home directories on either platform, plus the bare account name. `/home/dev` is the placeholder
# rather than an empty string so paths stay readable as paths.
# `dev` and `runner` are this project's own placeholder and container accounts. When the harness
# itself runs as one of them - which is what CI does - the literal-home pattern would match the
# container's own paths and fail every run. A gate that always fails gets disabled, which is the
# same outcome as no gate.
_GENERIC = ("dev", "root", "runner")

_PATTERNS = []
if _USER not in _GENERIC:
    _PATTERNS.append((re.compile(re.escape(_HOME)), "/home/dev"))
_PATTERNS += [
    (re.compile(r'/Users/[A-Za-z0-9._-]+'), "/home/dev"),
    # `runner` is this project's own container user, not an operator account.
    (re.compile(r'/home/(?!dev\b|runner\b)[A-Za-z0-9._-]+'), "/home/dev"),
    (re.compile(r'C:\\\\Users\\\\[A-Za-z0-9._-]+', re.I), r"C:\\Users\\dev"),
    # Session scratch directories carry a UUID and the account name together.
    (re.compile(r'/private/tmp/[A-Za-z0-9._-]*claude[^\s"\']*'), "/tmp/scratch"),
]
# The bare account name is redacted on the way out, because model output can put it anywhere.
# It is NOT used by `check()`: a name inside a path is a leak, a name in a sentence is not, and
# CI runs as an account called `runner` - which flagged the word "runner" in eleven files of
# ordinary prose the first time this gate ran. Structural path patterns catch the real thing.
_BARE_NAME = []
if _USER and _USER not in _GENERIC:
    _BARE_NAME.append((re.compile(rf'\b{re.escape(_USER)}\b'), "dev"))
_PATTERNS += _BARE_NAME
_STRUCTURAL = [p for p in _PATTERNS if p not in _BARE_NAME]


def clean(obj):
    """Redact operator identity from a string, or recursively from a result structure."""
    if isinstance(obj, str):
        for rx, repl in _PATTERNS:
            obj = rx.sub(repl, obj)
        return obj
    if isinstance(obj, dict):
        return {clean(k): clean(v) for k, v in obj.items()}
    if isinstance(obj, (list, tuple)):
        return [clean(v) for v in obj]
    return obj


# The GitHub handle on its own is SANCTIONED, and deliberately so: the project's rules name it as
# the right granularity because it is already public and is the name the work is known by. What is
# forbidden is a home path, a machine name, or the account name used as a filesystem identity.
# A checker that flags every `author:` line gets ignored, and an ignored checker catches nothing.
_SANCTIONED = re.compile(
    rf'^\s*(?:#\s*)?(?:author|vendor|owner|maintainer)\s*[:=]|'
    rf'github\.com[/:]{re.escape(_USER)}\b|'
    rf'\b{re.escape(_USER)}/[A-Za-z0-9._-]+|'          # a repo coordinate, handle/repo
    rf'{re.escape(_USER)}@users\.noreply\.github\.com', re.I)


def is_binary(path):
    """A NUL byte in the first block means this is not text. Images and jars are not leaks."""
    try:
        with open(path, "rb") as fh:
            return b"\0" in fh.read(8192)
    except OSError:
        return True


def check(path):
    """True if a file is free of operator identity, ignoring the sanctioned handle uses.

    Binary files are skipped here rather than by the caller. An earlier version left the choice of
    *what* to scan to a list of extensions in the CI workflow, and that list silently omitted the
    highest-risk files in the repository — the raw model-output `.out` files, which is precisely
    where the first leak landed. A checker that depends on being invoked with the right file list
    will eventually be invoked with the wrong one.
    """
    if is_binary(path):
        return True
    try:
        text = open(path, errors="replace").read()
    except OSError:
        return True
    for line in text.splitlines():
        if _SANCTIONED.search(line):
            continue
        redacted = line
        for rx, repl in _STRUCTURAL:
            redacted = rx.sub(repl, redacted)
        if redacted != line:
            return False
    return True


if __name__ == "__main__":
    import sys
    # This module's own patterns describe the shapes it redacts, so it matches itself.
    SELF = os.path.basename(__file__)
    bad = [p for p in sys.argv[1:]
           if os.path.basename(p) != SELF and not check(p)]
    for p in bad:
        print(f"IDENTITY LEAK: {p}")
    sys.exit(1 if bad else 0)
