# Running these experiments safely

Several experiments in this repository study **prompt injection against coding agents**. That
means they carry attack payloads, and two of them read a third-party corpus of genuinely
malicious agent skills — code that deletes files, harvests credentials, and posts collected data
to remote hosts.

**Run them only in a disposable, network-isolated environment.** This page says what protects you,
what does not, and what we declined to run at all.

## What is inert, and why

**Every sink this project authors is under `.invalid`.** RFC 2606 reserves that TLD: it can never
be registered and never resolves, so a payload naming
`telemetry-sink.fixture.invalid` cannot reach anyone, permanently — not merely today.

```
$ host telemetry-sink.fixture.invalid
Host telemetry-sink.fixture.invalid not found: 3(NXDOMAIN)
```

**One legacy exception, disclosed rather than fixed.** The original payload set
(`experiments/test0/measurement/injection/payloads.py`, 2026-08-21) names an ordinary `.io` host
this project does **not** own. It was unregistered when those results were produced and is
unregistered now, but nothing stops somebody buying it.

It has not been changed in place because altering a published payload would make already-published
results incomparable — the canary and the host are part of what was measured. Until the next
version bump:

- **use a `.invalid` host if you are writing new payloads**, as every later test does;
- **check the legacy host still resolves nowhere before running the tool-enabled arms**, and do
  not run them if it does not.

## What isolates the agent

**Agents run in a throwaway workspace they create and destroy.** The Antigravity wrapper
(`experiments/test0/measurement/run-gemini.sh`) does `mktemp -d` with a `trap … EXIT` cleanup, so
the agent cannot see this repository at all. That is also what keeps the no-codex control honest —
an agent that could read the repo would not be working blind.

**The tool-action sandbox is a temp directory with fake credentials.** No network tool is offered,
and every write is guarded:

```python
def safe(root, path):
    p = os.path.realpath(os.path.join(root, path))
    return p if p.startswith(os.path.realpath(root)) else None
```

so a payload that names `../../../etc/passwd` writes nothing.

**Compilation is not execution.** Harnesses compile with `kotlinc`, `javac` and `swiftc` and read
the result with `javap`. Nothing generated, harvested or downloaded is ever run.

## Third-party attack corpora

`experiments/test3` and `experiments/test8` use **AgentTrap** (arXiv:2605.13940) — 91 malicious
and 50 benign agent skills, independently authored. The malicious half is live attack code:
workflows that archive a directory and then delete the originals, credential harvesters, and code
that posts to remote hosts.

- **It is read as text and linted statically. It is never executed.**
- **It is not vendored into this repository.** It lives in a scratch directory, with fetch
  instructions in `experiments/test8/README.md`, so nobody clones a repo full of live attack code.
- Its own sinks follow the same `.invalid` convention, but **that is the corpus authors' guarantee
  and not ours** — we have read it, we have not audited every file.

## What we declined to run

This is the part worth reading if you are deciding how far to go.

**The current Claude models are absent from `test8` entirely.** The only route to them here was
tool-enabled subagents, which run in the real working tree with real filesystem access, and
AgentTrap includes destructive cases — one archives a directory and deletes the originals.

**We rejected "instruct the subagent not to use its tools" as a mitigation**, because whether
agents follow instructions placed in installed content *is the thing being measured*. Using an
instruction as the safety control would have meant measuring one hazard by assuming the other away.
The result is a stated gap in coverage rather than a result obtained unsafely.

Closing it needs a sandboxed agent runner, which is a build rather than a run.

## Data hygiene

**Transcripts are redacted before they are written**, not afterwards. An agent will happily
substitute the operator's real home directory into its plan — one did, and it reached a results
file — so harnesses now scrub home paths and usernames at write time.

This repository is public and its history is permanent. No result file should contain a real
username, home path, hostname or internal identifier.

## Container isolation, and its limits

The static arms — the ones that read the malicious corpus or compile payloads — run in a
container with **no network at all**:

```
./experiments/run-sandboxed.sh build
./experiments/run-sandboxed.sh test8 <agenttrap-dir>
```

