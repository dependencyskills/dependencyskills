# How Coding-Agent Tools Are Customized

Reference · 2026-09-25
Keywords: which tools read .agents/skills; AGENTS.md support; MCP config file per tool; mcpServers vs servers vs context_servers; project MCP trust gates; hooks per tool; plugin formats; Agent Plugins 1.0; installer targets; Codex, Gemini CLI, Cursor, Copilot, Devin (Windsurf), Kiro, Junie, Amp, Augment, Zed, Cline, Roo, Continue, Aider, OpenHands, Goose, opencode, Crush, Kilo.

A survey of where nineteen coding-agent tools read instructions, skills, MCP servers, hooks and plugins, compiled from each tool's official documentation — or its official repository, where the documentation is silent — as read on **2026-09-25**. It exists to decide where an installer puts a project's skill and how it registers an MCP server with each tool. **"Unconfirmed" means the documentation did not say, not that the tool lacks the feature.** The pages were read through a summarizing fetch and load-bearing facts re-checked; one fabricated claim was caught and removed in that pass, so treat long lists of fields and events as possibly incomplete. Claude Code and Antigravity have their own references: [claude-code-customization-system.md](claude-code-customization-system.md) and [antigravity-customization-system.md](antigravity-customization-system.md).

## Summary

"Own skills dir" is the tool's own directory besides `.agents/skills`. "UI" is a settings screen rather than a documented file.

| Tool | Reads `AGENTS.md` | Reads project `.agents/skills` | Own skills dir (project / user) | MCP, project | MCP, user | Hooks | Plugin format (pinning) |
|---|---|---|---|---|---|---|---|
| Claude Code | from 2.1.277, when there is no `CLAUDE.md` | **no** (import only) | `.claude/skills` / `~/.claude/skills` | `.mcp.json` `mcpServers`, approval | `~/.claude.json` | yes | `.claude-plugin/plugin.json` (`version`) |
| Antigravity | yes | yes | — / `~/.gemini/config` | `.agents/plugins/<name>/mcp_config.json` | `~/.gemini/config/mcp_config.json` | yes | `plugin.json` under `.agents/plugins/` |
| Codex (CLI, IDE) | yes | yes — its only project dir | — / `~/.agents/skills` | `.codex/config.toml` `[mcp_servers.x]`, trusted projects only | `~/.codex/config.toml` | yes | Agent Plugins 1.0 (git ref/sha, npm semver) |
| Gemini CLI | only if opted in | yes, over `.gemini/skills` | `.gemini/skills` / `~/.gemini/skills` | `.gemini/settings.json` `mcpServers`, folder trust | `~/.gemini/settings.json` | yes | `gemini-extension.json` (`--ref`) |
| Cursor | yes | yes | `.cursor/skills` / `~/.cursor/skills` | `.cursor/mcp.json` `mcpServers` | `~/.cursor/mcp.json` | yes | `plugin.json` or `.cursor-plugin/` (unconfirmed) |
| Copilot in VS Code | yes (root) | yes | `.github/skills` / `~/.copilot/skills` | `.vscode/mcp.json` `servers`, or `.mcp.json` `mcpServers`; workspace trust | UI (path unconfirmed) | preview | Agent Plugins or `.claude-plugin` (none) |
| Copilot CLI | yes | yes | `.github/skills` / `~/.copilot/skills` | `.mcp.json` or `.github/mcp.json`, folder trust | `~/.copilot/mcp-config.json` | yes | Agent Plugins 1.0 (ref or full sha) |
| Copilot coding agent | yes | yes | `.github/skills` / — | repository settings (no file) | — | `.github/hooks/*.json` | unconfirmed |
| Devin Desktop (was Windsurf) | yes | yes | `.devin/skills`, `.windsurf/skills` / `~/.config/devin/skills` | none documented | `~/.config/devin/mcp_config.json` | yes | none for Cascade |
| Devin CLI | yes | yes | `.devin/skills` / `~/.config/devin/skills` | `.devin/mcp_config.json` (trust unconfirmed) | `~/.config/devin/mcp_config.json` | yes | `.devin-plugin/plugin.json` (sha/ref) |
| Amazon Q Developer IDE | unconfirmed | no (no skills) | — | `.amazonq/default.json` | `~/.aws/amazonq/default.json` | none | none |
| Kiro | yes | **unconfirmed** | `.kiro/skills` / `~/.kiro/skills` | `.kiro/settings/mcp.json` | `~/.kiro/settings/mcp.json` | yes | Powers: Agent Plugins 1.0 (unconfirmed) |
| JetBrains Junie | yes | yes | `.junie/skills` / `~/.junie/skills` | `.junie/mcp/mcp.json` | `~/.junie/mcp/mcp.json` | CLI; project hooks off by default | extensions (unconfirmed) |
| Amp | yes | yes | — / `~/.config/amp/skills` | `.amp/settings.json` `amp.mcpServers`, approval | `~/.config/amp/settings.json` | plugin events | TS plugins, no manifest (git) |
| Augment | yes | yes, lowest precedence | `.augment/skills` / `~/.augment/skills` | unconfirmed | `~/.augment/settings.json` | yes | `.augment-plugin/plugin.json` (unconfirmed) |
| Zed | yes (first match only) | yes | — / `~/.agents/skills` | `.zed/settings.json` `context_servers`, trust-gated | user `settings.json` | none | `extension.toml`, MCP only |
| Cline | yes | **no** | `.cline/skills` / `~/.cline/skills` | none documented | `~/.cline/…` (docs conflict) | yes | `package.json` `cline.plugins`, CLI only |
| Roo Code (shut down 2026-05) | yes | yes | `.roo/skills` / `~/.roo/skills` | `.roo/mcp.json` | extension storage | none | none |
| Continue | source only | **no** | `.continue/skills` / `~/.continue/skills` (source only) | `.continue/mcpServers/*` | `~/.continue/config.yaml` | CLI (source only) | Hub blocks (unconfirmed) |
| Aider | no (`--read`) | no | — | no MCP | no MCP | none | none |
| OpenHands | yes | yes, primary | `.openhands/skills` (legacy) / `~/.agents/skills` | only inside plugins | `~/.openhands/mcp.json` | yes | `.plugin/`, `.claude-plugin/` or Agent Plugins (tag/branch) |
| Goose | yes | yes | `.goose/skills` (compat) / `~/.agents/skills` | none documented | `~/.config/goose/config.yaml` `extensions` | via plugins | Open Plugins `plugin.json` (none) |
| opencode | yes | yes | `.opencode/skills` / `~/.config/opencode/skills` | `opencode.json(c)` `mcp` | `~/.config/opencode/opencode.json` | plugin events | JS/TS or npm (unconfirmed) |
| Crush | yes | yes | `.crush/skills` / `~/.config/crush/skills` | `crushrc`, legacy `crush.json` | `~/.config/crush/crushrc` | `PreToolUse` only | none |
| Kilo Code | yes | yes | `.kilo/skills` / `~/.kilo/skills` | `kilo.jsonc` `mcp` | `~/.config/kilo/kilo.jsonc` | plugin hooks | JS/TS or npm (`pkg@x.y.z`) |

