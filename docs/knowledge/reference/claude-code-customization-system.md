# Claude Code Customization System Guide

Reference · 2026-09-25
Keywords: Claude Code customizations; CLAUDE.md vs AGENTS.md; .claude/rules path-scoped rules; .claude/skills layout; .agents/skills not read; /import and claude import; skill frontmatter; progressive disclosure; subagents; plugin.json and marketplace.json; settings precedence; MCP local, project and user scope; .mcp.json schema; hooks events and exit codes; additionalContext.

Reference for Claude Code's customization system, from the official documentation at `code.claude.com/docs` as read on 2026-09-25, and from what was verified on one machine running Claude Code **2.1.276**. Where the two differ, or where something was checked rather than read, it says so. The companion for Antigravity is [antigravity-customization-system.md](antigravity-customization-system.md).

## The one fact this project trips on

**Claude Code does not read `.agents/skills/`.** Project skills load from `.claude/skills/` only — *"Claude Code loads project skills from `.claude/skills/` in the directory where you start it and in every parent directory up to the repository root"* (skills documentation). Antigravity reads `.agents/skills/` natively, and it is the cross-agent location this project installs into.

What makes it look otherwise, verified on one machine across nineteen projects that keep skills in `.agents/skills/`:

- **Links.** A skills installer had linked each skill into `.claude/skills/`, which Claude Code follows.
- **Global copies.** In the projects with no `.claude/skills/` at all, every skill was also installed in `~/.claude/skills/`, so Claude Code found it there.
- **The importer.** `/import` in a session, or `claude import` from a terminal, copies a project's `.agents/skills/*` into `.claude/skills/` — once, as a snapshot, skipping anything that is a symlink ("skipping project-scope read for safety"). It is found in the binary's import code alongside the Cursor paths it also imports from; it is not a live read.
- **The gap.** Thirty skills in two projects existed only in `.agents/skills/`, with no link and no global copy, and were unavailable to Claude Code.

## Instructions: `CLAUDE.md`, `AGENTS.md` and rules

| File | Scope | Notes |
|---|---|---|
| Managed policy `CLAUDE.md` | the organization | deployed by IT; cannot be excluded |
| `~/.claude/CLAUDE.md` | the user, every project | |
| `./CLAUDE.md` or `./.claude/CLAUDE.md` | the project, committed | equivalent; the first found is read |
| `./CLAUDE.local.md` | the project, personal | not committed |

- Files in the working directory and **every directory above** load at launch; files in subdirectories load when Claude reads files there.
- `@path/to/file` imports a file inline, relative to the importing file, up to **four hops** deep. Code spans and fenced blocks are skipped. An import outside the working directory asks for approval the first time.
- **`AGENTS.md`**: the documentation says Claude Code reads it natively from 2.1.277, by default only when there is no `CLAUDE.md` in the working directory or above, with a *Project instructions* setting to change that (`claude-md-or-agents-md`, `claude-md-and-agents-md`, `claude-md`, `managed-only`). Not verified here, where 2.1.276 is installed; a `CLAUDE.md` that points to `AGENTS.md` works in any version.
- **`.claude/rules/`**: one `.md` file per rule, found recursively. A rule loads at launch unless its frontmatter has `paths:` — globs, brace expansion allowed — in which case it loads only when Claude reads a matching file.
- Size: aim under 200 lines per `CLAUDE.md`; a file over 4 MiB is skipped.

## Skills

**Locations**, highest precedence first: managed; personal `~/.claude/skills/<name>/SKILL.md`; project `.claude/skills/<name>/SKILL.md`, walking up to the repository root and finding nested ones; plugin skills, invoked as `<plugin>:<skill>`; directories added with `--add-dir`; bundled skills. There is no setting to add another skill directory, and no reading of `.agents/skills/`. A `<skill-name>` entry that is a symlink to a directory is followed, and a target reached from several places loads once.

**Progressive disclosure**: every skill's `description` is loaded at session start; the body loads only when the skill is used. Keep `SKILL.md` under 500 lines and link supporting files from it.

**Frontmatter** Claude Code understands, beyond the Agent Skills specification's `name` and `description`:

| Field | Effect |
|---|---|
| `allowed-tools` | tools pre-approved while the skill runs |
| `disable-model-invocation: true` | only the user can invoke it |
| `user-invocable: false` | only Claude can; hidden from the `/` menu |
| `context: fork` | runs in an isolated subagent context |
| `model`, `agent`, `arguments`, `argument-hint` | model choice, subagent type, named arguments, argument hint |
| `hooks` | hooks scoped to the skill |

Substitutions available in a skill: `${CLAUDE_PROJECT_DIR}`, `${CLAUDE_SKILL_DIR}`, `${CLAUDE_SESSION_ID}`.

**Commands** in `.claude/commands/` and `~/.claude/commands/` are the older form of the same thing: they still work, take the same frontmatter, and new work should be a skill.

## Subagents

Markdown with frontmatter in `.claude/agents/<name>.md` (project) or `~/.claude/agents/<name>.md` (user), found recursively; a subfolder name joins the agent's name with a colon. Precedence: managed, then `--agents`, then project, then user, then plugins. Frontmatter includes `name`, `description`, `tools`, `model`, `permissionMode`, `memory`, `skills`, `mcpServers`, `maxTurns`, `effort`, `isolation` (`worktree`) and `omitClaudeMd`.

