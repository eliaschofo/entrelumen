"""Hand-authored ENTRELUMEN item icons on a native 16x16 grid (no resampling)."""
from PIL import Image
import sys, math
sys.path.insert(0,'.')
from grid import dump
from palette import RAMPS
def pal(**kw): return kw
class Canvas:
    def __init__(s,P): s.P=P; s.g=[['.']*16 for _ in range(16)]
    def px(s,x,y,c):
        if 0<=x<16 and 0<=y<16: s.g[y][x]=c
    def get(s,x,y): return s.g[y][x] if 0<=x<16 and 0<=y<16 else '.'
    def rect(s,x0,y0,x1,y1,c):
        for y in range(y0,y1+1):
            for x in range(x0,x1+1): s.px(x,y,c)
    def disc(s,cx,cy,r,c):
        for y in range(16):
            for x in range(16):
                if (x-cx)**2+(y-cy)**2<=r*r: s.px(x,y,c)
    def ring(s,cx,cy,r0,r1,c):
        for y in range(16):
            for x in range(16):
                d=(x-cx)**2+(y-cy)**2
                if r0*r0<d<=r1*r1: s.px(x,y,c)
    def line(s,pts,c):
        for (x0,y0),(x1,y1) in zip(pts,pts[1:]):
            n=max(abs(x1-x0),abs(y1-y0))
            for i in range(n+1):
                s.px(round(x0+(x1-x0)*i/max(n,1)),round(y0+(y1-y0)*i/max(n,1)),c)
    def outline(s,c='O',diag=False):
        occ={(x,y) for y in range(16) for x in range(16) if s.g[y][x]!='.'}
        for y in range(16):
            for x in range(16):
                if (x,y) in occ: continue
                nb=[(1,0),(-1,0),(0,1),(0,-1)]+([(1,1),(-1,-1),(1,-1),(-1,1)] if diag else [])
                if any((x+a,y+b) in occ for a,b in nb): s.px(x,y,c)
    def save(s,name):
        im=Image.new('RGBA',(16,16),(0,0,0,0))
        for y in range(16):
            for x in range(16):
                c=s.g[y][x]
                if c!='.': im.putpixel((x,y),tuple(int(s.P[c][i:i+2],16) for i in (1,3,5))+(255,))
        im.save(f'grids/{name}.png'); dump(f'grids/{name}.png',f'grids/{name}.txt')
R=RAMPS
def ark_bus():
    # a spool of thick teal bus cable between aged-copper flanges, plug end trailing out
    c=Canvas(dict(O='#141417',a=R['teal'][0],b=R['teal'][1],B=R['teal'][2],h=R['teal'][3],W=R['teal'][4],
                  v=R['violet'][2],V=R['violet'][4],k=R['copper'][1],K=R['copper'][3],L=R['copper'][4],H=R['copper'][6],
                  e=R['verdigris'][2],E=R['verdigris'][4]))
    # windings (x 3..11, y 4..11), alternating rows with light/dark to read as coils
    for y in range(4,12):
        for x in range(3,12):
            c.px(x,y,'B' if y%2==0 else 'b')
        c.px(3,y,'a'); c.px(11,y,'a')
        if y%2==0: c.px(4,y,'h'); c.px(5,y,'h'); c.px(6,y,'W') if y==4 else None
    for (x,y) in [(7,5),(8,7),(6,9),(9,11)]: c.px(x,y,'v')
    for (x,y) in [(8,5),(9,7),(7,9)]: c.px(x,y,'V')
    # flanges: flat discs seen from the side (top and bottom)
    for y,(dark) in ((2,False),(12,True)):
        for x in range(1,14): c.px(x,y,'K'); c.px(x,y+1,'k')
        for x in range(2,13): c.px(x,y,'L')
        c.px(2,y,'H'); c.px(3,y,'H')
        c.px(1,y+1,'k'); c.px(13,y+1,'k')
    for (x,y) in [(5,3),(10,13),(12,3)]: c.px(x,y,'e')
    c.px(11,13,'E')
    # axle hub
    c.px(7,2,'k'); c.px(7,12,'k')
    # trailing cable to a plug at the top right
    c.line([(12,5),(13,4),(14,4)],'B'); c.px(13,5,'b'); c.rect(14,1,15,3,'K'); c.px(14,1,'H'); c.px(15,3,'k')
    c.outline(); c.save('ark_bus')
