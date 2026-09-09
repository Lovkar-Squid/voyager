"""Validate the Observatory designs the way build_pack validates the Departure Point.

Four questions, for every look and every level: does every block id exist in this Minecraft, does
anything float or hang off nothing, does every Domum Ornamentum block carry its material, and does
the level fit the shared box once aligned on its hut block. Run before rendering and before
building the pack - a block id that does not exist becomes AIR in Structurize without a word of
warning, and a DO block with no material comes out wearing the wrong stone.
"""
import sys

import build_pack as bp
import floatcheck
import observatory as obs
import voxel

BOX_HALF_X, BOX_Z0, BOX_Z1, BOX_H = 11, -12, 12, 26

known = bp.vanilla_blocks()
known |= {"voyager:blockhutobservatory"}
# Exposure and Exposure: Space are hard requirements of this building (docs/OBSERVATORY.md 5:
# without them the Observatory hut is not registered at all), so its blueprints may use their
# blocks. Domum Ornamentum is a hard requirement of MineColonies. Listed by hand because their
# jars are not in libs/.
known |= {"exposure_space:analyzer", "exposure_space:night_analyzer", "exposure:lightroom",
          "exposure:photograph_frame_small", "exposure:photograph_frame_medium",
          "exposure:photograph_frame_large"}
known |= {"domum_ornamentum:beige_stone_bricks", "domum_ornamentum:cream_stone_bricks",
          "domum_ornamentum:brown_stone_bricks", "domum_ornamentum:brown_bricks",
          "domum_ornamentum:sand_stone_bricks", "domum_ornamentum:beige_bricks",
          "domum_ornamentum:black_brick_extra", "domum_ornamentum:blue_brick_extra",
          "domum_ornamentum:blockbarreldeco_standing", "domum_ornamentum:architectscutter",
          "domum_ornamentum:vanilla_stairs_compat", "domum_ornamentum:vanilla_slab_compat",
          "domum_ornamentum:vanilla_wall_compat", "domum_ornamentum:squarepillar",
          "domum_ornamentum:shingle", "domum_ornamentum:framed",
          "domum_ornamentum:gray_brick_extra", "domum_ornamentum:light_blue_brick_extra"}

ok = True
for look in obs.LOOKS:
    for lv in range(1, 6):
        s = bp.aligned(lambda l, _look=look: obs.build(_look, l), lv)
        missing = bp.unknown_blocks(s, known)
        if missing:
            print(f"!! {s.name}: blocks that do not exist:", sorted(missing))
            ok = False
        loose = floatcheck.floating(s)
        if loose:
            print(f"!! {s.name}: {len(loose)} floating block(s), e.g.", loose[:4])
            ok = False
        # A block can be connected and still pop off on placement: a ladder with no wall behind
        # it, a lantern hung from air, a lectern on nothing.
        loose2 = floatcheck.attachment(s)
        if loose2:
            print(f"!! {s.name}: {len(loose2)} block(s) with nothing to attach to, e.g.", loose2[:4])
            ok = False
        # A Domum Ornamentum mix-and-match block with no material in its block entity comes out of
        # the builder wearing DO's fallback, not the stone you designed - and nothing warns you.
        bare = sorted(pos for pos, b in s.blocks.items()
                      if voxel.parse_state(b)[0] in voxel.DO_SLOTS and pos not in s.materials)
        if bare:
            print(f"!! {s.name}: {len(bare)} Domum Ornamentum block(s) with no material, e.g.", bare[:4])
            ok = False
        (x0, y0, z0), (x1, y1, z1) = s.bounds()
        fits = (-BOX_HALF_X <= x0 and x1 <= BOX_HALF_X and BOX_Z0 <= z0 and z1 <= BOX_Z1
                and y1 - y0 + 1 <= BOX_H)
        print(f"{s.name:16s} {str(s.size()):16s} {len(s.blocks):5d} blocks  {len(s.materials):3d} DO  "
              f"anchor {s.anchor}  x[{x0},{x1}] z[{z0},{z1}] h{y1 - y0 + 1}  "
              f"{'fits' if fits else 'OUTSIDE THE BOX'}")
        if not fits:
            ok = False
print("OK" if ok else "PROBLEMS ABOVE")
sys.exit(0 if ok else 1)
