#!/usr/bin/env bash
# Two-way sync between the project knowledge base and docs/knowledge/.
#
# Model: YouTrack is the organizing surface, git is the history and merge
# engine. Content flows BOTH ways per-article, three-way merged against a
# recorded base. Structure (hierarchy, titles) flows DOWN only - rearrange
# in YouTrack and the tree follows. The one exception: a NEW local file's
# location chooses its parent article at birth.
#
# Files and section directories are ID-prefixed like the stories snapshot
# (EVO-A-12_title-slug.md); new local files are renamed to match once their
# article exists.
#
# Usage: yt-sync.sh [KB_DIR] [options]
#   KB_DIR          sync domain (default ./docs/knowledge)
#   --project KEY   project key (default: .agents/config/story-tools.json)
#   --root "Title"  restrict to the subtree of one top-level article;
#                   that article's body becomes KB_DIR/README.md
#   --pull-only     apply KB -> local only; local changes reported as pending
#   --allow-delete  a locally deleted file deletes its article (soft delete)
#   --force         bootstrap over a non-empty KB_DIR with no sync state:
#                   existing files are adopted and pushed as new articles
#   --dry-run       print the full action plan; change nothing anywhere
#   --help          this text
#
# Conflicts get git-style markers and are NEVER pushed until the markers
# are gone - resolve with your usual git tooling, then sync again.
# Exit codes: 0 ok, 1 error, 2 completed but conflicts need resolution.
set -euo pipefail

KB_DIR=""; PROJECT="${YOUTRACK_PROJECT:-}"; ROOT=""; DRY=0; PULL_ONLY=0; ALLOW_DELETE=0; FORCE=0
while [[ $# -gt 0 ]]; do
  case "$1" in
    --project) PROJECT="$2"; shift 2;;
    --root) ROOT="$2"; shift 2;;
    --pull-only) PULL_ONLY=1; shift;;
    --allow-delete) ALLOW_DELETE=1; shift;;
    --force) FORCE=1; shift;;
    --dry-run) DRY=1; shift;;
    --help) awk 'NR>1 && !/^#/{exit} NR>1{sub(/^# ?/,""); print}' "$0"; exit 0;;
    *) KB_DIR="$1"; shift;;
  esac
done
KB_DIR="${KB_DIR:-./docs/knowledge}"

command -v git >/dev/null 2>&1 || { echo "error: git is required (used for three-way merges)" >&2; exit 1; }

# Credentials. A named connection - explicit, or the one this project's pointer
# names - beats whatever is already exported in the shell. The other way round,
# a stale YOUTRACK_URL left over from another instance silently redirects the
# project at the wrong server and every lookup fails for the wrong reason.
# A connection file only wins as a pair: URL and token together, or not at all.
CONN_SOURCE="environment"
candidates=( )
[[ -n "${YOUTRACK_ENV_FILE:-}" ]] && candidates+=("$YOUTRACK_ENV_FILE")
conn="${YOUTRACK_CONNECTION:-${YOUTRACK_PROFILE:-}}"
if [[ -z "$conn" ]]; then
  for pf in "./.agents/config/story-tools.json" "./.agents/youtrack.json"; do
    [[ -f "$pf" ]] && { conn=$(sed -nE 's/.*"connection": *"([^"]+)".*/\1/p' "$pf" | head -1); break; }
  done
