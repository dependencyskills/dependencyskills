"""Reading a SKILL.md as the Agent Skills specification describes it, and checking it."""

import re

from .names import MAX_NAME, NAME_RULE

KNOWN_FIELDS = {"name", "description", "license", "compatibility", "metadata", "allowed-tools"}
# What find_library may show of a skill the project did not choose: everything but a grant of tools.
SHOWN_FIELDS = ("name", "description", "license", "compatibility", "metadata")
MAX_DESCRIPTION = 1024
MAX_COMPATIBILITY = 500

_FRONTMATTER = re.compile(r"\A---\r?\n(.*?)\r?\n---[ \t]*(?:\r?\n|\Z)", re.S)
_TOP = re.compile(r"^([A-Za-z][\w-]*):[ \t]*(.*)$")
_NESTED = re.compile(r"^[ \t]+([\w.-]+):[ \t]*(.*)$")


def frontmatter(text):
    """(fields, body) of a SKILL.md, or (None, text) when it has no frontmatter.

    The subset of YAML the specification uses: top-level `key: value`, folded and literal block
    scalars (`>-`, `|`), and one level of `key: value` under `metadata`. Not a YAML parser: a field
    it cannot read is simply absent, and validation says so.
    """
    found = _FRONTMATTER.match(text)
    if not found:
        return None, text
    lines, fields, i = found.group(1).splitlines(), {}, 0
    while i < len(lines):
        top = _TOP.match(lines[i])
        i += 1
        if not top:
            continue
        key, value = top.group(1), top.group(2).strip()
        block = []
        while i < len(lines) and (lines[i][:1] in (" ", "\t") or not lines[i].strip()):
            block.append(lines[i])
            i += 1
        if value[:1] == ">":
            fields[key] = " ".join(b.strip() for b in block if b.strip())
        elif value[:1] == "|":
            fields[key] = "\n".join(b.strip() for b in block).strip("\n")
        elif not value and block:
            fields[key] = {m.group(1): _unquote(m.group(2)) for b in block if (m := _NESTED.match(b))}
        else:
            fields[key] = _unquote(value)
    return fields, text[found.end():].lstrip("\n")


def _unquote(value):
    value = value.strip()
    if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
        return value[1:-1]
    return value


def check(fields, directory):
    """(errors, notes) for a skill in `directory`, against the specification and spec/content.md.

    Errors make it not a skill, and it is not served. Notes are what a dependency skill should not do
    but which leave it readable; they travel with it.
    """
    errors, notes = [], []
    if fields is None:
        return ["no frontmatter"], notes
    name, description = fields.get("name"), fields.get("description")
    if not isinstance(name, str) or not name:
        errors.append("no name")
    else:
        if len(name) > MAX_NAME or not NAME_RULE.match(name):
            errors.append(f"name '{name}' is not 1-64 lowercase letters, digits and single hyphens")
        if name != directory:
            errors.append(f"name '{name}' does not match its directory '{directory}'")
    if not isinstance(description, str) or not description.strip():
        errors.append("no description")
    elif len(description) > MAX_DESCRIPTION:
        errors.append(f"description is {len(description)} characters, over {MAX_DESCRIPTION}")
    if isinstance(fields.get("compatibility"), str) and len(fields["compatibility"]) > MAX_COMPATIBILITY:
        errors.append(f"compatibility is over {MAX_COMPATIBILITY} characters")
    if "metadata" in fields and not isinstance(fields["metadata"], dict):
        errors.append("metadata is not a map")
    if "allowed-tools" in fields:
        notes.append("declares allowed-tools, which a dependency skill may not; they are not honoured")
    unknown = sorted(set(fields) - KNOWN_FIELDS)
    if unknown:
        # An error, as the reference validator makes it: anything else belongs under `metadata`.
        errors.append(f"has fields the specification does not allow: {', '.join(unknown)}")
    return errors, notes
