"""The `dependencyskills` command."""

import os
import sys

from . import __version__

USAGE = """usage: dependencyskills <command>

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
    if command == "version":
        print(__version__)
        return 0
    if command == "hook-settings":
        from .analytics import hook_settings
        print(hook_settings())
        return 0

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
