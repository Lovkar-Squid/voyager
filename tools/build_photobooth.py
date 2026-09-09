"""Generate the Photo Booth blueprints, and refuse to write a broken one.

Same gates as the Observatory: every block id must exist, nothing may float or hang off nothing,
every Domum Ornamentum block must carry its material, a citizen must be able to stand next to the
hut block, the bed must be whole and indoors, and every level must fit the shared footprint.
"""
import os
import sys

import build_pack as bp
import build_observatory as obs_pack
import photobooth as pb
import voxel
from floatcheck import attachment, floating
from walkcheck import standable

PACK_NAME = "Voyager"
PACK_DIR = bp.paths.res("blueprints", "voyager", "voyager") if hasattr(bp, "paths") else None
FOLDER = "photobooth"
BUILDING_TYPE = "voyager:photobooth"
BE_TYPE = "voyager:colonybuilding"
BOX = pb.BOX
REQUIRED_MODS = ("structurize", "minecolonies", "domum_ornamentum", "exposure", "voyager")


def pad_ground(s):
    (bx0, _, bz0), (bx1, _, bz1) = BOX
    for x in range(bx0, bx1 + 1):
        for z in range(bz0, bz1 + 1):
            if (x, 0, z) not in s.blocks:
                s.set(x, 0, z, bp.KEEP)


def one(look, lv, known, pack_dir):
    s = bp.aligned(lambda l, _look=look: pb.build(_look, l), lv)
    ok = True
    missing = bp.unknown_blocks(s, known)
    if missing:
        print("!! blocks that do not exist:", s.name, sorted(missing))
        ok = False
    loose = floating(s)
    if loose:
        print("!! floating:", s.name, loose[:4])
        ok = False
    unattached = attachment(s)
    if unattached:
        print("!! nothing to attach to:", s.name, unattached[:3])
        ok = False
    bare = sorted(pos for pos, b in s.blocks.items()
                  if voxel.parse_state(b)[0] in voxel.DO_SLOTS and pos not in s.materials)
    if bare:
        print("!! Domum Ornamentum block with no material:", s.name, bare[:3])
        ok = False
    ax, ay, az = s.anchor
    if not any(standable(s, ax + dx, ay, az + dz) for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
        print("!! nowhere to stand next to the hut block:", s.name)
        ok = False
    feet = [p for p, b in s.blocks.items() if "part=foot" in b]
    heads = [p for p, b in s.blocks.items() if "part=head" in b]
    if len(feet) != 1 or len(heads) != 1:
        print("!! the photographer's bed is not whole:", s.name, feet, heads)
        ok = False
    if len(s.entities) != 1:
        print("!! expected exactly one camera stand:", s.name, s.entities)
        ok = False
    (x0, y0, z0), (x1, y1, z1) = s.bounds()
    (bx0, by0, bz0), (bx1, by1, bz1) = BOX
    if not (bx0 <= x0 and x1 <= bx1 and bz0 <= z0 and z1 <= bz1 and y1 - y0 + 1 <= by1 - by0 + 1):
        print(f"!! outside the box: {s.name} x[{x0},{x1}] z[{z0},{z1}] h{y1 - y0 + 1}")
        ok = False

    mats = len(s.materials)
    pad_ground(s)
    file_name = f"{s.name}.blueprint"
    path = os.path.join(pack_dir, FOLDER, file_name)
    s.to_blueprint(path, file_name, PACK_NAME, f"{FOLDER}/{file_name}", BUILDING_TYPE,
                   required_mods=REQUIRED_MODS, be_type=BE_TYPE, box=BOX)
    back = voxel.load_blueprint(path)
    norm = {(x - bx0, y - by0, z - bz0): voxel.parse_state(b) for (x, y, z), b in s.blocks.items()}
    same = norm == {q: voxel.parse_state(b) for q, b in back.blocks.items()}
    print(f"{file_name:24s} anchor {s.anchor!s:14s} {len(s.blocks):5d} blocks  {mats:3d} DO  "
          f"{os.path.getsize(path):6d} B  roundtrip={'ok' if same else 'MISMATCH'}")
    return ok and same


def main():
    import paths
    pack_dir = paths.res("blueprints", "voyager", "voyager")
    os.makedirs(os.path.join(pack_dir, FOLDER), exist_ok=True)
    known = bp.vanilla_blocks() | obs_pack.EXTRA_KNOWN | {
        "voyager:blockhutphotobooth",
        "exposure:photograph_frame_small", "exposure:photograph_frame_medium",
        "exposure:photograph_frame_large", "exposure:lightroom"}
    ok = True
    for look in pb.LOOKS:
        for lv in range(1, 6):
            ok = one(look, lv, known, pack_dir) and ok
    print("ALL OK" if ok else "PROBLEMS")
    return 0 if ok else 1


if __name__ == "__main__":
    sys.exit(main())
