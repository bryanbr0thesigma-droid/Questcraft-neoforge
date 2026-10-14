#!/usr/bin/env python3
"""Writes pojlib/modloader-builds.json (and the app's copy): for every Minecraft version Risugami's ModLoader was made
for and Mojang still lists, the newest ModLoader build, where to download it and its SHA-256, as MCArchive
(mcarchive.net, which keeps the mods of that time) lists them. Pojlib's RisugamiModLoader reads it.

Each build is downloaded here and checked against MCArchive's SHA-256: from MCArchive's own copy when it has one,
else from Risugami's Mediafire page (the app does the same, reading the file's address from the page). Kept in
work/archives for test_versions.sh.

Usage: make_registry.py [mcarchive-modloader.html]   (the page is downloaded when not given)"""
import hashlib
import html
import json
import os
import re
import sys
import urllib.request

HERE = os.path.dirname(os.path.abspath(__file__))
REPO = os.path.dirname(HERE)
ARCHIVES = os.path.join(HERE, 'work', 'archives')
PAGE = 'https://mcarchive.net/mods/modloader'
MANIFEST = 'https://piston-meta.mojang.com/mc/game/version_manifest_v2.json'
# MCArchive's name for a version where Mojang's differs
MOJANG_NAMES = {'1.0.0': '1.0'}


def get(url):
    request = urllib.request.Request(url, headers={'User-Agent': 'Mozilla/5.0 (QuestCraft ModLoader list)'})
    return urllib.request.urlopen(request, timeout=120).read()


def mediafire_link(page):
    """The file's address on a Mediafire download page (over https: the page gives http, which Android refuses)."""
    match = re.search(r'href="(https?://download\d+\.mediafire\.com/[^"]+)"', page)
    return match.group(1).replace('http://', 'https://', 1) if match else None


def builds(page):
    """(MCArchive version, [build]) per version on the page, newest build first; a build is its file name, SHA-256 and links."""
    out = []
    for block in re.split(r'<h2>For Minecraft ', page)[1:]:
        version = html.unescape(re.search(r'gvsn=([^"]+)"', block).group(1))
        files = []
        for sub in re.findall(r'<section class="sub-block">(.*?)</section>', block, re.S):
            name = html.unescape(re.search(r'class="file-name">([^<]+)<', sub).group(1)).strip()
            sha = re.search(r'sha256 hash">([0-9a-f]{64})<', sub)
            links = {label.split()[0]: html.unescape(href) for href, label in re.findall(r'<a class="button" href="([^"]+)">([^<]+)</a>', sub)}
            lower = name.lower()
            if sha and 'javadoc' not in lower and 'source' not in lower and lower.endswith(('.zip', '.rar')):
                files.append({'file': name, 'sha256': sha.group(1), 'links': links})
        out.append((version, files))
    return out


def fetch(build):
    """Downloads a build into work/archives (once), checks its SHA-256, and says where the app gets it from."""
    os.makedirs(ARCHIVES, exist_ok=True)
    target = os.path.join(ARCHIVES, build['file'])
    source = {}
    if 'Archive' in build['links']:
        source['url'] = build['links']['Archive']
    if 'Official' in build['links']:
        source['mediafire'] = build['links']['Official'].replace('http://', 'https://', 1)
    if not os.path.exists(target) or hashlib.sha256(open(target, 'rb').read()).hexdigest() != build['sha256']:
        url = source.get('url') or mediafire_link(get(source['mediafire']).decode('utf-8', 'replace'))
        if not url:
            raise RuntimeError('no file address on the Mediafire page of ' + build['file'])
        data = get(url)
        if hashlib.sha256(data).hexdigest() != build['sha256']:
            raise RuntimeError(build['file'] + ' is not the file MCArchive lists')
        open(target, 'wb').write(data)
    return target, source


def main(page_file=None):
    page = open(page_file, encoding='utf-8').read() if page_file else get(PAGE).decode('utf-8')
    mojang = {v['id'] for v in json.loads(get(MANIFEST))['versions']}
    versions = []
    for name, files in builds(page):
        minecraft = MOJANG_NAMES.get(name, name)
        if minecraft not in mojang:
            print('skip %-10s Mojang has no such version' % name)
            continue
        if not files:
            print('skip %-10s no ModLoader build listed' % name)
            continue
        build = files[0]
        try:
            path, source = fetch(build)
        except Exception as e:
            print('skip %-10s %s: %s' % (name, build['file'], e))
            continue
        entry = {'name': minecraft, 'file': build['file'], 'format': build['file'].rsplit('.', 1)[1].lower(),
                 'sha256': build['sha256'], 'size': os.path.getsize(path)}
        entry.update(source)
        versions.append(entry)
        print('%-10s %-26s %s' % (minecraft, build['file'], 'MCArchive' if 'url' in source else 'Mediafire'))
    text = json.dumps({'source': PAGE, 'versions': versions}, indent=2) + '\n'
    for path in (os.path.join(REPO, 'pojlib', 'modloader-builds.json'),
                 os.path.join(REPO, 'pojlib', 'src', 'main', 'assets', 'loaders', 'modloader-builds.json')):
        open(path, 'w').write(text)
    print('%d versions' % len(versions))


if __name__ == '__main__':
    main(*sys.argv[1:])
