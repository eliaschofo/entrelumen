"""Pixel-art cleanup for native 16x16 candidates: limited palette without dithering,
isolated-pixel removal inside same-luminance regions, binary alpha. No resampling."""
from PIL import Image
import sys
def lum(c): return 0.299*c[0]+0.587*c[1]+0.114*c[2]
def clean(im, k=12, passes=1, speck_tol=40):
    im=im.convert('RGBA'); w,h=im.size
    a=[[im.getpixel((x,y))[3]>=128 for x in range(w)] for y in range(h)]
    rgb=Image.new('RGB',(w,h),(0,0,0))
    for y in range(h):
        for x in range(w):
            if a[y][x]: rgb.putpixel((x,y),im.getpixel((x,y))[:3])
    ops=[(x,y) for y in range(h) for x in range(w) if a[y][x]]
    strip=Image.new('RGB',(len(ops),1))
    for i,(x,y) in enumerate(ops): strip.putpixel((i,0),rgb.getpixel((x,y)))
    q=strip.quantize(colors=k,method=Image.Quantize.MEDIANCUT,dither=Image.Dither.NONE).convert('RGB')
    px={}
    for i,(x,y) in enumerate(ops): px[(x,y)]=q.getpixel((i,0))
    for _ in range(passes):
        new=dict(px)
        for (x,y),c in px.items():
            n8=[px.get((x+dx,y+dy)) for dx in (-1,0,1) for dy in (-1,0,1) if (dx,dy)!=(0,0)]
            if any(v is None for v in n8): continue      # keep silhouette/outline pixels
            if c in n8: continue
            n4=[px[(x+1,y)],px[(x-1,y)],px[(x,y+1)],px[(x,y-1)]]
            best=max(set(n4),key=n4.count)
            if n4.count(best)>=2 and abs(lum(best)-lum(c))<=speck_tol: new[(x,y)]=best
        px=new
    out=Image.new('RGBA',(w,h),(0,0,0,0))
    for (x,y),c in px.items(): out.putpixel((x,y),c+(255,))
    return out
if __name__=='__main__':
    src,dst=sys.argv[1],sys.argv[2]; k=int(sys.argv[3]) if len(sys.argv)>3 else 12
    clean(Image.open(src),k).save(dst)
