import unittest
from pathlib import Path

from dependencyskills_lightweight.skillfile import check, frontmatter

SKILLS = Path(__file__).resolve().parents[2] / "agent-skills"


class OurSkillsTest(unittest.TestCase):
    """The skills this project ships follow the Agent Skills specification (https://agentskills.io/specification).

    The same check the lookup applies to a library's skill, applied to our own: a name of 1-64 lowercase
    letters, digits and single hyphens that matches its directory, a description within its limit, and
    frontmatter the specification defines. The plugin and the installer write these into other people's
    projects, so one that broke the standard would break there, not here.
    """

    def test_each_skill_we_ship_follows_the_specification(self):
        shipped = sorted(p.parent for p in SKILLS.glob("*/SKILL.md"))
        self.assertEqual(["librarian", "librarian-skill-author"], [p.name for p in shipped])
        for directory in shipped:
            with self.subTest(skill=directory.name):
                fields, body = frontmatter((directory / "SKILL.md").read_text())
                errors, _ = check(fields, directory.name)
                self.assertEqual([], errors)
                self.assertTrue(body.strip(), "a SKILL.md needs instructions after its frontmatter")
                # Only the directories the specification names, beside SKILL.md.
                extra = {p.name for p in directory.iterdir()} - {"SKILL.md", "references", "assets", "scripts"}
                self.assertEqual(set(), extra)


if __name__ == "__main__":
    unittest.main()
