import os
import tempfile
import unittest
from pathlib import Path

from dependencyskills_lightweight import authoring
from dependencyskills_lightweight.find import find
from dependencyskills_lightweight.lookup import get_skill, list_skills
from dependencyskills_lightweight.project import refresh
from support import Machine, skill_text


def write(path, text):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(text)


class PythonTest(Machine):
    """A virtual environment's site-packages, laid out as pip and uv install a wheel."""

    def setUp(self):
        super().setUp()
        self.site = self.project / ".venv" / "lib" / "python3.13" / "site-packages"
        write(self.project / ".venv" / "pyvenv.cfg", "home = /usr/bin\n")
        self.site.mkdir(parents=True)

    def install(self, name, version, package, skills=None, summary=""):
        dist = self.site / f"{package}-{version}.dist-info"
        write(dist / "METADATA", f"Metadata-Version: 2.4\nName: {name}\nVersion: {version}\nSummary: {summary}\n\nLong text.\n")
        record = [f"{package}/__init__.py,,"]
        write(self.site / package / "__init__.py", "")
        for path, content in (skills or {}).items():
            write(self.site / package / "skills" / path, content)
            record.append(f"{package}/skills/{path},,")
        write(dist / "RECORD", "\n".join(record) + "\n")

    def test_serves_a_declared_distributions_guide_from_inside_its_package(self):
        write(self.project / "pyproject.toml", '[project]\nname = "acme-app"\ndependencies = ["Acme_Text>=1.0"]\n')
        self.install("Acme_Text", "1.0.0", "acme_text", {"acme-text/SKILL.md": skill_text("acme-text")})
        self.install("date-helper", "2.0", "date_helper", summary="Format a date for display.")
        project = refresh(self.store, self.project)

        self.assertIn("pypi:acme-text 1.0.0", list_skills(self.store, project))
        self.assertIn("Call Acme.normalize.", get_skill(self.store, project, "acme-text"))
        self.assertIn("pypi:date-helper (2.0) — no guide; not a dependency of this project",
                      find(self.store, project, "format a date for display"))

    def test_requirements_and_dependency_groups_are_declarations_too(self):
        write(self.project / "requirements.txt", "# pinned\nacme-text==1.0.0\n")
        write(self.project / "pyproject.toml", '[project]\nname = "acme-app"\n[dependency-groups]\ndev = ["acme-lint"]\n')
        self.install("acme-text", "1.0.0", "acme_text", {"acme-text/SKILL.md": skill_text("acme-text")})
        self.install("acme-lint", "0.5", "acme_lint", {"acme-lint/SKILL.md": skill_text("acme-lint")})
        listing = list_skills(self.store, refresh(self.store, self.project))

        self.assertIn("pypi:acme-text 1.0.0", listing)
        self.assertIn("pypi:acme-lint 0.5", listing)


class GoTest(Machine):
    """The module cache, laid out as `go mod download` fills it."""

    def setUp(self):
        super().setUp()
        self.cache = self.temp / "gomod"
        self._saved_cache = os.environ.get("GOMODCACHE")
        os.environ["GOMODCACHE"] = str(self.cache)

    def tearDown(self):
        if self._saved_cache is None:
            os.environ.pop("GOMODCACHE", None)
        else:
            os.environ["GOMODCACHE"] = self._saved_cache
        super().tearDown()

    def test_serves_a_directly_required_modules_guide_and_follows_a_local_replace(self):
        write(self.cache / "example.com" / "!acme" / "text@v1.2.0" / "skills" / "example-com-acme-text" / "SKILL.md",
              skill_text("example-com-acme-text"))
        write(self.cache / "example.com" / "acme" / "indirect@v0.1.0" / "skills" / "example-com-acme-indirect" / "SKILL.md",
              skill_text("example-com-acme-indirect"))
        write(self.temp / "local" / "skills" / "example-com-acme-local" / "SKILL.md", skill_text("example-com-acme-local"))
        write(self.project / "go.mod", "module example.com/acme/app\n\ngo 1.27\n\nrequire (\n"
              "\texample.com/Acme/text v1.2.0\n\texample.com/acme/indirect v0.1.0 // indirect\n"
              "\texample.com/acme/local v0.0.0\n)\n\nreplace example.com/acme/local => ../local\n")
        listing = list_skills(self.store, refresh(self.store, self.project))

        self.assertIn("golang:example.com/Acme/text v1.2.0", listing)
        self.assertIn("golang:example.com/acme/local v0.0.0", listing)
        self.assertNotIn("indirect", listing)


