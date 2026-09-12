"""Render original publication vectors; no network or game imagery."""
from pathlib import Path
import xml.etree.ElementTree as E
from PIL import Image, ImageDraw, ImageFont

HERE=Path(__file__).resolve().parent
INK='#172E32'; TEAL='#337A79'; IVORY='#E8DFC3'; COPPER='#B8794B'; PATINA='#65A69A'

def rect(x,y,w,h,c):return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{c}"/>'
def line(x,y,a,b,c,width=2):return f'<line x1="{x}" y1="{y}" x2="{a}" y2="{b}" stroke="{c}" stroke-width="{width}"/>'
def text(x,y,s,size,c):return f'<text x="{x}" y="{y}" font-size="{size}" font-family="Consolas" fill="{c}">{s}</text>'

def atlas(x,y,scale):
    nodes=[]
    for node in E.fromstring((HERE.parent/'svg/item/atlas.svg').read_text()):
        tag=node.tag.split('}')[-1];a=node.attrib
        if tag=='rect':nodes.append(rect(x+int(a['x'])*scale,y+int(a['y'])*scale,int(a['width'])*scale,int(a['height'])*scale,a['fill']))
        elif tag=='polygon':
            points=' '.join(f'{x+int(p.split(",")[0])*scale},{y+int(p.split(",")[1])*scale}' for p in a['points'].split())
            nodes.append(f'<polygon points="{points}" fill="{a["fill"]}"/>')
    return nodes

def render(name,w,h,nodes):
    source=f'<svg xmlns="http://www.w3.org/2000/svg" width="{w}" height="{h}" viewBox="0 0 {w} {h}">\n'+ '\n'.join(nodes)+'\n</svg>\n'
    (HERE/(name+'.svg')).write_text(source,encoding='utf-8',newline='\n')
    im=Image.new('RGB',(w,h),INK);d=ImageDraw.Draw(im)
    for node in E.fromstring(source):
        tag=node.tag.split('}')[-1];a=node.attrib
        if tag=='rect':
            x,y,ww,hh=[int(a[k]) for k in ('x','y','width','height')];d.rectangle((x,y,x+ww-1,y+hh-1),fill=a['fill'])
        elif tag=='line':d.line(tuple(int(a[k]) for k in ('x1','y1','x2','y2')),fill=a['stroke'],width=int(a['stroke-width']))
        elif tag=='polygon':d.polygon([tuple(map(int,p.split(','))) for p in a['points'].split()],fill=a['fill'])
        elif tag=='text':
            font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',int(a['font-size']))
            d.text((int(a['x']),int(a['y'])),node.text,font=font,fill=a['fill'],anchor='ls')
    im.save(HERE/(name+'.png'))

def main():
    avatar=[rect(0,0,400,400,INK),rect(24,24,352,352,COPPER),rect(28,28,344,344,INK)]
    for x in (64,200,336):avatar += [line(x,40,x,360,'#254449'),line(40,x,360,x,'#254449')]
    avatar += atlas(72,60,8)
    avatar += [rect(192,32,16,4,PATINA),rect(192,364,16,4,PATINA),rect(32,192,4,16,PATINA),rect(364,192,4,16,PATINA)]
    render('avatar-400',400,400,avatar)
    for locale,subtitle,stage,concept in [('en','THE LIVING ATLAS','IN DEVELOPMENT','ORIGINAL CONCEPT ART'),('es','EL ATLAS VIVO','EN DESARROLLO','ARTE CONCEPTUAL ORIGINAL')]:
        nodes=[rect(0,0,1200,480,INK)]
        # Survey grid and broken routes are original diagrammatic artwork.
        for x in range(24,1200,80):nodes += [line(x,16,x,464,'#213B40',1)]
        for y in range(24,480,80):nodes += [line(16,y,1184,y,'#213B40',1)]
        nodes += [line(56,392,744,392,COPPER),line(744,392,840,296,COPPER),line(840,296,1048,296,COPPER)]
        for x,y in [(56,392),(264,392),(488,392),(744,392),(840,296),(1048,296)]:nodes += [rect(x-5,y-5,10,10,PATINA),rect(x-2,y-2,4,4,IVORY)]
        nodes += atlas(870,70,8)
        nodes += [rect(56,64,32,4,COPPER),text(56,146,'ENTRELUMEN',70,IVORY),text(60,198,subtitle,25,PATINA),
                  text(60,276,stage,20,IVORY),text(60,316,'Minecraft 1.21.1 / NeoForge',18,COPPER),text(60,448,concept,15,PATINA)]
        render('cover-'+locale,1200,480,nodes)
    print('Rendered original avatar 400px and bilingual covers 1200x480 from local SVG art.')

if __name__=='__main__':main()