## Plugins

A plugin is a directory, optionally with a manifest at `.claude-plugin/plugin.json`, and components in their standard places: `skills/<name>/SKILL.md`, `commands/`, `agents/`, `hooks/hooks.json`, `.mcp.json`, `.lsp.json`, `settings.json`, and `bin/` for executables put on `PATH`.

- **Manifest**: `name` (required, kebab-case), `displayName`, `version`, `description`, `author`, `homepage`, `repository`, `license`, `keywords`, `defaultEnabled`, `dependencies`, and paths or inline configuration for `skills`, `commands`, `agents`, `hooks`, `mcpServers`, `lspServers` and `outputStyles`; `userConfig` prompts for values at install.
- **Versions**: setting `version` pins the plugin to it until the field changes — except for a plugin with a `command` source, one from the claude.ai marketplace, or one loaded in place from a local-directory marketplace.
- **Marketplaces**: a repository or directory with `.claude-plugin/marketplace.json`, cataloguing plugins and where to fetch each.
- **Install scopes**: user (every project on the machine), project (for all collaborators, through the committed `.claude/settings.json`), local (this checkout only).

## Settings

| File | Scope | Commit it |
|---|---|---|
| managed settings | the organization | deployed outside the repository |
| `~/.claude/settings.json` | the user | no |
| `.claude/settings.json` | the project | yes |
| `.claude/settings.local.json` | this checkout | no |
| command-line flags, `--settings` | this run | — |

Some settings are valid only at certain levels — user, local or managed; managed only; or only in the global configuration `~/.claude.json`.

## MCP servers

| Scope | Stored in | Shared |
|---|---|---|
| local (the default) | `~/.claude.json`, under the project's path | no |
| project | `.mcp.json` at the project root | yes, through git |
| user | `~/.claude.json` | no; every project |
| plugin | the plugin's `.mcp.json` or manifest | with the plugin |

A stdio server in `.mcp.json`:

```json
{ "mcpServers": { "<name>": { "type": "stdio", "command": "<command>", "args": ["<arg>"], "env": { "<KEY>": "<value>" } } } }
```

- `${VAR}` and `${VAR:-default}` are expanded; an unset variable with no default loads as its literal text, with a warning.
- A server from `.mcp.json` asks for approval on first interactive use, and not until the workspace is trusted. `claude mcp reset-project-choices` resets the answers.
- `claude mcp add --scope local|project|user <name> -- <command> [args…]` registers a stdio server; `--env KEY=value` goes before `--`.
- When the same name is defined in several places: local, then project, then user, then plugin, then claude.ai connectors; managed configuration outranks all.

Claude Code passes the session id to a stdio MCP server as `CLAUDE_CODE_SESSION_ID` — verified here, where a server's own log recorded it.

## Hooks

Configured in any settings file, a plugin's `hooks/hooks.json`, or a skill's frontmatter:

```json
{ "hooks": { "<Event>": [ { "matcher": "<tool or pattern>", "hooks": [ { "type": "command", "command": "<command>" } ] } ] } }
```

- **Events**: `SessionStart`, `SessionEnd`, `UserPromptSubmit`, `Stop`, `StopFailure`, `PreToolUse`, `PostToolUse`, and the standalone `FileChanged`, `CwdChanged`, `ConfigChange`, `Notification`, `InstructionsLoaded`, `PreModelSwitch`, `PostModelSwitch`, `PermissionRequest`, `MessageDisplay`, `SubagentStart`, `SubagentStop`.
- **Types**: `command` (JSON on stdin), `http` (POSTed), `mcp_tool`, `prompt`, and the experimental `agent`. Common fields: `matcher`, `if` (a permission rule, tool events only), `timeout`, `statusMessage`, and `once` in skill frontmatter.
- **Input** includes `session_id`, `transcript_path`, `cwd`, `permission_mode` and `hook_event_name`.
- **Exit codes**: `0` proceeds and reads JSON from stdout; `2` blocks, where the event can be blocked; anything else proceeds and shows a hook error.
- **Output**: `hookSpecificOutput` carries fields such as `permissionDecision`, `updatedInput` and `additionalContext`. The documentation lists `additionalContext` for `PreToolUse`, `UserPromptSubmit` and `PermissionRequest`.

## What this means for this project

- **Project skills go in `.agents/skills/`**, the cross-agent location, and Claude Code needs a bridge until it reads it: `/import`, which must be re-run when a skill changes, or a global install in `~/.claude/skills/`. A link works but is a mess outside a development checkout.
- **The lookup registers at local scope** (`claude mcp add --scope local`), which keeps it out of the project's git; `.mcp.json` is the choice for a team that wants it for every collaborator.
- **The correction hook is `UserPromptSubmit`**, one of the events documented to take `additionalContext`, though the hook adds none: it only counts.
- **A plugin could carry all of it** — the skill, the server and the hook — pinned by `version`, which would be the native route for a per-harness package of the installer (#44).
