# Antigravity Customization System Guide

Reference · 2026-09-25
Keywords: Antigravity customizations; GEMINI.md vs AGENTS.md; .agents/skills layout; progressive disclosure; mcp_config.json schema; stdio vs SSE transport; hooks.json lifecycle; PreToolUse PostToolUse; plugin.json bundle; skills.json inheritance; defaultRulesBudget.

Authoritative reference for the Google Antigravity customization system, synthesised from the harness documentation (`agy-customizations`). It documents the discovery hierarchy, configuration schemas, lifecycle hooks, and context governance governing Antigravity agents across CLI, IDE, and desktop environments.

## Architectural philosophy

Antigravity structures agent configuration around three design principles:

1. **Progressive disclosure**: To protect context budgets, customizations are not loaded into the active context window indiscriminately. For skills, only names and descriptions are injected at startup into the system prompt; the full `SKILL.md` body and subordinate reference files are fetched on demand only when activated.
2. **Deterministic deduplication**: Rules and customizations discovered across multiple directory levels or inherited configs are deduplicated by resolved canonical file path, ensuring identical rules are never injected multiple times in a single turn.
3. **Hard context bounds**: Workspace rule files are strictly capped at 24 KB (24,000 bytes) per file (after expanding file transclusions) and truncated on line boundaries. All always-on and global rules share a dedicated 20,000-token rules budget (`defaultRulesBudget`), separated from the token allocations for skills, subagents, and tool descriptions. Rules that exceed this budget are demoted to file-pointer summaries read on demand.

## Discovery hierarchy and loading precedence

Antigravity discovers customizations by walking the filesystem from the current working directory up to the repository root (defined by the nearest `.git` directory), combined with global user configuration.

When naming or declaration conflicts occur, customizations resolve by strict descending priority:

1. **Workspace project discovery**: Discovered by walking up from the current working directory to the repository root. Standard root is `.agents/` (aliases `.agent/`, `_agents/`, and `_agent/` are also recognised).
2. **Declared workspace manifests**: Customizations explicitly registered via `skills.json` or `plugins.json` in the workspace root.
3. **Global user discovery**: Machine-local directory at `~/.gemini/config/`.
4. **Built-in customizations**: System runbooks and skills mounted by agent configuration.
5. **Global declared manifests**: Customizations registered in global JSON manifests.

## Customization types

| Type | Configuration target | Scope | Purpose |
|---|---|---|---|
| **Rules** | `GEMINI.md`, `AGENTS.md`, `.agents/rules/*.md` | Contextual / Hierarchical | Enforce conventions, coding constraints, and API restrictions. |
| **Skills** | `.agents/skills/<name>/SKILL.md` | On-demand (progressive) | Procedures, tool workflows, and runbooks. |
| **Plugins** | `.agents/plugins/<name>/plugin.json` | Bundled package | Namespaced bundle of skills, rules, hooks, and MCP configs. |
| **Hooks** | `.agents/hooks.json` | Deterministic lifecycle | Intercept tool calls, audit safety, overwrite arguments, or force loop continuation. |
| **MCP Servers** | `mcp_config.json` | Tool integration | Connect stdio executables or SSE endpoints exposing model tools and resources. |

## Rules

Rules govern agent behaviour without requiring explicit activation.

- **Placement**: `GEMINI.md` or `AGENTS.md` placed in any directory from the CWD up to the project root, or `.agents/rules/*.md`.
- **Precedence**: `GEMINI.md` is loaded first, followed by `AGENTS.md`. Subdirectory rules supplement and specialise parent rules.
- **Budgeting**: Capped at 24 KB per file. When the aggregate 20,000-token `defaultRulesBudget` is exhausted, lower-precedence rules degrade to on-demand file pointers.

## Skills

Skills conform to the Agent Skills directory structure.

```text
.agents/skills/<skill-name>/
├── SKILL.md          # Required: Frontmatter plus procedural markdown instructions
├── scripts/          # Optional: Helper scripts and executable tooling
├── examples/         # Optional: Code and usage samples
├── resources/        # Optional: Data files, templates, or assets
└── references/       # Optional: Detailed background documentation
```

### Frontmatter specification

Every `SKILL.md` must open with a YAML frontmatter block containing two mandatory fields:

```yaml
---
name: my-skill-name
description: Clear, third-person description stating what the skill does and precisely when to activate it.
---
```

- **`name`**: Lowercase, hyphenated string (maximum 64 characters) matching the containing directory.
- **`description`**: Decisive semantic trigger evaluated by the model during startup injection.

## Plugins

