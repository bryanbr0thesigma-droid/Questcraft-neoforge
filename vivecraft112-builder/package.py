#!/usr/bin/env python3
"""Packs QuestCraft's Vivecraft 1.12.2: the classes MCP reobfuscated (Vivecraft's own and the game and OptiFine
classes it changes, under the game's runtime names), Vivecraft's assets and LWJGL's OpenXR bindings. Like Vivecraft's
installer it holds no unchanged Minecraft or OptiFine code: the launcher puts the jar over the vanilla client and the
OptiFine build Vivecraft uses (see merge()).

Usage: package.py <mcp940/reobf/minecraft> <Vivecraft_112/resources> <lwjgl-openxr jar> <mcp940/conf/joined.srg> <out jar>
       package.py --merge <vanilla client jar> <OptiFine jar> <Vivecraft jar> <out jar>   (what the launcher does)
"""
import os
import sys
import zipfile

# the jar is reproducible: same inputs, same bytes
STAMP = (2021, 11, 20, 0, 0, 0)


def add(out, name, data):
    info = zipfile.ZipInfo(name, STAMP)
    info.compress_type = zipfile.ZIP_DEFLATED
    info.external_attr = 0o644 << 16
    out.writestr(info, data)


def package(reobf, resources, openxr, srg, target):
    entries = {}
    for root, _, files in os.walk(reobf):
        for f in files:
            if f.endswith('.class'):
                path = os.path.join(root, f)
                entries[os.path.relpath(path, reobf).replace(os.sep, '/')] = open(path, 'rb').read()
    # assets only: resources/natives are SteamVR's and KAT VR's desktop libraries
    assets = os.path.join(resources, 'assets')
    for root, _, files in os.walk(assets):
        for f in files:
            path = os.path.join(root, f)
            entries[os.path.relpath(path, resources).replace(os.sep, '/')] = open(path, 'rb').read()
    # MCP's names, which Vivecraft's reflection (MCReflection, ObfNames) looks the game's fields and methods up by
    entries['mappings/vivecraft/joined.srg'] = open(srg, 'rb').read()
    with zipfile.ZipFile(openxr) as z:
        for name in z.namelist():
            if name.startswith('org/lwjgl/openxr/') and name.endswith('.class'):
                entries[name] = z.read(name)
    with zipfile.ZipFile(target, 'w') as out:
        add(out, 'META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\r\nImplementation-Title: Vivecraft 1.12.2 for QuestCraft\r\n\r\n')
        for name in sorted(entries):
            add(out, name, entries[name])
    print('packed %d entries into %s' % (len(entries) + 1, target))


def is_signature(name):
    upper = name.upper()
    return upper.startswith('META-INF/') and (upper.endswith('.SF') or upper.endswith('.RSA') or upper.endswith('.DSA')
                                              or upper.endswith('.EC') or upper == 'META-INF/MANIFEST.MF')


def merge(vanilla, optifine, vivecraft, target):
    """vanilla, then OptiFine over it, then Vivecraft over both; Mojang's signature is dropped, the classes change"""
    entries = {}
    for jar in (vanilla, optifine, vivecraft):
        with zipfile.ZipFile(jar) as z:
            for name in z.namelist():
                if not name.endswith('/') and not is_signature(name):
                    entries[name] = z.read(name)
    with zipfile.ZipFile(target, 'w') as out:
        add(out, 'META-INF/MANIFEST.MF', b'Manifest-Version: 1.0\r\n\r\n')
        for name in sorted(entries):
            add(out, name, entries[name])
    print('merged %d entries into %s' % (len(entries) + 1, target))


if __name__ == '__main__':
    if sys.argv[1] == '--merge':
        merge(*sys.argv[2:6])
    else:
        package(*sys.argv[1:6])
