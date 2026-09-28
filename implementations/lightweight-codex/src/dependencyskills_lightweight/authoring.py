"""For a library's author in an ecosystem with no build plugin of ours: what its skill is called, where it
goes, and whether it will ship and be read.

The Gradle plugin's `dependencySkillName` and `checkDependencySkill`, and the Maven plugin's `name` goal,
do this for the JVM. A package that ships its source — npm today — needs no plugin to carry a skill,
only the name, the place, and a check that the packaging does not leave it behind.
"""

import json
import re
from pathlib import Path

from .names import own_name
from .skillfile import check, frontmatter

_VERSION = re.compile(r"(?m)^metadata:\s*\r?\n(?:[ \t]+.*\r?\n)*?[ \t]+version:\s*(.+)$")


def describe(directory):
    """{"ecosystem", "library", "name", "path", "version"} for the library whose root is `directory`, or None."""
    directory = Path(directory)
    manifest = directory / "package.json"
    if manifest.is_file():
        try:
            package = json.loads(manifest.read_text("utf-8"))
        except ValueError:
            return None
        if isinstance(package.get("name"), str):
            library = f"npm:{package['name']}"
            name = own_name(library)
            return {"ecosystem": "npm", "library": library, "name": name, "path": f"skills/{name}/SKILL.md",
                    "version": package.get("version"), "manifest": package}
    return None


def warnings(directory):
    """What would make this library's skill not ship, or not be read — the checks the build plugins make."""
    found = describe(directory)
    if found is None:
        return ["no package.json here: run this from the root of the library"]
    directory = Path(directory)
    skill = directory / found["path"]
    problems = []
    if not skill.is_file():
        others = sorted(p.parent.name for p in (directory / "skills").glob("*/SKILL.md"))
        problems.append(f"there is no {found['path']}" + (f"; found {', '.join(others)}, which are served as the "
                        "package's other guides, after its own" if others else ""))
        return problems
    fields, _ = frontmatter(skill.read_text("utf-8"))
    errors, notes = check(fields, found["name"])
    problems += errors + notes
    if fields is not None:
        text = skill.read_text("utf-8")
        version = _VERSION.search(text.split("\n---", 2)[0] + "\n")
        stated = version.group(1).strip().strip("'\"") if version else None
        if stated is None:
            problems.append(f"no metadata.version; state the version it describes ('{found['version']}')")
        elif found["version"] and stated != found["version"]:
            problems.append(f"it says it describes version '{stated}', but package.json is '{found['version']}'")
    if (skill.parent / "scripts").exists():
        problems.append("its scripts/ directory is never served: a library's skill tells an agent how to use the "
                        "library, never what to run")
    if found["ecosystem"] == "npm":
        files = found["manifest"].get("files")
        if isinstance(files, list) and not any(str(f).strip("/").split("/")[0] in ("skills", "skills/**") for f in files):
            problems.append('package.json lists "files" without "skills", so npm publishes the package without its '
                            'skill; add "skills" to "files"')
        if (directory / ".npmignore").is_file() and re.search(r"(?m)^/?skills/?\s*$", (directory / ".npmignore").read_text()):
            problems.append(".npmignore excludes skills/, so npm publishes the package without its skill")
    return problems
