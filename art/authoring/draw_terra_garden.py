"""Terra's hydroponic garden: the grow lamp (8 frames), the garden plan and the two garden blocks.

Native 16x16 grids, hand-placed texel by texel and mirror-symmetric left-right (Elias's canon); every
grid is built from its left half and mirrored, so symmetry holds by construction.

- terra_grow_lamp (item, animated): Terra's grow lamp. A verdigris hood hung from a copper ring holds a
  captive luminous flame, drawn in the luminosities' language (dark outline, bright ramp, a 2x2 white-hot
  core, tongues that flicker every frame) but upside down: it hangs from the hood and drips light onto
  the crops. Sunlight gold edged in leaf green, a pairing no luminosity uses. Eight frames, frametime 2.
- terra_garden_plan (item): a hanging scroll between two copper rods; verdigris ink on parchment draws
  the garden's front elevation (the roof, the four pillars, the stepped troughs, the sun).
- hydroponic_trough (block): a verdigris copper tank; the side is a window onto water and hanging white
  roots, the top is water with four net cups and their seedlings.
- terra_garden_core (block): calcite and verdigris with a round lamp on the front: dark when the garden
  is unbuilt, teal once the garden stands, sunlight gold while it grows.

    python art/authoring/draw_terra_garden.py            # write the grids
    python art/authoring/draw_terra_garden.py --check    # compare with the repo
"""
import argparse
import os
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, HERE)
from palette import RAMPS as R  # noqa: E402

ROOT = os.path.normpath(os.path.join(HERE, '..', '..'))
GRIDS = os.path.join(ROOT, 'art', 'grids')

VER = R['verdigris']            # oxidized copper: '#1c3f37' .. '#b0e8cc'
CU = R['copper']
TEAL = R['teal']
LEAF = R['leaf']
STRAW = R['straw']
BRASS = R['brass']
PARCH = R['parch']
GLASS = R['glass']
IRON = R['iron']
INK = R['ink'][1]
CALCITE = ['#6d7076', IRON[3], '#aeb2b6', IRON[4], '#d6d9dc', IRON[5]]   # vanilla calcite's cool white stone


def mirror(half_rows):
    """16 rows of 8 characters (the left half) -> 16 rows of 16, mirrored about the vertical axis."""
    rows = []
    for r in half_rows:
        assert len(r) == 8, r
        rows.append(r + r[::-1])
    assert len(rows) == 16
    return rows


def grid_text(palette, rows):
    used = sorted({c for r in rows for c in r if c != '.'})
    missing = [c for c in used if c not in palette]
    assert not missing, missing
    lines = [f'{c} {palette[c]}' for c in used]
    return '\n'.join(lines) + '\n\n' + '\n'.join(rows) + '\n'


# ---- the grow lamp ---------------------------------------------------------------------------------

LAMP_PAL = {
    'k': VER[0], 'v': VER[1], 'V': VER[2], 'w': VER[3], 'W': VER[4], 'X': VER[5],     # hood
    'c': CU[2], 'C': CU[4], 'D': CU[5],                                                # ring and rim
    'a': LEAF[1], 'b': LEAF[3], 'g': LEAF[5],                                          # flame edge, green halo
    's': STRAW[2], 'S': STRAW[3], 'y': BRASS[5], 'Y': '#fffbe6',                       # sunlight ramp, core
}
# The body: ring (rows 0-2), hood (3-6), rim (7). Left halves; the flame is added per frame.
LAMP_BODY = [
    '......cC',
    '.....c..',
    '......cD',
    '.....kVW',
    '....kVWX',
    '...kvVWX',
    '..kvVVWW',
    '..cCCCCD',
]
# The hanging flame, rows 8-15, one list per frame: an inverted fireball under the rim (rows 8-11, a
# white-hot core 'Y' in the sunlight ramp 'y'/'S'/'s', edged in the green 'g'/'b' and outlined in 'a')
# whose three tongues drip down and flicker, now and then letting a drop of light fall free.
_FLAME_BODY = ['..agsSyy', '..bgSyYY', '..bgSyYY', '..abgSyy']
_TONGUES = [
    ['...abgSS', '....a.gS', '.......g', '........'],
    ['...abgSS', '...ab.gS', '....a..S', '.......g'],
    ['...abgSs', '....ab.g', '.....g..', '.......S'],
    ['...abgSS', '....a.gS', '......gS', '.......g'],
    ['...abgSS', '...ab.gs', '....b..g', '........'],
    ['...abgSS', '....a.gS', '.......S', '....g...'],
    ['...abgSs', '....ab.S', '......g.', '.......g'],
    ['...abgSS', '...a..gS', '....g..g', '........'],
]
LAMP_FLAMES = [_FLAME_BODY + tongues for tongues in _TONGUES]

