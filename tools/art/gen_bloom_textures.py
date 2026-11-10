#!/usr/bin/env python3
"""Ore-bloom and stepping-stone bloom sprites, kitbashed from the Excalibur resource pack (used with permission;
not MIT, see LICENSE).

Each bloom takes its own Excalibur plant. Colours come from the item it drops (a brightness ramp sampled from that
item's Excalibur texture, or Create's for zinc):
  * petals mode: green foliage is kept; the petals take the product's colours;
  * whole mode:  the whole plant takes a palette, and the product shows as small nuggets/gems when ripe.
Stages: 0 and 1 are the lower 6 / 11 rows with petals still green buds, 2 is the full plant with muted petals,
3 is ripe (full colour plus a highlight).

Usage: python3 tools/art/gen_bloom_textures.py [<excalibur assets/minecraft/textures dir>]
"""
import colorsys
import glob
import io
import sys
import zipfile
from PIL import Image

EXC = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/src/excalibur/pack/assets/minecraft/textures'
OUT = 'src/main/resources/assets/cultivar/textures/block/{}_stage{}.png'


def hexes(*cs):
    return [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,) for c in cs]


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def ramp_of(im, n=6):
    im = im.convert('RGBA')
    px = sorted((im.getpixel((x, y))[:3] for y in range(im.height) for x in range(im.width) if im.getpixel((x, y))[3] > 200), key=lum)
    k = len(px)
    out = []
    for i in range(n):
        chunk = px[i * k // n:(i + 1) * k // n] or px[-1:]
        out.append(tuple(sum(c[j] for c in chunk) // len(chunk) for j in range(3)) + (255,))
    return out


def item_ramp(name):
    if name == 'create:raw_zinc':
        jar = glob.glob('/root/.gradle/caches/modules-2/files-2.1/com.simibubi.create/create-1.21.1/6.0.10-281/*/*.jar')[0]
        with zipfile.ZipFile(jar) as z:
            return ramp_of(Image.open(io.BytesIO(z.read('assets/create/textures/item/raw_zinc.png'))))
    return ramp_of(Image.open(f'{EXC}/item/{name}.png'))


def source(name):
    return Image.open(f'{EXC}/block/{name}.png').convert('RGBA').crop((0, 0, 16, 16))


def opaque(im):
    return [(x, y) for y in range(16) for x in range(16) if im.getpixel((x, y))[3] > 0]


def is_foliage(c):
    h, s, v = colorsys.rgb_to_hsv(c[0] / 255, c[1] / 255, c[2] / 255)
    return 0.12 <= h <= 0.48 and s > 0.18 and v < 0.85


def mapped(c, palette, lo, hi):
    t = 0 if hi == lo else (lum(c) - lo) / (hi - lo)
    return palette[min(len(palette) - 1, max(0, int(t * len(palette))))]


def darker(c, f):
    return (int(c[0] * f), int(c[1] * f), int(c[2] * f), c[3])


def scatter(im, every, offset):
    return [(x, y) for x, y in opaque(im) if (x * 7 + y * 13 + offset) % every == 0]


# --------------------------------------------------------------------------------------------------- stage builders

def petals(base, product, highlight=True, leaf_shade=1.0, glint=None):
    """Foliage kept (optionally shaded), petals recoloured. Returns [bud-only, muted, ripe] full-size images."""
    im = source(base)
    if leaf_shade != 1.0:
        for p in opaque(im):
            if is_foliage(im.getpixel(p)):
                im.putpixel(p, darker(im.getpixel(p), leaf_shade))
    pet = [(x, y) for x, y in opaque(im) if not is_foliage(im.getpixel((x, y)))]
    leaf = [im.getpixel(p) for p in opaque(im) if is_foliage(im.getpixel(p))] or [hexes('4d6b2a')[0]]
    leaf.sort(key=lum)
    bud_c = leaf[len(leaf) // 3]
    lums = [lum(im.getpixel(p)) for p in pet] or [0]
    lo, hi = min(lums), max(lums)
    bud, muted, ripe = im.copy(), im.copy(), im.copy()
    for p in pet:
        c = mapped(im.getpixel(p), product, lo, hi)
        bud.putpixel(p, bud_c)
        muted.putpixel(p, darker(c, 0.62))
        ripe.putpixel(p, c)
    if highlight and pet:
        top = min(pet, key=lambda p: (p[1], p[0]))
        ripe.putpixel(top, glint or product[-1])
    return bud, muted, ripe


def whole(base, palette, product, every=13, offset=0, fruit_on=None):
    """Whole plant recoloured; the product shows as two-tone nuggets on scattered pixels when ripe."""
    im = source(base)
    lums = [lum(im.getpixel(p)) for p in opaque(im)]
    lo, hi = min(lums), max(lums)
    body = Image.new('RGBA', (16, 16))
    for p in opaque(im):
        body.putpixel(p, mapped(im.getpixel(p), palette, lo, hi))
    ripe = body.copy()
    spots = fruit_on(body) if fruit_on else scatter(body, every, offset)
    for x, y in spots:
        ripe.putpixel((x, y), product[-2])
        if y + 1 < 16 and body.getpixel((x, y + 1))[3] > 0:
            ripe.putpixel((x, y + 1), product[2])
    if spots:
        x, y = spots[0]
        ripe.putpixel((x, y), product[-1])
    return body, body, ripe


def tips(im):
    out = []
    for x in range(16):
        ys = [y for y in range(16) if im.getpixel((x, y))[3] > 0]
        if ys:
            out.append((x, ys[0]))
    return out


def every_other_tip(im):
    return tips(im)[::3]


# --------------------------------------------------------------------------------------------------- the blooms

BONE = hexes('8c8676', 'a8a290', 'c4bea9', 'd9d3bf', 'e9e4d2', 'f7f4e8')
INK = hexes('0f0f16', '1a1a26', '262838', '343750', '474b68', '61678a')
CLAY = hexes('5d6470', '737b88', '8a92a0', 'a0a8b5', 'b5bcc8', 'cad0da')
COAL = hexes('141414', '1f1f21', '2c2c2f', '3b3b40', '4d4d53', '646469')
FLINT = hexes('26262a', '35353a', '47474d', '5b5b62', '727279', '8c8c93')
VERDIGRIS = hexes('1d3b33', '285246', '35695a', '43826f', '5a9e88', '7cbca5')
FERN_GREEN = hexes('1f3214', '2a441b', '375723', '466c2c', '588337', '6e9b45')
SLIME = hexes('4a8c32', '64ab42', '7fc552', '9ada66', 'b4ea7e', 'd2f7a6')
RUST = hexes('4a1f10', '6e2f16', '8f421c', 'ad5a26', 'c77634', 'dd9548')
BRIAR = hexes('1c120c', '2a1a10', '3b2516', '4f321c', '664225', '7e5430')

BLOOMS = {
    # stepping stones
    'bonecap': lambda: whole('brown_mushroom', BONE, item_ramp('bone_meal'), every=29),
    'inkcap': lambda: whole('red_mushroom1', INK, item_ramp('ink_sac'), every=11, offset=2),
    'madder_root': lambda: petals('rose_bush_top', item_ramp('red_dye')),
    'woad': lambda: petals('lilac_top', item_ramp('blue_dye')),
    'clayreed': lambda: whole('kelp_plant', CLAY, item_ramp('clay_ball'), every=9, offset=1),
    'silkweed': lambda: petals('azure_bluet', item_ramp('string')),
    'glowcap': lambda: petals('closed_eyeblossom', item_ramp('glowstone_dust')),
    'coalroot': lambda: whole('bush', COAL, item_ramp('coal'), every=11, offset=3),
    'flintwort': lambda: whole('tall_grass_top', FLINT, item_ramp('flint'), fruit_on=every_other_tip),
    'slimecap': lambda: petals('sweet_berry_bush_stage3', SLIME, leaf_shade=0.5),
    'brimstone_puff': lambda: petals('firefly_bush', item_ramp('gunpowder')),
    'rustwort': lambda: petals('pink_tulip', RUST, glint=item_ramp('iron_nugget')[-1]),
    'shardbloom': lambda: petals('open_eyeblossom', item_ramp('amethyst_shard')),
    # ores and gems
    'ferrous_bloom': lambda: petals('oxeye_daisy', item_ramp('raw_iron')),
    'redstone_fern': lambda: whole('fern3', FERN_GREEN, item_ramp('redstone'), fruit_on=every_other_tip),
    'quartz_lily': lambda: petals('white_tulip', item_ramp('quartz')),
    'verdigris_vine': lambda: whole('twisting_vines_plant', VERDIGRIS, item_ramp('raw_copper'), every=9, offset=4),
    'lazuli_bell': lambda: petals('cornflower', item_ramp('lapis_lazuli')),
    'brassleaf': lambda: petals('dandelion', item_ramp('create:raw_zinc')),
    'aurum_thistle': lambda: petals('allium', item_ramp('raw_gold')),
    'emerald_fern': lambda: whole('large_fern_top', FERN_GREEN, item_ramp('emerald'), every=12, offset=5),
    'diamond_briar': lambda: whole('deadbush1', BRIAR, item_ramp('diamond'), every=9, offset=2),
}


def rows(im, n):
    out = Image.new('RGBA', (16, 16))
    out.paste(im.crop((0, 16 - n, 16, 16)), (0, 16 - n))
    return out


if __name__ == '__main__':
    for name, make in BLOOMS.items():
        bud, muted, ripe = make()
        for stage, img in enumerate((rows(bud, 6), rows(bud, 11), muted, ripe)):
            img.save(OUT.format(name, stage))
    print('bloom textures ok')