def calibration_frame():
    c=Canvas(dict(O='#141417',a=R['iron'][1],b=R['iron'][2],B=R['iron'][3],h=R['iron'][4],w=R['iron'][5],
                  k=R['copper'][2],K=R['copper'][4],H=R['copper'][5],g=R['teal'][2],G=R['teal'][4],W=R['teal'][5]))
    # square frame 2px thick, bevelled: light top/left, dark bottom/right
    c.rect(2,2,13,13,'B'); c.rect(4,4,11,11,'.')
    for i in range(2,14): c.px(i,2,'h'); c.px(2,i,'h'); c.px(i,13,'a'); c.px(13,i,'a')
    for i in range(4,12): c.px(i,4,'a'); c.px(4,i,'a'); c.px(i,11,'h'); c.px(11,i,'h')
    c.px(2,2,'w')
    # copper wire windings at corners (diagonal stripes)
    for (x,y) in [(2,3),(3,2),(3,4),(4,3),(12,2),(13,3),(11,3),(12,4),(2,12),(3,13),(3,11),(4,12),(12,13),(13,12),(11,12),(12,11)]: c.px(x,y,'K')
    for (x,y) in [(2,2),(13,2),(2,13),(13,13),(4,4),(11,4),(4,11),(11,11)]: c.px(x,y,'k')
    c.px(3,2,'H'); c.px(12,2,'H')
    # crosshair struts and lens
    for i in range(5,11): c.px(i,7,'b'); c.px(7,i,'b')
    c.disc(7.5,7.5,1.9,'g'); c.px(7,7,'G'); c.px(8,7,'g'); c.px(7,8,'g'); c.px(7,6,'G'); c.px(6,7,'G'); c.px(7,7,'W')
    c.outline(); c.save('calibration_frame')
def handling_core():
    c=Canvas(dict(O='#141417',a=R['brass'][0],b=R['brass'][1],B=R['brass'][2],k=R['brass'][3],K=R['brass'][4],H=R['brass'][5],
                  t=R['teal'][1],T=R['teal'][2],g=R['teal'][3],G=R['teal'][4],W=R['teal'][5],i=R['iron'][2],I=R['iron'][3]))
    # glowing core orb
    c.disc(9,6,3.2,'T'); c.disc(9,6,2.2,'g'); c.px(8,5,'G'); c.px(9,5,'G'); c.px(8,4,'W'); c.px(10,8,'t'); c.px(11,7,'t'); c.px(11,6,'t')
    # three brass fingers wrapping the orb
    c.line([(4,11),(5,7),(6,4),(7,2)],'k'); c.px(8,2,'K'); c.px(7,1,'H')
    c.line([(5,11),(9,11),(12,10),(13,8)],'k'); c.px(13,7,'K'); c.px(14,7,'H')
    c.line([(5,10),(7,9)],'K')
    # knuckle joints
    for (x,y) in [(5,7),(9,11)]: c.px(x,y,'B')
    # wrist gear/base bottom-left
    c.disc(3.5,12.5,2.3,'B'); c.px(3,12,'b'); c.px(4,12,'b'); c.px(3,13,'a')
    for (x,y) in [(1,12),(3,10),(6,12),(3,15)]: c.px(x,y,'k')
    c.px(2,11,'K')
    c.outline(); c.save('handling_core')

