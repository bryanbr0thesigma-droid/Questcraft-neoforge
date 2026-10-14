#!/usr/bin/env python3
"""Writes the versions the app offers for ModLoader: those of pojlib/modloader-builds.json that passed
test_versions.sh (work/test/results.txt), newest first. Into supportedVersions-modloader.json, the (empty) mod lists
mods-modloader.json and devmods-modloader.json, their copies in the app, and the Godot launcher's desktop mock."""
import json
import os
import re

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)


def main():
    passed = set()
    for line in open(os.path.join(HERE, 'work', 'test', 'results.txt')):
        parts = line.split()
        if len(parts) >= 2 and parts[1] == 'PASS':
            passed.add(parts[0])
    builds = json.load(open(os.path.join(REPO, 'pojlib', 'modloader-builds.json')))['versions']
    offered = [b['name'] for b in builds if b['name'] in passed]
    lists = {
        'supportedVersions-modloader.json': {'supportedVersions': offered},
        # no mods every ModLoader instance gets: ModLoader itself is in the client, mods come from Modrinth
        'mods-modloader.json': {'versions': [{'name': v, 'coreMods': [], 'defaultMods': []} for v in offered]},
        'devmods-modloader.json': {'versions': [{'name': v, 'coreMods': [], 'defaultMods': []} for v in offered]},
    }
    for name, content in lists.items():
        text = json.dumps(content, indent=2) + '\n'
        for path in (os.path.join(REPO, 'pojlib', name), os.path.join(REPO, 'pojlib', 'src', 'main', 'assets', 'loaders', name)):
            open(path, 'w').write(text)
    bridge = os.path.join(REPO, 'godot-launcher', 'scripts', 'bridge.gd')
    text = open(bridge).read()
    text = re.sub(r'const MOCK_MODLOADER_VERSIONS := \[.*\]', 'const MOCK_MODLOADER_VERSIONS := ' + json.dumps(offered), text)
    open(bridge, 'w').write(text)
    print('%d of %d versions offered; not: %s' % (len(offered), len(builds), ', '.join(b['name'] for b in builds if b['name'] not in passed) or 'none'))


if __name__ == '__main__':
    main()
