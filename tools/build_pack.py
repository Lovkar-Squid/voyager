"""Generate the Structurize pack shipped inside the Voyager jar.

resources/blueprints/voyager/voyager/pack.json
resources/blueprints/voyager/voyager/voyager.png        (pack icon)
resources/blueprints/voyager/voyager/expedition/launchpad1-5.blueprint
resources/blueprints/voyager/voyager/expedition/endgate1-5.blueprint
"""
import json
import os
import sys

import designs
import paths
import voxel
from floatcheck import attachment, floating
from walkcheck import reachable, standable

PACK_NAME = "Voyager"
PACK_DIR = paths.res("blueprints", "voyager", "voyager")
FOLDER = "expedition"
BUILDING_TYPE = "voyager:voyager"
BE_TYPE = "voyager:colonybuilding"
# One footprint for every level and both looks (the union of the ten, anchors aligned): the
# build tool outline never changes between upgrades and the builder clears the whole area
# from level 1. Levels 1-4 are shifted so their hut block sits exactly where level 5's does.
BOX = ((-15, 0, -16), (14, 43, 11))
GROUND = "structurize:blocksolidsubstitution"     # "a solid block of this terrain" (fills, costs materials)
KEEP = "structurize:blocksubstitution"            # "leave whatever is there" (no work, no materials)


def aligned(fn, level):
    """Level `level` of a look, moved so its anchor coincides with level 5's anchor."""
    s = fn(level)
    a5 = fn(5).anchor
    dx, dy, dz = a5[0] - s.anchor[0], 0, a5[2] - s.anchor[2]
    return s.translated(dx, dy, dz) if (dx or dz) else s


def pad_ground(s):
    """Around the design, inside the shared footprint: everything from the pad's surface level up
    is air (the builder clears trees and hills so the outline is always free), the ground itself is
    left alone (no landscaping: no filling of dips, no hauling of dirt - that is what made the
    first survival builds crawl). The design's own foundation layer is part of the design."""
    (bx0, by0, bz0), (bx1, by1, bz1) = BOX
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            if (x, 0, z) not in s.blocks:
                s.set(x, 0, z, KEEP)


MC_ASSETS = paths.lib(os.environ.get("MC_ASSETS_JAR", "mc-extra.jar"))   # client assets jar (blockstates)
KNOWN_MOD_BLOCKS = {"structurize:blocksolidsubstitution", "structurize:blocksubstitution", "voyager:blockhutvoyager", "minecolonies:blockminecoloniesrack"}


def vanilla_blocks():
    """Every block id that has a blockstate file in the client assets - a block that is not in
    here (polished_andesite_wall, say) silently becomes air when Structurize loads the blueprint."""
    import re
    import zipfile
    ids = set()
    with zipfile.ZipFile(MC_ASSETS) as jar:
        for name in jar.namelist():
            m = re.match(r"assets/minecraft/blockstates/([a-z0-9_]+)\.json$", name)
            if m:
                ids.add("minecraft:" + m.group(1))
    return ids


def unknown_blocks(s, known):
    return sorted({voxel.parse_state(b)[0] for b in s.blocks.values() if b is not None
                   and voxel.parse_state(b)[0] not in known and voxel.parse_state(b)[0] not in KNOWN_MOD_BLOCKS})


def walkable_departure(s):
    """MineColonies sends the Voyager to a walkable cell right next to the departure block (same
    height, not the block itself) - so one must exist, and the hut must be reachable from it."""
    deps = [p for p, tags in s.tags.items() if "departure" in tags]
    if len(deps) != 1:
        return False, f"{len(deps)} departure tags"
    x, y, z = deps[0]
    stands = [(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)) if standable(s, x + dx, y, z + dz)]
    if not stands:
        return False, f"no walkable cell next to the departure point {deps[0]}"
    for stand in stands:
        ok, _ = reachable(s, stand, s.anchor)
        if ok:
            return True, f"departure {deps[0]} reachable via {stand}"
    return False, f"hut {s.anchor} not reachable from the departure point {deps[0]}"


def main():
    os.makedirs(os.path.join(PACK_DIR, FOLDER), exist_ok=True)
    with open(os.path.join(PACK_DIR, "pack.json"), "w") as f:
        json.dump({
            "icon": "voyager.png",
            "name": PACK_NAME,
            "authors": ["Lovkar", "Claude"],
            "desc": "Buildings for the Voyager professions. The Departure Point comes as a Launchpad with a "
                    "rocket or as an End Gate; the Observatory comes as a Copper Dome, a Stargazer's Keep, "
                    "a Sand Court, a Skyward Station or an Aperture Array. Each is the same building - pick the look you "
                    "like. Works with every colony style.",
            "mods": ["structurize", "minecolonies", "voyager"],
            "version": "1",
            "pack-format": "1",
        }, f, indent=2)
    ok = True
    known = vanilla_blocks()
    for lv in range(1, 6):
        for fn in (designs.launchpad, designs.endgate):
            s = aligned(fn, lv)
            missing = unknown_blocks(s, known)
            if missing:
                print("!! blocks that do not exist in this Minecraft on", s.name, missing)
                ok = False
            if fn is designs.launchpad and floating(s):
                print("!! floating blocks on", s.name)
                ok = False
            if attachment(s):
                print("!! unattached blocks on", s.name, attachment(s)[:3])
                ok = False
            if fn is designs.launchpad:
                # the pad must stand on its own while the rocket is away
                grounded = designs.launchpad(lv)
                for pos in [q for q, tags in grounded.tags.items() if "rocket" in tags]:
                    grounded.blocks.pop(pos, None)
                loose = floating(grounded)
                if loose:
                    print("!! floats once the rocket is gone on", s.name, loose[:5])
                    ok = False
                overlap = [q for q, tags in s.tags.items() if "rocket" in tags and q in s.blocks
                           and voxel.parse_state(s.blocks[q])[0] in ("minecraft:iron_bars", "minecraft:ladder", "minecraft:chain")]
                if overlap:
                    print("!! rocket overlaps other structures on", s.name, overlap[:5])
                    ok = False
            pad_ground(s)
            walk_ok, why = walkable_departure(s)
            if not walk_ok:
                print("!! walk:", s.name, why)
                ok = False
            rocket = sum(1 for tags in s.tags.values() if "rocket" in tags)
            file_name = f"{s.name}.blueprint"
            path = os.path.join(PACK_DIR, FOLDER, file_name)
            s.to_blueprint(path, file_name, PACK_NAME, f"{FOLDER}/{file_name}", BUILDING_TYPE,
                           required_mods=("structurize", "minecolonies", "voyager"), be_type=BE_TYPE, box=BOX)
            back = voxel.load_blueprint(path)
            (x0, y0, z0), _ = BOX
            norm = {(x - x0, y - y0, z - z0): voxel.parse_state(b) for (x, y, z), b in s.blocks.items()}
            same = norm == {p: voxel.parse_state(b) for p, b in back.blocks.items()}
            anchor_ok = back.anchor == (s.anchor[0] - x0, s.anchor[1] - y0, s.anchor[2] - z0)
            print(f"{file_name:22s} anchor {s.anchor!s:14s} {len(s.blocks):5d} blocks  {os.path.getsize(path):6d} B  "
                  f"roundtrip={'ok' if same else 'MISMATCH'} anchor={'ok' if anchor_ok else 'BAD'} rocket={rocket} {why}")
            ok = ok and same and anchor_ok
    print("ALL OK" if ok else "PROBLEMS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
