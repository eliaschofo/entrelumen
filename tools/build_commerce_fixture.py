"""Writes the GameTest fixture of Solsticio's commerce markers (never shipped in the release JAR).

A 13 x 4 x 13 stone floor with DATA structure-block markers on layer 1: three shops, an inn, three
common villager homes, an easter egg, the trading hall and one unknown marker.

    python tools/build_commerce_fixture.py
"""
import gzip
import os
import struct

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
OUT = os.path.join(ROOT, 'companion/src/main/resources/data/entrelumen/structure/commerce_fixture.nbt')
DATA_VERSION = 3955
SIZE = (13, 4, 13)
MARKERS = [
    ('shop:bookstore', (1, 1, 1)),
    ('shop:maps', (6, 1, 1)),
    ('shop:rarities', (11, 1, 1)),
    ('sidequest:7_inn', (1, 1, 11)),
    ('bogus:marker', (3, 1, 11)),
    ('easter:fountain_coin', (6, 1, 11)),
    ('resident', (1, 1, 6)),
    ('resident', (11, 1, 6)),
    ('resident', (11, 1, 11)),
    ('trading_hall', (6, 1, 6)),
]


def nbt(tag, name, value):
    return bytes([tag]) + struct.pack('>H', len(name.encode())) + name.encode() + payload(tag, value)


def payload(tag, value):
    if tag == 3:
        return struct.pack('>i', value)
    if tag == 8:
        return struct.pack('>H', len(value.encode())) + value.encode()
    if tag == 10:
        return b''.join(nbt(t, k, v) for k, (t, v) in value.items()) + b'\x00'
    if tag == 9:
        kind, items = value
        return bytes([kind if items else 0]) + struct.pack('>i', len(items)) + b''.join(payload(kind, i) for i in items)
    raise ValueError(tag)


def main():
    palette = [{'Name': (8, 'minecraft:stone')},
               {'Name': (8, 'minecraft:structure_block'), 'Properties': (10, {'mode': (8, 'data')})}]
    blocks = [{'pos': (9, (3, [x, 0, z])), 'state': (3, 0)} for z in range(SIZE[2]) for x in range(SIZE[0])]
    for name, pos in MARKERS:
        blocks.append({'pos': (9, (3, list(pos))), 'state': (3, 1),
                       'nbt': (10, {'id': (8, 'minecraft:structure_block'), 'mode': (8, 'DATA'), 'metadata': (8, name)})})
    root = {'DataVersion': (3, DATA_VERSION), 'size': (9, (3, list(SIZE))), 'palette': (9, (10, palette)),
            'blocks': (9, (10, blocks)), 'entities': (9, (10, []))}
    with gzip.GzipFile(OUT, 'wb', mtime=0) as out:
        out.write(nbt(10, '', root))
    print(OUT, len(blocks), 'blocks')


if __name__ == '__main__':
    main()
