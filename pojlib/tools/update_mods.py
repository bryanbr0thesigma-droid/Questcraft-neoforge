#!/usr/bin/env python3
"""Regenerates mods-neoforge.json / devmods-neoforge.json / supportedVersions.json from Modrinth
and supportedVersions-quilt.json (Quilt shares the Fabric mod list, so it only needs a version list).

For every Minecraft version and mod it takes the newest NeoForge build (releases before betas).
Mods that have no NeoForge build for a version are left out and reported.

    python3 tools/update_mods.py            # writes the files next to this tools/ directory
"""
import json
import os
import shutil
import sys
import urllib.parse
import urllib.request

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..")
UA = {"User-Agent": "questcraft-neoforge-port/1.0"}

# NeoForge exists from 1.20.2; these are the versions QuestCraft supports that have a NeoForge Vivecraft build (see vivecraft-builder/)
VERSIONS = ["1.21.11", "1.21.8", "1.21.5", "1.21.4", "1.21.1", "1.20.6", "1.20.4"]

# Versions Quilt was boot-tested on with the Fabric mod set (every version of QuestCraft's Fabric list). Quilt loads
# Fabric mods, so it has no mod list of its own, only this list of versions.
QUILT_VERSIONS = ["1.21.11", "1.21.10", "1.21.8", "1.21.5", "1.21.4", "1.21.1", "1.20.6", "1.20.4", "1.20.1", "1.19.4",
                  "1.19.2"]

# What QuestCraft's Fabric lists (Pojlib QuestCraft-6.0.1) ship per Minecraft version, by launcher slug.
FABRIC_SETS = {
    "1.21.11": ["Sodium", "Lithium", "BadOptimizations", "zFastNoise", "Modernfix-mVUS", "Better-Biome-Blend",
                "EntityCulling", "Simple-Voice-Chat"],
    "1.21.8": ["Sodium", "Cloth-Config", "Ferrite-Core", "MoreCulling", "Lithium", "ImmediatelyFast", "Krypton",
               "Simple-Voice-Chat", "BadOptimizations", "bbrb", "EntityCulling"],
    "1.21.5": ["Sodium", "Cloth-Config", "Ferrite-Core", "MoreCulling", "Lithium", "ImmediatelyFast", "Krypton",
               "Simple-Voice-Chat", "BadOptimizations", "bbrb", "EntityCulling"],
    "1.21.4": ["Sodium", "Cloth-Config", "Ferrite-Core", "ModernFix", "MoreCulling", "Lithium", "ImmediatelyFast",
               "Krypton", "Simple-Voice-Chat", "BadOptimizations", "bbrb", "EntityCulling"],
    "1.21.1": ["Sodium", "ModernFix", "Noisium", "Lithium", "MoreCulling", "Simple-Voice-Chat", "Cloth-Config",
               "BadOptimizations", "Ferrite-Core", "ImmediatelyFast", "bbrb", "EntityCulling"],
    "1.20.6": ["Sodium", "ModernFix", "Noisium", "Lithium", "MoreCulling", "Cloth-Config", "Simple-Voice-Chat",
               "EntityCulling"],
    "1.20.4": ["Sodium", "Cloth-Config", "ImmediatelyFast", "Simple-Voice-Chat", "ModernFix", "Noisium", "Lithium",
               "EntityCulling", "Ferrite-Core", "Krypton"],
    # Fabric-Language-Kotlin is left out on purpose, it only exists for Fabric
}

# launcher slug -> Modrinth projects (id or slug) in order of preference, the first one with a build wins
MODRINTH = {
    "Sodium": ["AANobbMI", "embeddium"],  # Sodium has no NeoForge build for 1.20.4, Embeddium does
    "Cloth-Config": ["9s6osm5g"],
    "Ferrite-Core": ["uXXizFIs"],
    "MoreCulling": ["51shyZVL"],
    "Lithium": ["gvQqBUqZ"],
    "ImmediatelyFast": ["5ZwdcRci"],
    "Krypton": ["fQEb0iXm"],
    "Simple-Voice-Chat": ["9eGKb6K1"],
    "BadOptimizations": ["g96Z4WVZ"],
    "bbrb": ["Xh8hkQmD"],
    "EntityCulling": ["NNAgCjsB"],
    "ModernFix": ["nmDcB62a"],
    "Noisium": ["KuNKN7d2"],
    "zFastNoise": ["OnlVIpq5"],
    "Modernfix-mVUS": ["TjSm1wrD"],  # a ModernFix variant, the fabric list ships it for 1.21.11
    "Better-Biome-Blend": ["Rs6c7WyL"],
}

# Fabric API can not exist on NeoForge; a multi-loader jar that asks for it asks for it on its Fabric side only
IGNORED_REQUIREMENTS = {"P7dR8mSH"}

# QuestCraft's Vivecraft fork (QuestCraftPlusPlus/VivecraftMod, OpenXR branches), its NeoForge module built by
# .github/workflows/vivecraft.yml. Upstream only publishes the Fabric jar.
# The jars are committed to pojlib/src/main/assets/vivecraft-jars/neoforge/ (bundled in the app as .jar.bin) (copied from vivecraft-builder/dist/ after a build) and linked by
# their raw URL, which works for anyone who can read the repository without signing in, that is, once it is public.
VIVECRAFT_URL = ("https://raw.githubusercontent.com/bryanbr0thesigma-droid/Questcraft-neoforge/"
                 "claude/fervent-mayer-8idkzr/pojlib/src/main/assets/vivecraft-jars/neoforge/vivecraft-neoforge-{mc}.jar.bin")


