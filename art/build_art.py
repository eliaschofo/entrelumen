"""Original pixel-vector artwork. SVG source -> restricted SVG rasterizer -> PNG.

Only integer rect/polygon primitives are used. Pillow rasterizes these without
antialiasing so 32px textures remain crisp. No external assets are loaded.
"""
import argparse
import hashlib
import json
from pathlib import Path
import xml.etree.ElementTree as ET
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parents[1]
ART = ROOT / "art"
PACK = ROOT / "pack/resourcepacks/entrelumen"
ASSETS = PACK / "assets/entrelumen"
P = {"ink":"#172E32", "shadow":"#254449", "teal":"#337A79", "patina":"#65A69A",
     "ivory":"#E8DFC3", "paper":"#C7BA99", "copper":"#B8794B", "edge":"#DFAB70",
     "dark_copper":"#754E3B", "light":"#F6E8B5", "leaf":"#91B17B"}


def rect(x, y, w, h, color):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{P.get(color,color)}"/>'


def poly(points, color):
    return f'<polygon points="{points}" fill="{P.get(color,color)}"/>'


def svg(parts):
    return '<svg xmlns="http://www.w3.org/2000/svg" width="32" height="32" viewBox="0 0 32 32" shape-rendering="crispEdges">\n' + '\n'.join(parts) + '\n</svg>\n'


def compass(x=16, y=16):
    return [poly(f'{x},{y-8} {x+3},{y-2} {x+8},{y} {x+3},{y+2} {x},{y+8} {x-3},{y+2} {x-8},{y} {x-3},{y-2}', 'ivory'),
            poly(f'{x},{y-6} {x},{y} {x+5},{y}', 'copper'), rect(x-1,y-1,2,2,'ink')]


def item_art():
    atlas = [rect(5,4,21,25,'ink'),rect(4,3,21,24,'dark_copper'),rect(5,2,21,23,'copper'),
             rect(8,3,17,21,'teal'),rect(5,3,3,22,'dark_copper'),rect(9,4,14,18,'shadow'),
             rect(9,25,16,2,'ivory'),rect(9,25,13,1,'paper'),rect(24,13,4,5,'dark_copper'),rect(24,13,3,3,'edge'),
             rect(6,5,1,3,'edge'),rect(6,19,1,3,'edge'),rect(12,23,3,6,'teal'),rect(13,23,1,4,'patina')]
    atlas += [poly('16,6 18,12 22,14 18,16 16,21 14,16 10,14 14,12','ivory'),poly('16,8 16,14 20,14','edge'),rect(15,13,2,2,'ink')]
    lens=[poly('10,3 22,3 28,9 28,21 22,27 10,27 4,21 4,9','ink'),
          poly('10,4 22,4 27,9 27,21 22,26 10,26 5,21 5,9','dark_copper'),
          poly('11,5 21,5 26,10 26,20 21,25 11,25 6,20 6,10','edge'),
          poly('11,7 21,7 24,10 24,20 21,23 11,23 8,20 8,10','teal'),
          poly('12,8 21,8 23,10 23,19 20,22 12,22 9,19 9,11','patina'),
          poly('12,8 21,8 12,17 9,17 9,11','ivory'),poly('15,22 23,14 23,19 20,22','teal'),
          rect(14,3,4,3,'copper'),rect(14,24,4,3,'copper'),rect(4,13,3,4,'copper'),rect(25,13,3,4,'copper'),rect(19,10,2,2,'light')]
    notes=[poly('6,4 24,2 28,25 9,28','ink'),poly('7,5 23,3 26,24 10,26','paper'),
           poly('4,6 21,6 25,10 25,28 4,28','ink'),poly('5,7 20,7 24,11 24,27 5,27','ivory'),
           poly('20,7 20,11 24,11','paper'),rect(7,11,8,1,'paper'),rect(7,13,5,1,'paper'),
           poly('7,22 9,18 12,19 14,15 18,17 20,14 22,16 22,23 7,23','patina'),
           rect(9,19,2,2,'teal'),rect(14,16,2,2,'teal'),rect(19,15,2,2,'teal'),
           rect(7,24,9,1,'dark_copper'),rect(17,4,3,7,'copper'),rect(18,4,1,6,'edge')]
    core=[rect(8,25,16,3,'ink'),rect(10,22,12,3,'dark_copper'),rect(12,20,8,3,'copper'),
          poly('16,2 23,8 23,17 16,23 9,17 9,8','ink'),
          poly('16,3 22,8 22,17 16,22 10,17 10,8','edge'),
          poly('16,6 20,9 20,16 16,19 12,16 12,9','teal'),
          poly('16,7 18,11 16,17 14,11','light'),rect(5,9,2,9,'patina'),rect(3,11,1,5,'teal'),
          rect(25,9,2,9,'patina'),rect(28,11,1,5,'teal'),rect(8,26,16,1,'edge')]
    return {"atlas":atlas,"raw_lens":lens,"survey_notes":notes,"signal_core":core}


