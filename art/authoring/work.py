"""Promote a draft candidate into an editable master-palette grid and preview it."""
import sys, os
sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image, ImageDraw
from palette import remap
from clean import clean
from grid import dump, render
R=os.path.dirname(os.path.abspath(__file__))+'/'
def promote(item, src, ramps, k=16):
    im=Image.open(R+'out/'+src).convert('RGBA')
    im=clean(im,k,passes=1)
    im=remap(im,ramps)
    im.save(R+f'grids/{item}.png'); dump(R+f'grids/{item}.png', R+f'grids/{item}.txt')
def preview(items, out='preview.png'):
    S=10
    sheet=Image.new('RGBA',(len(items)*(16*S+12)+12,16*S+64),(139,139,139,255)); d=ImageDraw.Draw(sheet)
    inv=Image.new('RGBA',(18,18),(139,139,139,255))
    for i,it in enumerate(items):
        render(R+f'grids/{it}.txt', R+f'grids/{it}.png')
        p=Image.open(R+f'grids/{it}.png').convert('RGBA')
        x=12+i*(16*S+12)
        sheet.alpha_composite(p.resize((16*S,16*S),Image.NEAREST),(x,8))
        sheet.alpha_composite(p,(x,16*S+16)); sheet.alpha_composite(p.resize((32,32),Image.NEAREST),(x+22,16*S+16))
        sheet.alpha_composite(p.resize((48,48),Image.NEAREST),(x+60,16*S+12))
        d.text((x,16*S+50),it,fill=(0,0,0,255))
    sheet.save(R+out)
if __name__=='__main__':
    if sys.argv[1]=='promote': promote(sys.argv[2],sys.argv[3],sys.argv[4].split(','))
    else: preview(sys.argv[2].split(','), sys.argv[3] if len(sys.argv)>3 else 'preview.png')
