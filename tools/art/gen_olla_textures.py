#!/usr/bin/env python3
"""Olla textures, kitbashed from a textures dir (vanilla for the default art, Excalibur for its built-in pack).

  * olla_side.png         the source's terracotta, shaded like a buried pot: a darker rim band at the top,
                          a lighter shoulder below it, damp darkening toward the base;
  * olla_top.png          terracotta lid ring with a dark empty mouth;
  * olla_top_filled.png   the same with water in the mouth (the source's water, ramped to a fixed blue: block
                          textures can't take the biome water tint here).

Usage: python3 tools/art/gen_olla_textures.py <assets/minecraft/textures dir>
"""
import sys
from PIL import Image

SRC = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/src/excalibur/pack/assets/minecraft/textures'
OUT = 'src/main/resources/assets/cultivar/textures/block'
WATER = [(0x1f, 0x3d, 0x7a, 255), (0x2a, 0x52, 0x9a, 255), (0x34, 0x66, 0xb3, 255), (0x45, 0x7b, 0xc6, 255), (0x63, 0x98, 0xd6, 255)]


def load(name):
    return Image.open(f'{SRC}/block/{name}.png').convert('RGBA').crop((0, 0, 16, 16))


def shade(c, f):
    return tuple(min(255, max(0, int(v * f))) for v in c[:3]) + (c[3],)


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def side():
    im = load('terracotta')
    for y in range(16):
        f = 0.72 if y < 2 else 1.12 if y == 2 else 1.0 - max(0, y - 9) * 0.035  # rim, shoulder, damp base
        for x in range(16):
            im.putpixel((x, y), shade(im.getpixel((x, y)), f))
    for x in range(16):
        im.putpixel((x, 1), shade(im.getpixel((x, 1)), 0.85))  # rim lip shadow
    return im


def mouth(filled):
    im = load('terracotta')
    water = load('water_still')
    wl = [lum(water.getpixel((x, y))) for y in range(16) for x in range(16)]
    lo, hi = min(wl), max(wl)
    for y in range(16):
        for x in range(16):
            d = ((x - 7.5) ** 2 + (y - 7.5) ** 2) ** 0.5
            c = im.getpixel((x, y))
            if d < 4.6:
                if filled:
                    t = (lum(water.getpixel((x, y))) - lo) / (hi - lo or 1)
                    im.putpixel((x, y), WATER[min(4, int(t * 5))])
                else:
                    im.putpixel((x, y), shade(c, 0.35))
            elif d < 5.8:
                im.putpixel((x, y), shade(c, 1.15))  # lip catches the light
            elif d < 6.6:
                im.putpixel((x, y), shade(c, 0.85))
    if filled:
        im.putpixel((6, 6), (0xb8, 0xd8, 0xf5, 255))  # glint
    return im


side().save(f'{OUT}/olla_side.png')
mouth(False).save(f'{OUT}/olla_top.png')
mouth(True).save(f'{OUT}/olla_top_filled.png')
print('olla textures ok')
