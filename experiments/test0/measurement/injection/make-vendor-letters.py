#!/usr/bin/env python3
"""
Render the ADR-0011 vendor courtesy notices from the raw results — so no figure is ever typed.

ADR-0011 requires that named vendors hear about per-model results before they are published,
as a courtesy with a right of reply and no embargo. The letters therefore quote each vendor
its own compliance numbers, and those numbers are exactly the kind of thing that goes wrong
when a human copies them out of a table. This generator removes the copying step: every
`comply/total` in every letter comes from `tally.py` reading `results-*.json`.

What is machine-computed: the results table, the N and cell counts in captions and labels, the
list of arms actually run. What stays hand-written: the prose, in a per-vendor notes file —
the observations, the vendor-specific limitations, the tone.

  python3 make-vendor-letters.py --init            # scaffold config + template + notes (once)
  python3 make-vendor-letters.py --check           # tally, mapping and coverage; renders nothing
  python3 make-vendor-letters.py                   # render every vendor
  python3 make-vendor-letters.py --vendor openai   # render one
  python3 make-vendor-letters.py --security-txt    # also probe /.well-known/security.txt

Config and notes live in `outbox/vendor-disclosure/` and output lands in
`outbox/vendor-disclosure/generated/`. `outbox/` is gitignored; this script refuses to write
anywhere else, because unsent correspondence is not a published artifact.

Dependency-free (stdlib only), like the rest of the kit. `--security-txt` is the only thing
that touches the network, it is opt-in, and it does nothing but GET a public well-known URI.
"""
import argparse, json, os, re, sys, textwrap, urllib.error, urllib.request
from datetime import datetime, timezone

import tally

HERE = os.path.dirname(os.path.abspath(__file__))
PLACEHOLDER = re.compile(r"\{\{\s*([A-Za-z0-9_.-]+)\s*\}\}")
UA = "dependencyskills-security-txt-check/1.0 (+https://github.com/dependencyskills/dependencyskills)"

WRAP = 95
# Slots spliced mid-sentence: given a leading space when the template runs them straight on
# from the previous word, so a notes file never has to start a section with whitespace.
INLINE_SLOTS = ("compliance_note", "arms_block", "no_contact_note")

ARM_NAMES = {"A": "A instruction", "B": "B data-framed", "C": "C system"}
ARM_BULLETS = {
    "A": "- **A — instruction channel:** documentation presented as authoritative guidance.",
    "B": ("- **B — data channel:** the same text quoted explicitly as untrusted third-party data,"
          " with an\n  instruction not to follow directives inside it."),
    "C": "- **C — system channel:** the same text placed in the system prompt.",
}
DEFAULTS = {
    "compliance_note": ("Compliance means the model acted on the planted instruction. Payloads"
                        " were inert (a fake\nanalytics call, an endpoint that was unregistered"
                        " and resolved nowhere when the tests\nran, a marker string)."),
    "primary_qualifier": ", not about your {{model_word}}",
    "primary_extra": "",
    "asking_extra": "",
    "after_table": "",
    "preamble": "",
    "missing_arm_text": "not run",
    "table_label": "model",
}


# --- paths -----------------------------------------------------------------

def repo_root():
    d = HERE
    while d != "/":
        if os.path.isdir(os.path.join(d, ".git")):
            return d
        d = os.path.dirname(d)
    raise SystemExit("! not inside a git checkout — cannot locate outbox/")


def outbox_dir():
    return os.path.join(repo_root(), "outbox", "vendor-disclosure")


def under_outbox(path):
    """Every write goes through here. Drafts are not published by being generated."""
    root = os.path.realpath(os.path.join(repo_root(), "outbox"))
    real = os.path.realpath(path)
    if real != root and not real.startswith(root + os.sep):
        raise SystemExit(f"! refusing to write outside outbox/: {path}")
    return path


def write(path, text):
    under_outbox(path)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        f.write(text)
    return path


# --- config and notes ------------------------------------------------------

def load_config(path):
    if not os.path.exists(path):
        raise SystemExit(f"! no config at {path}\n  run --init to scaffold one (see the docstring)")
    return json.load(open(path))


