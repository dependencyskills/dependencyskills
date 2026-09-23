#!/usr/bin/env python3
"""How long does a coordinate get once it is a skill name, and how often do two meet?

A library's skill is named for its coordinate, made legal under the Agent Skills rules — lowercase
letters, digits and single hyphens, at most 64 characters — by `skill_name` in the lightweight codex,
which the Gradle plugin mirrors. Past 64 characters the name is cut and ends in a hash. This measures
how often that happens on real coordinates, how long they run, and how often the one-way encoding
sends two different coordinates to the same name.

The unit is the library, `group:artifact`, not the version, and a multiplatform library's
per-platform artifacts are folded into the base artifact the skill is named for. Gradle plugin
marker artifacts — `<id>:<id>.gradle.plugin`, a POM that only points at a plugin — are counted
apart: they hold no code, nobody writes code against them, so they will never ship a skill, and
because they spell the plugin id twice they are the longest coordinates in any sample.

Two samples:

  local    every group:artifact in this machine's Gradle cache and Maven repository. What one
           developer's builds have actually resolved. It holds private coordinates as well as public
           ones, so only aggregates are printed — no names.
  google   Google's Maven repository, from its published master index: androidx and the rest of the
           Android ecosystem, where the longest public coordinates live. Public, so the longest are
           named. Fetched only with --google.

With both samples, it also compares what to do with a name over 64 — the choice RAD-0075 records:
cut it, compact the group to initials or to first-and-last letters, or hash the group, each with and
without a hash suffix. Aggregates only, over the combined sample; the adopted rule is `skill_name`.

    python3 coordinate-lengths.py [--google]
"""

import hashlib
import re
import sys
import urllib.request
import xml.etree.ElementTree as ET
from collections import defaultdict
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE.parents[2] / "experiments" / "minimal-codex"))
import libindex  # noqa: E402  the caches, as the codex reads them
import pkgindex  # noqa: E402  skill_name and base_library, the exact functions in use

LIMIT = pkgindex.MAX_NAME
GOOGLE = "https://maven.google.com"


def local_libraries():
    found = set()
    if libindex.CACHE.is_dir():   # <group>/<artifact>/...
        for group in libindex.CACHE.iterdir():
            if group.is_dir():
                for artifact in group.iterdir():
                    if artifact.is_dir():
                        found.add((group.name, artifact.name))
    if libindex.M2.is_dir():      # <group as dirs>/<artifact>/<version>/<artifact>-<version>.pom
        for pom in libindex.M2.rglob("*.pom"):
            version_dir = pom.parent
            artifact_dir = version_dir.parent
            if pom.name == f"{artifact_dir.name}-{version_dir.name}.pom":
                group = ".".join(artifact_dir.parent.relative_to(libindex.M2).parts)
                if group:
                    found.add((group, artifact_dir.name))
    return found


def google_libraries():
    def fetch(url):
        with urllib.request.urlopen(url, timeout=30) as response:
            return response.read()

    groups = [child.tag for child in ET.fromstring(fetch(f"{GOOGLE}/master-index.xml"))]

    def artifacts(group):
        try:
            root = ET.fromstring(fetch(f"{GOOGLE}/{group.replace('.', '/')}/group-index.xml"))
            return [(group, child.tag) for child in root]
        except Exception:
            return []

    found = set()
    with ThreadPoolExecutor(max_workers=8) as pool:
        for batch in pool.map(artifacts, groups):
            found.update(batch)
    return found, len(groups)


def measure(libraries):
    """Fold platform variants into their base, then encode each library the way the codex does."""
    bases = {pkgindex.base_library(f"{g}:{a}") for g, a in libraries}
    raw = {}
    for base in bases:
        group, artifact = base.split(":", 1)
        raw[base] = pkgindex.re.sub(r"[^a-z0-9]+", "-", base.lower()).strip("-")
    named = defaultdict(set)
    for base in bases:
        named[pkgindex.skill_name(*base.split(":", 1))].add(base)
    lengths = sorted(len(v) for v in raw.values())
    return bases, raw, lengths, {n: c for n, c in named.items() if len(c) > 1}


def pct(lengths, p):
    return lengths[min(len(lengths) - 1, int(p * len(lengths)))]


