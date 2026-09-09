"""Generate the Observatory blueprints into the Voyager Structurize pack.

resources/blueprints/voyager/voyager/observatory/<look>1-5.blueprint, for all four looks.

Same shape as build_pack.py, with the Observatory's own shared box. Run check_observatory.py
first - this writes the files, it does not judge them.
"""
import os
import sys

import build_pack as bp
import observatory as obs
import paths
import voxel
from floatcheck import attachment, floating
from walkcheck import standable

PACK_NAME = "Voyager"
PACK_DIR = paths.res("blueprints", "voyager", "voyager")
FOLDER = "observatory"
BUILDING_TYPE = "voyager:observatory"
BE_TYPE = "voyager:colonybuilding"
# The shared footprint: every level of every look, aligned on its hut block, fits inside this.
# The build tool outline never changes between upgrades, so an upgrade never appears to move the
# building - the rule Marko set for the Departure Point.
BOX = ((-11, 0, -12), (11, 25, 12))

# Blocks from the mods this building depends on. Exposure and Exposure: Space because the
# Observatory does not exist without them; Domum Ornamentum because MineColonies requires it.
EXTRA_KNOWN = {
    "voyager:blockhutobservatory",
    "exposure:lightroom", "exposure:photograph_frame_small", "exposure:photograph_frame_medium",
    "exposure:photograph_frame_large",
    "exposure_space:analyzer", "exposure_space:night_analyzer",
    "domum_ornamentum:beige_stone_bricks", "domum_ornamentum:cream_stone_bricks",
    "domum_ornamentum:brown_stone_bricks", "domum_ornamentum:brown_bricks",
    "domum_ornamentum:sand_stone_bricks", "domum_ornamentum:beige_bricks",
    "domum_ornamentum:black_brick_extra", "domum_ornamentum:blue_brick_extra",
    "domum_ornamentum:blockbarreldeco_standing", "domum_ornamentum:architectscutter",
    "domum_ornamentum:vanilla_stairs_compat", "domum_ornamentum:vanilla_slab_compat",
    "domum_ornamentum:vanilla_wall_compat", "domum_ornamentum:squarepillar",
    "domum_ornamentum:shingle", "domum_ornamentum:framed",
    "domum_ornamentum:gray_brick_extra", "domum_ornamentum:light_blue_brick_extra",
}
REQUIRED_MODS = ("structurize", "minecolonies", "domum_ornamentum", "exposure", "exposure_space", "voyager")


def pad_ground(s):
    """Everything inside the shared footprint the design does not touch is left alone: the builder
    clears what is above the pad but does no landscaping."""
    (bx0, _, bz0), (bx1, _, bz1) = BOX
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            if (x, 0, z) not in s.blocks:
                s.set(x, 0, z, bp.KEEP)


def walkable_hut(s):
    """A citizen has to be able to stand next to the hut block."""
    x, y, z = s.anchor
    stands = [(x + dx, y, z + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))
              if standable(s, x + dx, y, z + dz)]
    if not stands:
        return False, f"nowhere to stand next to the hut block {s.anchor}"
    return True, f"stand {stands[0]}"


def one(look, lv, known):
    s = bp.aligned(lambda l, _look=look: obs.build(_look, l), lv)
    ok = True
    missing = bp.unknown_blocks(s, known)
    if missing:
        print("!! blocks that do not exist:", s.name, missing)
        ok = False
    loose = floating(s)
    if loose:
        print("!! floating:", s.name, loose[:4])
        ok = False
    unattached = attachment(s)
    if unattached:
        print("!! nothing to attach to:", s.name, unattached[:3])
        ok = False
    walk_ok, why = walkable_hut(s)
    if not walk_ok:
        print("!! walk:", s.name, why)
        ok = False
    mats = len(s.materials)
    pad_ground(s)
    file_name = f"{s.name}.blueprint"
    path = os.path.join(PACK_DIR, FOLDER, file_name)
    s.to_blueprint(path, file_name, PACK_NAME, f"{FOLDER}/{file_name}", BUILDING_TYPE,
                   required_mods=REQUIRED_MODS, be_type=BE_TYPE, box=BOX)
    back = voxel.load_blueprint(path)
    (x0, y0, z0), _ = BOX
    norm = {(x - x0, y - y0, z - z0): voxel.parse_state(b) for (x, y, z), b in s.blocks.items()}
    same = norm == {q: voxel.parse_state(b) for q, b in back.blocks.items()}
    anchor_ok = back.anchor == (s.anchor[0] - x0, s.anchor[1] - y0, s.anchor[2] - z0)
    print(f"{file_name:26s} anchor {s.anchor!s:14s} {len(s.blocks):5d} blocks  {mats:3d} DO  "
          f"{os.path.getsize(path):6d} B  roundtrip={'ok' if same else 'MISMATCH'} "
          f"anchor={'ok' if anchor_ok else 'BAD'}  {why}")
    return ok and same and anchor_ok


def main():
    os.makedirs(os.path.join(PACK_DIR, FOLDER), exist_ok=True)
    known = bp.vanilla_blocks() | EXTRA_KNOWN
    ok = True
    for look in obs.LOOKS:
        for lv in range(1, 6):
            ok = one(look, lv, known) and ok
    print("ALL OK" if ok else "PROBLEMS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
