# The npm In-Package Skill

RAD-0077 · 2026-09-17

Keywords: skills inside an npm package; skills/<name>/SKILL.md; package.json files entry; node_modules skill discovery; symlink into .agents/skills; how many skills can an agent hold; npm package name as namespace; what the JVM should copy from npm; TanStack Intent; get-tsconfig skill.

Measured against: published tarballs and installed `node_modules` trees inspected on 2026-09-12 and 2026-09-17 — `get-tsconfig` at 4.10.0, 4.14.0 and 5.0.0-beta.4, and the repository at tag `v5.0.0-beta.4` — plus the `antfu/skills-npm` proposal as read on 2026-09-17. Adoption figures are from [RAD-0072](Research-RAD-0072-The-One-Thing-We-Are-Not-Doing).

## Question

npm is the one ecosystem where libraries already ship skills to their consumers, with no standard behind it. **What exactly is the convention, what does it get right, where does it break down, and what should this project take from it rather than reinvent?**

## Trail

### The shape, as published and as installed

The skill is a hand-written directory beside the source, listed in `files`, and shipped verbatim. Nothing compiles it.

    get-tsconfig/                    the repository root, and the package root
    ├── package.json                 "files": ["dist", "skills"]
    ├── src/                         TypeScript source, compiled to dist/
    ├── skills/
    │   └── get-tsconfig/
    │       └── SKILL.md             the library's own skill
    └── tests/

Installed, that lands at `node_modules/get-tsconfig/skills/get-tsconfig/SKILL.md`. In a monorepo the same directory sits under each published package, beside its own `package.json`.

**Versioning is the library's own, by construction.** Of three copies of the same library installed on one machine, 4.10.0 and 4.14.0 declare `files: ["dist"]` and ship no skill; 5.0.0-beta.4 declares `files: ["dist", "skills"]` and ships one. The skill arrives with the version that carries it and cannot drift from it.

**The package name is the namespace.** `skills/<name>/SKILL.md` has the package's own name as the directory, which the Agent Skills specification requires to match the frontmatter `name`. So the convention is already *namespace* scoped; npm simply has one namespace per published package.

**Adoption is real and recent.** RAD-0072 measured skills in published tarballs for at least six widely-used libraries, with TanStack Intent as the visible push and adopters including Electric, tRPC, Redux Toolkit, Prisma, Apollo Client and Arcjet. The supporting proposals are weaker than the practice: `antfu/skills-npm` is still a proposal, and the agentskills `package.json` field issue closed on 2026-09-05.

### What it gets right

- **The author writes it, and it ships inside their own artifact.** Provenance is by construction — the opposite of a republishing catalogue ([RAD-0076](Research-RAD-0076-Skills-Republished-By-A-Third-Party)).
- **It needs no new mechanism.** One entry in `files`, which every npm author already understands.
- **It is version-tied**, which is the property [ADR-0009](Decisions-ADR-0009-Transport-Is-Sources-Jar) depends on for the JVM.
- **The file is `SKILL.md`**, the name every skill convention uses, in a directory named for the namespace it documents.

### Where it breaks down

- **Discovery ends in a directory of everything.** The proposal's route is to glob `node_modules/**/skills/*/SKILL.md` and symlink each one into the agent's skills directory. Every dependency that ships a skill then competes for the agent's attention at startup, and the cost grows with the dependency graph rather than with the work in hand. A project with fifty such dependencies has fifty descriptions loaded before anyone has written a line.
- **Symlinking hands third-party instructions the same standing as the developer's own skills.** Once linked into `.agents/skills/`, a library's text is indistinguishable from a skill the developer wrote, including any `allowed-tools` it declares.
- **It says nothing about the moment of use.** Loading a skill list is not a trigger. RAD-0073 measured agents writing the wrong code in 13 of 16 unprompted runs; a fuller skills directory does not address that.
- **It is npm-shaped.** A package root exists because npm publishes a directory. A jar has no equivalent that reaches the copy an agent reads — measured in [RAD-0075](Research-RAD-0075-Naming-The-Skill-File), where `META-INF` never appears in a sources jar.

### What this project should take, and what it should do differently

**Take the shape.** `<namespace>/SKILL.md`, authored by the library, shipped inside the artifact, tied to its version. On the JVM that is the library's root namespace in the source tree, which is where an import prefix finds it and where the sources jar carries it.

**Replace the symlink, not the convention.** Where npm's tooling copies every skill into the agent's directory, this project installs **one** skill — the generated pointer — that lists what the resolved dependencies carry and fetches a skill only when the code in front of the agent names that namespace. The startup cost is one description rather than fifty, and the library's text is read as a reference rather than promoted to a first-party skill. That is an alternative to the symlink plugin for anyone who already has these packages installed; it takes nothing away from the npm convention and needs no change to it.

**Do not fork the npm layout.** Where a package already ships `skills/<name>/SKILL.md`, index that. RAD-0075's experiments in placing a file in `src/` or copying one into `dist/` were tests of whether an alternative *could* work, not a proposal that anyone should.

## Findings

**Measured.**

- The convention is `skills/<name>/SKILL.md` at the package root, shipped by a `files` entry; the directory is authored beside `src/`, not generated.
- The skill is version-tied by construction: three installed copies of one library, and only the version that declares it ships one.
- The package name is the namespace, and the specification requires the directory name to match the skill's `name`.

**Observed elsewhere (RAD-0072).**

- Skills ship in published tarballs for at least six widely-used libraries; the formal proposals behind the practice are stalled or closed.

**Argued.**

- Symlinking every discovered skill into the agent's directory scales with the dependency graph, not with the task, and erases the distinction between the developer's skills and a dependency's.
- A pointer that indexes what is installed serves the same need at a fixed startup cost, and is an alternative to the symlink route rather than a competing convention.

## Recommendation

**Not a commitment.**

1. **Adopt the npm layout as it stands for npm.** Ship and index `skills/<name>/SKILL.md`; do not invent a source-tree placement for that ecosystem.
2. **Carry the same shape to the JVM as a namespace-scoped file in the source tree**, for the reasons RAD-0075 measured, and keep the file name identical so the convention reads as one thing across ecosystems.
3. **Offer the pointer as the alternative to symlinking**, and say plainly what it changes: one skill installed instead of many, a dependency's text kept as a reference, and retrieval at the moment of use.
4. **Measure the claim before repeating it.** That a pointer beats a full skills directory at scale is argued here, not measured; the comparison wants a project with a realistic number of skill-shipping dependencies.

## Connections

- [RAD-0073](Research-RAD-0073-A-Skill-Written-As-Source) — the skill inside the source, the pointer, and the uptake measurements.
- [RAD-0075](Research-RAD-0075-Naming-The-Skill-File) — where each placement lands, and why `META-INF` is not the JVM answer.
- [RAD-0076](Research-RAD-0076-Skills-Republished-By-A-Third-Party) — the same gap approached by republishing, and why authorship matters.
- [RAD-0072](Research-RAD-0072-The-One-Thing-We-Are-Not-Doing) — the distribution field and the adoption figures.
- `docs/knowledge/reference/agent-file-conventions.md` — the inventory this sits in.
