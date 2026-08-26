#!/usr/bin/env python3
"""
Fetch identifiers from packages this machine has NEVER downloaded, for a clean false-positive test.

WHY IT HAS TO BE FRESH. The classifier's negative class is the local caches, and a false-positive
rate measured on the data a model trained on is not a measurement. It also has a subtler problem:
the caches hold what one project resolved, so they are 68% Kotlin Multiplatform and heavy on one
set of publishers. A rate that holds there might be a rate that holds for *that* code.

WHAT IT DOES. Pulls a random sample from the npm registry's own listing of packages nobody here has
cached, takes the newest version's tarball, and extracts declared names with the same extractor the
corpus uses. npm rather than maven because the registry exposes a package listing without a search
index, and because it is the ecosystem the corpus is thinnest in relative to its real size.

NO CODE IS EXECUTED. Tarballs are read in memory and parsed; nothing is installed, nothing runs.
The packages are ordinary public dependencies, not an attack corpus.

Run: uv run --with tree-sitter --with tree-sitter-language-pack python fresh_sample.py --n 120
Out: fresh-identifiers.json (gitignored) — identifiers and doc comments, so `test19` can use
     the same never-downloaded sample for prose without fetching a second time.
"""
import json
import os
import random
import sqlite3
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "corpus"))
DB = os.path.join(HERE, "..", "corpus", "corpus.db")
OUT = os.path.join(HERE, "fresh-identifiers.json")
SEED = 17
UA = {"User-Agent": "dependencyskills-experiment (public package metadata)"}

# Two discovery routes, because one of them was not enough. A dependency walk from mundane seeds
# produces a sample shaped like code people actually resolve — but modern packages declare few
# dependencies, so the walk drains after a few dozen. The registry's search endpoint supplies
# breadth; the walk supplies realism. Both are used and the counts are reported separately.
SEEDS = ["chalk", "yargs", "date-fns", "pino", "nanoid", "zod", "got", "execa", "tar-fs",
         "cheerio", "fast-glob", "sharp", "ajv", "undici", "mime", "qs", "semver", "ws"]

# Neutral, spread across purposes. Not "security" or "logging" — a topic-shaped sample would make
# the false-positive rate a statement about that topic's vocabulary.
TOPICS = ["parser", "http", "date", "string", "stream", "test", "cli", "config", "math",
          "image", "database", "queue", "cache", "template", "color", "path", "csv", "crypto",
          "validation", "geometry", "markdown", "compression", "random", "unicode", "audio"]


def already_have():
    db = sqlite3.connect(DB)
    have = {r[0] for r in db.execute("SELECT artifact FROM libraries WHERE ecosystem='npm'")}
    have |= {r[0].split("@")[0] for r in db.execute(
        "SELECT library FROM libraries WHERE ecosystem='npm'")}
    db.close()
    return have


def fetch_json(url):
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30) as r:
        return json.load(r)


def search_names(topic, pages=2):
    """Package names from the registry's search endpoint. Public, no key, no scraping."""
    out = []
    for page in range(pages):
        try:
            res = fetch_json("https://registry.npmjs.org/-/v1/search"
                             f"?text={topic}&size=250&from={page * 250}")
        except Exception:
            break
        objs = res.get("objects") or []
        out.extend((o.get("package") or {}).get("name") for o in objs)
        if len(objs) < 250:
            break
    return [n for n in out if n]


def discover(n, have):
    """Uncached packages, from a dependency walk and from topic search. Newest version of each."""
    r = random.Random(SEED)
    pool, seen = [], set()
    # route 1 — dependency edges out from the seeds
    queue, walked = list(SEEDS), 0
    while queue and walked < n:
        name = queue.pop(0)
        if name in seen:
            continue
        seen.add(name)
        walked += 1
        try:
            meta = fetch_json(f"https://registry.npmjs.org/{urllib_quote(name)}")
        except Exception:
            continue
        latest = (meta.get("dist-tags") or {}).get("latest")
        ver = (meta.get("versions") or {}).get(latest) or {}
        deps = sorted((ver.get("dependencies") or {}).keys())
        r.shuffle(deps)
        queue.extend(deps[:8])
        pool.append(("walk", name))
    # route 2 — topic search, for breadth the walk cannot reach
    topics = list(TOPICS)
    r.shuffle(topics)
    for topic in topics:
        pool.extend(("search", nm) for nm in search_names(topic))
        if len(pool) > n * 8:
            break

    r.shuffle(pool)
    found, taken = [], set()
    for route, name in pool:
        if len(found) >= n:
            break
        base = name.split("/")[-1]
        if name in taken or name in have or base in have:
            continue
        taken.add(name)
        try:
            meta = fetch_json(f"https://registry.npmjs.org/{urllib_quote(name)}")
        except Exception:
            continue
        latest = (meta.get("dist-tags") or {}).get("latest")
        if not latest:
            continue
        tarball = (((meta.get("versions") or {}).get(latest) or {}).get("dist") or {}).get("tarball")
        if tarball:
            found.append((name, latest, tarball, route))
            if len(found) % 25 == 0:
                print(f"  {len(found):>4}/{n}  {name}@{latest}", flush=True)
    return found


def urllib_quote(name):
    return name.replace("/", "%2F")


def main():
    n = int(sys.argv[sys.argv.index("--n") + 1]) if "--n" in sys.argv else 120
    from tree_sitter_language_pack import get_parser
    import build as corpus_build
    import io as _io
    import urllib.request as _u

    have = already_have()
    print(f"# {len(have)} npm packages already in the corpus — these are excluded\n")
    picks = discover(n, have)
    routes = {}
    for *_, route in picks:
        routes[route] = routes.get(route, 0) + 1
    print(f"\n# {len(picks)} uncached packages selected: "
          + ", ".join(f"{v} by {k}" for k, v in sorted(routes.items())) + "\n")

    parsers = {"javascript": get_parser("javascript"), "typescript": get_parser("typescript")}
    names, docs, packages = set(), [], []
    for name, ver, url, _route in picks:
        try:
            with _u.urlopen(_u.Request(url, headers=UA), timeout=60) as r:
                body = r.read()
        except Exception as e:
            print(f"  skip {name}: {e}", file=sys.stderr)
            continue
        tmp = os.path.join(HERE, ".tarball.tgz")
        open(tmp, "wb").write(body)
        try:
            declared, entries = corpus_build.walk_tarball(tmp, parsers)
        finally:
            os.unlink(tmp)
        if declared or entries:
            names |= declared
            # Deduplicated within the package, the same rule the npm harvest uses: one module
            # emitted as CommonJS, ESM and .d.ts carries its doc comments three times.
            seen = set()
            for e in entries:
                if e[3] not in seen:
                    seen.add(e[3])
                    docs.append({"symbol": e[1], "signature": e[2], "doc": e[3]})
            packages.append(f"{name}@{ver}")
    json.dump({"packages": packages, "routes": routes, "identifiers": sorted(names),
               "docs": docs}, open(OUT, "w"))
    print(f"\n# {len(packages)} packages, {len(names)} distinct identifiers, "
          f"{len(docs)} doc comments -> {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
