#!/usr/bin/env python3
"""Shade and Lamp Cloche textures and models, kitbashed from the Excalibur resource pack (used with permission;
not MIT, see LICENSE).

  * shade_cloche.png   Excalibur tinted glass, smoked to a dark blue-grey (spec §4 blackened steel hue);
  * blackened_rim.png  Excalibur anvil on the blackened-steel ramp, for the shade cloche's rim and knob;
  * lamp_cloche.png    Excalibur orange stained glass warmed to amber, with brass leading;
  * lamp_core.png      Excalibur lit redstone lamp, warmed: the bulb hanging inside the lamp cloche.

Models reuse the Bell Cloche's geometry (models/block/bell_cloche.json). The lamp's glass and bulb faces carry
neoforge_data block/sky light 15 so they glow without emitting real light (real light would leak to neighbours).

Usage: python3 tools/art/gen_cloche_variants.py [<excalibur assets/minecraft/textures dir>]
"""
import copy
import json
import sys
from PIL import Image

EXC = sys.argv[1] if len(sys.argv) > 1 else '/home/claude/src/excalibur/pack/assets/minecraft/textures'
TEX = 'src/main/resources/assets/cultivar/textures/block'
MODELS = 'src/main/resources/assets/cultivar/models'


def hexes(*cs):
    return [tuple(int(c[i:i + 2], 16) for i in (0, 2, 4)) + (255,) for c in cs]


STEEL = hexes('202022', '252629', '2f2f32', '363639', '3f3e42', '484748')
BRASS = hexes('845125', 'ab7531', 'c4943d', 'd6b055', 'dfbe5e', 'f4df8d')
AMBER = hexes('8a4a12', 'b5661a', 'd48724', 'e8a636', 'f5c55a', 'fde39a')


def load(name):
    return Image.open(f'{EXC}/block/{name}.png').convert('RGBA').crop((0, 0, 16, 16))


def lum(c):
    return 0.2126 * c[0] + 0.7152 * c[1] + 0.0722 * c[2]


def ramp(im, palette, alpha=None):
    px = [(x, y) for y in range(16) for x in range(16) if im.getpixel((x, y))[3] > 0]
    ls = [lum(im.getpixel(p)) for p in px]
    lo, hi = min(ls), max(ls)
    out = Image.new('RGBA', (16, 16))
    for p, l in zip(px, ls):
        t = 0 if hi == lo else (l - lo) / (hi - lo)
        r, g, b, _ = palette[min(len(palette) - 1, int(t * len(palette)))]
        out.putpixel(p, (r, g, b, im.getpixel(p)[3] if alpha is None else alpha(im.getpixel(p)[3])))
    return out


def shade_glass():
    src = load('tinted_glass')
    out = Image.new('RGBA', (16, 16), (40, 42, 52, 175))  # smoked pane
    for x in range(16):
        for y in range(16):
            p = src.getpixel((x, y))
            if p[3] > 200:  # Excalibur's frame and streaks become steel leading
                out.putpixel((x, y), STEEL[3 + (x + y) % 3])
    for i in range(3, 13):  # one faint highlight streak
        out.putpixel((i, 15 - i), (120, 124, 140, 140))
    return out


def lamp_glass():
    src = load('orange_stained_glass')
    out = Image.new('RGBA', (16, 16), (232, 160, 60, 110))
    for x in range(16):
        for y in range(16):
            p = src.getpixel((x, y))
            if p[3] > 200:
                out.putpixel((x, y), BRASS[2 + (x * 3 + y) % 3])
    for i in range(16):
        for x, y in ((i, 15 - i),):
            if 2 <= y < 14:
                out.putpixel((x, y), (255, 236, 180, 130))
    return out


def model(glass, rim, emissive=False, bulb=None):
    base = json.load(open(f'{MODELS}/block/bell_cloche.json'))
    m = copy.deepcopy(base)
    m['textures'] = {'glass': glass, 'rim': rim, 'particle': glass}
    if bulb:
        m['textures']['bulb'] = bulb
        faces = {d: {'uv': [6, 6, 10, 10], 'texture': '#bulb'} for d in ('north', 'south', 'east', 'west', 'down')}
        m['elements'].append({'from': [6.5, 10, 6.5], 'to': [9.5, 13, 9.5], 'faces': faces})
        m['elements'].append({'from': [7.5, 13, 7.5], 'to': [8.5, 15, 8.5],  # chain to the top
                              'faces': {d: {'uv': [0, 0, 1, 2], 'texture': '#rim'} for d in ('north', 'south', 'east', 'west')}})
    if emissive:
        for e in m['elements']:
            for f in e['faces'].values():
                if f['texture'] in ('#glass', '#bulb'):
                    f['neoforge_data'] = {'block_light': 15, 'sky_light': 15, 'ambient_occlusion': False}
    return m


ramp(load('anvil'), STEEL).save(f'{TEX}/blackened_rim.png')
shade_glass().save(f'{TEX}/shade_cloche.png')
lamp_glass().save(f'{TEX}/lamp_cloche.png')
ramp(load('redstone_lamp_on'), AMBER).save(f'{TEX}/lamp_core.png')

for name, m in (('shade_cloche', model('cultivar:block/shade_cloche', 'cultivar:block/blackened_rim')),
                ('lamp_cloche', model('cultivar:block/lamp_cloche', 'cultivar:block/brass_knob', True, 'cultivar:block/lamp_core'))):
    with open(f'{MODELS}/block/{name}.json', 'w') as f:
        json.dump(m, f, indent=2)
        f.write('\n')
    with open(f'{MODELS}/item/{name}.json', 'w') as f:
        f.write('{ "parent": "cultivar:block/%s" }\n' % name)

# Trellis blockstates: one cloche part per type.
for trellis in ('timber_trellis', 'iron_trellis'):
    path = f'src/main/resources/assets/cultivar/blockstates/{trellis}.json'
    bs = json.load(open(path))
    parts = [p for p in bs['multipart'] if 'cloche' not in p.get('when', {})]
    for kind in ('bell', 'shade', 'lamp'):
        parts.append({'when': {'cloche': 'true', 'cloche_type': kind}, 'apply': {'model': f'cultivar:block/{kind}_cloche'}})
    bs['multipart'] = parts
    with open(path, 'w') as f:
        json.dump(bs, f, indent=2)
print('cloche variants ok')
