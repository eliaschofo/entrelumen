from PIL import Image
import sys; sys.path.insert(0,'.')
from grid import dump
P=dict(O='#141417',o='#0f1f17',d='#1b3a29',g='#27523a',G='#36704d',L='#4b8f60',
 c='#6b3424',C='#ad5a3f',K='#e08e6a',H='#f4b596',t='#1a9690',T='#8ef2e4',w='#e6fffb',
 Q='#a99368',q='#cfbb8f',P='#e8dcb5',p='#fbf5e3')
W=16; g=[['.']*W for _ in range(W)]
def s(x,y,c):
    if 0<=x<W and 0<=y<W: g[y][x]=c
def rect(x0,y0,x1,y1,c):
    for y in range(y0,y1+1):
        for x in range(x0,x1+1): s(x,y,c)
# page block (behind cover, offset right/down by 1) 
rect(4,2,13,14,'P')
for y in range(3,15,2): s(13,y,'q')          # page lines on the fore-edge
for x in range(5,13,2): s(x,14,'q')
rect(4,14,13,14,'q'); s(13,14,'Q'); 
for x in range(4,14): s(x,15,'O')
for y in range(2,15): s(14,y,'O')
s(14,15,'.'); s(13,15,'O')
# cover
rect(2,1,12,13,'g')
for x in range(3,12): s(x,1,'L')              # top highlight
for y in range(2,13): s(2,y,'G') if False else None
rect(2,1,3,13,'d'); s(3,1,'G')               # spine
for y in (3,7,11): s(2,y,'o'); s(3,y,'G')    # spine bands
for y in range(2,13): s(12,y,'G')             # right bevel light
for x in range(4,12): s(x,13,'d')             # bottom shadow
# outline
for x in range(2,13): s(x,0,'O'); s(x,14,'O') if g[14][x] in '.q' and x<4 else None
for y in range(1,14): s(1,y,'O')
for x in range(2,13): s(x,14,'O') if x<4 else None
s(1,0,'.'); s(13,1,'O'); s(13,0,'.')
for x in range(2,4): s(x,14,'O')
# copper corner caps
for (x,y,c) in [(11,1,'K'),(12,1,'H'),(12,2,'K'),(11,2,'C'),(11,12,'C'),(12,12,'K'),(12,13,'C'),(11,13,'c')]: s(x,y,c)
# compass rose emblem (center ~ (7,7))
for (x,y,c) in [(7,3,'K'),(7,4,'C'),(7,5,'C'),(7,9,'C'),(7,10,'c'),(4,7,'C'),(5,7,'C'),(9,7,'C'),(10,7,'c'),
                (6,6,'t'),(8,6,'t'),(6,8,'t'),(8,8,'t'),(7,6,'T'),(6,7,'T'),(8,7,'T'),(7,8,'t'),(7,7,'w')]: s(x,y,c)
for (x,y) in [(5,4),(9,10),(10,4),(4,10),(9,3),(5,11),(10,9),(4,5)]:
    if g[y][x]=='g': s(x,y,'d')
for (x,y) in [(5,2),(6,2),(4,3)]:
    if g[y][x]=='g': s(x,y,'G')
for (x,y) in [(5,5),(9,5),(5,9),(9,9)]: s(x,y,'q')
im=Image.new('RGBA',(W,W),(0,0,0,0))
for y in range(W):
    for x in range(W):
        if g[y][x]!='.': im.putpixel((x,y),tuple(int(P[g[y][x]][i:i+2],16) for i in (1,3,5))+(255,))
im.save('grids/atlas.png'); dump('grids/atlas.png','grids/atlas.txt')
