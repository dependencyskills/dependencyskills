#!/usr/bin/env python3
"""KDoc coverage of a published library, from its -sources.jar on Maven Central.

    kdoc-coverage.py <group:artifact[:version]> ...

Coverage = share of public top-level/member declarations (fun/class/interface/
object/val/var, indent <= 4) immediately preceded by a `/** */`. Line-based and
approximate — the same method RAD-0011 used (undercounts docs sitting above an
annotation block). For picking a sparsely-documented library, relative order is
what matters.
"""
import sys, io, re, zipfile, urllib.request
import xml.etree.ElementTree as ET

BASE = "https://repo1.maven.org/maven2"
MODS = r"(?:public|open|final|abstract|sealed|data|inline|suspend|operator|infix|external|actual|expect|override|annotation|enum|value|companion|tailrec|inner|const|lateinit)\s+"
DECL = re.compile(r"^(?:" + MODS + r")*(fun|class|interface|object|val|var)\b")


def fetch(url):
    return urllib.request.urlopen(url, timeout=60).read()


def latest(group, artifact):
    url = f"{BASE}/{group.replace('.', '/')}/{artifact}/maven-metadata.xml"
    root = ET.fromstring(fetch(url))
    return root.findtext("versioning/release") or root.findtext("versioning/latest")


def coverage(jar):
    total = documented = 0
    with zipfile.ZipFile(io.BytesIO(jar)) as z:
        for name in z.namelist():
            if not name.endswith(".kt"):
                continue
            lines = z.read(name).decode("utf-8", "replace").split("\n")
            for i, line in enumerate(lines):
                indent = len(line) - len(line.lstrip(" "))
                if indent > 4:
                    continue
                s = line.strip()
                if re.match(r"^(private|internal|protected)\b", s):
                    continue
                if not DECL.match(s):
                    continue
                total += 1
                j = i - 1
                while j >= 0:
                    p = lines[j].strip()
                    if p == "" or p.startswith("@") or p.startswith("//"):
                        j -= 1
                        continue
                    if p.endswith("*/"):
                        documented += 1
                    break
    return total, documented


for coord in sys.argv[1:]:
    parts = coord.split(":")
    group, artifact = parts[0], parts[1]
    try:
        version = parts[2] if len(parts) > 2 else latest(group, artifact)
        url = f"{BASE}/{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}-sources.jar"
        total, documented = coverage(fetch(url))
        pct = (100 * documented / total) if total else 0
        print(f"{coord.split(':')[1]:24s} {version:12s} {documented:5d}/{total:<5d}  {pct:4.0f}%")
    except Exception as e:
        print(f"{coord:36s} ERROR {type(e).__name__}: {e}")
