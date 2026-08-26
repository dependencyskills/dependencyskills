#!/usr/bin/env python3
"""
test21 — identifiers from compiled classes, for the libraries that ship no source.

WHY THERE IS A GAP TO FILL. The corpus is built from `-sources.jar`, and **168 cached artifacts
have a binary and no sources jar** — `androidx.browser`, `androidx.room:room-common`,
`androidx.documentfile` among them. They are invisible to every experiment so far. `test2` measured
what bytecode recovers and found it **degraded but usable**: the public surface survives, the
documentation does not, and parameter *names* do not either — only their types.

So this is `test18`'s question asked of a different source: does the identifier classifier hold up
on names recovered from compiled classes?

THE RISK THAT MAKES IT WORTH RUNNING. Bytecode contains names no human wrote — `lambda$next$0`,
`access$000`, `$$serializer`, `this$0` — and a classifier calibrated on hand-written identifiers
has never seen them. If compiler-generated names score high, the false-positive rate on binary-only
libraries is worse than the published one and nobody would know.

NO JDK, AND NO `javap`. The class file format carries `methods` and `fields` tables whose entries
index the constant pool directly, so the names come out of ~60 lines of struct parsing. That is
faster than a subprocess per class, and it also exposes `ACC_SYNTHETIC`, which `javap` hides by
default — and hiding it is exactly what would conceal the risk above.

Run: uv run python bytecode_harvest.py
Out: bytecode-identifiers.json (gitignored)
"""
import json
import os
import struct
import sys
import zipfile

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(HERE, "..", "corpus"))
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
OUT = os.path.join(HERE, "bytecode-identifiers.json")

ACC_SYNTHETIC = 0x1000
ACC_BRIDGE = 0x0040
CONSTANT_SIZES = {3: 4, 4: 4, 5: 8, 6: 8, 7: 2, 8: 2, 9: 4, 10: 4, 11: 4, 12: 4,
                  15: 3, 16: 2, 17: 4, 18: 4, 19: 2, 20: 2}


def parse_class(data):
    """(class name, [(member name, synthetic)]) from one .class file. Constant pool + tables."""
    if len(data) < 10 or data[:4] != b"\xca\xfe\xba\xbe":
        return None, []
    pool, i, n = {}, 10, struct.unpack_from(">H", data, 8)[0]
    idx = 1
    while idx < n:
        tag = data[i]
        i += 1
        if tag == 1:                                    # CONSTANT_Utf8
            ln = struct.unpack_from(">H", data, i)[0]
            pool[idx] = data[i + 2:i + 2 + ln].decode("utf-8", "replace")
            i += 2 + ln
        else:
            i += CONSTANT_SIZES.get(tag, 2)
        # long and double occupy two pool slots — the spec's one genuinely surprising rule
        idx += 2 if tag in (5, 6) else 1
    try:
        _flags, this_idx = struct.unpack_from(">HH", data, i)
        i += 6                                          # access, this, super
        ifc = struct.unpack_from(">H", data, i)[0]
        i += 2 + ifc * 2
        cls = pool.get(struct.unpack_from(">H", data, 0)[0])  # placeholder, resolved below
    except struct.error:
        return None, []
    # `this_class` is a CONSTANT_Class whose name_index we cannot read without the class entries,
    # so take the name from the file path instead — the caller has it and it is authoritative.
    members = []
    for _table in ("fields", "methods"):
        try:
            count = struct.unpack_from(">H", data, i)[0]
            i += 2
            for _ in range(count):
                acc, name_idx, _desc_idx, attrs = struct.unpack_from(">HHHH", data, i)
                i += 8
                for _a in range(attrs):
                    _an, alen = struct.unpack_from(">HI", data, i)
                    i += 6 + alen
                nm = pool.get(name_idx)
                if nm:
                    members.append((nm, bool(acc & (ACC_SYNTHETIC | ACC_BRIDGE))))
        except struct.error:
            break
    return cls, members


