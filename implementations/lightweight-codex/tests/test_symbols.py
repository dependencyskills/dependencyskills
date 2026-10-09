import json
import os

from dependencyskills_lightweight.lookup import get_symbol
from dependencyskills_lightweight.project import refresh
from support import Machine

FORMATS = '''package com.acme.text

/**
 * Formats this instant for display.
 *
 * @param style how much of it to show
 */
@Throws(IllegalStateException::class)
public fun Instant.format(
    style: Style = Style.Medium,
    zone: TimeZone,
): String {
    return render(this, style, zone).format()
}

class Money(val amount: Long, val currency: String) {
    /** Adds two amounts in the same currency. */
    operator fun plus(other: Money): Money = Money(amount + other.amount, currency)

    fun format(style: Style): String
}
'''


class SymbolTest(Machine):
    """One declaration's signature and doc comment, from the published source of the version in scope."""

    def setUp(self):
        super().setUp()
        self.publish("com.acme:acme-text:1.0", sources={"com/acme/text/Formats.kt": FORMATS})
        self.build(["com.acme:acme-text:1.0"])
        self.scope = refresh(self.store, self.project)

    def test_returns_the_signature_and_its_doc_comment_and_not_the_body_or_the_calls(self):
        answer = get_symbol(self.store, self.scope, "com.acme:acme-text", "format")

        self.assertIn("`format` in com.acme:acme-text 1.0: 2 declarations.", answer)
        self.assertIn("com/acme/text/Formats.kt:9", answer)
        self.assertIn("Formats this instant for display.", answer)
        self.assertIn("@Throws(IllegalStateException::class)", answer)
        self.assertIn("    zone: TimeZone,\n): String", answer)
        self.assertNotIn("render(this", answer)          # the body, and the call to another format() in it
        self.assertIn("the library's own source, as published", answer)

    def test_a_type_narrows_it_to_that_types_member(self):
        answer = get_symbol(self.store, self.scope, "com.acme:acme-text", "Money.format")

        self.assertIn("1 declaration.", answer)
        self.assertIn("fun format(style: Style): String", answer)
        self.assertNotIn("Instant.format", answer)

    def test_a_one_line_body_is_left_out_and_a_default_value_is_kept(self):
        answer = get_symbol(self.store, self.scope, "com.acme:acme-text", "Money.plus")

        self.assertIn("operator fun plus(other: Money): Money\n```", answer)
        self.assertNotIn("amount + other.amount", answer)
        self.assertIn("style: Style = Style.Medium,", get_symbol(self.store, self.scope, "com.acme:acme-text", "format"))

    def test_with_two_versions_in_scope_it_reads_the_newest_and_names_the_other(self):
        self.publish("com.acme:acme-text:1.10", sources={"com/acme/text/Formats.kt": FORMATS.replace("class Money", "class Cash")})
        self.build(["com.acme:acme-text:1.0", "com.acme:acme-text:1.10"])
        scope = refresh(self.store, self.project)

        answer = get_symbol(self.store, scope, "com.acme:acme-text", "Cash")
        self.assertIn("`Cash` in com.acme:acme-text 1.10: 1 declaration.", answer)
        self.assertIn("also resolves com.acme:acme-text at 1.0", answer)
        self.assertIn("class Money(", get_symbol(self.store, scope, "com.acme:acme-text:1.0", "Money"))

    def test_a_type_is_found_by_its_own_name(self):
        self.assertIn("class Money(val amount: Long, val currency: String)",
                      get_symbol(self.store, self.scope, "com.acme:acme-text", "Money"))

    def test_a_near_miss_names_what_is_close_and_a_parameter_is_not_a_declaration(self):
        self.assertIn("Close: format.", get_symbol(self.store, self.scope, "com.acme:acme-text", "Format"))
        self.assertIn("declares nothing named `zone`", get_symbol(self.store, self.scope, "com.acme:acme-text", "zone"))

    def test_only_this_projects_libraries_are_read(self):
        self.publish("com.acme:acme-other:1.0", sources={"com/acme/Other.kt": "class Other\n"})
        self.assertIn("is not a dependency of this project",
                      get_symbol(self.store, self.scope, "com.acme:acme-other", "Other"))

    def test_an_installed_packages_source_is_read_too(self):
        package = self.project / "node_modules" / "@acme" / "text"
        package.mkdir(parents=True)
        (package / "package.json").write_text(json.dumps({"name": "@acme/text", "version": "1.2.0"}))
        (package / "index.d.ts").write_text("/** Trim and fold. */\nexport declare function normalize(text: string): string;\n")
        (self.project / "package.json").write_text(json.dumps({"name": "acme-app", "dependencies": {"@acme/text": "^1.2.0"}}))
        (self.project / "node_modules" / ".package-lock.json").write_text("{}")
        os.remove(self.project / "build" / "dependencyskills" / "bom.cdx.json")
        scope = refresh(self.store, self.project)

        answer = get_symbol(self.store, scope, "@acme/text", "normalize")
        self.assertIn("export declare function normalize(text: string): string;", answer)
        self.assertIn("Trim and fold.", answer)
