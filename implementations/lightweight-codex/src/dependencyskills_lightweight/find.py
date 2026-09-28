"""Searching the libraries already on this machine for one that does what the agent needs (RAD-0078).

An agent that needed a date formatter wrote its own while one sat in the local cache, because it
could see only what the project had resolved. This searches every cached sources jar by what each
library says it is for — its skill's frontmatter, or its POM's description — and never serves a
skill's body: a library the project did not choose may describe itself, and may not instruct. Its
full skill is served once the developer adds it and the project is built.
"""

import json
import math
import re
import time
import zipfile
from pathlib import Path

from . import caches, npm
from .index import MAX_FILE, REPUBLISHER_GROUPS, SERVED_DIRS, SKILL_ENTRY, stamp_of
from .names import ecosystem, library as library_of, own_name, skill_name, version as version_of
from .project import scope_of
from .skillfile import SHOWN_FIELDS, check, frontmatter

RESCAN = 300          # seconds; the caches change when something downloads, not between tool calls
LIMIT = 8
RELEVANCE = 0.4       # a match must score this fraction of the best to be shown
SKILL_BONUS = 1.5     # a library that ships a skill counts for more, without outranking the right answer
STOP = {"the", "and", "for", "with", "that", "this", "from", "into", "library", "libraries", "use", "using", "kotlin"}


def describe(coordinate, jar, version_dir):
    """(description, frontmatter JSON or None) for one cached sources jar, reading no skill body.

    A skill counts only if it is filed under the jar's own coordinate and is valid, exactly as when it
    is indexed; its shown fields are kept and its body never is. A library without one is described
    by its POM.
    """
    own = skill_name(*library_of(coordinate).split(":"))
    text = None
    try:
        with zipfile.ZipFile(jar) as archive:
            for entry in archive.namelist():
                filed = SKILL_ENTRY.match(entry)
                if filed and filed.group(1) == own and filed.group(2) == "SKILL.md":
                    text = archive.read(entry).decode("utf-8", "replace")
                    break
    except (zipfile.BadZipFile, OSError, KeyError):
        pass
    if text:
        fields, _ = frontmatter(text)
        if fields and not check(fields, own)[0]:
            shown = {k: fields[k] for k in SHOWN_FIELDS if k in fields}
            return str(fields["description"]).strip(), json.dumps(shown)
    artifact = coordinate.split(":")[1]
    return caches.pom_description(version_dir, artifact, version_of(coordinate)), None


def describe_package(coordinate, directory):
    """(description, frontmatter JSON or None) for an installed package: its own guide's shown fields, else
    the first valid guide it ships under another name, else its package.json description. Never a body."""
    own = own_name(library_of(coordinate))
    skills = npm.skills_in(directory, SERVED_DIRS, MAX_FILE)
    for name in [own] + sorted(n for n in skills if n != own):
        text = skills.get(name, {}).get("text")
        if text:
            fields, _ = frontmatter(text)
            if fields and not check(fields, name)[0]:
                return str(fields["description"]).strip(), json.dumps({k: fields[k] for k in SHOWN_FIELDS if k in fields})
    return npm.description(directory), None


def scan(store):
    """Bring the table of cached libraries up to date: only changed jars are opened, and the walk
    itself is skipped for RESCAN seconds."""
    last = store.setting("cache_scan")
    if last and time.time() - float(last) < RESCAN:
        return
    known = dict(store.execute("SELECT carrier, stamp FROM cached"))
    seen = set()
    for coordinate, version_dir, jar in caches.discover():
        seen.add(coordinate)
        stamp = stamp_of(jar)
        if known.get(coordinate) == stamp:
            continue
        description, shown = describe(coordinate, jar, version_dir)
        store.execute("INSERT OR REPLACE INTO cached (carrier, library, description, frontmatter, stamp)"
                      " VALUES (?, ?, ?, ?, ?)", (coordinate, library_of(coordinate), description, shown, stamp))
    store.executemany("DELETE FROM cached WHERE carrier = ?", [(c,) for c in set(known) - seen])
    store.commit()
    store.set_setting("cache_scan", str(time.time()))


