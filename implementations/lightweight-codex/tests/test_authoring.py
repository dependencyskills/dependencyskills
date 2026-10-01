import json
import tempfile
import unittest
from pathlib import Path

from dependencyskills_lightweight import authoring
from support import skill_text


class NpmAuthoringTest(unittest.TestCase):
    """What the build plugins' name and check tasks do on the JVM, for an npm package, which needs no plugin."""

    def setUp(self):
        self.library = Path(tempfile.mkdtemp())
        self.manifest({"name": "@acme/text", "version": "1.2.0", "files": ["index.js", "skills"]})

    def manifest(self, package):
        (self.library / "package.json").write_text(json.dumps(package))

    def skill(self, name="acme-text", version="1.2.0"):
        path = self.library / "skills" / name / "SKILL.md"
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(skill_text(name, extra=f"metadata:\n  version: \"{version}\"\n"))

    def test_names_the_skill_from_the_package(self):
        found = authoring.describe(self.library)
        self.assertEqual("acme-text", found["name"])
        self.assertEqual("skills/acme-text/SKILL.md", found["path"])

    def test_a_correct_skill_has_nothing_to_say(self):
        self.skill()
        self.assertEqual([], authoring.warnings(self.library))

    def test_files_that_leave_the_skill_behind_are_named(self):
        self.skill()
        self.manifest({"name": "@acme/text", "version": "1.2.0", "files": ["index.js"]})
        self.assertTrue(any('without "skills"' in w for w in authoring.warnings(self.library)))

    def test_a_stale_version_and_a_missing_skill_are_named(self):
        self.skill(version="1.1.0")
        self.assertTrue(any("describes version '1.1.0'" in w for w in authoring.warnings(self.library)))
        (self.library / "skills" / "acme-text" / "SKILL.md").unlink()
        self.assertIn("there is no skills/acme-text/SKILL.md", authoring.warnings(self.library)[0])


if __name__ == "__main__":
    unittest.main()
