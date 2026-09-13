"""Native pixel artwork. Indexed sprites or integer SVG primitives -> PNG.

Only integer rect/polygon primitives are used. Pillow rasterizes these without
antialiasing so native16px items and32px blocks remain crisp. The Atlas retains
its versioned PixelLab export bytes, verified against its editable pixel grid.
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
COMPANION = ROOT / 'companion/src/main/resources/assets/entrelumen'
P = {"ink":"#172E32", "shadow":"#254449", "teal":"#337A79", "patina":"#65A69A",
     "ivory":"#E8DFC3", "paper":"#C7BA99", "copper":"#B8794B", "edge":"#DFAB70",
     "dark_copper":"#754E3B", "light":"#F6E8B5", "leaf":"#91B17B"}


def rect(x, y, w, h, color):
    return f'<rect x="{x}" y="{y}" width="{w}" height="{h}" fill="{P.get(color,color)}"/>'


def poly(points, color):
    return f'<polygon points="{points}" fill="{P.get(color,color)}"/>'


def svg(parts, size=32):
    return f'<svg xmlns="http://www.w3.org/2000/svg" width="{size}" height="{size}" viewBox="0 0 {size} {size}" shape-rendering="crispEdges">\n' + '\n'.join(parts) + '\n</svg>\n'


def compass(x=16, y=16):
    return [poly(f'{x},{y-8} {x+3},{y-2} {x+8},{y} {x+3},{y+2} {x},{y+8} {x-3},{y+2} {x-8},{y} {x-3},{y-2}', 'ivory'),
            poly(f'{x},{y-6} {x},{y} {x+5},{y}', 'copper'), rect(x-1,y-1,2,2,'ink')]


def item_art():
    return {'atlas':sprite_art('atlas'),
      'raw_lens':[poly('5,2 10,2 13,5 13,10 10,13 5,13 2,10 2,5','dark_copper'),poly('5,3 10,3 12,5 12,10 10,12 5,12 3,10 3,5','copper'),poly('5,4 10,4 11,5 11,10 10,11 5,11 4,10 4,5','teal'),rect(5,4,4,1,'ivory'),rect(4,5,1,3,'ivory'),rect(8,8,3,3,'patina')],
      'survey_notes':[rect(3,3,10,11,'paper'),rect(2,2,10,11,'ivory'),rect(3,4,5,1,'paper'),poly('3,10 5,7 7,9 9,6 10,10','patina'),rect(4,11,5,1,'dark_copper')],
      'signal_core':[rect(4,12,8,2,'dark_copper'),rect(5,11,6,1,'copper'),poly('8,2 11,5 11,9 8,12 5,9 5,5','copper'),poly('8,3 10,5 10,9 8,10 6,9 6,5','teal'),rect(7,5,1,3,'ivory')],**component_art()}


def sprite_art(name):
    """Lossless native pixel source; run-length rectangles keep generated SVG readable."""
    data = json.loads((ART/'sprites'/f'{name}.json').read_text(encoding='utf-8'))
    assert data['size'] == [16,16] and len(data['pixels']) == 16
    assert len(data['palette']) <= 20
    result = []
    for y,row in enumerate(data['pixels']):
        assert len(row) == 16
        x = 0
        while x < 16:
            value = row[x]
            end = x + 1
            while end < 16 and row[end] == value:
                end += 1
            if value != data['transparent']:
                color = data['palette'][value]
                assert len(color) == 7 and color.startswith('#')
                result.append(rect(x,y,end-x,1,color))
            x = end
    return result


COMPONENT_NAMES = {
    'calibration_frame': ('Calibration Frame', 'Marco de calibración'),
    'energy_coupler': ('Energy Coupler', 'Acoplador de energía'),
    'living_matrix': ('Living Matrix', 'Matriz viva'),
    'ration_bundle': ('Travel Rations', 'Provisiones de viaje'),
    'routing_matrix': ('Routing Matrix', 'Matriz de distribución'),
    'propagation_core': ('Propagation Core', 'Núcleo de propagación'),
    'power_regulator': ('Power Regulator', 'Regulador de energía'),
    'inventory_sensor': ('Inventory Sensor', 'Sensor de inventario'),
    'handling_core': ('Handling Core', 'Núcleo de manipulación'),
    'spectral_lens': ('Spectral Lens', 'Lente espectral'),
    'horizon_chart': ('Horizon Chart', 'Carta de horizontes'),
    'ecosystem_capsule': ('Ecosystem Capsule', 'Cápsula de ecosistema'),
    'containment_seal': ('Containment Seal', 'Sello de contención'),
    'ark_bus': ('Ark Connection Bus', 'Bus de conexión del Arca'),
    'renewal_engine': ('Renewal Engine', 'Motor de renovación'),
    'habitation_contract': ('Habitation Charter', 'Carta de habitabilidad'),
}
CAPTURE_NAMES = {
    'active': ('A capture is already running.', 'Ya hay una captura en curso.'),
    'integrated_only': ('Enter a singleplayer or integrated LAN world first.', 'Primero entrá a un mundo individual o LAN integrado.'),
    'started': ('Capture started: %s', 'Captura iniciada: %s'),
    'error': ('Capture failed: %s', 'La captura falló: %s'),
    'stopped': ('Capture saved: %s', 'Captura guardada: %s'),
}


def component_art():
    # Native16 silhouettes redrawn for inventory readability, never scaled32px source.
    return {
      'calibration_frame':[rect(3,3,10,10,'dark_copper'),rect(4,4,8,8,'edge'),rect(5,5,6,6,'#00000000'),rect(7,2,2,3,'patina'),rect(11,7,3,2,'patina')],
      'energy_coupler':[rect(2,4,3,8,'copper'),rect(11,4,3,8,'copper'),rect(5,6,6,4,'dark_copper'),rect(7,4,2,8,'teal'),rect(2,4,2,1,'edge'),rect(11,4,2,1,'edge')],
      'living_matrix':[poly('8,2 14,8 8,14 2,8','shadow'),rect(4,6,8,1,'copper'),rect(6,4,1,8,'copper'),poly('7,9 6,6 9,4 11,4 10,7','leaf'),rect(7,9,1,3,'patina')],
      'ration_bundle':[poly('5,4 11,4 13,8 12,13 4,13 3,8','paper'),poly('5,4 4,2 7,3 9,2 11,3 11,4','ivory'),rect(4,7,8,2,'teal'),rect(7,5,2,8,'teal'),rect(7,7,2,2,'copper')],
      'routing_matrix':[rect(3,3,10,10,'shadow'),rect(4,7,8,1,'copper'),rect(7,5,1,7,'copper'),rect(3,6,3,3,'ivory'),rect(10,6,3,3,'ivory'),rect(6,10,3,3,'patina')],
      'propagation_core':[poly('5,2 10,2 13,5 13,11 10,14 5,14 2,11 2,5','dark_copper'),rect(4,4,7,8,'teal'),rect(7,7,1,5,'ivory'),poly('7,8 5,6 5,4 8,5 9,7','leaf'),rect(9,4,2,2,'patina')],
      'power_regulator':[rect(5,2,6,12,'copper'),rect(6,3,4,8,'shadow'),rect(7,5,2,5,'teal'),rect(6,11,4,1,'ivory'),rect(3,5,2,2,'edge'),rect(11,9,2,2,'edge')],
      'inventory_sensor':[poly('2,7 5,4 10,4 14,7 10,11 5,11','copper'),poly('4,7 6,5 9,5 11,7 9,9 6,9','ivory'),rect(7,5,2,5,'teal'),rect(7,6,1,2,'ink'),rect(4,12,8,1,'shadow')],
      'handling_core':[rect(6,2,4,4,'copper'),rect(7,3,2,1,'patina'),rect(7,6,2,3,'dark_copper'),rect(3,8,10,2,'copper'),rect(3,10,2,4,'edge'),rect(11,10,2,4,'copper'),rect(5,12,2,2,'edge'),rect(9,12,2,2,'copper')],
      'spectral_lens':[poly('8,2 13,5 13,10 8,14 3,10 3,5','patina'),poly('8,3 11,5 11,10 8,12 5,10 5,5','shadow'),poly('8,4 6,6 6,9 9,11 7,11 5,9 5,6','ivory'),rect(10,6,2,2,'edge')],
      'horizon_chart':[rect(3,3,10,10,'ivory'),rect(2,2,2,12,'paper'),rect(12,2,2,12,'paper'),poly('5,10 7,7 9,9 10,6 11,10','patina'),rect(8,4,1,3,'copper'),rect(7,5,3,1,'copper')],
      'ecosystem_capsule':[rect(5,2,6,2,'copper'),poly('5,4 10,4 12,6 12,13 4,13 4,6','patina'),rect(5,6,1,5,'ivory'),rect(6,12,5,1,'paper'),rect(8,8,1,4,'dark_copper'),poly('8,9 6,7 7,6 9,8 10,6 11,6 10,9','leaf')],
      'containment_seal':[poly('5,3 10,2 13,6 12,10 8,12 3,10 2,6','dark_copper'),poly('5,4 9,3 11,6 10,9 7,10 4,8','copper'),rect(6,5,4,1,'edge'),rect(7,6,1,3,'ivory'),poly('4,10 7,11 5,14 3,13','teal'),poly('9,11 11,10 13,13 10,14','patina')],
      'ark_bus':[rect(2,5,12,6,'teal'),rect(3,6,10,1,'copper'),rect(3,9,10,1,'edge'),rect(7,6,2,4,'ivory')]+[rect(x,y,1,2,'copper') for x in (3,6,9,12) for y in (3,11)],
      'renewal_engine':[poly('3,3 11,3 13,5 13,7 10,7 10,5 5,5 5,7 2,4','copper'),poly('12,13 4,13 2,11 2,9 5,9 5,11 10,11 10,9 14,12','patina'),poly('8,5 10,7 9,10 7,11 5,9 6,6','leaf'),rect(7,8,1,4,'ivory')],
      'habitation_contract':[rect(3,2,9,12,'paper'),rect(4,2,8,11,'ivory'),poly('5,7 8,4 11,7','copper'),rect(6,7,4,3,'teal'),rect(8,8,1,2,'patina'),rect(5,11,5,1,'paper'),rect(11,11,3,3,'copper')],
    }


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
    size=int(root.attrib['width']);image=Image.new('RGBA',(size,size),(0,0,0,0));draw=ImageDraw.Draw(image)
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


def atlas_export():
    data = (ART/'sprites/atlas.png').read_bytes()
    source = json.loads((ART/'sprites/atlas.json').read_text(encoding='utf-8'))
    assert hashlib.sha256(data).hexdigest() == source['provenance']['export_sha256']
    return data


def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args()
    pieces={**{'item/'+k:v for k,v in item_art().items()},**{'block/'+k:v for k,v in block_art().items()}}
    if args.check:
        for name,parts in pieces.items():
            source=ART/'svg'/(name+'.svg');target=ASSETS/'textures'/(name+'.png')
            assert source.read_bytes()==svg(parts,16 if name.startswith('item/') else 32).encode('utf-8'),f'stale SVG or non-LF text {name}'
            with Image.open(target) as actual:
                assert actual.size==((16,16) if name.startswith('item/') else (32,32)) and actual.mode=='RGBA'
                assert actual.tobytes()==raster(svg(parts,16 if name.startswith('item/') else 32)).tobytes(),f'stale PNG {name}'
        assert (ASSETS/'textures/item/atlas.png').read_bytes() == atlas_export()
        assert json.loads((PACK/'pack.mcmeta').read_text(encoding='utf-8'))['pack']['pack_format']==34
        for name in item_art():
            model=json.loads((ASSETS/'models/item'/f'{name}.json').read_text())
            assert model['textures']['layer0']=='entrelumen:item/'+name
        assert set(component_art())==set(COMPONENT_NAMES)
        for name in item_art():
            native=COMPANION/'textures/item'/f'{name}.png'
            assert native.read_bytes()==(ASSETS/'textures/item'/f'{name}.png').read_bytes(),f'native texture drift: {name}'
            assert (COMPANION/'models/item'/f'{name}.json').read_bytes()==(ASSETS/'models/item'/f'{name}.json').read_bytes()
        for index,locale in enumerate(('en_us','es_es')):
            native_lang=json.loads((COMPANION/'lang'/f'{locale}.json').read_text(encoding='utf-8'))
            for name,names in COMPONENT_NAMES.items():
                assert native_lang['item.entrelumen.'+name]==names[index]
            for name,names in CAPTURE_NAMES.items():
                assert native_lang['entrelumen.capture.'+name]==names[index]
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
        for name in block_art():
            assert (COMPANION/'textures/block'/f'{name}.png').read_bytes() == (ASSETS/'textures/block'/f'{name}.png').read_bytes()
            if not name.startswith('module_'):
                for kind in ('item','block'):
                    assert (COMPANION/'models'/kind/f'{name}.json').read_bytes() == (ASSETS/'models'/kind/f'{name}.json').read_bytes()
        print('PASS: 29 deterministic SVG/PNG pairs, 20 item models, 7 block models, complete native mod/resource-pack artwork parity and EN/ES names. In-game visual review pending.')
        return
    images={}
    for name,parts in pieces.items():
        source=ART/'svg'/(name+'.svg');target=ASSETS/'textures'/(name+'.png')
        source.parent.mkdir(parents=True,exist_ok=True);target.parent.mkdir(parents=True,exist_ok=True)
        source.write_text(svg(parts,16 if name.startswith('item/') else 32),encoding='utf-8',newline='\n')
        images[name]=raster(svg(parts,16 if name.startswith('item/') else 32))
        if name == 'item/atlas':
            with Image.open(ART/'sprites/atlas.png') as original:
                assert original.convert('RGBA').tobytes() == images[name].tobytes(), 'Atlas indexed source differs from its native export'
            target.write_bytes(atlas_export())
        else:
            images[name].save(target)
    for name in item_art():
        write_json(ASSETS/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'entrelumen:item/'+name}})
        target=COMPANION/'textures/item'/f'{name}.png'
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes((ASSETS/'textures/item'/f'{name}.png').read_bytes())
        write_json(COMPANION/'models/item'/f'{name}.json',{'parent':'minecraft:item/generated','textures':{'layer0':'entrelumen:item/'+name}})
    for name in block_art():
        target=COMPANION/'textures/block'/f'{name}.png'
        target.parent.mkdir(parents=True,exist_ok=True)
        target.write_bytes((ASSETS/'textures/block'/f'{name}.png').read_bytes())
        if name.startswith('module_'):continue
        for destination in (ASSETS,COMPANION):
            write_json(destination/'models/block'/f'{name}.json',{'parent':'minecraft:block/cube_bottom_top','textures':{'side':'entrelumen:block/'+name,'top':'entrelumen:block/module_top','bottom':'entrelumen:block/module_bottom'}})
            write_json(destination/'models/item'/f'{name}.json',{'parent':'entrelumen:block/'+name})
    write_json(PACK/'pack.mcmeta',{'pack':{'pack_format':34,'description':{'translate':'resourcePack.entrelumen.description'}}})
    for index,(locale,description) in enumerate([('en_us','ENTRELUMEN · Copper, maps and living light'),('es_es','ENTRELUMEN · Cobre, mapas y luz viva')]):
        names={'item.entrelumen.'+key:value[index] for key,value in COMPONENT_NAMES.items()}
        write_json(ASSETS/'lang'/f'{locale}.json',{'resourcePack.entrelumen.description':description,**names})
        native_path=COMPANION/'lang'/f'{locale}.json'
        native=json.loads(native_path.read_text(encoding='utf-8'))
        native.update(names)
        native.update({'entrelumen.capture.'+key:value[index] for key,value in CAPTURE_NAMES.items()})
        write_json(native_path,native)
    icon=Image.new('RGBA',(128,128),P['ink']);icon.alpha_composite(images['item/atlas'].resize((128,128),Image.Resampling.NEAREST));icon.save(PACK/'pack.png')
    atlas_preview=Image.new('RGBA',(384,160),P['ink'])
    for index,color in enumerate(('#8B8B8B',P['ivory'],P['ink'])):
        panel=Image.new('RGBA',(112,144),color)
        panel.alpha_composite(images['item/atlas'].resize((48,48),Image.Resampling.NEAREST),(32,32))
        panel.alpha_composite(images['item/atlas'],(48,108))
        atlas_preview.alpha_composite(panel,(8+128*index,8))
    atlas_preview.save(ART/'atlas-inventory-preview.png')
    # Honest texture contact sheet, never presented as a game screenshot.
    sheet=Image.new('RGB',(1000,110+204*((len(images)+4)//5)),'#102329');draw=ImageDraw.Draw(sheet)
    font=ImageFont.load_default(size=16);title=ImageFont.load_default(size=24)
    draw.text((26,18),'ENTRELUMEN / original texture atlas',font=title,fill=P['ivory'])
    draw.text((26,54),'16px items / 32px blocks | nearest preview, not gameplay',font=font,fill=P['patina'])
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
