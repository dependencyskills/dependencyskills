#!/usr/bin/env python3
"""
test7, follow-up 5 — do the JVM's SECURITY analysers catch this, off the shelf?

[RAD-0028](../../docs/knowledge/research/RAD-0028-sast-tooling-as-a-detection-layer.md) question 1.
`lint_as_detector.py` showed stock detekt and ktlint already flag every identifier-borne payload
measured to produce harm. This asks what the security-specific tools add **before anyone writes
a custom rule** — the point being that a rule we author forfeits ADR-0007's "conform rather than
invent" benefit and re-opens RAD-0021's failure mode, so the off-the-shelf number is worth
having first.

RAD-0028 predicts **nothing**, on the grounds that SAST hunts code that misbehaves at runtime
while this payload executes nothing and misbehaves at *reading* time. That is reasoning, not
evidence. This is the falsification attempt.

TOOLS

  spotbugs + find-sec-bugs   bytecode analysis; the JVM's actual SAST layer. Reads the compiled
                             class, where doc comments do not exist at all - so it can only ever
                             see the identifier.
  semgrep                    generic AST/regex matching over SOURCE, run with its published
                             rulesets only. The one candidate that could see comments.

SCORED DIFFERENTIALLY against a clean control, so anything a tool says about a file with no
payload in it cancels. That correction was forced twice while building `lint_as_detector.py`:
scoring raw findings flagged the control both times.

PRIVACY. semgrep runs locally, but is invoked with `--metrics=off` so no usage data leaves the
machine. Payloads are inert and name a path without performing anything.

Run: python3 sast_as_detector.py
"""
import json, os, re, shutil, subprocess, sys, tempfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from lint_as_detector import CASES as _CASES, SRC   # same payload set, not restated

# POSITIVE CONTROL. Without one, "0 payloads flagged" is indistinguishable from "the harness
# silently did nothing" - which is the failure mode this whole run exists to avoid. MD5 is a
# textbook find-sec-bugs trigger (WEAK_MESSAGE_DIGEST_MD5) and a standard semgrep audit finding.
# If this row does not FLAG, every other row is uninterpretable.
POSITIVE = """    fun hashIt(input: String): String {
        val md = java.security.MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("")
    }"""

CASES = dict(_CASES)
CASES["POSITIVE CONTROL"] = POSITIVE

# Published rulesets only. `p/security-audit` and `p/secrets` are the ones that would plausibly
# fire on a payload naming a credentials file; `p/kotlin` is the language pack.
SEMGREP_CONFIGS = ["p/security-audit", "p/secrets", "p/kotlin"]

FSB_COORD = ("https://repo1.maven.org/maven2/com/h3xstream/findsecbugs/"
             "findsecbugs-plugin/1.14.0/findsecbugs-plugin-1.14.0.jar")


def write_case(d, body):
    pkg = os.path.join(d, "src", "com", "example", "time")
    os.makedirs(pkg, exist_ok=True)
    p = os.path.join(pkg, "DateFormatter.kt")
    open(p, "w").write(SRC.replace("{body}", body))
    return p


def compile_case(d):
    """SpotBugs reads bytecode, so each case has to be compiled before it can be scanned."""
    out = os.path.join(d, "out")
    src = os.path.join(d, "src")
    r = subprocess.run([shutil.which("kotlinc"), src, "-d", out],
                       capture_output=True, text=True, timeout=1800)
    return out if r.returncode == 0 else None


def fetch_fsb(cache):
    jar = os.path.join(cache, "findsecbugs-plugin.jar")
    if os.path.exists(jar):
        return jar
    os.makedirs(cache, exist_ok=True)
    r = subprocess.run(["curl", "-sSfL", "-o", jar, FSB_COORD], capture_output=True, text=True)
    return jar if r.returncode == 0 else None


def kotlin_stdlib():
    """SpotBugs needs kotlin-stdlib on the auxiliary classpath or it reports 'classes needed for
    analysis were missing' and skips work. A null result from a tool that could not resolve its
    types is not a null result - the first run of this script hit exactly that."""
    for c in ("/opt/homebrew/opt/kotlin/libexec/lib/kotlin-stdlib.jar",):
        if os.path.exists(c):
            return c
    kc = shutil.which("kotlinc")
    if kc:
        guess = os.path.join(os.path.dirname(os.path.realpath(kc)), "..", "lib",
                             "kotlin-stdlib.jar")
        if os.path.exists(guess):
            return os.path.realpath(guess)
    return None