Plugins distribute reusable, namespaced customizations as a single unit. A plugin resides in `.agents/plugins/<plugin-name>/` (workspace) or `~/.gemini/config/plugins/<plugin-name>/` (global).

```text
.agents/plugins/<plugin-name>/
├── plugin.json       # Required: Manifest
├── mcp_config.json   # Optional: Bundled MCP server definitions
├── hooks.json        # Optional: Bundled lifecycle hooks
├── rules/            # Optional: Bundled rules (AGENTS.md recommended)
└── skills/           # Optional: Bundled skills (<name>/SKILL.md)
```

- **Manifest (`plugin.json`)**: Minimally `{ "name": "display-name" }`. A plugin can declare `"disabled": true` to ship inactive.
- **Namespacing**: Tools and skills provided by plugins are namespaced (`<plugin>_<tool>`) to prevent collisions with user or workspace definitions.
- **Activation state**: Toggled via settings or CLI (`agy plugin enable <name>`); persisted in `~/.gemini/config/config.json`.

## Model Context Protocol (MCP)

Antigravity supports Model Context Protocol (MCP) tools via stdio and SSE transports.

### Configuration locations

- **User / Global scope**: `~/.gemini/config/mcp_config.json` (also read by the desktop client at `~/.gemini/antigravity/mcp_config.json`).
- **Project scope**: Configured via a project plugin at `.agents/plugins/<plugin-name>/mcp_config.json`. Antigravity does not read a bare, unmanaged `mcp_config.json` at the project root.

### Schema

```json
{
  "mcpServers": {
    "local-service": {
      "command": "uvx",
      "args": ["my-mcp-package@latest", "--flag"],
      "env": {
        "SERVICE_PORT": "8080"
      }
    },
    "remote-service": {
      "serverUrl": "https://mcp.example.com/sse"
    }
  }
}
```

- **`command`** (string): Executable binary or command path for stdio servers.
- **`args`** (array of strings, optional): Command-line arguments.
- **`env`** (map of strings, optional): Environment variables passed to the spawned process.
- **`serverUrl`** (string): HTTP/HTTPS URL for SSE remote servers.
- **`disabled`** (boolean, optional): Set to `true` to deactivate without removing the entry.

## Lifecycle hooks (`hooks.json`)

Hooks execute synchronous shell scripts at defined points in the agent execution loop, providing deterministic guardrails and automated workflows. Configured in `.agents/hooks.json` or within plugins.

### Supported events

| Event | Fired when | Matcher syntax | Payload structure |
|---|---|---|---|
| `PreToolUse` | Before a tool executes | Tool name regex (e.g. `run_command`, `run_command\|view_file`, `*`) | Grouped (`matcher` + `hooks`) |
| `PostToolUse` | After a tool completes | Tool name regex | Grouped (`matcher` + `hooks`) |
| `PreInvocation` | Before model reasoning call | N/A | Flat handler list |
| `PostInvocation` | After model tool emission | N/A | Flat handler list |
| `Stop` | When execution loop terminates | N/A | Flat handler list |

### Execution contract

Hooks receive context on **stdin** as camelCase protojson and return instructions on **stdout** as JSON.

```json
{
  "safety-gate": {
    "PreToolUse": [
      {
        "matcher": "run_command",
        "hooks": [
          {
            "type": "command",
            "command": "./scripts/safety-gate.sh",
            "timeout": 15
          }
        ]
      }
    ]
  }
}
```

- **`PreToolUse` decisions**:
  - `"allow"`: Permitted immediately.
  - `"deny"`: Hard blocked.
  - `"ask"`: Prompts human confirmation.
  - `"force_ask"`: Prompts human confirmation ignoring prior approval cache.
  - `"overwrite"`: Shallow top-level key replacement of tool call arguments before execution.
- **Loop continuation (`Stop` / `PostInvocation`)**:
  - `Stop` hooks can return `{"decision": "continue", "reason": "Tests still running"}` to block agent termination and force another turn.

## External JSON manifests (`skills.json` and `plugins.json`)

For projects storing customizations outside default directories, `skills.json` and `plugins.json` in the customization root (`.agents/` or `~/.gemini/config/`) define explicit paths and inheritance:

```json
{
  "inherits": [
    {
      "path": "/shared/team/skills.json",
      "include_only": ["linter-.*"],
      "exclude": [".*-deprecated"]
    }
  ],
  "entries": [
    {
      "path": "tools/custom-skills"
    },
    {
      "path": "~/shared-skills"
    }
  ]
}
```

- **Path resolution**: Leading `/` is absolute; leading `~/` is home-relative; un-prefixed paths resolve relative to the repository root. Directories in `entries` are scanned one level deep for matching customization folders.