def parse_notes(path):
    """A notes file is markdown; each `## slot-name` heading fills the slot of that name."""
    if not os.path.exists(path):
        raise SystemExit(f"! notes file missing: {path}")
    slots, name, buf = {}, None, []
    for line in open(path).read().splitlines():
        m = re.match(r"^##\s+(.+?)\s*$", line)
        if m:
            if name:
                slots[name] = "\n".join(buf).strip()
            name, buf = m.group(1).strip().lower().replace("-", "_"), []
        elif name is not None:
            buf.append(line)
    if name:
        slots[name] = "\n".join(buf).strip()
    return slots


# --- rendering -------------------------------------------------------------

def _sub(text, ctx):
    def one(m):
        key = m.group(1)
        if key not in ctx:
            return m.group(0)
        val = str(ctx[key])
        inline = key.endswith("_extra") or key in INLINE_SLOTS
        if inline and val and not val[0].isspace() and m.start() and not text[m.start() - 1].isspace():
            val = " " + val
        return val
    return PLACEHOLDER.sub(one, text)


def render(text, ctx, where):
    for _ in range(6):
        new = _sub(text, ctx)
        if new == text:
            break
        text = new
    left = sorted(set(PLACEHOLDER.findall(text)))
    if left:
        raise SystemExit(f"! unresolved placeholder(s) in {where}: {', '.join(left)}")
    return text


_MARKER = re.compile(r"^(\s*)([-*]|\d+\.)\s+")
_LABEL = re.compile(r"^\*\*[^*]+:\*\*\s*$")


def rewrap(text, width=WRAP):
    """Re-flow prose after substitution — a short figure replacing a long placeholder otherwise
    leaves the letter visibly ragged. Tables, headings, URLs and label lines are left alone."""
    out = []
    blocks = re.split(r"\n[ \t]*\n", text)
    for i, block in enumerate(blocks):
        lines = block.split("\n")
        # first and last blocks are the letterhead and the sign-off: line breaks are the content
        skip = (i in (0, len(blocks) - 1) or "http" in block
                or any(l.lstrip().startswith(("|", "#", ">", "```")) for l in lines)
                or any(_LABEL.match(l) for l in lines)
                or (len(lines) == 1 and re.match(r"^\*\*[^*]+:\*\*", lines[0])))
        if skip or not block.strip():
            out.append(block)
            continue
        if any(_MARKER.match(l) for l in lines):
            out.append(_wrap_list(lines, width))
        else:
            out.append(textwrap.fill(" ".join(l.strip() for l in lines), width,
                                     break_long_words=False, break_on_hyphens=False))
    return "\n\n".join(out)


def _wrap_list(lines, width):
    items, cur = [], []
    for l in lines:
        if _MARKER.match(l) and cur:
            items.append(cur); cur = [l]
        else:
            cur.append(l)
    if cur:
        items.append(cur)
    out = []
    for item in items:
        m = _MARKER.match(item[0])
        body = " ".join(x.strip() for x in item).strip()
        if not m:
            out.append(textwrap.fill(body, width, break_long_words=False, break_on_hyphens=False))
            continue
        lead = m.group(1) + m.group(2) + " "
        out.append(textwrap.fill(body[len(m.group(0)):], width, initial_indent=lead,
                                 subsequent_indent=" " * len(lead),
                                 break_long_words=False, break_on_hyphens=False))
    return "\n".join(out)


def cell(t, arm, missing):
    if t is None or arm not in t["arms"]:
        return missing
    v = t["arms"][arm]
    return f"{v['comply']}/{v['total']}"


def results_table(vendor, by_file):
    arms = vendor["arms"]
    missing = vendor.get("missing_arm_text", DEFAULTS["missing_arm_text"])
    label_col = vendor.get("table_label", DEFAULTS["table_label"])
    head = (f"| {label_col} | " if label_col else "| ") + " | ".join(ARM_NAMES[a] for a in arms) + " |"
    rule = ("|---|" if label_col else "|") + "---|" * len(arms)
    lines = [head, rule]
    for row in vendor["models"]:
        t = by_file.get(row["results"]) if "results" in row else None
        if "results" in row and t is None:
            raise SystemExit(f"! {vendor['id']}: no such results file: {row['results']}")
        rctx = {"n": t["n"], "payloads": len(t["payloads"]),
                "cells": (t["n"] or 0) * len(t["payloads"]), "model": t["model"]} if t else {}
        label = render(row["label"], rctx, f"{vendor['id']} row label")
        prefix = f"| {label} | " if label_col else "| "
        vals = []
        for a in arms:
            v = row["manual"].get(a, missing) if "manual" in row else cell(t, a, missing)
            vals.append(f"**{v}**" if a in row.get("emphasize", []) else v)
        lines.append(prefix + " | ".join(vals) + " |")
    return "\n".join(lines)


