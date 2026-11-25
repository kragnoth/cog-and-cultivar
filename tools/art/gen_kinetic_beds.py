#!/usr/bin/env python3
"""Planter and Trial Bed models in the Assayer's crafter style: a Mechanical Crafter body with the cog window in its
sides (the renderer spins a Create cogwheel there), topped by the bed's open soil box.

  * lower slab   0-6 px: crafter side (lower rows), dark log underneath;
  * cog window   6-10 px: the crafter side's window band, as thin outer/inner planes so the cog shows through;
  * soil box    10-16 px: 2 px casing walls round a soil floor at 13 px (PlanterBedBakedModel lays the chosen soil's
                          top face on it; trellises stand on the bed as before).

Planter Bed: andesite casing (side recoloured by gen_assayer.py); Trial Bed: brass casing and Create's crafter side.
Run after gen_assayer.py (it makes the andesite side texture).

Usage: python3 tools/art/gen_kinetic_beds.py
"""
import json
import os

ASSETS = 'src/main/resources/assets/cultivar'
BEDS = {
    'planter_bed': {'casing': 'create:block/andesite_casing', 'side': 'cultivar:block/assayer/andesite_side',
                    'floor': 'cultivar:block/bed_soil'},
    'trial_bed': {'casing': 'create:block/brass_casing', 'side': 'create:block/crafter_side',
                  'floor': 'cultivar:block/trial_soil'},
}


def face(tex, uv, cull=None):
    f = {"texture": tex, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f


def box(frm, to, faces):
    return {"from": frm, "to": to, "faces": faces}


def model():
    side_low = [0, 10, 16, 16]
    band = [0, 6, 16, 10]
    top = [0, 0, 16, 6]
    els = [
        # Lower slab.
        box([0, 0, 0], [16, 6, 16], {**{d: face("#side", side_low, d) for d in ("north", "south", "east", "west")},
                                    "down": face("#bottom", [0, 0, 16, 16], "down"), "up": face("#bottom", [0, 0, 16, 16])}),
        # Underside of the soil box, seen through the window.
        box([0, 10, 0], [16, 10, 16], {"down": face("#bottom", [0, 0, 16, 16])}),
    ]
    # Cog window: outer face and inner face of each side, the crafter's window band.
    for d, frm, to, inner in (("north", [0, 6, 0], [16, 10, 0], "south"), ("south", [0, 6, 16], [16, 10, 16], "north"),
                              ("west", [0, 6, 0], [0, 10, 16], "east"), ("east", [16, 6, 0], [16, 10, 16], "west")):
        els.append(box(frm, to, {d: face("#side", band, d), inner: face("#side", band)}))
    # Soil box walls (2 px), crafter-side outside, casing rim on top and inside.
    walls = [([0, 10, 0], [16, 16, 2]), ([0, 10, 14], [16, 16, 16]), ([0, 10, 2], [2, 16, 14]), ([14, 10, 2], [16, 16, 14])]
    for frm, to in walls:
        x0, _, z0 = frm
        x1, _, z1 = to
        faces = {"up": face("#casing", [x0, z0, x1, z1])}
        for d, edge, outer, span in (("north", z0, 0, (x0, x1)), ("south", z1, 16, (x0, x1)),
                                     ("west", x0, 0, (z0, z1)), ("east", x1, 16, (z0, z1))):
            faces[d] = face("#side", [span[0], top[1], span[1], top[3]], d) if edge == outer \
                else face("#casing", [span[0], 10, span[1], 16])
        els.append(box(frm, to, faces))
    # Soil floor.
    els.append(box([2, 10, 2], [14, 13, 14], {"up": face("#floor", [2, 2, 14, 14])}))
    return els


for bed, t in BEDS.items():
    out = {"parent": "block/block", "render_type": "minecraft:cutout",
           "textures": {"casing": t['casing'], "side": t['side'], "floor": t['floor'],
                        "bottom": "create:block/smooth_dark_log_top", "particle": t['casing']},
           "elements": model()}
    with open(f'{ASSETS}/models/block/{bed}.json', 'w') as f:
        json.dump(out, f, indent=1)
    with open(f'{ASSETS}/models/item/{bed}.json', 'w') as f:
        json.dump({"parent": f"cultivar:block/{bed}"}, f, indent=1)
print('kinetic bed models ok')
