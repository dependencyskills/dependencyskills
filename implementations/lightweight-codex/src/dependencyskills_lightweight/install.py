"""Installing the lightweight system into a project, without becoming the carrier this project warns about (#44).

A procedure an agent fetches from a URL and follows is remote instruction text nobody reviewed, run
with write access to build and instruction files. So this is not that. It is fixed code in a pinned
package, and:

- **It fetches nothing.** The skills it writes travel inside this package; the MCP server it
  registers is this package, pinned to this version, which the package manager checks against the
  published digests.
- **Its effects are declared and bounded**: a skill copied into `.agents/skills/`, the standard place
  for a project's skills, one MCP server entry per chosen harness, and — only when asked — the
  correction hook. No links. It never edits a build file; the plugin lines are printed, for the developer
  or their agent to add.
- **Nothing in the repository steers it.** It reads no file to decide what to do, so text planted in
  one cannot change the plan.
- **It proposes, and applies only when asked.** Without `--apply` it prints the plan. With it, it
  records every change and its digest in a manifest, reports what landed, and `uninstall` reverses
  exactly that — refusing to delete anything that was changed after it was written.
"""

import hashlib
import json
import os
import re
import shutil
import subprocess
from datetime import datetime, timezone
from pathlib import Path

from . import __version__

SERVER = "librarian"
MANIFEST = Path(".agents") / "dependencyskills-install.json"
DEFAULT_SOURCE = f"dependencyskills-lightweight-codex=={__version__}"
HARNESSES = ("claude", "codex", "gemini", "antigravity")
SKILL_FOR = {"consumer": "librarian", "library": "to-library-skill"}


def bundled_skill(name):
    """The skill directory this package carries — or, run from a source checkout, the one beside it."""
    packaged = Path(__file__).parent / "skills" / name
    if packaged.is_dir():
        return packaged
    checkout = Path(__file__).resolve().parents[3] / "agent-skills" / name
    if checkout.is_dir():
        return checkout
    raise FileNotFoundError(f"this build of dependencyskills carries no skill named {name}")


def server_command(source):
    """The MCP server as a harness runs it: this package, at the pinned version, through uvx."""
    return ["uvx", "--from", source, "dependencyskills", "mcp"]


def digest(path):
    return hashlib.sha256(Path(path).read_bytes()).hexdigest()


def antigravity_config():
    """Antigravity's user-scope MCP configuration. It reads project skills from `.agents/skills/` itself,
    so it needs no link; a project-scope server needs a plugin manifest, which is not written here."""
    return Path.home() / ".gemini" / "config" / "mcp_config.json"


def codex_config():
    return Path(os.environ.get("CODEX_HOME") or Path.home() / ".codex") / "config.toml"


def plan(role, project, harnesses, hook, source):
    """The effects of installing `role` ("consumer" or "library") into `project`, and what is left to the developer."""
    project = Path(project).resolve()
    skill = SKILL_FOR[role]
    # The one standard place for a project's skills. No links: they are fine in a checkout someone is
    # developing in, and a mess everywhere else.
    effects = [{"kind": "skill", "skill": skill, "path": f".agents/skills/{skill}"}]
    if role == "consumer":
        command = server_command(source)
        if "claude" in harnesses:
            effects.append({"kind": "claude-mcp", "name": SERVER, "command": command,
                            "where": "Claude Code's local scope for this project, outside its git"})
        if "codex" in harnesses:
            effects.append({"kind": "codex-mcp", "name": SERVER, "command": command, "path": str(codex_config()),
                            "where": "Codex's own configuration, for every project; it answers only where a build reported"})
        if "gemini" in harnesses:
            effects.append({"kind": "gemini-mcp", "name": SERVER, "command": command, "path": ".gemini/settings.json",
                            "where": "this project's Gemini settings"})
        if "antigravity" in harnesses:
            effects.append({"kind": "antigravity-mcp", "name": SERVER, "command": command, "path": str(antigravity_config()),
                            "where": "Antigravity's user configuration, for every project; it answers only where a build reported"})
        if hook:
            effects.append({"kind": "claude-hook", "path": ".claude/settings.local.json",
                            "command": " ".join(["uvx", "--from", source, "dependencyskills", "hook"])})
    yours = [
        "Apply the Gradle plugin `org.dependencyskills.plugin` to every module "
        + ("whose dependencies the agent should see" if role == "consumer" else "that publishes a library")
        + f", at version {__version__.split('a')[0]} — this installer never edits a build file.",
    ]
    if role == "library":
        yours.append("Ask your agent to write the library's skill with the to-library-skill skill, and review it before release.")
    else:
        yours.append("Build once, so the build writes the report the lookup reads.")
    return {"role": role, "project": str(project), "version": __version__, "source": source,
            "effects": effects, "yours": yours}


