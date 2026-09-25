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
        "name": "list_dependency_skills",
        "description": (
            "List which of this project's own dependencies ship a skill written by the library's authors: "
            "guidance on how the library is meant to be used and what goes wrong. Call this before writing, "
            "changing or fixing code that uses a dependency, even when the API looks familiar, then read the "
            "skill for the library the code uses with get_dependency_skill. Each entry carries the skill's own "
            "description, to decide which one applies."),
        "inputSchema": {"type": "object", "properties": {}, "additionalProperties": False},
    },
    {
        "name": "get_dependency_skill",
        "description": (
            "Read the skill a dependency ships, as its authors wrote it, for the version this project "
            "resolved. Only this project's own dependencies are answered."),
        "inputSchema": {
            "type": "object",
            "properties": {"library": {"type": "string", "description": "group:artifact, e.g. com.example:acme-text"}},
            "required": ["library"],
            "additionalProperties": False,
        },
    },
    {
        "name": "find_library",
        "description": (
            "Search the libraries already downloaded on this machine for one that does what you need — before "
            "writing something a library might already do, such as formatting, parsing or validation. Answers "
            "with each library's coordinate and what it says it is for, marked as a dependency of this project "
            "or not. Adding one is the developer's decision: propose it."),
        "inputSchema": {
            "type": "object",
            "properties": {"need": {"type": "string", "description": "what the code needs, in plain words, e.g. locale-aware date formatting"}},
            "required": ["need"],
            "additionalProperties": False,
        },
    },
    {
        "name": "get_dependency_skill_file",
        "description": (
            "Read one of a dependency skill's other files — a reference under references/ or a file under "
            "assets/ — by the relative path the skill links to it by."),
        "inputSchema": {
            "type": "object",
            "properties": {
                "library": {"type": "string", "description": "group:artifact"},
                "path": {"type": "string", "description": "the path within the skill, e.g. references/swift.md"},
            },
            "required": ["library", "path"],
            "additionalProperties": False,
        },
    },
]


def call(store, name, arguments):
    """The text a tool answers with, or None for an unknown tool or bad arguments."""
    project = refresh(store, os.getcwd())
    if name == "list_dependency_skills":
        return list_skills(store, project)
    if name == "get_dependency_skill" and isinstance(arguments.get("library"), str):
        return get_skill(store, project, arguments["library"].strip())
    if name == "find_library" and isinstance(arguments.get("need"), str):
        return find(store, project, arguments["need"].strip())
    if name == "get_dependency_skill_file" and isinstance(arguments.get("library"), str) \
            and isinstance(arguments.get("path"), str):
        return get_file(store, project, arguments["library"].strip(), arguments["path"])
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
                          "serverInfo": {"name": "dependency-skills", "version": __version__}}
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
            print(f"dependency-skills: {method} failed: {problem!r}", file=sys.stderr)
            send({"jsonrpc": "2.0", "id": request_id, "error": {"code": -32603, "message": str(problem)}})
