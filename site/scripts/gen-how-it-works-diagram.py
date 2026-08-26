#!/usr/bin/env python3
"""
Regenerate the "how it works" diagram. Run from the repository root:

    python3 site/scripts/gen-how-it-works-diagram.py

Emits two SVGs from one structure so the site copy and the downloadable file cannot drift:
  site/public/how-it-works.svg   baked colours plus prefers-color-scheme, so it still themes
                                 correctly when saved out and opened on its own
  /tmp/hiw_inline.svg            theming driven by Starlight's data-theme toggle; paste into
                                 site/src/content/docs/how-it-works.md, replacing the <svg> block

Edit the palette or BOXES here, never the generated SVG.
"""
RAMPS = {
 #            light: fill      stroke     title      sub          dark: fill     stroke     title      sub
 "gray":   (("#F1F3F5","#A8B0B8","#2B3238","#5A646E"), ("#242A30","#4A545E","#E6EDF3","#A5B0BA")),
 "blue":   (("#E6F1FB","#5B8DC4","#0C447C","#2E6BA8"), ("#10304F","#4B7FB5","#CFE3F7","#9CC2E6")),
 "amber":  (("#FDF3E3","#D9A441","#7A4E0B","#A5701A"), ("#3A2B10","#B98B2E","#F6E2BC","#DCC08A")),
 "teal":   (("#E3F4F1","#4C9E93","#0F4F49","#2A776E"), ("#103733","#3F8E83","#C7EAE4","#93CFC6")),
 "purple": (("#EFEAFA","#8B76C4","#40317A","#61509E"), ("#241C3D","#7A66B4","#DDD3F5","#B7A7E4")),
 "green":  (("#E8F4EA","#5C9A68","#1E4F2B","#3B7448"), ("#14301B","#4C8459","#CDE8D3","#9CCBA6")),
}
NEUTRAL = (("#7D8590","#B6BFC9","#57606A"), ("#7D8590","#3D444D","#9AA4AE"))  # edge, region, plain sub

BOXES = [
 ("blue",  140, 50, 400, 54, "this project resolves its dependencies", "declared by default, transitive opt-in"),
 ("gray",   60,186, 126, 48, "harvest",   "source or class"),
 ("gray",  206,186, 126, 48, "parse",     "dedupe"),
 ("amber", 352,186, 126, 48, "classify",  "the gate"),
 ("teal",  498,186, 126, 48, "summarise", "quarantine"),
 ("purple", 76,262, 250, 64, "raw documentation",  "vector only, never shown"),
 ("green", 354,262, 250, 64, "rewritten sentence", "vector and shown text"),
 ("blue",  140,380, 400, 54, "query — scoped to this project", "only coordinates this project resolved"),
 ("green", 200,480, 280, 54, "the coding agent", "sees the rewrite and the signature"),
]
EDGES = ["M340,104 V134","M186,210 H206","M332,210 H352","M478,210 H498",
         "M340,234 V262","M340,346 V380","M340,464 V480"]

def rules(mode):
    i = 0 if mode == "light" else 1
    out = []
    for name,(l,d) in RAMPS.items():
        f,s,t,sb = (l,d)[i]
        out += [f".hiw .{name}-b {{ fill:{f}; stroke:{s}; stroke-width:1; }}",
                f".hiw .{name}-t {{ fill:{t}; }}", f".hiw .{name}-s {{ fill:{sb}; }}"]
    e, r, p = NEUTRAL[i]
    out += [f".hiw .edge {{ stroke:{e}; stroke-width:1.5; fill:none; }}",
            f".hiw .head {{ fill:none; stroke:{e}; stroke-width:1.5; stroke-linecap:round; stroke-linejoin:round; }}",
            f".hiw .region {{ fill:none; stroke:{r}; stroke-width:1; stroke-dasharray:4 4; }}",
            f".hiw .plain {{ fill:{p}; }}",
            f".hiw .lead {{ fill:{RAMPS['gray'][i][2]}; }}"]
    return "\n  ".join(out)

