#!/usr/bin/env python3
"""Puts the built Vivecraft 1.12.2 into the app and writes pojlib/vivecraft-legacy.json (and the app's copy), the
list Pojlib's LegacyVivecraft reads: the jar, the OptiFine build it was made against and Vivecraft's libraries.

Usage: make_registry.py <vivecraft-1.12.2-questcraft.jar> <OptiFine-1.12.2_HD_U_F5.jar>"""
import hashlib
import json
import os
import shutil
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
RAW = 'https://raw.githubusercontent.com/bryanbr0thesigma-droid/Questcraft-neoforge/claude/fervent-mayer-8idkzr'
VERSION = '1.12.2-jrbudda-11-r6-qc1'
ASSET = 'vivecraft-jars/legacy/vivecraft-1.12.2-questcraft.jar.bin'
OPTIFINE_URL = 'https://vivecraft.org/jar/build/OptiFine-1.12.2_HD_U_F5.jar'

# Vivecraft 1.12.2's own libraries: org.json for its settings profiles, vecmath, and LaunchWrapper, whose classes
# OptiFine looks for (it is not used to start the game)
LIBRARIES = [
    ('net.minecraft:launchwrapper:1.12', 'net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar',
     'https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar',
     '111e7bea9c968cdb3d06ef4632bf7ff0824d0f36', 32999),
    ('org.json:json:20140107', 'org/json/json/20140107/json-20140107.jar',
     'https://repo1.maven.org/maven2/org/json/json/20140107/json-20140107.jar',
     'd1ffca6e2482b002702c6a576166fd685e3370e3', 64952),
    ('java3d:vecmath:1.3.1', 'java3d/vecmath/1.3.1/vecmath-1.3.1.jar',
     'https://libraries.minecraft.net/java3d/vecmath/1.3.1/vecmath-1.3.1.jar',
     'a0ae4f51da409fa0c20fa0ca59e6bbc9413ae71d', 289881),
]


def sha1(path):
    return hashlib.sha1(open(path, 'rb').read()).hexdigest()


def main(jar, optifine):
    optifine_sha1 = sha1(optifine)
    if hashlib.md5(open(optifine, 'rb').read()).hexdigest() != '3c3d75fd20ebef6ac0091d358d01b4bb':
        sys.exit('not the OptiFine build Vivecraft 1.12.2 is made against (md5 3c3d75fd20ebef6ac0091d358d01b4bb)')

    target = os.path.join(REPO, 'pojlib', 'src', 'main', 'assets', ASSET)
    os.makedirs(os.path.dirname(target), exist_ok=True)
    shutil.copyfile(jar, target)

    registry = {'versions': [{
        'name': '1.12.2',
        'version': VERSION,
        'vivecraft': {'url': RAW + '/pojlib/src/main/assets/' + ASSET, 'sha1': sha1(jar), 'size': os.path.getsize(jar)},
        'optifine': {'url': OPTIFINE_URL, 'sha1': optifine_sha1, 'size': os.path.getsize(optifine)},
        'libraries': [{'name': name, 'downloads': {'artifact': {'path': path, 'url': url, 'sha1': sha, 'size': size}}}
                      for name, path, url, sha, size in LIBRARIES],
    }]}
    text = json.dumps(registry, indent=2) + '\n'
    for path in (os.path.join(REPO, 'pojlib', 'vivecraft-legacy.json'),
                 os.path.join(REPO, 'pojlib', 'src', 'main', 'assets', 'loaders', 'vivecraft-legacy.json')):
        open(path, 'w').write(text)
    print('Vivecraft %s: %s (%d bytes), OptiFine %s' % (VERSION, sha1(jar), os.path.getsize(jar), optifine_sha1))


if __name__ == '__main__':
    main(sys.argv[1], sys.argv[2])
