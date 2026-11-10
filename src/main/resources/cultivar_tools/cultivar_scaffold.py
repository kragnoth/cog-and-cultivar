#!/usr/bin/env python3
"""Cog & Cultivar pack-maker scaffold. Python 3.9+, standard library only.

Workflow (see docs/PACKMAKERS.md in the Cog & Cultivar repo):
  1. In game (op):            /cultivar export
  2. Scaffold a datapack:     python3 cultivar_scaffold.py scaffold crops.json --assets <mods folder> --assets <minecraft client jar>
  3. Fill in mutation_plan.csv (parents, chance, habitat), then:
                              python3 cultivar_scaffold.py mutations mutation_plan.csv
  4. Sanity-check the tree:   python3 cultivar_scaffold.py check crops.json --pack cultivar_scaffold
  5. Copy cultivar_scaffold/ into <world>/datapacks/ (or a global datapack mod) and /reload.

Everything generated is a starting point: plain JSON meant to be edited by hand.
"""
import argparse
import csv
import json
import os
import sys
import zipfile
from collections import defaultdict

PACK_FORMAT = 48  # Minecraft 1.21.1 data packs

# ----------------------------------------------------------------------------- growth requirement presets
# Chosen from the soils a crop accepts. Values match the condition types in the mod (cultivar:light, humidity,
# acidity, nutrients). Tweak freely; tolerance widens with the plant's Strength stat.
PRESETS = {
    "farmland": [
        {"type": "cultivar:light", "min": 10, "max": 15, "tolerance": 0.5},
        {"type": "cultivar:humidity", "value": "wet", "tolerance": 0.15},
        {"type": "cultivar:nutrients", "value": "high", "comparison": "at_least", "tolerance": 0.1},
    ],
    "nether": [
        {"type": "cultivar:humidity", "value": "dry", "tolerance": 0.2},
        {"type": "cultivar:acidity", "value": "slightly_alkaline", "tolerance": 0.2},
    ],
    "dark": [
        {"type": "cultivar:light", "min": 0, "max": 7, "tolerance": 0.5},
        {"type": "cultivar:humidity", "value": "damp", "comparison": "at_least", "tolerance": 0.15},
        {"type": "cultivar:nutrients", "value": "medium", "comparison": "at_least", "tolerance": 0.1},
    ],
    "arid": [
        {"type": "cultivar:light", "min": 12, "max": 15, "tolerance": 0.5},
        {"type": "cultivar:humidity", "value": "dry", "comparison": "at_most", "tolerance": 0.15},
    ],
    "wild": [
        {"type": "cultivar:light", "min": 8, "max": 15, "tolerance": 0.5},
        {"type": "cultivar:humidity", "value": "damp", "comparison": "at_least", "tolerance": 0.15},
    ],
}
# Blocks whose "age" is a growth timer, not visible stages: give them a short, readable stage count.
TIMER_KINDS = {"CactusBlock", "SugarCaneBlock", "BambooStalkBlock", "KelpBlock", "CaveVinesBlock",
               "WeepingVinesBlock", "TwistingVinesBlock", "GrowingPlantHeadBlock"}
NETHER_SOILS = {"minecraft:soul_sand", "minecraft:soul_soil", "minecraft:netherrack", "minecraft:crimson_nylium", "minecraft:warped_nylium"}


def preset_for(crop):
    on = set(crop.get("placeable_on", []))
    if crop.get("kind") == "MushroomBlock":
        return "dark"
    if "minecraft:farmland" in on:
        return "farmland"
    if on and on <= NETHER_SOILS | {"minecraft:end_stone"}:
        return "nether"
    if on and on <= {"minecraft:sand", "minecraft:red_sand"}:
        return "arid"
    if on:
        return "wild"
    return {"CactusBlock": "arid"}.get(crop.get("kind"), "farmland")


