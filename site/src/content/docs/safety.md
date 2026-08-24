---
title: Running attacks safely
description: The rig this project uses to run other people's attack code — a no-network container, a sinkhole that records what a payload tries to send, and an honest account of what each one does and does not guarantee.
---

Measuring prompt injection means running code written to do harm. Some of it is ours, and inert by
construction. Some of it is not: the published corpora this project measures against contain live,
runnable attack code — workflows that delete originals after archiving them, credential harvesters,
and scripts that post what they collect to a remote host.

Two questions follow, and **the usual answer to the first destroys the second**:

| question | the usual answer |
|---|---|
| Is it safe to run? | take the network away |
| What did it try to do? | *unanswerable — a payload that would exfiltrate a credential and one that does nothing look identical* |

So the rig has two modes, and which one is the default is a deliberate choice rather than a
convenience.

## Two modes

```bash
# no network at all
./experiments/run-sandboxed.sh test8 <corpus>

# a network that goes exactly one place
./experiments/run-sandboxed.sh --observe test8 <corpus>
```

Both mount the repository and the corpus **read-only**, run on a read-only root filesystem with all
capabilities dropped, `no-new-privileges`, and memory and process limits. Only a scratch results
directory is writable.

The difference is the network, and it is the difference between two kinds of claim:

| mode | the claim | what kind of claim it is |
|---|---|---|
| default | nothing was exfiltrated | a **proof** — there was no network stack to use |
| `--observe` | everything sent was recorded and went nowhere | a **strong belief** — it rests on the container platform honouring an isolated network |

That distinction is the whole reason there are two modes. When the question is *may I run this at
all*, the proof is the only acceptable answer, so it stays the default. `--observe` is for the
separate question of what a payload meant to do, and it is worth being explicit that you have
stepped down a rung to ask it.

## How the observation works

Under `--observe` the arm gets a network containing exactly one other machine: a sinkhole that
answers every DNS query with its own address, redirects every TCP port to a single listener,
terminates TLS with a certificate authority generated at startup, and writes each request down
before returning a plausible `200 OK`.

**Returning success is load-bearing.** A payload that receives a connection error may retry, fall
back to a second channel, or give up — in each case before showing what it actually intended. The
sinkhole is trying to be a convincing victim, not a wall.

Two structural choices are worth stating because both look backwards at first glance:

**The privilege sits with the recorder, never with the payload.** The sinkhole runs as root and
holds the capability needed to rewrite its own routing table. The container reading somebody else's
malware keeps every capability dropped. The port-catching rule is applied where traffic *arrives*
rather than where it leaves, which gets the same coverage without ever handing elevated privilege to
the thing under test — and leaves nothing in the payload's environment to suggest it is being
watched.

**The containment is the network, not the code.** The sinkhole's network is created as an isolated
one, so no gateway is attached to it and nothing on it has a route off the host. If every line of
the recorder were wrong, the packets would still have nowhere to go. Getting that ordering right is
what makes the second mode defensible at all.

We check that rather than assume it. On the isolated network a container's routing table contains
only its own subnet and **no default route**; the identical probe on an ordinary network finds one.
A connection aimed straight at a public IP address, bypassing the sinkhole's DNS entirely, fails to
connect.

## Why there is a positive control

A recorder that quietly records nothing is **indistinguishable from a payload that did nothing** —
and the second reading is the one everybody wants to believe.

So the rig ships a self-test that sends a known canary by four routes, and it is meant to be run
before any empty log is trusted:

| route | `--observe` | default mode |
|---|---|---|
| plain HTTP request with a body | recorded | name does not resolve |
| HTTPS, the client verifying the certificate properly | recorded | name does not resolve |
| a DNS lookup with the secret encoded in the name | recorded | name does not resolve |
| a raw socket on a non-web port | recorded | name does not resolve |

Every destination is a `.invalid` name (RFC 2606), which cannot resolve anywhere real by
construction.