def ecosystem_capsule():
    c=Canvas(dict(O='#141417',g=R['glass'][1],G=R['glass'][2],h=R['glass'][3],w=R['glass'][4],
                  k=R['wood'][2],K=R['wood'][4],c=R['copper'][2],C=R['copper'][4],H=R['copper'][6],
                  l=R['leaf'][1],L=R['leaf'][3],M=R['leaf'][4],s=R['wood'][1],y=R['straw'][2],Y=R['straw'][3],b='#1f1d22'))
    # flask body: round bottom, neck
    c.disc(7.5,10,5.2,'G')
    c.rect(6,3,9,6,'G')
    # glass shading: left bright rim, right darker
    for y in range(4,16):
        for x in range(16):
            if c.get(x,y)=='G':
                if c.get(x-1,y)=='.' : c.px(x,y,'h')
                elif c.get(x+1,y)=='.' or c.get(x,y+1)=='.': c.px(x,y,'g')
    c.px(4,8,'w'); c.px(4,9,'w'); c.px(3,10,'w'); c.px(7,4,'w')
    # soil + sprout inside
    for x in range(4,12): c.px(x,13,'s')
    for x in range(5,11): c.px(x,12,'s') if x in (5,10) else None
    c.line([(8,12),(8,8)],'l'); c.px(7,8,'L'); c.px(6,7,'L'); c.px(6,8,'M'); c.px(9,9,'L'); c.px(10,8,'L'); c.px(10,9,'M')
    # tiny bee
    c.px(10,6,'y'); c.px(11,6,'b'); c.px(10,5,'w')
    # cork and copper band
    c.rect(6,1,9,2,'k'); c.px(6,1,'K'); c.px(7,1,'K')
    c.rect(6,3,9,3,'C'); c.px(6,3,'H'); c.px(9,3,'c')
    c.outline(); c.save('ecosystem_capsule')
def ration_bundle():
    c=Canvas(dict(O='#141417',a=R['parch'][1],b=R['parch'][2],B=R['parch'][3],h=R['parch'][4],w=R['parch'][5],
                  t=R['wood'][1],T=R['wood'][3],r=R['straw'][0],q=R['straw'][1],Q=R['straw'][2],y=R['straw'][3],
                  f=R['iron'][2],F=R['iron'][3],G=R['iron'][4]))
    # cloth sack
    c.disc(7,10,5.3,'B')
    c.rect(5,3,9,5,'B')
    for y in range(16):
        for x in range(16):
            if c.get(x,y)=='B':
                if c.get(x-1,y)=='.' or c.get(x,y-1)=='.': c.px(x,y,'h')
                elif c.get(x+1,y)=='.' or c.get(x,y+1)=='.': c.px(x,y,'a')
    for (x,y) in [(4,9),(5,11),(9,12),(10,9),(6,13)]: c.px(x,y,'b')
    c.px(4,7,'w'); c.px(5,7,'w')
    # knotted twine at the neck with ears
    for x in range(4,11): c.px(x,6,'t')
    c.px(7,6,'T'); c.px(4,2,'h'); c.px(3,1,'B'); c.px(10,2,'B'); c.px(11,1,'a'); c.px(5,2,'B'); c.px(9,2,'B')
    # bread loaf poking out right
    c.rect(10,5,13,8,'Q'); c.px(10,5,'y'); c.px(11,5,'y'); c.px(12,5,'y'); c.px(13,8,'r'); c.px(12,8,'q'); c.px(13,7,'q')
    c.px(11,6,'q'); c.px(12,7,'q')
    # small dried fish tucked under the twine, tail up-left
    c.px(3,5,'F'); c.px(2,4,'G'); c.px(3,4,'f'); c.px(2,3,'F'); c.px(1,3,'f')
    c.outline(); c.save('ration_bundle')
def routing_matrix():
    c=Canvas(dict(O='#141417',a=R['leather'][0],b=R['leather'][1],B=R['leather'][2],h=R['leather'][3],H=R['leather'][4],
                  k=R['copper'][2],K=R['copper'][4],L=R['copper'][5],t=R['teal'][2],T=R['teal'][3],W=R['teal'][5],g=R['brass'][3]))
    # diagonal board (diamond-ish rectangle rotated 45)
    for y in range(16):
        for x in range(16):
            if abs((x-7.5)+(y-7.5))<=7 and abs((x-7.5)-(y-7.5))<=4.5: c.px(x,y,'B')
    for y in range(16):
        for x in range(16):
            if c.get(x,y)=='B':
                if c.get(x,y-1)=='.' or c.get(x-1,y)=='.': c.px(x,y,'h')
                elif c.get(x,y+1)=='.' or c.get(x+1,y)=='.': c.px(x,y,'b')
    # copper traces
    c.line([(4,6),(6,6),(8,8),(10,8)],'k'); c.line([(6,4),(6,6)],'k'); c.line([(8,8),(8,11)],'k'); c.line([(10,8),(11,9)],'k')
    c.px(5,6,'K'); c.px(7,7,'K'); c.px(9,8,'K')
    # crystal nodes
    for (x,y) in [(4,5),(11,10),(8,11)]: c.px(x,y,'T'); c.px(x+1,y,'t'); c.px(x,y+1,'t')
    c.px(4,5,'W'); c.px(11,10,'W')
    c.px(6,4,'g'); c.px(7,4,'L')
    c.outline(); c.save('routing_matrix')
