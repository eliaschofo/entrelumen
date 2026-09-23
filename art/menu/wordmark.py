"""ENTRELUMEN wordmark: blocky extruded letters that age across Minecraft's four copper
stages (copper -> exposed -> weathered -> oxidized), authored on a 1:1 GUI texel grid."""
from PIL import Image
import random, sys
G={
'E':["####","#...","###.","#...","####"],
'N':["#...#","##..#","#.#.#","#..##","#...#"],
'T':["#####","..#..","..#..","..#..","..#.."],
'R':["####.","#...#","####.","#..#.","#...#"],
'L':["#...","#...","#...","#...","####"],
'U':["#...#","#...#","#...#","#...#",".###."],
'M':["#...#","##.##","#.#.#","#...#","#...#"],
}
h=lambda s:tuple(int(s[i:i+2],16) for i in (1,3,5))
# ramps: hi, face, mid, low, ex1, ex2, ex3 ; accents drawn from vanilla copper stage textures
STAGES=[
 dict(hi='#e8937a',face='#c87456',mid='#b26247',low='#9a5038',ex1='#7a3c2a',ex2='#5c2c20',ex3='#44211a'),
 dict(hi='#d69a8c',face='#a87762',mid='#947661',low='#7f6a55',ex1='#5f4a3e',ex2='#4a3a32',ex3='#382c27'),
 dict(hi='#8cc8a8',face='#66a977',mid='#6c975c',low='#4f7a5e',ex1='#3c5c4a',ex2='#2e483b',ex3='#23372e'),
 dict(hi='#7fd3ad',face='#53a178',mid='#3e816b',low='#396e59',ex1='#2a5246',ex2='#20413a',ex3='#18322d'),
]
STAGES=[{k:h(v) for k,v in s.items()} for s in STAGES]
OL=(16,20,22)
SHADOW=(10,24,28)
PLAN=[0,0,0,1,1,1,2,2,3,3]   # E N T R E L U M E N
def glyph(ch,U):
    g=G[ch]; face=set()
    for r,row in enumerate(g):
        for c,v in enumerate(row):
            if v=='#':
                for dy in range(U):
                    for dx in range(U): face.add((c*U+dx,r*U+dy))
    return face,len(g[0])*U,len(g)*U
def build(word='ENTRELUMEN',U=5,depth=3,gap=2,seed=7):
    gl=[glyph(ch,U) for ch in word]
    W=sum(g[1] for g in gl)+gap*(len(word)-1)+depth+2; H=gl[0][2]+depth+2
    im=Image.new('RGBA',(W,H),(0,0,0,0)); px=im.load()
    x0=1
    for li,(face,w,hh) in enumerate(gl):
        S=STAGES[PLAN[li]]; N=STAGES[min(PLAN[li]+1,3)]
        R=random.Random(seed*131+li)
        F=lambda p:p in face
        lay={}
        for d in range(depth,0,-1):
            for (x,y) in face:
                q=(x+d,y+d)
                if q not in face: lay[q]=S['ex1'] if d==1 else S['ex2'] if d==2 else S['ex3']
        for (x,y) in face:
            col=S['face']
            if not F((x,y-1)) or not F((x-1,y)): col=S['hi']
            elif not F((x+1,y)) or not F((x,y+1)): col=S['mid']
            lay[(x,y)]=col
        # plate seams: horizontal break at a unit boundary inside long strokes
        for (x,y) in face:
            if y%U==U-1 and F((x,y+1)) and F((x,y-1)) and ((x//U)+(y//U)+li)%3==1:
                lay[(x,y)]=S['low']
        # weathering patches in the next stage's colours, clustered, favouring lower areas
        cells=sorted(face)
        n=4 if PLAN[li]<3 else 2
        for _ in range(n):
            cx,cy=R.choice([p for p in cells if p[1]>=hh//3])
            for dx,dy in [(0,0),(1,0),(0,1),(-1,0),(1,1),(0,-1),(2,1)][:R.randint(3,7)]:
                q=(cx+dx,cy+dy)
                if q in face: lay[q]=N['face'] if (dx+dy)%2==0 else N['mid']
            if (cx,cy-1) in face: lay[(cx,cy-1)]=N['hi']
        occ=set(lay); ol=set()
        for (x,y) in occ:
            for ax,ay in ((1,0),(-1,0),(0,1),(0,-1),(1,1)):
                q=(x+ax,y+ay)
                if q not in occ: ol.add(q)
        for (x,y) in ol: px[x0+x,1+y]=OL+(255,)
        for (x,y),c in lay.items(): px[x0+x,1+y]=c+(255,)
        x0+=w+gap
    # second, outer ring and a drop shadow so oxidized letters separate from teal skies
    halo=Image.new('RGBA',(W+2,H+3),(0,0,0,0)); hp=halo.load(); src=im.load()
    occ={(x,y) for y in range(H) for x in range(W) if src[x,y][3]}
    for (x,y) in occ:
        for ax,ay in ((1,0),(-1,0),(0,1),(0,-1),(1,1),(0,2),(1,2)):
            q=(x+ax+1,y+ay+1)
            if (x+ax,y+ay) not in occ and 0<=q[0]<W+2 and 0<=q[1]<H+3: hp[q]=SHADOW+(255,)
    halo.alpha_composite(im,(1,1))
    return halo
if __name__=='__main__':
    im=build(); print(im.size); im.save(sys.argv[1] if len(sys.argv)>1 else 'wordmark-preview.png')
