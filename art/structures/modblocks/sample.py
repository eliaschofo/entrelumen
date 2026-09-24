"""Sample sheet of the mod palette: every block rendered alone with voxrender, grouped by category,
with its short name, so a generator author can pick at a glance.

    python art/structures/modblocks/sample.py [--scale 22] [--cols 12] [--out sample.png]
"""
import argparse
import os
import sys
import tempfile

from PIL import Image, ImageChops, ImageDraw, ImageFont

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.dirname(HERE))
from voxrender import render  # noqa: E402
from modblocks import palette as mp  # noqa: E402

CATEGORIES = [('path', 'Caminos y pavimentos'), ('bridge', 'Puentes, barandas y balcones'),
              ('window', 'Ventanas y vitrales'), ('lamp', 'Faroles y apliques'), ('roof', 'Techos y toldos'),
              ('planter', 'Maceteros y jardines'), ('furniture', 'Muebles de tienda'), ('decor', 'Decoración')]
BG = (240, 238, 232)
INK, MUTED, RULE = (52, 50, 46), (128, 124, 116), (214, 210, 200)
MODTAG = {'mcwpaths': 'MPaths', 'mcwbridges': 'MBridges', 'mcwstairs': 'MStairs', 'mcwfences': 'MFences',
          'mcwwindows': 'MWindows', 'mcwdoors': 'MDoors', 'mcwtrpdoors': 'MTrapdoors', 'mcwlights': 'MLights',
          'mcwroofs': 'MRoofs', 'supplementaries': 'Supp', 'amendments': 'Amend', 'handcrafted': 'Handcr',
          'refurbished_furniture': 'Refurb', 'chipped': 'Chipped', 'rechiseled': 'Rechis', 'framedblocks': 'Framed'}


def font(size, bold=False):
    for name in (('segoeuib.ttf', 'arialbd.ttf') if bold else ('segoeui.ttf', 'arial.ttf')):
        try:
            return ImageFont.truetype(name, size)
        except OSError:
            continue
    return ImageFont.load_default()


def tile(rec, scale, tmp):
    state = rec.get('preview') or rec['default']
    vox = {(0, 1, 0): state}
    if rec['category'] == 'path' and rec['render'] == 'thin':
        vox[(0, 0, 0)] = 'minecraft:grass_block'           # pavings are laid over the ground
    render(vox, tmp, scale=scale, sky=(BG, BG))
    im = Image.open(tmp).convert('RGB')
    diff = ImageChops.difference(im, Image.new('RGB', im.size, BG)).convert('L')
    box = diff.point(lambda v: 255 if v > 3 else 0).getbbox()     # the sky gradient rounds to BG +- 1
    return im.crop(box) if box else im


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument('--scale', type=int, default=22)
    ap.add_argument('--cols', type=int, default=12)
    ap.add_argument('--out', default=os.path.join(HERE, 'sample.png'))
    a = ap.parse_args()
    blocks = mp.data()['blocks']
    cw, ch, pad = 128, 150, 16
    f_label, f_tag, f_head, f_title = font(12), font(10), font(17, True), font(24, True)
    rows = []
    for cat, title in CATEGORIES:
        items = [b for b in blocks if b['category'] == cat]
        if items:
            rows.append(('head', '%s  ·  %d' % (title, len(items))))
            for i in range(0, len(items), a.cols):
                rows.append(('cells', items[i:i + a.cols]))
    W = pad * 2 + a.cols * cw
    H = 70 + sum(38 if k == 'head' else ch for k, _ in rows) + 44
    sheet = Image.new('RGB', (W, H), BG)
    d = ImageDraw.Draw(sheet)
    d.text((pad, 18), 'ENTRELUMEN · paleta de bloques de mods para Solsticio  (%d bloques)' % len(blocks),
           fill=INK, font=f_title)
    y = 70
    fd, tmp = tempfile.mkstemp(suffix='.png')
    os.close(fd)
    try:
        for kind, payload in rows:
            if kind == 'head':
                d.line([(pad, y + 6), (W - pad, y + 6)], fill=RULE)
                d.text((pad, y + 12), payload, fill=INK, font=f_head)
                y += 38
                continue
            for i, b in enumerate(payload):
                x = pad + i * cw
                im = tile(b, a.scale, tmp)
                im.thumbnail((cw - 16, ch - 44), Image.NEAREST)
                sheet.paste(im, (x + (cw - im.width) // 2, y + (ch - 40 - im.height) // 2))
                d.text((x + cw // 2, y + ch - 34), b['label'], fill=INK, font=f_label, anchor='mt')
                d.text((x + cw // 2, y + ch - 18), '%s · %s' % (MODTAG.get(b['mod'], b['mod']), b['render']),
                       fill=MUTED, font=f_tag, anchor='mt')
            y += ch
    finally:
        os.remove(tmp)
    d.text((pad, H - 32), 'Texturas © sus autores (Macaw\'s, Supplementaries Team, Terrarium, MrCrayfish, XFactHD, '
           'SuperMartijn642, Mojang). Solo vista previa local; ids y estados en palette.json.', fill=MUTED, font=f_tag)
    sheet.save(a.out)
    print(a.out, sheet.size)


if __name__ == '__main__':
    main()
