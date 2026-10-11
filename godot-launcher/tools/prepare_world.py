#!/usr/bin/env python3
"""Prepares the launcher's Minecraft room (the Unity project's QCWorld model) for the Godot project.

1. Textures: the model refers to `tex/<name>.png`. Most come from the Unity project (WinterLodge/tex); the others
   (biome-tinted grass and leaves, azalea pots, flowers, beds, chests, ...) are taken from a Minecraft client jar and
   tinted/adapted where Mineways would have.
2. Light: the Unity launcher used baked lightmaps whose second UV set only exists inside Unity, so they cannot be
   reused. Instead the light is computed from the blocks, Minecraft style, and written into the model as vertex
   colours: block light from lanterns and other emitters, sky light where nothing is above, a little ambient light,
   darker sides and undersides, and smooth shading with ambient occlusion at the corners.

    python3 godot-launcher/tools/prepare_world.py [path/to/minecraft/client.jar]
"""
import math
import os
import re
import shutil
import sys
import zipfile
from collections import deque

import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "unity-wrapper", "Assets")
OUT = os.path.join(HERE, "..", "assets", "world")

# ---------------------------------------------------------------- textures

PLAINS_GRASS = (121, 192, 90)
PLAINS_FOLIAGE = (119, 171, 47)
WATER = (63, 118, 228)

# when a name is not in the jar: the block to use instead
ALIASES = {
    "MWO_chest_top": "oak_planks", "MWO_chest_side": "oak_planks", "MWO_chest_front": "oak_planks",
    "MWO_chest_latch": "iron_block", "MWO_double_chest_front_left": "oak_planks",
    "MWO_double_chest_front_right": "oak_planks", "MWO_double_chest_back_left": "oak_planks",
    "MWO_double_chest_back_right": "oak_planks", "MWO_double_chest_top_left": "oak_planks",
    "MWO_double_chest_top_right": "oak_planks",
    "MW_bed_feet_top": "red_wool", "MW_bed_head_top": "red_wool", "MW_bed_feet_end": "oak_planks",
    "MW_bed_feet_side": "oak_planks", "MW_bed_head_end": "oak_planks", "MW_bed_head_side": "oak_planks",
    "MW_decorated_pot_base1": "bricks", "MW_decorated_pot_base2": "bricks", "MW_decorated_pot_base3": "bricks",
    "MW_decorated_pot_base4": "bricks",
}

FALLBACK_COLORS = [
    ("water", (48, 96, 200)), ("grass", (96, 160, 64)), ("leaves", (56, 110, 40)), ("azalea", (80, 130, 60)),
    ("bed", (170, 40, 40)), ("chest", (150, 105, 50)), ("planks", (170, 70, 70)), ("quartz", (236, 230, 222)),
    ("iron", (205, 205, 205)),
]


