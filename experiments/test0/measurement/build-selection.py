#!/usr/bin/env python3
"""
Generate the selection A/B prompts (test0) — RAD-0007's measurement.

Selection asks the question no model can answer from training: among several *genuinely
overlapping* real libraries that all fit the task, does the agent reach for the one THIS
project prefers? Real libraries, because the point is overriding a training-default
*habit* (a synthetic library has no prior).

A refinement: the project's **declared dependency tree is itself a selection signal** —
if the build file lists only Moshi, a good agent should use Moshi just by reading the
classpath, no codex needed. So the codex's *unique* value is not the single-dependency
case (the tree handles it) but the **ambiguous** one: when several overlapping libraries
are all on the classpath and the tree cannot say which the project sanctions. Four
conditions isolate this:

  A        : the task alone — the model's default (habit).
  dep1     : the tree lists ONLY the preferred lib — does the classpath alone redirect?
  dep2     : the tree lists BOTH the preferred and a common alternative, no preference —
             an ambiguous classpath.
  dep2pref : dep2 PLUS the project's authored standard — the codex's unique contribution,
             resolving what the tree cannot.

The dependency tree's own power is A -> dep1. The codex's unique selection value is
dep2 -> dep2pref. Scored by sweep-selection.sh (preferred | other | mixed | none).
"""
import os

DOMAINS = {
    "json": dict(
        pref_name="Moshi", pref_coord="com.squareup.moshi:moshi:1.15.1",
        alt_name="kotlinx.serialization", alt_coord="org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3",
        standard="JSON is handled with **Moshi**",
        task="""Implement `toJson` to serialize this data class to a JSON string:

    data class User(val id: Int, val name: String)

    fun toJson(u: User): String {
        // TODO: serialize u to a JSON string
    }""",
        ask="Use whatever JSON library you would normally reach for in a Kotlin project."),
    "http": dict(
        pref_name="the Ktor client", pref_coord="io.ktor:ktor-client-core:2.3.12",
        alt_name="OkHttp", alt_coord="com.squareup.okhttp3:okhttp:4.12.0",
        standard="HTTP is done with the **Ktor client**",
        task="""Implement `fetch` to make an HTTP GET request to a URL and return the response
body as a String:

    fun fetch(url: String): String {
        // TODO: HTTP GET url, return the body as a String
    }""",
        ask="Use whatever HTTP client library you would normally reach for."),
    "assert": dict(
        pref_name="Strikt", pref_coord="io.strikt:strikt-core:0.34.1",
        alt_name="AssertJ", alt_coord="org.assertj:assertj-core:3.24.2",
        standard="test assertions use **Strikt**",
        task="""Write the body of a test that asserts `result` equals 42 and that `name` is not
blank:

    fun checkOutput(result: Int, name: String) {
        // TODO: assert result == 42, and that name is not blank
    }""",
        ask="Use whatever assertion library you would normally reach for in a test."),
}

INTRO = "You are working in a Kotlin project."
CLOSE = "Reply with only the Kotlin code, including the imports you would use."


def deps_block(coords):
    lines = "\n".join(f"      {c}" for c in coords)
    return ("The project's declared dependencies (from its dependency index) include:\n\n"
            f"{lines}\n")


def main():
    here = os.path.dirname(os.path.abspath(__file__))
    outdir = os.path.join(here, "prompts")
    for dom, d in DOMAINS.items():
        pref, alt = d["pref_coord"], d["alt_coord"]
        conds = {
            "A": f"{INTRO}\n\n{d['task']}\n\n{d['ask']}\n\n{CLOSE}\n",
            "dep1": f"{INTRO}\n\n{deps_block([pref])}\n{d['task']}\n\n{CLOSE}\n",
            # alternative (the common habit) listed FIRST, biasing against the preferred,
            # so any dep2 -> dep2pref lift is not a position artefact (order held identical).
            "dep2": f"{INTRO}\n\n{deps_block([alt, pref])}\n{d['task']}\n\n{CLOSE}\n",
            "dep2pref": (f"{INTRO}\n\n{deps_block([alt, pref])}\n"
                         f"The project's index also records a standard: {d['standard']} — "
                         f"the project standardizes on it, so prefer it over other libraries "
                         f"on the classpath that do the same job.\n\n{d['task']}\n\n{CLOSE}\n"),
        }
        for cond, text in conds.items():
            path = os.path.join(outdir, f"task-sel-{dom}-{cond}.txt")
            with open(path, "w") as f:
                f.write(text)
            print(f"wrote {os.path.relpath(path, here)}")


if __name__ == "__main__":
    main()
