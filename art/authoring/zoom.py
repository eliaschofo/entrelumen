import sys
from PIL import Image, ImageDraw
R='G:/Elias/Codex/Entrelumen-work/art-redo-20260923/out/'
job=sys.argv[1]; ids=[int(x) for x in sys.argv[2:]]
S=10
sheet=Image.new('RGBA',(len(ids)*(16*S+10)+10,16*S+60),(139,139,139,255)); d=ImageDraw.Draw(sheet)
for i,n in enumerate(ids):
    p=Image.open(f'{R}{job}/c{n:02d}.png').convert('RGBA')
    x=10+i*(16*S+10)
    sheet.alpha_composite(p.resize((16*S,16*S),Image.NEAREST),(x,10))
    sheet.alpha_composite(p,(x,16*S+20)); sheet.alpha_composite(p.resize((32,32),Image.NEAREST),(x+24,16*S+20))
    d.text((x+70,16*S+30),str(n),fill=(0,0,0,255))
sheet.save(f'G:/Elias/Codex/Entrelumen-work/art-redo-20260923/zoom-{job}.png')
