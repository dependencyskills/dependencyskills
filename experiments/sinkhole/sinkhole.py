#!/usr/bin/env python3
"""
A sinkhole that answers every name and accepts every connection, and writes down what it was sent.

WHY. The static arms run under `--network=none`, which is a proof: nothing was exfiltrated because
nothing could be. That proof costs us the measurement. A payload that tries to POST a credential
to a remote host and one that does nothing at all look identical from inside a container with no
network, and the difference between them is exactly what the experiments are trying to see.

This gives the container a network that goes to precisely one place. Every hostname resolves here,
every TCP connection lands here, TLS is terminated here with a CA generated at startup, and the
request is written to `egress.jsonl` before a plausible success is returned. The payload believes
it succeeded, which matters: an attack that gets a connection error may retry, fall back, or abort
before revealing what it meant to do.

WHAT MAKES THIS SAFE is not this file. It is the Docker network, created `--internal`, which has no
route off the host. If everything below were wrong, the packets would still have nowhere to go.
This process is the recorder, not the containment.

HOW IT CATCHES EVERY PORT. An iptables REDIRECT in *this* container's PREROUTING chain sends all
inbound TCP to one listener, and the original port is recovered with SO_ORIGINAL_DST. Doing the
redirect here rather than in the experiment container is deliberate — the experiment container
keeps `--cap-drop=ALL`, and nothing about its configuration hints that it is being watched.

KNOWN GAPS, stated rather than discovered later:
  * A payload connecting to a hardcoded IP rather than a hostname never asks DNS. It still arrives
    here, because this is the only route on the network, but the address it wanted is the one it
    dialled, and that is what gets logged.
  * A payload that pins a certificate, or checks the issuer, sees a CA it does not know and fails.
    The connection and its SNI are logged; the body is not. That is a recorded refusal, not a miss.
  * UDP other than DNS is not captured.
"""
import json
import os
import socket
import ssl
import struct
import sys
import tempfile
import threading
import time
from datetime import datetime, timedelta, timezone

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID

SHARED = os.environ.get("SINKHOLE_SHARED", "/shared")
LOG_PATH = os.path.join(SHARED, "egress.jsonl")
CA_PATH = os.path.join(SHARED, "ca.pem")
READY_PATH = os.path.join(SHARED, "ready")

LISTEN_PORT = int(os.environ.get("SINKHOLE_PORT", "8888"))
SELF_IP = os.environ.get("SINKHOLE_IP", "10.77.0.2")
SO_ORIGINAL_DST = 80
BODY_CAP = 16384          # enough to hold a harvested file; long bodies are truncated and counted

_log_lock = threading.Lock()
_started = time.time()


def record(**fields):
    """One event per line. Ordering is by elapsed seconds, so no wall-clock leaves the container."""
    fields = {"at": round(time.time() - _started, 3), **fields}
    line = json.dumps(fields, ensure_ascii=False)
    with _log_lock:
        with open(LOG_PATH, "a") as fh:
            fh.write(line + "\n")
        print(line, flush=True)


# --------------------------------------------------------------------------- certificates

def _make_ca():
    key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "sinkhole observation CA")])
    now = datetime.now(timezone.utc)
    cert = (
        x509.CertificateBuilder()
        .subject_name(name)
        .issuer_name(name)
        .public_key(key.public_key())
        .serial_number(x509.random_serial_number())
        .not_valid_before(now - timedelta(days=1))
        .not_valid_after(now + timedelta(days=825))
        .add_extension(x509.BasicConstraints(ca=True, path_length=None), critical=True)
        .sign(key, hashes.SHA256())
    )
    return key, cert


CA_KEY, CA_CERT = _make_ca()
LEAF_KEY = rsa.generate_private_key(public_exponent=65537, key_size=2048)
_CERT_DIR = tempfile.mkdtemp(prefix="sinkhole-certs-")
_contexts = {}
_ctx_lock = threading.Lock()


