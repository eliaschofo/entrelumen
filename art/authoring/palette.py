"""ENTRELUMEN master ramps (dark -> light). Items pick subsets; remap uses perceptual distance."""
import math
RAMPS={
 'ink':      ['#141417','#1f1d22','#2b2124'],
 'copper':   ['#4a2419','#6b3424','#8e4631','#ad5a3f','#c8714f','#e08e6a','#f4b596'],
 'brass':    ['#4d3210','#7a511a','#a8742a','#cf9c3c','#ecc866','#fff0b0'],
 'verdigris':['#1c3f37','#2b5e50','#3c7f69','#52a07f','#72c49e','#b0e8cc'],
 'teal':     ['#0d3337','#11605f','#1a9690','#35ccbd','#8ef2e4','#e6fffb'],
 'parch':    ['#4f3f26','#7d6843','#a99368','#cfbb8f','#e8dcb5','#fbf5e3'],
 'leather':  ['#11251b','#1b3a29','#27523a','#36704d','#4b8f60'],
 'iron':     ['#25272c','#41454d','#646a73','#8d939c','#bcc1c7','#e9ecef'],
 'wood':     ['#33200f','#51351b','#6f4b28','#8f6437','#b0844d'],
 'crimson':  ['#320a12','#5c1320','#8c1d2f','#bd3040','#e8606a','#ffa8ac'],
 'violet':   ['#241238','#3f2064','#5f3592','#8757c4','#b58de6','#e6d4ff'],
 'leaf':     ['#16300f','#24491a','#386a24','#50902f','#76b83e','#b4e070'],
 'sky':      ['#1f3b73','#2f5ea8','#4a86d6','#7fb2f0','#c4e0ff'],
 'glass':    ['#2f4f5a','#557f8c','#86b3be','#b9dde3','#e9f8fa'],
 'straw':    ['#6b4a12','#a57a1e','#d8ac34','#f4d86a'],
}
def h2r(s): return tuple(int(s[i:i+2],16) for i in (1,3,5))
def lab(c):
    def f(u):
        u/=255; return ((u+0.055)/1.055)**2.4 if u>0.04045 else u/12.92
    r,g,b=map(f,c)
    X=(0.4124*r+0.3576*g+0.1805*b)/0.95047; Y=0.2126*r+0.7152*g+0.0722*b; Z=(0.0193*r+0.1192*g+0.9505*b)/1.08883
    g_=lambda t:t**(1/3) if t>0.008856 else 7.787*t+16/116
    return (116*g_(Y)-16, 500*(g_(X)-g_(Y)), 200*(g_(Y)-g_(Z)))
def remap(im, ramps):
    from PIL import Image
    pal=[h2r(c) for r in ramps for c in RAMPS[r]]
    labs=[lab(c) for c in pal]
    out=im.convert('RGBA').copy(); w,h=out.size; cache={}
    for y in range(h):
        for x in range(w):
            p=out.getpixel((x,y))
            if p[3]<128: out.putpixel((x,y),(0,0,0,0)); continue
            c=p[:3]
            if c not in cache:
                L=lab(c); cache[c]=pal[min(range(len(pal)),key=lambda i:sum((a-b)**2 for a,b in zip(L,labs[i])))]
            out.putpixel((x,y),cache[c]+(255,))
    return out
