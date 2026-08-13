#!/usr/bin/env bash
# Pull YouTrack stories into a local markdown snapshot.
#
# Usage: yt-pull.sh [PROJECT_KEY] [OUT_DIR] [--dimensions-only]
#   --dimensions-only   refresh docs/dimensions.md only (no issue snapshot)
#   PROJECT_KEY  defaults to $YOUTRACK_PROJECT from the selected profile
#   OUT_DIR      defaults to ./docs/stories
#
# Connection selection: $YOUTRACK_CONNECTION, else the machine's only one,
# else the legacy env file. The snapshot is one .md per issue plus an
# INDEX.md; a dimensions.md (the project's field values - Subsystem,
# Type, Priority, Stage, Fix versions - plus existing topical tags, so
# offline/fallback agents can pick from real values instead of guessing)
# is written to the docs root, beside OUT_DIR;
# files are GENERATED - YouTrack stays the source of truth.
set -euo pipefail

# A named connection - explicit, or the one this project's pointer names - beats
# whatever is already exported in the shell. The other way round, a stale
# YOUTRACK_URL left over from another instance silently redirects the project at
# the wrong server. A connection file only wins as a pair: URL and token, or
# neither.
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
[[ -z "$YOUTRACK_URL" || -z "$YOUTRACK_TOKEN" ]] && { echo "error: no YouTrack credentials found" >&2; exit 1; }

DIMONLY=0; ARGS=()
for a in "$@"; do [[ "$a" == "--dimensions-only" ]] && DIMONLY=1 || ARGS+=("$a"); done
set -- "${ARGS[@]:-}"

PROJECT="${1:-${YOUTRACK_PROJECT:-}}"
[[ -z "$PROJECT" ]] && { echo "usage: yt-pull.sh <PROJECT_KEY> [OUT_DIR]" >&2; exit 1; }
OUT="${2:-./docs/stories}"
[[ "$DIMONLY" == 1 ]] || mkdir -p "$OUT"

FIELDS="idReadable,summary,description,resolved,tags(name),customFields(name,value(name)),links(direction,linkType(name),issues(idReadable))"
TOP=100; SKIP=0; TOTAL=0
if [[ "$DIMONLY" != 1 ]]; then
: > /tmp/yt-pull-issues.jsonl

while :; do
  BATCH=$(curl -sS -G "$YOUTRACK_URL/api/issues" \
    -H "Authorization: Bearer $YOUTRACK_TOKEN" \
    --data-urlencode "query=project: {$PROJECT} sort by: {issue id} asc" \
    --data-urlencode "fields=$FIELDS" \
    --data-urlencode "\$top=$TOP" \
    --data-urlencode "\$skip=$SKIP")
  COUNT=$(printf '%s' "$BATCH" | python3 -c "import json,sys; print(len(json.load(sys.stdin)))")
  [[ "$COUNT" == "0" ]] && break
  printf '%s' "$BATCH" | python3 -c "
import json, sys
for it in json.load(sys.stdin):
    print(json.dumps(it))" >> /tmp/yt-pull-issues.jsonl
  TOTAL=$((TOTAL + COUNT)); SKIP=$((SKIP + TOP))
  [[ "$COUNT" -lt "$TOP" ]] && break
done

OUT="$OUT" URL="$YOUTRACK_URL" PROJECT="$PROJECT" python3 <<'EOF'
import json, os, re, datetime

out = os.environ['OUT']; url = os.environ['URL'].rstrip('/'); project = os.environ['PROJECT']
issues = [json.loads(l) for l in open('/tmp/yt-pull-issues.jsonl') if l.strip()]

def field(it, name):
    for f in it.get('customFields') or []:
        if f.get('name') == name:
            v = f.get('value')
            if isinstance(v, dict): return v.get('name')
            if isinstance(v, list): return ', '.join(x.get('name','') for x in v)
            return v
    return None

def slug(text, maxlen=60):
    t = re.sub(r'[^A-Za-z0-9]+', '-', text or '').strip('-').lower()
    return t[:maxlen].rstrip('-') or 'untitled'

index = []
for it in issues:
    iid = it['idReadable']
    state = next((v for v in (field(it, n) for n in ('Stage', 'Kanban State', 'Status', 'State')) if v), '')
    subsystem = field(it, 'Subsystem') or ''
    tags = ', '.join(t['name'] for t in it.get('tags') or [])
    links = []
    for l in it.get('links') or []:
        for li in l.get('issues') or []:
            links.append(f"{l.get('linkType',{}).get('name','link')} {li['idReadable']}")
    body = it.get('description') or '_(no description)_'
    # human-findable filename: EVO-2_title-of-the-story.md; drop stale
    # copies of this issue from earlier pulls (old name or changed title)
    fname = f"{iid}_{slug(it.get('summary'))}.md"
    for stale in os.listdir(out):
        if (stale == iid + '.md' or stale.startswith(iid + '_')) and stale != fname:
            os.remove(os.path.join(out, stale))
    with open(os.path.join(out, fname), 'w') as f:
        f.write(f"""---
id: {iid}
summary: "{(it.get('summary') or '').replace('"', "'")}"
state: "{state}"
subsystem: "{subsystem}"
resolved: {str(bool(it.get('resolved'))).lower()}
tags: "{tags}"
links: "{'; '.join(links)}"
url: {url}/issue/{iid}
---
<!-- GENERATED snapshot ({datetime.date.today()}): do not edit - YouTrack is the source of truth. Re-run scripts/yt-pull.sh to refresh. -->

# {iid}: {it.get('summary','')}

{body}
""")
    index.append((iid, fname, it.get('summary',''), state, subsystem, bool(it.get('resolved'))))

