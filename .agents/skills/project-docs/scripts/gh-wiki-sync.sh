#!/usr/bin/env bash
# Two-way sync between docs/knowledge/ and the GitHub repo wiki.
#
# Model: git is the merge engine on BOTH sides - the wiki is itself a git
# repo (<repo>.wiki.git). Content flows both ways per-page, three-way
# merged against a recorded base (KB_DIR/.gh-wiki-sync/ - commit it,
# never hand-edit). Structure flows UP: the wiki has no hierarchy UI, so
# the docs/knowledge/ tree owns the layout - page names encode the path
# (Architecture-Decisions-Foo) and a generated _Sidebar.md shows the
# tree. A page created fresh in the wiki UI lands at the KB root on pull,
# reported for filing; moving it into a section renames the page on the
# next sync.
#
# Mapping: docs/knowledge/README.md <-> Home; a section's README.md <->
# the section page (Architecture-Decisions); leaves <-> path-joined
# pages. _Sidebar.md is generated - wiki-side edits to it are
# overwritten. A local move shows up as a page rename (content match);
# move+edit degrades to delete+create - pass --allow-delete after a
# reorganize to prune the old page.
#
# Usage: gh-wiki-sync.sh [KB_DIR] [options]
#   KB_DIR          sync domain (default ./docs/knowledge)
#   --repo O/R      GitHub repo (default: tracker.repo from
#                   .agents/config/story-tools.json)
#   --pull-only     apply wiki -> local only; local changes reported pending
#   --allow-delete  a locally deleted file deletes its wiki page
#   --force         bootstrap over a non-empty KB_DIR with no sync state:
#                   local files are adopted and pushed as pages
#   --dry-run       print the full action plan; change nothing anywhere
#   --help          this text
#
# Conflicts get git-style markers and are NEVER pushed until the markers
# are gone - resolve locally, then sync again.
# Exit codes: 0 ok, 1 error, 2 completed but conflicts need resolution.
set -uo pipefail

KB_DIR=""; REPO=""; DRY=0; PULL_ONLY=0; ALLOW_DELETE=0; FORCE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --repo) REPO="$2"; shift 2;;
    --pull-only) PULL_ONLY=1; shift;;
    --allow-delete) ALLOW_DELETE=1; shift;;
    --force) FORCE=1; shift;;
    --dry-run) DRY=1; shift;;
    --help) awk 'NR>1 && !/^#/{exit} NR>1{sub(/^# ?/,""); print}' "$0"; exit 0;;
    *) KB_DIR="$1"; shift;;
  esac
done
KB_DIR="${KB_DIR:-./docs/knowledge}"

command -v git >/dev/null 2>&1 || { echo "error: git is required" >&2; exit 1; }
command -v python3 >/dev/null 2>&1 || { echo "error: python3 is required" >&2; exit 1; }

read_pointer() {
  local f="./.agents/config/story-tools.json"
  [[ -f "$f" ]] || return 0
  sed -nE 's/.*"'"$1"'": *"?([^",}]+)"?.*/\1/p' "$f" | head -1
}
[[ -z "$REPO" ]] && REPO="$(read_pointer repo || true)"

# GH_WIKI_URL overrides the remote entirely (used by the test harness).
if [[ -z "${GH_WIKI_URL:-}" ]]; then
  [[ -z "$REPO" ]] && { echo "usage: gh-wiki-sync.sh [KB_DIR] --repo owner/repo" >&2; exit 1; }
  CONN="$(read_pointer connection || true)"
  if [[ -z "${GITHUB_TOKEN:-}" && -n "$CONN" && -f "$HOME/.agents/story-tools/connections/$CONN.env" ]]; then
    # shellcheck disable=SC1090
    source "$HOME/.agents/story-tools/connections/$CONN.env"
  fi
  if [[ -z "${GITHUB_TOKEN:-}" && -f "$HOME/.agents/story-tools/connections/github.env" ]]; then
    # shellcheck disable=SC1091
    source "$HOME/.agents/story-tools/connections/github.env"
  fi
  if [[ -z "${GITHUB_TOKEN:-}" ]] && command -v gh >/dev/null 2>&1; then
    GITHUB_TOKEN="$(gh auth token 2>/dev/null || true)"
  fi
  [[ -z "${GITHUB_TOKEN:-}" ]] && { echo "error: no GitHub token - run the story-tools installer" >&2; exit 1; }
  WIKI_URL="https://x-access-token:${GITHUB_TOKEN}@github.com/${REPO}.wiki.git"
else
  WIKI_URL="$GH_WIKI_URL"
fi

