#!/usr/bin/env python3
"""
Turn an `egress.jsonl` into something a person can read in the terminal.

The raw log is the record; this is the reading of it. Two things it does deliberately:

  * **It separates asking from sending.** A DNS lookup with no connection behind it is a payload
    that resolved a host and then thought better of it — or one whose exfiltration channel *is*
    the lookup. Those are different findings from a POST with a body, and collapsing them into one
    "contacted a remote host" count would lose the distinction the sinkhole exists to draw.
  * **It redacts.** Whatever a payload harvested is in the body verbatim, and this summary is the
    part someone pastes into a report. `clean()` is the same function the harnesses write through.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
from redact import clean  # noqa: E402

BODY_PREVIEW = 300


def main(path):
    events = []
    with open(path) as fh:
        for line in fh:
            line = line.strip()
            if line:
                try:
                    events.append(json.loads(line))
                except json.JSONDecodeError:
                    pass

    looked_up = [e for e in events if e.get("kind") == "dns"]
    requests = [e for e in events if e.get("kind") == "http"]
    refused = [e for e in events if e.get("kind") == "tls-refused"]
    raw = [e for e in events if e.get("kind") in ("raw", "connect", "tls")]

    reached = {e.get("host", "") for e in requests}
    asked = {e.get("name", "") for e in looked_up}

    print()
    print("egress observed")
    print("=" * 62)
    print(f"  names resolved      {len(asked)}")
    print(f"  requests completed  {len(requests)}")
    print(f"  TLS refused by peer {len(refused)}   (destination seen, body not)")
    print(f"  other connections   {len(raw)}")

    if asked:
        print("\nnames asked for — a lookup alone can be the exfiltration")
        for name in sorted(asked):
            mark = "sent to" if any(name in (h or "") for h in reached) else "resolved only"
            print(f"  {mark:<14} {clean(name)}")

    if requests:
        print("\nwhat was sent")
        for e in requests:
            body = clean(e.get("body", ""))
            body = " ".join(body.split())
            if len(body) > BODY_PREVIEW:
                body = body[:BODY_PREVIEW] + f" … [{e.get('body_bytes', 0)} bytes]"
            print(f"\n  {e.get('method', '')} {clean(e.get('target', ''))}")
            for key in ("authorization", "x-api-key", "cookie", "user-agent"):
                if key in e.get("headers", {}):
                    print(f"    {key}: {clean(e['headers'][key])[:80]}")
            if body:
                print(f"    body: {body}")

    if refused:
        print("\nrefused our certificate — it wanted a real one")
        for e in refused:
            print(f"  {clean(str(e.get('host')))}:{e.get('port')}")

    if raw:
        print("\nconnections that were not HTTP")
        for e in raw:
            data = " ".join(clean(e.get("data", "")).split())[:120]
            print(f"  port {e.get('port')}  {e.get('note', '')}  {data}")

    print()
    if not (requests or refused or raw) and not asked:
        print("nothing was sent, and nothing was looked up.")
    return 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        print("usage: summarise.py <egress.jsonl>", file=sys.stderr)
        sys.exit(2)
    sys.exit(main(sys.argv[1]))