with open(os.path.join(out, 'INDEX.md'), 'w') as f:
    f.write(f"# YouTrack snapshot: project {project} ({datetime.date.today()})\n\n")
    f.write("GENERATED - do not edit. Re-run scripts/yt-pull.sh to refresh.\n\n")
    f.write("| ID | Summary | Subsystem | State | Resolved |\n|---|---|---|---|---|\n")
    for iid, fn, s, st, sub, r in index:
        f.write(f"| [{iid}]({fn}) | {s} | {sub} | {st} | {'yes' if r else ''} |\n")

print(f"Wrote {len(issues)} issues + INDEX.md to {out}")
EOF

fi

# dimensions.md - project field values + every usable tag, for offline
# picking. Lives at the docs ROOT (parent of the stories dir), with the
# agent's other indexes - git-native, never synced to the KB. Written on
# every run, including --dimensions-only.
DIM_DIR="$(dirname "$OUT")"
DIM_DIR="$DIM_DIR" URL="$YOUTRACK_URL" TOKEN="$YOUTRACK_TOKEN" PROJECT="$PROJECT" python3 <<'EOF'
import json, os, datetime, urllib.request, urllib.parse

URL = os.environ['URL'].rstrip('/'); TOKEN = os.environ['TOKEN']
PROJECT = os.environ['PROJECT']; out = os.environ['DIM_DIR']

def api(path):
    req = urllib.request.Request(URL + path, headers={'Authorization': 'Bearer ' + TOKEN})
    with urllib.request.urlopen(req) as r:
        return json.loads(r.read())

RESERVED = {'needs-gherkin', 'discovered', 'ready-for-agent', 'ready-for-human',
            'needs-triage', 'needs-info', 'triaged', 'bug', 'enhancement',
            'wontfix', 'Star'}

projects = api(f'/api/admin/projects?fields=id,shortName&query={urllib.parse.quote(PROJECT)}')
pid = next((p['id'] for p in projects if p.get('shortName') == PROJECT), None)
lines = [f'# Project dimensions: {PROJECT} ({datetime.date.today()})', '',
         'GENERATED by yt-pull - do not edit. Pick from these values; adding a',
         'new one is a deliberate act (see the triage skill), never a typo.', '']
if pid:
    fields = api(f'/api/admin/projects/{pid}/customFields'
                 '?fields=field(name),bundle(values(name,archived,released))')
    for f in fields:
        name = (f.get('field') or {}).get('name')
        vals = [v for v in ((f.get('bundle') or {}).get('values') or [])
                if not v.get('archived')]
        if not (name and vals):
            continue
        # version bundles carry `released`: current/upcoming is what new
        # work targets, shipped ones are history and should not be picked
        # by accident.
        current = [v['name'] for v in vals if not v.get('released')]
        shipped = [v['name'] for v in vals if v.get('released')]
        if shipped:
            lines.append(f'## {name} (current and upcoming)')
            lines += ([f'- {v}' for v in current] or
                      ['- _(none open - a new one is a project-settings change)_'])
            lines += ['', f'Already released - history, do not target new work: '
                          + ', '.join(shipped[-12:]) + ('' if len(shipped) <= 12
                          else f' (+{len(shipped) - 12} older)'), '']
        else:
            lines.append(f'## {name}')
            lines += [f'- {v["name"]}' for v in vals] + ['']
# Tags: list EVERY usable tag, workflow ones included. Filtering the
# machinery out left agents unable to see that needs-triage exists, so
# they invented substitutes.
WORKFLOW = [
    ('needs-triage',    'awaiting triage - the inbox'),
    ('triaged',         'has been dispositioned; never removed once earned'),
    ('ready-for-agent', 'an agent can pick this up'),
    ('ready-for-human', 'needs a person - judgment, access, or design'),
    ('needs-info',      'waiting on the reporter'),
    ('wontfix',         'closed with the reason recorded'),
    ('bug',             'category: something is broken'),
    ('enhancement',     'category: new feature or improvement'),
    ('discovered',      'born from other work, not yet triaged'),
    ('needs-gherkin',   'completion requires a QA section'),
]
tags = api('/api/tags?fields=name&$top=500')
present = {t['name'].lower() for t in tags if t.get('name')}
reserved_lower = {r.lower() for r in RESERVED}

lines.append('## Workflow tags (machinery - apply per the triage state machine)')
lines.append('')
missing = []
for name, meaning in WORKFLOW:
    if name.lower() in present:
        lines.append(f'- `{name}` - {meaning}')
    else:
        missing.append(name)
if missing:
    lines += ['', 'Not on this server yet (run the installer to create them): '
                  + ', '.join(f'`{m}`' for m in missing)]
lines += ['', 'These are never topical and never inherited by discovered work.', '']

topical = sorted({t['name'] for t in tags
                  if t.get('name') and t['name'].lower() not in reserved_lower})
lines.append('## Topical tags (reuse before inventing)')
if topical:
    lines += [f'- {t}' for t in topical] + ['']
else:
    lines += ['_(none yet)_', '']
open(os.path.join(out, 'dimensions.md'), 'w').write('\n'.join(lines))
print(f'Wrote dimensions.md ({len(WORKFLOW) - len(missing)} workflow + '
      f'{len(topical)} topical tags)')
EOF
