#!/usr/bin/env python3
"""Ponder scene structures (vanilla structure NBT) for the Cog & Cultivar Ponder scenes.

Writes src/main/resources/assets/cultivar/ponder/*.nbt. Layer 0 is the base plate; crops are planted by the scene
code at play time, so the structures hold only blocks.
Run from the repo root: python3 tools/ponder/gen_ponder_schematics.py
"""
import os
import sys

sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..', 'worldgen'))
from gen_conservatory import Template  # noqa: E402

OUT = 'src/main/resources/assets/cultivar/ponder'
GRASS = 'minecraft:grass_block[snowy=false]'
FARMLAND = 'minecraft:farmland[moisture=7]'
WATER = 'minecraft:water[level=0]'
TRELLIS = 'cultivar:timber_trellis[cross=false,cloche=false]'


def plate(t, w, d, block=GRASS):
    t.fill(0, 0, 0, w - 1, 0, d - 1, block)


def row(t, xs, z):
    """Moist farmland under a row of trellises, with a water channel behind it."""
    for x in xs:
        t.set(x, 0, z, FARMLAND)
        t.set(x, 0, z + 1, WATER)
        t.set(x, 1, z, TRELLIS)


def spreading():
    t = Template(5, 3, 5)
    plate(t, 5, 5)
    row(t, (1, 2, 3), 2)
    return t


def stats():
    t = Template(7, 3, 5)
    plate(t, 7, 5)
    row(t, (1, 2, 3, 4, 5), 2)
    return t


def crossbreeding():
    t = Template(7, 3, 5)
    plate(t, 7, 5)
    row(t, (1, 2, 3), 2)
    t.set(5, 0, 2, 'minecraft:coarse_dirt')  # the humidity demo: a dry soil, levers added by the scene
    t.set(5, 1, 2, TRELLIS)
    return t


def assaying():
    t = Template(5, 3, 5)
    plate(t, 5, 5, 'minecraft:smooth_stone')
    t.set(0, 0, 2, 'create:creative_motor[facing=up,waterlogged=false]')
    t.set(0, 1, 2, 'create:cogwheel[axis=y,waterlogged=false]')  # meshes with the first Assayer like a crafter's cog
    t.set(1, 1, 2, 'cultivar:assaying_engine')
    t.set(2, 1, 2, 'cultivar:brass_assaying_engine')
    t.set(3, 1, 2, 'cultivar:chromatic_assaying_engine')
    return t


def field_crops():
    """A row of farmland by a water channel: seeds go straight in, no trellis."""
    t = Template(5, 3, 5)
    plate(t, 5, 5)
    for x in (1, 2, 3):
        t.set(x, 0, 2, FARMLAND)
        t.set(x, 0, 3, WATER)
    return t


def cloches():
    """Three trellises on watered farmland, for a Bell, a Shade and a Lamp Cloche."""
    t = Template(5, 3, 5)
    plate(t, 5, 5)
    row(t, (1, 2, 3), 2)
    return t


def olla():
    """An Olla in the ground (layer 0) under a 5x5 of dry farmland (layer 1)."""
    t = Template(5, 3, 5)
    plate(t, 5, 5, 'minecraft:dirt')
    t.set(2, 0, 2, 'cultivar:olla[filled=false]')
    t.fill(0, 1, 0, 4, 1, 4, 'minecraft:farmland[moisture=0]')
    return t


def sprinkler():
    """A Sprinkler on a fluid pipe in the middle of a 5x5 of farmland."""
    t = Template(5, 3, 5)
    plate(t, 5, 5, 'minecraft:farmland[moisture=7]')
    t.set(2, 0, 2, 'create:fluid_pipe[axis=y,waterlogged=false,up=true,down=true,north=false,south=false,east=false,west=false]')
    t.set(2, 1, 2, 'cultivar:sprinkler[facing=up,active=true]')
    return t


def planter_beds():
    """A 3x3 plot of Planter Beds with a chest + hopper feeding the corner bed."""
    t = Template(6, 4, 6)
    plate(t, 6, 6, 'minecraft:smooth_stone')
    for x in (2, 3, 4):
        for z in (1, 2, 3):
            t.set(x, 1, z, 'cultivar:planter_bed')
    t.set(5, 0, 1, 'create:creative_motor[facing=up,waterlogged=false]')
    t.set(5, 1, 1, 'create:cogwheel[axis=y,waterlogged=false]')  # meshes with the corner bed, like a crafter
    t.set(1, 1, 1, 'minecraft:hopper[enabled=true,facing=east]')
    t.set(1, 2, 1, 'minecraft:chest[facing=west,type=single,waterlogged=false]')
    return t


def trial_beds():
    """A 3x3 Trial plot."""
    t = Template(5, 4, 5)
    plate(t, 5, 5, 'minecraft:smooth_stone')
    for x in (1, 2, 3):
        for z in (1, 2, 3):
            t.set(x, 1, z, 'cultivar:trial_bed')
    t.set(4, 0, 1, 'create:creative_motor[facing=up,waterlogged=false]')
    t.set(4, 1, 1, 'create:cogwheel[axis=y,waterlogged=false]')
    return t


for name, build in (('spreading', spreading), ('crop_stats', stats), ('crossbreeding', crossbreeding),
                    ('assaying', assaying), ('field_crops', field_crops), ('cloches', cloches), ('olla', olla),
                    ('sprinkler', sprinkler), ('planter_beds', planter_beds), ('trial_beds', trial_beds)):
    build().save(f'{OUT}/{name}.nbt')
print('ponder schematics ok')