# ----------------------------------------------------------------------------- textures from jars / packs
class Assets:
    """Read-only view over mod jars, resource-pack zips and folders, first match wins (pass packs first)."""

    def __init__(self, paths):
        self.sources = []
        for p in paths:
            if os.path.isdir(p) and not os.path.isdir(os.path.join(p, "assets")):
                for name in sorted(os.listdir(p)):
                    if name.endswith((".jar", ".zip")):
                        self._add(os.path.join(p, name))
            else:
                self._add(p)

    def _add(self, path):
        if os.path.isdir(path):
            self.sources.append(("dir", path))
        elif zipfile.is_zipfile(path):
            self.sources.append(("zip", zipfile.ZipFile(path)))

    def read_json(self, rel):
        for kind, src in self.sources:
            try:
                if kind == "dir":
                    full = os.path.join(src, rel)
                    if os.path.isfile(full):
                        with open(full, encoding="utf-8") as f:
                            return json.load(f)
                else:
                    return json.loads(src.read(rel).decode("utf-8"))
            except KeyError:
                continue
            except (ValueError, OSError):
                return None
        return None


def split_id(rid, default_ns="minecraft"):
    ns, _, path = rid.partition(":") if ":" in rid else (default_ns, "", rid)
    return ns, path


def model_chain(assets, model_id):
    """Textures and parent ids for a model, following parents."""
    textures, parents, seen = {}, [], set()
    while model_id and model_id not in seen:
        seen.add(model_id)
        ns, path = split_id(model_id)
        data = assets.read_json(f"assets/{ns}/models/{path}.json")
        if not data:
            break
        for k, v in data.get("textures", {}).items():
            textures.setdefault(k, v)
        model_id = data.get("parent")
        if model_id:
            parents.append(model_id)
    return textures, parents


def resolve_texture(textures, key):
    v, depth = textures.get(key), 0
    while isinstance(v, str) and v.startswith("#") and depth < 8:
        v, depth = textures.get(v[1:]), depth + 1
    return v


def render_for(assets, crop):
    """Per-age textures from the crop's blockstate; returns (render dict or None, note)."""
    if assets is None:
        return None, "no --assets given; add render textures by hand"
    ns, path = split_id(crop["id"])
    if crop.get("kind") in TIMER_KINDS or crop.get("kind") in ("CocoaBlock", "MushroomBlock"):
        return None, "non-crop model shape; use a cultivar:crop/ model or add textures by hand"
    bs = assets.read_json(f"assets/{ns}/blockstates/{path}.json")
    if not bs or "variants" not in bs:
        return None, "blockstate not found or multipart; add render textures by hand"
    textures, shape = [], "cross"
    for age in range(crop.get("max_age", 0) + 1):
        variant = None
        for key, val in bs["variants"].items():
            props = dict(p.split("=", 1) for p in key.split(",") if "=" in p)
            if props.get("age", str(age)) == str(age):
                variant = val[0] if isinstance(val, list) else val
                break
        if not variant:
            continue
        tex, parents = model_chain(assets, variant.get("model"))
        if any("crop" in p for p in parents):
            shape = "hash"
        for key in ("crop", "cross", "plant", "texture", "all", "particle"):
            t = resolve_texture(tex, key)
            if t:
                t = t if ":" in t else "minecraft:" + t
                if not textures or textures[-1] != t:
                    textures.append(t)
                break
    if not textures:
        return None, "no textures found in models; add render by hand"
    return {"textures": textures, "shape": shape}, None


# ----------------------------------------------------------------------------- scaffold
def plant_id(crop):
    """Plants are named after the crop, not its block: wheat_seeds -> wheat, cocoa_beans -> cocoa, potato -> potato."""
    ns, _ = split_id(crop["id"])
    _, seed = split_id(crop["seed"])
    for suffix in ("_seeds", "_seed", "_beans", "_pod"):
        if seed.endswith(suffix) and len(seed) > len(suffix):
            seed = seed[: -len(suffix)]
            break
    return f"{ns}:{seed}"


def products_for(crop):
    out = []
    yields = crop.get("yields", [])
    for y in yields:
        if y["item"] == crop["seed"] and len(yields) > 1:
            continue  # seeds come back through the genome; keep only real produce
        if y["max"] <= 0:
            continue
        p = {"item": y["item"], "min": max(1, y["min"]), "max": max(1, y["max"])}
        if y["max"] <= 1 and y["avg"] < 0.99:
            p["chance"] = round(max(0.01, y["avg"]), 2)
        out.append(p)
    return out


