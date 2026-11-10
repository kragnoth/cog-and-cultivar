#!/usr/bin/env python3
"""Blight sprites, kitbashed from the Excalibur resource pack (used with permission; not MIT, see LICENSE).

Each blight takes its own Excalibur plant silhouette, a palette mapped by brightness, and a small detail of its own
(slime beads, embers, pustules, galls...). Stages reveal the sprite from the ground up (5, 8, 12, 16 rows), like
Rust-rot. Rust-rot itself comes from gen_phase1_textures.py.

Usage: python3 tools/art/gen_blight_textures.py <excalibur assets/minecraft/textures dir>
"""
import sys
from PIL import Image

EXC = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/src/excalibur/pack/assets/minecraft/textures'
OUT = 'src/main/resources/assets/cultivar/textures/block/{}_stage{}.png'
ROWS = (5, 8, 12, 16)


def hexes(*cs):
    return [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,) for c in cs]


def source(name, flip=False):
    im = Image.open(f'{EXC}/block/{name}.png').convert('RGBA').crop((0, 0, 16, 16))  # first frame if animated
    return im.transpose(Image.FLIP_TOP_BOTTOM) if flip else im


def bottom_align(im):
    """Shift the sprite down so its lowest opaque row sits on the ground (flipped sources start mid-air)."""
    rows = [y for y in range(16) if any(im.getpixel((x, y))[3] > 0 for x in range(16))]
    if not rows or rows[-1] == 15:
        return im
    out = Image.new('RGBA', (16, 16))
    out.paste(im, (0, 15 - rows[-1]))
    return out


def recolour(im, palette):
    """Map every opaque pixel to the palette by its brightness relative to the sprite's own range."""
    px = [(x, y, im.getpixel((x, y))) for y in range(16) for x in range(16) if im.getpixel((x, y))[3] > 0]
    lums = [0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2] for _, _, c in px]
    lo, hi = min(lums), max(lums)
    out = Image.new('RGBA', (16, 16))
    for (x, y, c), l in zip(px, lums):
        t = 0 if hi == lo else (l - lo) / (hi - lo)
        out.putpixel((x, y), palette[min(len(palette) - 1, int(t * len(palette)))])
    return out


def opaque(im):
    return [(x, y) for y in range(16) for x in range(16) if im.getpixel((x, y))[3] > 0]


def dot(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), c)


def on_plant(im, every, offset=0):
    """Deterministic scatter of plant pixels (no randomness, so re-runs are stable)."""
    return [(x, y) for x, y in opaque(im) if (x * 7 + y * 13 + offset) % every == 0]


def tips(im):
    """Topmost opaque pixel of each column."""
    out = []
    for x in range(16):
        ys = [y for y in range(16) if im.getpixel((x, y))[3] > 0]
        if ys:
            out.append((x, ys[0]))
    return out


# --------------------------------------------------------------------------------------------------- the blights

def bog_rot():
    im = recolour(source('seagrass1'), hexes('1f2412', '2e3418', '434a1f', '5a5f27', '717533', '8a8a45'))
    for x, y in on_plant(im, 17, 3):  # slime beads: a bright bead with a darker underside
        dot(im, x, y, hexes('8fd16a')[0])
        dot(im, x, y + 1, hexes('4f8f3a')[0])
    return im


def dry_rot():
    im = recolour(source('tall_dry_grass'), hexes('6e604a', '8f7f62', 'b3a284', 'cbbb9a', 'ddd0b2', 'ece3cb'))
    for x, y in tips(im):  # rotted, darkened tips
        dot(im, x, y, hexes('4a3a2a')[0])
    for x, y in on_plant(im, 23, 5):  # cracks
        dot(im, x, y, hexes('5e4f3b')[0])
    return im


def ash_blight():
    im = recolour(source('crimson_roots'), hexes('2a2a2c', '3d3d40', '55555a', '6f6f73', '8c8c90', 'aaaaae'))
    for i, (x, y) in enumerate(tips(im)):  # embers smouldering at a few tips
        if i % 3 == 1:
            dot(im, x, y, hexes('e8752a')[0])
            dot(im, x, y + 1, hexes('9c3b1c')[0])
    return im


def sour_moss():
    im = recolour(source('sweet_berry_bush_stage1'), hexes('2f3a10', '45561a', '617a22', '7f9a2b', 'a3b835', 'c4d24a'))
    for x, y in on_plant(im, 19, 2):  # acid pustules
        dot(im, x, y, hexes('eef06a')[0])
    return im


def mildew():
    base = recolour(source('fern'), hexes('5c6366', '7a8286', '9aa2a6', 'b8bfc2', 'd3d9db', 'eef2f2'))
    im = base.copy()
    fuzz = hexes('f4f7f7')[0]
    for x, y in opaque(base):  # powdery halo just outside the leaves
        for dx, dy in ((1, 0), (-1, 0), (0, -1)):
            nx, ny = x + dx, y + dy
            if 0 <= nx < 16 and 0 <= ny < 16 and base.getpixel((nx, ny))[3] == 0 and (nx * 5 + ny * 3) % 7 == 0:
                im.putpixel((nx, ny), fuzz[:3] + (170,))
    return im


def sun_scald():
    im = recolour(source('dead_bush0'), hexes('3a1e10', '6a3414', '9a5016', 'c47420', 'df9c34', 'f2c75a'))
    for x, y in opaque(im):  # charred upper third
        if y < 6 and (x + y) % 2 == 0:
            dot(im, x, y, hexes('24150d')[0])
    return im


def leach_creep():
    im = bottom_align(recolour(source('hanging_roots', flip=True), hexes('2c3440', '3c4654', '505c6c', '667385', '7f8c9c', '9aa6b4')))
    for x, y in tips(im):  # pale, washed-out growing tips
        dot(im, x, y, hexes('c3cbd4')[0])
    return im


def glut_canker():
    im = recolour(source('crimson_fungus'), hexes('2e0f22', '4a1834', '6a2448', '8c325c', 'ad4470', 'c96a8a'))
    for x, y in on_plant(im, 11, 4):  # swollen pale galls
        dot(im, x, y, hexes('e6b7a2')[0])
    return im


BLIGHTS = {'bog_rot': bog_rot, 'dry_rot': dry_rot, 'ash_blight': ash_blight, 'sour_moss': sour_moss,
           'mildew': mildew, 'sun_scald': sun_scald, 'leach_creep': leach_creep, 'glut_canker': glut_canker}

for name, make in BLIGHTS.items():
    full = make()
    for stage, rows in enumerate(ROWS):
        im = Image.new('RGBA', (16, 16))
        im.paste(full.crop((0, 16 - rows, 16, 16)), (0, 16 - rows))
        im.save(OUT.format(name, stage))
print('blight textures ok')
