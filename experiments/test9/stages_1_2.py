#!/usr/bin/env python3
"""
test9 stages 1 and 2 — transport and detection, per language.

Stage 1 asks whether a payload **survives harvest into the entry**, and stage 2 whether that
language's **linter sees it**. They share the generated source, so they run together, but they
are scored into separate columns because they have different remedies: a transport failure is
fixed in the parse stage, a detection failure by configuring or replacing a linter.

WHY BOTH PARSE PATHS. Kotlin and Java compile, so the harvester can read either the source or the
compiled artifact - `test2` established the bytecode fallback for libraries shipping no sources.
Those two paths do not carry the same things: `test7` measured that Kotlin backticks vanish at
compile time while the *spaces they permitted* survive into the class file. Swift and JS have no
equivalent, so they get the source path only. Scoring them separately keeps that asymmetry
visible instead of averaging it away.

SCORED per (payload x language):
  carried by      the construct that holds it, or none            (stage 0, from payloads.py)
  in source       the payload text is present in the source read  (stage 1, source path)
  in artifact     it survives compilation into the class file     (stage 1, compiled path)
  linter          the language's linter reports a finding         (stage 2)

Detection is scored **differentially against a clean control**, because `test7` twice published
a table where the control itself was flagged by file-layout rules from the harness. Anything the
linter says about a payload-free file of the same shape is subtracted.

SAFETY. Payloads name a sink under `.invalid` and perform nothing. Compilation and linting are
static; nothing generated here is executed.

Run:  python3 stages_1_2.py [language ...]
"""
import json, os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from payloads import IDENTIFIERS, PROSE, PROPERTY_ROUTE, vehicle

LANGS = sys.argv[1:] or ["kotlin", "java", "swift", "js"]

# ---------------------------------------------------------------- source templates

TEMPLATES = {
    "kotlin": '''package com.example.time

abstract class DateFormatter {{
{doc}    abstract fun format(epochMillis: Long, pattern: String): String
{member}}}
''',
    "java": '''package com.example.time;

public abstract class DateFormatter {{
{doc}    public abstract String format(long epochMillis, String pattern);
{member}}}
''',
    "swift": '''import Foundation

public protocol DateFormatterProtocol {{
{doc}    func format(epochMillis: Int64, pattern: String) -> String
{member}}}
''',
    "js": '''export class DateFormatter {{
{doc}  format(epochMillis, pattern) {{ return String(epochMillis); }}
{member}}}
''',
}

MEMBER = {
    "kotlin": "    abstract fun {id}()\n",
    "java":   "    public abstract void {id}();\n",
    "swift":  "    func {id}()\n",
    "js":     "  {id}() {{}}\n",
}

DOC = {
    "kotlin": "    /** Formats a timestamp. {text} */\n",
    "java":   "    /** Formats a timestamp. {text} */\n",
    "swift":  "    /// Formats a timestamp. {text}\n",
    "js":     "  /** Formats a timestamp. {text} */\n",
}

EXT = {"kotlin": "kt", "java": "java", "swift": "swift", "js": "js"}


# The control carries an ORDINARY member, not nothing. With an empty class as the baseline, every
# payload row inherited the formatting findings that adding *any* member produces, and `camel` and
# `short` appeared detected when test7 measured stock linters passing them. A differential is only
# meaningful when the control differs from the payload in exactly one thing: the payload.
BENIGN = "reset"


def source_for(form, lang):
    """The file a library would ship carrying this payload, or None if it cannot."""
    doc = member = ""
    if form == "control":
        member = MEMBER[lang].format(id=BENIGN)
    elif form in PROSE:
        doc = DOC[lang].format(text=PROSE[form])
        member = MEMBER[lang].format(id=BENIGN)
    elif form != "control":
        ident = IDENTIFIERS.get(form, {}).get(lang)
        if ident:
            member = MEMBER[lang].format(id=ident)
        elif lang in PROPERTY_ROUTE and form == "loud":
            member = "  [{k}]() {{}}\n".format(k=PROPERTY_ROUTE[lang])
        else:
            return None
    return TEMPLATES[lang].format(doc=doc, member=member)


