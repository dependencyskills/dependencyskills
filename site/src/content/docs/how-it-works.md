---
title: How it works
description: The shape of the indexer — a shared machine-level store keyed by coordinate, and the trust boundary that decides what a coding agent ever sees.
---

An agent gets an index of what your dependencies can do. Two things decide the shape of the
thing that builds it: **what it costs**, and **what the agent is allowed to read**.

<svg xmlns="http://www.w3.org/2000/svg" width="100%" viewBox="0 0 680 554" role="img" aria-labelledby="hiwT hiwD" style="max-width:680px;height:auto;margin:1.5rem 0">
<title id="hiwT">How the indexer works</title>
<desc id="hiwD">A project resolves its dependencies and asks which coordinates are not yet indexed. A shared machine-level store, keyed by coordinate and version, runs harvest, parse, classify and summarise once per library version and holds a two-faced index. Queries are scoped to the coordinates this project resolved, and only the rewritten sentence crosses a trust boundary to the coding agent.</desc>
<style>
  .hiw text { font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Helvetica, Arial, sans-serif; }
  .hiw .t { font-size:14px; font-weight:500; }
  .hiw .ts { font-size:12px; font-weight:400; }
  .hiw .gray-b { fill:#F1F3F5; stroke:#A8B0B8; stroke-width:1; }
  .hiw .gray-t { fill:#2B3238; }
  .hiw .gray-s { fill:#5A646E; }
  .hiw .blue-b { fill:#E6F1FB; stroke:#5B8DC4; stroke-width:1; }
  .hiw .blue-t { fill:#0C447C; }
  .hiw .blue-s { fill:#2E6BA8; }
  .hiw .amber-b { fill:#FDF3E3; stroke:#D9A441; stroke-width:1; }
  .hiw .amber-t { fill:#7A4E0B; }
  .hiw .amber-s { fill:#A5701A; }
  .hiw .teal-b { fill:#E3F4F1; stroke:#4C9E93; stroke-width:1; }
  .hiw .teal-t { fill:#0F4F49; }
  .hiw .teal-s { fill:#2A776E; }
  .hiw .purple-b { fill:#EFEAFA; stroke:#8B76C4; stroke-width:1; }
  .hiw .purple-t { fill:#40317A; }
  .hiw .purple-s { fill:#61509E; }
  .hiw .green-b { fill:#E8F4EA; stroke:#5C9A68; stroke-width:1; }
  .hiw .green-t { fill:#1E4F2B; }
  .hiw .green-s { fill:#3B7448; }
  .hiw .edge { stroke:#7D8590; stroke-width:1.5; fill:none; }
  .hiw .head { fill:none; stroke:#7D8590; stroke-width:1.5; stroke-linecap:round; stroke-linejoin:round; }
  .hiw .region { fill:none; stroke:#B6BFC9; stroke-width:1; stroke-dasharray:4 4; }
  .hiw .plain { fill:#57606A; }
  .hiw .lead { fill:#2B3238; }
  :root[data-theme='dark'] .hiw .gray-b { fill:#242A30; stroke:#4A545E; stroke-width:1; }
  :root[data-theme='dark'] .hiw .gray-t { fill:#E6EDF3; }
  :root[data-theme='dark'] .hiw .gray-s { fill:#A5B0BA; }
  :root[data-theme='dark'] .hiw .blue-b { fill:#10304F; stroke:#4B7FB5; stroke-width:1; }
  :root[data-theme='dark'] .hiw .blue-t { fill:#CFE3F7; }
  :root[data-theme='dark'] .hiw .blue-s { fill:#9CC2E6; }
  :root[data-theme='dark'] .hiw .amber-b { fill:#3A2B10; stroke:#B98B2E; stroke-width:1; }
  :root[data-theme='dark'] .hiw .amber-t { fill:#F6E2BC; }
  :root[data-theme='dark'] .hiw .amber-s { fill:#DCC08A; }
  :root[data-theme='dark'] .hiw .teal-b { fill:#103733; stroke:#3F8E83; stroke-width:1; }
  :root[data-theme='dark'] .hiw .teal-t { fill:#C7EAE4; }
  :root[data-theme='dark'] .hiw .teal-s { fill:#93CFC6; }
  :root[data-theme='dark'] .hiw .purple-b { fill:#241C3D; stroke:#7A66B4; stroke-width:1; }
  :root[data-theme='dark'] .hiw .purple-t { fill:#DDD3F5; }
  :root[data-theme='dark'] .hiw .purple-s { fill:#B7A7E4; }
  :root[data-theme='dark'] .hiw .green-b { fill:#14301B; stroke:#4C8459; stroke-width:1; }
  :root[data-theme='dark'] .hiw .green-t { fill:#CDE8D3; }
  :root[data-theme='dark'] .hiw .green-s { fill:#9CCBA6; }
  :root[data-theme='dark'] .hiw .edge { stroke:#7D8590; stroke-width:1.5; fill:none; }
  :root[data-theme='dark'] .hiw .head { fill:none; stroke:#7D8590; stroke-width:1.5; stroke-linecap:round; stroke-linejoin:round; }
  :root[data-theme='dark'] .hiw .region { fill:none; stroke:#3D444D; stroke-width:1; stroke-dasharray:4 4; }
  :root[data-theme='dark'] .hiw .plain { fill:#9AA4AE; }
  :root[data-theme='dark'] .hiw .lead { fill:#E6EDF3; }
</style>
<defs><marker id="hiwArrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto-start-reverse"><path class="head" d="M0,1 L9,5 L0,9"/></marker></defs>
<g class="hiw">
<rect class="blue-b" x="140" y="50" width="400" height="54" rx="4"/>
<text class="t blue-t" x="340" y="74" text-anchor="middle">this project resolves its dependencies</text>
<text class="ts blue-s" x="340" y="92" text-anchor="middle">declared by default, transitive opt-in</text>
<rect class="region" x="40" y="134" width="600" height="212" rx="12"/>
<text class="t lead" x="56" y="157">shared store — keyed by coordinate and version</text>
<text class="ts plain" x="56" y="175">built once per library version, reused by every project on the machine</text>
<rect class="gray-b" x="60" y="186" width="126" height="48" rx="4"/>
<text class="t gray-t" x="123" y="208" text-anchor="middle">harvest</text>
<text class="ts gray-s" x="123" y="225" text-anchor="middle">source or class</text>
<rect class="gray-b" x="206" y="186" width="126" height="48" rx="4"/>
<text class="t gray-t" x="269" y="208" text-anchor="middle">parse</text>
<text class="ts gray-s" x="269" y="225" text-anchor="middle">dedupe</text>
<rect class="amber-b" x="352" y="186" width="126" height="48" rx="4"/>
<text class="t amber-t" x="415" y="208" text-anchor="middle">classify</text>
<text class="ts amber-s" x="415" y="225" text-anchor="middle">the gate</text>
<rect class="teal-b" x="498" y="186" width="126" height="48" rx="4"/>
<text class="t teal-t" x="561" y="208" text-anchor="middle">summarise</text>
<text class="ts teal-s" x="561" y="225" text-anchor="middle">quarantine</text>
<rect class="purple-b" x="76" y="262" width="250" height="64" rx="4"/>
<text class="t purple-t" x="201" y="288" text-anchor="middle">raw documentation</text>
<text class="ts purple-s" x="201" y="306" text-anchor="middle">vector only, never shown</text>
<rect class="green-b" x="354" y="262" width="250" height="64" rx="4"/>
<text class="t green-t" x="479" y="288" text-anchor="middle">rewritten sentence</text>
<text class="ts green-s" x="479" y="306" text-anchor="middle">vector and shown text</text>
<rect class="blue-b" x="140" y="380" width="400" height="54" rx="4"/>
<text class="t blue-t" x="340" y="404" text-anchor="middle">query — scoped to this project</text>
<text class="ts blue-s" x="340" y="422" text-anchor="middle">only coordinates this project resolved</text>
<text class="ts plain" x="352" y="124">only coordinates not already indexed</text>
<path class="edge" d="M340,104 V134" marker-end="url(#hiwArrow)"/>
<path class="edge" d="M186,210 H206" marker-end="url(#hiwArrow)"/>
<path class="edge" d="M332,210 H352" marker-end="url(#hiwArrow)"/>
<path class="edge" d="M478,210 H498" marker-end="url(#hiwArrow)"/>
<path class="edge" d="M340,234 V262" marker-end="url(#hiwArrow)"/>
<path class="edge" d="M340,346 V380" marker-end="url(#hiwArrow)"/>
<path class="edge" d="M340,464 V480" marker-end="url(#hiwArrow)"/>
<path class="edge" d="M340,434 V464"/>
<line x1="40" y1="464" x2="640" y2="464" stroke="#C0392B" stroke-width="1.5" stroke-dasharray="6 4"/>
<text class="ts plain" x="636" y="458" text-anchor="end">trust boundary — only the rewrite crosses</text>
<rect class="green-b" x="200" y="480" width="280" height="54" rx="4"/>
<text class="t green-t" x="340" y="504" text-anchor="middle">the coding agent</text>
<text class="ts green-s" x="340" y="522" text-anchor="middle">sees the rewrite and the signature</text>
</g>
</svg>


<p style="margin-top:0.75rem"><a href="/how-it-works.svg" download>Download this diagram (SVG)</a></p>

## The cost problem, and why the store is shared

The expensive step is rewriting each piece of documentation into a sentence in a caller's own
words, and that is one local model call **per documented declaration**. A single small project —
99 dependencies — produces about **5,400** of them once duplicates are removed. Rebuilding that
for every project, on every machine, in front of every checkout, is not a thing anyone would run
twice.

But a resolved dependency never changes. `io.ktor:ktor-client-core:3.5.1` is the same artifact
everywhere, for ever, so what we extract from it is the same too. That makes it cacheable with no
invalidation problem at all — the same property the Gradle and Maven caches already rely on.

So the store lives **on the machine, not in the project**, keyed by coordinate and version. The
first project to use a library pays. Every project after that pays nothing. Without this the
design does not work, and the rewriting step — which is also the security control — would have to
be dropped.

## The per-project part is small, and it is a boundary

A project resolves its dependencies, asks the store which of those it has not seen before, and
indexes only those. Everything else is already there.

Queries are then **scoped to the coordinates that project actually resolved**. This is not a
performance filter. A shared store holds entries from every library any project on the machine
has ever pulled in, and without the scope a poisoned entry dragged in by one project would be
reachable from another that never depended on it — a laundering route created by our own caching
decision. The scope is what closes it.

By default the index covers **declared** dependencies only. The transitive tail is opt-in, and it
is a real trade rather than a free default: when we measured it, **11 of 17** capabilities a
developer actually reached for lived only in the tail.

## Two faces, because they fail on different questions

Each entry is stored twice over: once as the library's **own documentation**, and once as the
**rewritten sentence**. Both are searchable; only the rewrite is ever displayed.

That is safe because a search key is a list of numbers, and nothing reads it. The original text
can decide *which* entry surfaces without ever reaching the agent — which also means an entry
whose rewrite was rejected can still be **found**, rather than silently vanishing from search.

Keeping both is measurably better than either alone: on the same questions, both faces together
put the right answer in the first ten **15 times out of 17**, against 13 for the documentation
alone and 10 for the rewrite alone. Gluing the two texts into a single key is *worse* than either —
the gain needs them kept apart.

## The boundary at the bottom

Library documentation is written by whoever published the library, and
[some of it is hostile](/injection/). The rewriting step exists so that text never reaches the
agent verbatim, and a cheap classifier sits in front of it to catch
[the casual attempts](/experiments/) before they get that far.

What crosses the line to the agent is the rewrite and the signature. Nothing else does.

The decision behind all of this, including what was rejected, is
[ADR-0012](https://github.com/dependencyskills/dependencyskills/blob/master/docs/knowledge/decisions/ADR-0012-a-shared-machine-level-index-store.md).
