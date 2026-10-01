"""For a library's author in an ecosystem with no build plugin of ours: what its skill is called, where it
goes, and whether it will ship and be read.

The Gradle plugin's `dependencySkillName` and `checkDependencySkill`, and the Maven plugin's `name` and
`author` goals, do this for the JVM. A package that ships its source — npm, PyPI, Go, Cargo — needs no
plugin to carry a skill, only the name, the place, and a check that the packaging does not leave it behind.
"""

import re
from pathlib import Path

from . import cargo, golang, npm, pypi
from .names import own_name
from .skillfile import check, frontmatter

_VERSION = re.compile(r"(?m)^metadata:\s*\r?\n(?:[ \t]+.*\r?\n)*?[ \t]+version:\s*(.+)$")
# Where each ecosystem's library keeps the file that names it, in the order they are looked for.
MANIFESTS = ((npm, "package.json"), (pypi, "pyproject.toml"), (golang, "go.mod"), (cargo, "Cargo.toml"))


def describe(directory):
    """{"ecosystem", "library", "name", "path", "version", "manifest"} for the library rooted at `directory`, or None."""
    directory = Path(directory)
    for module, file in MANIFESTS:
        if not (directory / file).is_file():
            continue
        found = module.manifest(directory)
        if found is None:
            continue
        library, version, extra = found
        name = own_name(library)
        # A Python distribution installs its import package, so the skill travels inside it.
        root = pypi.skill_root(directory, library.split(":", 1)[1]) if module is pypi else Path(".")
        path = (root / "skills" / name / "SKILL.md").as_posix()
        return {"ecosystem": module.NAME, "library": library, "name": name, "path": path,
                "version": version, "manifest": extra}
    return None


def warnings(directory):
    """What would make this library's skill not ship, or not be read — the checks the build plugins make."""
    found = describe(directory)
    if found is None:
        return ["no package.json, pyproject.toml, go.mod or Cargo.toml here: run this from the root of the library"]
    directory = Path(directory)
    skill = directory / found["path"]
    problems = []
    if not skill.is_file():
        others = sorted(p.parent.name for p in (skill.parent.parent).glob("*/SKILL.md"))
        problems.append(f"there is no {found['path']}" + (f"; found {', '.join(others)}, which are served as the "
                        "package's other guides, after its own" if others else ""))
        if found["ecosystem"] == "pypi" and (directory / "skills").is_dir():
            problems.append("a skills/ directory at the project root is not installed with the package; the skill "
                            f"belongs inside the import package, at {found['path']}")
        return problems
    text = skill.read_text("utf-8")
    fields, _ = frontmatter(text)
    errors, notes = check(fields, found["name"])
    problems += errors + notes
    if fields is not None and found["version"]:
        version = _VERSION.search(text.split("\n---", 2)[0] + "\n")
        stated = version.group(1).strip().strip("'\"") if version else None
        if stated is None:
            problems.append(f"no metadata.version; state the version it describes ('{found['version']}')")
        elif stated != found["version"]:
            problems.append(f"it says it describes version '{stated}', but the manifest says '{found['version']}'")
    if (skill.parent / "scripts").exists():
        problems.append("its scripts/ directory is never served: a library's skill tells an agent how to use the "
                        "library, never what to run")
    return problems + _packaging(directory, found)


def _packaging(directory, found):
    """What in the ecosystem's packaging would publish the library without its skill."""
    manifest, problems = found["manifest"], []
    if found["ecosystem"] == "npm":
        files = manifest.get("files")
        if isinstance(files, list) and not any(str(f).strip("/").split("/")[0] == "skills" for f in files):
            problems.append('package.json lists "files" without "skills", so npm publishes the package without its '
                            'skill; add "skills" to "files"')
        ignore = directory / ".npmignore"
        if ignore.is_file() and re.search(r"(?m)^/?skills/?\s*$", ignore.read_text("utf-8")):
            problems.append(".npmignore excludes skills/, so npm publishes the package without its skill")
    elif found["ecosystem"] == "pypi":
        backend = str(manifest.get("build-system", {}).get("build-backend", ""))
        setuptools = manifest.get("tool", {}).get("setuptools", {})
        if backend.startswith("setuptools") and "skills" not in str(setuptools.get("package-data", "")):
            problems.append("setuptools installs only Python files unless told otherwise: add the skill to "
                            '[tool.setuptools.package-data], for example "*" = ["skills/**/*"] (RAD-0075)')
    elif found["ecosystem"] == "cargo":
        include, exclude = manifest.get("include"), manifest.get("exclude")
        if isinstance(include, list) and not any(str(p).lstrip("/").startswith("skills") for p in include):
            problems.append('Cargo.toml lists "include" without skills/, so cargo publishes the crate without its '
                            'skill; add "skills/**"')
        if isinstance(exclude, list) and any(str(p).lstrip("/").startswith("skills") for p in exclude):
            problems.append('Cargo.toml "exclude" covers skills/, so cargo publishes the crate without its skill')
    return problems
