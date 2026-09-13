"""Original native GUI textures; deterministic palette pixels, no font/image inputs."""
import argparse, hashlib, io, json
from pathlib import Path
from PIL import Image, ImageDraw
ROOT=Path(__file__).resolve().parents[2]
HERE=ROOT/'art/gui'
DEST=ROOT/'pack/config/fancymenu/assets/entrelumen/gui'
P={'ink':'#132B29','pine':'#1B3631','raised':'#284139','deep':'#101D26','copper':'#B8794B','edge':'#DFAB70','rust':'#754E3B','dark':'#493A33','patina':'#65A69A','ivory':'#E8DFC3','sand':'#C7BA99'}
def canvas(w,h): return Image.new('RGBA',(w,h),(0,0,0,0))
def button(state):
 im=canvas(160,20);d=ImageDraw.Draw(im)
 active=state!='disabled';hover=state=='hover'
 d.rectangle((0,0,159,19),fill=P['deep'])
 d.rectangle((1,1,158,18),fill=P['copper' if active else 'dark'])
 d.line((1,1,158,1),fill=P['edge' if hover else 'rust'])
 d.line((1,2,1,17),fill=P['edge' if hover else 'copper' if active else 'rust'])
 d.line((2,18,158,18),fill=P['dark'])
 d.rectangle((3,3,156,16),fill=P['raised' if hover else 'pine' if active else 'ink'])
 # Quiet irregular grain. Text zone remains dark, with no icon beneath labels.
 for y in range(4,16):
  for x in range(4,156):
   n=(x*19+y*37+x*y*3)%113
   if n<5: d.point((x,y),fill=P['pine' if hover else 'ink'])
 for x in (2,157):
  d.point((x,2),fill=P['ivory' if hover else 'edge' if active else 'rust'])
  d.point((x,17),fill=P['rust'])
 if hover:
  d.line((5,2,154,2),fill=P['patina'])
  d.point((5,17),fill=P['patina']);d.point((154,17),fill=P['patina'])
 return im

def icon(kind):
 im=canvas(16,16);d=ImageDraw.Draw(im)
 if kind=='atlas':
  d.rectangle((2,2,12,13),fill=P['deep']);d.rectangle((3,1,12,12),fill=P['rust'])
  d.rectangle((4,2,11,10),fill=P['pine']);d.line((4,11,11,11),fill=P['ivory'])
  d.line((4,12,11,12),fill=P['sand']);d.line((3,2,3,11),fill=P['copper'])
  d.line((5,3,10,3),fill=P['raised']);d.point((5,7),fill=P['patina'])
  d.line((6,6,9,6),fill=P['copper']);d.line((8,4,8,8),fill=P['edge'])
  d.point((7,7),fill=P['patina']);d.point((12,5),fill=P['edge'])
 elif kind=='compass':
  d.polygon([(5,1),(10,1),(14,5),(14,10),(10,14),(5,14),(1,10),(1,5)],fill=P['deep'])
  d.polygon([(5,2),(10,2),(13,5),(13,10),(10,13),(5,13),(2,10),(2,5)],fill=P['copper'])
  d.rectangle((4,4,11,11),fill=P['ink']);d.line((5,3,10,3),fill=P['edge'])
  d.line((3,5,3,9),fill=P['edge']);d.line((5,12,10,12),fill=P['rust'])
  d.polygon([(8,4),(8,8),(5,11)],fill=P['patina']);d.polygon([(9,4),(9,8),(6,11)],fill=P['ivory'])
  d.point((8,8),fill=P['copper'])
 return im

def tile(kind):
 im=canvas(16,16);d=ImageDraw.Draw(im);d.rectangle((0,0,15,15),fill=P['pine'])
 for y in range(16):
  for x in range(16):
   if (x*17+y*29+x*y)%31<4:d.point((x,y),fill=P['ink'])
 if kind=='copper_frame':
  d.rectangle((0,0,15,15),outline=P['deep']);d.rectangle((1,1,14,14),outline=P['copper'])
  d.line((2,2,13,2),fill=P['edge']);d.line((2,3,2,12),fill=P['rust'])
  for xy in [(1,1),(14,1),(1,14),(14,14)]:d.point(xy,fill=P['patina'])
 return im

def encode(im):
 b=io.BytesIO();im.save(b,format='PNG',optimize=False);return b.getvalue()
def luminance(rgb):
 c=[v/255 for v in rgb];return sum(a*(v/12.92 if v<=.04045 else ((v+.055)/1.055)**2.4) for a,v in zip((.2126,.7152,.0722),c))
def outputs():
 images={**{'button_'+s:button(s) for s in ('normal','hover','disabled')},**{'icon_'+s:icon(s) for s in ('atlas','compass')},**{'tile_'+s:tile(s) for s in ('pine','copper_frame')}}
 palette={tuple(bytes.fromhex(v[1:])) for v in P.values()}
 for name,im in images.items():
  for count,c in im.getcolors(im.width*im.height):
   assert c[3] in (0,255)
   assert c[3]==0 or c[:3] in palette
 text=tuple(bytes.fromhex(P['ivory'][1:]));ratios={}
 for s in ('normal','hover','disabled'):
  im=images['button_'+s];cols={im.getpixel((x,y))[:3] for y in range(4,16) for x in range(8,152)}
  ratios[s]=round(min((luminance(text)+.05)/(luminance(c)+.05) for c in cols),2)
  assert ratios[s]>=4.5
 assert len({encode(images['button_'+s]) for s in ('normal','hover','disabled')})==3
 out={}
 for name,im in images.items():
  for folder in (HERE,DEST):out[folder/(name+'.png')]=encode(im)
 sheet=Image.new('RGBA',(176,100),P['deep'])
 for i,s in enumerate(('normal','hover','disabled')):sheet.paste(images['button_'+s],(8,4+24*i))
 for i,s in enumerate(('icon_atlas','icon_compass','tile_pine','tile_copper_frame')):sheet.paste(images[s],(8+24*i,80),images[s])
 out[HERE/'contact-sheet.png']=encode(sheet.resize((704,400),Image.Resampling.NEAREST))
 manifest={'palette':P,'native_dimensions':{n:list(i.size) for n,i in images.items()},'text_color':P['ivory'],'text_minimum_contrast':ratios,'text_insets':[8,4,8,4],'scaling':'native 1:1 GUI units or integer nearest; no smoothing; button not nine-slice','states':'normal, hover (also keyboard focus), disabled; native localized text rendered separately','provenance':'Original deterministic pixel drawing, no game textures, external images or fonts.','sha256':{n:hashlib.sha256(encode(i)).hexdigest() for n,i in images.items()}}
 out[HERE/'manifest.json']=(json.dumps(manifest,indent=2)+'\n').encode()
 return out

def main():
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
 for path,data in outputs().items():
  if a.check:
   assert path.exists() and path.read_bytes()==data,f'Stale: {path}'
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 print('PASS: deterministic native pixels, 11-color palette, binary alpha, 3 distinct states, text contrast >=4.5:1')
if __name__=='__main__':main()