The Claude Code and Antigravity rows come from their own references, not from this survey.

## What it means for an installer

- **`.agents/skills/<name>/SKILL.md` is the one place that reaches almost everyone.** Of these tools, only Claude Code, Cline, Continue, Aider and Amazon Q Developer do not read it, and Kiro's documentation does not say. Several read `.claude/skills` as well — Cursor, both Copilots, Amp, Augment, Goose, opencode, Crush, Kilo — which is how a tool that reads only its own directory is reached by those that borrow Claude's.
- **No MCP file works everywhere, and even the wrapping key differs**: `mcpServers` (most), `servers` (`.vscode/mcp.json`), `[mcp_servers.x]` (Codex TOML), `amp.mcpServers`, `context_servers` (Zed), `mcp` with an array `command` (opencode, Kilo), `extensions` with `cmd` (Goose). Registration has to be per tool. A root `.mcp.json` with `mcpServers` is read by Claude Code, Copilot CLI and VS Code Copilot.
- **Project-scope MCP is trust-gated almost everywhere** — Claude Code, Codex, Gemini CLI, both Copilots, Zed, Amp — so a written project config does nothing until the person approves it. User scope avoids the gate but applies to every project, which the lookup tolerates because it answers only where a build reported.
- **A cross-vendor plugin format now exists: Agent Plugins 1.0** — a root `plugin.json` with `$schema` `https://agent-plugins.org/schemas/1.0.0/plugin.schema.json`, an `mcp.json` with `mcpServers`, and `skills/`. Codex, both Copilots, Kiro Powers and OpenHands document it; Cursor shows a root `plugin.json` of unconfirmed schema. One such package — the skill and the server, pinned — would install natively in those tools, which is the per-harness packaging an installer could otherwise hand-write.

## Per tool, briefly

