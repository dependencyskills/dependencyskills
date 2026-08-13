# Tracker Binding: YouTrack (to-issues)

Connection facts from `.agents/config/story-tools.json` (mcpServer names the
MCP server, project names the YouTrack project). Credentials come from the
story-tools installer - never collected in chat.

| Operation | YouTrack command |
|---|---|
| Create a story | `create_issue` (MCP predefined) in the config's project, body in canonical format |
| Link a blocker | `link_issues` with "depends on" (fallback: note the ID under a "Blocked by" line in the body) |
| Link to a parent issue | `link_issues` "subtask of" when the source was an epic/issue; otherwise reference it in `## References` |
| Tag AFK-ready slices | `manage_issue_tags` → `ready-for-agent` |
| Tag strict-rule slices | `manage_issue_tags` → `needs-gherkin` (gates completion on a `## QA` Gherkin section) |
| Topical batch tag | `manage_issue_tags` → the Title Case feature/PRD name (e.g. `Trust Insights`); query later with `tag: {Trust Insights}` |
| Set release | `update_issue` → Fix versions (field + version values are project settings) |
| List dimension values | `story_project_dimensions` - show available Subsystem/Type/Priority/Fix versions values in the quiz (fallback: sample recent issues) |
| Set Subsystem | `update_issue` → Subsystem (from the listed values; new values are a deliberate, admin-level addition) |
| Set Priority | `update_issue` → Priority (Show-stopper/Critical/Major/Normal/Minor) |
| Set Estimation | `update_issue` → Estimation (e.g. `4h`, `1d`; requires time tracking enabled - the installer turns it on, or Project Settings > Time Tracking) |