def lamp_frames():
    return [mirror(LAMP_BODY + flame) for flame in LAMP_FLAMES]


# ---- the garden plan -------------------------------------------------------------------------------

PLAN_PAL = {
    'k': CU[0], 'c': CU[2], 'C': CU[4], 'D': CU[5],                # rods
    'p': PARCH[2], 'P': PARCH[3], 'q': PARCH[4], 'Q': PARCH[5],    # paper (shadow to light)
    'i': VER[1], 'I': VER[3],                                       # verdigris ink
    'y': STRAW[3],                                                  # the sun
}
PLAN = mirror([
    '.kcCCCCC',
    '..pqqqqq',
    '..pQQQQy',
    '..pQQQiQ',
    '..pQQiQQ',
    '..pQiQQQ',
    '..piiiii',
    '..piQQQQ',
    '..piQQQI',
    '..piQQII',
    '..piQIII',
    '..piIIII',
    '..piiiii',
    '..pqqqqq',
    '.kcCCCCD',
    '........',
])

# ---- the hydroponic trough -------------------------------------------------------------------------

TROUGH_PAL = {
    'k': VER[0], 'v': VER[1], 'V': VER[2], 'w': VER[3], 'W': VER[4], 'X': VER[5],
    't': TEAL[1], 'T': TEAL[2], 'u': TEAL[3], 'U': TEAL[4], 'G': GLASS[4],
    'r': PARCH[4], 'R': PARCH[5],
    'l': LEAF[2], 'L': LEAF[4], 'M': LEAF[5],
    'n': INK,
}
TROUGH_SIDE = mirror([
    'wWWWWWWX',
    'VwwwwwwW',
    'vVVVVVVV',
    'vwUGUuUU',
    'vwuRuuRu',
    'vwTrTTrT',
    'vwTrTtrT',
    'vwTrtTTt',
    'vwtrtTTt',
    'vwttrtTt',
    'vwtttttt',
    'vwtttttt',
    'vVVVVVVV',
    'vwWwwwww',
    'vwwwwwww',
    'kvvvvvvv',
])
_TROUGH_TOP_HALF = [
    'wWWWWWWW',
    'WVVVVVVV',
    'WVuUuuuT',
    'WVuVVVVu',
    'WVuVMLVu',
    'WVuVlLVT',
    'WVTVVVVu',
    'WVuTuUuT',
]
TROUGH_TOP = mirror(_TROUGH_TOP_HALF + _TROUGH_TOP_HALF[::-1])
TROUGH_BOTTOM = mirror([
    'kvvvvvvv',
    'vVVVVVVV',
    'vVwwwwww',
    'vVwVVVVV',
    'vVwVkkVV',
    'vVwVkVVV',
    'vVwVVVVV',
    'vVwVVVVV',
    'vVwVVVVV',
    'vVwVVVVV',
    'vVwVkVVV',
    'vVwVkkVV',
    'vVwVVVVV',
    'vVwwwwww',
    'vVVVVVVV',
    'kvvvvvvv',
])


# ---- the garden core -------------------------------------------------------------------------------

CORE_PAL = {
    'k': VER[0], 'v': VER[1], 'V': VER[2], 'w': VER[3], 'W': VER[4], 'X': VER[5],
    'a': CALCITE[0], 'b': CALCITE[1], 'c': CALCITE[2], 'd': CALCITE[3], 'e': CALCITE[4], 'f': CALCITE[5],
    'n': INK, 'g': GLASS[0], 'G': GLASS[1],
    'l': LEAF[2], 'L': LEAF[4],
    't': TEAL[1], 'T': TEAL[2], 'u': TEAL[3], 'U': TEAL[4], 'Z': TEAL[5],
    's': STRAW[2], 'S': STRAW[3], 'y': BRASS[5], 'Y': '#fffbe6', 'm': LEAF[3], 'M': LEAF[5],
}
# The front: a round lamp in a verdigris ring on calcite, over a copper vent plate. The lens per state,
# rows 3-11 (left halves): dark glass round a sleeping sprout; teal once the garden stands; sunlight
# gold round a green sprout while it grows.
_LENS = {
    'unbuilt': ['bdeWVkkk', 'bdWVkggg', 'bdWkgggG', 'bdWkggGG', 'bdWkggGl', 'bdWkgGGl', 'bdWkggll', 'bdWVkggg', 'bdeWVkkk'],
    'built':   ['bdeWVkkk', 'bdWVktTT', 'bdWktTuu', 'bdWkTuuU', 'bdWkTuUl', 'bdWkTuUl', 'bdWkTull', 'bdWVktTT', 'bdeWVkkk'],
    'growing': ['bdeWVkkk', 'bdWVksSS', 'bdWksSyy', 'bdWkSyYY', 'bdWkSyYm', 'bdWkSyYm', 'bdWkSymM', 'bdWVksSS', 'bdeWVkkk'],
}


