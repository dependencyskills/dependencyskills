# Public subjects worth measuring

The projects to hand are all KMP and all one author's. That is enough to
answer "what does *my* dependency graph cost" and not enough to say anything
about the ecosystems whose conventions this is meant to help.

These are suggestions, chosen for a reason rather than for fame. Clone
whichever are convenient — the count is the only thing needed from each.

## JVM / Android — the channel with no convention

- **Spring PetClinic** (`spring-projects/spring-petclinic`) — the canonical
  small-but-real Spring app. Deliberately modest, and still pulls a large
  transitive graph, which is the point: the direct/transitive gap is the
  whole argument about whether a skill per dependency is even meaningful.
- **Now in Android** (`android/nowinandroid`) — Google's reference modern
  Android app. Multi-module, Compose, Hilt. The closest thing to a
  representative modern Android dependency graph.
- **Ktor samples** or a mid-sized Ktor service — a JVM server that is not
  Spring, so the number is not an artifact of one framework's conventions.

## Kotlin Multiplatform — beyond one author

- **Compose Multiplatform samples** (`JetBrains/compose-multiplatform`,
  the examples) — JetBrains' own, so nobody can call it unrepresentative.
- **`ktorio/ktor`** itself, or another widely used KMP *library* — a
  library's own graph is smaller than an app's and is the case where a
  skill is being authored rather than consumed.

## npm — where the conventions already exist

This is where the cost argument has to land, because these ecosystems have
working directory conventions and the ceiling applies to them.

- Any Next.js app of moderate size, or **`vercel/next.js`**'s own examples —
  particularly apt given Vercel ships a skills CLI, so the cost of their own
  convention against their own dependency graph is the sharpest possible
  version of the question.
- **`vitejs/vite`** or a project using it — a smaller, more modern graph, to
  avoid the number being dismissed as a node_modules joke.
- Something deliberately heavy — a create-react-app-era project — as the
  upper bound.

## Swift / SPM

- **`pointfreeco/swift-composable-architecture`** — a real SPM library with
  real transitive dependencies, in an ecosystem where SPM graphs are usually
  small. Useful precisely because it should produce a *low* number: if the
  cost is negligible there, say so.

## Python, if it is cheap to add

- Any Django or FastAPI project of moderate size. `site-packages` unpacks,
  so the directory convention works, and the count is what decides whether
  it scales.

## What to collect

Per project: unique resolved dependencies, and how many are direct. Those
two numbers carry the argument. Everything else is detail.

Do not cherry-pick. A project with a small graph is as much a finding as one
with a large graph — if the cost turns out to be negligible outside the JVM,
that is worth publishing, and publishing it is what makes the JVM number
credible.
