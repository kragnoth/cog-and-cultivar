#!/usr/bin/env python3
"""Assayer art: Create's Mechanical Crafter laid on its back, re-cased per tier, with farmland on top.

Reads Create's own crafter geometry and textures straight from the Create jar (nothing copied by hand), then writes:

  * textures/block/assayer/andesite_side.png    crafter side, brass parts recoloured to Create's andesite casing;
  * textures/block/assayer/chromatic_side.png   crafter side on the chromatic ramp;
  * textures/block/assayer/chromatic_casing.png Create's brass casing on the chromatic ramp (iridescent violet with
                                                a cyan glint), Create having no chromatic casing of its own;
  * models/block/assayer/<tier>.json + models/item/<id>.json, blockstates/<id>.json.

Geometry is the crafter's up-facing item model: the valve frame and opening on top (the opening shows moist farmland
instead of the crafting grid), the side windows where the cogwheel shows (the block entity renderer spins a Create
cogwheel there). Brass adds a small trellis on the farmland, chromatic a trellis under a glass cloche.

The brass tier uses Create's textures unchanged; andesite and chromatic swap only the casing parts.

Usage: python3 tools/art/gen_assayer.py [<create jar>]
"""
import colorsys
import copy
import glob
import io
import json
import os
import sys
import zipfile
from PIL import Image

JAR = sys.argv[1] if len(sys.argv) > 1 else sorted(glob.glob(os.path.expanduser(
    '~/.gradle/caches/modules-2/files-2.1/com.simibubi.create/create-1.21.1/6.0.10-281/*/create-1.21.1-6.0.10-281.jar')))[0]
ASSETS = 'src/main/resources/assets/cultivar'
TEX = f'{ASSETS}/textures/block/assayer'

jar = zipfile.ZipFile(JAR)


def create_tex(name):
    return Image.open(io.BytesIO(jar.read(f'assets/create/textures/block/{name}.png'))).convert('RGBA')


def hexes(*cs):
    return [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) for c in cs]


# Chromatic compound: deep violet through magenta-lilac to a pale cyan glint, dark to light.
CHROMATIC = hexes('24123a', '3d1f63', '5b2f8e', '7c47b4', 'a06fd2', 'c6a2ea', 'cfeaf5')


def is_brass(c):
    """Create's brass: warm, saturated (the casing frame and plates), not the dark wood or the transparent window."""
    r, g, b, a = c
    if a == 0:
        return False
    h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
    return 0.05 < h < 0.17 and s > 0.35 and v > 0.45


def brass_or_rim(img):
    """Brass pixels, plus anything on the 1 px outer rim (the crafter's dark corner rivets are brass too)."""
    return lambda c, x, y: is_brass(c) or (c[3] > 0 and (x in (0, 15) or y in (0, 15)))


def ramp_of(img, pick):
    """Distinct colours of the picked pixels, darkest first."""
    cols = sorted({img.getpixel((x, y))[:3] for x in range(16) for y in range(16) if pick(img.getpixel((x, y)), x, y)},
                  key=lambda c: 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2])
    return cols


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def recolour(img, ramp, pick=None, rest=None):
    """Maps picked pixels by brightness rank onto `ramp`; `rest` (optional) recolours everything else that's opaque."""
    pick = pick or brass_or_rim(img)
    src = ramp_of(img, pick)
    lo, hi = lum(src[0]), lum(src[-1])
    out = img.copy()
    for x in range(16):
        for y in range(16):
            c = img.getpixel((x, y))
            if pick(c, x, y):
                t = 0 if hi == lo else (lum(c) - lo) / (hi - lo)
                r = ramp[min(len(ramp) - 1, int(t * (len(ramp) - 1) + 0.5))]
                out.putpixel((x, y), r + (c[3],))
            elif rest and c[3] > 0:
                out.putpixel((x, y), rest(c))
    return out


def andesite_ramp():
    """The andesite casing's frame colours (its grey border), darkest first, as the casing ramp."""
    cas = create_tex('andesite_casing')
    border = [cas.getpixel((x, y))[:3] for x in range(16) for y in range(16) if x in (0, 1, 14, 15) or y in (0, 1, 14, 15)]
    return sorted(set(border), key=lum)


def night_wood(c):
    """Chromatic casing's planks: the brass casing's wood pushed toward a dark blue-violet."""
    h, s, v = colorsys.rgb_to_hsv(*(k / 255 for k in c[:3]))
    r, g, b = colorsys.hsv_to_rgb(0.74, min(1, s * 0.6 + 0.15), v * 0.8)
    return (int(r * 255), int(g * 255), int(b * 255), c[3])


