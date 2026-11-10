#!/usr/bin/env python3
"""Builds the Derelict Conservatory structure templates (vanilla structure NBT) from a readable layout.

Run from the repo root: python3 tools/worldgen/gen_conservatory.py
Writes src/main/resources/data/cultivar/structure/derelict_conservatory/{greenhouse,lean_to}.nbt.
The structure is placed whole; decay (missing glass, moss, cobwebs) is added per instance by the
cultivar:derelict_decay processor list, so no two ruins look alike.
"""
import gzip
import io
import os
import struct

DATA_VERSION = 3955  # Minecraft 1.21.1
OUT = 'src/main/resources/data/cultivar/structure/derelict_conservatory'


# ----------------------------------------------------------------------------- tiny NBT writer
class Byte(int): pass
class Short(int): pass
class Int(int): pass


def _name(buf, name):
    b = name.encode('utf-8')
    buf.write(struct.pack('>H', len(b)) + b)


def _tag_id(v):
    if isinstance(v, Byte): return 1
    if isinstance(v, Short): return 2
    if isinstance(v, (Int, int)) and not isinstance(v, bool): return 3
    if isinstance(v, float): return 6
    if isinstance(v, str): return 8
    if isinstance(v, list): return 9
    if isinstance(v, dict): return 10
    raise TypeError(type(v))


def _payload(buf, v):
    t = _tag_id(v)
    if t == 1: buf.write(struct.pack('>b', v))
    elif t == 2: buf.write(struct.pack('>h', v))
    elif t == 3: buf.write(struct.pack('>i', v))
    elif t == 6: buf.write(struct.pack('>d', v))
    elif t == 8: _name(buf, v)
    elif t == 9:
        inner = _tag_id(v[0]) if v else 0
        buf.write(struct.pack('>bi', inner, len(v)))
        for x in v:
            _payload(buf, x)
    elif t == 10:
        for k, x in v.items():
            buf.write(struct.pack('>b', _tag_id(x)))
            _name(buf, k)
            _payload(buf, x)
        buf.write(b'\x00')


def write_nbt(path, root):
    buf = io.BytesIO()
    buf.write(b'\x0a')
    _name(buf, '')
    _payload(buf, root)
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with gzip.open(path, 'wb') as f:
        f.write(buf.getvalue())


# ----------------------------------------------------------------------------- template builder
class Template:
    def __init__(self, sx, sy, sz):
        self.size = (sx, sy, sz)
        self.blocks = {}

    def set(self, x, y, z, state, nbt=None):
        self.blocks[(x, y, z)] = (state, nbt)

    def fill(self, x0, y0, z0, x1, y1, z1, state):
        for x in range(x0, x1 + 1):
            for y in range(y0, y1 + 1):
                for z in range(z0, z1 + 1):
                    self.set(x, y, z, state)

    def save(self, path):
        palette, index, blocks = [], {}, []
        for (x, y, z), (state, nbt) in sorted(self.blocks.items()):
            if state not in index:
                index[state] = len(palette)
                name, _, props = state.partition('[')
                entry = {'Name': name}
                if props:
                    entry['Properties'] = dict(p.split('=') for p in props.rstrip(']').split(','))
                palette.append(entry)
            b = {'pos': [Int(x), Int(y), Int(z)], 'state': Int(index[state])}
            if nbt:
                b['nbt'] = nbt
            blocks.append(b)
        write_nbt(path, {'DataVersion': Int(DATA_VERSION), 'size': [Int(v) for v in self.size],
                         'palette': palette, 'blocks': blocks, 'entities': []})


FOUNDATION = 'minecraft:cobblestone'
POST = 'minecraft:polished_deepslate_wall'
BEAM = 'minecraft:polished_deepslate'
GLASS = 'minecraft:glass'
PANE = 'minecraft:glass_pane'
ROOF_EDGE = 'minecraft:weathered_cut_copper_slab[type=bottom,waterlogged=false]'
TRELLIS = 'cultivar:timber_trellis[cross=false,cloche=false]'
CHEST_NBT = {'id': 'minecraft:chest', 'LootTable': 'cultivar:chests/derelict_conservatory'}