def frame():
    a=[rect(0,0,32,32,'ink'),rect(1,1,30,30,'dark_copper'),rect(2,2,28,28,'copper'),
       rect(4,4,24,24,'shadow'),rect(5,5,22,22,'ink'),rect(2,2,28,1,'edge'),rect(2,3,1,26,'edge'),
       rect(5,27,22,1,'patina'),rect(27,5,1,22,'teal')]
    for x,y in [(2,2),(27,2),(2,27),(27,27)]:
        a += [rect(x,y,3,3,'dark_copper'),rect(x,y,2,2,'edge')]
    return a


def block_art():
    engineering=[rect(13,7,6,4,'copper'),rect(13,21,6,4,'copper'),rect(7,13,4,6,'copper'),rect(21,13,4,6,'copper'),
                 rect(9,9,14,14,'edge'),rect(11,11,10,10,'copper'),rect(13,13,6,6,'ink'),rect(15,15,2,2,'patina')]
    arcane=[poly('16,6 24,16 16,26 8,16','patina'),poly('16,8 22,16 16,24 10,16','ink'),
            poly('16,11 19,16 16,21 13,16','ivory'),rect(7,8,2,2,'edge'),rect(23,22,2,2,'edge')]
    nature=[rect(15,11,2,14,'copper'),poly('15,18 8,16 7,10 12,10 16,15','leaf'),
            poly('17,15 19,8 25,7 24,13','patina'),rect(10,12,2,2,'ivory'),rect(21,9,2,2,'ivory'),rect(11,24,11,1,'paper')]
    exploration=compass()
    logistics=[rect(8,8,5,5,'ivory'),rect(19,8,5,5,'ivory'),rect(13,20,6,5,'copper'),
               rect(10,14,2,4,'patina'),rect(20,14,2,4,'patina'),rect(10,17,12,2,'patina'),rect(15,18,2,2,'patina'),
               rect(9,9,3,1,'paper'),rect(20,9,3,1,'paper')]
    habitation=[poly('7,15 16,7 25,15','edge'),rect(9,15,14,10,'ivory'),rect(14,18,4,7,'teal'),
                rect(10,16,3,3,'paper'),rect(19,16,3,3,'paper'),rect(15,20,2,4,'light'),rect(7,25,18,1,'dark_copper')]
    ark=[poly('9,10 12,7 20,7 23,10 23,22 20,25 12,25 9,22','copper'),
         poly('11,11 13,9 19,9 21,11 21,21 19,23 13,23 11,21','teal'),
         poly('16,10 19,16 16,22 13,16','ivory'),rect(15,14,2,4,'light')]
    for x,y in [(6,8),(24,8),(6,15),(24,15),(6,22),(24,22)]:ark += [rect(x,y,2,2,'patina')]
    result={n:frame()+g for n,g in [('engineering_module',engineering),('arcane_module',arcane),('nature_module',nature),('exploration_module',exploration),('logistics_module',logistics),('habitation_module',habitation),('ark_controller',ark)]}
    result['module_top']=frame()+[rect(8,8,16,16,'dark_copper'),rect(10,10,12,12,'teal'),rect(12,12,8,8,'shadow'),rect(14,14,4,4,'patina')]
    result['module_bottom']=frame()+[rect(8,10,16,2,'dark_copper'),rect(8,15,16,2,'dark_copper'),rect(8,20,16,2,'dark_copper')]
    return result


def raster(source):
    root=ET.fromstring(source)
    image=Image.new('RGBA',(32,32),(0,0,0,0));draw=ImageDraw.Draw(image)
    for node in root:
        tag=node.tag.split('}')[-1];a=node.attrib
        if tag=='rect':
            x,y,w,h=[int(a[k]) for k in ('x','y','width','height')]
            draw.rectangle((x,y,x+w-1,y+h-1),fill=a['fill'])
        elif tag=='polygon':
            draw.polygon([tuple(map(int,p.split(','))) for p in a['points'].split()],fill=a['fill'])
        else:raise ValueError(f'Unsupported original SVG primitive: {tag}')
    return image


