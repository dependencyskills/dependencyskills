"""An MCP server over stdio: newline-delimited JSON-RPC 2.0 on stdin and stdout.

The harness starts it in the project and stops it when the session ends, so there is no daemon.
Stdout carries protocol messages and nothing else — a stray print would corrupt the stream — so
everything else goes to stderr, which harnesses keep as a log.
"""

import json
import os
import sys

from . import __version__
from .find import find
from .lookup import get_file, get_skill, list_skills
from .project import refresh
from .store import Store

TOOLS = [
    {
        "name": "list_guides",
        "description": (
            "List this project's libraries whose authors ship a guide: how the library is meant to be used, "
            "what it is not for, and what goes wrong. Call it before writing, changing or fixing code that uses "
            "a library, even when the API looks familiar, and again after adding a dependency and building. "
            "Each entry says in one line what the library is for."),
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "read_guide",
        "description": (
            "Read a library's guide, as its authors wrote it, for the version this project resolved — or, with "
            "`file`, one of the files the guide links to, such as references/swift.md. Only this project's own "
            "libraries are answered."),
        "inputSchema": {
            "type": "object",
            "properties": {
                "library": {"type": "string", "description": "group:artifact, e.g. com.example:acme-text"},
                "file": {"type": "string", "description": "optional: a file the guide links to, e.g. references/swift.md"},
            },
            "required": ["library"],
            "additionalProperties": False,
        },
    },
    {
        "name": "search_libraries",
        "description": (
            "Before writing something a library might already do — formatting, parsing, validation, dates, "
            "retry — describe the need in plain words. Answers with the libraries that match, this project's "
            "own and others already on this machine, each marked, with what each says it is for. Adding a "
            "library is the developer's decision: propose it."),
        "inputSchema": {
            "type": "object",
            "properties": {"need": {"type": "string", "description": "what the code needs, in plain words, e.g. locale-aware date formatting"}},
            "required": ["need"],
            "additionalProperties": False,
        },
    },
]


def call(store, name, arguments):
    """The text a tool answers with, or None for an unknown tool or bad arguments."""
    project = refresh(store, os.getcwd())
    if name == "list_guides":
        return list_skills(store, project)
    if name == "read_guide" and isinstance(arguments.get("library"), str):
        library, file = arguments["library"].strip(), arguments.get("file")
        if isinstance(file, str) and file.strip():
            return get_file(store, project, library, file)
        return get_skill(store, project, library)
    if name == "search_libraries" and isinstance(arguments.get("need"), str):
        return find(store, project, arguments["need"].strip())
    return None


def serve():
    protocol = sys.stdout
    sys.stdout = sys.stderr
    store = Store()

    def send(message):
        try:
            protocol.write(json.dumps(message) + "\n")
            protocol.flush()
        except (BrokenPipeError, OSError):
            os._exit(0)   # the client has gone — an ordinary end of a session

    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        try:
            request = json.loads(line)
        except ValueError:
            send({"jsonrpc": "2.0", "id": None, "error": {"code": -32700, "message": "parse error"}})
            continue
        method, request_id = request.get("method"), request.get("id")
        if request_id is None:
            continue   # a notification wants no answer
        params = request.get("params") or {}
        try:
            if method == "initialize":
                result = {"protocolVersion": params.get("protocolVersion", "2025-06-18"),
                          "capabilities": {"tools": {}},
                          "serverInfo": {"name": "librarian", "version": __version__}}
            elif method == "ping":
                result = {}
            elif method == "tools/list":
                result = {"tools": TOOLS}
            elif method == "tools/call":
                text = call(store, params.get("name"), params.get("arguments") or {})
                result = {"content": [{"type": "text", "text": text if text is not None
                                       else f"unknown tool or arguments: {params.get('name')}"}],
                          "isError": text is None}
            else:
                send({"jsonrpc": "2.0", "id": request_id, "error": {"code": -32601, "message": f"no method {method}"}})
                continue
            send({"jsonrpc": "2.0", "id": request_id, "result": result})
        except Exception as problem:   # one bad call must not end the session
            print(f"librarian: {method} failed: {problem!r}", file=sys.stderr)
            send({"jsonrpc": "2.0", "id": request_id, "error": {"code": -32603, "message": str(problem)}})