def run_spotbugs(classes, plugin):
    exe = shutil.which("spotbugs")
    if not exe or not classes:
        return ""
    cmd = [exe, "-textui", "-low"]
    aux = kotlin_stdlib()
    if aux:
        cmd += ["-auxclasspath", aux]
    if plugin:
        cmd += ["-pluginList", plugin]
    cmd += [classes]
    r = subprocess.run(cmd, capture_output=True, text=True, timeout=900)
    return (r.stdout or "") + (r.stderr or "")


def run_semgrep(srcdir):
    """Returns the set of rule ids directly - semgrep gives structured JSON, so putting its
    output through a text parser only creates a chance to lose findings, which is what the first
    version did."""
    exe = shutil.which("semgrep")
    if not exe:
        return set()
    out = set()
    for cfg in SEMGREP_CONFIGS:
        r = subprocess.run([exe, "scan", "--config", cfg, "--metrics=off",
                            "--json", "--quiet", srcdir],
                           capture_output=True, text=True, timeout=1200)
        try:
            for f in json.loads(r.stdout or "{}").get("results", []):
                out.add(f.get("check_id", "?"))
        except json.JSONDecodeError:
            pass
    return out


# SpotBugs -textui emits `<priority> <category> <CODE>: <message>` - e.g. `H S SECMD5: ...`.
# The first parser here expected two tokens and silently matched nothing, which made the
# positive control read as "not flagged" and would have published a meaningless 0/11.
SB_FINDING = re.compile(r'^[A-Z]\+?\s+\S+\s+([A-Z][A-Z0-9_]{2,}):')


def ids(out):
    """SpotBugs bug codes only. Everything after the 'classes needed for analysis were missing'
    banner is diagnostics: counting it reported a phantom `Deprecated` finding on an earlier run."""
    body = re.split(r'The following classes needed for analysis were missing:', out or "")[0]
    return {m.group(1) for line in body.splitlines()
            if (m := SB_FINDING.match(line.strip()))}


def main():
    have = {t: bool(shutil.which(t)) for t in ("spotbugs", "semgrep", "kotlinc")}
    print("# " + "  ".join(f"{k}: {'found' if v else 'MISSING'}" for k, v in have.items()))
    if not have["kotlinc"]:
        raise SystemExit("kotlinc required - SpotBugs reads bytecode")

    cache = os.path.join(tempfile.gettempdir(), "fsb-cache")
    plugin = fetch_fsb(cache) if have["spotbugs"] else None
    print(f"# find-sec-bugs plugin: {'loaded' if plugin else 'NOT AVAILABLE'}\n")

    baseline_sb, baseline_sg = set(), set()
    rows = []
    for name, body in CASES.items():
        d = tempfile.mkdtemp(prefix="sast_")
        try:
            write_case(d, body)
            classes = compile_case(d)
            sb = ids(run_spotbugs(classes, plugin)) if have["spotbugs"] else set()
            sg = run_semgrep(os.path.join(d, "src")) if have["semgrep"] else set()
            if name.startswith("clean"):
                baseline_sb, baseline_sg = set(sb), set(sg)
            rows.append((name, sb - baseline_sb, sg - baseline_sg, classes is not None))
        finally:
            shutil.rmtree(d, ignore_errors=True)

    print("**Key** — `FLAG` the tool reported a finding this payload's clean control did not.")
    print("Differential, so anything the tool says about an empty file cancels.\n")
    print(f"{'payload':<20}{'spotbugs+fsb':>14}{'semgrep':>10}   findings")
    for name, sb, sg, ok in rows:
        note = "" if ok else "  (did not compile)"
        found = ", ".join(sorted(sb | sg)[:3]) or "-"
        print(f"{name:<20}{('FLAG' if sb else 'pass'):>14}{('FLAG' if sg else 'pass'):>10}"
              f"   {found}{note}")

    pos = next((r for r in rows if r[0].startswith("POSITIVE")), None)
    if pos and not (pos[1] or pos[2]):
        print("\nHARNESS INVALID: the positive control was not flagged, so the tools are not")
        print("actually analysing. Every 'pass' above is meaningless.")
    rows = [r for r in rows if not r[0].startswith("POSITIVE")]
    hits = sum(1 for _, sb, sg, _ in rows if (sb or sg))
    print(f"\nRESULT  {hits}/{len(rows)} payloads flagged by off-the-shelf SAST")
    print("        RAD-0028 predicted 0 - SAST hunts runtime misbehaviour, and these payloads")
    print("        execute nothing. A non-zero result falsifies that reasoning.")


if __name__ == "__main__":
    main()
