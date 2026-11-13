#!/usr/bin/env python3
"""Checks that every shipped cross can be bred on ordinary trellises at base stats (Strength 1, timber).

A cross needs both parents mature next to the frame and the frame's habitat met, then the child must grow there.
Light is shared: adjacent blocks differ by at most 1, so each parent's light must be within 1 of the frame's.
Shade/Lamp Cloches and soil conditioners are ignored on purpose: every cross must work without them.
Soil is per plant: any shipped soil, shifted by heat (-1), water (+1) and a Bell Cloche (+1), dry farmland -1.
Run from the repo root: python3 tools/data/check_crosses.py
"""
import glob
import json
import sys

HUM = ["arid", "dry", "damp", "wet", "watery", "flooded"]
ACID = ["highly_acidic", "acidic", "slightly_acidic", "neutral", "slightly_alkaline", "alkaline", "highly_alkaline"]
NUT = ["none", "very_low", "low", "medium", "high", "very_high"]
SCALES = {"cultivar:humidity": HUM, "cultivar:acidity": ACID, "cultivar:nutrients": NUT}
STRENGTH = 1


def load(kind):
    out = {}
    for f in glob.glob(f"src/main/resources/data/*/cultivar/{kind}/**/*.json", recursive=True):
        ns = f.split("/")[4]
        path = f.split(f"/cultivar/{kind}/", 1)[1][:-5]
        out[f"{ns}:{path}"] = json.load(open(f))
    return out


plants, mutations, soils = load("plant"), load("mutation"), load("soil")


def soil_states():
    """Every (humidity, acidity, nutrients) a trellis can stand on."""
    states = set()
    for s in soils.values():
        h = HUM.index(s["humidity"])
        # Farmland: dry or heated -1 .. cloche +1 (its moisture already counts the water). Other soils: heat -1 ..
        # water beside and a cloche +2.
        top = 2 if "farmland" in str(s.get("blocks", "")) else 3
        for hi in range(h - 1, h + top):
            states.add((max(0, min(5, hi)), ACID.index(s["acidity"]), NUT.index(s["nutrients"])))
    return states


SOILS = soil_states()


SOIL_DEFAULTS = {"cultivar:humidity": ("equal", 0.15), "cultivar:acidity": ("equal", 0.2),
                 "cultivar:nutrients": ("at_least", 0.1)}  # the mod's codec defaults


def window(cond):
    i = SCALES[cond["type"]].index(cond["value"])
    default_cmp, default_tol = SOIL_DEFAULTS[cond["type"]]
    widen = int(cond.get("tolerance", default_tol) * STRENGTH)
    cmp = cond.get("comparison", default_cmp)
    lo = -99 if cmp == "at_most" else i - widen
    hi = 99 if cmp == "at_least" else i + widen
    return cond["type"], lo, hi


def light_range(conds):
    lo, hi = 0, 15
    for c in conds:
        if c["type"] == "cultivar:light":
            widen = int(c.get("tolerance", 0.5) * STRENGTH)
            lo, hi = max(lo, c.get("min", 10) - widen), min(hi, c.get("max", 15) + widen)
    return (lo, hi) if lo <= hi else None


def soil_ok(conds_lists):
    """Is there one soil state satisfying every soil condition in all the lists?"""
    wins = [window(c) for conds in conds_lists for c in conds if c["type"] in SCALES]
    idx = {"cultivar:humidity": 0, "cultivar:acidity": 1, "cultivar:nutrients": 2}
    return any(all(lo <= st[idx[t]] <= hi for t, lo, hi in wins) for st in SOILS)


def reqs(pid):
    return plants[pid].get("requirements", []) if pid in plants else None


problems = []
for mid, m in sorted(mutations.items()):
    # Optional-mod data is checked as if its mod were present.
    a, b, child = m["parent_a"], m["parent_b"], m["child"]
    if None in (reqs(a), reqs(b), reqs(child)):
        problems.append(f"{mid}: references a plant that does not exist")
        continue
    habitat = m.get("conditions", [])
    frame = light_range(habitat + reqs(child))
    la, lb = light_range(reqs(a)), light_range(reqs(b))
    issues = []
    if frame is None:
        issues.append("child cannot grow in the cross's light")
    elif la is None or lb is None:
        issues.append("a parent has no valid light")
    else:
        lo, hi = max(frame[0], la[0] - 1, lb[0] - 1), min(frame[1], la[1] + 1, lb[1] + 1)
        if lo > hi:
            issues.append(f"light: frame {frame}, {a} {la}, {b} {lb}")
    if not soil_ok([habitat, reqs(child)]):
        issues.append("no soil meets the habitat and the child's needs together")
    for p in (a, b):
        if not soil_ok([reqs(p)]):
            issues.append(f"no soil grows {p}")
    if issues:
        problems.append(f"{mid} ({a} x {b} -> {child}): " + "; ".join(issues))

print(f"{len(mutations)} crosses checked")
for p in problems:
    print("  " + p)
sys.exit(1 if problems else 0)
