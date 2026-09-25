"""The lightweight codex: the skills a project's dependencies ship, served to its coding agent.

A build reports what the project resolved, as a CycloneDX SBOM in its build directory. This reads
that file, finds each library's skill in the local caches, and serves it over MCP to the agent the
harness started it for. Nothing runs between sessions, nothing is downloaded while an agent waits,
and a library the project did not choose may describe itself but never instruct.
"""

__version__ = "0.1.0a1"