# ---- capability check + clone ---------------------------------------------
if ! git ls-remote "$WIKI_URL" >/dev/null 2>&1; then
  if [[ -n "$REPO" && -n "${GITHUB_TOKEN:-}" ]]; then
    has_wiki="$(curl -sfS -m 10 -H "Authorization: Bearer $GITHUB_TOKEN" \
      -H "Accept: application/vnd.github+json" \
      "https://api.github.com/repos/$REPO" 2>/dev/null \
      | grep -oE '"has_wiki": *(true|false)' | grep -oE 'true|false' || true)"
    if [[ "$has_wiki" == "false" ]]; then
      echo "error: the wiki is disabled on $REPO (Settings > Features; private repos need a paid plan)" >&2
    elif [[ "$has_wiki" == "true" ]]; then
      echo "error: the wiki on $REPO is enabled but uninitialized - create the Home page once in the web UI, then re-run" >&2
    else
      echo "error: cannot reach the wiki repo for $REPO (check the token's Contents permission)" >&2
    fi
  else
    echo "error: cannot reach the wiki repo at $WIKI_URL" >&2
  fi
  exit 1
fi

TMP="$(mktemp -d)"; trap 'rm -rf "$TMP"' EXIT
git clone -q "$WIKI_URL" "$TMP/wiki" || { echo "error: wiki clone failed" >&2; exit 1; }

export KB_DIR REPO WIKI_URL DRY PULL_ONLY ALLOW_DELETE FORCE
export WIKI_CLONE="$TMP/wiki"
python3 <<'EOF'
import os, re, subprocess, sys, datetime

KB   = os.environ['KB_DIR'].rstrip('/')
W    = os.environ['WIKI_CLONE']
REPO = os.environ.get('REPO') or os.environ['WIKI_URL']
DRY  = os.environ['DRY'] == '1'
PULL = os.environ['PULL_ONLY'] == '1'
ADEL = os.environ['ALLOW_DELETE'] == '1'
FORCE = os.environ['FORCE'] == '1'

STATE = os.path.join(KB, '.gh-wiki-sync')
BASE  = os.path.join(STATE, 'base')
MANF  = os.path.join(STATE, 'manifest.tsv')

def run(*args, cwd=None, check=True, capture=False):
    return subprocess.run(args, cwd=cwd, check=check,
                          capture_output=capture, text=True)

def read(p):
    with open(p, encoding='utf-8') as f: return f.read()
