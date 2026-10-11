#!/usr/bin/env python3
"""Copies the launcher's Minecraft room (the Unity project's QCWorld model) into the Godot project.

The model is a Mineways export that refers to `tex/<name>.png`. The Unity project keeps most of those textures in
WinterLodge/tex; the ones it does not have get a plain block colour so nothing is left grey.

    python3 godot-launcher/tools/prepare_world.py
"""
import os
import re
import shutil
import sys

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
ASSETS = os.path.join(HERE, "..", "..", "unity-wrapper", "Assets")
OUT = os.path.join(HERE, "..", "assets", "world")

COLORS = [  # (keyword, rgb) first match wins
    ("water", (48, 96, 200)), ("grass", (96, 160, 64)), ("leaves", (56, 110, 40)), ("azalea", (80, 130, 60)),
    ("bed", (170, 40, 40)), ("chest", (150, 105, 50)), ("planks", (170, 70, 70)), ("quartz", (236, 230, 222)),
    ("iron", (205, 205, 205)), ("furnace", (96, 96, 98)), ("hopper", (60, 60, 64)), ("lever", (120, 90, 60)),
    ("loom", (140, 100, 60)), ("pot", (160, 90, 60)), ("candle", (190, 40, 40)), ("allium", (180, 110, 220)),
    ("bluet", (225, 230, 240)), ("oxeye", (240, 240, 230)), ("rose", (170, 30, 40)), ("torchflower", (230, 140, 40)),
    ("wither", (40, 30, 40)),
]


def color_for(name: str):
    for key, rgb in COLORS:
        if key in name:
            return rgb
    return (128, 128, 128)


def main() -> None:
    os.makedirs(os.path.join(OUT, "tex"), exist_ok=True)
    src_dir = os.path.join(ASSETS, "QCWorld")
    shutil.copy(os.path.join(src_dir, "QCWorld.obj"), OUT)
    mtl = open(os.path.join(src_dir, "QCWorld.mtl")).read()
    open(os.path.join(OUT, "QCWorld.mtl"), "w").write(mtl)

    found = {}
    for root, _, files in os.walk(os.path.join(ASSETS, "WinterLodge", "tex")):
        for f in files:
            if f.endswith(".png"):
                found.setdefault(f, os.path.join(root, f))

    copied = made = 0
    for name in sorted(set(re.findall(r"map_Kd tex/(\S+)", mtl))):
        target = os.path.join(OUT, "tex", name)
        if name in found:
            shutil.copy(found[name], target)
            copied += 1
        else:
            Image.new("RGB", (16, 16), color_for(name)).save(target)
            made += 1
    print(f"textures: {copied} copied, {made} generated as plain colours")


if __name__ == "__main__":
    sys.exit(main())
