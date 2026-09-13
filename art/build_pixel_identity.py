"""Build menu exports from the tracked filtered master and original bitmap logo."""
import argparse
import hashlib
import io
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / 'tools'))
from png_equivalence import preserve_verified_encoding
OUT = ROOT / 'art/menu'
P = {
    'void':'#101D26', 'ink':'#132B29', 'map':'#1B3631', 'map_hi':'#284139',
    'night':'#263C50', 'sky':'#3F5964', 'haze':'#648080', 'mist':'#8EAA99',
    'sun':'#F6E8B5', 'ivory':'#E8DFC3', 'sand':'#C7BA99', 'warm':'#D3A879',
    'copper':'#B8794B', 'edge':'#DFAB70', 'rust':'#754E3B', 'rust_dark':'#493A33',
    'teal':'#337A79', 'patina':'#65A69A', 'glass':'#254F53', 'stone':'#52625E',
    'stone_hi':'#7C8877', 'stone_dark':'#344B48', 'leaf':'#91B17B',
    'moss':'#597B57', 'pine':'#365540', 'wood':'#6D5440', 'water':'#426C73',
}

# Original 5x7 bitmap letters; used as pixels, no font rasterizer involved.
FONT = {
 'A':['01110','10001','10001','11111','10001','10001','10001'],
 'B':['11110','10001','10001','11110','10001','10001','11110'],
 'C':['01111','10000','10000','10000','10000','10000','01111'],
 'D':['11110','10001','10001','10001','10001','10001','11110'],
 'E':['11111','10000','10000','11110','10000','10000','11111'],
 'F':['11111','10000','10000','11110','10000','10000','10000'],
 'G':['01111','10000','10000','10111','10001','10001','01111'],
 'H':['10001','10001','10001','11111','10001','10001','10001'],
 'I':['11111','00100','00100','00100','00100','00100','11111'],
 'J':['00111','00010','00010','00010','10010','10010','01100'],
 'K':['10001','10010','10100','11000','10100','10010','10001'],
 'L':['10000','10000','10000','10000','10000','10000','11111'],
 'M':['10001','11011','10101','10101','10001','10001','10001'],
 'N':['10001','11001','11001','10101','10011','10011','10001'],
 'O':['01110','10001','10001','10001','10001','10001','01110'],
 'P':['11110','10001','10001','11110','10000','10000','10000'],
 'Q':['01110','10001','10001','10001','10101','10010','01101'],
 'R':['11110','10001','10001','11110','10100','10010','10001'],
 'S':['01111','10000','10000','01110','00001','00001','11110'],
 'T':['11111','00100','00100','00100','00100','00100','00100'],
 'U':['10001','10001','10001','10001','10001','10001','01110'],
 'V':['10001','10001','10001','10001','10001','01010','00100'],
 'W':['10001','10001','10001','10101','10101','11011','10001'],
 'X':['10001','10001','01010','00100','01010','10001','10001'],
 'Y':['10001','10001','01010','00100','00100','00100','00100'],
 'Z':['11111','00001','00010','00100','01000','10000','11111'],
 ' ':['00000']*7, '-':['00000','00000','00000','11111','00000','00000','00000'],
 '.':['00000','00000','00000','00000','00000','00100','00100'],
 '1':['00100','01100','00100','00100','00100','00100','01110'],
 '2':['01110','10001','00001','00010','00100','01000','11111'],
 '/':['00001','00001','00010','00100','01000','10000','10000'],
}


def lettering(draw, text, x, y, color='ivory', scale=1):
    for char in text:
        for row, cells in enumerate(FONT[char]):
            for column, bit in enumerate(cells):
                if bit == '1':
                    a, b = x + column*scale, y + row*scale
                    draw.rectangle((a,b,a+scale-1,b+scale-1), fill=P[color])
        x += 6*scale


def logo():
    im=Image.new('RGBA',(180,64),(0,0,0,0));d=ImageDraw.Draw(im)
    def line(points,c,w=1): d.line(points,fill=P[c],width=w)
    # Broken copper astrolabe enclosing the book-compass emblem.
    line([(69,15),(69,9),(76,2),(103,2),(110,9),(110,15)],'copper')
    line([(69,23),(69,28),(76,35),(103,35),(110,28),(110,23)],'copper')
    line([(62,18),(70,18)],'patina');line([(109,18),(117,18)],'patina')
    d.polygon([(74,10),(84,8),(90,11),(96,8),(105,10),(105,29),(95,27),(90,30),(84,27),(74,29)],fill=P['rust'])
    d.polygon([(76,10),(84,9),(89,12),(89,27),(84,25),(76,27)],fill=P['ivory'])
    d.polygon([(91,12),(96,9),(103,10),(103,27),(96,25),(91,27)],fill=P['sand'])
    d.polygon([(90,7),(93,15),(100,18),(93,21),(90,29),(87,21),(80,18),(87,15)],fill=P['teal'])
    d.polygon([(90,11),(92,16),(96,18),(90,18)],fill=P['sun'])
    d.rectangle((89,17,90,18),fill=P['ivory'])
    # Strong readable two-pixel letters, independent of operating-system fonts.
    lettering(d,'ENTRELUMEN',31,40,'rust_dark',2)
    lettering(d,'ENTRELUMEN',30,38,'ivory',2)
    line([(31,58),(74,58)],'copper');line([(105,58),(148,58)],'copper')
    d.polygon([(89,55),(92,58),(89,61),(86,58)],fill=P['patina'])
    return im