def body():
    o = ['<g class="hiw">']
    b = BOXES[0]
    def box(c,x,y,w,h,t,s):
        cx = x + w/2
        return (f'<rect class="{c}-b" x="{x}" y="{y}" width="{w}" height="{h}" rx="4"/>\n'
                f'<text class="t {c}-t" x="{cx:g}" y="{y+24}" text-anchor="middle">{t}</text>\n'
                f'<text class="ts {c}-s" x="{cx:g}" y="{y+42}" text-anchor="middle">{s}</text>')
    o.append(box(*BOXES[0]))
    o.append('<rect class="region" x="40" y="134" width="600" height="212" rx="12"/>')
    o.append('<text class="t lead" x="56" y="157">shared store — keyed by coordinate and version</text>')
    o.append('<text class="ts plain" x="56" y="175">built once per library version, reused by every project on the machine</text>')
    for spec in BOXES[1:7]:
        # small strip boxes use a tighter baseline
        c,x,y,w,h,t,s = spec
        cx = x + w/2
        dy1, dy2 = (22, 39) if h == 48 else (26, 44)
        o.append(f'<rect class="{c}-b" x="{x}" y="{y}" width="{w}" height="{h}" rx="4"/>')
        o.append(f'<text class="t {c}-t" x="{cx:g}" y="{y+dy1}" text-anchor="middle">{t}</text>')
        o.append(f'<text class="ts {c}-s" x="{cx:g}" y="{y+dy2}" text-anchor="middle">{s}</text>')
    o.append(box(*BOXES[7]))
    o.append('<text class="ts plain" x="352" y="124">only coordinates not already indexed</text>')
    for d in EDGES:
        o.append(f'<path class="edge" d="{d}" marker-end="url(#hiwArrow)"/>')
    o.append('<path class="edge" d="M340,434 V464"/>')
    o.append('<line x1="40" y1="464" x2="640" y2="464" stroke="#C0392B" stroke-width="1.5" stroke-dasharray="6 4"/>')
    o.append('<text class="ts plain" x="636" y="458" text-anchor="end">trust boundary — only the rewrite crosses</text>')
    o.append(box(*BOXES[8]))
    o.append('</g>')
    return "\n".join(o)

TITLE = "How the indexer works"
DESC = ("A project resolves its dependencies and asks which coordinates are not yet indexed. "
        "A shared machine-level store, keyed by coordinate and version, runs harvest, parse, "
        "classify and summarise once per library version and holds a two-faced index. Queries are "
        "scoped to the coordinates this project resolved, and only the rewritten sentence crosses "
        "a trust boundary to the coding agent.")
DEFS = ('<defs><marker id="hiwArrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" '
        'markerHeight="7" orient="auto-start-reverse">'
        '<path class="head" d="M0,1 L9,5 L0,9"/></marker></defs>')
FONT = ('.hiw text { font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Helvetica, '
        'Arial, sans-serif; }\n  .hiw .t { font-size:14px; font-weight:500; }\n'
        '  .hiw .ts { font-size:12px; font-weight:400; }')

# --- standalone file: prefers-color-scheme
standalone = f'''<svg xmlns="http://www.w3.org/2000/svg" width="680" height="554" viewBox="0 0 680 554" role="img" aria-labelledby="hiwT hiwD">
<title id="hiwT">{TITLE}</title>
<desc id="hiwD">{DESC}</desc>
<style>
  {FONT}
  {rules("light")}
  @media (prefers-color-scheme: dark) {{
  {rules("dark")}
  }}
</style>
{DEFS}
{body()}
</svg>
'''
open("site/public/how-it-works.svg","w").write(standalone)

# --- inline copy: follows Starlight's data-theme toggle
inline = f'''<svg xmlns="http://www.w3.org/2000/svg" width="100%" viewBox="0 0 680 554" role="img" aria-labelledby="hiwT hiwD" style="max-width:680px;height:auto;margin:1.5rem 0">
<title id="hiwT">{TITLE}</title>
<desc id="hiwD">{DESC}</desc>
<style>
  {FONT}
  {rules("light")}
  :root[data-theme='dark'] {rules("dark").replace(chr(10) + "  ", chr(10) + "  :root[data-theme='dark'] ")}
</style>
{DEFS}
{body()}
</svg>'''
open("/tmp/hiw_inline.svg","w").write(inline)
print("generated both")