def _context_for(host):
    """A certificate for whatever name was asked for, minted on demand and cached."""
    with _ctx_lock:
        if host in _contexts:
            return _contexts[host]

        now = datetime.now(timezone.utc)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, host[:64])])
        try:
            san = x509.SubjectAlternativeName([x509.DNSName(host)])
        except ValueError:
            san = x509.SubjectAlternativeName([x509.DNSName("unknown.invalid")])
        leaf = (
            x509.CertificateBuilder()
            .subject_name(subject)
            .issuer_name(CA_CERT.subject)
            .public_key(LEAF_KEY.public_key())
            .serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(days=1))
            .not_valid_after(now + timedelta(days=397))
            .add_extension(san, critical=False)
            .sign(CA_KEY, hashes.SHA256())
        )

        base = os.path.join(_CERT_DIR, f"{abs(hash(host))}")
        with open(base + ".crt", "wb") as fh:
            fh.write(leaf.public_bytes(serialization.Encoding.PEM))
            fh.write(CA_CERT.public_bytes(serialization.Encoding.PEM))
        with open(base + ".key", "wb") as fh:
            fh.write(LEAF_KEY.private_bytes(
                serialization.Encoding.PEM,
                serialization.PrivateFormat.TraditionalOpenSSL,
                serialization.NoEncryption()))

        ctx = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        ctx.load_cert_chain(base + ".crt", base + ".key")
        _contexts[host] = ctx
        return ctx


def _publish_ca():
    with open(CA_PATH, "wb") as fh:
        fh.write(CA_CERT.public_bytes(serialization.Encoding.PEM))


# --------------------------------------------------------------------------- DNS

def _qname(packet, offset):
    labels, seen = [], 0
    while offset < len(packet) and seen < 64:
        length = packet[offset]
        if length == 0:
            offset += 1
            break
        offset += 1
        labels.append(packet[offset:offset + length].decode("utf-8", "replace"))
        offset += length
        seen += 1
    return ".".join(labels), offset


def serve_dns():
    """Answer every A query with this host. Everything the payload dials therefore comes to us.

    The query itself is a finding on its own: DNS exfiltration hides its payload in the name, so a
    lookup for `<base32-of-a-secret>.attacker.example` never needs the connection to succeed.
    """
    sock = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", 53))
    while True:
        try:
            packet, peer = sock.recvfrom(4096)
            if len(packet) < 12:
                continue
            name, end = _qname(packet, 12)
            qtype = struct.unpack("!H", packet[end:end + 2])[0] if len(packet) >= end + 2 else 1
            record(kind="dns", name=name, qtype=qtype)

            header = packet[:2] + b"\x81\x80" + b"\x00\x01"
            question = packet[12:end + 4]
            if qtype == 1:                                   # A — point it at ourselves
                answer = (b"\xc0\x0c\x00\x01\x00\x01\x00\x00\x00\x1e\x00\x04"
                          + socket.inet_aton(SELF_IP))
                header += b"\x00\x01"
            else:                                            # anything else: no records, no error
                answer = b""
                header += b"\x00\x00"
            sock.sendto(header + b"\x00\x00\x00\x00" + question + answer, peer)
        except Exception as exc:                             # a recorder must not die on bad input
            record(kind="error", where="dns", detail=repr(exc)[:200])


# --------------------------------------------------------------------------- TCP

def _original_port(conn):
    """The port the payload actually dialled, before iptables redirected it here."""
    try:
        raw = conn.getsockopt(socket.SOL_IP, SO_ORIGINAL_DST, 16)
        port, addr = struct.unpack("!H4s", raw[2:8])
        return port, socket.inet_ntoa(addr)
    except OSError:
        return None, None


HTTP_METHODS = (b"GET", b"POST", b"PUT", b"HEAD", b"DELETE", b"PATCH", b"OPTIONS", b"CONNECT",
                b"TRACE", b"PROPFIND")


def _looks_like_http(peeked):
    """A method token followed by a space. Anything else is somebody's own protocol."""
    return any(peeked.startswith(m + b" ") for m in HTTP_METHODS)


