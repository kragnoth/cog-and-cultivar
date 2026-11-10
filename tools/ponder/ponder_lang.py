#!/usr/bin/env python3
"""Mirrors the Ponder scene text in CultivarScenes.java into assets/cultivar/lang/en_us.json.

Ponder looks text up as cultivar.ponder.<scene>.header and .text_N, numbered in the order the texts are created in
each storyboard; this reads scene.title(...) and every .text("...") in source order.
Run from the repo root: python3 tools/ponder/ponder_lang.py [--check]
"""
import collections
import json
import re
import sys

SRC = 'src/main/java/com/kragnoth/cultivar/client/ponder/CultivarScenes.java'
LANG = 'src/main/resources/assets/cultivar/lang/en_us.json'
LIT = r'"((?:[^"\\]|\\.)*)"'
TOKEN = re.compile(r'\.title\(\s*' + LIT + r'\s*,\s*' + LIT + r'\s*\)|\.text\(\s*' + LIT + r'\s*\)')


def unescape(s):
    return bytes(s, 'utf-8').decode('unicode_escape').encode('latin-1').decode('utf-8')


def entries():
    out, scene, n = collections.OrderedDict(), None, 0
    for m in TOKEN.finditer(open(SRC, encoding='utf-8').read()):
        if m.group(1) is not None:
            scene, n = m.group(1), 0
            out[f'cultivar.ponder.{scene}.header'] = unescape(m.group(2))
        else:
            n += 1
            out[f'cultivar.ponder.{scene}.text_{n}'] = unescape(m.group(3))
    return out


def main():
    want = entries()
    lang = json.load(open(LANG, encoding='utf-8'), object_pairs_hook=collections.OrderedDict)
    merged = collections.OrderedDict((k, v) for k, v in lang.items() if not k.startswith('cultivar.ponder.'))
    merged.update(want)
    text = json.dumps(merged, indent=2, ensure_ascii=False) + '\n'
    if '--check' in sys.argv:
        if text != open(LANG, encoding='utf-8').read():
            sys.exit('Ponder lang is stale: run python3 tools/ponder/ponder_lang.py')
        print(f'ponder lang up to date ({len(want)} entries)')
        return
    open(LANG, 'w', encoding='utf-8').write(text)
    print(f'{len(want)} ponder lang entries written')


main()