class CargoTest(Machine):
    """Cargo's registry sources, laid out as a build unpacks them."""

    def setUp(self):
        super().setUp()
        self.registry = self.temp / "cargo" / "registry" / "src" / "index.crates.io-0000000000000000"
        self._saved_home = os.environ.get("CARGO_HOME")
        os.environ["CARGO_HOME"] = str(self.temp / "cargo")

    def tearDown(self):
        if self._saved_home is None:
            os.environ.pop("CARGO_HOME", None)
        else:
            os.environ["CARGO_HOME"] = self._saved_home
        super().tearDown()

    def crate(self, name, version, skill=True, description=""):
        root = self.registry / f"{name}-{version}"
        write(root / "Cargo.toml", f'[package]\nname = "{name}"\nversion = "{version}"\ndescription = "{description}"\n')
        if skill:
            own = name.replace("_", "-")
            write(root / "skills" / own / "SKILL.md", skill_text(own))

    def test_serves_a_declared_crates_guide_at_the_locked_version_including_a_renamed_one(self):
        self.crate("acme_text", "0.3.1")
        self.crate("acme_time", "1.0.0")
        self.crate("date_helper", "2.0.0", skill=False, description="Format a date for display.")
        write(self.project / "Cargo.toml", '[package]\nname = "acme-app"\nversion = "0.1.0"\n\n[dependencies]\n'
              'acme_text = "0.3"\ntime = { package = "acme_time", version = "1" }\n')
        source = 'source = "registry+https://github.com/rust-lang/crates.io-index"'
        write(self.project / "Cargo.lock", "version = 4\n\n"
              f'[[package]]\nname = "acme_text"\nversion = "0.3.1"\n{source}\n\n'
              f'[[package]]\nname = "acme_time"\nversion = "1.0.0"\n{source}\n\n'
              f'[[package]]\nname = "date_helper"\nversion = "2.0.0"\n{source}\n')
        project = refresh(self.store, self.project)
        listing = list_skills(self.store, project)

        self.assertIn("cargo:acme_text 0.3.1", listing)
        self.assertIn("cargo:acme_time 1.0.0", listing)
        self.assertIn("cargo:date_helper (2.0.0) — no guide; not a dependency of this project",
                      find(self.store, project, "format a date for display"))


class AuthoringTest(unittest.TestCase):
    """The name and check a build plugin gives a JVM library, for Python, Go and Cargo."""

    def setUp(self):
        self.library = Path(tempfile.mkdtemp())

    def test_a_python_skill_belongs_inside_the_import_package(self):
        write(self.library / "pyproject.toml", '[project]\nname = "Acme.Text"\nversion = "1.0"\n'
              '[build-system]\nbuild-backend = "setuptools.build_meta"\n')
        (self.library / "src" / "acme_text").mkdir(parents=True)
        found = authoring.describe(self.library)
        self.assertEqual(("acme-text", "src/acme_text/skills/acme-text/SKILL.md"), (found["name"], found["path"]))

        write(self.library / found["path"], skill_text("acme-text", extra='metadata:\n  version: "1.0"\n'))
        self.assertTrue(any("package-data" in w for w in authoring.warnings(self.library)))

    def test_a_go_module_is_named_for_its_path_and_has_no_version_to_check(self):
        write(self.library / "go.mod", "module example.com/acme/text\n\ngo 1.27\n")
        found = authoring.describe(self.library)
        self.assertEqual("skills/example-com-acme-text/SKILL.md", found["path"])
        write(self.library / found["path"], skill_text("example-com-acme-text"))
        self.assertEqual([], authoring.warnings(self.library))

    def test_a_crates_include_list_that_drops_skills_is_named(self):
        write(self.library / "Cargo.toml", '[package]\nname = "acme_text"\nversion = "0.3.1"\ninclude = ["src/**"]\n')
        found = authoring.describe(self.library)
        self.assertEqual("skills/acme-text/SKILL.md", found["path"])
        write(self.library / found["path"], skill_text("acme-text", extra='metadata:\n  version: "0.3.1"\n'))
        self.assertTrue(any('"include" without skills/' in w for w in authoring.warnings(self.library)))


if __name__ == "__main__":
    unittest.main()
