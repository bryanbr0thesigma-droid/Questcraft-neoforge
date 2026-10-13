#!/usr/bin/env python3
"""make_registry.py: writes pojlib/visor-mods.json (and the copy the app carries) from the jars in pojlib/visor-jars/.
Quilt needs no entry of its own, it runs the Fabric jar."""
import json, os, re, sys, shutil

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
RAW = 'https://raw.githubusercontent.com/bryanbr0thesigma-droid/Questcraft-neoforge/claude/fervent-mayer-8idkzr'
VERSION = '0.6.0-beta-qc2'   # qc2: Vivecraft's Quest controller layout
LOADERS = ('fabric', 'forge', 'neoforge')

versions = {}
for loader in LOADERS:
    d = os.path.join(ROOT, 'pojlib', 'visor-jars', loader)
    if not os.path.isdir(d):
        continue
    for f in sorted(os.listdir(d)):
        m = re.fullmatch(r'visor-%s-(.+)\.jar' % loader, f)
        if m:
            versions.setdefault(m.group(1), {})[loader] = {
                'slug': 'Visor', 'version': VERSION,
                'download_link': '%s/pojlib/visor-jars/%s/%s' % (RAW, loader, f)}

def key(v):
    return [int(x) for x in v.split('.')]

registry = {'versions': [dict(name=v, **versions[v]) for v in sorted(versions, key=key, reverse=True)]}
for target in ('pojlib/visor-mods.json', 'pojlib/src/main/assets/loaders/visor-mods.json'):
    with open(os.path.join(ROOT, target), 'w') as out:
        json.dump(registry, out, indent=2)
        out.write('\n')
print('Visor builds:', {v: sorted(l) for v, l in versions.items()})