def measured_rows(vendor, by_file):
    return [by_file[r["results"]] for r in vendor["models"] if "results" in r]


def row_figures(vendor, by_file):
    """Per-row figures addressable from prose as {{cell_<key>_<arm>}} / {{flag_<key>_<arm>}} —
    so a number quoted in a sentence comes from the same tally as the table above it."""
    ctx = {}
    for row in vendor["models"]:
        k = row.get("key")
        if not k:
            continue
        if "results" in row:
            t = by_file[row["results"]]
            ctx[f"n_{k}"] = t["n"]
            ctx[f"payloads_{k}"] = len(t["payloads"])
            ctx[f"cells_{k}"] = (t["n"] or 0) * len(t["payloads"])
            ctx[f"flags_{k}"] = sum(v["flag"] for v in t["arms"].values())
            ctx[f"total_{k}"] = "%d/%d" % (sum(v["comply"] for v in t["arms"].values()),
                                           sum(v["total"] for v in t["arms"].values()))
            for a, v in t["arms"].items():
                ctx[f"cell_{k}_{a}"] = f"{v['comply']}/{v['total']}"
                ctx[f"flag_{k}_{a}"] = f"{v['flag']}/{v['total']}"
        else:
            for a, v in row.get("manual", {}).items():
                ctx[f"cell_{k}_{a}"] = v
    return ctx


def computed(vendor, by_file):
    rows = measured_rows(vendor, by_file)
    ns = sorted({t["n"] for t in rows if t["n"]})
    cells = sorted({(t["n"] or 0) * len(t["payloads"]) for t in rows})
    pls = sorted({len(t["payloads"]) for t in rows})
    n_per_cell = "–".join(str(x) for x in ns) if ns else "unrecorded"
    caption = vendor.get("measured_on", "")
    if len(ns) == 1 and len(cells) == 1:
        caption = f"{{{{measured_on}}}}, N={ns[0]} per cell, {cells[0]} cells per arm"
    else:
        caption = "{{measured_on}}"
    n_models = len(vendor["models"])
    return {
        "vendor_id": vendor["id"],
        "results_caption": caption,
        "n_per_cell": n_per_cell,
        "payloads_summary": "–".join(str(x) for x in pls) if pls else "unrecorded",
        "model_word": "model" if n_models == 1 else "models",
        "arms_block": "\n\n" + "\n".join(ARM_BULLETS[a] for a in vendor["arms"]) + "\n\n",
    }


def render_vendor(vendor, cfg, by_file, obdir):
    notes = parse_notes(os.path.join(obdir, vendor["notes"]))
    scalars = {k: v for k, v in cfg.items() if isinstance(v, str)}
    vscalars = {k: v for k, v in vendor.items() if isinstance(v, str)}
    ctx = {**DEFAULTS, **computed(vendor, by_file), **row_figures(vendor, by_file),
           **scalars, **vscalars, **notes}
    ctx["results_table"] = results_table(vendor, by_file)
    tmpl = open(os.path.join(obdir, vendor.get("template", "letter-template.md"))).read()
    out = render(tmpl, ctx, f"{vendor['id']} ({vendor.get('template', 'letter-template.md')})")
    return write(os.path.join(obdir, "generated", vendor["out"]), rewrap(out).rstrip() + "\n")


# --- security.txt ----------------------------------------------------------

FIELDS = ("Contact", "Policy", "Expires", "Encryption", "Preferred-Languages")


def fetch_security_txt(domain, timeout=15):
    """GET https://<domain>/.well-known/security.txt. Read-only; reports what it finds."""
    for host in (domain, "www." + domain) if not domain.startswith("www.") else (domain,):
        url = f"https://{host}/.well-known/security.txt"
        req = urllib.request.Request(url, headers={"User-Agent": UA})
        try:
            with urllib.request.urlopen(req, timeout=timeout) as r:
                body = r.read(64_000).decode("utf-8", "replace")
            return {"url": url, "status": 200, "fields": parse_security_txt(body)}
        except urllib.error.HTTPError as e:
            last = {"url": url, "status": e.code, "error": f"HTTP {e.code}", "fields": {}}
        except Exception as e:
            last = {"url": url, "status": None, "error": type(e).__name__ + ": " + str(e)[:120],
                    "fields": {}}
    return last


