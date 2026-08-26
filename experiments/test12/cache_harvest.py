#!/usr/bin/env python3
"""
test12 — build a WIDE prose corpus from the local Gradle module cache.

WHY THE CACHE. Every corpus this project has measured against came from 59 coordinates published
by **three** organisations. RAD-0036 recorded why that is a problem: three publishers is not a
definition of "normal library documentation", it is one house style. Documentation voice - length,
whether preconditions are stated at all, how imperative a comment is willing to be - is set by a
team's review culture and does not converge the way linted identifier form does.

A developer's Gradle cache is the fix, and it is already on disk. It is everything they have ever
built against: hundreds of publishers, real transitive tails, multiple ecosystems, and - crucially
- `-sources.jar` alongside most of it, because IDE source-download has been fetching them for
years.

WHAT THIS IS NOT. This produces a **prose** corpus, not an entry corpus. It deliberately does not
bind documentation to resolved symbols the way `test5/harvest.py` does, because the question here
is what real documentation *sounds like*, not what it documents. That also means no tree-sitter and
no network: doc comments are `/** ... */` in both Kotlin and Java, so a scanner is enough, and it
picks up Java sources that the Kotlin-only path skipped.

PRIVACY IS A HARD REQUIREMENT, NOT A SETTING. A real cache contains the developer's own private
packages and possibly a client's. Those must never reach a committed file. Two defences:

  * groups listed in `private-groups.txt` (gitignored) are dropped outright;
  * the output corpus is gitignored, and only the manifest is publishable - after review.

The harvester refuses to write a manifest if the exclusion file is missing, because a filter that
silently does nothing is the same failure as a scan that matches no files.

Run:  python3 cache_harvest.py            # harvest, report, write corpus + manifest
      python3 cache_harvest.py --dry-run  # counts only, writes nothing
"""
import json
import os
import re
import sys
import zipfile
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
PRIVATE_FILE = os.path.join(HERE, "private-groups.txt")
OUT = os.path.join(HERE, "prose-corpus.jsonl")
MANIFEST = os.path.join(HERE, "PROSE-MANIFEST.md")

# A doc comment, and the tags we strip so what remains is prose rather than structure.
DOC = re.compile(rb'/\*\*(.*?)\*/', re.S)
STAR = re.compile(r'^\s*\*ā?', re.M)
TAG_ONLY = re.compile(r'^\s*@\w+')
CODE_FENCE = re.compile(r'```.*?```', re.S)
MIN_WORDS = 6                      # below this it is a label, not documentation


def load_private():
    """Group prefixes to drop. Absent file is an error, not a default-allow."""
    if not os.path.exists(PRIVATE_FILE):
        return None
    out = []
    for line in open(PRIVATE_FILE):
        line = line.split("#", 1)[0].strip()
        if line:
            out.append(line)
    return out


def clean(raw):
    """A doc comment body reduced to the prose a reader would actually read."""
    text = raw.decode("utf-8", "replace")
    text = STAR.sub("", text)
    text = CODE_FENCE.sub(" ", text)
    lines = []
    for line in text.splitlines():
        s = line.strip()
        if not s or TAG_ONLY.match(s):     # @param/@return/@throws are structure, not voice
            continue
        lines.append(s)
    out = " ".join(lines)
    out = re.sub(r'\s+', ' ', out).strip()
    return out


def _has_sources(version_dir):
    """Does this version carry a -sources.jar? Checked before selecting it, not after."""
    for h in os.listdir(version_dir):
        hp = os.path.join(version_dir, h)
        if os.path.isdir(hp) and any(f.endswith("-sources.jar") for f in os.listdir(hp)):
            return True
    return False


