#!/usr/bin/env bash
# Personal work log - the developer's working time, by project.
#
# EXPERIMENTAL. Format and flags may change.
#
# The ledger is plain markdown you are meant to edit by hand. This script
# exists so that several agents appending at once cannot corrupt it, and so
# durations parse consistently. Anything it writes, you can rewrite.
#
#   worklog.sh add <duration> <project> [summary...]   record time
#       [--date YYYY-MM-DD] [--from HH:MM] [--to HH:MM]
#   worklog.sh start <project> [note...]               open a span
#   worklog.sh stop [project] [--summary "..."]        close it, compute
#   worklog.sh status                                  open spans + today
#   worklog.sh report [--month YYYY-MM] [--day D]      totals by project
#       [--project X] [--unsent] [--csv]
#   worklog.sh mark-sent <adapter> [--month YYYY-MM] [--project X]
#   worklog.sh path                                    print the ledger path
#
# Durations: 3h  90m  2h15m  1.5h  45  (bare number = minutes)
# Ledger:    $WORKLOG_DIR, else worklog.json "path", else ~/.agents/worklog
set -euo pipefail

CMD="${1:-}"; [[ $# -gt 0 ]] && shift || true
[[ -z "$CMD" || "$CMD" == "--help" || "$CMD" == "-h" ]] && {
  awk 'NR>1 && !/^#/{exit} NR>1{sub(/^# ?/,""); print}' "$0"; exit 0; }

CONFIG="$HOME/.agents/story-tools/worklog.json"
DIR="${WORKLOG_DIR:-}"
if [[ -z "$DIR" && -f "$CONFIG" ]]; then
  DIR=$(sed -nE 's/.*"path": *"([^"]+)".*/\1/p' "$CONFIG" | head -1)
fi
DIR="${DIR:-$HOME/.agents/worklog}"
DIR="${DIR/#\~/$HOME}"
mkdir -p "$DIR"

export DIR CONFIG CMD
python3 - "$@" <<'PYEOF'
import json, os, re, sys, time, datetime

DIR = os.environ['DIR']
CONFIG = os.environ['CONFIG']
CMD = os.environ['CMD']
OPEN = os.path.join(DIR, 'open.json')
LOCK = os.path.join(DIR, '.lock')

def die(msg, code=1):
    print(f'error: {msg}', file=sys.stderr); sys.exit(code)

def cfg():
    try:
        with open(CONFIG, encoding='utf-8') as f: return json.load(f)
    except Exception:
        return {}

# ---- locking ---------------------------------------------------------------
# mkdir is atomic on every filesystem we care about, which is the whole point:
# several project agents may append in the same second.

class Lock:
    def __enter__(self):
        for _ in range(200):
            try:
                os.mkdir(LOCK); return self
            except FileExistsError:
                try:
                    if time.time() - os.path.getmtime(LOCK) > 30:
                        os.rmdir(LOCK); continue      # stale
                except OSError:
                    pass
                time.sleep(0.05)
        die('could not acquire the ledger lock; remove ' + LOCK + ' if stale')
    def __exit__(self, *a):
        try: os.rmdir(LOCK)
        except OSError: pass

# ---- durations -------------------------------------------------------------

DUR = re.compile(r'^(?:(\d+(?:\.\d+)?)h)?(?:(\d+)m)?$', re.I)

def parse_duration(s):
    s = s.strip().lower()
    if re.fullmatch(r'\d+', s):
        return int(s)
    m = DUR.match(s)
    if not m or not any(m.groups()):
        die(f'cannot read duration "{s}" - try 3h, 90m, 2h15m, 1.5h')
    h = float(m.group(1) or 0); mm = int(m.group(2) or 0)
    total = int(round(h * 60)) + mm
    if total <= 0:
        die('duration must be greater than zero')
    return total

def fmt_duration(mins):
    h, m = divmod(int(mins), 60)
    if h and m: return f'{h}h{m:02d}m'
    if h:       return f'{h}h'
    return f'{m}m'

def round_to(mins, step):
    if step <= 1: return mins
    return max(step, int(round(mins / float(step))) * step)

# ---- the ledger ------------------------------------------------------------
# One file per month. Days are `## YYYY-MM-DD`, entries are list items:
#
#   - [09:15-11:30 ]2h15m  acme-api  summary text  [sent: freshbooks]
#
# Everything is a plain markdown list, so hand editing never breaks parsing.

ENTRY = re.compile(
    r'^-\s+'
    r'(?:(?P<span>\d{2}:\d{2}-\d{2}:\d{2})\s+)?'
    r'(?P<dur>\d+(?:\.\d+)?h(?:\d+m)?|\d+m|\d+h)\s+'
    r'(?P<project>\S+)\s*'
    r'(?P<summary>.*?)'
    r'(?:\s*\[sent:\s*(?P<sent>[^\]]*)\])?\s*$')

def month_path(day):
    return os.path.join(DIR, day[:7] + '.md')

def read_month(path):
    try:
        with open(path, encoding='utf-8') as f: return f.read()
    except FileNotFoundError:
        return ''

def write_atomic(path, text):
    tmp = path + '.tmp'
    with open(tmp, 'w', encoding='utf-8') as f:
        f.write(text if text.endswith('\n') else text + '\n')
    os.replace(tmp, path)

def split_days(text):
    """-> (header_lines, [(day, [lines])]) preserving anything unrecognised."""
    head, days, cur = [], [], None
    for line in text.splitlines():
        m = re.match(r'^##\s+(\d{4}-\d{2}-\d{2})\s*$', line)
        if m:
            cur = (m.group(1), []); days.append(cur); continue
        (cur[1] if cur else head).append(line)
    return head, days

def render(head, days, month):
    while head and not head[-1].strip(): head.pop()
    if not head: head = [f'# {month}', '',
                         '<!-- personal work log; edit freely -->']
    out = list(head) + ['']
    for day, lines in sorted(days, key=lambda d: d[0]):
        body = list(lines)
        # Trim both ends, or every rewrite adds another blank line and the
        # file grows whitespace forever.
        while body and not body[0].strip(): body.pop(0)
        while body and not body[-1].strip(): body.pop()
        out += [f'## {day}', ''] + body + ['']
    return '\n'.join(out).rstrip() + '\n'

def add_entry(day, line):
    path = month_path(day)
    head, days = split_days(read_month(path))
    for d, lines in days:
        if d == day:
            lines.append(line); break
    else:
        days.append((day, [line]))
    write_atomic(path, render(head, days, day[:7]))
    return path

def entries_for(day):
    _, days = split_days(read_month(month_path(day)))
    for d, lines in days:
        if d == day:
            return [m.groupdict() for m in
                    (ENTRY.match(l) for l in lines) if m]
    return []

# ---- open spans ------------------------------------------------------------
# A list, not a single span: overlap is permitted. Sorting out whether two
# projects genuinely shared an hour is the developer's call, not this script's.

def read_open():
    try:
        with open(OPEN, encoding='utf-8') as f: return json.load(f).get('spans', [])
    except Exception:
        return []

def write_open(spans):
    write_atomic(OPEN, json.dumps({'spans': spans}, indent=2))

def now():
    return datetime.datetime.now().astimezone()

# ---- commands --------------------------------------------------------------

def take_opt(args, name, has_value=True):
    if name in args:
        i = args.index(name)
        if has_value:
            if i + 1 >= len(args): die(f'{name} needs a value')
            v = args[i + 1]; del args[i:i + 2]; return v
        del args[i:i + 1]; return True
    return None

def cmd_add(args):
    date = take_opt(args, '--date') or now().strftime('%Y-%m-%d')
    frm  = take_opt(args, '--from')
    to   = take_opt(args, '--to')
    if len(args) < 2:
        die('usage: worklog.sh add <duration> <project> [summary...]')
    mins = parse_duration(args[0])
    project = args[1]
    summary = ' '.join(args[2:]).strip()
    span = f'{frm}-{to} ' if frm and to else ''
    line = f'- {span}{fmt_duration(mins)}  {project}'
    if summary: line += f'  {summary}'
    path = add_entry(date, line)
    print(f'{date}  {fmt_duration(mins)}  {project}' + (f'  {summary}' if summary else ''))
    print(f'  -> {path}')

def cmd_start(args):
    if not args: die('usage: worklog.sh start <project> [note...]')
    spans = read_open()
    project = args[0]; note = ' '.join(args[1:]).strip()
    # Starting elsewhere does NOT close anything. Overlap is allowed on
    # purpose; only the developer can say how shared time should land.
    spans.append({'project': project, 'note': note,
                  'start': now().isoformat(timespec='seconds')})
    write_open(spans)
    print(f'started {project}' + (f' - {note}' if note else ''))
    others = [s for s in spans[:-1]]
    if others:
        print('  also open: ' + ', '.join(s['project'] for s in others))

def cmd_stop(args):
    summary = take_opt(args, '--summary') or ''
    spans = read_open()
    if not spans: die('no open span')
    if args:
        picked = [s for s in spans if s['project'] == args[0]]
        if not picked: die(f'no open span for {args[0]}')
        s = picked[-1]
    elif len(spans) > 1:
        die('several spans open (' + ', '.join(x['project'] for x in spans) +
            ') - name one: worklog.sh stop <project>')
    else:
        s = spans[0]
    start = datetime.datetime.fromisoformat(s['start'])
    end = now()
    mins = (end - start).total_seconds() / 60.0
    if mins <= 0: die('the span ends before it starts; fix it by hand')
    step = int(cfg().get('rounding', 15))
    rounded = round_to(mins, step)
    if mins > 16 * 60:
        # Never quietly bank an overnight span - ask.
        print(f'that span is {fmt_duration(mins)} ({s["project"]}, opened '
              f'{start.strftime("%Y-%m-%d %H:%M")}).', file=sys.stderr)
        print('too long to record without checking. Say what it actually was:',
              file=sys.stderr)
        print(f'  worklog.sh add <duration> {s["project"]} "<summary>"',
              file=sys.stderr)
        print('  worklog.sh stop --discard   (drop the span, record nothing)',
              file=sys.stderr)
        sys.exit(2)
    spans.remove(s); write_open(spans)
    text = summary or s.get('note', '')
    line = (f'- {start.strftime("%H:%M")}-{end.strftime("%H:%M")}  '
            f'{fmt_duration(rounded)}  {s["project"]}')
    if text: line += f'  {text}'
    day = start.strftime('%Y-%m-%d')
    add_entry(day, line)
    print(f'{day}  {fmt_duration(rounded)}  {s["project"]}' +
          (f'  {text}' if text else ''))

def cmd_discard(args):
    spans = read_open()
    if not spans: die('no open span')
    if args:
        spans = [s for s in spans if s['project'] != args[0]]
    else:
        spans = spans[:-1]
    write_open(spans); print('discarded')

def cmd_status(args):
    spans = read_open()
    if spans:
        for s in spans:
            start = datetime.datetime.fromisoformat(s['start'])
            el = (now() - start).total_seconds() / 60.0
            print(f'open  {s["project"]:<16} since {start.strftime("%H:%M")} '
                  f'({fmt_duration(el)})' + (f'  {s["note"]}' if s.get('note') else ''))
    else:
        print('no open spans')
    day = now().strftime('%Y-%m-%d')
    rows = entries_for(day)
    total = sum(parse_duration(r['dur']) for r in rows)
    print(f'\n{day}: {fmt_duration(total) if total else "nothing logged"}')
    for r in rows:
        print(f'  {r["dur"]:>7}  {r["project"]:<16} {r["summary"]}')

def cmd_report(args):
    month = take_opt(args, '--month')
    day = take_opt(args, '--day')
    only = take_opt(args, '--project')
    unsent = take_opt(args, '--unsent', has_value=False)
    csv = take_opt(args, '--csv', has_value=False)
    month = month or (day[:7] if day else now().strftime('%Y-%m'))
    _, days = split_days(read_month(os.path.join(DIR, month + '.md')))
    rows = []
    for d, lines in sorted(days, key=lambda x: x[0]):
        if day and d != day: continue
        for line in lines:
            m = ENTRY.match(line)
            if not m: continue
            g = m.groupdict()
            if only and g['project'] != only: continue
            if unsent and (g.get('sent') or '').strip(): continue
            rows.append((d, parse_duration(g['dur']), g['project'],
                         g['summary'].strip()))
    if csv:
        print('date,minutes,project,summary')
        for d, mins, p, s in rows:
            print(f'{d},{mins},{p},"' + s.replace('"', '""') + '"')
        return
    by_proj = {}
    for d, mins, p, s in rows: by_proj[p] = by_proj.get(p, 0) + mins
    width = max([len(p) for p in by_proj] + [7])
    for p, mins in sorted(by_proj.items(), key=lambda kv: -kv[1]):
        print(f'{p:<{width}}  {fmt_duration(mins):>8}')
    if by_proj:
        print(f'{"total":<{width}}  {fmt_duration(sum(by_proj.values())):>8}')
    else:
        print('nothing recorded' + (' (unsent only)' if unsent else ''))

def cmd_mark_sent(args):
    if not args: die('usage: worklog.sh mark-sent <adapter> [--month M] [--project P]')
    adapter = args[0]; rest = args[1:]
    month = take_opt(rest, '--month') or now().strftime('%Y-%m')
    only = take_opt(rest, '--project')
    path = os.path.join(DIR, month + '.md')
    head, days = split_days(read_month(path))
    n = 0
    for d, lines in days:
        for i, line in enumerate(lines):
            m = ENTRY.match(line)
            if not m: continue
            g = m.groupdict()
            if only and g['project'] != only: continue
            sent = [x.strip() for x in (g.get('sent') or '').split(',') if x.strip()]
            if adapter in sent: continue
            sent.append(adapter)
            base = line.split('[sent:')[0].rstrip()
            lines[i] = f'{base}  [sent: {", ".join(sent)}]'
            n += 1
    write_atomic(path, render(head, days, month))
    print(f'marked {n} entr' + ('y' if n == 1 else 'ies') + f' sent to {adapter}')

def cmd_path(args):
    print(os.path.join(DIR, now().strftime('%Y-%m') + '.md'))

args = sys.argv[1:]
if CMD == 'stop' and '--discard' in args:
    args.remove('--discard'); CMD = 'discard'

handlers = {'add': cmd_add, 'start': cmd_start, 'stop': cmd_stop,
            'discard': cmd_discard, 'status': cmd_status, 'report': cmd_report,
            'mark-sent': cmd_mark_sent, 'path': cmd_path}
if CMD not in handlers:
    die(f'unknown command "{CMD}" - try --help')

if CMD in ('status', 'report', 'path'):
    handlers[CMD](args)
else:
    with Lock():
        handlers[CMD](args)
PYEOF
