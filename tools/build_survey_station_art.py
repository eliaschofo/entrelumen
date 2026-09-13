"""Original survey-station geometry and native textures; software preview is not in-game."""
import argparse,io,json,hashlib
from pathlib import Path
from PIL import Image,ImageDraw
from png_equivalence import same_png_pixels
ROOT=Path(__file__).resolve().parents[1]
P=json.loads((ROOT/'art/sprites/survey_station.palette.json').read_text())['palette']
def texture(kind):
 im=Image.new('RGB',(16,16),P[{'wood':'E','copper':'F','map':'L','lens':'H'}[kind]]);d=ImageDraw.Draw(im)
 for y in range(16):
  for x in range(16):
   n=(x*17+y*31+x*y*7)%41
   if kind=='wood' and (n<5 or x%5==0):d.point((x,y),fill=P['F' if n<2 else 'E'])
   if kind=='copper' and n<9:d.point((x,y),fill=P['H' if n<5 else 'I'])
   if kind=='map' and n<3:d.point((x,y),fill=P['P'])
 if kind=='wood':
  for y in [3,11]:d.line((1,y,14,y),fill=P['F'])
 if kind=='copper':d.line((0,0,15,0),fill=P['G']);d.line((0,1,0,15),fill=P['G']);d.line((15,1,15,15),fill=P['E'])
 if kind=='map':
  d.rectangle((1,1,14,14),outline=P['K']);d.line([(3,12),(5,10),(5,7),(8,7),(10,4),(13,4)],fill=P['H'],width=1)
  d.line([(3,4),(5,3),(7,4),(8,3)],fill=P['K']);d.line((10,9,10,13),fill=P['F']);d.line((8,11,12,11),fill=P['F']);d.point((10,11),fill=P['G'])
 if kind=='lens':
  d.rectangle((1,1,14,14),fill=P['I']);d.rectangle((3,3,12,12),fill=P['J']);d.line((4,4,10,4),fill=P['M']);d.line((4,5,4,8),fill=P['M']);d.line((6,12,12,6),fill=P['H'])
 return im

def geometry():
 e=[]
 def box(name,a,b,t,top=None):
  dx,dy,dz=[b[i]-a[i] for i in range(3)]
  faces={}
  for f in ['north','south','east','west','up','down']:
   material=top if f=='up' and top else t
   w,h=(dx,dz) if f in ('up','down') else (dx,dy) if f in ('north','south') else (dz,dy)
   # Structural materials keep one texel per block unit; map/lens are authored inserts.
   uv=[0,0,16,16] if material in ('map','lens') else [0,0,w,h]
   faces[f]={'uv':uv,'texture':'#'+material}
  e.append({'name':name,'from':a,'to':b,'faces':faces})
 # Sturdy open frame, capped feet and a recessed lower instrument shelf.
 for x,z in [(2,2),(12,2),(2,12),(12,12)]:
  box('oak post',[x,1,z],[x+2,10,z+2],'wood');box('copper foot',[x-1,0,z-1],[x+3,2,z+3],'copper')
 box('lower shelf',[3,3,3],[13,4,13],'wood')
 box('desk slab',[1,9,1],[15,11,15],'wood')
 box('front copper rail',[1,9,0],[15,10,1],'copper')
 box('left copper rail',[0,9,1],[1,10,15],'copper')
 box('right copper rail',[15,9,1],[16,10,15],'copper')
 box('map parchment',[2,11,2],[10,12,12],'map')
 box('map roller left',[1,11,2],[2,13,12],'wood')
 box('map roller right',[10,11,2],[11,13,12],'copper')
 box('instrument plinth',[11,11,10],[15,12,14],'copper')
 box('instrument upright',[12,12,11],[14,15,13],'copper')
 box('lens lower rim',[11,12,9],[15,13,10],'copper')
 box('lens upper rim',[11,15,9],[15,16,10],'copper')
 box('lens left rim',[11,13,9],[12,15,10],'copper')
 box('lens right rim',[14,13,9],[15,15,10],'copper')
 box('opaque polished lens',[12,13,9],[14,15,10],'lens')
 box('survey notebook',[5,4,6],[10,5,11],'wood','map')
 return e

def png(im):
 b=io.BytesIO();im.save(b,format='PNG');return b.getvalue()
