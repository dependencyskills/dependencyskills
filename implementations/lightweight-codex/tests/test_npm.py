import json
import os

from dependencyskills_lightweight.find import find
from dependencyskills_lightweight.lookup import get_file, get_skill, list_skills
from dependencyskills_lightweight.names import own_name
from dependencyskills_lightweight.project import refresh
from support import Machine, skill_text


class NpmTest(Machine):
    """An npm project needs no plugin: what package.json declares, at the version node_modules holds (RAD-0079)."""

    def install(self, name, version, skills=None, description="", declared=True, field="dependencies"):
        """A package as npm unpacks it into node_modules; `skills` maps a path under skills/ to its content."""
        directory = self.project / "node_modules" / name
        directory.mkdir(parents=True, exist_ok=True)
        (directory / "package.json").write_text(json.dumps({"name": name, "version": version, "description": description}))
        for path, content in (skills or {}).items():
            file = directory / "skills" / path
            file.parent.mkdir(parents=True, exist_ok=True)
            file.write_text(content)
        if declared:
            self.declare(name, f"^{version}", field)

    def declare(self, name, range_, field="dependencies"):
        manifest_path = self.project / "package.json"
        manifest = json.loads(manifest_path.read_text()) if manifest_path.is_file() else {"name": "acme-app"}
        manifest.setdefault(field, {})[name] = range_
        manifest_path.write_text(json.dumps(manifest))
        # What npm does on every install: a new time on the marker, whatever the filesystem's resolution.
        marker = self.project / "node_modules" / ".package-lock.json"
        marker.parent.mkdir(parents=True, exist_ok=True)
        marker.write_text("{}")
        stamp = marker.stat().st_mtime_ns + len(manifest.get(field, {})) * 1_000_000_007
        os.utime(marker, ns=(stamp, stamp))

    def test_the_naming_rule_puts_the_scope_where_a_group_would_be(self):
        self.assertEqual("acme-text", own_name("npm:@acme/text"))
        self.assertEqual("left-pad", own_name("npm:left-pad"))

    def test_serves_a_declared_packages_own_guide_then_its_others(self):
        self.install("@acme/text", "1.2.0", {
            "acme-text/SKILL.md": skill_text("acme-text", description="Format acme text."),
            "acme-text/references/react.md": "Use <AcmeText/> in React.",
            "acme-migrate/SKILL.md": skill_text("acme-migrate", description="Migrate from acme 1.", body="Rename shout to yell."),
            "acme-migrate/references/steps.md": "Step one.",
        })
        project = refresh(self.store, self.project)

        listing = list_skills(self.store, project)
        self.assertIn("npm:@acme/text 1.2.0  (2 guides)", listing)
        self.assertIn("Format acme text.", listing)
        guide = get_skill(self.store, project, "npm:@acme/text")
        self.assertLess(guide.index("Call Acme.normalize."), guide.index("Rename shout to yell."))
        self.assertIn("Another guide npm:@acme/text ships, named acme-migrate", guide)
        self.assertIn("- references/react.md", guide)
        self.assertIn("- acme-migrate/references/steps.md", guide)
        self.assertIn("Use <AcmeText/>", get_file(self.store, project, "npm:@acme/text", "references/react.md"))
        self.assertIn("Step one.", get_file(self.store, project, "npm:@acme/text", "acme-migrate/references/steps.md"))

    def test_a_bare_package_name_is_understood(self):
        self.install("@acme/text", "1.2.0", {"acme-text/SKILL.md": skill_text("acme-text")})
        project = refresh(self.store, self.project)

        self.assertIn("Call Acme.normalize.", get_skill(self.store, project, "@acme/text"))

    def test_dev_dependencies_count_and_undeclared_packages_do_not(self):
        self.install("@acme/lint", "2.0.0", {"acme-lint/SKILL.md": skill_text("acme-lint")}, field="devDependencies")
        self.install("@acme/transitive", "1.0.0", {"acme-transitive/SKILL.md": skill_text("acme-transitive")}, declared=False)
        project = refresh(self.store, self.project)

        listing = list_skills(self.store, project)
        self.assertIn("npm:@acme/lint 2.0.0", listing)
        self.assertNotIn("transitive", listing)
        self.assertIn("is not a dependency of this project", get_skill(self.store, project, "npm:@acme/transitive"))

    def test_a_declared_package_not_installed_yet_is_not_in_scope_until_it_is(self):
        self.install("@acme/text", "1.2.0", {"acme-text/SKILL.md": skill_text("acme-text")})
        self.declare("@acme/later", "^1.0.0")
        project = refresh(self.store, self.project)
        self.assertNotIn("@acme/later", list_skills(self.store, project))

        self.install("@acme/later", "1.0.0", {"acme-later/SKILL.md": skill_text("acme-later")})
        project = refresh(self.store, self.project)
        self.assertIn("npm:@acme/later 1.0.0", list_skills(self.store, project))

    def test_an_invalid_skill_is_not_served(self):
        self.install("@acme/text", "1.2.0", {"wrong-name/SKILL.md": skill_text("not-the-directory")})
        project = refresh(self.store, self.project)

        self.assertIn("None of this project's libraries ships a guide", list_skills(self.store, project))

    def test_search_finds_an_installed_package_the_project_did_not_declare(self):
        self.install("@acme/text", "1.2.0", {"acme-text/SKILL.md": skill_text("acme-text", description="Format acme text.")})
        self.install("date-helper", "3.0.0", description="Format a date for display.", declared=False)
        project = refresh(self.store, self.project)

        found = find(self.store, project, "format a date for display")
        self.assertIn("npm:date-helper (3.0.0) — no guide; not a dependency of this project", found)
        self.assertIn("its package description: Format a date for display.", found)
