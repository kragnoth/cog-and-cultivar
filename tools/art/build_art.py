#!/usr/bin/env python3
"""Builds all kitbashed textures twice from the same art scripts:

  * vanilla style (the default, shipped in assets/cultivar/textures): every script is run against the vanilla 1.21.1
    client textures. Excalibur-only sprites are mapped to close vanilla stand-ins (ALIASES), hand-picked palettes
    are lifted toward vanilla's brighter look (GRADE), and flat vanilla glass frames are redrawn as brass (POLISH);
  * Excalibur style (optional): the same scripts run against the Excalibur resource pack (used with permission;
    not MIT, see README). Only textures that differ from the vanilla build go into the built-in resource pack
    resourcepacks/excalibur_style, which players pick from the Resource Packs screen.

Usage (from the repo root):
  python3 tools/art/build_art.py [--vanilla <minecraft 1.21.1 client jar>] [--excalibur <excalibur textures dir>]
Without --excalibur only the vanilla (default) art is rebuilt.
"""
import colorsys
import glob
import os
import runpy
import shutil
import sys
import tempfile
import zipfile
from PIL import Image

REPO = os.getcwd()
ASSETS = 'src/main/resources/assets/cultivar'
PACK = 'src/main/resources/resourcepacks/excalibur_style'
DEFAULT_JAR = os.path.expanduser('~/.gradle/caches/neoformruntime/artifacts/minecraft_1.21.1_client.jar')
SCRIPTS = ['gen_phase1_textures', 'gen_art_pass', 'gen_bloom_textures', 'gen_blight_textures',
           'gen_conditioner_textures', 'gen_cloche_variants']

# Excalibur-only sprite → vanilla 1.21.1 stand-in (similar silhouette; the scripts recolour them anyway).
ALIASES = {
    'block/red_mushroom1': 'block/brown_mushroom',  # vanilla's red mushroom is too small to read once inked
    'block/seagrass1': 'block/seagrass',
    'block/fern3': 'block/fern',
    'block/deadbush1': 'block/dead_bush',
    'block/dead_bush0': 'block/dead_bush',
    'block/bush': 'block/sweet_berry_bush_stage2',
    'block/tall_dry_grass': 'block/short_grass',
    'block/firefly_bush': 'block/lily_of_the_valley',
    'block/closed_eyeblossom': 'block/torchflower',
    'block/open_eyeblossom': 'block/blue_orchid',
}

# Outputs drawn from hand-picked palettes, lifted for vanilla: (value gain, saturation gain, contrast).
GRADE = {
    'block/*_rot_stage*': (1.18, 1.15, 1.10), 'block/ash_blight_stage*': (1.15, 1.0, 1.10),
    'block/sour_moss_stage*': (1.12, 1.15, 1.10), 'block/mildew_stage*': (0.95, 1.0, 1.0),
    'block/sun_scald_stage*': (1.12, 1.10, 1.10), 'block/leach_creep_stage*': (1.18, 1.10, 1.10),
    'block/glut_canker_stage*': (1.15, 1.15, 1.10), 'block/blackened_rim': (1.45, 1.0, 1.15),
    'block/shade_cloche': (1.30, 1.0, 1.0), 'block/iron_trellis': (1.10, 1.0, 1.10), 'block/iron_rod': (1.10, 1.0, 1.10),
    'block/coalroot_stage*': (1.25, 1.0, 1.15), 'block/inkcap_stage*': (1.25, 1.10, 1.15),
    'block/diamond_briar_stage*': (1.30, 1.10, 1.10), 'block/emerald_fern_stage*': (1.15, 1.15, 1.05),
    'block/redstone_fern_stage*': (1.15, 1.15, 1.05), 'block/flintwort_stage*': (1.20, 1.0, 1.10),
}


BRASS = [(0x84, 0x51, 0x25, 255), (0xab, 0x75, 0x31, 255), (0xc4, 0x94, 0x3d, 255),
         (0xd6, 0xb0, 0x55, 255), (0xdf, 0xbe, 0x5e, 255), (0xf4, 0xdf, 0x8d, 255)]


def leaded(path, tint, mullions=(7, 8)):
    """Vanilla glass has a plain flat frame, so the brass came out one flat tone: redraw it as shaded leading
    (lit top/left, shadowed bottom/right) with a centre cross, over a light tint so the pane reads as glass."""
    im = Image.new('RGBA', (16, 16), tint)
    for i in range(16):
        im.putpixel((i, 0), BRASS[4]); im.putpixel((0, i), BRASS[4])
        im.putpixel((i, 15), BRASS[1]); im.putpixel((15, i), BRASS[1])
        for m in mullions:
            im.putpixel((m, i), BRASS[3] if m == mullions[0] else BRASS[2])
            im.putpixel((i, m), BRASS[3] if m == mullions[0] else BRASS[2])
    for x, y in ((0, 0), (15, 0), (0, 15), (15, 15), (7, 7), (8, 8)):
        im.putpixel((x, y), BRASS[0])  # rivets
    for i in range(2, 6):  # glints, one per upper pane
        im.putpixel((i, 7 - i), (255, 255, 255, 150)); im.putpixel((i + 8, 7 - i), (255, 255, 255, 120))
    im.save(path)