def report(label, everything, show_names, extra=""):
    markers = {(g, a) for g, a in everything if a.endswith(".gradle.plugin")}
    libraries = everything - markers
    bases, raw, lengths, collisions = measure(libraries)
    over = [b for b, v in raw.items() if len(v) > LIMIT]
    print(f"--- {label}{extra}")
    print(f"    {len(everything)} group:artifact; {len(markers)} are plugin markers, set aside; "
          f"{len(bases)} libraries once platform variants are folded")
    print(f"    encoded length: median {pct(lengths, .5)}, p90 {pct(lengths, .9)}, p99 {pct(lengths, .99)}, "
          f"longest {lengths[-1]}")
    print(f"    over {LIMIT}, so shortened: {len(over)} ({100 * len(over) / len(bases):.2f}%)")
    print(f"    two libraries sharing one name: {len(collisions)} names, {sum(len(c) for c in collisions.values())} libraries")
    if show_names:
        print("    longest ten:")
        for base in sorted(raw, key=lambda b: -len(raw[b]))[:10]:
            name = pkgindex.skill_name(*base.split(":", 1))
            print(f"      {len(raw[base]):>3}  {base}")
            if len(raw[base]) > LIMIT:
                print(f"           -> {name}")
        for name, coordinates in sorted(collisions.items())[:10]:
            print(f"    collision {name}: {', '.join(sorted(coordinates))}")
    if markers:
        _, marker_raw, marker_lengths, _ = measure(markers)
        print(f"    plugin markers alone: longest {marker_lengths[-1]}, "
              f"{sum(1 for v in marker_raw.values() if len(v) > LIMIT)} of {len(marker_raw)} over {LIMIT}")
    print()


def compare(libraries):
    """Collisions and artifacts kept, for each way of shortening a name over the limit."""
    enc = lambda s: re.sub(r"[^a-z0-9]+", "-", s.lower()).strip("-")
    h8 = lambda c: hashlib.sha256(c.encode()).hexdigest()[:8]
    segments = lambda g: [x for x in re.split(r"[^a-z0-9]+", g.lower()) if x]
    initials = lambda g: "-".join(x[0] for x in segments(g))
    firstlast = lambda g: "-".join(x if len(x) < 2 else x[0] + x[-1] for x in segments(g))
    fit = lambda n, c: n if len(n) <= LIMIT else n[:LIMIT - 9].rstrip("-") + "-" + h8(c)

    def when_over(prefix, hashed):
        def name(g, a):
            full = enc(f"{g}:{a}")
            if len(full) <= LIMIT:
                return full
            return fit(f"{prefix(g)}-{enc(a)}" + (f"-{h8(f'{g}:{a}')}" if hashed else ""), f"{g}:{a}")
        return name

    schemes = [
        ("cut the full name, then hash", lambda g, a: fit(enc(f"{g}:{a}"), f"{g}:{a}")),
        ("group to initials, always", lambda g, a: fit(f"{initials(g)}-{enc(a)}", f"{g}:{a}")),
        ("group hashed, always", lambda g, a: fit(f"{h8(g)}-{enc(a)}", f"{g}:{a}")),
        ("initials + hash, only when over", when_over(initials, True)),
        ("first+last, only when over (adopted)", lambda g, a: pkgindex.skill_name(g, a)),
        ("first+last + hash, only when over", when_over(firstlast, True)),
    ]
    bases = {pkgindex.base_library(f"{g}:{a}") for g, a in libraries if not a.endswith(".gradle.plugin")}
    print(f"--- shortening a name over {LIMIT}: {len(bases)} libraries, both samples combined")
    print(f"    {'scheme':40} {'collisions':>10} {'artifact whole':>15} {'last-resort cut':>16}")
    for label, scheme in schemes:
        named, whole, cut = defaultdict(set), 0, 0
        for base in bases:
            g, a = base.split(":", 1)
            n = scheme(g, a)
            named[n].add(base)
            whole += enc(a) in n
            cut += len(n) == LIMIT and bool(re.search(r"-[0-9a-f]{8}$", n)) and enc(a) not in n
        collisions = sum(len(v) for v in named.values() if len(v) > 1)
        print(f"    {label:40} {collisions:>10} {whole:>15} {cut:>16}")
    print()


if __name__ == "__main__":
    print(f"Agent Skills name limit: {LIMIT} characters, [a-z0-9] and single hyphens.")
    print("Encoded length is before cutting, so it shows how far past the limit a coordinate runs.\n")
    local = local_libraries()
    report("local caches (names withheld: private coordinates are mixed in)", local, show_names=False)
    if "--google" in sys.argv:
        libraries, groups = google_libraries()
        report("Google's Maven repository", libraries, show_names=True, extra=f", {groups} groups")
        compare(local | libraries)
