"""The `dependencyskills` command."""

import os
import sys

from . import __version__

USAGE = """usage: dependencyskills <command>

  install consumer|author [--harness claude,codex,gemini,antigravity] [--hook] [--source SPEC] [--apply]
                      put this project's half in place: a skill, and for a consumer the MCP
                      server; prints the plan, and changes nothing without --apply
  uninstall [--apply] reverse what install recorded
  name                for a library with no build plugin (npm, Python, Go, Cargo): print its skill's name and path
  check               for the same: say what would stop its skill shipping or being read
  mcp [--project DIR] serve the lookup over MCP on stdio; the agent's harness starts this. Each tool
                      answers for its `project` argument, else for DIR, else for the directory it is
                      started in
  list                the project's libraries whose authors ship a guide       (the list_guides tool)
  guide <library> [file]
                      one library's guide, or a file it links to                (the read_guide tool)
  search <need...>    the libraries on this machine that do what you need       (the search_libraries tool)
                      The three answer as the MCP tools do, for a harness without them; run in the project.
  log [on [path]|off] switch the local analytics log; off by default
  stats               summarise the log
  hook                a Claude Code UserPromptSubmit hook: count messages and corrections
  hook-settings       print the settings fragment that installs the hook
  version             print the version
"""


def main(argv=None):
    arguments = list(sys.argv[1:] if argv is None else argv)
    command, rest = (arguments[0], arguments[1:]) if arguments else ("", [])
    if command == "mcp":
        # The default project, for a configuration that serves one project but does not start the server in it;
        # a tool's own `project` argument overrides it.
        project = _option(rest, "--project", None)
        if project:
            if not os.path.isdir(project):
                print(f"--project {project}: no such directory", file=sys.stderr)
                return 2
            os.chdir(project)
        from .mcp import serve
        serve()
        return 0
    if command == "install" and rest[:1] in (["consumer"], ["author"]):
        from . import install
        options = rest[1:]
        harnesses = _option(options, "--harness", "claude").split(",")
        unknown = [h for h in harnesses if h not in install.HARNESSES]
        if unknown:
            print(f"unknown harness: {', '.join(unknown)}; choose from {', '.join(install.HARNESSES)}", file=sys.stderr)
            return 2
        proposal = install.plan(rest[0], os.getcwd(), harnesses, "--hook" in options,
                                _option(options, "--source", None))
        print(install.render(proposal, install.apply(proposal) if "--apply" in options else None))
        return 0
    if command == "uninstall":
        from . import install
        print(install.uninstall(os.getcwd(), "--apply" in rest))
        return 0
    if command == "version":
        print(__version__)
        return 0
    if command == "hook-settings":
        from .analytics import hook_settings
        print(hook_settings())
        return 0

    if command in ("name", "check"):
        from . import authoring
        found = authoring.describe(os.getcwd())
        if command == "name":
            if found is None:
                print("no package.json, pyproject.toml, go.mod or Cargo.toml here: run this from the root of the library",
                      file=sys.stderr)
                return 1
            print(f"name: {found['name']}")
            print(f"path: {found['path']}")
            return 0
        problems = authoring.warnings(os.getcwd())
        for problem in problems:
            print(f"dependencyskills: {problem}")
        return 1 if problems else 0
    from .store import Store
    store = Store()
    # The three tools, from a shell: the same call, so the same answer and the same log entry. `skill` and
    # `find` are the older names for the last two.
    if command == "list":
        from .mcp import call
        print(call(store, "list_guides", {}))
    elif command in ("guide", "skill") and rest:
        from .mcp import call
        arguments = {"library": rest[0]}
        if len(rest) > 1:
            arguments["file"] = rest[1]
        print(call(store, "read_guide", arguments))
    elif command in ("search", "find") and rest:
        from .mcp import call
        print(call(store, "search_libraries", {"need": " ".join(rest)}))
    elif command == "log":
        from .analytics import switch
        print(switch(store, rest))
    elif command == "stats":
        from .analytics import stats
        print(stats(store))
    elif command == "hook":
        from .analytics import prompt_hook
        try:
            prompt_hook(store, sys.stdin.read())
        except Exception:   # never between a developer and their agent
            pass
    else:
        print(USAGE, end="", file=sys.stderr)
        return 2
    return 0


def _option(options, name, default):
    if name in options and options.index(name) + 1 < len(options):
        return options[options.index(name) + 1]
    return default