def describe(effect):
    kind = effect["kind"]
    if kind == "skill":
        return f"copy the {effect['skill']} skill into {effect['path']}/"
    if kind == "claude-hook":
        return f"add the correction hook to {effect['path']} (Claude Code; counts messages and corrections into a local log)"
    return f"register the MCP server `{effect['name']}` in {effect['where']}: {' '.join(effect['command'])}"


def render(proposal, applied=None):
    lines = [f"dependencyskills {proposal['version']} — install the {proposal['role']} half into {proposal['project']}", ""]
    lines.append("Applied:" if applied is not None else "Would (nothing is changed without --apply):")
    for effect in proposal["effects"]:
        outcome = f"  [{applied[id(effect)]}]" if applied is not None else ""
        lines.append(f"  - {describe(effect)}{outcome}")
    lines += ["", "Yours to do:"] + [f"  - {item}" for item in proposal["yours"]]
    if proposal["role"] == "consumer":
        lines += ["", "Nothing was fetched: the skill is the one this version carries, and the server is this version, "
                      f"pinned as {proposal['source']}."]
    else:
        lines += ["", "Nothing was fetched: the skill is the one this version carries."]
    if applied is not None:
        lines += ["", f"Every change is recorded in {MANIFEST}. Undo it with: dependencyskills uninstall --apply"]
    return "\n".join(lines)


def apply(proposal):
    """Make the changes, record them with their digests, and return {id(effect): outcome}."""
    project = Path(proposal["project"])
    manifest_path = project / MANIFEST
    previous = json.loads(manifest_path.read_text()) if manifest_path.is_file() else {"changes": []}
    changes, outcomes = [], {}
    for effect in proposal["effects"]:
        outcome, record = _apply(project, effect, previous)
        outcomes[id(effect)] = outcome
        if record:
            changes.append(record)
    kept = [c for c in previous["changes"] if not any(_same(c, n) for n in changes)]
    manifest_path.parent.mkdir(parents=True, exist_ok=True)
    manifest_path.write_text(json.dumps({"version": __version__, "source": proposal["source"],
                                         "applied": datetime.now(timezone.utc).isoformat(timespec="seconds"),
                                         "changes": kept + changes}, indent=2) + "\n")
    return outcomes


def _same(a, b):
    return a["kind"] == b["kind"] and a.get("path") == b.get("path") and a.get("name") == b.get("name")


def _apply(project, effect, previous):
    kind = effect["kind"]
    if kind == "skill":
        target = project / effect["path"]
        source = bundled_skill(effect["skill"])
        ours = next((c for c in previous["changes"] if c["kind"] == "skill" and c["path"] == effect["path"]), None)
        if target.exists() and not (ours and _unchanged(target, ours["files"])):
            return "left alone: something else is already there", None
        if target.exists():
            shutil.rmtree(target)
        shutil.copytree(source, target, ignore=shutil.ignore_patterns("scripts", "__pycache__"))
        files = {str(p.relative_to(target)): digest(p) for p in sorted(target.rglob("*")) if p.is_file()}
        return "done", {"kind": "skill", "path": effect["path"], "files": files}
    if kind == "claude-mcp":
        if not shutil.which("claude"):
            return "not done: the claude command is not on PATH; run  claude mcp add --scope local " \
                   f"{effect['name']} -- {' '.join(effect['command'])}", None
        done = subprocess.run(["claude", "mcp", "add", "--scope", "local", effect["name"], "--", *effect["command"]],
                              cwd=project, capture_output=True, text=True)
        if done.returncode != 0:
            return f"not done: {(done.stderr or done.stdout).strip().splitlines()[-1] if (done.stderr or done.stdout) else 'claude mcp add failed'}", None
        return "done", {"kind": "claude-mcp", "name": effect["name"]}
    if kind == "codex-mcp":
        config = Path(effect["path"])
        text = config.read_text() if config.is_file() else ""
        if re.search(rf"(?m)^\[mcp_servers\.{re.escape(effect['name'])}\]", text):
            return "left alone: Codex already has a server of that name", None
        block = (f"\n# Added by dependencyskills {__version__}; removed by `dependencyskills uninstall`.\n"
                 f"[mcp_servers.{effect['name']}]\ncommand = {json.dumps(effect['command'][0])}\n"
                 f"args = {json.dumps(effect['command'][1:])}\n")
        config.parent.mkdir(parents=True, exist_ok=True)
        config.write_text(text + block)
        return "done", {"kind": "codex-mcp", "name": effect["name"], "path": str(config), "block": block}
    if kind in ("gemini-mcp", "antigravity-mcp"):
        return _merge(project / effect["path"], ["mcpServers", effect["name"]],
                      {"command": effect["command"][0], "args": effect["command"][1:]}, effect)
    if kind == "claude-hook":
        path = project / effect["path"]
        settings = json.loads(path.read_text()) if path.is_file() else {}
        entry = {"hooks": [{"type": "command", "command": effect["command"]}]}
        hooks = settings.setdefault("hooks", {}).setdefault("UserPromptSubmit", [])
        if entry in hooks:
            return "already there", {"kind": "claude-hook", "path": effect["path"], "entry": entry}
        hooks.append(entry)
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(json.dumps(settings, indent=2) + "\n")
        return "done", {"kind": "claude-hook", "path": effect["path"], "entry": entry}
    raise ValueError(f"unknown effect {kind}")


