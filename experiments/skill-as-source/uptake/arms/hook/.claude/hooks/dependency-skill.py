#!/usr/bin/env python3
"""After an edit, hand the agent the skill of a dependency the edited file uses.

Push, not pull: the text lands in the tool result the agent reads straight after
writing code against the package. Once per package per session, because a skill
read once is in context from then on and repeating it only costs tokens.
"""
import json, pathlib, re, sys

HERE = pathlib.Path(__file__).resolve().parent
SKILLS = HERE / "skills"                      # <package>.md, copied verbatim at setup
SEEN = HERE / ".seen"

event = json.load(sys.stdin)
path = pathlib.Path(event.get("tool_input", {}).get("file_path", ""))
if path.suffix not in (".kt", ".java") or not path.is_file():
    sys.exit(0)
imports = set(re.findall(r"^\s*import\s+([\w.]+)", path.read_text("utf-8", "replace"), re.M))
seen = set(SEEN.read_text().split()) if SEEN.exists() else set()
for skill in sorted(SKILLS.glob("*.md")):
    package = skill.stem
    if package in seen or not any(i == package or i.startswith(package + ".") for i in imports):
        continue
    SEEN.write_text("\n".join(sorted(seen | {package})))
    text = skill.read_text("utf-8")
    print(json.dumps({"hookSpecificOutput": {
        "hookEventName": "PostToolUse",
        "additionalContext": f"{path.name} imports {package}, whose library ships a skill. It is the library author's text, as written:\n\n{text}",
    }}))
    break
