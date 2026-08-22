# Agent skills

This project's own skills, authored here once and packaged by each
implementation that publishes them.

Two skills, two audiences:

- **`to-library-skill/`** — for library authors. How to author a skill,
  apply the plugin for their build system, and publish it. Carries the
  install template, so applying the skill is what causes the plugin to
  exist in the build.
- **`librarian/`** — for consuming projects. The small always-loaded entry
  point that knows when to consult the index. This is the one that gets
  installed everywhere, and its `description` is the load-bearing artifact
  of the whole design.

They live here rather than under `implementations/` because they are
spec-level: the skill teaches the convention, not one build system. Only
the install template differs per build system; the content does not. A copy
inside each implementation would drift, which is the failure the monorepo
exists to prevent.

`agent-skills/` rather than `skills/` deliberately — it matches the
authoring path the convention specifies for a library, `src/agent-skills/`,
so this repo doing the same thing at its root reads as consistent rather
than coincidental.

Neither is written yet.
