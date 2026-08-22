#!/usr/bin/env python3
"""
test1 — "resolve in the index" demo.

The Dokka arm showed cross-library inherited docs are realized only with the supertype
*source* in the same parse run. This proves the alternative: realize the same enrichment
as a GRAPH JOIN in the index, from per-library tree-sitter extraction alone — no Dokka, no
multi-source parse. Parse = local extraction (each entry + its `override → supertype`
edge); the index resolves the edge against the supertype's entry, which is present because
the whole graph is harvested.

Harvest kaml and kotlinx.serialization independently (tree-sitter, read in place), build
one index, and resolve each of kaml's undocumented `override`s against its supertype member.

Run: uv run --with tree-sitter --with tree-sitter-language-pack resolve_in_index.py
"""
from extract_treesitter import fetch_sources, extract

LIBS = [
    "com.charleskorn.kaml:kaml:0.104.0",
    "org.jetbrains.kotlinx:kotlinx-serialization-core-jvm:1.9.0",
]


def main():
    index = {}          # symbol -> entry (the harvested graph)
    first = None
    for coord in LIBS:
        pinned, _, zf = fetch_sources(coord)
        es = extract(zf)
        if first is None:
            first = es          # kaml — the library whose overrides we resolve
        for e in es:
            index.setdefault(e["symbol"], e)

    def resolve(member, supers, seen=None):
        # follow the supertype chain through the index until a documented member is found
        seen = seen or set()
        for st in supers:
            if st in seen:
                continue
            seen.add(st)
            se = index.get(f"{st}.{member}")
            if se and se["has_kdoc"]:
                return f"{st}.{member}"
            cls = index.get(st)                       # the supertype's own class entry
            if cls:
                hit = resolve(member, cls["super_types"], seen)
                if hit:
                    return hit
        return None

    undoc_ovr = [e for e in first if e["is_override"] and not e["has_kdoc"]]
    resolved, examples = 0, []
    for e in undoc_ovr:
        hit = resolve(e["member"], e["super_types"])
        if hit:
            resolved += 1
            if len(examples) < 6:
                examples.append((e["symbol"], hit))

    print(f"index: {len(index)} entries from {len(LIBS)} libraries (tree-sitter, per-library, in place)")
    print(f"kaml undocumented overrides: {len(undoc_ovr)}")
    print(f"  RESOLVED to a documented supertype member via index join: {resolved}")
    print(f"  → realized inherited docs with NO Dokka and NO multi-source parse")
    print("examples:")
    for a, b in examples:
        print(f"  {a}\n     ← inherits doc from  {b}")


if __name__ == "__main__":
    main()