def walk_archive(path):
    """One jar or aar -> (declared names, synthetic names). aar wraps its classes in classes.jar."""
    plain, synth = set(), set()
    try:
        z = zipfile.ZipFile(path)
    except (zipfile.BadZipFile, OSError):
        return plain, synth
    with z:
        names = z.namelist()
        inner = [n for n in names if n.endswith("classes.jar")]
        if inner and not any(n.endswith(".class") for n in names):
            import io
            try:
                return walk_bytes(z.read(inner[0]))
            except (zipfile.BadZipFile, OSError, KeyError):
                return plain, synth
        for n in names:
            if not n.endswith(".class"):
                continue
            try:
                _c, members = parse_class(z.read(n))
            except (zipfile.BadZipFile, OSError, RuntimeError):
                continue
            simple = n[:-len(".class")].split("/")[-1]
            (synth if "$" in simple and simple.split("$")[-1].isdigit() else plain).add(simple)
            for nm, is_syn in members:
                if nm in ("<init>", "<clinit>"):
                    continue
                (synth if is_syn or nm.startswith(("lambda$", "access$", "this$", "$$")) else
                 plain).add(nm)
    return plain, synth


def walk_bytes(blob):
    import io
    import tempfile
    with tempfile.NamedTemporaryFile(suffix=".jar", delete=False) as f:
        f.write(blob)
        p = f.name
    try:
        return walk_archive(p)
    finally:
        os.unlink(p)


def main():
    import build as corpus_build
    private = corpus_build.load_private(corpus_build.PRIVATE, "publishers")
    plain, synth, libs, binary_only = set(), set(), [], []
    for group in sorted(os.listdir(CACHE)):
        gp = os.path.join(CACHE, group)
        if not os.path.isdir(gp) or corpus_build.excluded(group, private):
            continue
        for artifact in sorted(os.listdir(gp)):
            ap = os.path.join(gp, artifact)
            if not os.path.isdir(ap):
                continue
            src = binj = version = None
            for v in sorted((d for d in os.listdir(ap)
                             if os.path.isdir(os.path.join(ap, d))), reverse=True):
                vp = os.path.join(ap, v)
                for h in os.listdir(vp):
                    hp = os.path.join(vp, h)
                    if not os.path.isdir(hp):
                        continue
                    for f in os.listdir(hp):
                        if f.endswith("-sources.jar"):
                            src = src or os.path.join(hp, f)
                        elif f.endswith((".jar", ".aar")) and not f.endswith("-javadoc.jar"):
                            if binj is None:
                                binj, version = os.path.join(hp, f), v
                if binj:
                    break
            if not binj:
                continue
            p, s = walk_archive(binj)
            if not p and not s:
                continue
            plain |= p
            synth |= s
            lib = f"{group}:{artifact}:{version}"
            libs.append(lib)
            if not src:
                binary_only.append(lib)
            if len(libs) % 200 == 0:
                print(f"  … {len(libs)} artifacts", file=sys.stderr, flush=True)
    # What bytecode adds that source cannot reach: identifiers that appear ONLY in artifacts with
    # no sources jar. Everything else is a second route to something already harvested.
    only_ids = set()
    for lib in binary_only:
        group, artifact, version = lib.split(":")
        vp = os.path.join(CACHE, group, artifact, version)
        for h in os.listdir(vp):
            hp = os.path.join(vp, h)
            if not os.path.isdir(hp):
                continue
            for f in os.listdir(hp):
                if f.endswith((".jar", ".aar")) and not f.endswith(
                        ("-javadoc.jar", "-sources.jar")):
                    p2, _s2 = walk_archive(os.path.join(hp, f))
                    only_ids |= p2
    json.dump({"libraries": libs, "binary_only": binary_only,
               "identifiers": sorted(plain), "synthetic": sorted(synth),
               "binary_only_identifiers": sorted(only_ids)}, open(OUT, "w"))
    print(f"# {len(only_ids)} identifiers live in the binary-only artifacts")
    print(f"\n# {len(libs)} artifacts, {len(binary_only)} of them with NO sources jar")
    print(f"# {len(plain)} author-written identifiers, {len(synth)} compiler-generated")
    print(f"# wrote {OUT}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
