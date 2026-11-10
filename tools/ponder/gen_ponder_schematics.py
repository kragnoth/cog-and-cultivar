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
    t.set(2, 0, 2, 'create:shaft[axis=y,waterlogged=false]')
    t.set(2, 1, 2, 'cultivar:assaying_engine')
    return t


for name, build in (('spreading', spreading), ('crop_stats', stats), ('crossbreeding', crossbreeding),
                    ('assaying', assaying)):
    build().save(f'{OUT}/{name}.nbt')
print('ponder schematics ok')