fi
[[ -n "$conn" ]] && candidates+=("$HOME/.agents/story-tools/connections/$conn.env")
if [[ -z "${YOUTRACK_URL:-}" ]]; then
  conns=( "$HOME"/.agents/story-tools/connections/*.env )
  [[ ${#conns[@]} -eq 1 && -f "${conns[0]}" ]] && candidates+=("${conns[0]}")
fi
for f in ${candidates[@]+"${candidates[@]}"}; do
  [[ -f "$f" ]] || continue
  prev_url="${YOUTRACK_URL:-}"; prev_token="${YOUTRACK_TOKEN:-}"
  unset YOUTRACK_URL YOUTRACK_HOST YOUTRACK_TOKEN YOUTRACK_API_TOKEN
  # shellcheck disable=SC1090
  source "$f"
  if [[ -n "${YOUTRACK_URL:-${YOUTRACK_HOST:-}}" && -n "${YOUTRACK_TOKEN:-${YOUTRACK_API_TOKEN:-}}" ]]; then
    CONN_SOURCE="$f"; break
  fi
  YOUTRACK_URL="$prev_url"; YOUTRACK_TOKEN="$prev_token"
done
YOUTRACK_URL="${YOUTRACK_URL:-${YOUTRACK_HOST:-}}"
YOUTRACK_TOKEN="${YOUTRACK_TOKEN:-${YOUTRACK_API_TOKEN:-}}"
[[ -z "$YOUTRACK_URL" || -z "$YOUTRACK_TOKEN" ]] && { echo "error: no YouTrack credentials found - run the story-tools installer" >&2; exit 1; }
if [[ -z "$PROJECT" ]]; then
  for pf in "./.agents/config/story-tools.json" "./.agents/youtrack.json"; do
    [[ -f "$pf" ]] && { PROJECT=$(sed -nE 's/.*"project": *"([^"]+)".*/\1/p' "$pf" | head -1); break; }
  done
fi
[[ -z "$PROJECT" ]] && { echo "error: no project key (--project or .agents/config/story-tools.json)" >&2; exit 1; }

export YOUTRACK_URL YOUTRACK_TOKEN PROJECT KB_DIR ROOT DRY PULL_ONLY ALLOW_DELETE FORCE CONN_SOURCE
python3 <<'EOF'
import json, os, re, shutil, subprocess, sys, tempfile, urllib.request, urllib.parse

URL = os.environ['YOUTRACK_URL'].rstrip('/')
TOKEN = os.environ['YOUTRACK_TOKEN']
PROJECT = os.environ['PROJECT']
KB = os.environ['KB_DIR'].rstrip('/')
ROOT = os.environ['ROOT']
DRY = os.environ['DRY'] == '1'
PULL_ONLY = os.environ['PULL_ONLY'] == '1'
ALLOW_DELETE = os.environ['ALLOW_DELETE'] == '1'
FORCE = os.environ['FORCE'] == '1'

SYNC = os.path.join(KB, '.yt-sync')
STATE_FILE = os.path.join(SYNC, 'state.json')
BASE_DIR = os.path.join(SYNC, 'base')

def api(path, payload=None, method=None):
    req = urllib.request.Request(
        URL + path,
        data=json.dumps(payload).encode() if payload is not None else None,
        headers={'Authorization': 'Bearer ' + TOKEN, 'Content-Type': 'application/json'},
        method=method or ('POST' if payload is not None else 'GET'))
    with urllib.request.urlopen(req) as r:
        body = r.read()
        return json.loads(body) if body else None

def canon(s):
    return (s or '').replace('\r\n', '\n').rstrip()

# YouTrack renders `summary` as the article title. A heading in the body
# therefore shows up a SECOND time under it. So the H1 lives on disk (where
# a file needs a title and title_of reads it) and is stripped at the API
# boundary. The recorded base is kept in LOCAL form, so merges compare like
# with like and only these two functions know the difference.
H1_RE = re.compile(r'\A\s*#\s+(.+?)\s*(?:\n+|\Z)')

def to_local(summary, content):
    """Body as it lives on disk: opens with its title as an H1. YouTrack
    owns the title, so a rename there rewrites the local heading."""
    body = canon(content)
    m = H1_RE.match(body)
    if m:
        body = body[m.end():]
    title = (summary or '').strip() or (m.group(1).strip() if m else 'Untitled')
    return canon(f'# {title}\n\n{body}') if body else canon(f'# {title}')

def to_remote(content):
    """Body as YouTrack stores it: no leading H1."""
    body = canon(content)
    m = H1_RE.match(body)
    return canon(body[m.end():]) if m else body

def read_file(p):
    try:
        with open(p, encoding='utf-8') as f:
            return canon(f.read())
    except FileNotFoundError:
        return None

def write_file(p, content):
    os.makedirs(os.path.dirname(p) or '.', exist_ok=True)
    with open(p, 'w', encoding='utf-8') as f:
        f.write(content + '\n' if content else '')

HAS_MARKERS = re.compile(r'^(<{7} |>{7} |={7}$)', re.M)

def merge3(base, local, remote):
    """git merge-file three-way. Returns (merged_text, clean)."""
    with tempfile.TemporaryDirectory() as td:
        pb, pl, pr = (os.path.join(td, n) for n in ('base', 'local', 'remote'))
        for p, c in ((pb, base), (pl, local), (pr, remote)):
            with open(p, 'w', encoding='utf-8') as f:
                f.write((c or '') + '\n')
        r = subprocess.run(
            ['git', 'merge-file', '-p', '-L', 'local', '-L', 'base (last sync)',
             '-L', 'YouTrack', pl, pb, pr],
            capture_output=True, text=True)
        return canon(r.stdout), r.returncode == 0

# ---- remote state -----------------------------------------------------------

projects = api(f'/api/admin/projects?fields=id,shortName&query={urllib.parse.quote(PROJECT)}')
pid = next((p['id'] for p in projects if p.get('shortName') == PROJECT), None)
if not pid:
    # Name the server and where it came from. The usual cause is not a missing
    # project but the wrong host, and neither is visible from the old message.
    visible = [p.get('shortName') for p in api('/api/admin/projects?fields=shortName&$top=200')]
    hint = ', '.join(sorted(s for s in visible if s)) or '(none visible to this token)'
    sys.exit(f'error: project {PROJECT} not found on {URL}\n'
             f'  credentials from: {os.environ.get("CONN_SOURCE", "environment")}\n'
             f'  projects visible there: {hint}')

arts, skip = [], 0
while True:
    batch = api('/api/articles?fields=id,idReadable,summary,content,parentArticle(id),project(shortName)'
                f'&$top=100&$skip={skip}')
    if not batch: break
    arts += [a for a in batch if (a.get('project') or {}).get('shortName') == PROJECT]
    if len(batch) < 100: break
    skip += 100

children = {}
for a in arts:
    children.setdefault((a.get('parentArticle') or {}).get('id'), []).append(a)

root_id = None
if ROOT:
    root = next((a for a in children.get(None, []) if a.get('summary') == ROOT), None)
    if not root:
        sys.exit(f'error: no top-level article "{ROOT}" in the {PROJECT} knowledge base')
    root_id = root['id']
    keep = set()
    def collect(i):
        keep.add(i)
        for c in children.get(i, []): collect(c['id'])
    collect(root_id)
    arts = [a for a in arts if a['id'] in keep]

by_id = {a['id']: a for a in arts}

def slug(text, maxlen=60):
    t = re.sub(r'[^A-Za-z0-9]+', '-', text or '').strip('-').lower()
    return t[:maxlen].rstrip('-') or 'untitled'

# desired path per article: structure flows down from here.
# Names are ID-prefixed like the stories snapshot: EVO-A-12_title-slug[.md]
def art_name(a):
    return f"{a.get('idReadable') or a['id']}_{slug(a.get('summary'))}"

paths = {}
def assign(a, dirpath):
    kids = sorted(children.get(a['id'], []), key=lambda x: (x.get('summary') or '', x['id']))
    if kids:
        d = os.path.join(dirpath, art_name(a))
        paths[a['id']] = os.path.join(d, 'README.md')
        for c in kids: assign(c, d)
    else:
        paths[a['id']] = os.path.join(dirpath, art_name(a) + '.md')

if root_id:
    paths[root_id] = os.path.join(KB, 'README.md')
    for c in sorted(children.get(root_id, []), key=lambda x: (x.get('summary') or '', x['id'])):
        assign(c, KB)
else:
    for a in sorted(children.get(None, []), key=lambda x: (x.get('summary') or '', x['id'])):
        assign(a, KB)

# ---- local state ------------------------------------------------------------

state = {'project': PROJECT, 'articles': {}}
if os.path.isfile(STATE_FILE):
    with open(STATE_FILE, encoding='utf-8') as f:
        state = json.load(f)
smap = state.setdefault('articles', {})

def local_md():
    found = []
    for dp, dns, fns in os.walk(KB):
        dns[:] = [d for d in dns if d != '.yt-sync']
        for fn in fns:
            if fn.endswith('.md'):
                found.append(os.path.join(dp, fn))
    return found

bootstrapping = not os.path.isfile(STATE_FILE)
if bootstrapping and os.path.isdir(KB) and local_md() and not FORCE:
    sys.exit(f'error: {KB} has markdown files but no sync state ({STATE_FILE}).\n'
             'Re-run with --force to adopt them: each file becomes a new article, '
             'pushed up on this first sync.')

report = {k: [] for k in ('Pulled', 'Pushed', 'Merged', 'CONFLICTS', 'Moved', 'Deleted', 'New', 'Notes')}
conflict_count = 0

def base_path(aid): return os.path.join(BASE_DIR, aid + '.md')
def read_base(aid): return read_file(base_path(aid))
def put_base(aid, content):
    if not DRY: write_file(base_path(aid), content)
def drop_base(aid):
    if not DRY and os.path.isfile(base_path(aid)): os.remove(base_path(aid))

# normalize local moves: a file missing from its recorded path whose exact
# content shows up at an unrecorded path moved locally - move it back.
known = {e['path'] for e in smap.values()}
present = set(local_md())
unknown = sorted(present - known)
for aid, e in sorted(smap.items()):
    if e['path'] in present or e.get('orphaned'):
        continue
    b = read_base(aid)
    if b is None: continue
    match = next((u for u in unknown if read_file(u) == b), None)
    if match:
        if not DRY:
            os.makedirs(os.path.dirname(e['path']) or '.', exist_ok=True)
            shutil.move(match, e['path'])
        unknown.remove(match)
        present.discard(match); present.add(e['path'])
        report['Notes'].append(f'normalized local move: {match} -> {e["path"]} (structure flows down; rearrange in YouTrack)')

# ---- per-article sync -------------------------------------------------------

for aid in sorted(paths):
    a = by_id[aid]
    desired = paths[aid]
    remote = to_local(a.get('summary'), a.get('content'))
    e = smap.get(aid)

    if e is None:  # new in the KB
        if read_file(desired) is not None and desired not in known:
            # bootstrap collision: an adopted local file occupies this path
            report['Notes'].append(f'path collision at {desired}; keeping KB version, local copy at {desired}.local.md')
            if not DRY: shutil.move(desired, desired + '.local.md')
            unknown = [u for u in unknown if u != desired]
        if not DRY: write_file(desired, remote)
        put_base(aid, remote)
        smap[aid] = {'path': desired, 'summary': a.get('summary'), 'idReadable': a.get('idReadable')}
        report['Pulled'].append(f'{desired}  (new: "{a.get("summary")}")')
        continue

    e.pop('orphaned', None)  # article is back; clear any orphan flag
    cur = e['path']
    if cur != desired:  # KB rename / move
        if not DRY and os.path.isfile(cur):
            os.makedirs(os.path.dirname(desired) or '.', exist_ok=True)
            shutil.move(cur, desired)
        report['Moved'].append(f'{cur} -> {desired}')
        e['path'] = desired
    e['summary'] = a.get('summary'); e['idReadable'] = a.get('idReadable')

    local = read_file(desired if not DRY else (desired if os.path.isfile(desired) else cur))
    base = read_base(aid)

    if local is None:  # locally deleted
        if ALLOW_DELETE and not PULL_ONLY:
            if not DRY: api(f'/api/articles/{aid}', method='DELETE')
            drop_base(aid); smap.pop(aid, None)
            report['Deleted'].append(f'article "{a.get("summary")}" ({a.get("idReadable")}) - deleted locally, removed from KB')
        else:
            report['Notes'].append(f'{desired} deleted locally but "{a.get("summary")}" still in KB - restore it, or re-run with --allow-delete')
        continue

    if HAS_MARKERS.search(local):
        conflict_count += 1
        report['CONFLICTS'].append(f'{desired}  (unresolved markers from a previous sync - resolve, then re-run)')
        continue

    lc, rc = local != (base or ''), remote != (base or '')
    if not lc and not rc:
        continue
    if lc and not rc:
        if PULL_ONLY:
            report['Notes'].append(f'{desired} has local edits (pending push; run without --pull-only)')
        else:
            if not DRY: api(f'/api/articles/{aid}?fields=id', {'content': to_remote(local)})
            put_base(aid, local)
            report['Pushed'].append(desired)
    elif rc and not lc:
        if not DRY: write_file(desired, remote)
        put_base(aid, remote)
        report['Pulled'].append(desired)
    else:
        merged, clean = merge3(base or '', local, remote)
        if clean:
            if PULL_ONLY:
                report['Notes'].append(f'{desired} merges cleanly with KB edits (pending; run without --pull-only)')
                continue
            if not DRY:
                write_file(desired, merged)
                api(f'/api/articles/{aid}?fields=id', {'content': to_remote(merged)})
            put_base(aid, merged)
            report['Merged'].append(desired)
        else:
            conflict_count += 1
            if not DRY: write_file(desired, merged)
            put_base(aid, remote)  # after resolution, local-vs-base drives the push
            report['CONFLICTS'].append(desired)

# ---- articles gone from the KB ---------------------------------------------

for aid in sorted(set(smap) - set(paths)):
    e = smap[aid]
    local = read_file(e['path'])
    base = read_base(aid)
    if local is None or local == (base or ''):
        if local is not None and not DRY:
            os.remove(e['path'])
        drop_base(aid); smap.pop(aid, None)
        report['Deleted'].append(f'{e["path"]}  ("{e.get("summary")}" removed in YouTrack)')
    else:
        e['orphaned'] = True
        report['CONFLICTS'].append(f'{e["path"]}  (edited locally but "{e.get("summary")}" was deleted in YouTrack - '
                                   'keep by moving/renaming it (becomes a new article) or delete the file)')
        conflict_count += 1

# ---- new local files -> new articles ---------------------------------------

def title_of(path, content):
    m = re.search(r'^#\s+(.+)$', content or '', re.M)
    if m: return m.group(1).strip()
    stem = os.path.splitext(os.path.basename(path))[0]
    if stem == 'README': stem = os.path.basename(os.path.dirname(path))
    return re.sub(r'[-_]+', ' ', stem).strip().title() or 'Untitled'

dir_article = {os.path.dirname(e['path']): aid for aid, e in smap.items()
               if os.path.basename(e['path']) == 'README.md' and not e.get('orphaned')}
if root_id: dir_article[KB] = root_id

orphan_paths = {e['path'] for e in smap.values() if e.get('orphaned')}

def natkey(text):
    """Natural sort: 2-plan before 10-plan, which plain lexical gets wrong.

    Creation order decides article IDs, and the ID becomes the filename
    once the article exists - so the order files are pushed in is the
    order the KB keeps forever. A numeric prefix on a new doc is how you
    say 'these belong in this sequence'; honour it."""
    out = []
    for tok in re.split(r'(\d+)', text):
        if not tok:
            continue
        out.append((0, int(tok), '') if tok.isdigit() else (1, 0, tok.lower()))
    return tuple(out)

# shallow before deep (a section article must exist before its children),
# README first within a directory, then natural order of the path.
new_files = sorted((u for u in unknown if u not in orphan_paths),
                   key=lambda p: (p.count(os.sep),
                                  os.path.basename(p) != 'README.md',
                                  natkey(p)))
created_ids = []

for nf in new_files:
    content = read_file(nf) or ''
    if HAS_MARKERS.search(content):
        conflict_count += 1
        report['CONFLICTS'].append(f'{nf}  (new file contains conflict markers; not pushed)')
        continue
    d = os.path.dirname(nf)
    # walk up to the nearest directory that has an owning article
    chain = []
    dd = d
    while dd != KB and dd not in dir_article:
        chain.append(dd); dd = os.path.dirname(dd)
    parent = dir_article.get(dd)  # None => top-level article
    if PULL_ONLY or DRY:
        report['New'].append(f'{nf} -> article "{title_of(nf, content)}"' + (' (pending)' if PULL_ONLY else ''))
        continue
    for cd in reversed(chain):  # create stub section articles for new dirs
        rp = os.path.join(cd, 'README.md')
        stub_title = title_of(rp, read_file(rp))
        payload = {'summary': stub_title, 'content': to_remote(read_file(rp) or ''), 'project': {'id': pid}}
        if parent: payload['parentArticle'] = {'id': parent}
        made = api('/api/articles?fields=id,idReadable', payload)
        smap[made['id']] = {'path': rp, 'summary': stub_title, 'idReadable': made.get('idReadable')}
        put_base(made['id'], read_file(rp) or '')
        if read_file(rp) is None: write_file(rp, '')
        dir_article[cd] = made['id']
        parent = made['id']
        created_ids.append(made['id'])
        report['New'].append(f'{rp} -> section "{stub_title}"')
    if os.path.basename(nf) == 'README.md' and d in dir_article:
        continue  # created above as the section article
    title = title_of(nf, content)
    payload = {'summary': title, 'content': to_remote(content), 'project': {'id': pid}}
    if parent: payload['parentArticle'] = {'id': parent}
    made = api('/api/articles?fields=id,idReadable', payload)
    smap[made['id']] = {'path': nf, 'summary': title, 'idReadable': made.get('idReadable')}
    put_base(made['id'], content)
    created_ids.append(made['id'])
    report['New'].append(f'{nf} -> article "{title}" ({made.get("idReadable")})')

# newborn articles get their ID stamped into the file/dir name (EVO-A-12_...)
def settle_name(aid):
    e = smap.get(aid)
    if not e: return
    p = e['path']
    fake = {'id': aid, 'idReadable': e.get('idReadable'), 'summary': e.get('summary')}
    if os.path.basename(p) == 'README.md':
        old_dir = os.path.dirname(p)
        if old_dir == KB: return
        new_dir = os.path.join(os.path.dirname(old_dir), art_name(fake))
        if new_dir == old_dir: return
        if not DRY: shutil.move(old_dir, new_dir)
        for e2 in smap.values():
            if e2['path'] == old_dir or e2['path'].startswith(old_dir + os.sep):
                e2['path'] = new_dir + e2['path'][len(old_dir):]
        report['Notes'].append(f'renamed {old_dir}/ -> {new_dir}/ (ID prefix)')
    else:
        newp = os.path.join(os.path.dirname(p), art_name(fake) + '.md')
        if newp == p: return
        if not DRY: shutil.move(p, newp)
        e['path'] = newp
        report['Notes'].append(f'renamed {p} -> {newp} (ID prefix)')

for aid in created_ids:  # shallow-first: section dirs precede their leaves
    settle_name(aid)

# ---- finish -----------------------------------------------------------------

if not DRY:
    # prune dirs emptied by moves/deletes
    for dp, dns, fns in os.walk(KB, topdown=False):
        if dp != KB and dp != SYNC and not dns and not fns:
            os.rmdir(dp)
    os.makedirs(SYNC, exist_ok=True)
    with open(STATE_FILE, 'w', encoding='utf-8') as f:
        json.dump(state, f, indent=2, sort_keys=True)

prefix = '(dry run) ' if DRY else ''
total = sum(len(v) for v in report.values())
if total == 0:
    print(f'{prefix}In sync: {len(paths)} articles, nothing to do.')
else:
    for section, lines in report.items():
        if lines:
            print(f'{prefix}{section}:')
            for l in lines: print(f'  {l}')
    if report['Moved']:
        print(f'{prefix}Reminder: entries Moved above - update any docs/ indexes or instruction files that reference old paths.')
sys.exit(2 if conflict_count else 0)
EOF
