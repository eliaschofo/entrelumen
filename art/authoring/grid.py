"""Editable text grids for native pixel art. dump: png -> .txt ; render: .txt -> png.
Format: lines 'X #rrggbb' define palette chars ('.' is transparent), then a blank line, then rows."""
import sys
from PIL import Image
CH='ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789@$%&*+=?'
def dump(png, out):
    im=Image.open(png).convert('RGBA'); w,h=im.size
    pal={}
    rows=[]
    for y in range(h):
        r=''
        for x in range(w):
            p=im.getpixel((x,y))
            if p[3]<128: r+='.'; continue
            c=p[:3]
            if c not in pal: pal[c]=CH[len(pal)]
            r+=pal[c]
        rows.append(r)
    # sort palette by luminance for readability
    lines=[f"{v} #{c[0]:02x}{c[1]:02x}{c[2]:02x}" for c,v in sorted(pal.items(),key=lambda kv:0.3*kv[0][0]+0.59*kv[0][1]+0.11*kv[0][2])]
    open(out,'w').write('\n'.join(lines)+'\n\n'+'\n'.join(rows)+'\n')
def render(txt, png, scale=1):
    pal={}; rows=[]; body=False
    for line in open(txt).read().splitlines():
        if not body:
            if not line.strip(): body=True; continue
            k,v=line.split(); pal[k]=tuple(int(v[i:i+2],16) for i in (1,3,5))
        elif line.strip(): rows.append(line.rstrip())
    w=max(len(r) for r in rows); h=len(rows)
    im=Image.new('RGBA',(w,h),(0,0,0,0))
    for y,r in enumerate(rows):
        for x,c in enumerate(r):
            if c!='.': im.putpixel((x,y),pal[c]+(255,))
    if scale>1: im=im.resize((w*scale,h*scale),Image.NEAREST)
    im.save(png)
if __name__=='__main__':
    {'dump':dump,'render':render}[sys.argv[1]](*sys.argv[2:4])
