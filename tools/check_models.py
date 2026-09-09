"""Every texture our block models name has to exist, or the block comes out magenta and black.

Minecraft says nothing when a model points at a texture that is not there - you find out by
looking at the block in game, which is how the Observatory's hut block shipped with a missing
desk leg and a missing telescope. Two whole families of vanilla textures do not exist even though
the blocks do:

  * walls, fences and most "shaped" blocks have no texture of their own - a wall is drawn with the
    texture of the block it is cut from (deepslate_brick_wall -> deepslate_bricks);
  * every waxed copper block reuses the unwaxed texture (waxed_exposed_copper -> exposed_copper).

Run over resources/assets/**/models/**/*.json, resolving vanilla textures against the client
assets jar and ours against resources/.
"""
import glob
import json
import os
import re
import sys
import zipfile

import paths

MC_ASSETS = paths.lib(os.environ.get("MC_ASSETS_JAR", "mc-extra.jar"))
RES = paths.res()


def vanilla_textures():
    with zipfile.ZipFile(MC_ASSETS) as jar:
        return {n[len("assets/minecraft/textures/"):-len(".png")]
                for n in jar.namelist()
                if n.startswith("assets/minecraft/textures/") and n.endswith(".png")}


def ours():
    found = set()
    for path in glob.glob(os.path.join(RES, "assets", "*", "textures", "**", "*.png"), recursive=True):
        rel = os.path.relpath(path, os.path.join(RES, "assets"))
        ns, _, sub = rel.partition(os.sep + "textures" + os.sep)
        found.add(f"{ns}:{sub[:-len('.png')]}".replace(os.sep, "/"))
    return found


def main():
    vanilla = vanilla_textures()
    mine = ours()
    bad = 0
    checked = 0
    for path in sorted(glob.glob(os.path.join(RES, "assets", "**", "models", "**", "*.json"), recursive=True)):
        with open(path) as f:
            try:
                model = json.load(f)
            except json.JSONDecodeError as e:
                print(f"!! {os.path.relpath(path, RES)}: not valid JSON: {e}")
                bad += 1
                continue
        for key, value in (model.get("textures") or {}).items():
            if not isinstance(value, str) or value.startswith("#"):
                continue          # a reference to another slot, resolved by the parent
            checked += 1
            ns, _, sub = value.partition(":")
            if not _:
                ns, sub = "minecraft", value
            known = vanilla if ns == "minecraft" else mine
            wanted = sub if ns == "minecraft" else f"{ns}:{sub}"
            if wanted not in known:
                print(f"!! {os.path.relpath(path, RES)}: \"{key}\": \"{value}\" does not exist")
                bad += 1
    print(f"{checked} texture reference(s) checked, {bad} missing")
    return 1 if bad else 0


if __name__ == "__main__":
    sys.exit(main())
