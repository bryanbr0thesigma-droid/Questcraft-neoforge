#!/usr/bin/env python3
"""Copies the menu art of the Unity launcher (Resources/UI) into the Godot project, scaled down for the headset.

    python3 godot-launcher/tools/prepare_ui.py
"""
import os
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = os.path.join(HERE, "..", "..", "unity-wrapper", "Assets", "Resources")
OUT = os.path.join(HERE, "..", "assets", "ui")

# (file in Resources, name here, longest side)
FILES = [
    ("Pojav_x_Vivecraft.png", "logo.png", 1100),
    ("UI/Background.png", "background.png", 1267),
    ("UI/MainButton.png", "button.png", 298),
    ("UI/gear.png", "gear.png", 128),
    ("UI/notebook.png", "notebook.png", 128),
    ("UI/ExitButton.png", "exit.png", 128),
    ("UI/FolderIcon.png", "folder.png", 128),
    ("UI/SearchIcon.png", "search.png", 128),
    ("UI/UpArrow.png", "up.png", 128),
    ("UI/BackButton.png", "back.png", 128),
    ("UI/TrashBin.png", "trash.png", 128),
    ("UI/Reload.png", "reload.png", 128),
]

os.makedirs(OUT, exist_ok=True)
for source, name, size in FILES:
    im = Image.open(os.path.join(SRC, source)).convert("RGBA")
    if max(im.size) > size:
        scale = size / max(im.size)
        im = im.resize((round(im.width * scale), round(im.height * scale)), Image.LANCZOS)
    im.save(os.path.join(OUT, name), optimize=True)
    print(name, im.size)
