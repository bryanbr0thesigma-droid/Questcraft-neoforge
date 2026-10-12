#!/usr/bin/env python3
"""Makes the NeoForge builds of ModernFix work on Minecraft 1.21.2 and later.

Since 1.21.2 EntityRenderDispatcher has two methods called getRenderer (one takes an Entity, the other an
EntityRenderState). ModernFix's mixin perf.dynamic_entity_renderers.EntityRenderDispatcherMixin names the method
without its descriptor, so Mixin applies the injection to both and fails on the second ("Invalid descriptor"), which
aborts the game at start-up. The patch gives the injection the exact method.

ModernFix is LGPL-3.0 (https://github.com/embeddedt/ModernFix); this changes one string constant in one class of
the published jar.

usage: patch_modernfix.py <in.jar> <out.jar>
"""
import struct
import sys
import zipfile

MIXIN = "org/embeddedt/modernfix/common/mixin/perf/dynamic_entity_renderers/EntityRenderDispatcherMixin.class"
OLD = b"getRenderer"
NEW = b"getRenderer(Lnet/minecraft/world/entity/Entity;)Lnet/minecraft/client/renderer/entity/EntityRenderer;"


def patch_class(data: bytes) -> bytes:
    assert data[:4] == b"\xca\xfe\xba\xbe"
    count = struct.unpack(">H", data[8:10])[0]
    pos = 10
    i = 1
    hits = []
    while i < count:
        tag = data[pos]
        if tag == 1:
            length = struct.unpack(">H", data[pos + 1:pos + 3])[0]
            if data[pos + 3:pos + 3 + length] == OLD:
                hits.append((pos, length))
            pos += 3 + length
        elif tag in (5, 6):
            pos += 9
            i += 1
        elif tag in (3, 4, 9, 10, 11, 12, 17, 18):
            pos += 5
        elif tag in (7, 8, 16, 19, 20):
            pos += 3
        elif tag == 15:
            pos += 4
        else:
            raise ValueError("unknown constant pool tag %d" % tag)
        i += 1
    if not hits:
        raise ValueError("the mixin has no 'getRenderer' constant: already patched, or not the version this is for")
    assert len(hits) == 1, hits
    pos, length = hits[0]
    return data[:pos] + b"\x01" + struct.pack(">H", len(NEW)) + NEW + data[pos + 3 + length:]


def main(src, dst):
    with zipfile.ZipFile(src) as zin, zipfile.ZipFile(dst, "w", zipfile.ZIP_DEFLATED) as zout:
        done = False
        for item in zin.infolist():
            data = zin.read(item.filename)
            if item.filename == MIXIN:
                data = patch_class(data)
                done = True
            zout.writestr(item, data)
        if not done:
            raise SystemExit("no " + MIXIN + " in " + src)
    print("patched", dst)


if __name__ == "__main__":
    main(*sys.argv[1:3])