def preview(elements,textures):
 # Orthographic ray casting against the actual axis-aligned cuboids: no painter ordering.
 # Camera direction is (1,1,-1); nearest surface wins independently for every pixel.
 import math
 im=Image.new('RGB',(320,280),'#101D1C')
 for py in range(im.height):
  for px in range(im.width):
   u=(px+.5-32)/8;v=(py+.5-208)/4
   # Projection: u=x+z, v=x-z-2y. Ray origin outside geometry.
   origin=[u/2+64,64-v/2,u/2-64];direction=[-1,-1,1]
   hit=None
   for e in elements:
    near=-float('inf');far=float('inf');face=None
    for axis in range(3):
     lo=(e['from'][axis]-origin[axis])/direction[axis]
     hi=(e['to'][axis]-origin[axis])/direction[axis]
     entering=('east','up','north')[axis]
     if lo>hi:lo,hi=hi,lo
     if lo>near:near=lo;face=entering
     far=min(far,hi)
    if near<=far and near>=0 and (hit is None or near<hit[0]):hit=(near,e,face)
   if hit:
    distance,e,face=hit;point=[origin[i]+distance*direction[i] for i in range(3)]
    a=e['from'];b=e['to'];f=e['faces'][face];tex=textures[f['texture'][1:]]
    if face=='up':tu=(point[0]-a[0])/(b[0]-a[0]);tv=(point[2]-a[2])/(b[2]-a[2])
    elif face=='north':tu=(point[0]-a[0])/(b[0]-a[0]);tv=(b[1]-point[1])/(b[1]-a[1])
    else:tu=(point[2]-a[2])/(b[2]-a[2]);tv=(b[1]-point[1])/(b[1]-a[1])
    uv=f['uv'];x=min(15,max(0,int(uv[0]+tu*(uv[2]-uv[0]))));y=min(15,max(0,int(uv[1]+tv*(uv[3]-uv[1]))))
    color=tex.getpixel((x,y));shade={'up':1,'north':.85,'east':.7}[face]
    im.putpixel((px,py),tuple(int(c*shade) for c in color))
 return im

def outputs():
 textures={t:texture(t) for t in ['wood','copper','map','lens']};elements=geometry()
 assert 15<=len(elements)<=30
 assert all(0<=v<=16 for e in elements for k in ('from','to') for v in e[k])
 palette={tuple(bytes.fromhex(c[1:])) for c in P.values()}
 assert all({c for _,c in im.getcolors(256)}<=palette for im in textures.values())
 assert all(all(isinstance(v,int) for v in f['uv']) for e in elements for f in e['faces'].values())
 model={'parent':'minecraft:block/block','ambientocclusion':True,'textures':{t:'entrelumen:block/survey_station_'+t for t in textures},'elements':elements}
 model['textures']['particle']='entrelumen:block/survey_station_wood'
 item={'parent':'entrelumen:block/survey_station','display':{'gui':{'rotation':[30,225,0],'translation':[0,-1,0],'scale':[.7,.7,.7]}}}
 blockstate={'variants':{'':{'model':'entrelumen:block/survey_station'}}}
 out={}
 for base in [ROOT/'companion/src/main/resources/assets/entrelumen',ROOT/'pack/resourcepacks/entrelumen/assets/entrelumen']:
  for name,obj in [('models/block/survey_station.json',model),('models/item/survey_station.json',item),('blockstates/survey_station.json',blockstate)]:out[base/name]=(json.dumps(obj,indent=2)+'\n').encode()
  for t,im in textures.items():out[base/f'textures/block/survey_station_{t}.png']=png(im)
 out[ROOT/'art/survey-station-model-preview.png']=png(preview(elements,textures))
 return out
if __name__=='__main__':
 p=argparse.ArgumentParser();p.add_argument('--check',action='store_true');a=p.parse_args()
 for path,data in outputs().items():
  if a.check:
   assert path.is_file(),path
   actual=path.read_bytes()
   if path.suffix=='.png':
    assert same_png_pixels(actual,data),path
    if actual!=data:print(f'PASS identical PNG mode, size and pixels; encoding differs: {path.name}')
   else:assert actual==data,path
  else:path.parent.mkdir(parents=True,exist_ok=True);path.write_bytes(data)
 print('PASS deterministic survey station:24 cuboids, native16 textures, integerUV; preview is software model, not in-game.')