def core_front(state):
    return mirror(['bddddddd', 'bdeeeeee', 'bdeeWWWW'] + _LENS[state]
                  + ['bdeeWWWW', 'bVVVVVVV', 'bVwnwwnw', 'avvvvvvv'])

CORE_SIDE = mirror([
    'bddddddd',
    'bdeeeeee',
    'bdefffff',
    'bdeVVVVV',
    'bdeVwwwl',
    'bdeVwwLl',
    'bdeVwwLl',
    'bdeVwLll',
    'bdeVwLlL',
    'bdeVwwLl',
    'bdeVwwwl',
    'bdeVVVVV',
    'bdeeeeee',
    'bVVVVVVV',
    'bVwnwwnw',
    'avvvvvvv',
])
CORE_TOP = mirror([
    'kvvvvvvv',
    'vWwwwwww',
    'vwXwwwww',
    'vwwVVVVV',
    'vwwVggGG',
    'vwwVgGGZ',
    'vwwVGGZU',
    'vwwVGZUu',
    'vwwVGZUu',
    'vwwVGGZU',
    'vwwVgGGZ',
    'vwwVggGG',
    'vwwVVVVV',
    'vwXwwwww',
    'vWwwwwww',
    'kvvvvvvv',
])
CORE_BOTTOM = mirror([
    'abbbbbbb',
    'bcccccccc'[:8],
    'bcdddddd',
    'bcdccccc',
    'bcdcbbbb',
    'bcdcbccc',
    'bcdcbcdd',
    'bcdcbcdd',
    'bcdcbcdd',
    'bcdcbcdd',
    'bcdcbccc',
    'bcdcbbbb',
    'bcdccccc',
    'bcdddddd',
    'bccccccc',
    'abbbbbbb',
])


def grids():
    """Every grid this script owns: relative path under art/grids -> text."""
    out = {}
    for i, frame in enumerate(lamp_frames()):
        out[f'item/terra_grow_lamp__f{i}.txt'] = grid_text(LAMP_PAL, frame)
    out['item/terra_garden_plan.txt'] = grid_text(PLAN_PAL, PLAN)
    out['block/hydroponic_trough.txt'] = grid_text(TROUGH_PAL, TROUGH_SIDE)
    out['block/hydroponic_trough_top.txt'] = grid_text(TROUGH_PAL, TROUGH_TOP)
    out['block/hydroponic_trough_bottom.txt'] = grid_text(TROUGH_PAL, TROUGH_BOTTOM)
    for state in ('unbuilt', 'built', 'growing'):
        name = 'terra_garden_core_front' + ('' if state == 'unbuilt' else '_' + state)
        out[f'block/{name}.txt'] = grid_text(CORE_PAL, core_front(state))
    out['block/terra_garden_core.txt'] = grid_text(CORE_PAL, CORE_SIDE)
    out['block/terra_garden_core_top.txt'] = grid_text(CORE_PAL, CORE_TOP)
    out['block/terra_garden_core_bottom.txt'] = grid_text(CORE_PAL, CORE_BOTTOM)
    return out


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--check', action='store_true')
    args = parser.parse_args()
    stale = []
    for rel, text in grids().items():
        path = os.path.join(GRIDS, rel)
        if args.check:
            if not os.path.exists(path) or open(path, encoding='utf-8').read() != text:
                stale.append(rel)
            continue
        with open(path, 'w', encoding='utf-8', newline='\n') as f:
            f.write(text)
    if args.check:
        assert not stale, f'stale grids: {stale}'
        print(f'PASS: {len(grids())} Terra garden grids match draw_terra_garden.py')
    else:
        print(f'Wrote {len(grids())} grids')


if __name__ == '__main__':
    main()