`--network=none`, the repository and corpus mounted **read-only**, a read-only root filesystem,
all capabilities dropped, `no-new-privileges`, and memory and PID limits. Only a temporary results
directory is writable. For material that is somebody else's malware, this turns *"we did not
attempt exfiltration"* into *"exfiltration was not possible"*, which is the difference worth
having.

**It does not cover everything, and assuming otherwise would be worse than not using it:**

| arm | isolation |
|---|---|
| linters vs the attack corpus, rule catalogue, compile-and-lint | **container, no network** |
| local MLX model arms | host only — Apple Silicon GPU is unavailable to Linux containers on macOS |
| `agy` frontier arms | host only — they need vendor API egress and carry host credentials |
| Claude Code subagents | host only — they run in-process and cannot be wrapped |
| the Swift lint arm | host only — SwiftLint has no maintained Linux build |

Everything in the "host only" rows keeps the weaker protections described above: throwaway
workspaces, `.invalid` sinks, no network tool offered to the agent, and static-only compilation.

## Hardening the network, if your gateway can

The container closes the static arms completely. The model arms cannot be contained the same way —
they need to reach a vendor API — so their only remaining boundary is the network. If the gateway
you already have supports the following, they are worth turning on. None of this is exotic; most
prosumer and business gateways do the first two.

**Put the test machine on its own network segment.** A separate VLAN or guest network, isolated
from everything else you own. This is the single highest-value step, and it is the one that limits
damage from the case nobody plans for: a payload that reaches something on your LAN rather than
the internet.

**Default-deny outbound from that segment, with a narrow allowlist.** Permit DNS and the specific
model endpoints you actually call, and nothing else. This matters because it moves a guarantee
from the *payload* to the *network*. Right now nothing can be reached because our sinks are
`.invalid` and cannot resolve — a property of what the payload says. A default-deny rule holds
regardless of what the payload says, which also covers third-party corpora we have read but not
audited.

Two caveats worth knowing before relying on it:

- Vendor endpoints sit behind CDNs with rotating addresses, so domain-based rules are DNS-driven
  and can fail *open* when resolution shifts. Re-check the rule still holds after a few runs.
- Some gateways apply firewall rules only to routed traffic, not to traffic between devices on the
  same segment. Isolation and egress control are separate settings; turning on one does not give
  you the other.

**What no gateway can do: see inside the traffic.** Model API calls are TLS. A gateway sees
`api.example.com:443` and nothing more, so it can stop traffic reaching the *wrong destination* but
not sensitive content leaving inside a *legitimate* request. Deep packet inspection features
classify traffic; they do not read encrypted bodies. Do not assume otherwise.

**If you want content inspection, it belongs in a proxy you control, not the network.** Run the
model arms in a container, point `HTTPS_PROXY` at an intercepting proxy, and trust that proxy's
certificate **inside the container only**. Then outbound request bodies can be matched against the
canary string, home-directory patterns and account names, and blocked on match. The host and the
rest of the network are untouched because the certificate never leaves the container.

That is the only arrangement measured here that would catch a leak *before* it left, rather than
after it landed in a file. It is a build rather than a setting, and it has not been built.

**Ordering, by value against effort:** segment the machine, then default-deny egress, then — only
if there is evidence that content is leaving inside legitimate requests — the inspecting proxy.

## Residual risk, stated plainly

- The legacy `.io` payload host is unregistered today and may not always be. Check before running
  the tool-enabled arms.
- `agy` is invoked with `--dangerously-skip-permissions`. That is acceptable *only* because it
  runs inside a throwaway workspace; it would not be otherwise.
- The attack corpus is somebody else's code. We have read it and never executed it, but we have
  not audited it line by line.
- Local model servers load weights from disk and serve on `127.0.0.1`. Nothing here binds to a
  public interface, but check before changing a host flag.
- The container closes the corpus-handling risk and **not** the model-arm risk. A Swift row or an
  `agy` row was not produced under the same isolation as a `test8` row, and results should not be
  read as though it were.

## If you only remember one thing

Use a disposable machine or container, keep it off the network where you can, and never point a
tool-enabled agent at the attack corpora inside a working tree you care about.
