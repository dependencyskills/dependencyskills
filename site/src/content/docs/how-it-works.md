---
title: How it works
description: The two halves we are trying — a skill a library ships in its own artifact, and a lookup on the developer's machine that finds it for the version a project uses. Experimental.
---

:::caution[Experimental]
This describes what we are trying on our own projects. None of the lookup is published yet, and any of it may change.
:::

An agent should know what the libraries a project already depends on can do, and how their authors mean them to be used — for the version the project actually uses. We are trying that in two halves: the library ships the guidance, and a lookup on the developer's machine finds it.

## What a library ships

A library's authors write a skill — an ordinary [Agent Skill](https://agentskills.io/specification), a `SKILL.md` — and it travels inside the library's own published artifact: the sources jar on the JVM, the package itself for npm, PyPI, Go and Cargo. Because it ships with the code, it always describes that version. [How our libraries ship a skill](/library-skills/) has the format, where it lives, and how we set it up.

## What finds it

The other half is a small lookup that runs on the developer's machine, beside the coding agent. It has no service, no model and nothing running between sessions: the agent's tools start it, and it stops with them.

<svg xmlns="http://www.w3.org/2000/svg" width="100%" viewBox="0 0 680 510" role="img" aria-labelledby="lkT lkD" style="max-width:680px;height:auto;margin:1.5rem 0">
<title id="lkT">How the lighter lookup works</title>
<desc id="lkD">A library's authors write its skill in the source tree, and it ships inside the library's published artifact: the sources jar, or the package itself. A project's build reports what its code can import, at the resolved version, and fetches the sources jars. A lookup on the developer's machine reads that report and finds the skills in the local caches, then answers the coding agent for this project and this version. The agent lists, reads and searches the skills and gets the authors' text marked as theirs; a skill of ours, the librarian, tells it when to look.</desc>
<style>
  .lk text { font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; }
  .lk .t { font-size:14px; font-weight:500; }
  .lk .ts { font-size:12px; font-weight:400; }
  .lk .gray-b { fill:#F1F3F5; stroke:#A8B0B8; stroke-width:1; }
  .lk .gray-t { fill:#2B3238; }
  .lk .gray-s { fill:#5A646E; }
  .lk .blue-b { fill:#E6F1FB; stroke:#5B8DC4; stroke-width:1; }
  .lk .blue-t { fill:#0C447C; }
  .lk .blue-s { fill:#2E6BA8; }
  .lk .amber-b { fill:#FDF3E3; stroke:#D9A441; stroke-width:1; }
  .lk .amber-t { fill:#7A4E0B; }
  .lk .amber-s { fill:#A5701A; }
  .lk .teal-b { fill:#E3F4F1; stroke:#4C9E93; stroke-width:1; }
  .lk .teal-t { fill:#0F4F49; }
  .lk .teal-s { fill:#2A776E; }
  .lk .purple-b { fill:#EFEAFA; stroke:#8B76C4; stroke-width:1; }
  .lk .purple-t { fill:#40317A; }
  .lk .purple-s { fill:#61509E; }
  .lk .green-b { fill:#E8F4EA; stroke:#5C9A68; stroke-width:1; }
  .lk .green-t { fill:#1E4F2B; }
  .lk .green-s { fill:#3B7448; }
  .lk .edge { stroke:#7D8590; stroke-width:1.5; fill:none; }
  .lk .head { fill:none; stroke:#7D8590; stroke-width:1.5; stroke-linecap:round; stroke-linejoin:round; }
  .lk .region { fill:none; stroke:#B6BFC9; stroke-width:1; stroke-dasharray:4 4; }
  .lk .plain { fill:#57606A; }
  .lk .lead { fill:#2B3238; }
  :root[data-theme='dark'] .lk .gray-b { fill:#242A30; stroke:#4A545E; stroke-width:1; }
  :root[data-theme='dark'] .lk .gray-t { fill:#E6EDF3; }
  :root[data-theme='dark'] .lk .gray-s { fill:#A5B0BA; }
  :root[data-theme='dark'] .lk .blue-b { fill:#10304F; stroke:#4B7FB5; stroke-width:1; }
  :root[data-theme='dark'] .lk .blue-t { fill:#CFE3F7; }
  :root[data-theme='dark'] .lk .blue-s { fill:#9CC2E6; }
  :root[data-theme='dark'] .lk .amber-b { fill:#3A2B10; stroke:#B98B2E; stroke-width:1; }
  :root[data-theme='dark'] .lk .amber-t { fill:#F6E2BC; }
  :root[data-theme='dark'] .lk .amber-s { fill:#DCC08A; }
  :root[data-theme='dark'] .lk .teal-b { fill:#103733; stroke:#3F8E83; stroke-width:1; }
  :root[data-theme='dark'] .lk .teal-t { fill:#C7EAE4; }
  :root[data-theme='dark'] .lk .teal-s { fill:#93CFC6; }
  :root[data-theme='dark'] .lk .purple-b { fill:#241C3D; stroke:#7A66B4; stroke-width:1; }
  :root[data-theme='dark'] .lk .purple-t { fill:#DDD3F5; }
  :root[data-theme='dark'] .lk .purple-s { fill:#B7A7E4; }
  :root[data-theme='dark'] .lk .green-b { fill:#14301B; stroke:#4C8459; stroke-width:1; }
  :root[data-theme='dark'] .lk .green-t { fill:#CDE8D3; }
  :root[data-theme='dark'] .lk .green-s { fill:#9CCBA6; }
  :root[data-theme='dark'] .lk .edge { stroke:#7D8590; stroke-width:1.5; fill:none; }
  :root[data-theme='dark'] .lk .head { fill:none; stroke:#7D8590; stroke-width:1.5; stroke-linecap:round; stroke-linejoin:round; }
  :root[data-theme='dark'] .lk .region { fill:none; stroke:#3D444D; stroke-width:1; stroke-dasharray:4 4; }
  :root[data-theme='dark'] .lk .plain { fill:#9AA4AE; }
  :root[data-theme='dark'] .lk .lead { fill:#E6EDF3; }
</style>
<defs><marker id="lkArrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path class="head" d="M0,1 L9,5 L0,9"/></marker></defs>
<g class="lk">
<rect class="blue-b" x="220" y="20" width="360" height="54" rx="4"/>
<text class="t blue-t" x="400" y="44" text-anchor="middle">a library's authors write its skill</text>
<text class="ts blue-s" x="400" y="62" text-anchor="middle">src/commonMain/skills/&lt;name&gt;/SKILL.md</text>
<line class="edge" x1="400" y1="74" x2="400" y2="110" marker-end="url(#lkArrow)"/>
<text class="ts plain" x="412" y="96">inside its published artifact</text>
<rect class="gray-b" x="220" y="112" width="360" height="54" rx="4"/>
<text class="t gray-t" x="400" y="136" text-anchor="middle">the sources jar, or the package itself</text>
<text class="ts gray-s" x="400" y="154" text-anchor="middle">Maven Central, npm, PyPI, Go, Cargo</text>
<line class="edge" x1="400" y1="166" x2="400" y2="202" marker-end="url(#lkArrow)"/>
<text class="ts plain" x="412" y="188">fetched by the project's build</text>
<rect class="amber-b" x="220" y="204" width="360" height="54" rx="4"/>
<text class="t amber-t" x="400" y="228" text-anchor="middle">the project's build reports what it uses</text>
<text class="ts amber-s" x="400" y="246" text-anchor="middle">what its code can import, at the resolved version</text>
<line class="edge" x1="400" y1="258" x2="400" y2="294" marker-end="url(#lkArrow)"/>
<text class="ts plain" x="412" y="280">the report the lookup reads</text>
<rect class="teal-b" x="220" y="296" width="360" height="54" rx="4"/>
<text class="t teal-t" x="400" y="320" text-anchor="middle">the lookup, on the developer's machine</text>
<text class="ts teal-s" x="400" y="338" text-anchor="middle">finds the skills in the local caches</text>
<line class="edge" x1="400" y1="350" x2="400" y2="418" marker-end="url(#lkArrow)"/>
<text class="ts plain" x="412" y="388">this project, this version</text>
<rect class="green-b" x="220" y="420" width="360" height="70" rx="4"/>
<text class="t green-t" x="400" y="444" text-anchor="middle">the coding agent</text>
<text class="ts green-s" x="400" y="462" text-anchor="middle">lists, reads and searches the skills</text>
<text class="ts green-s" x="400" y="478" text-anchor="middle">gets the authors' text, marked as theirs</text>
<rect class="purple-b" x="20" y="428" width="160" height="54" rx="4"/>
<text class="t purple-t" x="100" y="452" text-anchor="middle">librarian skill</text>
<text class="ts purple-s" x="100" y="470" text-anchor="middle">tells it when to look</text>
<line class="edge" x1="180" y1="455" x2="218" y2="455" marker-end="url(#lkArrow)"/>
</g>
</svg>

### What the project tells it

A Gradle or Maven build writes down what the code compiles against — everything it can import, not only what it declares — in a standard SBOM file, which in our setup a build plugin of ours produces. The same build fetches the sources jars of what it reports, because a build does not download them by default. An npm, Python, Go or Cargo project needs nothing extra: the lookup reads what the project declares and what is installed.

### Where it finds the skills

Where they already are. On the JVM that is the sources jars in the Gradle cache and the local Maven repository; for the other ecosystems, the installed package's own directory. It never downloads anything. A library's skill is taken only from the artifact whose coordinate its name encodes, so one library cannot speak for another. What it finds is indexed once per library version, in one index per machine that it can always rebuild.

### What the agent can ask

Three things, and every answer is for the version this project resolved:

- **What do my dependencies offer?** The libraries in the project that ship a skill, each with one line on what it is for.
- **How is this one meant to be used?** One library's skill, or a file it links to — a Swift or JavaScript reference, say.
- **Does something already do this?** A need in plain words — *"format a date for display"*, *"retry a failed request"* — matched against what the project's libraries say they do. It also searches the other libraries already on the machine, but for those it shows only what each says it is for: a library the project did not choose may describe itself, and may not instruct.

### What it will and will not hand over

The library authors' own text, as written, and marked as theirs — documentation from that library, not instructions from the developer, and never permission to run a command, fetch a link or install anything. It does not rewrite it, and it does not screen it: what keeps the surface small is that only a library's own authors wrote it, only for libraries the project chose. [The heavier design](/codex/) is the one that rewrites.

### How an agent reaches it

As an MCP server, or, where none is set up, through the same answers from a command. A skill of ours, `librarian`, sits in the project and tells the agent when to look: before calling into a library, and before writing something a library might already do — the moment an agent is least likely to think of asking.

## What is still open

How it is packaged. What a developer installs, where an MCP server is registered when one configuration serves every project, and whether the lookup should travel inside the `librarian` skill as a script so it works with nothing installed — sandboxes, worktrees and cloud sessions included — are all undecided. So is whether our build plugins are published at all; a library does not need them to ship a skill.
