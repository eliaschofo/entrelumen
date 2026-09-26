"""Spawner augmenters: copper medallions. Basic tiers in fresh copper, strong ones in verdigris.

Every emblem is 8x8 and mirror-symmetric, so it sits exactly on the medallion's centre (7.5, 7.5)
of the 16x16 icon; the medallion is lit from above, so the whole badge is mirror-symmetric too
(pack rule; Elias noticed the old 7x7 emblems could not be centred).
"""
import sys
sys.path.insert(0, '.')
from draw_items import Canvas  # noqa: E402
from palette import RAMPS as R  # noqa: E402

STRONG = {'echoing', 'ignore_conditions', 'ignore_light', 'ignore_players', 'no_ai', 'redstone_control'}
E = {
    'burning': (["...aa...", "...aa...", "..abba..", "..abba..", ".abccba.", ".abccba.", "abccccba", ".bbbbbb."],
                dict(a=R['crimson'][3], b=R['straw'][2], c=R['straw'][3])),
    'echoing': (["..aaaa..", ".a....a.", "a..bb..a", "a.b..b.a", "a.b..b.a", "a..bb..a", ".a....a.", "..aaaa.."],
                dict(a=R['teal'][1], b=R['teal'][3])),
    'ignore_conditions': (["...aa...", "..abba..", ".abccba.", "abccccba", "abccccba", ".abccba.", "..abba..", "...aa..."],
                          dict(a=R['teal'][1], b=R['teal'][3], c=R['teal'][5])),
    'ignore_light': (["..aaaa..", ".aabbaa.", "aab..baa", "ab....ba", "ab....ba", "aab..baa", ".aabbaa.", "..aaaa.."],
                     dict(a=R['straw'][3], b=R['straw'][1])),
    'ignore_players': (["........", "..bbbb..", ".b.cc.b.", "b.cccc.b", "aaaaaaaa", ".b.cc.b.", "..bbbb..", "........"],
                       dict(a=R['crimson'][3], b=R['parch'][4], c=R['ink'][0])),
    'initial_health': (["........", ".aa..aa.", "abbaabba", "abbbbbba", ".abbbba.", "..abba..", "...aa...", "........"],
                       dict(a=R['crimson'][1], b=R['crimson'][3])),
    'max_delay': (["aaaaaaaa", ".bbbbbb.", "..bccb..", "...cc...", "...cc...", "..bccb..", ".bccccb.", "aaaaaaaa"],
                  dict(a=R['wood'][3], b=R['glass'][3], c=R['straw'][3])),
    'min_delay': (["..aaaa..", ".a.bb.a.", "a..bb..a", "a..bb..a", "a......a", "a......a", ".a....a.", "..aaaa.."],
                  dict(a=R['parch'][4], b=R['ink'][0])),
    'max_nearby': (["........", ".aa..aa.", ".aa..aa.", "........", "........", ".aa..aa.", ".aa..aa.", "........"],
                   dict(a=R['leaf'][3])),
    'no_ai': (["........", "........", "aaa..aaa", "........", "........", "..bbbb..", "........", "........"],
              dict(a=R['parch'][5], b=R['parch'][3])),
    'player_range': (["aaa..aaa", "a......a", "a......a", "...bb...", "...bb...", "a......a", "a......a", "aaa..aaa"],
                     dict(a=R['parch'][4], b=R['teal'][3])),
    'redstone_control': (["...aa...", "..abba..", "...aa...", "...cc...", "...cc...", "...cc...", "..cccc..", "........"],
                         dict(a=R['crimson'][3], b=R['crimson'][5], c=R['wood'][3])),
    'silent': (["...aa...", "..aaaa..", "..aaaa..", ".aaaaaa.", "bbbbbbbb", "aaaaaaaa", "...aa...", "........"],
               dict(a=R['parch'][4], b=R['crimson'][3])),
    'spawn_count': (["........", "...aa...", "...aa...", ".aaaaaa.", ".aaaaaa.", "...aa...", "...aa...", "........"],
                    dict(a=R['straw'][3])),
    'spawn_range': (["...aa...", "..a..a..", ".a....a.", "a..bb..a", "a..bb..a", ".a....a.", "..a..a..", "...aa..."],
                    dict(a=R['parch'][4], b=R['teal'][3])),
    'youthful': (["........", ".aa..aa.", "..aaaa..", "...bb...", "...bb...", "..cccc..", ".cccccc.", "........"],
                 dict(a=R['leaf'][4], b=R['leaf'][2], c=R['wood'][2])),
}


def draw(name):
    strong = name in STRONG
    rim = R['verdigris'] if strong else R['copper']
    P = dict(O='#141417', r=rim[1], R=rim[2], f=rim[3], F=rim[4], h=rim[5], x=R['copper'][2] if strong else R['copper'][1])
    emb, cols = E[name]
    assert all(len(row) == 8 and row == row[::-1] for row in emb) and len(emb) == 8, name
    keys = 'ABCDEFG'
    for i, (k, v) in enumerate(cols.items()):
        P[keys[i]] = v
    c = Canvas(P)
    c.disc(7.5, 7.5, 6.6, 'r')
    c.disc(7.5, 7.5, 5.6, 'f')
    for y in range(16):                      # lit from above: brighter upper band, darker rim below
        for x in range(16):
            g = c.get(x, y)
            if g == 'f' and y < 6:
                c.px(x, y, 'F')
            if g == 'r':
                c.px(x, y, 'R' if y < 7 else ('x' if y > 9 else 'r'))
    for x in (6, 9):
        c.px(x, 3, 'h')
    c.px(7, 0, 'r'); c.px(8, 0, 'r'); c.px(7, 1, 'R'); c.px(8, 1, 'R')    # the loop it hangs from
    for yy, row in enumerate(emb):
        for xx, ch in enumerate(row):
            if ch != '.':
                c.px(4 + xx, 4 + yy, keys[list(cols).index(ch)])
    c.outline()
    for y in range(16):
        assert c.g[y] == c.g[y][::-1], (name, y)
    c.save('augment_' + name)


if __name__ == '__main__':
    for n in E:
        draw(n)
