import json
import os
import subprocess
import sys
from pathlib import Path

from dependencyskills_lightweight.analytics import prompt_hook
from support import Machine

SOURCE = str(Path(__file__).resolve().parent.parent / "src")


class ServerTest(Machine):

    def test_answers_over_stdio_and_writes_nothing_else_to_stdout(self):
        self.publish("com.acme:acme-text:1.0")
        self.build(["com.acme:acme-text:1.0"])
        requests = [
            {"jsonrpc": "2.0", "id": 1, "method": "initialize", "params": {"protocolVersion": "2025-06-18"}},
            {"jsonrpc": "2.0", "method": "notifications/initialized"},
            {"jsonrpc": "2.0", "id": 2, "method": "tools/list"},
            {"jsonrpc": "2.0", "id": 3, "method": "tools/call",
             "params": {"name": "get_dependency_skill", "arguments": {"library": "com.acme:acme-text"}}},
            {"jsonrpc": "2.0", "id": 4, "method": "tools/call", "params": {"name": "no_such_tool", "arguments": {}}},
        ]
        done = subprocess.run([sys.executable, "-m", "dependencyskills_lightweight", "mcp"], cwd=self.project,
                              input="".join(json.dumps(r) + "\n" for r in requests), capture_output=True, text=True,
                              env={**os.environ, "PYTHONPATH": SOURCE}, timeout=60)
        answers = {m["id"]: m for m in map(json.loads, done.stdout.splitlines())}   # every line is protocol

        self.assertEqual({1, 2, 3, 4}, set(answers))
        self.assertEqual(4, len(answers[2]["result"]["tools"]))
        self.assertIn("Call Acme.normalize.", answers[3]["result"]["content"][0]["text"])
        self.assertTrue(answers[4]["result"]["isError"])


class HookTest(Machine):

    def test_counts_every_message_and_logs_a_correction_with_its_excerpt(self):
        prompt_hook(self.store, json.dumps({"session_id": "s1", "prompt": "please add a chart"}))
        prompt_hook(self.store, json.dumps({"session_id": "s1", "prompt": "No, that's the old api — use the library instead of JS."}))
        prompt_hook(self.store, "not json")
        events = [json.loads(line) for line in (self.temp / "log.jsonl").read_text().splitlines()]

        self.assertEqual(2, sum(e["event"] == "prompt" for e in events))
        corrections = [e for e in events if e["event"] == "correction"]
        self.assertEqual(1, len(corrections))
        self.assertEqual("s1", corrections[0]["session"])
