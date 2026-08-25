# Security

This repository is **security research**. It contains working prompt-injection payloads, harnesses
that read third-party attack corpora, and a container that deliberately gives malware a network to
talk to. Read this before running anything in it.

## ⚠️ Do not point a tool-enabled agent at this material

The most dangerous thing you can do here is aim an **agentic coding tool** — one with real
filesystem access, running in a real working tree — at an attack corpus. That configuration hands
live destructive code to something that reads files, writes files and runs commands for you. One
case in the corpus we measure against archives a directory and then deletes the originals.

**We specifically did not do this, and it cost us a measurement.** The current Claude models are
absent from `test8` entirely, because the only route to them here ran tool-enabled subagents in the
real working tree. Subagents run **in-process**, so the container that isolates every other
experiment cannot wrap them.

**Telling the agent not to use its tools is not a control.** Whether agents obey instructions
placed in installed content is precisely what this project measures. Using an instruction as the
safety mechanism would mean measuring one hazard by assuming the other away.

This is not a claim about any vendor or model. Our own measurements found exposure varies between
models of comparable capability, and that **no property of the agent can be relied on**. The hazard
is the *configuration*: agent, plus tools, plus a real filesystem, plus somebody else's malware.

See [SAFETY.md](SAFETY.md) for how the experiments are actually run, and what each mode does and
does not guarantee.

## Reporting a vulnerability

**In this project** — the harnesses, the sinkhole, the published payloads, the site, or the
tooling — report it privately through **GitHub's private vulnerability reporting** on this
repository. If that is unavailable to you, open an issue that says only that you have a security
finding and asks for a private channel; **do not put details in a public issue.**

Useful to include: what you ran, what happened, and what you expected. A proof of concept is
welcome but never required — a clear description is worth more than a working exploit.

**In a third-party corpus** we measure against — AgentTrap and the other published benchmarks —
report it to *those* maintainers. Their material is not ours, we do not control it, and we have
read it without auditing it line by line.

## What we consider a vulnerability here

Because this is a research repository rather than a deployed service, the interesting failures are
about **containment and honesty** rather than about exploitation:

| in scope | example |
|---|---|
| the isolation leaking | a way for a sandboxed arm to reach the network or the host filesystem |
| the sinkhole failing open | traffic escaping the internal network, or the recorder missing what it claims to catch |
| a payload that is not inert | any authored payload that could act against a real system rather than a `.invalid` sink |
| identity leaking | a real name, home path, hostname or credential reaching a commit or the published site |
| a measurement that is wrong in a way that matters | a harness scoring the payload rather than the phenomenon — this has happened, more than once |

That last row is deliberate. A published result that overstates a defence is a security problem in
a project whose output is other people's threat models.

**Out of scope:** the attack payloads themselves working as documented — that is the finding, not a
bug — and vulnerabilities in the third-party corpora, which belong upstream.

## What we do not promise

No response-time commitment and no bounty. This is a research project, not a product. What we will
do is read what you send, tell you what we think, and correct the record publicly if you are right
— the repository already carries several corrections to its own earlier claims.
