#!/usr/bin/env python3
"""test25 phase C — what a model's size costs the summariser.

Drives the summariser's OWN harness. The prompt, the four properties, the verifier, adjudication
and the degradation accounting are imported rather than reimplemented, and the only thing that
varies is which model produced the text. Anything else would be comparing two components.

    ./run.sh gen <model.gguf> 200 | ...     # not used directly; sweep.py drives it

    uv run python sweep.py <model.gguf> [--n 60]
"""
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
SUM = os.path.normpath(os.path.join(HERE, "..", "summariser"))
sys.path.insert(0, SUM)
sys.path.insert(0, os.path.normpath(os.path.join(HERE, "..", "test5")))

import summarise as S  # noqa: E402

END = "<<<END>>>"


def generate_all(model, prompts, predict=200, runtime="gen"):
    """One JVM, one model load, every prompt. Returns raw completions in order.

    `runtime` selects which binding runs it - `gen` is llama.cpp over GGUF, `jgen` is JLama over
    safetensors. Nothing else differs between the two paths, which is what makes the comparison
    one of runtimes rather than of harnesses.
    """
    proc = subprocess.Popen(
        [os.path.join(HERE, "run.sh"), runtime, model, str(predict)],
        stdin=subprocess.PIPE, stdout=subprocess.PIPE,
        stderr=open('/tmp/gen-stderr.log', 'w'),
        text=True, bufsize=1)
    out = []
    # Anything the library writes to stdout before it is ready is not a completion.
    while True:
        line = proc.stdout.readline()
        if not line:
            raise RuntimeError("the generator exited before it was ready")
        if line.rstrip("\n") == "<<<READY>>>":
            break
    try:
        for p in prompts:
            proc.stdin.write(p.replace("\r", "") + "\n" + END + "\n")
            proc.stdin.flush()
            buf = []
            while True:
                line = proc.stdout.readline()
                if not line:
                    raise RuntimeError("the generator exited early")
                if line.rstrip("\n") == END:
                    break
                buf.append(line.rstrip("\n").replace("\\n", "\n"))
            out.append("\n".join(buf))
    finally:
        try:
            proc.stdin.close()
            proc.wait(timeout=20)
        except Exception:
            proc.kill()
    return out


MARKDOWN = [
    (re.compile(r"^\s*/\*\*|\*/\s*$", re.M), " "),      # comment open and close
    (re.compile(r"^\s*\*\s?", re.M), ""),                # the leading star on each line
    (re.compile(r"```.*?```", re.S), " "),                 # fenced code
    (re.compile(r"^\s*@\w+.*$", re.M), " "),              # @param, @return, @sample
    # A link's TEXT is usually not prose about the capability - a quarter of this corpus carries
    # "Report a problem" - so the link goes entirely rather than being unwrapped into a sentence.
    (re.compile(r"\[[^\]]+\]\([^)]*\)"), " "),          # [text](url) -> nothing
    (re.compile(r"\[([^\]]+)\]"), r"\1"),                 # [Foo] -> Foo
    (re.compile(r"`+"), ""),                               # inline code markers
    (re.compile(r"\s+"), " "),
]


def render(doc):
    """KDoc and Javadoc are markdown wearing comment syntax. Rendering it to plain prose before
    the model sees it tests whether the markup in the OUTPUT is the model echoing the markup in
    the INPUT - which would make the verifier's rejection of backticks a symptom rather than the
    problem."""
    text = doc or ""
    for pattern, repl in MARKDOWN:
        text = pattern.sub(repl, text)
    return text.strip()


def main():
    model = sys.argv[1]
    n = int(sys.argv[sys.argv.index("--n") + 1]) if "--n" in sys.argv else 60
    runtime = sys.argv[sys.argv.index("--runtime") + 1] if "--runtime" in sys.argv else "gen"

    import json
    entries = json.load(open(S.CORPUS))[:n]
    if "--clean" in sys.argv:
        entries = [dict(e, doc=render(e["doc"])) for e in entries]
    prompts = [S.prompt_for(e) for e in entries]

    started = time.time()
    raw = generate_all(model, prompts, runtime=runtime)
    elapsed = time.time() - started

    degraded = 0
    rows = []
    for entry, text in zip(entries, raw):
        line = S.first_line(text)
        # `--no-backticks` tests one hypothesis: that the backtick in the verifier's CODEISH
        # pattern is doing formatting work rather than safety work, and is rejecting faithful
        # output for a markdown convention. A backtick is not an imperative and not code.
        if "--no-backticks" in sys.argv:
            line = line.replace("`", "")
        result, why = S.adjudicate(entry, line)
        if result["degraded"]:
            degraded += 1
        rows.append((entry, line, result, why))

    print(f"\n# {os.path.basename(model)}  —  {len(entries)} entries, {elapsed:.0f}s "
          f"({elapsed / len(entries):.1f}s each)")
    print(f"# degraded: {degraded} of {len(entries)} ({degraded / len(entries):.1%})\n")

    # WHY it degraded, over every entry rather than the ten printed below. The distribution is
    # the interesting part: a model whose default style is verbose degrades on shape without
    # being any less faithful, so this rate ranks output style against the verifier's rules and
    # must not be read as ranking capability.
    reasons = {}
    for _e, _l, result, why in rows:
        if result["degraded"]:
            reasons[why] = reasons.get(why, 0) + 1
    for why, count in sorted(reasons.items(), key=lambda kv: -kv[1]):
        print(f"    {count:>3}  {why}")
    print()
    for entry, line, result, why in rows[:10]:
        mark = "DEGRADED" if result["degraded"] else "ok      "
        print(f"  {mark} {entry.get('symbol', '?')[-60:]}")
        print(f"    doc:  {' '.join(entry.get('doc', '').split())[:120]}")
        print(f"    out:  {line[:120]}" + (f"   [{why}]" if result["degraded"] else ""))
    return 0


if __name__ == "__main__":
    sys.exit(main())
