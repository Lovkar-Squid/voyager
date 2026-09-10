"""Read every pasted building back out of the test world and judge it as built.

The paste test (a throwaway server mod, see BUILDING.md) puts the fifty blueprints into a world
through Structurize itself, one per 64 blocks, anchor at y=150. This reads each one back, diffs
it against the design block for block (names, not properties - Structurize re-evaluates door
hinges and bed halves on placement), counts the frames and the stand, and runs the access audit
on what the world actually holds. python3 worldcheck.py /path/to/testworld
"""
import sys

import access
import build_pack as bp
import observatory as o
import photobooth as pb
import worldread as wr
from voxel import Structure, parse_state

SPACING, Y = 64, 150
SUBST = {"structurize:blocksolidsubstitution", "structurize:blocksubstitution"}
NATURAL = {"minecraft:dirt", "minecraft:grass_block", "minecraft:stone", "minecraft:water", "minecraft:air"}


def readback(world, des, idx):
    ax, ay, az = des.anchor
    wx, wy, wz = (idx % 10) * SPACING, Y, (idx // 10) * SPACING
    dx, dy, dz = wx - ax, wy - ay, wz - az
    (x0, y0, z0), (x1, y1, z1) = des.bounds()
    blocks = wr.box(world, (x0 + dx, y0 + dy, z0 + dz), (x1 + dx, y1 + dy, z1 + dz))
    s = Structure(des.name)
    for (x, y, z), st in blocks.items():
        s.set(x - dx, y - dy, z - dz, st)
    s.anchor = des.anchor
    s.tags = des.tags
    for eid, (px, py, pz), tag in wr.entities(world, (x0 + dx, y0 + dy, z0 + dz), (x1 + dx, y1 + dy + 2, z1 + dz)):
        if eid in ("exposure:camera_stand", "exposure:photograph_frame"):
            extra = {"__tile__": True, "Facing": int(tag["Facing"])} if eid == "exposure:photograph_frame" else {}
            s.entity(int(px // 1) - dx, int(py + 0.5) - dy, int(pz // 1) - dz, eid, extra=extra)
    return s


def judge(world, kind, look, lv, idx):
    mod = o if kind == "observatory" else pb
    des = bp.aligned(lambda l, _look=look: mod.build(_look, l), lv)
    s = readback(world, des, idx)
    diffs = []
    for p, b in des.blocks.items():
        n = parse_state(b)[0]
        if n in SUBST:
            continue
        w = s.get(*p)
        if w is None or parse_state(w)[0] != n:
            diffs.append((p, n, w and parse_state(w)[0]))
    probs = access.audit(s)
    frames = sum(1 for e in s.entities if e[1] == "exposure:photograph_frame")
    stands = sum(1 for e in s.entities if e[1] == "exposure:camera_stand")
    exp_frames = sum(1 for e in des.entities if e[1] == "exposure:photograph_frame")
    ok = not diffs and not probs and stands == 1 and frames == exp_frames
    print(f"{kind:11s} {des.name:14s} {len(des.blocks):5d} blocks  diffs {len(diffs):3d}  frames {frames}/{exp_frames}  stand {stands}  "
          f"audit {'ok' if not probs else probs[:2]}{'' if ok else '   <<<'}")
    if diffs:
        print("     e.g.", diffs[:4])
    return ok


if __name__ == "__main__":
    world = sys.argv[1] if len(sys.argv) > 1 else "/root/nfserver/testworld"
    jobs = [("observatory", l, i) for l in o.LOOKS for i in range(1, 6)] + [("photobooth", l, i) for l in pb.LOOKS for i in range(1, 6)]
    ok = True
    for idx, (kind, look, lv) in enumerate(jobs):
        ok = judge(world, kind, look, lv, idx) and ok
    print("WORLD READBACK", "ALL OK" if ok else "PROBLEMS")
    sys.exit(0 if ok else 1)