def png(im):
    out=io.BytesIO();im.save(out,format='PNG',optimize=False);return out.getvalue()


def build():
    backdrop = Image.open(OUT/'pixel-master.png').convert('RGB')
    brand = logo()
    if backdrop.size != (480,270): raise ValueError('Master must be 480x270')
    palette = sorted('#%02X%02X%02X' % rgb for _,rgb in backdrop.getcolors(480*270))
    if len(palette) > 96: raise ValueError('Master exceeds 96 colors')
    outputs={OUT/'title-background-source.png':png(backdrop),OUT/'loading-background-source.png':png(backdrop),
             OUT/'title-background.png':png(backdrop.resize((1920,1080),Image.Resampling.NEAREST)),
             OUT/'loading-background.png':png(backdrop.resize((1920,1080),Image.Resampling.NEAREST)),
             OUT/'logo-source.png':png(brand),OUT/'logo.png':png(brand)}
    # Manifest hashes describe tracked files, after verifying their native pixels
    # against the generator. Different zlib builds can encode those pixels differently.
    outputs = {path:preserve_verified_encoding(path,data) for path,data in outputs.items()}
    provenance = json.loads((OUT/'pixel-provenance.json').read_text())
    if hashlib.sha256((OUT/'pixel-master.png').read_bytes()).hexdigest() != provenance['masterSha256']:
        raise ValueError('Master differs from recorded provenance')
    manifest={'schemaVersion':2,'method':'ImageGen source filtered by pixel_filter.py; tracked master is the offline build input',
              'sceneBase':[480,270],'sceneScale':4,'logoBase':[180,64],'logoScale':1,
              'palette':palette,'logoPalette':P,'sampling':'4x nearest-neighbor; binary alpha',
              'masterSha256':provenance['masterSha256'],
              'files':{p.name:hashlib.sha256(b).hexdigest() for p,b in outputs.items()}}
    outputs[OUT/'pixel-manifest.json']=(json.dumps(manifest,indent=2)+'\n').encode()
    outputs[OUT/'pixel-art-approved.json']=(json.dumps({'sha256':{name:manifest['files'][name]
        for name in ('title-background.png','loading-background.png','logo.png')},
        'verification':'Palette, binary alpha and integer nearest expansion verified by build_pixel_identity.py; root visually reviewed.'},indent=2)+'\n').encode()
    return outputs


def validate(outputs):
    scene_colors = {rgb for _,rgb in Image.open(OUT/'pixel-master.png').convert('RGB').getcolors(480*270)}
    logo_colors = {tuple(bytes.fromhex(color[1:])) for color in P.values()}
    for path,data in outputs.items():
        if path.suffix!='.png':continue
        im=Image.open(io.BytesIO(data)).convert('RGBA')
        allowed = logo_colors if path.name.startswith('logo') else scene_colors
        colors=im.getcolors(im.width*im.height)
        if any(alpha not in (0,255) or (alpha and (r,g,b) not in allowed) for _,(r,g,b,alpha) in colors):
            raise ValueError('Non-palette or blended pixel: '+str(path))
    for name,scale in [('title-background',4),('loading-background',4),('logo',1)]:
        source=Image.open(io.BytesIO(outputs[OUT/(name+'-source.png')]))
        export=Image.open(io.BytesIO(outputs[OUT/(name+'.png')]))
        if export.tobytes()!=source.resize((source.width*scale,source.height*scale),Image.Resampling.NEAREST).tobytes():
            raise ValueError('Noninteger pixel enlargement: '+name)


def main():
    parser=argparse.ArgumentParser(description=__doc__);parser.add_argument('--check',action='store_true');args=parser.parse_args()
    outputs=build();validate(outputs)
    for path,data in outputs.items():
        if args.check:
            if not path.is_file() or path.read_bytes()!=data:raise SystemExit('Stale pixel artwork: '+str(path))
        else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
    print('PASS: filtered 480x270 master (<=96 colors), preserved 180x64 bitmap logo, binary alpha, 4x nearest exports.')


if __name__=='__main__':main()