# ModernFix for 1.21.4 and 1.21.11 is the published jar with one string changed, see patch_modernfix.py: since 1.21.2
# EntityRenderDispatcher has two getRenderer methods and the published mixin names neither exactly, so the game does
# not start. The patched jars are bundled in the app. (mc, slug) -> (the version to show, the file in bundled-mods)
PATCHED = {
    ("1.21.11", "Modernfix-mVUS"): ("5.21.0-qc1", "modernfix-neoforge-5.21.0-qc1.jar.bin"),
    ("1.21.4", "ModernFix"): ("5.20.3+mc1.21.4-qc1", "modernfix-neoforge-5.20.3-mc1.21.4-qc1.jar.bin"),
}
PATCHED_URL = ("https://raw.githubusercontent.com/bryanbr0thesigma-droid/Questcraft-neoforge/"
               "claude/fervent-mayer-8idkzr/pojlib/src/main/assets/bundled-mods/neoforge/{file}")


# Raise this when the bundled jar of a version changes: instances only fetch a mod again when its version differs.
# 1.21.11 1.1: eye poses relative to the head (stereo view was wrong before); 1.2: OpenXR input reaches the game (processInputs)
VIVECRAFT_VERSIONS = {"1.21.11": "1.2"}


def newest(slug, mc):
    query = urllib.parse.urlencode({"loaders": json.dumps(["neoforge"]), "game_versions": json.dumps([mc])})
    request = urllib.request.Request(f"https://api.modrinth.com/v2/project/{slug}/version?{query}", headers=UA)
    versions = json.load(urllib.request.urlopen(request, timeout=30))
    rank = {"release": 0, "beta": 1, "alpha": 2}
    # stable sort: newest first within a type; a jar built for NeoForge alone beats a multi-loader jar,
    # whose "required" list (for example Fabric API) is meant for its other loaders
    versions.sort(key=lambda v: (rank.get(v["version_type"], 3), len(v["loaders"]) > 1))
    if not versions:
        return None
    version = versions[0]
    files = [f for f in version["files"] if f.get("primary")] or version["files"]
    required = [d["project_id"] for d in version["dependencies"]
                if d["dependency_type"] == "required" and d.get("project_id")]
    return {"version": version["version_number"], "download_link": files[0]["url"],
            "project_id": version["project_id"], "required": required}


def main():
    result = {"versions": []}
    for mc in VERSIONS:
        entry = {"name": mc,
                 "coreMods": [{"slug": "Vivecraft", "version": VIVECRAFT_VERSIONS.get(mc, "1.0"),
                               "download_link": VIVECRAFT_URL.format(mc=mc)}],
                 "defaultMods": []}
        for slug in FABRIC_SETS[mc]:
            for candidate in MODRINTH[slug]:
                build = newest(candidate, mc)
                if build:
                    if (mc, slug) in PATCHED:
                        version, file = PATCHED[(mc, slug)]
                        build = {**build, "version": version, "download_link": PATCHED_URL.format(file=file)}
                    entry["defaultMods"].append({"slug": slug, **build})
                    break
            else:
                print(f"{mc}: no NeoForge build of {slug}, left out", file=sys.stderr)
        # NeoForge refuses to start when a required mod is missing, so check what the picks ask for
        have = {m["project_id"] for m in entry["defaultMods"]}
        for mod in entry["defaultMods"]:
            for dep in mod["required"]:
                if dep not in have and dep not in IGNORED_REQUIREMENTS:
                    print(f"{mc}: {mod['slug']} requires Modrinth project {dep} which is not in the list",
                          file=sys.stderr)
        for mod in entry["defaultMods"]:  # only needed for the check
            del mod["project_id"], mod["required"]
        result["versions"].append(entry)

    for name in ("mods-neoforge.json", "devmods-neoforge.json"):
        with open(os.path.join(OUT, name), "w") as f:
            json.dump(result, f, indent="\t")
            f.write("\n")
    with open(os.path.join(OUT, "supportedVersions.json"), "w") as f:
        json.dump({"supportedVersions": VERSIONS}, f, indent=2)
        f.write("\n")
    # the app carries copies of the lists, used when the repository cannot be read (see APIHandler, MinecraftInstances)
    bundled = os.path.join(OUT, "src", "main", "assets", "loaders")
    os.makedirs(bundled, exist_ok=True)
    with open(os.path.join(OUT, "supportedVersions-quilt.json"), "w") as f:
        json.dump({"supportedVersions": QUILT_VERSIONS}, f, indent=2)
        f.write("\n")
    for src, dst in (("mods-neoforge.json", "mods-neoforge.json"), ("devmods-neoforge.json", "devmods-neoforge.json"),
                     ("supportedVersions.json", "supportedVersions-neoforge.json"),
                     ("supportedVersions-quilt.json", "supportedVersions-quilt.json")):
        shutil.copy(os.path.join(OUT, src), os.path.join(bundled, dst))


if __name__ == "__main__":
    main()