os.makedirs(TEX, exist_ok=True)
side = create_tex('crafter_side')
recolour(side, andesite_ramp()).save(f'{TEX}/andesite_side.png')
recolour(side, CHROMATIC, rest=night_wood).save(f'{TEX}/chromatic_side.png')
casing = recolour(create_tex('brass_casing'), CHROMATIC, rest=night_wood)
casing.putpixel((2, 2), CHROMATIC[-1] + (255,))  # a glint in the top-left corner, like light catching the alloy
casing.save(f'{TEX}/chromatic_casing.png')

# ---------------------------------------------------------------- models

crafter_item = json.loads(jar.read('assets/create/models/block/mechanical_crafter/item.json'))
GEAR = ('Gear', 'Gear2', 'Gear3', 'Gear4', 'GearCaseInner', 'GearCaseOuter')

TIERS = {
    'andesite': {'id': 'assaying_engine', 'casing': 'create:block/andesite_casing', 'side': 'cultivar:block/assayer/andesite_side'},
    'brass': {'id': 'brass_assaying_engine', 'casing': 'create:block/brass_casing', 'side': 'create:block/crafter_side'},
    'chromatic': {'id': 'chromatic_assaying_engine', 'casing': 'cultivar:block/assayer/chromatic_casing',
                  'side': 'cultivar:block/assayer/chromatic_side'},
}


def face(tex, uv):
    return {"texture": tex, "uv": uv}


def trellis():
    """A small trellis standing on the farmland: four lattice panes around the 6x6 opening."""
    full = [0, 0, 16, 16]
    out = []
    for x in (5.5, 10.5):
        out.append({"name": "trellis", "from": [x, 16.5, 5.5], "to": [x, 22, 10.5],
                    "faces": {"east": face("#trellis", full), "west": face("#trellis", full)}})
    for z in (5.5, 10.5):
        out.append({"name": "trellis", "from": [5.5, 16.5, z], "to": [10.5, 22, z],
                    "faces": {"north": face("#trellis", full), "south": face("#trellis", full)}})
    return out


def cloche():
    """A glass cloche over the trellis, with a brass knob."""
    glass = {d: face("#cloche", [2, 1, 14, 15]) for d in ("north", "south", "east", "west")}
    glass["up"] = face("#cloche", [2, 2, 14, 14])
    return [{"name": "cloche", "from": [4.5, 16.5, 4.5], "to": [11.5, 23, 11.5], "faces": glass},
            {"name": "knob", "from": [7.5, 23, 7.5], "to": [8.5, 24, 8.5],
             "faces": {d: face("#knob", [7, 7, 9, 9]) for d in ("north", "south", "east", "west", "up")}}]


def model(tier, item):
    t = TIERS[tier]
    elems = []
    for e in copy.deepcopy(crafter_item['elements']):
        name = e.get('name', '')
        if name == 'valve_lid' or (not item and name in GEAR):
            continue  # the lid is the crafter's output flap; the gear is drawn spinning by the renderer
        if name == 'opening':  # farmland where the crafting grid was
            e['faces'] = {"up": face("#soil", [5, 5, 11, 11])}
        if name == 'Bottom' and not item:
            e['faces']['down'] = face("#8", [0, 0, 16, 16])
        elems.append(e)
    tex = {"3": "create:block/crafter_thingies", "4": t['side'], "5": t['casing'], "7": t['casing'],
           "8": "create:block/smooth_dark_log_top", "9": "create:block/cogwheel", "soil": "minecraft:block/farmland_moist",
           "particle": t['casing']}
    if tier in ('brass', 'chromatic'):
        tex["trellis"] = "cultivar:block/timber_trellis"
        elems += trellis()
    if tier == 'chromatic':
        tex["cloche"] = "cultivar:block/bell_cloche"
        tex["knob"] = "cultivar:block/brass_knob"
        elems += cloche()
    out = {"parent": "block/block", "render_type": "minecraft:translucent" if tier == 'chromatic' else "minecraft:cutout",
           "textures": tex, "elements": elems}
    if item:
        out["display"] = crafter_item.get("display", {})
    return out


os.makedirs(f'{ASSETS}/models/block/assayer', exist_ok=True)
for tier, t in TIERS.items():
    with open(f'{ASSETS}/models/block/assayer/{tier}.json', 'w') as f:
        json.dump(model(tier, False), f, indent=1)
    with open(f'{ASSETS}/models/item/{t["id"]}.json', 'w') as f:
        json.dump(model(tier, True), f, indent=1)
    with open(f'{ASSETS}/blockstates/{t["id"]}.json', 'w') as f:
        json.dump({"variants": {"": {"model": f"cultivar:block/assayer/{tier}"}}}, f, indent=1)
print('assayer art ok')
