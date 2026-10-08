from pathlib import Path
from html import escape

ROOT = Path(__file__).parent
PARTS = []
def add(s): PARTS.append(s)
def txt(x, y, text, size=16, color='#203f45', weight=400, anchor='start'):
    add(f'<text x="{x}" y="{y}" font-family="Inter,Arial,sans-serif" font-size="{size}" fill="{color}" font-weight="{weight}" text-anchor="{anchor}">{escape(text)}</text>')
def rect(x,y,w,h,r=16,fill='#f5faf8',stroke='#a6bfc0',opacity=1):
    add(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="{r}" fill="{fill}" stroke="{stroke}" opacity="{opacity}"/>')
def line(x1,y1,x2,y2,color='#aec4c4',width=1,dash=''):
    add(f'<path d="M{x1} {y1}L{x2} {y2}" stroke="{color}" stroke-width="{width}" fill="none" stroke-dasharray="{dash}"/>')
def circle(x,y,r,fill='#e8f8f4',stroke='#6baaa8'):
    add(f'<circle cx="{x}" cy="{y}" r="{r}" fill="{fill}" stroke="{stroke}"/>')
def geode(x,y,r=35):
    points=' '.join(f'{x+a*r},{y+b*r}' for a,b in [(0,-1),(.78,-.65),(1,.13),(.52,.94),(-.43,1),(-1,.24),(-.8,-.55)])
    add(f'<polygon points="{points}" fill="#b4e2ce" stroke="#539591"/>')
    line(x,y-r,x-r*.43,y+r,color='#91c8c1'); line(x-r,y+r*.24,x+r,y+r*.13,color='#91c8c1')
    line(x-r*.8,y-r*.55,x+r*.52,y+r*.94,color='#b8e5df')
def button(x,y,w,label,active=False):
    rect(x,y,w,48,12, '#d9eee8' if active else '#f6faf7','#57928d' if active else '#b2c6c4')
    txt(x+w/2,y+30,label,14,weight=600 if active else 400,anchor='middle')
def heading(title,sub):
    txt(22,46,'GEODE',11,'#57817d',600)
    txt(22,82,title,28,weight=600)
    txt(22,104,sub,12,'#5a7776')
def base(name,index):
    add(f'<g id="{name.lower()}" transform="translate({24+index*416},95)">')
    rect(0,0,390,844,26,'#dcece4','#86a4a4')
    add('<path d="M1 155 L52 128 L100 158 L152 124 L207 162 L282 132 L389 160 V230 H1Z" fill="#a6bcbc" opacity=".23"/>')
    add('<path d="M1 205 Q95 177 200 210 T389 195 V325 H1Z" fill="#92acaa" opacity=".18"/>')
    line(18,700,373,700,color='#a1c3bb')
    txt(195,827,'SYSTEM GESTURE INSET',9,'#6c8985',anchor='middle')
def close(): add('</g>')
def miniplayer():
    rect(14,735,362,74,18,'#edf7f1','#81aaa6')
    txt(30,758,'Now playing',11,'#5b7771')
    txt(30,781,'01 dream set 2',14,weight=600)
    circle(283,769,22,'#d7ece5'); txt(283,776,'Ⅱ',20,anchor='middle')
    geode(340,765,18); txt(340,795,'Navigate',9,'#3d6662',anchor='middle')
    line(30,798,308,798,color='#b6cdca',width=3);line(30,798,124,798,color='#4c8c82',width=3)
def tabs(labels,y=124):
    widths=[350/len(labels)]*len(labels)
    x=20
    for n,l in enumerate(labels):
        w=widths[n]; txt(x+w/2,y+28,l,12,weight=600 if n==0 else 400,anchor='middle');
        if n==0: line(x+6,y+44,x+w-6,y+44,color='#4f8b7f',width=3)
        x+=w

def row(y,title,sub,trailing='',h=72):
    rect(20,y,350,h,12,'#f2f8f3','#c5d6d0')
    txt(34,y+27,title,15,weight=500); txt(34,y+48,sub,11,'#667d75');
    if trailing: txt(351,y+30,trailing,13,'#4d7169',anchor='end')

add('<svg xmlns="http://www.w3.org/2000/svg" width="2110" height="1040" viewBox="0 0 2110 1040" role="img" aria-labelledby="title desc">')
add('<title id="title">Geode UI 2.0 — five destination wireframes</title><desc id="desc">Player, Library, Visuals, Studio and Settings. Orbit navigation opens as a separate overlay. Content screens have one compact now playing strip with a navigation handle. Pale panels preserve text contrast over the natural lake environment.</desc>')
rect(0,0,2110,1040,0,'#f7f8f2','#f7f8f2');txt(24,38,'GEODE / UI 2.0 / NATIVE APP WIREFRAMES',23,weight=600)
txt(24,65,'Structure, hit regions and hierarchy — the world renderer uses real GPU geometry and lighting; these diagrams are not final artwork.',14,'#6f8178')

base('Player',0);heading('Player','Music in a living landscape')
geode(195,287,75)
add('<ellipse cx="195" cy="393" rx="55" ry="12" fill="#8bbdad" opacity=".26"/>')
txt(195,442,'Tap the geode to open orbit navigation',11,'#66837a',anchor='middle')
rect(20,466,350,94,16,'#edf7ef','#91b1a6');rect(34,480,56,56,10,'#bdd7cd','#9cbdb0')
txt(106,486,'NOW PLAYING',10,'#657e71');txt(106,511,'01 dream set 2',18,weight=600);txt(106,539,'Nomad · Warehouse Raves 5',11,'#657e71')
rect(20,575,350,132,18,'#edf7ef','#91b1a6')
line(42,599,348,599,width=3);line(42,599,143,599,color='#4c907e',width=3);txt(42,618,'02:14',11,'#577467');txt(348,618,'06:42',11,'#577467',anchor='end')
for x,label in [(96,'Previous'),(195,'Pause'),(294,'Next')]:
    circle(x,653,24,'#e2f1e5');txt(x,658,{'Previous':'◀','Pause':'Ⅱ','Next':'▶'}[label],18,anchor='middle')
txt(42,697,'A–B',11,'#577467');txt(181,697,'Auto off',11,'#577467');txt(348,697,'Queue (4)',11,'#577467',anchor='end')
rect(20,718,350,30,10,'#edf7ef','#91b1a6');txt(195,738,'Live spectrum',11,'#577467',anchor='middle')
button(130,766,130,'Navigate');close()

base('Library',1);heading('Library','Tracks, collections and saved music');tabs(['Tracks','Albums','Artists','Folders','Playlists'])
rect(20,180,350,54,12,'#f9fcf7','#98b8ac');txt(38,213,'Search titles, artists, albums',14,'#5c796d');
button(20,246,350,'Sort: Title ⌄',True);
for y,title,sub,d in [(312,'(I Wanna Give You) Dev…','Nomad · Warehouse Raves 5','6:42'),(394,'00 · Funk Tribu & Odym…','Unknown · All My Music','3:20'),(476,'00 · HOEHENANGST…','Unknown · All My Music','5:26'),(558,'02 · Lake reflections','Example media metadata','4:18')]:row(y,title,sub,d)
txt(25,676,'Import and play collection controls stay contextual',11,'#547368');miniplayer();close()

base('Visuals',2);heading('Visuals','Tunnel · One native preview')
rect(20,124,350,132,16,'#abc8c1','#80a49b');add('<path d="M20 227 Q80 149 195 217 T370 153" stroke="#d8f5e6" stroke-width="6" fill="none"/>');txt(38,150,'LIVE VISUALIZER PREVIEW',10,weight=600)
button(255,268,115,'View live')
tabs(['Presets','Styles','Customize','Textures','Takes'],322)
button(20,387,166,'Import preset');button(204,387,166,'Video templates')
txt(24,456,'Built-in',13,weight=600)
row(468,'tunnel · Chill','Built-in preset','▶');row(549,'tunnel · Punchy','Built-in preset','▶');row(630,'tunnel · Hypno','Built-in preset','▶')
miniplayer();close()

base('Studio',3);heading('Studio','Clips, timeline and export')
button(20,124,136,'Timeline');button(174,124,196,'Open a video…')
for y,name,duration in [(193,'Lake session.mp4','00:14'),(328,'Morning light.mp4','00:32'),(463,'Dream set.mp4','01:08')]:
    rect(20,y,350,119,14,'#f2f7ed','#acbfae');rect(34,y+14,96,56,8,'#bdd7cd','#9cbdb0')
    txt(146,y+34,name,15,weight=600);txt(146,y+59,duration,12,'#5b7861')
    txt(44,y+101,'Send',12);txt(161,y+101,'Rename',12);txt(282,y+101,'Delete',12)
txt(195,631,'Timeline and clip editing open separate pages',11,'#547368',anchor='middle')
miniplayer();close()

base('Settings',4);heading('Settings','Make Geode feel yours');
for y,title,sub in [(125,'Look','Materials, text and layout'),(207,'Audio','Playback, equalizer and live input'),(289,'Export','Format, quality and saved defaults'),(371,'Folders','Music sources, presets and cache'),(453,'Behavior','Interaction, safety and display'),(535,'Help','Tutorial and practical guidance'),(617,'About','Version, licenses and privacy')]:row(y,title,sub,'›')
miniplayer();close()

txt(24,980,'01 / SPATIAL HOME',13,weight=600);txt(440,980,'02 / LIBRARY',13,weight=600);txt(856,980,'03 / VISUALS',13,weight=600);txt(1272,980,'04 / STUDIO',13,weight=600);txt(1688,980,'05 / SETTINGS',13,weight=600)
txt(24,1007,'48 dp minimum semantic targets · readable pale surface · visible labels · saved screen state · one foreground renderer',14,'#59786c')
add('</svg>'); svg='\n'.join(PARTS);(ROOT/'WIREFRAMES.svg').write_text(svg)
html='''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Geode UI 2.0 Wireframes</title><style>body{margin:0;background:#f7f8f2;font:16px system-ui;color:#203f45}header,footer{padding:20px 24px}h1{margin:0 0 12px;font-size:24px}p{max-width:1000px;line-height:1.6}nav{display:flex;gap:12px;flex-wrap:wrap}a{padding:14px 18px;background:#e0eee4;border:1px solid #9fbfb0;border-radius:10px;color:#254b3b;text-decoration:none}.canvas{overflow:auto;padding:0 0 20px}svg{display:block;min-width:1800px;width:100%}@media print{header,footer{display:none}.canvas{overflow:visible}svg{min-width:0}}</style><header><h1>Geode UI 2.0 — structure before implementation</h1><p>Five native Android destinations. The spatial Player is the home; labelled orbit navigation opens as a separate overlay. Every content screen retains its own state and one compact Player strip, with the navigation handle on the right. These wireframes show layout and readable controls; final lighting, transparent lenses and lake motion come from the scoped GPU world.</p><nav><a href="WIREFRAMES.svg">Open full-size vector</a><a href="BLUEPRINT.md">Implementation blueprint</a><a href="#player">Player</a><a href="#library">Library</a><a href="#visuals">Visuals</a><a href="#studio">Studio</a><a href="#settings">Settings</a></nav></header><div class="canvas">'''+svg+'''</div><footer><p><strong>Navigation:</strong> the geode handle opens labelled orbit navigation; track metadata opens Player; play/pause acts without navigating. Back dismisses the top overlay first, then leaves a nested page, then returns to Player. Export, search and modal panels suspend environmental effects when they obscure them.</p><p><strong>Blueprint companion:</strong> feature parity, overlay ownership, component contracts, state restoration, rendering ownership, asset inventory and clean-switch gates are documented in BLUEPRINT.md.</p></footer></html>'''
(ROOT/'WIREFRAMES.html').write_text(html)
print(ROOT/'WIREFRAMES.html');print(ROOT/'WIREFRAMES.svg')
