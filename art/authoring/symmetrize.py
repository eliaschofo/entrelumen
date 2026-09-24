"""Enforce left-right mirror symmetry on 16x16 grids by copying the left half onto the right."""
import sys; sys.path.insert(0,'.')
from PIL import Image
from grid import dump
def mirror(name, keep='left'):
    im=Image.open(f'grids/{name}.png').convert('RGBA'); px=im.load()
    for y in range(16):
        for x in range(8):
            if keep=='left': px[15-x,y]=px[x,y]
            else: px[x,y]=px[15-x,y]
    im.save(f'grids/{name}.png'); dump(f'grids/{name}.png',f'grids/{name}.txt')
if __name__=='__main__':
    for a in sys.argv[1:]:
        n,_,k=a.partition(':'); mirror(n,k or 'left')