def containment_seal():
    c=Canvas(dict(O='#141417',a=R['iron'][0],b=R['iron'][1],B=R['iron'][2],h=R['iron'][3],H=R['iron'][4],
                  r=R['crimson'][1],m=R['crimson'][2],M=R['crimson'][3],L=R['crimson'][4],W=R['crimson'][5],d='#320a12'))
    c.ring(7.5,7.5,3.6,6.6,'B')
    for y in range(16):
        for x in range(16):
            if c.get(x,y)=='B':
                dx,dy=x-7.5,y-7.5
                if dx+dy<-4: c.px(x,y,'h')
                elif dx+dy>4: c.px(x,y,'b')
    for (x,y) in [(7,1),(8,1),(1,7),(1,8),(14,7),(14,8),(7,14),(8,14)]: c.px(x,y,'H')
    for (x,y) in [(7,2),(2,7),(13,8),(8,13)]: c.px(x,y,'a')
    # gem inside (faceted)
    c.disc(7.5,7.5,3.3,'m')
    for (x,y) in [(6,5),(7,5),(5,6),(6,6)]: c.px(x,y,'L')
    c.px(6,5,'W'); c.px(9,9,'r'); c.px(8,10,'r'); c.px(10,8,'r'); c.px(9,10,'d'); c.px(10,9,'d')
    c.px(8,6,'M'); c.px(9,7,'M')
    # wax seal blob lower right
    c.disc(12.5,12.5,2.3,'m'); c.px(12,11,'L'); c.px(11,12,'M'); c.px(13,13,'r'); c.px(12,13,'r'); c.px(14,12,'r')
    c.outline(); c.save('containment_seal')


def power_regulator():
    # squat steel regulator block (3/4 view) with a brass gauge and two copper terminals
    c=Canvas(dict(O='#141417',a=R['iron'][0],b=R['iron'][1],B=R['iron'][2],h=R['iron'][3],H=R['iron'][4],w=R['iron'][5],
                  q=R['brass'][1],Q=R['brass'][3],y=R['brass'][4],Y=R['brass'][5],r=R['crimson'][3],
                  k=R['copper'][2],K=R['copper'][4],L=R['copper'][6],t=R['teal'][3]))
    # top face (parallelogram) y 3..6, front face y 6..13
    for y in range(3,7):
        for x in range(2+ (6-y), 14 - (y-3) + (6-y) - 3 + 3):
            pass
    c.rect(2,6,11,13,'B')          # front
    c.rect(12,4,13,11,'b')         # right side
    c.px(12,12,'b'); c.px(12,3,'.')
    for i,y in enumerate(range(3,6)):
        for x in range(4-i+1,14-i): c.px(x,y,'h')
    c.rect(2,5,12,5,'h'); c.rect(3,4,13,4,'H'); c.rect(4,3,13,3,'H')
    for x in range(2,12): c.px(x,6,'H')
    c.px(2,6,'w'); c.px(3,6,'w')
    for y in range(7,14): c.px(2,y,'h'); c.px(11,y,'b')
    for x in range(3,12): c.px(x,13,'a')
    for (x,y) in [(3,12),(10,12),(3,7),(10,7)]: c.px(x,y,'a')
    # gauge
    c.disc(6.5,9.5,2.4,'Q')
    for (x,y) in [(5,8),(6,8),(7,8)]: c.px(x,y,'Y')
    c.px(4,9,'y'); c.px(8,11,'q'); c.px(9,10,'q'); c.px(5,11,'q')
    c.px(6,10,'O'); c.px(6,9,'b'); c.px(7,8,'r'); c.px(5,8,'Y'); c.px(6,8,'Y')
    # status light
    c.px(10,8,'t')
    # copper terminals on top
    for x0 in (5,10):
        c.px(x0,2,'K'); c.px(x0+1,2,'k'); c.px(x0,1,'L'); c.px(x0+1,1,'K')
    c.outline(); c.save('power_regulator')

if __name__=='__main__':
    for n in sys.argv[1:]: globals()[n]()
