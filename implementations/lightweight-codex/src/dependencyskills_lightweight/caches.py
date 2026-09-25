"""Where a library's sources jar is on this machine: the Gradle cache and the local Maven repository."""

import os
import re
from pathlib import Path

from .names import PLATFORM_SUFFIX, parts

_DESCRIPTION = re.compile(r"<description>(.*?)</description>", re.S)


def gradle_cache():
    home = Path(os.environ.get("GRADLE_USER_HOME") or Path.home() / ".gradle")
    return home / "caches" / "modules-2" / "files-2.1"


def maven_repository():
    return Path(os.environ.get("M2_REPO") or Path.home() / ".m2" / "repository")


def locate(coordinate):
    """(version directory, sources jar) for `group:artifact:version`, or None.

    Gradle files an artifact at `<group>/<artifact>/<version>/<checksum>/<file>`, the group one
    dotted directory; Maven nests the group and puts files directly under the version. A
    multiplatform library recorded under its root coordinate whose own sources jar is absent is
    found through a platform module's (`platform_sources`).
    """
    group, artifact, version = parts(coordinate)
    name = f"{artifact}-{version}-sources.jar"
    gradle = gradle_cache() / group / artifact / version
    if gradle.is_dir():
        for hashed in gradle.iterdir():
            if (hashed / name).is_file():
                return gradle, hashed / name
    maven = maven_repository().joinpath(*group.split("."), artifact, version)
    if (maven / name).is_file():
        return maven, maven / name
    return platform_sources(group, artifact, version)


def platform_sources(group, artifact, version):
    """(version directory, sources jar) of a multiplatform library's platform module, or None.

    The build records a multiplatform library under its root coordinate, and fetches the sources of
    the platform module it compiled against — `charts-jvm`, not `charts` — because that is the variant
    its graph selected. Every platform sources jar carries the library's `commonMain`, so its skill
    is the root's. The JVM module is preferred, then any.
    """
    def is_platform(module):
        return module != artifact and PLATFORM_SUFFIX.search(module) and PLATFORM_SUFFIX.sub("", module) == artifact

    found = []
    gradle = gradle_cache() / group
    if gradle.is_dir():
        for module in gradle.iterdir():
            if is_platform(module.name) and (module / version).is_dir():
                name = f"{module.name}-{version}-sources.jar"
                found += [(module.name, module / version, h / name)
                          for h in (module / version).iterdir() if (h / name).is_file()]
    maven = maven_repository().joinpath(*group.split("."))
    if maven.is_dir():
        for module in maven.iterdir():
            jar = module / version / f"{module.name}-{version}-sources.jar"
            if is_platform(module.name) and jar.is_file():
                found.append((module.name, module / version, jar))
    if not found:
        return None
    found.sort(key=lambda f: (not f[0].endswith("-jvm"), f[0]))
    return found[0][1], found[0][2]


def discover():
    """(coordinate, version directory, sources jar) for every sources jar in both caches, once each."""
    found, seen = [], set()
    cache = gradle_cache()
    if cache.is_dir():
        for jar in cache.rglob("*-sources.jar"):
            version_dir = jar.parent.parent
            artifact, version = version_dir.parent.name, version_dir.name
            if jar.name == f"{artifact}-{version}-sources.jar":
                coordinate = f"{version_dir.parent.parent.name}:{artifact}:{version}"
                if coordinate not in seen:
                    seen.add(coordinate)
                    found.append((coordinate, version_dir, jar))
    repository = maven_repository()
    if repository.is_dir():
        for jar in repository.rglob("*-sources.jar"):
            version_dir = jar.parent
            artifact, version = version_dir.parent.name, version_dir.name
            try:
                group = ".".join(version_dir.parent.parent.relative_to(repository).parts)
            except ValueError:
                continue
            coordinate = f"{group}:{artifact}:{version}"
            if group and jar.name == f"{artifact}-{version}-sources.jar" and coordinate not in seen:
                seen.add(coordinate)
                found.append((coordinate, version_dir, jar))
    return found


def pom_description(version_dir, artifact, version):
    """The `<description>` of the library's POM, whichever cache layout holds it, or ""."""
    name = f"{artifact}-{version}.pom"
    candidates = [version_dir / name] + [h / name for h in version_dir.iterdir() if h.is_dir()] \
        if version_dir.is_dir() else []
    for pom in candidates:
        if pom.is_file():
            try:
                found = _DESCRIPTION.search(pom.read_text("utf-8", "replace"))
            except OSError:
                continue
            return " ".join(found.group(1).split()) if found else ""
    return ""