def write_json(path,data):
    path.parent.mkdir(parents=True,exist_ok=True)
    path.write_text(json.dumps(data,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    pieces={**{'item/'+k:v for k,v in item_art().items()},**{'block/'+k:v for k,v in block_art().items()}}
    if args.check:
        for name,parts in pieces.items():
            source=ART/'svg'/(name+'.svg');target=ASSETS/'textures'/(name+'.png')
            assert source.read_bytes()==svg(parts).encode('utf-8'),f'stale SVG or non-LF text {name}'
            with Image.open(target) as actual:
                assert actual.size==(32,32) and actual.mode=='RGBA'
                assert actual.tobytes()==raster(svg(parts)).tobytes(),f'stale PNG {name}'
        assert json.loads((PACK/'pack.mcmeta').read_text(encoding='utf-8'))['pack']['pack_format']==34
        for name in item_art():
            model=json.loads((ASSETS/'models/item'/f'{name}.json').read_text())
            assert model['textures']['layer0']=='entrelumen:item/'+name
        for name in block_art():
            if name.startswith('module_'):continue
            model=json.loads((ASSETS/'models/block'/f'{name}.json').read_text())
            assert model['textures']['side']=='entrelumen:block/'+name
        manifest=json.loads((ART/'resourcepack-sha256.json').read_text(encoding='utf-8'))
        actual={p.relative_to(PACK).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
                for p in sorted(PACK.rglob('*'),key=lambda p:p.relative_to(PACK).as_posix()) if p.is_file()}
        assert manifest==actual,'resource-pack hash inventory is stale'
        for path in PACK.rglob('*'):
            if path.is_file() and path.suffix in ('.json','.mcmeta'):
                assert b'\r' not in path.read_bytes(),f'non-LF generated text: {path}'
        print('PASS: 13 original SVG/PNG pairs, 4 item models, 7 block models; pack_format 34. In-game load not verified.')
        return
    images={}
    for name,parts in pieces.items():
        source=ART/'svg'/(name+'.svg');target=ASSETS/'textures'/(name+'.png')
        source.parent.mkdir(parents=True,exist_ok=True);target.parent.mkdir(parents=True,exist_ok=True)
        source.write_text(svg(parts),encoding='utf-8',newline='\n');images[name]=raster(svg(parts));images[name].save(target)
    for name in item_art():
        write_json(ASSETS/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'entrelumen:item/'+name}})
    for name in block_art():
        if name.startswith('module_'):continue
        write_json(ASSETS/'models/block'/f'{name}.json',{'parent':'minecraft:block/cube_bottom_top','textures':{'side':'entrelumen:block/'+name,'top':'entrelumen:block/module_top','bottom':'entrelumen:block/module_bottom'}})
        write_json(ASSETS/'models/item'/f'{name}.json',{'parent':'entrelumen:block/'+name})
    write_json(PACK/'pack.mcmeta',{'pack':{'pack_format':34,'description':{'translate':'resourcePack.entrelumen.description'}}})
    for locale,description in [('en_us','ENTRELUMEN · Copper, maps and living light'),('es_es','ENTRELUMEN · Cobre, mapas y luz viva')]:
        write_json(ASSETS/'lang'/f'{locale}.json',{'resourcePack.entrelumen.description':description})
    icon=Image.new('RGBA',(128,128),P['ink']);icon.alpha_composite(images['item/atlas'].resize((128,128),Image.Resampling.NEAREST));icon.save(PACK/'pack.png')
    # Honest texture contact sheet, never presented as a game screenshot.
    sheet=Image.new('RGB',(1000,730),'#102329');draw=ImageDraw.Draw(sheet)
    try:font=ImageFont.truetype('C:/Windows/Fonts/consola.ttf',17);title=ImageFont.truetype('C:/Windows/Fonts/consolab.ttf',26)
    except OSError:font=title=ImageFont.load_default()
    draw.text((26,18),'ENTRELUMEN / original texture atlas',font=title,fill=P['ivory'])
    draw.text((26,54),'32 px source | 4x nearest-neighbour | artwork preview, not gameplay',font=font,fill=P['patina'])
    for index,(name,image) in enumerate(images.items()):
        x=24+(index%5)*196;y=98+(index//5)*204
        draw.rectangle((x,y,x+159,y+159),fill='#254449')
        sheet.paste(image.resize((128,128),Image.Resampling.NEAREST),(x+16,y+16),image.resize((128,128),Image.Resampling.NEAREST))
        draw.text((x,y+164),name.split('/')[-1].replace('_',' '),font=font,fill=P['ivory'])
    sheet.save(ART/'contact-sheet.png')
    write_json(ART/'palette.json',P)
    hashes={p.relative_to(PACK).as_posix():hashlib.sha256(p.read_bytes()).hexdigest()
            for p in sorted(PACK.rglob('*'),key=lambda p:p.relative_to(PACK).as_posix()) if p.is_file()}
    write_json(ART/'resourcepack-sha256.json',hashes)
    print('Generated original SVG sources, usable resource pack, and contact sheet.')


if __name__=='__main__':main()