POLISH = {
    'block/assay_glass': lambda p: leaded(p, (210, 228, 235, 60)),
    'block/lamp_cloche': lambda p: leaded(p, (240, 170, 70, 120)),
}


def grade(path, gain, sat, contrast):
    im = Image.open(path).convert('RGBA')
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = im.getpixel((x, y))
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            v = min(1.0, max(0.0, (v * gain - 0.5) * contrast + 0.5))
            s = min(1.0, s * sat)
            r, g, b = colorsys.hsv_to_rgb(h, s, v)
            im.putpixel((x, y), (round(r * 255), round(g * 255), round(b * 255), a))
    im.save(path)


def run_scripts(textures_src, vanilla):
    """Runs every art script in a scratch copy of the assets; returns {relative texture path: png bytes} written."""
    tmp = tempfile.mkdtemp(prefix='cc_art_')
    work = os.path.join(tmp, 'work')
    for sub in ('textures', 'models', 'blockstates'):
        shutil.copytree(f'{REPO}/{ASSETS}/{sub}', f'{work}/{ASSETS}/{sub}')
    shutil.copytree(f'{REPO}/tools', f'{work}/tools')
    tex = f'{work}/{ASSETS}/textures'
    stamp = {p: os.stat(p).st_mtime_ns for p in glob.glob(f'{tex}/**/*.png', recursive=True)}
    cwd, argv = os.getcwd(), sys.argv
    os.chdir(work)
    try:
        for name in SCRIPTS:
            sys.argv = [name, textures_src] + ([f'{ASSETS}/textures'] if name == 'gen_phase1_textures' else [])
            runpy.run_path(f'tools/art/{name}.py', run_name='__main__')
        if vanilla:
            for pattern, (gain, sat, contrast) in GRADE.items():
                for p in glob.glob(f'{ASSETS}/textures/{pattern}.png'):
                    grade(p, gain, sat, contrast)
            for name, fix in POLISH.items():
                fix(f'{ASSETS}/textures/{name}.png')
    finally:
        os.chdir(cwd)
        sys.argv = argv
    out = {}
    for p in glob.glob(f'{tex}/**/*.png', recursive=True):
        if stamp.get(p) != os.stat(p).st_mtime_ns:
            out[os.path.relpath(p, tex)] = open(p, 'rb').read()
    shutil.rmtree(tmp)
    return out


def vanilla_textures(jar):
    tmp = tempfile.mkdtemp(prefix='cc_vanilla_')
    with zipfile.ZipFile(jar) as z:
        for n in z.namelist():
            if n.startswith('assets/minecraft/textures/') and n.endswith('.png'):
                dst = os.path.join(tmp, n[len('assets/minecraft/textures/'):])
                os.makedirs(os.path.dirname(dst), exist_ok=True)
                with open(dst, 'wb') as f:
                    f.write(z.read(n))
    for alias, real in ALIASES.items():
        shutil.copy(f'{tmp}/{real}.png', f'{tmp}/{alias}.png')
    return tmp


def write(root, files):
    for rel, data in files.items():
        dst = f'{root}/{rel}'
        os.makedirs(os.path.dirname(dst), exist_ok=True)
        with open(dst, 'wb') as f:
            f.write(data)


def main():
    args = dict(zip(sys.argv[1::2], sys.argv[2::2]))
    src = vanilla_textures(args.get('--vanilla', DEFAULT_JAR))
    vanilla = run_scripts(src, vanilla=True)
    shutil.rmtree(src)
    write(f'{ASSETS}/textures', vanilla)
    print(f'vanilla style: {len(vanilla)} textures -> {ASSETS}/textures')
    if '--excalibur' in args:
        exc = run_scripts(args['--excalibur'], vanilla=False)
        differs = {rel: data for rel, data in exc.items() if vanilla.get(rel) != data}
        shutil.rmtree(f'{PACK}/assets', ignore_errors=True)
        write(f'{PACK}/assets/cultivar/textures', differs)
        with open(f'{PACK}/pack.mcmeta', 'w') as f:
            f.write('{\n  "pack": {\n    "pack_format": 34,\n    "description": '
                    '"Cog & Cultivar art kitbashed from Excalibur. Best with the Excalibur resource pack."\n  }\n}\n')
        icon = Image.open(f'{PACK}/assets/cultivar/textures/item/seed_satchel_dial.png').convert('RGBA')
        icon.resize((128, 128), Image.NEAREST).save(f'{PACK}/pack.png')
        print(f'excalibur style: {len(differs)} textures -> {PACK}')


main()
