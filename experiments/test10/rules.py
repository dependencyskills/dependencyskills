#!/usr/bin/env python3
"""
test10 — the candidate rule catalogue. Add a rule, re-run, watch the gap close.

[RAD-0034](../../docs/knowledge/research/RAD-0034-better-linters-or-better-configuration.md) measured
that every ecosystem can enforce a naming bound through the linter it already runs - SwiftLint
`custom_rules`, Checkstyle `MethodName`, ESLint `id-match`, detekt `FunctionNaming` /
`FunctionMaxLength`. This catalogue is the set of bounds to enforce, kept separate from the runner
so a rule can be added, adjusted or withdrawn without touching the harness.

WHAT A RULE IS. Each entry is a **regex over harvested text**, plus the mechanism that would
enforce it in each ecosystem. Evaluating the regex directly is an approximation of running four
linters, taken deliberately so the catalogue can be iterated in seconds rather than minutes;
RAD-0034 already verified each mechanism can express this shape. A rule that survives iteration
here should be re-checked through the real tool before it is proposed.

EVERY RULE CARRIES ITS COST. A bound is only usable if its false-rejection rate on real library
content is known, so `evaluate.py` prices each one against `test5`'s 14,899-entry harvest. A rule
with no cost line has not been measured and must not be adopted -
[RAD-0021](../../docs/knowledge/research/RAD-0021-admission-control-at-harvest.md) was withdrawn for
exactly that.

WHAT THIS CANNOT DO. These are constraints on the *form* of an identifier or a doc comment. They
do not touch prose that is well-formed and merely false
([RAD-0029](../../docs/knowledge/research/RAD-0029-the-agent-as-a-trust-launderer.md)), and they do not
touch the 46% of published attacks that need no precondition at all
([RAD-0031](../../docs/knowledge/research/RAD-0031-which-vectors-reach-a-real-project.md)).
"""
import re


# A DECLARATION, in the four grammars, as an alternation:
#   kotlin/swift  fun name(   func name(
#   java          <modifiers> <type> name(
#   js            two-space-indented  name(   - no keyword at all
# A generic "capitalised word before a bracket" was tried and cost 41.8% of the real corpus,
# because it matches every constructor call - `String(epochMillis)` among them. Declaration
# context is not optional; a real linter has it for free because it parses the grammar.
DECL = (r'(?:(?:\bfun|\bfunc)\s+'
        r'|\b(?:public|protected|private|internal|static|final|abstract)\s+(?:[\w<>\[\].]+\s+)*'
        r'|^[ \t]{2,})')

# RESOLUTION IS THE UNIFYING CHECK. A rule that flags a *name* should ask whether that name
# resolves to anything the library actually declares. This is the one check a codex can make and
# a linter cannot, because the codex already holds the graph: `test2` established that bytecode
# supertype edges are fully qualified, so resolve-in-index needs no import resolution.
#
# It is NOT RAD-0021's withdrawn signal. That grounded *prose claims* against structure, which is
# a semantic judgement and failed at 26.9% false positives on a real graph. This resolves a
# *token* against a declared surface, which is mechanical.
#
# A rule may therefore be a regex, or a callable (text, ctx) -> bool where ctx["declared"] is the
# set of simple names the surface declares.

CAP_DECL = re.compile(
    r'(?:(?:\bfun|\bfunc)\s+'
    r'|\b(?:public|protected|private|internal|static|final|abstract|expect|actual|open|inline)'
    r'\s+(?:[\w<>\[\].]+\s+)*'
    r'|^[ \t]{2,})([A-Z_][A-Za-z0-9_]{3,})\s*\(', re.M)
ANNOT_ARG = re.compile(r'@\w+\s*\([^)]*$')
SPELLED = re.compile(r'\b(\w+)\s+dot\s+(\w+)\b', re.I)
SPELLED_OK = re.compile(r'\bdot\s+(product|notation|operator|syntax|separated|files?|char|'
                        r'character|above|below)\b', re.I)


def unresolved_capitalised_declaration(text, ctx):
    """A declaration named like a type, whose name is not a type this surface declares.

    `public fun HttpClient(` is a factory function and legitimate *because* `HttpClient` is
    declared. `ReplaceWith(` is legitimate *because* it sits inside an annotation argument. Both
    exemptions need context a regex alone does not have, which is why a plain casing pattern cost
    6.3% of the corpus.
    """
    for m in CAP_DECL.finditer(text or ""):
        before = (text or "")[max(0, m.start() - 160):m.start()]
        if ANNOT_ARG.search(before):
            continue
        if m.group(1) not in ctx.get("declared", ()):
            return True
    return False


def unresolved_spelled_reference(text, ctx):
    """Punctuation spelled out as words, where the reconstructed token resolves to nothing.

    Somebody writing `kotlin dot text dot Regex` in prose is naming something real, oddly. An
    attacker writing `config dot env` is naming a file the library never declares.
    """
    t = " ".join((text or "").split())
    qualified = ctx.get("qualified", ())
    for m in SPELLED.finditer(t):
        if SPELLED_OK.search(m.group(0)):
            continue
        # Resolve the RECONSTRUCTED token, not its halves. Checking the halves exempted
        # `config dot env` outright, because `config` is a declared name somewhere in a
        # 14,899-symbol corpus - which is true of almost any common word.
        if f"{m.group(1)}.{m.group(2)}".lower() in qualified:
            continue
        return True
    return False