def write_json(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(obj, f, indent=2)
        f.write("\n")


def cmd_scaffold(args):
    export = json.load(open(args.export, encoding="utf-8"))
    assets = Assets(args.assets) if args.assets else None
    out = args.out
    write_json(os.path.join(out, "pack.mcmeta"), {"pack": {"pack_format": PACK_FORMAT, "description": "Cultivar plants scaffolded by cultivar_scaffold.py"}})
    plan_rows, report, seen = [], [], {}
    # Several blocks can be stages of one crop (budding/grown tomatoes, rice/panicles): prefer real crops with yields.
    ordered = sorted(export["crops"], key=lambda c: (c.get("kind") != "CropBlock", not c.get("yields"), c["id"]))
    for crop in ordered:
        if crop.get("covered_by") and not args.include_covered:
            continue
        if args.mod and crop["mod"] not in args.mod:
            continue
        if crop["seed"] == "minecraft:air":
            report.append(f"SKIP {crop['id']}: no seed item")
            continue
        pid = plant_id(crop)
        if pid in seen:
            report.append(f"MERGE {crop['id']} into {pid} (already scaffolded from {seen[pid]}); check its stages/height by hand")
            continue
        seen[pid] = crop["id"]
        ns, path = split_id(pid)
        preset = preset_for(crop)
        render, note = render_for(assets, crop)
        plant = {
            "stages": 4 if crop.get("kind") in TIMER_KINDS else max(1, min(16, crop.get("max_age", 0) + 1)),
            "growth_chance": 0.75,
            "growth_bonus": 0.025,
            "spread_chance": 0.1,
            "seeds": crop["seed"],
            "products": products_for(crop),
            "requirements": PRESETS[preset],
        }
        height = {"CactusBlock": 3, "SugarCaneBlock": 3, "BambooStalkBlock": 4, "KelpBlock": 4}.get(crop.get("kind"), 1)
        if height > 1:
            plant["height"] = height  # grows up a stacked trellis
            plant["stages"] = height * 2
            plant["after_harvest_stage"] = 1
        if not plant["products"]:
            plant["products"] = [{"item": crop["seed"], "min": 1, "max": 2}]
            note = (note + "; " if note else "") + "no loot found, product defaults to the seed item"
        if render:
            plant["render"] = render
        if crop["mod"] != "minecraft":
            # Only load when the crop's mod is present, so the datapack is safe in any pack.
            plant = {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": crop["mod"]}], **plant}
        write_json(os.path.join(out, "data", ns, "cultivar", "plant", f"{path}.json"), plant)
        plan_rows.append({"child": pid, "parent_a": "", "parent_b": "", "chance": "0.25",
                          "light_min": "", "light_max": "", "humidity": "", "comparison": "", "acidity": "", "nutrients": "",
                          "notes": f"preset={preset}; soils={' '.join(crop.get('placeable_on', [])) or '?'}"})
        report.append(f"PLANT {pid} from {crop['id']} ({preset}){' - ' + note if note else ''}")
    plan = os.path.join(out, "mutation_plan.csv")
    if not os.path.exists(plan) or args.overwrite_plan:
        with open(plan, "w", newline="", encoding="utf-8") as f:
            w = csv.DictWriter(f, fieldnames=list(plan_rows[0].keys()) if plan_rows else ["child"])
            w.writeheader()
            w.writerows(plan_rows)
    else:
        report.append(f"NOTE {plan} exists; left untouched (use --overwrite-plan)")
    print("\n".join(report))
    print(f"\n{len(plan_rows)} plant(s) written to {out}/data. Fill in {plan}, then run: mutations {plan}")


# ----------------------------------------------------------------------------- mutations from the plan
def cmd_mutations(args):
    out = args.out or os.path.dirname(os.path.abspath(args.plan))
    count = 0
    with open(args.plan, newline="", encoding="utf-8") as f:
        for row in csv.DictReader(f):
            a, b, child = (row.get(k, "").strip() for k in ("parent_a", "parent_b", "child"))
            if not (a and b and child):
                continue
            conditions = []
            lo, hi = row.get("light_min", "").strip(), row.get("light_max", "").strip()
            if lo or hi:
                conditions.append({"type": "cultivar:light", "min": int(lo or 0), "max": int(hi or 15), "tolerance": 0})
            cmp = row.get("comparison", "").strip() or "equal"
            for kind in ("humidity", "acidity", "nutrients"):
                v = row.get(kind, "").strip()
                if v:
                    conditions.append({"type": f"cultivar:{kind}", "value": v, "comparison": cmp, "tolerance": 0})
            mutation = {"parent_a": a, "parent_b": b, "child": child, "chance": float(row.get("chance") or 0.25)}
            mods = sorted({split_id(x)[0] for x in (a, b, child)} - {"minecraft", "cultivar"})
            if mods:
                mutation = {"neoforge:conditions": [{"type": "neoforge:mod_loaded", "modid": m} for m in mods], **mutation}
            if conditions:
                mutation["conditions"] = conditions
            ns, path = split_id(child)
            name = path if not row.get("variant") else f"{path}_{row['variant']}"
            write_json(os.path.join(out, "data", args.namespace or ns, "cultivar", "mutation", f"{name}.json"), mutation)
            count += 1
    print(f"{count} mutation(s) written under {out}/data/*/cultivar/mutation/")


# ----------------------------------------------------------------------------- check
def load_pack(pack):
    plants, mutations, soils = {}, [], {}
    if not pack:
        return plants, mutations, soils
    data = os.path.join(pack, "data")
    for ns in os.listdir(data) if os.path.isdir(data) else []:
        for kind in ("plant", "mutation", "soil"):
            d = os.path.join(data, ns, "cultivar", kind)
            for name in os.listdir(d) if os.path.isdir(d) else []:
                if not name.endswith(".json"):
                    continue
                entry = json.load(open(os.path.join(d, name), encoding="utf-8"))
                if kind == "plant":
                    plants[f"{ns}:{name[:-5]}"] = entry
                elif kind == "soil":
                    soils[f"{ns}:{name[:-5]}"] = entry
                else:
                    mutations.append(entry)
    return plants, mutations, soils


# Breedability: parents and the grafting frame are adjacent, so they share light (neighbouring blocks differ by at
# most 1); each plant has its own soil, shifted by heat (-1), water (+1) and a Bell Cloche (+1). Checked at base
# stats (Strength 1), so a warning means a fresh seed can't do it; bred-up Strength may still manage.
HUMIDITY = ["arid", "dry", "damp", "wet", "watery", "flooded"]
SCALES = {"cultivar:humidity": (0, HUMIDITY),
          "cultivar:acidity": (1, ["highly_acidic", "acidic", "slightly_acidic", "neutral", "slightly_alkaline",
                                   "alkaline", "highly_alkaline"]),
          "cultivar:nutrients": (2, ["none", "very_low", "low", "medium", "high", "very_high"])}


def soil_states(soils):
    states = set()
    for s in soils.values():
        h = HUMIDITY.index(s.get("humidity", "wet"))
        a = SCALES["cultivar:acidity"][1].index(s.get("acidity", "slightly_acidic"))
        n = SCALES["cultivar:nutrients"][1].index(s.get("nutrients", "high"))
        for shifted in range(h - 1, h + 3):
            states.add((max(0, min(5, shifted)), a, n))
    return states


def light_range(conds):
    lo, hi = 0, 15
    for c in conds:
        if c.get("type") == "cultivar:light":
            widen = int(c.get("tolerance", 0.5))
            lo, hi = max(lo, c.get("min", 0) - widen), min(hi, c.get("max", 15) + widen)
    return (lo, hi) if lo <= hi else None


def soil_fits(states, *cond_lists):
    windows = []
    for conds in cond_lists:
        for c in conds:
            if c.get("type") in SCALES:
                slot, scale = SCALES[c["type"]]
                i, widen, cmp = scale.index(c["value"]), int(c.get("tolerance", 0)), c.get("comparison", "equal")
                windows.append((slot, -99 if cmp == "at_most" else i - widen, 99 if cmp == "at_least" else i + widen))
    return any(all(lo <= st[slot] <= hi for slot, lo, hi in windows) for st in states)


def breedability(m, plants, states):
    """Reasons this cross can't be bred on plain trellises at base stats (empty if it can)."""
    need = lambda p: plants.get(p, {}).get("requirements", [])
    habitat, child = m.get("conditions", []), need(m["child"])
    frame, la, lb = light_range(habitat + child), light_range(need(m["parent_a"])), light_range(need(m["parent_b"]))
    issues = []
    if frame is None or la is None or lb is None:
        issues.append("a light requirement is empty")
    elif max(frame[0], la[0] - 1, lb[0] - 1) > min(frame[1], la[1] + 1, lb[1] + 1):
        issues.append(f"no shared light: frame {frame}, {m['parent_a']} {la}, {m['parent_b']} {lb}")
    if not soil_fits(states, habitat, child):
        issues.append("no soil meets the habitat and the child's needs together")
    for p in (m["parent_a"], m["parent_b"]):
        if not soil_fits(states, need(p)):
            issues.append(f"no soil grows {p}")
    return issues


def cmd_check(args):
    export = json.load(open(args.export, encoding="utf-8"))
    pack_plants, pack_mutations, pack_soils = load_pack(args.pack)
    plant_data = {**export.get("plants", {}), **pack_plants}
    plants = set(plant_data)
    mutations = list(export.get("mutations", {}).values()) + pack_mutations
    states = soil_states({**export.get("soils", {}), **pack_soils})
    problems = 0
    produced = defaultdict(list)
    pairs = defaultdict(list)
    for m in mutations:
        for k in ("parent_a", "parent_b", "child"):
            if m[k] not in plants:
                print(f"ERROR mutation {m['parent_a']} x {m['parent_b']} -> {m['child']}: unknown plant {m[k]}")
                problems += 1
        produced[m["child"]].append(m)
        pairs[tuple(sorted((m["parent_a"], m["parent_b"])))].append(m)
        if all(m[k] in plants for k in ("parent_a", "parent_b", "child")):
            for issue in breedability(m, plant_data, states):
                print(f"WARN {m['parent_a']} x {m['parent_b']} -> {m['child']} can't be bred on plain trellises: {issue}")
    for pair, ms in pairs.items():
        if len(ms) > 1 and any(not m.get("conditions") for m in ms):
            print(f"WARN {pair[0]} x {pair[1]} forks into {len(ms)} children but not all have habitat conditions")
    roots = sorted(p for p in plants if p not in produced)
    print(f"{len(plants)} plants, {len(mutations)} mutations")
    print("Roots (not bred from anything; players must find these):")
    for r in roots:
        print("  " + r)
    uncovered = [c["id"] for c in export["crops"] if not c.get("covered_by") and plant_id(c) not in plants]
    if uncovered:
        print(f"Crops with no Cultivar plant yet ({len(uncovered)}):")
        for u in uncovered:
            print("  " + u)
    return 1 if problems else 0


def main():
    ap = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    sub = ap.add_subparsers(dest="cmd", required=True)
    s = sub.add_parser("scaffold", help="write plant JSON for every uncovered crop, plus mutation_plan.csv")
    s.add_argument("export", help="crops.json from /cultivar export")
    s.add_argument("--out", default="cultivar_scaffold", help="datapack folder to write (default: cultivar_scaffold)")
    s.add_argument("--assets", action="append", help="mods folder, mod jar, resource pack or client jar to read blockstates from (repeatable)")
    s.add_argument("--mod", action="append", help="only crops from this mod id (repeatable)")
    s.add_argument("--include-covered", action="store_true", help="also scaffold crops Cultivar already covers")
    s.add_argument("--overwrite-plan", action="store_true", help="replace an existing mutation_plan.csv")
    m = sub.add_parser("mutations", help="turn a filled-in mutation_plan.csv into mutation JSON")
    m.add_argument("plan")
    m.add_argument("--out", help="datapack folder (default: the plan's folder)")
    m.add_argument("--namespace", help="namespace for the mutation files (default: the child's)")
    c = sub.add_parser("check", help="report unknown plants, unbreedable crosses, unconditioned forks, roots and uncovered crops")
    c.add_argument("export")
    c.add_argument("--pack", help="scaffolded datapack folder to include")
    args = ap.parse_args()
    sys.exit({"scaffold": cmd_scaffold, "mutations": cmd_mutations, "check": cmd_check}[args.cmd](args) or 0)


if __name__ == "__main__":
    main()
