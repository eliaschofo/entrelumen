"""Spawner augmenters: copper medallions. Basic tiers in fresh copper, strong ones in verdigris."""
import sys; sys.path.insert(0,'.')
from draw_items import Canvas
from palette import RAMPS as R
STRONG={'echoing','ignore_conditions','ignore_light','ignore_players','no_ai','redstone_control'}
# 7x7 emblems; key letters map to colours given per emblem
E={
 'burning':(["...a...","..aa...","..aba..",".abba..",".abcba.","abccba.",".bbbb.."],dict(a=R['crimson'][3],b=R['straw'][2],c=R['straw'][3])),
 'echoing':(["..aaa..",".a...a.","a.bbb.a","a.b.b.a","a.bbb.a",".a...a.","..aaa.."],dict(a=R['teal'][1],b=R['teal'][3])),
 'ignore_conditions':(["...a...","..aba..",".abcba.","abcccba",".abcba.","..aba..","...a..."],dict(a=R['teal'][1],b=R['teal'][3],c=R['teal'][5])),
 'ignore_light':(["..aaa..",".aa....","aa.....","aa..b..","aa.....",".aa....","..aaa.."],dict(a=R['straw'][3],b=R['straw'][2])),
 'ignore_players':(["a......",".a.....","..bbb..",".bcac..","..bbb..",".....a.","......a"],dict(a=R['crimson'][3],b=R['parch'][4],c=R['ink'][0])),
 'initial_health':([".......",".aa.aa.","abbabba","abbbbba",".abbba.","..aba..","...a..."],dict(a=R['crimson'][1],b=R['crimson'][3])),
 'max_delay':(["aaaaa..",".bbb...",".bcb...","..c....",".b.b...",".bccb..","aaaaa.."],dict(a=R['wood'][3],b=R['glass'][3],c=R['straw'][3])),
 'min_delay':(["..aaa..",".a...a.","a..b..a","a..bb.a","a.....a",".a...a.","..aaa.."],dict(a=R['parch'][4],b=R['ink'][0])),
 'max_nearby':([".......",".aa.aa.",".aa.aa.",".......","..aa...","..aa...","......."],dict(a=R['leaf'][3])),
 'no_ai':(["aaaa...","...a...","..a....",".a.....","aaaa...","....bb.","....bb."],dict(a=R['parch'][5],b=R['parch'][3])),
 'player_range':(["aaa.aaa","a.....a","a..b..a",".......","a..b..a","a.....a","aaa.aaa"],dict(a=R['parch'][4],b=R['teal'][3])),
 'redstone_control':(["...a...","..aba..","...c...","...c...","...c...","..ccc..","......."],dict(a=R['crimson'][3],b=R['crimson'][5],c=R['wood'][3])),
 'silent':(["...a...","..aa...","aaaa.b.","aaaa..b","aaaa.b.","..aa...","...a..."],dict(a=R['parch'][4],b=R['crimson'][3])),
 'spawn_count':(["...a...","...a...",".aaaaa.","...a...","...a...",".......","......."],dict(a=R['straw'][3])),
 'spawn_range':(["...a...","..a.a..",".a...a.","a..b..a",".a...a.","..a.a..","...a..."],dict(a=R['parch'][4],b=R['teal'][3])),
 'youthful':([".......","..a.a..","...a...","...b...","...b...","..ccc..","......."],dict(a=R['leaf'][4],b=R['leaf'][2],c=R['wood'][2])),
}
def draw(name):
    strong=name in STRONG
    rim=R['verdigris'] if strong else R['copper']
    P=dict(O='#141417',r=rim[1],R=rim[2],f=rim[3],F=rim[4],h=rim[5],x=R['copper'][2] if strong else R['copper'][1])
    emb,cols=E[name]
    keys='ABCDEFG'
    for i,(k,v) in enumerate(cols.items()): P[keys[i]]=v
    c=Canvas(P)
    # medallion disc r=6.5 centred (7.5,7.5)
    c.disc(7.5,7.5,6.6,'r')
    c.disc(7.5,7.5,5.6,'f')
    for y in range(16):
        for x in range(16):
            g=c.get(x,y)
            if g=='f' and (x+y)<12: c.px(x,y,'F')
    c.px(4,4,'h'); c.px(5,3,'h'); c.px(3,5,'h')
    # rim light/shadow
    for y in range(16):
        for x in range(16):
            if c.get(x,y)=='r':
                if x+y<14: c.px(x,y,'R')
                elif x+y>17: c.px(x,y,'x')
    # little hanging loop at the top (it is a badge)
    c.px(7,0,'r'); c.px(8,0,'r'); c.px(7,1,'R')
    # emblem
    for yy,row in enumerate(emb):
        for xx,ch in enumerate(row):
            if ch!='.': c.px(4+xx,4+yy,keys[list(cols).index(ch)])
    c.outline(); c.save('augment_'+name)
if __name__=='__main__':
    for n in E: draw(n)
