#!/usr/bin/env python3
"""Downloads Mojang's client jar, version file and (for 1.6) libraries of every version in pojlib/modloader-builds.json
into work/clients and work/libraries, checked against Mojang's SHA-1s, for the desktop test (test_versions.sh)."""
import hashlib
import json
import os
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
WORK = os.path.join(HERE, 'work')
MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'


def get(url):
    return urllib.request.urlopen(urllib.request.Request(url, headers={'User-Agent': 'QuestCraft'}), timeout=120).read()


def fetch(url, sha1, target):
    if os.path.exists(target) and hashlib.sha1(open(target, 'rb').read()).hexdigest() == sha1:
        return
    data = get(url)
    if hashlib.sha1(data).hexdigest() != sha1:
        raise RuntimeError('wrong file: ' + url)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    open(target, 'wb').write(data)


def main():
    os.makedirs(os.path.join(WORK, 'clients'), exist_ok=True)
    versions = {v['id']: v for v in json.loads(get(MANIFEST))['versions']}
    builds = json.load(open(os.path.join(REPO, 'pojlib', 'modloader-builds.json')))['versions']
    for build in builds:
        name = build['name']
        info = json.loads(get(versions[name]['url']))
        json.dump(info, open(os.path.join(WORK, 'clients', name + '.json'), 'w'))
        client = info['downloads']['client']
        fetch(client['url'], client['sha1'], os.path.join(WORK, 'clients', name + '.jar'))
        libraries = []
        for library in info['libraries']:
            artifact = library.get('downloads', {}).get('artifact')
            # LWJGL is the headset's own, LaunchWrapper and ASM are not used (the legacy launcher starts the game)
            if not artifact or library['name'].startswith('org.lwjgl') or 'launchwrapper' in library['name'] or 'org.ow2.asm' in library['name']:
                continue
            target = os.path.join(WORK, 'libraries', artifact['path'])
            fetch(artifact['url'], artifact['sha1'], target)
            libraries.append(target)
        open(os.path.join(WORK, 'clients', name + '.classpath'), 'w').write(os.pathsep.join(libraries))
        print('%-10s %s, %d libraries' % (name, info['mainClass'], len(libraries)))


if __name__ == '__main__':
    main()
