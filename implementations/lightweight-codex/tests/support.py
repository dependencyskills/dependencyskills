"""A machine in a temporary directory: an empty Gradle cache, a Maven repository, a store, a project."""

import json
import os
import tempfile
import unittest
import zipfile
from pathlib import Path

from dependencyskills_lightweight.names import skill_name
from dependencyskills_lightweight.store import Store


def skill_text(name, description="Use for acme text instead of hand-rolling it.", body="Call Acme.normalize.", extra=""):
    return f"---\nname: {name}\ndescription: {description}\n{extra}---\n\n{body}\n"


class Machine(unittest.TestCase):

    def setUp(self):
        self.temp = Path(tempfile.mkdtemp())
        self.maven = self.temp / "m2"
        self.gradle = self.temp / "gradle"
        self.project = self.temp / "project"
        self.project.mkdir()
        self._environment = {k: os.environ.get(k) for k in
                             ("M2_REPO", "GRADLE_USER_HOME", "DEPENDENCYSKILLS_CODEX_DIR", "DEPENDENCYSKILLS_LOG")}
        os.environ.update(M2_REPO=str(self.maven), GRADLE_USER_HOME=str(self.gradle),
                          DEPENDENCYSKILLS_CODEX_DIR=str(self.temp / "home"),
                          DEPENDENCYSKILLS_LOG=str(self.temp / "log.jsonl"))
        self.store = Store()

    def tearDown(self):
        self.store.db.close()
        for key, value in self._environment.items():
            if value is None:
                os.environ.pop(key, None)
            else:
                os.environ[key] = value

    def publish(self, coordinate, skills=None, prefix="", description=None):
        """A sources jar in the local Maven repository. `skills` maps a path under skills/ to its content."""
        group, artifact, version = coordinate.split(":")
        directory = self.maven.joinpath(*group.split("."), artifact, version)
        directory.mkdir(parents=True, exist_ok=True)
        if skills is None:
            name = skill_name(group, artifact)
            skills = {f"{name}/SKILL.md": skill_text(name)}
        jar = directory / f"{artifact}-{version}-sources.jar"
        with zipfile.ZipFile(jar, "w") as archive:
            archive.writestr(f"{prefix}com/acme/Api.kt", "package com.acme\nclass Api\n")
            for path, content in skills.items():
                archive.writestr(f"{prefix}skills/{path}", content)
        pom = f"<project><description>{description}</description></project>" if description else "<project/>"
        (directory / f"{artifact}-{version}.pom").write_text(pom)
        return jar

    def gradle_publish(self, coordinate, skills):
        """A sources jar in the Gradle cache, under a checksum directory as Gradle files it."""
        group, artifact, version = coordinate.split(":")
        directory = self.gradle / "caches" / "modules-2" / "files-2.1" / group / artifact / version / "0a1b2c"
        directory.mkdir(parents=True, exist_ok=True)
        with zipfile.ZipFile(directory / f"{artifact}-{version}-sources.jar", "w") as archive:
            for path, content in skills.items():
                archive.writestr(f"commonMain/skills/{path}", content)

    def build(self, coordinates, declared=()):
        """What the Gradle plugin writes: a CycloneDX SBOM in the root build directory."""
        components = []
        for coordinate in coordinates:
            group, artifact, version = coordinate.split(":")
            properties = [{"name": "dependencyskills:declared", "value": "true"}] if coordinate in declared \
                else [{"name": "dependencyskills:project", "value": ":"}]
            components.append({"purl": f"pkg:maven/{group}/{artifact}@{version}", "properties": properties})
        sbom = self.project / "build" / "dependencyskills" / "bom.cdx.json"
        sbom.parent.mkdir(parents=True, exist_ok=True)
        sbom.write_text(json.dumps({"bomFormat": "CycloneDX", "specVersion": "1.6", "components": components}))
        # Distinct modification times, whatever the filesystem's resolution.
        stamp = sbom.stat().st_mtime_ns + len(coordinates) + len(list(self.temp.rglob("*.cdx.json"))) * 1_000_000_007
        os.utime(sbom, ns=(stamp, stamp))
