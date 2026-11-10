#!/usr/bin/env python3
"""Phase 7 art pass, kitbashed from the Excalibur resource pack (used with permission; not MIT, see LICENSE).

  * seed.png            Excalibur beetroot seeds (the most solid clumps) in greyscale, tinted per species in game
                        (colour from the plant's `seed_color`, else its first product's sprite);
  * seed_satchel(_dial) Excalibur bundle with a brass buckle and rivets (the dial version adds the brass dial);
  * bed_soil.png        Planter Bed floor: dark moist tilled loam, so it doesn't read as coarse dirt (a dry soil);
  * trial_soil.png      Trial Bed floor: tilled loam pegged out with brass markers and twine into four test plots;
  * assay_glass.png     Assaying Engine window: Excalibur glass with its frame in brass and rivets at the corners.

Usage: python3 tools/art/gen_art_pass.py [<excalibur assets/minecraft/textures dir>]
"""
import sys
from PIL import Image

EXC = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/src/excalibur/pack/assets/minecraft/textures'
OUT = 'src/main/resources/assets/cultivar/textures'
BRASS = [(0x84, 0x51, 0x25, 255), (0xab, 0x75, 0x31, 255), (0xc4, 0x94, 0x3d, 255),
         (0xd6, 0xb0, 0x55, 255), (0xdf, 0xbe, 0x5e, 255), (0xf4, 0xdf, 0x8d, 255)]
TWINE = (0xd8, 0xcc, 0xa8, 255)


def load(path):
    return Image.open(f'{EXC}/{path}.png').convert('RGBA').crop((0, 0, 16, 16))


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def to_brass(im):
    """Map opaque pixels onto the brass ramp by brightness."""
    px = [(x, y) for y in range(16) for x in range(16) if im.getpixel((x, y))[3] > 200]
    ls = [lum(im.getpixel(p)) for p in px] or [0]
    lo, hi = min(ls), max(ls)
    out = im.copy()
    for p in px:
        t = 0 if hi == lo else (lum(im.getpixel(p)) - lo) / (hi - lo)
        out.putpixel(p, BRASS[min(5, int(t * 6))])
    return out


def seed():
    """Greyscale, lifted so the per-species tint reads, with the darkest pixels kept as an outline."""
    im = load('item/beetroot_seeds')
    out = Image.new('RGBA', (16, 16))
    for y in range(16):
        for x in range(16):
            r, g, b, a = im.getpixel((x, y))
            if a:
                v = int(110 + lum((r, g, b)) / 255 * 145)
                out.putpixel((x, y), (v, v, v, a))
    return out


def satchel():
    im = load('item/bundle')
    for x in range(6, 10):  # brass buckle across the cinch
        im.putpixel((x, 6), BRASS[3] if x in (6, 9) else BRASS[1])
    im.putpixel((7, 7), BRASS[5])
    im.putpixel((8, 7), BRASS[4])
    for x, y in ((4, 10), (11, 10), (5, 13), (10, 13)):  # rivets
        im.putpixel((x, y), BRASS[4])
    return im


def satchel_dial(base):
    dial = to_brass(load('item/clock_00')).resize((7, 7), Image.NEAREST)
    out = base.copy()
    out.alpha_composite(dial, (8, 8))
    return out


def bed_soil():
    im = load('block/farmland_moist')
    for x, y in ((3, 2), (11, 5), (6, 10), (13, 13), (1, 13)):  # a few lighter clods
        r, g, b, a = im.getpixel((x, y))
        im.putpixel((x, y), (min(255, r + 28), min(255, g + 22), min(255, b + 16), a))
    return im


def trial_soil():
    im = load('block/farmland')
    for i in range(16):  # twine dividing four test plots
        if i % 2 == 0:
            im.putpixel((7, i), TWINE)
            im.putpixel((i, 7), TWINE)
    for x, y in ((7, 7), (0, 0), (15, 0), (0, 15), (15, 15), (7, 0), (0, 7), (15, 7), (7, 15)):
        im.putpixel((x, y), BRASS[4])  # brass marker pegs
    im.putpixel((7, 7), BRASS[5])
    return im


def assay_glass():
    im = to_brass(load('block/glass'))
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (7, 7), (8, 8)):
        if im.getpixel((x, y))[3] > 0:
            im.putpixel((x, y), BRASS[0])  # rivets
    return im


s = satchel()
s.save(f'{OUT}/item/seed_satchel.png')
satchel_dial(s).save(f'{OUT}/item/seed_satchel_dial.png')
seed().save(f'{OUT}/item/seed.png')
bed_soil().save(f'{OUT}/block/bed_soil.png')
trial_soil().save(f'{OUT}/block/trial_soil.png')
assay_glass().save(f'{OUT}/block/assay_glass.png')
print('art pass ok')
