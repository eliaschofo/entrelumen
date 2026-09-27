"""A compact voxel grid for Solsticio v8: one uint16 palette index per cell in a flat array.

The v8 island is about 345 x 345 x 240 cells; a dict of tuples that size would not fit in the
memory this workstation has free, and most passes (the ground fill, the leak check, the renders)
walk whole columns. Columns are contiguous (y varies fastest), so a column is one slice.

Index 0 is empty. Setting 'minecraft:air' also empties the cell: the city is placed into the void,
so explicit air never needs to be stored.
"""
from array import array

AIR = 'minecraft:air'


class Grid:
    def __init__(self, x0, x1, y0, y1, z0, z1, alloc=True):
        self.x0, self.x1, self.y0, self.y1, self.z0, self.z1 = x0, x1, y0, y1, z0, z1
        self.nx, self.ny, self.nz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
        self.data = array('H')
        self.palette = [None]
        self.index = {}
        self.dropped = 0
        if alloc:
            self.reset()

    def reset(self):
        """Empty every cell and the palette (allocates the array)."""
        self.data = array('H', bytes(2 * self.nx * self.ny * self.nz))
        self.palette[:] = [None]
        self.index.clear()
        self.dropped = 0

    # ---- palette ----
    def sid(self, state):
        if state is None or state == AIR:
            return 0
        i = self.index.get(state)
        if i is None:
            i = len(self.palette)
            self.palette.append(state)
            self.index[state] = i
        return i

    # ---- cells ----
    def inside(self, x, y, z):
        return self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1 and self.z0 <= z <= self.z1

    def at(self, x, y, z):
        return ((x - self.x0) * self.nz + (z - self.z0)) * self.ny + (y - self.y0)

    def col(self, x, z):
        """Index of (x, y0, z): the column is data[col : col + ny]."""
        return ((x - self.x0) * self.nz + (z - self.z0)) * self.ny

    def set(self, x, y, z, state):
        if not (self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1 and self.z0 <= z <= self.z1):
            self.dropped += 1
            return
        self.data[((x - self.x0) * self.nz + (z - self.z0)) * self.ny + (y - self.y0)] = self.sid(state)

    __setitem__ = lambda self, k, v: self.set(k[0], k[1], k[2], v)

    def setdefault(self, x, y, z, state):
        """Set only an empty cell; returns True when it was set."""
        if not self.inside(x, y, z):
            return False
        i = self.at(x, y, z)
        if self.data[i]:
            return False
        self.data[i] = self.sid(state)
        return True

    def get(self, x, y, z, default=None):
        if not (self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1 and self.z0 <= z <= self.z1):
            return default
        v = self.data[((x - self.x0) * self.nz + (z - self.z0)) * self.ny + (y - self.y0)]
        return self.palette[v] if v else default

    def gid(self, x, y, z):
        if not (self.x0 <= x <= self.x1 and self.y0 <= y <= self.y1 and self.z0 <= z <= self.z1):
            return 0
        return self.data[((x - self.x0) * self.nz + (z - self.z0)) * self.ny + (y - self.y0)]

    def filled(self, x, y, z):
        return self.gid(x, y, z) != 0

    def clear(self, x, y, z):
        if self.inside(x, y, z):
            self.data[self.at(x, y, z)] = 0

    def fill_box(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(min(x0, x1), max(x0, x1) + 1):
            for z in range(min(z0, z1), max(z0, z1) + 1):
                for y in range(min(y0, y1), max(y0, y1) + 1):
                    self.set(x, y, z, state)

    def top(self, x, z, pred=None):
        """Highest filled y in the column (optionally where pred(state) holds), or None."""
        if not (self.x0 <= x <= self.x1 and self.z0 <= z <= self.z1):
            return None
        c = self.col(x, z)
        d = self.data
        for k in range(self.ny - 1, -1, -1):
            v = d[c + k]
            if v and (pred is None or pred(self.palette[v])):
                return self.y0 + k
        return None

    # ---- bulk ----
    def count(self):
        return len(self.data) - self.data.count(0)

    def items(self):
        """(x, y, z, state) for every filled cell, column by column."""
        d, ny, pal = self.data, self.ny, self.palette
        for x in range(self.x0, self.x1 + 1):
            for z in range(self.z0, self.z1 + 1):
                c = self.col(x, z)
                seg = d[c:c + ny]
                if not any(seg):
                    continue
                for k, v in enumerate(seg):
                    if v:
                        yield x, self.y0 + k, z, pal[v]

    def histogram(self):
        """state -> count of filled cells."""
        counts = [0] * len(self.palette)
        for v in self.data:
            counts[v] += 1
        return {self.palette[i]: n for i, n in enumerate(counts) if i and n}

    def bounds(self):
        """(x0, y0, z0, x1, y1, z1) of the filled cells."""
        xs, zs, ys = [], [], []
        d, ny = self.data, self.ny
        ymin, ymax = None, None
        for x in range(self.x0, self.x1 + 1):
            for z in range(self.z0, self.z1 + 1):
                c = self.col(x, z)
                seg = d[c:c + ny]
                if not any(seg):
                    continue
                xs.append(x)
                zs.append(z)
                lo = next(k for k, v in enumerate(seg) if v)
                hi = ny - 1 - next(k for k, v in enumerate(reversed(seg)) if v)
                ymin = lo if ymin is None else min(ymin, lo)
                ymax = hi if ymax is None else max(ymax, hi)
        return min(xs), self.y0 + ymin, min(zs), max(xs), self.y0 + ymax, max(zs)