# Each rule: id -> (what it rejects, regex, per-ecosystem mechanism, why it is believed)
RULES = {
    "no_spaces_in_identifier": (
        "an identifier containing a space",
        r'\bfun\s+`[^`]*\s[^`]*`|\bfunc\s+`[^`]*\s',
        {"kotlin": "detekt FunctionNaming", "swift": "SwiftLint custom_rules",
         "java": "n/a - inexpressible", "js": "n/a - inexpressible"},
        "0 of 14,899 real entries; Kotlin backticks are the only vehicle and they live in test "
        "sources, which are never harvested",
    ),
    # DELEGATED, not approximable. A regex for "capitalised name before a bracket" matched 940
    # real entries - 6.3% - and every sample was legitimate: `public fun HttpClient(` and
    # `public fun URLBuilder(` are factory functions named after the type they build, `public
    # class LocalFileContent(` is a class declaration, and `ReplaceWith(` sits inside @Deprecated.
    # ktlint's own message says "except factory methods" because it parses the grammar and can
    # tell the difference. This bound is real and belongs in the pipeline - enforced by the
    # ecosystem's linter, which RAD-0034 verified can express it - but it cannot be approximated
    # here, so it is excluded from the catalogue's cost rather than reported with a false price.
    "unresolved_capitalised_decl": (
        "a type-shaped declaration whose name this surface never declares",
        # Language-agnostic: an identifier immediately before "(", not preceded by a dot. Keying
        # on fun/func/void keywords let JS escape entirely, because a JS method declaration is a
        # bare `name() {}` with no keyword at all.
        unresolved_capitalised_declaration,
        {"kotlin": "detekt FunctionNaming", "java": "Checkstyle MethodName",
         "swift": "SwiftLint custom_rules", "js": "ESLint id-match"},
        "the ecosystem convention every style guide already states",
    ),
    "identifier_word_count": (
        "a declaration name longer than the measured p99 of six words",
        DECL + r'[a-z]+(?:[A-Z][a-z0-9]*){6,}\s*\(',
        {"kotlin": "detekt FunctionMaxLength", "java": "Checkstyle MethodName",
         "swift": "SwiftLint custom_rules", "js": "ESLint id-match"},
        "real functions are a median of 2 words and a p99 of 6; the rule costs 0.47% on Kotlin",
    ),
    "constant_word_count": (
        "an ALL_CAPS constant longer than four words",
        # Scoped to a DECLARATION. Run over arbitrary entry text this cost 0.456%, entirely on
        # Kotlin compiler diagnostic names inside @Suppress(...) arguments - 15 distinct tokens,
        # none of them a declared symbol. The measured bound is on declarations, and applying it
        # to any text is a different and more expensive rule.
        r'(?:\b(?:val|var|const|let|static\s+final\s+\w+)\s+)'
        r'[A-Z][A-Z0-9]*(?:_[A-Z0-9]+){4,}\b',
        {"kotlin": "detekt", "java": "Checkstyle", "swift": "SwiftLint", "js": "ESLint"},
        "no ALL_CAPS identifier in the entire corpus exceeds four words",
    ),
    "spelled_out_punctuation": (
        "punctuation spelled out, where the reconstructed name resolves to nothing",
        unresolved_spelled_reference,
        {"kotlin": "detekt ForbiddenPattern", "java": "Checkstyle RegexpSingleline",
         "swift": "SwiftLint custom_rules", "js": "ESLint no-restricted-syntax"},
        "0 of 14,899; and constraint-induced - identifier grammar forbids '.' and '/', so "
        "smuggling a path through one forces the spelling out",
    ),
    "filesystem_path": (
        "a filesystem path in documentation or a signature",
        r'(?<![\w.])/(etc|usr|opt|var|tmp|root|home|Users)/|~/[\w.]|\.env\b|\.ssh/|id_rsa',
        {"kotlin": "detekt ForbiddenPattern", "java": "Checkstyle RegexpSingleline",
         "swift": "SwiftLint custom_rules", "js": "ESLint no-restricted-syntax"},
        "4 of 14,899 real entries mention one - 0.027%",
    ),
}

# Payload forms this catalogue is measured against, imported by the runner from test9 so the two
# phases stay on one payload set rather than drifting apart.
PAYLOAD_SOURCE = "../test9/payloads.py"


def summary():
    print(f"{'rule':<26}{'rejects':<48}{'mechanisms'}")
    for rid, (what, _, mech, _why) in RULES.items():
        live = ", ".join(k for k, v in mech.items() if not v.startswith("n/a"))
        print(f"{rid:<26}{what:<48}{live}")


if __name__ == "__main__":
    summary()
