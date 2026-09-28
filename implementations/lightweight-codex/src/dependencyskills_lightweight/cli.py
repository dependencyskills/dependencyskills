"""The `dependencyskills` command."""

import os
import sys

from . import __version__

USAGE = """usage: dependencyskills <command>

  install consumer|author [--harness claude,codex,gemini,antigravity] [--hook] [--source SPEC] [--apply]
                      put this project's half in place: a skill, and for a consumer the MCP
                      server; prints the plan, and changes nothing without --apply
  uninstall [--apply] reverse what install recorded
  name                for a library with no build plugin (npm): print its skill's name and path
  check               for the same: say what would stop its skill shipping or being read
  mcp                 serve the lookup over MCP on stdio; the agent's harness starts this
  skill <group:artifact>
                      print a dependency's skill, as the agent would read it
  find <need...>      search the libraries on this machine for one that does what you need
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
                                _option(options, "--source", install.DEFAULT_SOURCE))
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
                print("no package.json here: run this from the root of the library", file=sys.stderr)
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
    if command == "skill" and rest:
        from .lookup import get_skill
        from .project import refresh
        print(get_skill(store, refresh(store, os.getcwd()), rest[0]))
    elif command == "find" and rest:
        from .find import find
        from .project import refresh
        print(find(store, refresh(store, os.getcwd()), " ".join(rest)))
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
