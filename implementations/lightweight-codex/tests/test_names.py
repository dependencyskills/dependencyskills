import unittest

from dependencyskills_lightweight.names import library, skill_name


class NamesTest(unittest.TestCase):

    def test_the_same_vectors_as_the_gradle_plugin(self):
        # Shared with SkillPackagingTest: the two must name a skill identically, or none is found.
        vectors = {
            ("com.example.acme", "acme-text"): "com-example-acme-acme-text",
            ("com.example", "Acme_Text.core"): "com-example-acme-text-core",
            ("com.google.android.apps.common.testing.accessibility.framework", "accessibility-test-framework"):
                "cm-ge-ad-as-cn-tg-ay-fk-accessibility-test-framework",
            ("io.example.instrumentation", "example-instrumentation-annotations-support-library"):
                "io-ee-in-example-instrumentation-annotations-support-library",
            ("com.example", "an-artifact-name-so-long-that-even-a-compacted-group-cannot-save-it-at-all"):
                "cm-ee-an-artifact-name-so-long-that-even-a-compacted-gr-a08a1e4d",
        }
        for (group, artifact), expected in vectors.items():
            self.assertEqual(expected, skill_name(group, artifact))

    def test_a_platform_module_is_its_library(self):
        self.assertEqual("io.acme:text", library("io.acme:text-jvm:1.0"))
        self.assertEqual("io.acme:text", library("io.acme:text-wasm-js:1.0"))
        self.assertEqual("io.acme:text-tools", library("io.acme:text-tools:1.0"))
