#!/usr/bin/env python3
"""Recolours the Rust-rot sprites into the habitat blights (same shapes, so the family reads as one).

Run from the repo root: python3 tools/art/gen_blight_textures.py
"""
import colorsys
from PIL import Image

SRC = 'src/main/resources/assets/cultivar/textures/block/rust_rot_stage{}.png'
OUT = 'src/main/resources/assets/cultivar/textures/block/{}_stage{}.png'

# name: (hue 0-1 or None to keep, saturation x, value x)
VARIANTS = {
    'bog_rot': (0.22, 0.9, 0.75),      # slimy olive green
    'dry_rot': (0.10, 0.35, 1.15),     # bleached tan
    'ash_blight': (None, 0.0, 0.85),   # grey ash
    'sour_moss': (0.17, 1.1, 1.0),     # acid yellow-green
    'mildew': (0.55, 0.12, 1.35),      # chalky blue-white
    'sun_scald': (0.12, 1.2, 1.25),    # scorched orange-yellow
    'leach_creep': (0.60, 0.30, 0.80),  # washed-out slate
    'glut_canker': (0.88, 0.8, 0.8),   # bruised purple-red
}

for name, (hue, sat, val) in VARIANTS.items():
    for stage in range(4):
        img = Image.open(SRC.format(stage)).convert('RGBA')
        px = img.load()
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = px[x, y]
                if a == 0:
                    continue
                h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                h = h if hue is None else (hue + (h - 0.06) * 0.5) % 1.0  # keep the original's hue spread, re-centred
                s, v = min(1.0, s * sat), min(1.0, v * val)
                nr, ng, nb = colorsys.hsv_to_rgb(h, s, v)
                px[x, y] = (round(nr * 255), round(ng * 255), round(nb * 255), a)
        img.save(OUT.format(name, stage))
print('blight textures ok')