def _merge(path, keys, value, effect):
    settings = json.loads(path.read_text()) if path.is_file() else {}
    node = settings
    for key in keys[:-1]:
        node = node.setdefault(key, {})
    if keys[-1] in node:
        return ("already there", {"kind": effect["kind"], "path": effect["path"], "name": effect["name"], "value": value}) \
            if node[keys[-1]] == value else ("left alone: an entry of that name is already there", None)
    node[keys[-1]] = value
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(settings, indent=2) + "\n")
    return "done", {"kind": effect["kind"], "path": effect["path"], "name": effect["name"], "value": value}


def _unchanged(directory, files):
    present = {str(p.relative_to(directory)): digest(p) for p in directory.rglob("*") if p.is_file()}
    return present == files


def uninstall(project, apply_it):
    """Reverse what the manifest records. Returns the report; changes nothing unless `apply_it`."""
    project = Path(project).resolve()
    manifest_path = project / MANIFEST
    if not manifest_path.is_file():
        return f"Nothing to undo: {MANIFEST} is not in {project}."
    changes = json.loads(manifest_path.read_text())["changes"]
    lines, remaining = [("Removed:" if apply_it else "Would remove (nothing is changed without --apply):")], []
    for change in reversed(changes):
        outcome = _undo(project, change) if apply_it else "planned"
        lines.append(f"  - {_undo_description(change)}  [{outcome}]")
        if apply_it and not outcome.startswith("done"):
            remaining.append(change)
    if apply_it:
        if remaining:
            manifest_path.write_text(json.dumps({"changes": list(reversed(remaining))}, indent=2) + "\n")
        else:
            manifest_path.unlink()
            _prune(project, manifest_path.parent)
    return "\n".join(lines)


def _undo_description(change):
    kind = change["kind"]
    if kind == "skill":
        return f"the skill at {change['path']}/"
    if kind == "link":
        return f"the link {change['path']}"
    if kind == "claude-hook":
        return f"the correction hook in {change['path']}"
    return f"the MCP server `{change['name']}` ({kind.split('-')[0]})"


def _prune(project, directory):
    """Remove directories the uninstall left empty, up to the project — never the project itself."""
    while directory != project and project in directory.parents and directory.is_dir() and not any(directory.iterdir()):
        directory.rmdir()
        directory = directory.parent


def _undo(project, change):
    kind = change["kind"]
    if kind == "skill":
        target = project / change["path"]
        if not target.exists():
            return "done: already gone"
        if not _unchanged(target, change["files"]):
            return "left alone: it was changed after it was installed"
        shutil.rmtree(target)
        _prune(project, target.parent)
        return "done"
    if kind == "link":   # written only by the first alpha, which linked skills for Claude Code
        link = project / change["path"]
        if link.is_symlink() and os.readlink(link) == change["target"]:
            link.unlink()
            _prune(project, link.parent)
            return "done"
        return "done: already gone" if not link.exists() and not link.is_symlink() else "left alone: it points elsewhere now"
    if kind == "claude-mcp":
        if not shutil.which("claude"):
            return f"not done: run  claude mcp remove --scope local {change['name']}"
        done = subprocess.run(["claude", "mcp", "remove", "--scope", "local", change["name"]],
                              cwd=project, capture_output=True, text=True)
        return "done" if done.returncode == 0 else "not done: claude mcp remove failed"
    if kind == "codex-mcp":
        config = Path(change["path"])
        text = config.read_text() if config.is_file() else ""
        if change["block"] not in text:
            return "left alone: the entry was changed after it was added"
        config.write_text(text.replace(change["block"], "", 1))
        return "done"
    if kind in ("gemini-mcp", "antigravity-mcp"):
        path = project / change["path"]
        settings = json.loads(path.read_text()) if path.is_file() else {}
        servers = settings.get("mcpServers", {})
        if servers.get(change["name"]) != change["value"]:
            return "left alone: the entry was changed after it was added"
        del servers[change["name"]]
        path.write_text(json.dumps(settings, indent=2) + "\n")
        return "done"
    if kind == "claude-hook":
        path = project / change["path"]
        settings = json.loads(path.read_text()) if path.is_file() else {}
        hooks = settings.get("hooks", {}).get("UserPromptSubmit", [])
        if change["entry"] not in hooks:
            return "done: already gone"
        hooks.remove(change["entry"])
        path.write_text(json.dumps(settings, indent=2) + "\n")
        return "done"
    return "left alone: unknown change"