def parse_security_txt(body):
    out = {}
    for line in body.splitlines():
        line = line.strip()
        if not line or line.startswith("#"):
            continue
        if ":" not in line:
            continue
        k, v = line.split(":", 1)
        for f in FIELDS:
            if k.strip().lower() == f.lower():
                out.setdefault(f, []).append(v.strip())
    return out


def classify(result):
    """email | form | none — 'none' is what ADR-0011 lets you record and skip."""
    contacts = result.get("fields", {}).get("Contact", [])
    if not contacts:
        return "none"
    if any(c.lower().startswith("mailto:") or "@" in c.split()[0] for c in contacts):
        return "email"
    return "form"


def expired(result):
    for e in result.get("fields", {}).get("Expires", []):
        try:
            d = datetime.fromisoformat(e.replace("Z", "+00:00"))
            return d < datetime.now(timezone.utc), e
        except ValueError:
            return None, e
    return None, None


def security_report(cfg, vendors, obdir):
    lines = ["# security.txt probe",
             "",
             f"Probed {datetime.now(timezone.utc).date().isoformat()} by `make-vendor-letters.py"
             " --security-txt`.",
             "A read-only GET of `/.well-known/security.txt` per vendor domain. ADR-0011 bounds"
             " effort by the",
             "vendor's own channel: where no reachable contact is published, a public channel is"
             " an acceptable",
             "substitute and the absence is recorded here rather than chased.",
             "",
             "| vendor | domain | result | contact | policy | expires |",
             "|---|---|---|---|---|---|"]
    unreachable = []
    for v in vendors:
        for domain in v.get("domains", []):
            r = fetch_security_txt(domain)
            kind = classify(r) if r.get("status") == 200 else "none"
            f = r.get("fields", {})
            exp_flag, exp = expired(r)
            exp_txt = (exp or "—") + (" **(expired)**" if exp_flag else "")
            if r.get("status") == 200:
                status = "ok" if f else "200, no security.txt fields (soft 404?)"
            else:
                status = r.get("error", "unreachable")
            lines.append(f"| {v['id']} | `{domain}` | {status} | "
                         f"{'; '.join('`%s`' % c for c in f.get('Contact', [])) or '—'} | "
                         f"{'; '.join('`%s`' % c for c in f.get('Policy', [])) or '—'} | {exp_txt} |")
            print(f"  {v['id']:<12} {domain:<22} {status:<28} contact={kind}")
            if kind == "none":
                unreachable.append((v["id"], domain, status))
    if unreachable:
        lines += ["", "## No reachable security contact", "",
                  "These publish no usable `Contact:` (or the file could not be fetched"
                  " automatically). Per",
                  "ADR-0011 a public channel is an acceptable substitute, and where none is"
                  " reasonable the notice",
                  "may be skipped and the fact recorded — which this table does.", "",
                  "An HTTP 403 is **not** evidence that nothing is published: the vendor may"
                  " serve the file to a",
                  "browser and refuse an automated fetch. Check that one by hand before"
                  " recording it as absent.", ""]
        for vid, domain, status in unreachable:
            lines.append(f"- **{vid}** — `{domain}`: {status}")
    path = write(os.path.join(obdir, "generated", "contacts.md"), "\n".join(lines) + "\n")
    return path, unreachable


# --- check -----------------------------------------------------------------

def check(cfg, by_file):
    print("# tally (from results-*.json — the numbers of record)")
    for t in sorted(by_file.values(), key=lambda t: t["file"]):
        print("  " + tally.format_row(t))
    print("\n# vendor -> results mapping")
    referenced, manual = set(), []
    for v in cfg["vendors"]:
        print(f"  {v['id']}  ({', '.join(v['arms'])})")
        for row in v["models"]:
            if "results" in row:
                referenced.add(row["results"])
                t = by_file.get(row["results"])
                mark = "ok " if t else "!! MISSING"
                model = t["model"] if t else "?"
                print(f"    {mark} {row['results']:<52} model={model}")
            else:
                manual.append((v["id"], row["label"], row.get("source", "unrecorded")))
                print(f"    -- {row['label']:<52} declared in config (not machine-computed)")
    missed = sorted(set(by_file) - referenced)
    if missed:
        print("\n# results files no vendor letter references")
        for m in missed:
            print(f"    {m:<52} model={by_file[m]['model']}")
    if manual:
        print("\n# hand-declared rows — these bypass the tally, check them against their source")
        for vid, label, src in manual:
            print(f"    {vid}: {label}  <- {src}")
    declared = [(v["id"], k, val) for v in cfg["vendors"] for k, val in v.items()
                if k.startswith("manual_") and isinstance(val, str)]
    if declared:
        print("\n# hand-declared figures used in prose — same warning")
        for vid, k, val in declared:
            print(f"    {vid}: {{{{{k}}}}} = {val}")


