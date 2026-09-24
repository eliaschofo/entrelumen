"""Enchanting shelves and the Atlas Library: bookshelf language, ENTRELUMEN materials."""
import sys, math; sys.path.insert(0,'.')
from draw_items import Canvas
from palette import RAMPS as R
from draw_blocks import TUFF, noise
def frame_colours(kind):
    if kind=='wood': return dict(a=R['wood'][0],b=R['wood'][1],c=R['wood'][2],d=R['wood'][3],e=R['wood'][4])
    if kind=='copper': return dict(a=R['verdigris'][0],b=R['copper'][1],c=R['copper'][2],d=R['copper'][3],e=R['verdigris'][3])
    if kind=='tuff': return dict(a=TUFF[0],b=TUFF[1],c=TUFF[2],d=TUFF[3],e=TUFF[4])
    return dict(a=R['verdigris'][0],b=R['verdigris'][1],c=R['verdigris'][2],d=R['verdigris'][3],e=R['verdigris'][4])
def shelf(name, kind, spines, glow=None, seed=1):
    P=dict(O='#141417',k='#15191b'); P.update(frame_colours(kind))
    keys='FGHIJKLMN'
    for i,col in enumerate(spines): P[keys[i]]=col
    if glow: P['X']=glow[0]; P['Y']=glow[1]
    c=Canvas(P)
    c.rect(0,0,15,15,'c')
    for i in range(16): c.px(i,0,'d'); c.px(i,15,'a'); c.px(0,i,'d'); c.px(15,i,'b')
    for y in (7,8): 
        for x in range(16): c.px(x,y,'b' if y==8 else 'd')
    # two rows of books (rows y=1..6 and y=9..14)
    for row,(y0,y1) in enumerate(((1,6),(9,14))):
        x=1
        i=seed+row*7
        while x<15:
            w=1 if (i*7)%5 else 2
            h0=y0+((i*3)%3==0)
            col=keys[i%len(spines)]
            for xx in range(x,min(15,x+w)):
                for yy in range(h0,y1+1): c.px(xx,yy,col)
                c.px(xx,h0,'e' if (i%3)==0 else col)
            if glow and (i%4)==1:
                for yy in range(h0+1,y1): c.px(x,yy,'X')
                c.px(x,h0+2,'Y')
            if h0>y0:
                for xx in range(x,min(15,x+w)): c.px(xx,y0,'k')
            x+=w
            if x<15 and (i%5)==2: c.px(x,y1,'k'); c.px(x,y1-1,'k'); x+=1
            i+=1
    c.save('blk_'+name)
def end(name, kind, seed):
    P=dict(O='#141417'); P.update(frame_colours(kind)); c=Canvas(P)
    for y in range(16):
        for x in range(16): c.px(x,y,'c' if noise(x,y,seed)<0.7 else 'b')
    for i in range(16): c.px(i,0,'d'); c.px(0,i,'d'); c.px(i,15,'a'); c.px(15,i,'a')
    if kind=='wood':
        for y in (4,8,12):
            for x in range(1,15): c.px(x,y,'b')
    else:
        for i in range(3,13): c.px(i,3,'b'); c.px(3,i,'b'); c.px(i,12,'d'); c.px(12,i,'d')
    c.save('blk_'+name)
def library():
    P=dict(O='#141417',k='#15191b',a=TUFF[0],b=TUFF[1],c=TUFF[2],d=TUFF[3],e=TUFF[4],
           C=R['copper'][2],D=R['copper'][4],E=R['copper'][5],v=R['verdigris'][3],
           F=R['leather'][2],G=R['crimson'][2],H=R['violet'][2],I=R['parch'][3],J=R['teal'][2],X=R['teal'][3],Y=R['teal'][5])
    c=Canvas(P)
    for y in range(16):
        for x in range(16): c.px(x,y,'d' if noise(x,y,41)<0.7 else 'c')
    for i in range(16): c.px(i,0,'e'); c.px(0,i,'e'); c.px(i,15,'a'); c.px(15,i,'a')
    # copper frame and central shelf niche
    for i in range(2,14): c.px(i,2,'D'); c.px(2,i,'D'); c.px(i,13,'C'); c.px(13,i,'C')
    c.rect(3,3,12,12,'k')
    spines='FGHIFJGHIF'
    for x in range(3,13):
        col=spines[x-3]
        for y in range(4,8): c.px(x,y,col)
        for y in range(9,13): c.px(x,y,spines[(x+3)%10])
    for x in range(3,13): c.px(x,8,'C')
    # lumen seal at the centre over the shelves
    for (x,y,k) in [(7,6,'X'),(8,6,'X'),(7,9,'X'),(8,9,'X'),(6,7,'X'),(9,7,'X'),(6,8,'X'),(9,8,'X'),(7,7,'Y'),(8,7,'Y'),(7,8,'Y'),(8,8,'Y')]: c.px(x,y,k)
    for (x,y) in [(0,0),(15,0),(0,15),(15,15),(1,1),(14,1),(1,14),(14,14)]: c.px(x,y,'v')
    c.save('blk_atlas_library')
    # top: copper plate with compass rose
    P2=dict(O='#141417',a=R['copper'][1],b=R['copper'][2],c=R['copper'][3],d=R['copper'][4],e=R['copper'][5],
            p=R['parch'][4],t=R['teal'][3],T=R['teal'][5],v=R['verdigris'][3])
    c=Canvas(P2)
    for y in range(16):
        for x in range(16): c.px(x,y,'c' if noise(x,y,43)<0.75 else 'b')
    for i in range(16): c.px(i,0,'e'); c.px(0,i,'e'); c.px(i,15,'a'); c.px(15,i,'a')
    for (x,y) in [(7,2),(8,2),(7,3),(8,3),(7,12),(8,12),(7,13),(8,13),(2,7),(2,8),(3,7),(3,8),(12,7),(12,8),(13,7),(13,8)]: c.px(x,y,'p')
    c.rect(6,6,9,9,'t'); c.px(7,7,'T'); c.px(8,8,'T'); c.px(7,8,'T'); c.px(8,7,'T')
    for (x,y) in [(5,5),(10,5),(5,10),(10,10)]: c.px(x,y,'d')
    for (x,y) in [(1,1),(14,1),(1,14),(14,14),(3,12),(12,3)]: c.px(x,y,'v')
    c.save('blk_atlas_library_top')
if __name__=='__main__':
    shelf('cartographer_shelf','wood',[R['leather'][2],R['parch'][3],R['copper'][3],R['wood'][3],R['leather'][3]],seed=3)
    shelf('patina_shelf','copper',[R['verdigris'][2],R['teal'][1],R['parch'][2],R['verdigris'][3],R['copper'][2]],seed=5)
    shelf('lumen_shelf','tuff',[R['leather'][1],R['violet'][2],R['iron'][2],R['leather'][2],R['crimson'][2]],glow=(R['teal'][3],R['teal'][5]),seed=7)
    shelf('horizon_shelf','verd',[R['violet'][2],R['teal'][2],R['brass'][3],R['violet'][3],R['crimson'][2]],glow=(R['violet'][4],R['violet'][5]),seed=9)
    end('shelf_end_wood','wood',51); end('shelf_end_copper','copper',52); end('shelf_end_tuff','tuff',53); end('shelf_end_verdigris','verd',54)
    library()
