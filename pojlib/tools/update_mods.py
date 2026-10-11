#!/usr/bin/env python3
"""Regenerates mods-neoforge.json / devmods-neoforge.json / supportedVersions.json from Modrinth.

For every Minecraft version and mod it takes the newest NeoForge build (releases before betas).
Mods that have no NeoForge build for a version are left out and reported.

    python3 tools/update_mods.py            # writes the files next to this tools/ directory
"""
import json
import os
import sys
import urllib.parse
import urllib.request

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
UA = {"User-Agent": "questcraft-neoforge-port/1.0"}

# Same Minecraft versions as the NeoForge builds of Vivecraft (see vivecraft-builder/)
VERSIONS = ["1.21.5", "1.21.4", "1.21.1", "1.20.4"]

# slug used by the launcher -> candidates on Modrinth in order of preference (first one that has a build wins)
DEFAULT_MODS = {
    "Sodium": ["sodium", "embeddium"],  # Sodium has no NeoForge build for 1.20.4, Embeddium does
    "Cloth-Config": ["cloth-config"],
    "Server-Core": ["servercore"],
    "ImmediatelyFast": ["immediatelyfast"],
    "Simple-Voice-Chat": ["simple-voice-chat"],
    "Modern-Fix": ["modernfix"],
    "Noisium": ["noisium"],
    "Lithium": ["lithium"],
    "EntityCulling": ["entityculling"],
    "Ferrite-Core": ["ferrite-core"],
}

# Vivecraft with the QuestCraft patches, built by .github/workflows/vivecraft.yml
VIVECRAFT_URL = ("https://github.com/bryanbr0thesigma-droid/Questcraft-neoforge/releases/download/"
                 "vivecraft-{mc}/vivecraft-neoforge-{mc}.jar")


def newest(slug, mc):
    query = urllib.parse.urlencode({"loaders": json.dumps(["neoforge"]), "game_versions": json.dumps([mc])})
    request = urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}/version?{query}", headers=UA)
    versions = json.load(urllib.request.urlopen(request, timeout=30))
    rank = {"release": 0, "beta": 1, "alpha": 2}
    versions.sort(key=lambda v: rank.get(v["version_type"], 3))  # stable: newest first within a type
    if not versions:
        return None
    version = versions[0]
    files = [f for f in version["files"] if f.get("primary")] or version["files"]
    return {"version": version["version_number"], "download_link": files[0]["url"]}


def main():
    result = {"versions": []}
    for mc in VERSIONS:
        entry = {"name": mc,
                 "coreMods": [{"slug": "Vivecraft", "version": "1.0",
                               "download_link": VIVECRAFT_URL.format(mc=mc)}],
                 "defaultMods": []}
        for slug, candidates in DEFAULT_MODS.items():
            for candidate in candidates:
                build = newest(candidate, mc)
                if build:
                    entry["defaultMods"].append({"slug": slug, **build})
                    break
            else:
                print(f"{mc}: no NeoForge build of {slug}, left out", file=sys.stderr)
        result["versions"].append(entry)

    for name in ("mods-neoforge.json", "devmods-neoforge.json"):
        with open(os.path.join(OUT, name), "w") as f:
            json.dump(result, f, indent="\t")
            f.write("\n")
    with open(os.path.join(OUT, "supportedVersions.json"), "w") as f:
        json.dump({"supportedVersions": VERSIONS}, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