def tint(im: Image.Image, rgb) -> Image.Image:
    im = im.convert("RGBA")
    r, g, b, a = im.split()
    r = r.point(lambda v: v * rgb[0] // 255)
    g = g.point(lambda v: v * rgb[1] // 255)
    b = b.point(lambda v: v * rgb[2] // 255)
    return Image.merge("RGBA", (r, g, b, a))


def from_jar(jar: zipfile.ZipFile, name: str):
    path = f"assets/minecraft/textures/block/{name}.png"
    if path in jar.namelist():
        return Image.open(jar.open(path)).convert("RGBA")
    return None


def make_texture(jar, name: str):
    """A texture for a name the Unity project does not have, or None."""
    if jar is None:
        return None
    base = name[:-4]  # without .png
    if base == "grass_block_top_y":
        im = from_jar(jar, "grass_block_top")
        return tint(im, PLAINS_GRASS) if im else None
    if base == "grass_block_side_y":
        side, overlay = from_jar(jar, "grass_block_side"), from_jar(jar, "grass_block_side_overlay")
        if side is None:
            return None
        if overlay is not None:
            side = Image.alpha_composite(side, tint(overlay, PLAINS_GRASS))
        return side
    if base.endswith("_leaves_y"):
        im = from_jar(jar, base[:-2])
        return tint(im, PLAINS_FOLIAGE) if im else None
    if base == "water_still_y":
        im = from_jar(jar, "water_still")
        if im is None:
            return None
        frame = tint(im.crop((0, 0, 16, 16)), WATER)
        frame.putalpha(190)
        return frame
    return from_jar(jar, ALIASES.get(base, base))


def fallback_color(name: str):
    for key, rgb in FALLBACK_COLORS:
        if key in name:
            return rgb
    return (128, 128, 128)


def prepare_textures(mtl: str, jar):
    os.makedirs(os.path.join(OUT, "tex"), exist_ok=True)
    found = {}
    for root, _, files in os.walk(os.path.join(ASSETS, "WinterLodge", "tex")):
        for f in files:
            if f.endswith(".png"):
                found.setdefault(f, os.path.join(root, f))
    copied = made = plain = 0
    for name in sorted(set(re.findall(r"map_Kd tex/(\S+)", mtl))):
        target = os.path.join(OUT, "tex", name)
        if name in found:
            shutil.copy(found[name], target)
            copied += 1
            continue
        im = make_texture(jar, name)
        if im is not None:
            im.save(target)
            made += 1
        else:
            Image.new("RGB", (16, 16), fallback_color(name)).save(target)
            plain += 1
    print(f"textures: {copied} from the Unity project, {made} from the Minecraft jar, {plain} plain colours")


# ---------------------------------------------------------------- light

# materials that give light, and how much (0-15)
EMITTERS = {"lantern": 15, "end_rod": 14, "campfire_fire": 15, "campfire_log_lit": 8, "red_candle_lit": 6,
            "large_amethyst_bud": 4}
# materials light passes through (they do not make a block opaque)
TRANSPARENT = {"glass", "glass_pane_top", "iron_bars", "ladder", "chain", "lantern", "dead_bush", "flower_pot",
               "rose_bush_bottom", "allium", "azure_bluet", "red_tulip", "pink_tulip", "oxeye_daisy", "cornflower",
               "lily_of_the_valley", "wither_rose", "torchflower", "sea_pickle", "turtle_egg", "cave_vines",
               "cave_vines_plant", "large_amethyst_bud", "mangrove_propagule", "end_rod", "campfire_fire",
               "water_still", "red_candle", "red_candle_lit", "bamboo_stalk"}

SKY_FACTOR = 0.85
AMBIENT = np.array([0.30, 0.28, 0.30])
SKY_COLOR = np.array([0.82, 0.88, 1.0])
BLOCK_COLOR = np.array([1.0, 0.80, 0.55])
DIRECTION = {(1, 1): 1.0, (1, -1): 0.5, (0, 1): 0.8, (0, -1): 0.8, (2, 1): 0.7, (2, -1): 0.7}  # (axis, sign) -> shade


def parse_obj(path):
    verts, vts, vns, faces = [], [], [], []
    material = None
    order = []   # ("mtl", name) or ("f", index)
    for line in open(path):
        if line.startswith("v "):
            verts.append(tuple(map(float, line.split()[1:4])))
        elif line.startswith("vt "):
            vts.append(line.strip())
        elif line.startswith("vn "):
            vns.append(line.strip())
        elif line.startswith("usemtl"):
            material = line.split()[1]
            order.append(("mtl", material))
        elif line.startswith("f "):
            refs = [p.split("/") for p in line.split()[1:]]
            faces.append((material, refs))
            order.append(("f", len(faces) - 1))
    return verts, vts, vns, faces, order


def face_info(verts, refs):
    """(axis, sign, centre) of an axis aligned face, or (None, None, centre)."""
    pts = np.array([verts[int(r[0]) - 1] for r in refs])
    c = pts.mean(axis=0)
    ext = pts.max(axis=0) - pts.min(axis=0)
    flat = [k for k in range(3) if ext[k] < 1e-4]
    if len(flat) != 1 or len(pts) < 3:
        return None, None, c
    ax = flat[0]
    n = np.cross(pts[1] - pts[0], pts[2] - pts[0])
    sign = 1 if n[ax] > 0 else -1
    return ax, sign, c


def bake_light(obj_path, out_obj):
    verts, vts, vns, faces, order = parse_obj(obj_path)
    vmin = np.floor(np.min(verts, axis=0)).astype(int) - 3
    vmax = np.ceil(np.max(verts, axis=0)).astype(int) + 3
    shape = tuple(vmax - vmin + 1)

    def idx(cell):
        return tuple(int(cell[k]) - int(vmin[k]) for k in range(3))

    def inside(i):
        return all(0 <= i[k] < shape[k] for k in range(3))

    opaque = np.zeros(shape, dtype=bool)
    emit = np.zeros(shape, dtype=np.int8)
    infos = []
    for mat, refs in faces:
        ax, sign, c = face_info(verts, refs)
        infos.append((ax, sign, c))
        if ax is None:
            continue
        behind = [math.floor(c[k]) for k in range(3)]
        p = int(round(c[ax])) if abs(c[ax] - round(c[ax])) < 1e-3 else None
        if p is not None:
            behind[ax] = p - 1 if sign > 0 else p
        else:
            behind[ax] = math.floor(c[ax])
        i = idx(behind)
        if inside(i) and mat not in TRANSPARENT:
            opaque[i] = True
        if inside(i) and mat in EMITTERS:
            emit[i] = max(emit[i], EMITTERS[mat])
    # emitters that are bounded by their own faces only: also mark cells of non-axis aligned emitter faces
    for (mat, refs), (ax, sign, c) in zip(faces, infos):
        if ax is None and mat in EMITTERS:
            i = idx([math.floor(c[k]) for k in range(3)])
            if inside(i):
                emit[i] = max(emit[i], EMITTERS[mat])

    # sky light: open to the sky when no opaque block is above
    sky = np.zeros(shape, dtype=np.int8)
    for x in range(shape[0]):
        for z in range(shape[2]):
            column = opaque[x, :, z]
            top = np.nonzero(column)[0]
            top_y = top.max() if len(top) else -1
            sky[x, top_y + 1:, z] = 15
    neighbours = [(1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)]

    def flood(level):
        queue = deque(tuple(p) for p in np.argwhere(level > 0))
        while queue:
            p = queue.popleft()
            v = level[p]
            if v <= 1:
                continue
            for d in neighbours:
                q = (p[0] + d[0], p[1] + d[1], p[2] + d[2])
                if 0 <= q[0] < shape[0] and 0 <= q[1] < shape[1] and 0 <= q[2] < shape[2] \
                        and not opaque[q] and level[q] < v - 1:
                    level[q] = v - 1
                    queue.append(q)

    block = emit.copy()
    for p in np.argwhere(emit > 0):          # emitters are not opaque for their own light
        opaque[tuple(p)] = False
    flood(sky)
    flood(block)

    def light_at(cell):
        i = idx(cell)
        if not inside(i):
            return np.array([SKY_FACTOR * SKY_COLOR[0], SKY_FACTOR * SKY_COLOR[1], SKY_FACTOR * SKY_COLOR[2]])
        s = (sky[i] / 15.0) ** 1.5 * SKY_FACTOR
        b = (block[i] / 15.0) ** 1.4
        return np.clip(AMBIENT + s * SKY_COLOR + b * BLOCK_COLOR, 0, 1.0)

    def is_opaque(cell):
        i = idx(cell)
        return inside(i) and bool(opaque[i])

    def smooth(front, t1, t2, o1, o2):
        """Light and ambient occlusion for one corner of a face, from the four blocks around that corner."""
        base = light_at(front)
        cells = [front]
        s1 = [front[k] + (o1 if k == t1 else 0) for k in range(3)]
        s2 = [front[k] + (o2 if k == t2 else 0) for k in range(3)]
        cr = [front[k] + (o1 if k == t1 else 0) + (o2 if k == t2 else 0) for k in range(3)]
        o_s1, o_s2, o_c = is_opaque(s1), is_opaque(s2), is_opaque(cr)
        total, count = base.copy(), 1
        for cell, solid in ((s1, o_s1), (s2, o_s2), (cr, o_c)):
            total += base if solid else light_at(cell)
            count += 1
        ao = 0 if (o_s1 and o_s2) else 3 - (int(o_s1) + int(o_s2) + int(o_c))
        return total / count, 1.0 - 0.17 * (3 - ao)

    # write the model: one set of vertices per face, so every corner can have its own light
    lines = []
    out_v = 0
    new_v = []
    new_f = {}
    for n, ((mat, refs), (ax, sign, c)) in enumerate(zip(faces, infos)):
        pts = [np.array(verts[int(r[0]) - 1]) for r in refs]
        colors = []
        if ax is not None:
            p = int(round(c[ax])) if abs(c[ax] - round(c[ax])) < 1e-3 else None
            front = [math.floor(c[k]) for k in range(3)]
            front[ax] = (p if sign > 0 else p - 1) if p is not None else math.floor(c[ax] + 0.5 * sign)
            if is_opaque(front):   # a part-block (slab, stair): take the light from the air around it
                front[ax] += sign
            t1, t2 = [k for k in range(3) if k != ax]
            shade = DIRECTION.get((ax, sign), 0.8)
            for pt in pts:
                o1 = 1 if pt[t1] >= c[t1] else -1
                o2 = 1 if pt[t2] >= c[t2] else -1
                lightc, aof = smooth(front, t1, t2, o1, o2)
                colors.append(np.clip(lightc * aof * shade * 1.3, 0, 1))
            if mat in EMITTERS:   # light sources glow at full brightness
                colors = [np.ones(3) for _ in pts]
        else:
            cell = [math.floor(c[k]) for k in range(3)]
            lc = np.ones(3) if mat in EMITTERS else np.clip(light_at(cell) * 0.9 * 1.3, 0, 1)
            colors = [lc for _ in pts]
        new_f[n] = []
        for pt, col in zip(pts, colors):
            new_v.append(f"v {pt[0]:.4f} {pt[1]:.4f} {pt[2]:.4f} {col[0]:.3f} {col[1]:.3f} {col[2]:.3f}")
            out_v += 1
            new_f[n].append(out_v)
    with open(out_obj, "w") as f:
        f.write("# QuestCraft room with baked vertex light (see tools/prepare_world.py)\nmtllib QCWorld.mtl\n")
        f.write("\n".join(new_v) + "\n")
        f.write("\n".join(vts) + "\n")
        f.write("\n".join(vns) + "\n")
        for kind, value in order:
            if kind == "mtl":
                f.write(f"usemtl {value}\n")
            else:
                refs = faces[value][1]
                parts = []
                for new_index, r in zip(new_f[value], refs):
                    parts.append("/".join([str(new_index)] + r[1:]))
                f.write("f " + " ".join(parts) + "\n")
    print(f"light baked: {len(faces)} faces, {out_v} vertices, {int((block > 0).sum())} lit cells, "
          f"{int(emit.astype(bool).sum())} emitters")


def main() -> None:
    os.makedirs(os.path.join(OUT, "tex"), exist_ok=True)
    src = os.path.join(ASSETS, "QCWorld")
    mtl = open(os.path.join(src, "QCWorld.mtl")).read()
    open(os.path.join(OUT, "QCWorld.mtl"), "w").write(mtl)
    jar = None
    jar_path = sys.argv[1] if len(sys.argv) > 1 else None
    if jar_path and os.path.exists(jar_path):
        jar = zipfile.ZipFile(jar_path)
    prepare_textures(mtl, jar)
    bake_light(os.path.join(src, "QCWorld.obj"), os.path.join(OUT, "QCWorld.obj"))


if __name__ == "__main__":
    main()