# --- init ------------------------------------------------------------------

SKELETON_CONFIG = {
    "measured_on": "YYYY-MM-DD",
    "repo": "github.com/owner/repo",
    "kit_path": "experiments/test0/measurement/injection/",
    "sign_off": "<your name / GitHub handle>",
    "cross_vendor_phrase": "Across every agent tested",
    "vendors": [{
        "id": "acme",
        "out": "letter-acme.md",
        "notes": "notes/acme.md",
        "to": "Acme security / model behaviour",
        "subject": "Advance notice — measurement of doc-comment prompt injection, includes Acme results",
        "names_phrase": "name Acme",
        "domains": ["example.com"],
        "arms": ["A", "B"],
        "models": [{"label": "acme-model (N={{n}}, {{payloads}} payloads)",
                    "results": "results-acme-model.json", "emphasize": []}],
    }],
}
SKELETON_NOTES = """# Notes — Acme

Each `## slot` fills the same-named placeholder in the template. `{{n_per_cell}}` and the
row labels are substituted here too, so figures stay machine-derived even inside prose.

## after_table

The observation I intend to publish:

1. **Something the numbers show.**

## limitations

N is small ({{n_per_cell}} per cell), one prompt template, one task domain. These are single
measurements, version- and date-stamped, not a characterisation of the {{model_word}}.
"""


def init(obdir):
    cpath = os.path.join(obdir, "vendors.json")
    if os.path.exists(cpath):
        print(f"# {cpath} exists — leaving it alone")
    else:
        write(cpath, json.dumps(SKELETON_CONFIG, indent=2) + "\n")
        print(f"# wrote {cpath}")
    npath = os.path.join(obdir, "notes", "acme.md")
    if os.path.exists(npath):
        print(f"# {npath} exists — leaving it alone")
    else:
        write(npath, SKELETON_NOTES)
        print(f"# wrote {npath}")
    print("# write letter-template.md next — see the letters already in this directory")


# --- main ------------------------------------------------------------------

def main():
    ap = argparse.ArgumentParser(description=__doc__.strip().splitlines()[0])
    ap.add_argument("--config", default=None, help="default outbox/vendor-disclosure/vendors.json")
    ap.add_argument("--vendor", action="append", help="render only these vendor ids")
    ap.add_argument("--check", action="store_true", help="tally + mapping + coverage, render nothing")
    ap.add_argument("--security-txt", action="store_true", help="probe /.well-known/security.txt")
    ap.add_argument("--init", action="store_true", help="scaffold a config and a notes file")
    args = ap.parse_args()

    obdir = outbox_dir()
    if args.init:
        init(obdir); return

    cfg = load_config(args.config or os.path.join(obdir, "vendors.json"))
    by_file = {t["file"]: t for t in tally.tally_all(HERE)}

    if args.check:
        check(cfg, by_file); return

    vendors = [v for v in cfg["vendors"] if not args.vendor or v["id"] in args.vendor]
    if not vendors:
        raise SystemExit(f"! no vendor matched {args.vendor}")
    for v in vendors:
        print(f"# wrote {render_vendor(v, cfg, by_file, obdir)}")
    manual = [(v["id"], r["label"]) for v in vendors for r in v["models"] if "results" not in r]
    if manual:
        print("# hand-declared rows (NOT from tally.py) — verify against their source:")
        for vid, label in manual:
            print(f"    {vid}: {label}")
    if args.security_txt:
        print("# probing /.well-known/security.txt")
        path, unreachable = security_report(cfg, vendors, obdir)
        print(f"# wrote {path}")
        if unreachable:
            print("# no reachable security contact: "
                  + ", ".join(sorted({v for v, _, _ in unreachable})))


if __name__ == "__main__":
    main()