def stem(word):
    """A crude English stem, enough that "dates" meets "date" and "formatting" meets "format"."""
    for suffix in ("ing", "ed"):
        if word.endswith(suffix) and len(word) - len(suffix) >= 3:
            word = word[: -len(suffix)]
            if len(word) > 3 and word[-1] == word[-2] and word[-1] not in "aeioulsz":
                word = word[:-1]   # "formatting" is "format", not "formatt"
            return word
    if word.endswith(("ches", "shes", "sses", "xes", "zes")):
        return word[:-2]
    if word.endswith("s") and not word.endswith("ss") and len(word) > 3:
        return word[:-1]
    return word


def _terms(text):
    return {stem(w) for w in re.findall(r"[a-z0-9]+", text.lower())}


def find(store, project, need):
    """The cached libraries that best match `need`, with what each says it is for."""
    started = time.monotonic()
    scan(store)
    _, allowed = scope_of(store, project) if project else (None, None)
    in_scope = {lib for lib, _ in allowed or ()}
    words = {stem(w) for w in re.findall(r"[a-z0-9]+", need.lower()) if len(w) > 2 and w not in STOP}
    libraries = {}
    rows = list(store.execute("SELECT carrier, library, description, frontmatter FROM cached ORDER BY carrier"))
    # An npm project's node_modules is what is on this machine for it: every installed package, declared or not.
    if project and npm.is_project(Path(project)):
        for coordinate, directory in sorted(npm.everything_installed(Path(project)).items()):
            rows.append((coordinate, library_of(coordinate), *describe_package(coordinate, directory)))
    for carrier, library, description, shown in rows:
        entry = libraries.setdefault(library, {"versions": set(), "description": "", "shown": None})
        entry["versions"].add(version_of(carrier))
        if shown or not entry["description"]:
            entry["description"], entry["shown"] = description or entry["description"], shown or entry["shown"]
    terms = {library: _terms(f"{library} {entry['description']}") for library, entry in libraries.items()}
    # Rarer words count for more, and every word counts for something — a word every cached library
    # shares must not weigh zero, or a machine with few libraries matches nothing. A word that begins
    # one in the description counts half ("date", "datetime").
    weight = {w: math.log(1 + (len(terms) + 1) / (1 + sum(w in t for t in terms.values()))) for w in words}
    scored = []
    for library, entry in libraries.items():
        score = sum(weight[w] if w in terms[library] else
                    weight[w] / 2 if any(t.startswith(w) for t in terms[library]) else 0 for w in words)
        if score > 0:
            scored.append((round(score * (SKILL_BONUS if entry["shown"] else 1), 3), library))
    best = max((s for s, _ in scored), default=0)
    found = [lib for s, lib in sorted(scored, reverse=True) if s >= best * RELEVANCE][:LIMIT]
    store.log("query", command="find", asked=need, result="hit" if found else "none", project=project,
              libraries=found, ms=round((time.monotonic() - started) * 1000, 1))
    if not found:
        return (f"Nothing on this machine matches \"{need}\". This searches only libraries some build here "
                "has already downloaded; it does not search a registry.")
    lines = [f"Libraries on this machine matching \"{need}\". Each description is the library's own words "
             "about itself. One that is not a dependency of this project is the developer's decision to add: "
             "propose it, with your reason, rather than adding it yourself. Once it is added and the project "
             "is built, read_guide serves its full guide.", ""]
    for library in found:
        entry = libraries[library]
        versions = ", ".join(sorted(entry["versions"]))
        standing = "a dependency of this project — read its guide with read_guide" \
            if library in in_scope else "not a dependency of this project"
        if library.split(":")[0] in REPUBLISHER_GROUPS:
            standing += "; republishes other projects' guides — not the library's own words"
        if entry["shown"]:
            lines.append(f"- {library} ({versions}) — ships a guide; {standing}")
            for key, value in json.loads(entry["shown"]).items():
                value = ", ".join(f"{k}={v}" for k, v in value.items()) if isinstance(value, dict) \
                    else " ".join(str(value).split())
                lines.append(f"  {key}: {value}")
        else:
            lines.append(f"- {library} ({versions}) — no guide; {standing}")
            if entry["description"]:
                source = "its POM" if ecosystem(library) == "maven" else "its package description"
                lines.append(f"  {source}: {entry['description']}")
    return "\n".join(lines)
