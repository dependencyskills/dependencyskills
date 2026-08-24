# Sinkhole

**The question:** the static arms run with no network, which proves nothing was exfiltrated and
destroys the ability to say what a payload *tried* to do. From inside a container with no network,
an attack that would POST a harvested credential and an attack that does nothing look identical.
What did it try to send, and where was it addressed?

Used by `../run-sandboxed.sh --observe`. Not a defence, and not part of the tool — an instrument.

## How it works

Every name resolves here, every TCP port is redirected to one listener, TLS is terminated with a CA
minted at startup, and the request is written to `egress.jsonl` before a plausible `200` goes back.
The success reply is load-bearing: a payload that gets a connection error may retry, fall back to
another channel, or abort before revealing its intent.

| file | |
|---|---|
| `sinkhole.py` | DNS responder, TCP listener, per-name certificate minting, the log |
| `entrypoint.sh` | the iptables `REDIRECT` that makes one listener catch every port |
| `summarise.py` | reads `egress.jsonl` into something a person can read, through `redact.clean` |

Two design choices worth stating, because both look wrong at a glance:

**The iptables rule is in the sinkhole, not in the experiment container.** Doing it at the source
would need `NET_ADMIN` in the container running somebody else's malware, and that container keeps
`--cap-drop=ALL`. Redirecting on arrival gets the same coverage with the privilege on our side of
the line — and leaves nothing in the experiment container's configuration to suggest it is watched.

**The containment is the Docker network, not this code.** The network is created `--internal`, so
Docker attaches no gateway and nothing on it has a route off the host. If everything in this
directory were wrong, the packets would still have nowhere to go. That ordering is why `--observe`
can exist at all.

## Reading the log

Four kinds of line, and the distinction between the first two is the point of the whole exercise:

| kind | means |
|---|---|
| `dns` | a name was resolved. On its own — with no request behind it — this is either a payload that thought better of it, or one whose channel *is* the lookup, with the secret encoded in the name |
| `http` | a request completed, with body. This is the strong observation |
| `tls-refused` | it would not accept our CA. Destination recorded, body not — a refusal, not a miss |
| `raw` | not HTTP. Logged as bytes, unparsed |

## The self-test is not optional

`../run-sandboxed.sh selftest` sends a canary by all four routes. It exists because **a recorder
that silently records nothing is indistinguishable from a payload that did nothing**, and the
second reading is the one everybody wants to believe. Run it under `--observe` (all four recorded)
and under the default mode (none of them resolve) before trusting an empty log from a real arm.

That failure has already happened once in this repository, in the identity leak scanner: it matched
no files and reported clean.

## Known gaps

* A hardcoded IP never asks DNS. It still arrives — this is the only route — but there is no record
  of the intent, only of the connection.
* A payload that pins a certificate sees an unknown issuer and fails. `tls-refused`.
* Java clients want a keystore rather than a PEM, so they land in `tls-refused` too. A Java arm
  yields destinations, not bodies.
* UDP other than DNS is not captured.