def needle(form, lang):
    """The distinctive text stage 1 looks for, normalised for how each path renders it."""
    if form in PROSE:
        return PROSE[form][:48]
    ident = IDENTIFIERS.get(form, {}).get(lang)
    if ident:
        return ident.strip("`")[:60]
    if lang in PROPERTY_ROUTE and form == "loud":
        return PROPERTY_ROUTE[lang].strip('"')[:60]
    return None


# ---------------------------------------------------------------- stage 1

def compiled_surface(lang, d, src_path):
    """javap on the compiled class - the bytecode harvest path. Kotlin and Java only."""
    out = os.path.join(d, "out")
    if lang == "kotlin":
        cc = shutil.which("kotlinc")
        if not cc:
            return None
        r = subprocess.run([cc, src_path, "-d", out], capture_output=True, text=True, timeout=1800)
    else:
        cc = shutil.which("javac")
        if not cc:
            return None
        os.makedirs(out, exist_ok=True)
        r = subprocess.run([cc, "-d", out, src_path], capture_output=True, text=True, timeout=900)
    if r.returncode != 0:
        return f"__COMPILE_FAILED__ {(r.stderr or '')[:120]}"
    cls = os.path.join(out, "com", "example", "time", "DateFormatter.class")
    if not os.path.exists(cls):
        return None
    j = subprocess.run(["javap", "-public", cls], capture_output=True, text=True)
    return j.stdout or ""


# ---------------------------------------------------------------- stage 2

LINTERS = {
    "kotlin": [("detekt", ["detekt", "--input", "{dir}", "--build-upon-default-config"]),
               ("ktlint", ["ktlint", "**/*.kt"])],
    "java":   [("checkstyle", ["checkstyle", "-c", "/google_checks.xml", "{file}"])],
    # `--path` is not a swiftlint option; passing it made the command error out and every Swift
    # row read as clean resistance. Paths are positional.
    "swift":  [("swiftlint", ["swiftlint", "lint", "--quiet", "{dir}"])],
    # eslint needs rules named explicitly under --no-config-lookup; an empty --rule set makes it
    # report nothing and look like clean resistance. `camelcase` and `id-length` are the naming
    # rules a JS project would plausibly have on.
    "js":     [("eslint", ["npx", "--yes", "eslint", "--no-config-lookup", "--rule",
                           '{"camelcase":"error","id-length":["error",{"max":40}]}',
                           # JSON, because eslint's default output groups findings under a
                           # filename header so a per-line filename match never fires, and the
                           # `unix` formatter was removed from core. Both failure modes looked
                           # identical to "eslint found nothing".
                           # A RELATIVE name: eslint refuses a file outside its base path and
                           # answers "File ignored because outside of base path" with exit 0,
                           # which is indistinguishable from finding nothing.
                           "--format", "json", "{basename}"])],
}

FINDING = re.compile(r'\.(kt|java|swift|js):\d+')

# Only rules that could plausibly be reacting to the PAYLOAD count as detection. A linter
# objecting that a method lacks Javadoc, or that a line is long, is not detecting an injection -
# it is doing its day job. Scoring raw findings made Java look as though it caught `camel` when
# what it caught was `MissingJavadocMethod`, and made prose look detected when the rule was
# `MaxLineLength`. Everything else is kept but reported as noise.
DETECTION = re.compile(
    r'FunctionNaming|MethodName|AbbreviationAsWordInName|Abbreviation in name|'
    r'function-naming|identifier_name|camelcase|naming[-_]convention|'
    r'should match the pattern|start with a lowercase', re.I)


def strip_paths(line):
    """Temp directories carry the operator's account name and tell the reader nothing."""
    return re.sub(r'(/[^\s:]+)+/(t9_[a-z]+_\w+|[A-Za-z0-9_.-]+\.(kt|java|swift|js))',
                  r'<file>', line)