def greenhouse():
    """9 x 7 x 11 iron-framed glasshouse: two trellis beds either side of a water channel."""
    W, H, L = 9, 7, 11
    t = Template(W, H, L)
    # Foundation ring and floor.
    t.fill(0, 0, 0, W - 1, 0, L - 1, FOUNDATION)
    t.fill(1, 0, 1, W - 2, 0, L - 2, 'minecraft:coarse_dirt')
    # Beds of farmland either side of a water channel (farmland under trellises is kept by the trellis).
    for z in range(2, L - 2):
        t.set(4, 0, z, 'minecraft:water[level=0]')
        for x in (2, 3, 5, 6):
            t.set(x, 0, z, 'minecraft:farmland[moisture=7]')
        for x in (2, 6):
            t.set(x, 1, z, TRELLIS)
    # Iron frame: posts on the corners and every third block, glass between.
    for y in range(1, 5):
        for x in range(W):
            for z in (0, L - 1):
                t.set(x, y, z, POST if x % 4 == 0 else PANE)
        for z in range(L):
            for x in (0, W - 1):
                t.set(x, y, z, POST if z % 5 == 0 else PANE)
    # Doorway in the front wall.
    for y in (1, 2):
        t.set(4, y, 0, 'minecraft:air')
    # Ridged glass roof on deepslate beams, copper eaves.
    for z in range(L):
        for x in range(W):
            if x in (0, W - 1):
                t.set(x, 5, z, ROOF_EDGE)
            else:
                t.set(x, 5, z, BEAM if z % 5 == 0 else GLASS)
        for x in range(2, W - 2):
            t.set(x, 6, z, BEAM if x == 4 else GLASS)
    # Inside: the botanist's corner.
    t.set(7, 1, 9, 'minecraft:chest[facing=west,type=single,waterlogged=false]', CHEST_NBT)
    t.set(7, 1, 8, 'minecraft:composter[level=4]')
    t.set(1, 1, 9, 'minecraft:crafting_table')
    t.set(1, 1, 8, 'minecraft:water_cauldron[level=2]')
    t.set(1, 1, 1, 'minecraft:barrel[facing=up,open=false]')
    return t


def lean_to():
    """7 x 5 x 7 lean-to against nothing: a single bed, half its roof gone, one chest."""
    W, H, L = 7, 5, 7
    t = Template(W, H, L)
    t.fill(0, 0, 0, W - 1, 0, L - 1, FOUNDATION)
    t.fill(1, 0, 1, W - 2, 0, L - 2, 'minecraft:podzol')
    for z in range(2, L - 1):
        t.set(2, 0, z, 'minecraft:farmland[moisture=7]')
        t.set(3, 0, z, 'minecraft:water[level=0]')
        t.set(4, 0, z, 'minecraft:farmland[moisture=7]')
        t.set(2, 1, z, TRELLIS)
        t.set(4, 1, z, TRELLIS)
    for y in range(1, 4):
        for x in range(W):
            t.set(x, y, L - 1, POST if x % 3 == 0 else PANE)
        for z in range(L):
            for x in (0, W - 1):
                t.set(x, y, z, POST if z % 3 == 0 else PANE)
    for z in range(L):
        for x in range(W):
            t.set(x, 4, z, BEAM if x in (0, W - 1) or z == L - 1 else (GLASS if z >= 3 else 'minecraft:air'))
    t.set(5, 1, 5, 'minecraft:chest[facing=west,type=single,waterlogged=false]', CHEST_NBT)
    t.set(1, 1, 5, 'minecraft:composter[level=2]')
    return t


if __name__ == '__main__':
    greenhouse().save(f'{OUT}/greenhouse.nbt')
    lean_to().save(f'{OUT}/lean_to.nbt')
    print('conservatory templates ok')
