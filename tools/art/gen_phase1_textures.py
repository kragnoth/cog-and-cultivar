#!/usr/bin/env python3
"""Generate Phase 1 textures, kitbashed from the Excalibur resource pack (used with permission).

Usage: python3 gen_phase1_textures.py <excalibur assets/minecraft/textures dir> <mod textures dir>
Outputs are NOT MIT-licensed (see LICENSE). Re-run after palette or design changes.
"""
import sys
from PIL import Image

EXC, OUT = sys.argv[1], sys.argv[2]
T = (0, 0, 0, 0)

def ramp(path, n=6):
    px = [p[:3] for p in Image.open(path).convert('RGBA').getdata() if p[3] > 200]
    px.sort(key=lambda c: 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2])
    k = len(px)
    return [tuple(sum(c[j] for c in px[i * k // n:(i + 1) * k // n]) // max(1, len(px[i * k // n:(i + 1) * k // n])) for j in range(3)) + (255,) for i in range(n)]

WOOD = ramp(f'{EXC}/block/spruce_planks.png')
IRON = ramp(f'{EXC}/block/anvil.png')          # wrought iron reads near-black, like Joe's reference
RUST = [(0x5a, 0x2a, 0x1a, 255), (0x7a, 0x3a, 0x20, 255), (0x9a, 0x4e, 0x28, 255), (0xb8, 0x66, 0x34, 255), (0xd0, 0x82, 0x48, 255), (0xe4, 0xa0, 0x60, 255)]

def canvas():
    return Image.new('RGBA', (16, 16), T)

def put(im, x, y, c):
    if 0 <= x < 16 and 0 <= y < 16:
        im.putpixel((x, y), c)

def timber_trellis():
    im = canvas()
    for x in (1, 14):                                  # posts, lit left edge
        for y in range(16):
            put(im, x, y, WOOD[3]); put(im, x + 1 if x == 1 else x - 1, y, WOOD[1])
    for y in (3, 8, 13):                               # slats
        for x in range(1, 15):
            put(im, x, y, WOOD[4] if x % 4 else WOOD[2])
    for x in (7, 8):                                   # centre stake
        for y in range(2, 16):
            put(im, x, y, WOOD[3] if x == 7 else WOOD[1])
    for (x, y) in ((1, 3), (14, 3), (7, 8), (1, 13), (14, 13), (7, 3), (7, 13)):  # lashing
        put(im, x, y, WOOD[0]); put(im, x + 1, y, WOOD[0])
    return im

def iron_trellis():
    """Wrought-iron arch: rails, arched crown, collar rings, crown and base scrolls."""
    im = canvas()
    dark, mid, hi = IRON[0], IRON[2], IRON[4]
    for x in (1, 14):
        for y in range(4, 16):
            put(im, x, y, mid if x == 1 else dark)
    arch = [(1, 4), (1, 3), (2, 2), (3, 1), (4, 1), (5, 0), (6, 0), (7, 0), (8, 0), (9, 0), (10, 0), (11, 1), (12, 1), (13, 2), (14, 3), (14, 4)]
    for x, y in arch:
        put(im, x, y, mid)
    for x in (5, 10):                                  # inner bars
        for y in range(1, 16):
            put(im, x, y, dark)
    for x in (5, 10):                                  # collar rings
        put(im, x - 1, 9, hi); put(im, x, 9, hi); put(im, x + 1, 9, hi)
    for x, y in ((6, 3), (7, 2), (8, 2), (9, 3), (7, 4), (8, 4), (7, 5), (8, 5)):  # crown heart scroll
        put(im, x, y, hi if y < 4 else mid)
    for x, y in ((2, 14), (3, 13), (3, 12), (2, 11), (12, 11), (13, 12), (12, 13), (13, 14),  # base C-scrolls
                 (6, 14), (7, 13), (8, 13), (9, 14)):
        put(im, x, y, mid)
    for x in range(1, 15):                             # ground bar
        put(im, x, 15, dark)
    return im

def rod(palette):
    im = canvas()
    for x in range(16):
        for y in range(16):
            im.putpixel((x, y), palette[1 + (y % 3)] if (x + y) % 5 else palette[0])
    return im

def recolour(src, palette, keep_rows=16):
    im = Image.open(src).convert('RGBA')
    out = canvas()
    for x in range(16):
        for y in range(16 - keep_rows, 16):
            r, g, b, a = im.getpixel((x, y))
            if a > 0:
                lum = (0.2126 * r + 0.7152 * g + 0.0722 * b) / 255
                c = palette[min(len(palette) - 1, int(lum * len(palette)))]
                if (x * 7 + y * 3) % 11 == 0:          # rust speckle
                    c = palette[-1]
                out.putpixel((x, y), c)
    return out

timber_trellis().save(f'{OUT}/block/timber_trellis.png')
iron_trellis().save(f'{OUT}/block/iron_trellis.png')
rod(WOOD).save(f'{OUT}/block/timber_rod.png')
rod(IRON).save(f'{OUT}/block/iron_rod.png')
for stage, rows in enumerate((5, 8, 12, 16)):
    recolour(f'{EXC}/block/dead_bush.png', RUST, rows).save(f'{OUT}/block/rust_rot_stage{stage}.png')
Image.open(f'{EXC}/item/wheat_seeds.png').convert('RGBA').save(f'{OUT}/item/seed.png')
print('ok')
