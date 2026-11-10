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

# ---- Bell Cloche (1d): Excalibur glass frame with brass rim, plus a brass knob
BRASS = [(0x84, 0x51, 0x25, 255), (0xab, 0x75, 0x31, 255), (0xc4, 0x94, 0x3d, 255), (0xd6, 0xb0, 0x55, 255), (0xdf, 0xbe, 0x5e, 255), (0xf4, 0xdf, 0x8d, 255)]
# Cloche glass: own texture (Excalibur glass is nearly fully clear, so the bell vanished in-game).
# Faint green-grey tint, Excalibur glass's edge pixels kept, two diagonal highlight streaks. Rendered translucent.
exc_glass = Image.open(f'{EXC}/block/glass.png').convert('RGBA')
cloche = Image.new('RGBA', (16, 16), (200, 222, 214, 46))
for x in range(16):
    for y in range(16):
        p = exc_glass.getpixel((x, y))
        if p[3] > 200:
            cloche.putpixel((x, y), (p[0], p[1], p[2], 150))
for i in range(16):
    for x, y in ((i, 15 - i), (i + 3, 15 - i)):
        if 0 <= x < 16 and 2 <= y < 14:
            cloche.putpixel((x, y), (245, 250, 248, 110))
cloche.save(f'{OUT}/block/bell_cloche.png')
knob = canvas()
for x in range(16):
    for y in range(16):
        knob.putpixel((x, y), BRASS[5] if (x + y) % 3 == 0 else BRASS[3])
knob.save(f'{OUT}/block/brass_knob.png')
print('cloche ok')

# ---- Phase 2a tools: kitbash Excalibur tool sprites, brass accents from the C&C brass ramp
def tint(src, mapping):
    im = Image.open(src).convert('RGBA')
    out = im.copy()
    for x in range(16):
        for y in range(16):
            p = im.getpixel((x, y))
            if p[3] > 0:
                out.putpixel((x, y), mapping(x, y, p))
    return out

def lum(p):
    return (0.2126 * p[0] + 0.7152 * p[1] + 0.0722 * p[2]) / 255

def to_ramp(ramp):
    return lambda x, y, p: ramp[min(len(ramp) - 1, int(lum(p) * len(ramp)))]

shovel = Image.open(f'{EXC}/item/iron_shovel.png').convert('RGBA')
shovel.save(f'{OUT}/item/transplanting_spade.png')
full = shovel.copy()
SOIL = [(0x3b, 0x26, 0x18, 255), (0x55, 0x38, 0x22, 255), (0x6e, 0x4a, 0x2c, 255)]
LEAF = [(0x2e, 0x6b, 0x2a, 255), (0x4f, 0xae, 0x4a, 255), (0x7c, 0xd0, 0x5a, 255)]
for x, y, c in ((9, 4, SOIL[1]), (10, 4, SOIL[2]), (11, 4, SOIL[1]), (12, 4, SOIL[0]), (10, 5, SOIL[0]), (11, 5, SOIL[1]),
                (8, 5, SOIL[0]), (12, 5, SOIL[0]), (9, 5, SOIL[2]),                      # soil clump on the blade
                (10, 3, LEAF[0]), (10, 2, LEAF[1]), (9, 1, LEAF[2]), (11, 1, LEAF[1]), (12, 0, LEAF[2]), (8, 0, LEAF[1])):  # sprout
    full.putpixel((x, y), c)
full.save(f'{OUT}/item/transplanting_spade_holding.png')
shears = Image.open(f'{EXC}/item/shears.png').convert('RGBA')
for x, y in ((7, 7), (8, 8), (7, 8), (8, 7)):
    if shears.getpixel((x, y))[3] > 0:
        shears.putpixel((x, y), BRASS[4])                      # brass pivot
shears.save(f'{OUT}/item/pruning_shears.png')
Image.open(f'{EXC}/item/wooden_hoe.png').convert('RGBA').save(f'{OUT}/item/timber_tiller.png')
Image.open(f'{EXC}/item/iron_hoe.png').convert('RGBA').save(f'{OUT}/item/iron_tiller.png')
Image.open(f'{EXC}/item/bundle.png').convert('RGBA').save(f'{OUT}/item/seed_satchel.png')
tint(f'{EXC}/item/clock_00.png', to_ramp(BRASS)).save(f'{OUT}/item/brass_dial.png')
dial_overlay = canvas()
small = tint(f'{EXC}/item/clock_00.png', to_ramp(BRASS)).resize((7, 7), Image.NEAREST)
dial_overlay.alpha_composite(small, (8, 8))
satchel_dial = Image.open(f'{EXC}/item/bundle.png').convert('RGBA')
satchel_dial.alpha_composite(dial_overlay)
satchel_dial.save(f'{OUT}/item/seed_satchel_dial.png')
print('tools ok')

# ---- Phase 2b: Botanist's Loupe: brass-ringed lens on a short handle (kitbashed from Excalibur spyglass colours)
loupe = canvas()
for x in range(16):
    for y in range(16):
        d = ((x - 9.5) ** 2 + (y - 6.5) ** 2) ** 0.5
        if d <= 3.6:
            loupe.putpixel((x, y), (190, 220, 225, 200) if (x + y) % 5 else (240, 250, 250, 230))   # lens glass
        elif d <= 5.0:
            loupe.putpixel((x, y), BRASS[4] if x + y < 16 else BRASS[1])                          # brass ring
for i in range(6):
    loupe.putpixel((5 - i // 2 + 1, 10 + i), WOOD[3]); loupe.putpixel((6 - i // 2 + 1, 10 + i), WOOD[1])  # handle
loupe.save(f'{OUT}/item/botanists_loupe.png')
print('loupe ok')

# ---- Phase 2b: Botanical Ledger: Excalibur writable book with brass corners and a green leaf tab
ledger = Image.open(f'{EXC}/item/writable_book.png').convert('RGBA')
for x, y in ((2, 2), (3, 2), (2, 3), (13, 13), (12, 13), (13, 12)):
    if ledger.getpixel((x, y))[3] > 0:
        ledger.putpixel((x, y), BRASS[4])
for x, y in ((11, 1), (12, 1), (12, 2)):
    ledger.putpixel((x, y), (0x4f, 0xae, 0x4a, 255))
ledger.save(f'{OUT}/item/botanical_ledger.png')
print('ledger ok')

# ---- Phase 2c: Assay Card: Excalibur paper punched like a brass-edged tabulator card, with stat-colour bars
card = Image.open(f'{EXC}/item/paper.png').convert('RGBA')
bars = [(0x3f, 0x6f, 0xd8), (0x4f, 0xae, 0x4a), (0xc0, 0x47, 0x3b), (0xd6, 0xb0, 0x55)]
for i, col in enumerate(bars):
    y = 5 + i * 2
    for x in range(5, 5 + 3 + (i * 2) % 5):
        if card.getpixel((x, y))[3] > 0:
            card.putpixel((x, y), col + (255,))
for y in range(4, 12, 2):
    if card.getpixel((11, y))[3] > 0:
        card.putpixel((11, y), BRASS[1])
card.save(f'{OUT}/item/assay_card.png')
print('assay card ok')