Only what the table cannot hold; sources are the official pages named.

- **Codex** — config layers `~/.codex/config.toml`, `.codex/config.toml` (trusted projects only), `/etc/codex/config.toml`. Instructions: `AGENTS.override.md` then `AGENTS.md`, root down, capped at 32 KiB; `CLAUDE.md` not read by default. Hooks in `hooks.json` or a `[hooks]` table, project hooks only when trusted. Plugin marketplaces at `.agents/plugins/marketplace.json` and `~/.agents/plugins/marketplace.json`. learn.chatgpt.com/docs (config-file, agents-md, build-skills, extend/mcp, hooks); developers.openai.com/codex/plugins/build.
- **Gemini CLI** — `GEMINI.md`; `AGENTS.md` only through `context.fileName`. Project stdio servers need `gemini trust`; changed project hooks are treated as untrusted. Extensions install to `~/.gemini/extensions`, pinned with `--ref`. geminicli.com/docs (gemini-md, skills, tools/mcp-server, hooks, extensions/reference).
- **Cursor** — rules are `.cursor/rules/*.mdc` (a plain `.md` there is ignored) plus `AGENTS.md`; skills also read from `.claude/skills` and `.codex/skills`. cursor.com/docs (context/rules, context/skills, context/mcp, agent/hooks, reference/plugins).
- **GitHub Copilot** — reads `CLAUDE.md`, `.claude/rules`, and in the CLI `GEMINI.md` too. The CLI skips project MCP servers silently in an untrusted directory and does not read `.vscode/mcp.json`. The coding agent's MCP is set in repository settings, with secrets prefixed `COPILOT_MCP_`. code.visualstudio.com/docs/copilot/customization; docs.github.com/en/copilot (cli, hooks-reference, cli-plugin-reference).
- **Devin Desktop (Windsurf)** — Windsurf became Devin Desktop on 2026-06-02; `.windsurf/` paths are still read. Cascade has only a user MCP file; the Devin CLI adds `.devin/mcp_config.json` and a `.local` override. docs.devin.ai (desktop/cascade, cli/extensibility).
- **Kiro / Amazon Q Developer** — the Q Developer CLI is now Kiro CLI; `.amazonq/` paths moved to `.kiro/`. Kiro's MCP config carries `autoApprove` per tool; only variable expansion prompts. kiro.dev/docs; docs.aws.amazon.com/amazonq.
- **Junie** — documentation moved to junie.jetbrains.com/docs. Does not load `.claude/skills`, and offers to import it. Project hooks are ignored unless `--config-location` is passed.
- **Amp** — a skill may carry its own MCP servers, in a sibling `mcp.json` or `mcpServers` frontmatter; workspace servers need `amp mcp approve`. ampcode.com/docs.
- **Augment** — skills in precedence order with `.agents/skills` last. docs.augmentcode.com/cli.
- **Zed** — reads only the first instruction file it finds; project skills and project settings load only from trusted worktrees. zed.dev/docs/ai.
- **Cline** — hooks are macOS and Linux only; plugins are CLI and SDK only. docs.cline.bot.
- **Continue** — says it "has joined Cursor"; its documentation no longer covers the CLI, skills or hooks, so those rows come from the repository's source. Its CLI hooks read `.claude/settings.json` as well as its own.
- **Aider** — reads nothing automatically; no MCP (an open request), no skills, no hooks.
- **OpenHands** — `.agents/skills` is the primary project directory; hooks also accept the `.claude/settings.json` format. docs.openhands.dev.
- **Goose** — documentation moved to goose-docs.ai; plugins install to `.agents/plugins/<name>/`.
- **opencode, Kilo Code** — share a plugin model (JS/TS or npm packages); MCP under `mcp` with `command` as an array. opencode.ai/docs; kilo.ai/docs.
- **Crush** — primary configuration is now `crushrc`; `crush.json` is deprecated. The project instruction defaults appear only in source.

## Not confirmed by the documentation

Kiro reading `.agents/skills`; Gemini reading `AGENTS.md` without the opt-in; Cursor reading `.cursorrules` or `CLAUDE.md`, and its plugin pinning; VS Code Copilot's user MCP file path; any repository MCP file for the Copilot coding agent; trust for project MCP in Devin, Junie, Augment, Roo, Continue, OpenHands, Goose, opencode, Kilo; Augment's project MCP file; Zed's project `context_servers` example; Cline's canonical user MCP path and full hook list; plugin pinning for Cursor, Kiro, Junie, Augment, Goose, opencode.
