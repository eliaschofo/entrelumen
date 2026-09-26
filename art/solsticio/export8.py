"""Export Solsticio v8 as the companion's structure template: solsticio/city.nbt.

The grid is written as a vanilla structure template (DataVersion of 1.21.1): size, palette, one
entry per filled cell (y, then z, then x), block entity data where the builder left some (the
signs' text), and every marker as a DATA structure block carrying its name, which SolsticioCity
turns back into air and reads as the city's contract. Blocks are streamed into the gzip file, so
the ~1.6 million entries never sit in memory as objects.

    verify(path) reads a template back the same streaming way: size, palette, block count, markers.
"""
import gzip
import os
import struct

HERE = os.path.dirname(os.path.abspath(__file__))
CITY = os.path.normpath(os.path.join(HERE, '..', '..', 'companion', 'src', 'main', 'resources', 'data', 'entrelumen',
                                     'structure', 'solsticio', 'city.nbt'))
DATA_VERSION = 3955


def _s(text):
    b = text.encode('utf-8')
    return struct.pack('>H', len(b)) + b


def _named(tag, name):
    return bytes([tag]) + _s(name)


def payload(tag, v):
    if tag == 1:
        return struct.pack('>b', v)
    if tag == 3:
        return struct.pack('>i', v)
    if tag == 8:
        return _s(v)
    if tag == 9:
        t, items = v
        return bytes([t if items else 0]) + struct.pack('>i', len(items)) + b''.join(payload(t, it) for it in items)
    if tag == 10:
        return b''.join(_named(t, k) + payload(t, val) for k, (t, val) in v.items()) + b'\x00'
    raise ValueError(tag)


def export(G, markers, block_nbt, path):
    x0, y0, z0, x1, y1, z1 = G.bounds()
    for _, (x, y, z) in markers:
        x0, y0, z0 = min(x0, x), min(y0, y), min(z0, z)
        x1, y1, z1 = max(x1, x), max(y1, y), max(z1, z)
    size = [x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1]
    pal = G.palette
    used = sorted({v for v in set(G.data)} - {0})
    index = {sid: i for i, sid in enumerate(used)}
    marker_state = len(used)
    palette = []
    for sid in used:
        st = pal[sid]
        name, props = (st[:-1].split('[') if '[' in st else (st, ''))
        entry = {'Name': (8, name)}
        if props:
            entry['Properties'] = (10, {k: (8, v) for k, v in (p.split('=') for p in props.split(','))})
        palette.append(entry)
    palette.append({'Name': (8, 'minecraft:structure_block'), 'Properties': (10, {'mode': (8, 'data')})})
    total = sum(1 for v in G.data if v) + len(markers)
    mpos = {p: n for n, p in markers}
    os.makedirs(os.path.dirname(path), exist_ok=True)
    tmp = path + '.tmp'
    pos_head = _named(9, 'pos') + bytes([3]) + struct.pack('>i', 3)
    state_head = _named(3, 'state')
    nbt_head = _named(10, 'nbt')
    written = 0
    with open(tmp, 'wb') as raw, gzip.GzipFile(filename='', mode='wb', fileobj=raw, mtime=0, compresslevel=6) as f:
        f.write(_named(10, ''))
        f.write(_named(3, 'DataVersion') + struct.pack('>i', DATA_VERSION))
        f.write(_named(9, 'size') + bytes([3]) + struct.pack('>i', 3) + struct.pack('>iii', *size))
        f.write(_named(9, 'palette') + bytes([10]) + struct.pack('>i', len(palette)))
        for e in palette:
            f.write(payload(10, e))
        f.write(_named(9, 'blocks') + bytes([10]) + struct.pack('>i', total))
        buf = []
        d, ny = G.data, G.ny
        pack3, pack1 = struct.Struct('>iii').pack, struct.Struct('>i').pack
        for x in range(G.x0, G.x1 + 1):                 # column by column; the game sorts on load
            for z in range(G.z0, G.z1 + 1):
                base = G.col(x, z)
                seg = d[base:base + ny]
                if not any(seg):
                    continue
                for k, v in enumerate(seg):
                    if not v:
                        continue
                    y = G.y0 + k
                    rec = pos_head + pack3(x - x0, y - y0, z - z0) + state_head + pack1(index[v])
                    be = block_nbt.get((x, y, z))
                    if be:
                        rec += nbt_head + payload(10, be)
                    buf.append(rec + b'\x00')
                    written += 1
            if len(buf) > 50000:
                f.write(b''.join(buf))
                buf = []
        for (x, y, z), name in mpos.items():
            if G.filled(x, y, z):
                continue
            be = {'id': (8, 'minecraft:structure_block'), 'mode': (8, 'DATA'), 'metadata': (8, name)}
            buf.append(pos_head + pack3(x - x0, y - y0, z - z0) + state_head + pack1(marker_state) +
                       nbt_head + payload(10, be) + b'\x00')
            written += 1
        f.write(b''.join(buf))
        f.write(_named(9, 'entities') + bytes([0]) + struct.pack('>i', 0))
        f.write(b'\x00')
    if written != total:
        os.remove(tmp)
        raise RuntimeError('wrote %d of %d blocks (a marker inside a block?)' % (written, total))
    os.replace(tmp, path)
    return {'path': path, 'size': size, 'blocks': total, 'palette': len(palette), 'markers': len(markers),
            'blockEntities': len(block_nbt), 'bytes': os.path.getsize(path), 'origin': (x0, y0, z0)}


def verify(path):
    """Stream a template back: size, palette names, block count, marker names and positions."""
    data = gzip.decompress(open(path, 'rb').read())
    pos = [0]

    def rd(fmt):
        n = struct.calcsize(fmt)
        v = struct.unpack_from(fmt, data, pos[0])
        pos[0] += n
        return v[0]

    def string():
        n = rd('>H')
        v = data[pos[0]:pos[0] + n].decode('utf-8', 'replace')
        pos[0] += n
        return v

    def value(tag):
        if tag == 1:
            return rd('>b')
        if tag == 2:
            return rd('>h')
        if tag == 3:
            return rd('>i')
        if tag == 4:
            return rd('>q')
        if tag == 5:
            return rd('>f')
        if tag == 6:
            return rd('>d')
        if tag == 8:
            return string()
        if tag == 9:
            t = rd('>b')
            n = rd('>i')
            return [value(t) for _ in range(n)]
        if tag == 10:
            out = {}
            while True:
                t = rd('>b')
                if t == 0:
                    return out
                key = string()                  # the name comes before the value
                out[key] = value(t)
        if tag == 11:
            n = rd('>i')
            return [rd('>i') for _ in range(n)]
        raise ValueError(tag)

    assert rd('>b') == 10
    string()
    out = {'markers': [], 'signs': 0}
    while True:
        t = rd('>b')
        if t == 0:
            break
        name = string()
        if name == 'blocks':
            et = rd('>b')
            n = rd('>i')
            out['blocks'] = n
            for _ in range(n):
                b = value(10)
                nbt = b.get('nbt')
                if nbt and nbt.get('mode') == 'DATA':
                    out['markers'].append((nbt['metadata'], tuple(b['pos'])))
                elif nbt and nbt.get('id') == 'minecraft:sign':
                    out['signs'] += 1
        else:
            v = value(t)
            if name == 'palette':
                out['palette'] = [e['Name'] for e in v]
            else:
                out[name] = v
    return out
