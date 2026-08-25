---
title: What we are not running, and why
description: The experiments this project deliberately declined to run, the results that are missing because of it, and the one exception disclosed rather than fixed.
---

:::danger[The most dangerous configuration is an agent with tools]
Never point an **agentic coding tool** — one with real filesystem access, running in a real working
tree — at a third-party attack corpus. That hands live destructive code to something that reads
files, writes files and runs commands on your behalf.

**Telling the agent not to use its tools is not a control**, because whether agents follow
instructions placed in installed content is precisely what this project measures.
:::

Most research pages describe what was done. This one describes what was **not**, because in a
project about prompt injection the refusals carry as much information as the results — and a gap
that is stated is very different from a gap that is hidden.

## The measurement we gave up

**The current Claude models are absent from one experiment entirely.** That experiment scores 91
real malicious agent skills, and the only route to those models here ran **tool-enabled subagents**
in the real working tree.

Subagents run **in-process**. The container that isolates every other arm — no network, read-only
mounts, all capabilities dropped — cannot wrap something running inside the harness itself. So
there was no version of that run which was both meaningful and contained.

The obvious workaround was rejected on principle rather than on risk appetite:

> Instructing the agent not to use its tools would mean **measuring one hazard by assuming the
> other away.** Whether agents obey instructions placed in installed content is the thing under
> test. An instruction cannot be the safety control in an experiment about whether instructions are
> obeyed.

The result is a **stated gap in coverage rather than a result obtained unsafely.** Closing it needs
a sandboxed agent runner — a build, not a run — and until that exists the gap stands.

This is not a claim about any vendor or model. Our own measurements found exposure varies widely
between models of comparable capability, and that **no property of the agent can be relied on**.
The hazard is the configuration: agent, plus tools, plus a real filesystem, plus somebody else's
malware.

## What we do not execute at all

| | |
|---|---|
| **third-party attack code** | read and linted **statically**, never run. The corpora contain workflows that archive a directory and delete the originals, credential harvesters, and code that posts what it collects to a remote host |
| **network calls from any payload we author** | every sink is under `.invalid` (RFC 2606), a TLD that can never be registered and never resolves — not merely today |
| **anything with host credentials inside the container** | the container arms carry none; the arms that need vendor API access are the ones the container does not cover |

## The one exception, disclosed rather than fixed

The **original** payload set, from before this discipline was settled, names an ordinary `.io` host
this project does **not** own. It was unregistered when those results were produced and is
unregistered now — but nothing stops somebody buying it.

It has deliberately **not been changed in place**, because altering a published payload would make
already-published results incomparable: the host is part of what was measured. So instead:

- new payloads use `.invalid`, as every later test does;
- **check that the legacy host still resolves nowhere before running the tool-enabled arms**, and
  do not run them if it does not.

Rewriting it quietly would have been the tidier choice and the less honest one.

## Where the isolation does not reach

Stating this matters because a result produced under weaker isolation should not be read as though
it were produced under stronger:

| arm | isolation |
|---|---|
| linters vs the attack corpus, rule catalogue, compile-and-lint | **container, no network** |
| local model arms | host only — the GPU is unavailable to a Linux container on this platform |
| frontier-agent arms | host only — they need vendor API egress and carry host credentials |
| in-process subagents | host only — cannot be wrapped from outside |
| the Swift lint arm | host only — SwiftLint has no maintained Linux build |

The container closes the **corpus-handling** risk. It does not close the **model-arm** risk, and a
Swift row was not produced under the same conditions as a linter row.

One further honesty note: the frontier-agent harness runs with permission prompts disabled. That is
acceptable *only* because it runs inside a throwaway workspace, and it would not be acceptable
otherwise.

## If you take one thing from this page

Use a disposable machine or container, keep it off the network where you can, and **never point a
tool-enabled agent at attack corpora inside a working tree you care about.**

The rig that makes the safe arms safe is described in [running attacks safely](/safety/).
