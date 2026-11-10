#!/usr/bin/env python3
"""Generates the trellis crop-stage models in assets/cultivar/models/crop/.

The models only crop and arrange vanilla texture names by UV, so whatever resource pack is loaded supplies
the pixels. Run from the repo root: python3 tools/art/gen_crop_models.py
"""
import json
import os

OUT = 'src/main/resources/assets/cultivar/models/crop'


def plane_pair(tex, y0, y1, uv, tint=None, half=7.2):
    """Two crossed vertical planes (like vanilla's cross model) spanning y0..y1 with the given UV."""
    elems = []
    for angle in (45, -45):
        face = {"uv": uv, "texture": tex}
        if tint is not None:
            face["tintindex"] = tint
        elems.append({
            "from": [8 - half, y0, 8], "to": [8 + half, y1, 8],
            "rotation": {"origin": [8, 8, 8], "axis": "y", "angle": angle, "rescale": True},
            "shade": False,
            "faces": {"north": dict(face), "south": dict(face)},
        })
    return elems


def rise(tex, rows, tint=None):
    """The bottom `rows` pixels of a cross texture, standing on the soil: a plant partway grown."""
    return plane_pair(tex, 0, rows, [0, 16 - rows, 16, 16], tint)


def hang(tex, rows, top=16):
    """The top `rows` pixels of a cross texture, hanging from the trellis bar."""
    return plane_pair(tex, top - rows, top, [0, 0, 16, rows])


def box(frm, to, side, top=None, side_uv=None, top_uv=None):
    faces = {}
    for d in ("north", "south", "east", "west"):
        faces[d] = {"texture": side, **({"uv": side_uv} if side_uv else {})}
    if top:
        for d in ("up", "down"):
            faces[d] = {"texture": top, **({"uv": top_uv} if top_uv else {})}
    return {"from": frm, "to": to, "faces": faces}


def model(name, textures, elements):
    textures = {"particle": next(iter(textures.values())), **textures}
    os.makedirs(OUT, exist_ok=True)
    with open(f'{OUT}/{name}.json', 'w') as f:
        json.dump({"ambientocclusion": False, "textures": textures, "elements": elements}, f, indent=2)
        f.write('\n')


# Melon / pumpkin: tinted stem grows in; at maturity a small fruit sits at its foot.
for kind in ('melon', 'pumpkin'):
    tex = {"stem": f"minecraft:block/{kind}_stem", "side": f"minecraft:block/{kind}_side", "top": f"minecraft:block/{kind}_top"}
    for i, rows in enumerate((6, 10, 16)):
        model(f'{kind}_stage{i}', {"stem": tex["stem"]}, rise("#stem", rows, tint=0))
    model(f'{kind}_stage3', tex, rise("#stem", 16, tint=0) + [box([8.5, 0, 8.5], [14.5, 6, 14.5], "#side", "#top")])

# Sugar cane: grass-tinted like vanilla, rising in four steps.
for i, rows in enumerate((5, 9, 13, 16)):
    model(f'sugar_cane_stage{i}', {"cane": "minecraft:block/sugar_cane"}, rise("#cane", rows, tint=1))

# Bamboo: the vanilla shoot, then a 2px stalk (vanilla's stalk UV) with leaves on top.
model('bamboo_stage0', {"shoot": "minecraft:block/bamboo_stage0"}, rise("#shoot", 16))
for i, (h, leaves) in enumerate(((10, 'small'), (14, 'small'), (16, 'large')), start=1):
    tex = {"stalk": "minecraft:block/bamboo_stalk", "leaves": f"minecraft:block/bamboo_{leaves}_leaves"}
    stalk = box([7, 0, 7], [9, h, 9], "#stalk", side_uv=[0, 16 - h, 2, 16])
    model(f'bamboo_stage{i}', tex, [stalk] + plane_pair("#leaves", h - 9, h, [0, 0, 16, 9]))

ctex = {"side": "minecraft:block/cactus_side", "top": "minecraft:block/cactus_top"}
# Cactus is 3 tall on a stacked trellis; each full segment repeats the last model, so it stays a plain column.
for i, h in enumerate((4, 8, 12, 16)):
    elems = [box([5, 0, 5], [11, h, 11], "#side", "#top", side_uv=[5, 16 - h, 11, 16], top_uv=[5, 5, 11, 11])]
    model(f'cactus_stage{i}', ctex, elems)

# Cocoa: vanilla's pod faces (from the vanilla UV layout), hanging under the top bar on its hook.
for i, (w, h, side_uv, top_uv) in enumerate(((4, 5, [11, 4, 15, 9], [0, 0, 4, 4]),
                                             (6, 7, [9, 4, 15, 11], [0, 0, 6, 6]),
                                             (8, 9, [7, 3, 15, 12], [0, 0, 7, 7]))):
    top = 13
    pod = box([8 - w / 2, top - h, 8 - w / 2], [8 + w / 2, top, 8 + w / 2], "#pod", "#pod", side_uv=side_uv, top_uv=top_uv)
    model(f'cocoa_stage{i}', {"pod": f"minecraft:block/cocoa_stage{i}"},
          [pod] + plane_pair("#pod", top, top + 3, [12, 0, 16, 4], half=2))

# Glow berries: cave vines hang from the top bar; ripe stage uses the lit texture.
for i, rows in enumerate((6, 11, 16)):
    model(f'glow_berries_stage{i}', {"vines": "minecraft:block/cave_vines"}, hang("#vines", rows))
model('glow_berries_stage3', {"vines": "minecraft:block/cave_vines_lit"}, hang("#vines", 16))

# Mushrooms: a half-size button, then the full cap.
for kind in ('brown_mushroom', 'red_mushroom'):
    tex = {"cap": f"minecraft:block/{kind}"}
    model(f'{kind}_stage0', tex, plane_pair("#cap", 0, 8, [0, 0, 16, 16], half=3.6))
    model(f'{kind}_stage1', tex, plane_pair("#cap", 0, 16, [0, 0, 16, 16]))

# Pitcher plant: the pod, then the item sprite growing in (item sprites share the block atlas).
model('pitcher_plant_stage0', {"pod": "minecraft:item/pitcher_pod"}, plane_pair("#pod", 0, 8, [0, 0, 16, 16], half=3.6))
model('pitcher_plant_stage1', {"plant": "minecraft:item/pitcher_plant"}, rise("#plant", 9))
model('pitcher_plant_stage2', {"plant": "minecraft:item/pitcher_plant"}, rise("#plant", 16))
print('crop models ok')
