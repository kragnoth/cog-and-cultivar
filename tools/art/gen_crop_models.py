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
    # Ripe: a near-full-size fruit beside the stem (8.5 px; the plant's random_rotation turns it to a random side).
    model(f'{kind}_stage3', tex, rise("#stem", 16, tint=0) + [box([7, 0, 7], [15.5, 8.5, 15.5], "#side", "#top")])

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
# 12 px wide (vanilla is 14) so it reads as a cactus but still sits inside the trellis posts; the full texture width is
# squeezed in so the spines at its edges stay.
for i, h in enumerate((4, 8, 12, 16)):
    elems = [box([2, 0, 2], [14, h, 14], "#side", "#top", side_uv=[0, 16 - h, 16, 16], top_uv=[0, 0, 16, 16])]
    model(f'cactus_stage{i}', ctex, elems)

# Cocoa: vanilla's pod faces (from the vanilla UV layout), hanging under the top bar on its hook.
COCOA = ((4, 5, [11, 4, 15, 9], [0, 0, 4, 4]), (6, 7, [9, 4, 15, 11], [0, 0, 6, 6]),
         (8, 9, [8, 4, 16, 13], [0, 0, 8, 8]))  # vanilla cocoa pod sizes and UVs per stage


def cocoa_stem(tex, top, wood, axis="z", toward=1):
    """Vanilla's 4 px pod stem: a vertical plane from a pod's top to the wood face at `wood` along `axis`.
    `toward` is +1 when the wood lies in the + direction. UVs follow vanilla (u=16 at the wood end)."""
    near, far = [16, 0, 12, 4], [12, 0, 16, 4]
    lo, hi = (wood - 4, wood) if toward > 0 else (wood, wood + 4)
    if axis == "z":  # plane at x=8, wood to the south (+z) as in vanilla
        frm, to = [8, top, lo], [8, top + 4, hi]
        faces = {"west": far if toward > 0 else near, "east": near if toward > 0 else far}
    else:  # plane at z=centre, wood along x
        frm, to = [lo, top, 12], [hi, top + 4, 12]
        faces = {"north": near if toward > 0 else far, "south": far if toward > 0 else near}
    return {"from": frm, "to": to, "faces": {d: {"texture": tex, "uv": uv} for d, uv in faces.items()}}


for i, (w, h, side_uv, top_uv) in enumerate(COCOA):
    # Like vanilla, the pod hangs off wood: a thin jungle-log post at the back, the pod against its front face and
    # vanilla's stem plane running from the pod's top into the post.
    top, back = 13, 11
    post = box([6, 0, back], [10, 16, back + 4], "#log", "#log_top", side_uv=[6, 0, 10, 16], top_uv=[6, 6, 10, 10])
    pod = box([8 - w / 2, top - h, back - w], [8 + w / 2, top, back], "#pod", "#pod", side_uv=side_uv, top_uv=top_uv)
    model(f'cocoa_stage{i}', {"pod": f"minecraft:block/cocoa_stage{i}", "log": "minecraft:block/jungle_log",
                              "log_top": "minecraft:block/jungle_log_top"}, [post, pod, cocoa_stem("#pod", top, back)])

# On a trellis, cocoa grows as the real tree does: a short jungle-wood trunk standing against the back lattice, pods
# straight off the bark (cauliflory) at two heights, and a tuft of leaves at the crown. The lower pod is this stage's;
# the upper one, on the trunk's west face, is a stage behind (none at stage 0).
for i, (w, h, side_uv, top_uv) in enumerate(COCOA):
    tex = {"pod": f"minecraft:block/cocoa_stage{i}", "log": "minecraft:block/jungle_log",
           "log_top": "minecraft:block/jungle_log_top", "leaves": "minecraft:block/jungle_leaves"}
    trunk = [box([6, 0, 10], [10, 10, 14], "#log", "#log_top", side_uv=[6, 6, 10, 16], top_uv=[6, 6, 10, 10]),
             box([6.5, 10, 10.5], [9.5, 15, 13.5], "#log", "#log_top", side_uv=[6, 0, 9, 5], top_uv=[6, 6, 9, 9])]
    low_top = 9
    elems = trunk + [box([8 - w / 2, low_top - h, 10 - w], [8 + w / 2, low_top, 10], "#pod", "#pod", side_uv=side_uv, top_uv=top_uv),
                     cocoa_stem("#pod", low_top, 10)]
    if i > 0:
        tex["pod2"] = f"minecraft:block/cocoa_stage{i - 1}"
        w2, h2, side2, top2 = COCOA[i - 1]
        up_top = 13
        elems += [box([6.5 - w2, up_top - h2, 12 - w2 / 2], [6.5, up_top, 12 + w2 / 2], "#pod2", "#pod2", side_uv=side2, top_uv=top2),
                  cocoa_stem("#pod2", up_top, 6.5, axis="x", toward=1)]
    crown = (3, 13) if i == 0 else (5, 11)
    elems += plane_pair("#leaves", crown[1], 16, [0, 0, 16, 16 - crown[1]], tint=1, half=crown[0])
    model(f'cocoa_trellis_stage{i}', tex, elems)

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
# Ore-blooms and stepping-stone blooms have their own kitbashed sprites: tools/art/gen_bloom_textures.py.

print('crop models ok')