This control exists because the failure it guards against has **already happened here**. An earlier
identity-leak scanner in this repository matched zero files and reported the repository clean. It
was not detecting the absence of leaks; it was not detecting anything. Any check whose passing state
is silence needs a test that it can still fail.

## What it cannot see

Stated here rather than discovered later by someone relying on it:

| gap | what you get instead |
|---|---|
| a hardcoded IP address rather than a hostname | the connection still arrives — it is the only route — but there is no record of the *intent*, only of the contact |
| a payload that pins or checks the certificate issuer | it refuses the generated authority and fails. The destination and requested hostname are recorded; the body is not |
| a JVM client | the JVM wants a keystore rather than a certificate file, so it lands in the same refused state — destinations, not bodies |
| UDP other than DNS | not captured |

A refusal is a recorded event, not a miss. Knowing that something tried to reach a host over TLS and
would not accept an unknown authority is a finding in its own right.

## What this does not cover

The container closes the static arms — the ones that read corpora, compile payloads, and run
linters. It does not close everything, and assuming otherwise would be worse than not using it at
all. Arms that drive local models need the host GPU; arms that drive vendor APIs need real egress
and carry credentials; agents running in-process cannot be wrapped from outside.

Those keep weaker protections — disposable workspaces, sinks that resolve nowhere, no network tool
offered to the agent — and this is not a hypothetical distinction. One measurement is **missing from
this project entirely** because we would not run it: the newest Claude models are absent from one
experiment because the only available route ran tool-enabled agents against real filesystem access,
and instructing an agent not to use its tools is not a control when *whether agents follow
instructions* is the thing being measured.

That gap is also what the sinkhole is ultimately for. An agent has to believe it has a network
before an attack against it will fire at all.

## Beyond the container: hardening the network

The container closes the static arms completely. The arms that drive a model **cannot be contained
the same way** — they have to reach a vendor API — so for those the network is the only boundary
left. If the gateway you already own supports the following, they are worth turning on. None of it
is exotic; most prosumer and business gateways do the first two.

**Put the test machine on its own segment.** A separate VLAN or guest network, isolated from
everything else you own. This is the highest-value step by a wide margin, and it is the one that
limits the case nobody plans for: a payload that reaches something *on your LAN* rather than the
internet.

**Default-deny outbound from that segment, with a narrow allowlist.** Permit DNS and the specific
endpoints you actually call, nothing else. The reason this matters is worth being precise about: it
**moves a guarantee from the payload to the network**. Our own sinks cannot be reached because they
are `.invalid` names — a property of what the payload says. A default-deny rule holds regardless of
what the payload says, which is what you need for third-party corpora you have read but not
audited.

Two caveats before relying on it:

- Vendor endpoints sit behind CDNs with rotating addresses, so domain-based rules are DNS-driven and
  can fail **open** when resolution shifts. Re-check that the rule still holds after a few runs.
- Some gateways apply firewall rules only to routed traffic, not to traffic between devices on the
  same segment. Isolation and egress control are separate settings, and turning on one does not give
  you the other.

**What no gateway can do is see inside the traffic.** Model API calls are TLS. A gateway sees a
hostname and a port and nothing more, so it can stop traffic reaching the *wrong destination* but
not sensitive content leaving inside a *legitimate* request. Deep packet inspection features
classify traffic; they do not read encrypted bodies. Do not assume otherwise.

Content inspection belongs in a proxy you control rather than in the network — a container whose
outbound requests go through an intercepting proxy whose certificate is trusted **inside that
container only**, so the host and the rest of the network are untouched. Bodies can then be matched
against canary strings and account patterns and blocked on match. That is the only arrangement
described here that would catch a leak *before* it left rather than after it landed in a file.

Most of that machinery now exists in the sinkhole, which already generates the authority, terminates
TLS, captures bodies and matches them through the project's redaction module. Two differences
remain, and neither is cosmetic: the sinkhole **is** the destination rather than forwarding
upstream, and it **records** rather than blocks.

**Ordering, by value against effort:** segment the machine, then default-deny egress, then — only if
there is evidence that content is leaving inside legitimate requests — the inspecting proxy.
