#!/usr/bin/env python3
"""Soil conditioner sprites.

Items are kitbashed from the Excalibur resource pack (used with permission; not MIT, see LICENSE):
  * agricultural_lime.png  Excalibur sugar pile, re-ramped to a chalky ivory with grey grit;
  * sulfur_dust.png        Excalibur gunpowder pile on a sulfur-yellow ramp;
  * compost.png            Excalibur redstone pile's silhouette filled from the composter's compost, with leaf bits.

The soil overlays (block/amendment/<kind>_<1|2>.png) are procedural speckle patterns, drawn on the floor of a trellis
over amended soil: each kind scatters on its own pixel hash so several layers interleave instead of hiding each
other, and the _2 version is about twice as dense.

Usage: python3 tools/art/gen_conditioner_textures.py [<excalibur assets/minecraft/textures dir>]
"""
import os
import random
import sys
from PIL import Image

EXC = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/src/excalibur/pack/assets/minecraft/textures'
OUT = 'src/main/resources/assets/cultivar/textures'


def hexes(*cs):
    return [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,) for c in cs]


def load(path):
    return Image.open(f'{EXC}/{path}.png').convert('RGBA').crop((0, 0, 16, 16))


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def opaque(im):
    return [(x, y) for y in range(16) for x in range(16) if im.getpixel((x, y))[3] > 0]


def recolour(im, palette):
    px = opaque(im)
    ls = [lum(im.getpixel(p)) for p in px]
    lo, hi = min(ls), max(ls)
    out = im.copy()
    for p, l in zip(px, ls):
        t = 0 if hi == lo else (l - lo) / (hi - lo)
        r, g, b, _ = palette[min(len(palette) - 1, int(t * len(palette)))]
        out.putpixel(p, (r, g, b, im.getpixel(p)[3]))
    return out


LIME = hexes('8f8b80', 'b2ada0', 'cfcabd', 'e2ded2', 'efece3', 'fbf9f3')
SULFUR = hexes('8a7414', 'b39a1c', 'd2b928', 'e6d03c', 'f2e164', 'fbf09a')
COMPOST = hexes('2a1a0e', '3a2614', '4d331b', '624325', '7a5631', '93693d')
LEAF = hexes('3f5a22', '5a7d30')


def lime():
    im = recolour(load('item/sugar'), LIME)
    for x, y in opaque(im):
        if (x * 5 + y * 11) % 17 == 0:
            im.putpixel((x, y), LIME[1])  # grey grit
    return im


def sulfur():
    return recolour(load('item/gunpowder'), SULFUR)


def compost():
    shape = load('item/redstone')
    fill = recolour(load('block/composter_compost'), COMPOST)
    sl = [lum(shape.getpixel(p)) for p in opaque(shape)]
    lo, hi = min(sl), max(sl)
    out = Image.new('RGBA', (16, 16))
    for x, y in opaque(shape):
        c = fill.getpixel((x, y))
        t = (lum(shape.getpixel((x, y))) - lo) / (hi - lo or 1)  # keep the pile's shading
        f = 0.7 + 0.6 * t
        out.putpixel((x, y), (min(255, int(c[0] * f)), min(255, int(c[1] * f)), min(255, int(c[2] * f)), 255))
    for i, (x, y) in enumerate(p for p in opaque(shape) if (p[0] * 7 + p[1] * 3) % 13 == 0):
        out.putpixel((x, y), LEAF[i % 2])  # leaf bits
    return out


# ------------------------------------------------------------------------------------------------- soil overlays

OVERLAYS = {
    # kind: (seed, colours: highlight, body, accent)
    'alkaline': (11, hexes('fbf9f3', 'e2ded2', 'b2ada0')),
    'acidic': (23, hexes('fbf09a', 'e6d03c', 'b39a1c')),
    'rich': (37, hexes('b08048', '1e1209', '6f9a3a')),   # dark crumbs, light tops, leaf bits
    'lean': (41, hexes('d8cfb6', 'b8ad90', '948a70')),
    'wet': (53, hexes('cfe3f7', '8fb7e6', '4f76b0')),
    'dry': (67, hexes('ecd7a6', 'cdb07a', 'a3854f')),
}


def overlay(kind, density):
    """Small clumps of dust: a few pixels around each scattered centre. Density 2 adds clumps on top of density 1."""
    seed, (hi, body, accent) = OVERLAYS[kind]
    rnd = random.Random(seed)
    centres = [(rnd.randrange(16), rnd.randrange(16)) for _ in range(14)]
    im = Image.new('RGBA', (16, 16))
    for i, (cx, cy) in enumerate(centres[:7 if density == 1 else 14]):
        cells = [(0, 0)] + rnd.sample([(1, 0), (-1, 0), (0, 1), (0, -1), (1, 1), (-1, 1)], 2 + rnd.randrange(2))
        for j, (dx, dy) in enumerate(cells):
            x, y = (cx + dx) % 16, (cy + dy) % 16
            if kind == 'dry' and j > 0:
                im.putpixel((x, y), accent)  # crack edges
                continue
            im.putpixel((x, y), hi if j == 0 else (accent if (i + j) % 4 == 0 else body))
    return im


os.makedirs(f'{OUT}/block/amendment', exist_ok=True)
lime().save(f'{OUT}/item/agricultural_lime.png')
sulfur().save(f'{OUT}/item/sulfur_dust.png')
compost().save(f'{OUT}/item/compost.png')
for kind in OVERLAYS:
    for d in (1, 2):
        overlay(kind, d).save(f'{OUT}/block/amendment/{kind}_{d}.png')
print('conditioner textures ok')