def _read_http(stream, port, host_hint, tls):
    """Parse one request far enough to say where it was going and what it carried."""
    stream.settimeout(10)
    buf = b""
    while b"\r\n\r\n" not in buf and len(buf) < 65536:
        chunk = stream.recv(4096)
        if not chunk:
            break
        buf += chunk

    if not buf:
        return None

    head, _, rest = buf.partition(b"\r\n\r\n")
    lines = head.decode("utf-8", "replace").split("\r\n")
    request_line = lines[0] if lines else ""
    headers = {}
    for line in lines[1:]:
        key, sep, value = line.partition(":")
        if sep:
            headers[key.strip().lower()] = value.strip()

    body = rest
    try:
        want = int(headers.get("content-length", "0"))
    except ValueError:
        want = 0
    while len(body) < min(want, BODY_CAP):
        chunk = stream.recv(4096)
        if not chunk:
            break
        body += chunk

    parts = request_line.split()
    method = parts[0] if parts else ""
    path = parts[1] if len(parts) > 1 else ""
    host = headers.get("host") or host_hint or ""

    record(
        kind="http",
        tls=tls,
        port=port,
        method=method,
        host=host,
        path=path,
        # The destination as the payload wrote it, which is the form worth reading in a report.
        target=f"{'https' if tls else 'http'}://{host}{path}",
        headers=headers,
        body=body[:BODY_CAP].decode("utf-8", "replace"),
        body_bytes=len(body),
        truncated=len(body) > BODY_CAP,
    )

    payload = b'{"ok":true,"id":"1"}'
    stream.sendall(
        b"HTTP/1.1 200 OK\r\nContent-Type: application/json\r\n"
        b"Content-Length: " + str(len(payload)).encode() + b"\r\n"
        b"Connection: close\r\n\r\n" + payload)
    return True


def _read_raw(conn, port, note):
    """Not HTTP. Log whatever arrives — a shell, an SMTP conversation, a bespoke protocol."""
    conn.settimeout(5)
    buf = b""
    try:
        while len(buf) < BODY_CAP:
            chunk = conn.recv(4096)
            if not chunk:
                break
            buf += chunk
    except (socket.timeout, OSError):
        pass
    record(kind="raw", port=port, note=note, bytes=len(buf),
           data=buf[:BODY_CAP].decode("utf-8", "replace"))


def handle(conn, peer):
    port, addr = _original_port(conn)
    try:
        conn.settimeout(10)
        try:
            first = conn.recv(24, socket.MSG_PEEK)
        except (socket.timeout, OSError):
            first = b""

        if not first:
            record(kind="connect", port=port, address=addr, note="opened and sent nothing")
            return

        # A TLS ClientHello is a handshake record (0x16) followed by a version major of 3.
        if first[0] == 0x16 and len(first) > 1 and first[1] == 0x03:
            seen = {}

            def sni(sslsock, name, _ctx):
                seen["host"] = name
                sslsock.context = _context_for(name or "unknown.invalid")

            ctx = _context_for("unknown.invalid")
            ctx.sni_callback = sni
            try:
                stream = ctx.wrap_socket(conn, server_side=True)
            except ssl.SSLError as exc:
                # The client refused our certificate. That is itself the finding: something tried
                # to reach `seen['host']` over TLS and would not accept an unknown issuer.
                record(kind="tls-refused", port=port, address=addr,
                       host=seen.get("host"), detail=str(exc)[:200])
                return
            host = seen.get("host") or addr
            if _read_http(stream, port, host, tls=True) is None:
                record(kind="tls", port=port, host=host, note="handshake only, no request")
            return

        # Require an actual method token. "starts with a letter" is not enough — it swallows any
        # text protocol, and the raw bytes of a reverse shell are exactly what we most want to see
        # unmangled by an HTTP parser.
        if _looks_like_http(first):
            if _read_http(conn, port, addr, tls=False) is not None:
                return
        _read_raw(conn, port, note=f"dialled {addr}")
    except Exception as exc:
        record(kind="error", where="tcp", port=port, detail=repr(exc)[:200])
    finally:
        try:
            conn.close()
        except OSError:
            pass


def serve_tcp():
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    sock.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
    sock.bind(("0.0.0.0", LISTEN_PORT))
    sock.listen(128)
    while True:
        conn, peer = sock.accept()
        threading.Thread(target=handle, args=(conn, peer), daemon=True).start()


def main():
    open(LOG_PATH, "a").close()
    _publish_ca()
    threading.Thread(target=serve_dns, daemon=True).start()
    threading.Thread(target=serve_tcp, daemon=True).start()
    with open(READY_PATH, "w") as fh:
        fh.write("ready\n")
    record(kind="start", listening=LISTEN_PORT, self_ip=SELF_IP)
    try:
        while True:
            time.sleep(3600)
    except KeyboardInterrupt:
        pass


if __name__ == "__main__":
    sys.exit(main())