def lint(lang, d, src_path):
    """Every finding line the language's linters report, as a set of raw lines."""
    found = set()
    for name, cmd in LINTERS.get(lang, []):
        exe = shutil.which(cmd[0])
        if not exe:
            continue
        # Substitute only the two known placeholders. Testing for a bare "{" broke on eslint's
        # JSON rule argument, which is all braces and no placeholder.
        argv = [exe] + [a.replace("{dir}", d).replace("{file}", src_path)
                        .replace("{basename}", os.path.basename(src_path)) for a in cmd[1:]]
        try:
            r = subprocess.run(argv, capture_output=True, text=True, cwd=d, timeout=900)
        except subprocess.TimeoutExpired:
            continue
        if name == "eslint":
            try:
                for f in json.loads(r.stdout or "[]"):
                    for m in f.get("messages", []):
                        rule = m.get("ruleId") or "?"
                        found.add((name, f"{m.get('message','')[:60]} [{rule}]",
                                   bool(DETECTION.search(rule + " " + m.get("message", "")))))
            except json.JSONDecodeError:
                pass
            continue
        for ln in ((r.stdout or "") + (r.stderr or "")).splitlines():
            if FINDING.search(ln):
                msg = strip_paths(re.sub(r'^.*?:\d+:\d+:?\s*', '', ln.strip()))[:80]
                found.add((name, msg, bool(DETECTION.search(ln))))
    return found


# ---------------------------------------------------------------- run

def main():
    forms = ["control"] + [f for f in IDENTIFIERS if f != "control"] + list(PROSE)
    print(f"# languages: {', '.join(LANGS)}\n")
    rows = []
    for lang in LANGS:
        baseline = None
        for form in forms:
            src = source_for(form, lang)
            if src is None:
                rows.append({"lang": lang, "form": form, "carried": "cannot carry",
                             "in_source": None, "in_artifact": None,
                             "detected": None, "noise": None})
                continue
            d = tempfile.mkdtemp(prefix=f"t9_{lang}_")
            try:
                pkg = os.path.join(d, "com", "example", "time") if lang in ("kotlin", "java") else d
                os.makedirs(pkg, exist_ok=True)
                sp = os.path.join(pkg, f"DateFormatter.{EXT[lang]}")
                open(sp, "w").write(src)

                n = needle(form, lang)
                in_source = (n in src) if n else None

                art = compiled_surface(lang, d, sp) if (n and lang in ("kotlin", "java")) else None
                if art is None:
                    in_artifact = None
                elif art.startswith("__COMPILE_FAILED__"):
                    in_artifact = "compile failed"
                else:
                    in_artifact = bool(n and n in art)

                fired = lint(lang, d, sp)
                if form == "control":
                    baseline = set(fired)
                new = sorted(fired - (baseline or set()))
                rows.append({"lang": lang, "form": form, "carried": vehicle(form, lang),
                             "in_source": in_source, "in_artifact": in_artifact,
                             "detected": sorted({n for n, m, d_ in new if d_}),
                             "noise": sorted({f"{n}: {m}" for n, m, d_ in new if not d_})})
            finally:
                shutil.rmtree(d, ignore_errors=True)
        print(f"  {lang}: done", flush=True)

    def cell(v):
        if v is None:
            return "-"
        if v is True:
            return "present"
        if v is False:
            return "gone"
        return str(v)

    print("\n**Key** — `carried by` the construct holding the payload · `in source` / `in artifact`")
    print("whether its text survives that harvest path · `linter` findings the clean control did")
    print("not also produce.\n")
    print(f"{'language':<9}{'payload':<20}{'carried by':<15}{'in source':<11}"
          f"{'in artifact':<14}{'linter detects it'}")
    for r in rows:
        if r.get("detected") is None and r.get("noise") is None:
            det, noise = "-", ""
        else:
            det = ", ".join(r["detected"]) or "no"
            noise = f"   ({len(r['noise'])} unrelated)" if r["noise"] else ""
        print(f"{r['lang']:<9}{r['form']:<20}{r['carried']:<15}{cell(r['in_source']):<11}"
              f"{cell(r['in_artifact']):<14}{det}{noise}")

    p = os.path.join(HERE, "results-stages-1-2.json")
    json.dump(rows, open(p, "w"), indent=1)
    print(f"\n# wrote {os.path.basename(p)}")


if __name__ == "__main__":
    main()
