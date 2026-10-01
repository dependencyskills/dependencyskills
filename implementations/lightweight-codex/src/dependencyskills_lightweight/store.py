"""The machine's one store: what was indexed, what each project may read, and settings."""

import json
import os
import sqlite3
from datetime import datetime, timezone
from pathlib import Path

SCHEMA = """
CREATE TABLE IF NOT EXISTS skill (
  carrier TEXT, library TEXT, path TEXT, text TEXT, description TEXT, body TEXT, problems TEXT,
  UNIQUE (carrier, path)
);
CREATE TABLE IF NOT EXISTS skill_file (carrier TEXT, path TEXT, content TEXT, UNIQUE (carrier, path));
CREATE TABLE IF NOT EXISTS indexed (carrier TEXT PRIMARY KEY, outcome TEXT, stamp TEXT);
CREATE TABLE IF NOT EXISTS scope (project TEXT, carrier TEXT, UNIQUE (project, carrier));
CREATE TABLE IF NOT EXISTS declared (project TEXT, carrier TEXT, UNIQUE (project, carrier));
CREATE TABLE IF NOT EXISTS cached (
  carrier TEXT PRIMARY KEY, library TEXT, description TEXT, frontmatter TEXT, stamp TEXT
);
CREATE TABLE IF NOT EXISTS setting (key TEXT PRIMARY KEY, value TEXT);
"""


def home():
    """Where the store and the log live: `DEPENDENCYSKILLS_CODEX_DIR`, or `~/.dscodex`.

    The full codex's directory and its override, so a machine has one home for both: `skills.db` here
    beside the full codex's `codex.db` and `usage.db`, which that service can read as well (ADR-0012).
    """
    return Path(os.environ.get("DEPENDENCYSKILLS_CODEX_DIR") or Path.home() / ".dscodex")


class Store:
    """One SQLite file, shared by every agent session on the machine.

    Every session starts its own server, and each may index on its first call, so several processes
    write at once: WAL lets readers carry on while one writes, and the busy timeout makes a second
    writer wait rather than fail — which inside a tool call would be an agent told its dependencies
    have no skills.
    """

    def __init__(self, directory=None):
        self.directory = Path(directory) if directory else home()
        self.directory.mkdir(parents=True, exist_ok=True)
        self.db = sqlite3.connect(self.directory / "skills.db", timeout=30)
        self.db.execute("PRAGMA journal_mode=WAL")
        self.db.execute("PRAGMA busy_timeout=30000")
        self.db.executescript(SCHEMA)

    def execute(self, sql, parameters=()):
        return self.db.execute(sql, parameters)

    def executemany(self, sql, rows):
        return self.db.executemany(sql, rows)

    def commit(self):
        self.db.commit()

    def setting(self, key):
        row = self.db.execute("SELECT value FROM setting WHERE key = ?", (key,)).fetchone()
        return row[0] if row else None

    def set_setting(self, key, value):
        if value is None:
            self.db.execute("DELETE FROM setting WHERE key = ?", (key,))
        else:
            self.db.execute("INSERT OR REPLACE INTO setting (key, value) VALUES (?, ?)", (key, value))
        self.db.commit()

    def log_path(self):
        """Where analytics go, or None when logging is off — the default. `DEPENDENCYSKILLS_LOG` overrides."""
        if os.environ.get("DEPENDENCYSKILLS_LOG"):
            return Path(os.environ["DEPENDENCYSKILLS_LOG"])
        value = self.setting("log")
        return Path(value) if value else None

    def log(self, event, **fields):
        """Append one event as a JSON line. Never raises: a query must not fail because its log did.

        The agent's session id is recorded when the harness passes one, so a read can be matched with
        whatever else happened in that session.
        """
        path = self.log_path()
        if not path:
            return
        record = {"at": datetime.now(timezone.utc).isoformat(timespec="milliseconds"), "event": event,
                  "session": os.environ.get("CLAUDE_CODE_SESSION_ID"), "cwd": os.getcwd(), **fields}
        try:
            path.parent.mkdir(parents=True, exist_ok=True)
            with open(path, "a", encoding="utf-8") as out:
                out.write(json.dumps(record) + "\n")
        except OSError:
            pass