def write(p, s):
    os.makedirs(os.path.dirname(p) or '.', exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f: f.write(s)

def cap(seg):
    return '-'.join(w[:1].upper() + w[1:] for w in re.split(r'[-_ ]+', seg) if w)

def page_for(path):          # rel path -> wiki page name
    p = path[:-3] if path.endswith('.md') else path
    if p == 'README': return 'Home'
    if p.endswith('/README'): p = p[:-len('/README')]
    return '-'.join(cap(re.sub(r'[^A-Za-z0-9._ -]+', '', s)) for s in p.split('/'))

def title_for(path):         # link text: last segment, words capitalized
    p = path[:-3] if path.endswith('.md') else path
    if p.endswith('/README'): p = p[:-len('/README')]
    return cap(os.path.basename(p)).replace('-', ' ')

def has_markers(p):
    try: return bool(re.search(r'^<{7}( |$)', read(p), re.M))
    except OSError: return False

def same(a, b):
    try: return read(a) == read(b)
    except OSError: return False

# ---- gather ----------------------------------------------------------------
manifest = {}                      # path -> page
if os.path.isfile(MANF):
    for line in read(MANF).splitlines():
        if '\t' in line:
            pth, pg = line.split('\t', 1)
            manifest[pth] = pg
pages_of = {v: k for k, v in manifest.items()}

local_files = []
for root, dirs, files in os.walk(KB):
    dirs[:] = [d for d in dirs if d not in ('.gh-wiki-sync', '.yt-sync')]
    for f in files:
        if f.endswith('.md'):
            local_files.append(os.path.relpath(os.path.join(root, f), KB))
local_files.sort()

wiki_pages = sorted(f[:-3] for f in os.listdir(W)
                    if f.endswith('.md') and f not in ('_Sidebar.md', '_Footer.md'))

if not os.path.isfile(MANF) and local_files and not FORCE:
    print(f"error: {KB} is non-empty but has no sync state.", file=sys.stderr)
    print("  --force adopts every local file as a wiki page (and pulls wiki-only pages).", file=sys.stderr)
    sys.exit(1)

PUSHED, PULLED, MERGED, RENAMED, NEWWIKI, DELETED, PENDING, CONFLICTS = ([] for _ in range(8))
wiki_dirty = False
pairs = {}                         # final path -> page

def wiki_mv(old, new):
    global wiki_dirty
    if DRY: return
    r = subprocess.run(['git', 'mv', old + '.md', new + '.md'], cwd=W,
                       capture_output=True, text=True)
    if r.returncode != 0:
        os.replace(os.path.join(W, old + '.md'), os.path.join(W, new + '.md'))
    b = os.path.join(BASE, old + '.md')
    if os.path.isfile(b): os.replace(b, os.path.join(BASE, new + '.md'))
    wiki_dirty = True

def wiki_rm(pg):
    global wiki_dirty
    if DRY: return
    r = subprocess.run(['git', 'rm', '-q', pg + '.md'], cwd=W,
                       capture_output=True, text=True)
    if r.returncode != 0:
        try: os.remove(os.path.join(W, pg + '.md'))
        except OSError: pass
    try: os.remove(os.path.join(BASE, pg + '.md'))
    except OSError: pass
    wiki_dirty = True

def push_page(pth, pg):
    global wiki_dirty
    if DRY: return
    src = os.path.join(KB, pth)
    write(os.path.join(W, pg + '.md'), read(src))
    write(os.path.join(BASE, pg + '.md'), read(src))
    wiki_dirty = True

# ---- pass 1: known pairings ------------------------------------------------
for pth in sorted(manifest):
    pg = manifest[pth]
    L, R, B = os.path.join(KB, pth), os.path.join(W, pg + '.md'), os.path.join(BASE, pg + '.md')
    l_ex, r_ex = os.path.isfile(L), os.path.isfile(R)

    if not l_ex and not r_ex:
        continue                                   # gone both sides

    if not l_ex:
        moved = next((c for c in local_files
                      if c not in manifest and c not in pairs
                      and os.path.isfile(B) and same(os.path.join(KB, c), B)), None)
        if moved:
            newpg = page_for(moved)
            RENAMED.append(f"{pg} -> {newpg} ({moved})")
            if not PULL: wiki_mv(pg, newpg)
            pairs[moved] = newpg
        elif ADEL and not PULL:
            DELETED.append(f"{pg} (local file {pth} removed)")
            wiki_rm(pg)
        else:
            PENDING.append(f"{pth} deleted locally - page '{pg}' kept (use --allow-delete)")
            pairs[pth] = pg
        continue

    if not r_ex:                                   # page deleted in the UI
        if os.path.isfile(B) and same(L, B):
            PULLED.append(f"{pth} (page '{pg}' deleted in wiki - file pruned)")
            if not DRY:
                os.remove(L)
                try: os.remove(B)
                except OSError: pass
        else:
            CONFLICTS.append(f"{pth}: page '{pg}' deleted in wiki but the file has local edits - keep or delete it, then sync")
            pairs[pth] = pg
        continue

    expected = page_for(pth)
    if expected != pg:                             # local move of a live file
        RENAMED.append(f"{pg} -> {expected} ({pth})")
        if not PULL: wiki_mv(pg, expected)
        pg = expected
        R, B = os.path.join(W, pg + '.md'), os.path.join(BASE, pg + '.md')
    pairs[pth] = pg

    if has_markers(L):
        CONFLICTS.append(f"{pth} still has conflict markers - not pushed")
        continue

    l_chg = not (os.path.isfile(B) and same(L, B))
    r_chg = not (os.path.isfile(B) and same(R, B))

    if not l_chg and not r_chg:
        continue
    if l_chg and not r_chg:
        if PULL: PENDING.append(f"{pth} (local edit not pushed)"); continue
        PUSHED.append(f"{pth} -> {pg}")
        push_page(pth, pg)
    elif r_chg and not l_chg:
        PULLED.append(f"{pg} -> {pth}")
        if not DRY:
            write(L, read(R)); write(B, read(R))
    else:                                          # both changed: 3-way merge
        base = B if os.path.isfile(B) else os.devnull
        r = subprocess.run(['git', 'merge-file', '-p',
                            '-L', f'{pth} (local)', '-L', 'base', '-L', f'wiki:{pg}',
                            L, base, R], capture_output=True, text=True)
        if r.returncode == 0:
            MERGED.append(f"{pth} <-> {pg}")
            if not DRY:
                write(L, r.stdout)
                if PULL:
                    PENDING.append(f"{pth} (merged locally, push pending)")
                    write(B, read(R))
                else:
                    push_page(pth, pg)
        elif r.returncode > 0 and r.returncode < 128:
            CONFLICTS.append(f"{pth} (vs page '{pg}')")
            if not DRY:
                write(L, r.stdout)
                write(B, read(R))   # base := wiki; resolved local pushes next sync
        else:
            CONFLICTS.append(f"{pth}: merge failed ({r.stderr.strip()})")

# ---- pass 2: new local files -----------------------------------------------
for pth in local_files:
    if pth in pairs or pth in manifest: continue
    pg = page_for(pth)
    holder = next((p for p, g in pairs.items() if g == pg), None)
    if holder:
        CONFLICTS.append(f"{pth}: page name '{pg}' collides with {holder} - rename one")
        continue
    L = os.path.join(KB, pth)
    if has_markers(L):
        CONFLICTS.append(f"{pth} still has conflict markers - not pushed"); continue
    if pg in wiki_pages and pg not in pages_of:
        # bootstrap: both sides have this page
        if same(L, os.path.join(W, pg + '.md')):
            pairs[pth] = pg
            if not DRY: write(os.path.join(BASE, pg + '.md'), read(L))
            continue
        if not FORCE:
            CONFLICTS.append(f"{pth} and wiki page '{pg}' differ with no sync state - reconcile by hand or --force (local wins)")
            continue
    if PULL:
        PENDING.append(f"{pth} (new file, push pending)"); pairs[pth] = pg; continue
    PUSHED.append(f"{pth} -> {pg} (new)")
    push_page(pth, pg)
    pairs[pth] = pg

# ---- pass 3: new wiki pages ------------------------------------------------
for pg in wiki_pages:
    if pg in pairs.values() or pg in pages_of: continue
    pth = 'README.md' if pg == 'Home' else pg + '.md'
    L = os.path.join(KB, pth)
    if os.path.isfile(L) and not same(L, os.path.join(W, pg + '.md')):
        CONFLICTS.append(f"{pth} exists locally with different content than new wiki page '{pg}' - reconcile by hand")
        continue
    NEWWIKI.append(f"{pg} -> {pth}")
    if not DRY:
        write(L, read(os.path.join(W, pg + '.md')))
        write(os.path.join(BASE, pg + '.md'), read(os.path.join(W, pg + '.md')))
    pairs[pth] = pg

# ---- sidebar ---------------------------------------------------------------
def sidebar():
    lines = ["<!-- GENERATED by gh-wiki-sync from docs/knowledge/ - edits here are overwritten. Content pages are fine to edit; they sync back to the repo. -->",
             "**[Home](Home)**", ""]
    section_page = {os.path.dirname(p): pairs[p]
                    for p in pairs if os.path.basename(p) == 'README.md' and p != 'README.md'}
    emitted = set()
    def ensure_dir(d):
        if d in ('', '.') or d in emitted: return
        ensure_dir(os.path.dirname(d))
        emitted.add(d)
        depth = d.count('/')
        name = cap(os.path.basename(d)).replace('-', ' ')
        if d in section_page:
            lines.append('  ' * depth + f"- **[{name}]({section_page[d]})**")
        else:
            lines.append('  ' * depth + f"- **{name}**")
    for pth in sorted(pairs):
        if pth == 'README.md': continue
        d = os.path.dirname(pth)
        ensure_dir(d)
        if os.path.basename(pth) == 'README.md': continue   # emitted as its section
        depth = 0 if d in ('', '.') else d.count('/') + 1
        lines.append('  ' * depth + f"- [{title_for(pth)}]({pairs[pth]})")
    return '\n'.join(lines) + '\n'

if not DRY and not PULL:
    sb = sidebar()
    sb_path = os.path.join(W, '_Sidebar.md')
    if not (os.path.isfile(sb_path) and read(sb_path) == sb):
        write(sb_path, sb); wiki_dirty = True

# ---- commit + push + state -------------------------------------------------
if not DRY and not PULL and wiki_dirty:
    run('git', 'add', '-A', cwd=W)
    if subprocess.run(['git', 'diff', '--cached', '--quiet'], cwd=W).returncode != 0:
        run('git', '-c', 'user.name=story-tools docs sync',
            '-c', 'user.email=docs-sync@story-tools.local',
            'commit', '-q', '-m', f'docs sync: {datetime.date.today()}', cwd=W)
        r = subprocess.run(['git', 'push', '-q', 'origin', 'HEAD'], cwd=W,
                           capture_output=True, text=True)
        if r.returncode != 0:
            print(f"error: wiki push failed (token permissions?): {r.stderr.strip()}", file=sys.stderr)
            sys.exit(1)

if not DRY:
    os.makedirs(STATE, exist_ok=True)
    write(MANF, ''.join(f"{pth}\t{pg}\n" for pth, pg in sorted(pairs.items())))

# ---- report ----------------------------------------------------------------
print(f"gh-wiki-sync: {REPO} <-> {KB}" + (' (dry run)' if DRY else ''))
for title, items in (("Pushed:", PUSHED), ("Pulled:", PULLED), ("Merged:", MERGED),
                     ("Renamed pages:", RENAMED),
                     ("New from wiki (file these into a section):", NEWWIKI),
                     ("Deleted pages:", DELETED), ("Pending (not pushed):", PENDING)):
    if items:
        print(title)
        for i in items: print("  " + i)
if CONFLICTS:
    print("CONFLICTS - resolve the markers, then sync again:")
    for c in CONFLICTS: print("  " + c)
    sys.exit(2)
if not any((PUSHED, PULLED, MERGED, RENAMED, NEWWIKI, DELETED, PENDING)):
    print("Everything in sync.")
EOF