def walk_cache(private, limit_per_artifact=1):
    """Newest version per artifact by default — old versions are near-duplicate prose."""
    if not os.path.isdir(CACHE):
        sys.exit(f"no Gradle cache at {CACHE}")
    seen_docs = set()
    stats = Counter()
    for group in sorted(os.listdir(CACHE)):
        gp = os.path.join(CACHE, group)
        if not os.path.isdir(gp):
            continue
        if any(group == p or group.startswith(p + ".") for p in private):
            stats["groups_excluded"] += 1
            continue
        stats["groups_kept"] += 1
        for artifact in sorted(os.listdir(gp)):
            ap = os.path.join(gp, artifact)
            if not os.path.isdir(ap):
                continue
            # Newest versions that actually HAVE a sources jar. Taking simply the newest silently
            # dropped 42 artifacts whose latest release ships none — androidx.compose.ui among them.
            ordered = sorted((v for v in os.listdir(ap) if os.path.isdir(os.path.join(ap, v))),
                             reverse=True)
            versions = [v for v in ordered if _has_sources(os.path.join(ap, v))][:limit_per_artifact]
            for version in versions:
                vp = os.path.join(ap, version)
                jar = None
                for h in os.listdir(vp):
                    hp = os.path.join(vp, h)
                    if not os.path.isdir(hp):
                        continue
                    for f in os.listdir(hp):
                        if f.endswith("-sources.jar"):
                            jar = os.path.join(hp, f)
                            break
                    if jar:
                        break
                if not jar:
                    continue
                stats["jars"] += 1
                try:
                    zf = zipfile.ZipFile(jar)
                except (zipfile.BadZipFile, OSError):
                    stats["jars_unreadable"] += 1
                    continue
                with zf:
                    for name in zf.namelist():
                        lang = ("kotlin" if name.endswith(".kt")
                                else "java" if name.endswith(".java") else None)
                        if not lang:
                            continue
                        try:
                            src = zf.read(name)
                        except (zipfile.BadZipFile, OSError, RuntimeError):
                            continue
                        for m in DOC.finditer(src):
                            body = clean(m.group(1))
                            if len(body.split()) < MIN_WORDS:
                                stats["too_short"] += 1
                                continue
                            key = hash(body)
                            if key in seen_docs:            # the corpus is 63% duplicate (test5)
                                stats["duplicate"] += 1
                                continue
                            seen_docs.add(key)
                            stats["docs"] += 1
                            yield {
                                "doc": body,
                                "publisher": group,
                                "library": f"{group}:{artifact}:{version}",
                                "lang": lang,
                            }
    walk_cache.stats = stats


def main():
    dry = "--dry-run" in sys.argv
    private = load_private()
    if private is None:
        sys.exit(f"refusing to run: {os.path.basename(PRIVATE_FILE)} is missing.\n"
                 f"Create it with one group prefix per line for every publisher that must NOT be\n"
                 f"harvested (your own private packages, any client code). An empty file is a\n"
                 f"deliberate statement that nothing needs excluding; a missing one is an oversight.")

    rows = []
    for row in walk_cache(private):
        rows.append(row)
    st = walk_cache.stats

    pubs = Counter(r["publisher"] for r in rows)
    libs = {r["library"] for r in rows}
    langs = Counter(r["lang"] for r in rows)
    words = sum(len(r["doc"].split()) for r in rows)

    print(f"  publishers excluded : {st['groups_excluded']}")
    print(f"  publishers harvested: {st['groups_kept']}  ({len(pubs)} produced prose)")
    print(f"  sources jars read   : {st['jars']}  ({st['jars_unreadable']} unreadable)")
    print(f"  doc comments kept   : {st['docs']}")
    print(f"    dropped, too short: {st['too_short']}")
    print(f"    dropped, duplicate: {st['duplicate']}")
    print(f"  distinct libraries  : {len(libs)}")
    print(f"  languages           : {dict(langs)}")
    print(f"  total words of prose: {words:,}")
    print(f"\n  top publishers by doc count:")
    for p, n in pubs.most_common(8):
        print(f"    {n:>6}  {p}")

    if dry:
        print("\n  --dry-run: nothing written")
        return 0

    with open(OUT, "w") as fh:
        for r in rows:
            fh.write(json.dumps(r, ensure_ascii=False) + "\n")
    print(f"\n  wrote {OUT}")

    with open(MANIFEST, "w") as fh:
        fh.write("# Prose corpus manifest\n\n")
        fh.write("Built from a local Gradle module cache by `cache_harvest.py`. The corpus itself\n")
        fh.write("is gitignored — it is derived, large, and rebuildable. This records what it was\n")
        fh.write("built from, and exists so a later harvest has something to compare against\n")
        fh.write("(RAD-0036).\n\n")
        fh.write("Private publishers are excluded at harvest by `private-groups.txt`, which is\n")
        fh.write("also gitignored. Counts below are of what remained.\n\n")
        fh.write(f"- publishers: **{len(pubs)}**\n")
        fh.write(f"- libraries: **{len(libs)}**\n")
        fh.write(f"- doc comments: **{st['docs']}**\n")
        fh.write(f"- words of prose: **{words:,}**\n")
        fh.write(f"- languages: {dict(langs)}\n\n")
        fh.write("| doc comments | publisher |\n|---:|---|\n")
        for p, n in pubs.most_common():
            fh.write(f"| {n} | `{p}` |\n")
    print(f"  wrote {MANIFEST}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
