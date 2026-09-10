"""ASCII floor plans of a Structure, one level at a time: what stands on each square.

python3 floorplan.py observatory keep5 2 7    -> the ground floor (y=2) and the roof room (y=7)
Legend: '.' open floor, ' ' nothing to stand on, '#' wall/solid, 'D' door, 'L' ladder,
'R' rack, 'H' hut, 'A' analyser, 'C' console, 'X' cutter, 'K' chart table, 'B' books,
'E' enchanting table, 'l' lectern, 'b' bed, 'o' barrel, 'P' lightroom, 'M' the mount, 'T' tube,
'*' lamp/lantern, 'g' glass, '=' slab/stair, 'W' wall/fence/bars, '@' camera stand,
'^' sitter/photographer/studio/scope mark on the floor, 'f' a photograph frame.
"""
import sys

from voxel import parse_state
import walkcheck

KEY = [("blockhut", "H"), ("rack", "R"), ("night_analyzer", "C"), ("analyzer", "A"), ("cutter", "X"),
       ("cartography", "K"), ("bookshelf", "B"), ("enchanting", "E"), ("lectern", "l"), ("bed", "b"),
       ("barrel", "o"), ("lightroom", "P"), ("lodestone", "M"), ("_door", "D"), ("ladder", "L"),
       ("lantern", "*"), ("glowstone", "*"), ("bulb", "*"), ("end_rod", "*"), ("glass", "g"),
       ("_slab", "="), ("_stairs", "="), ("slab_compat", "="), ("stairs_compat", "="), ("shingle", "="),
       ("_wall", "W"), ("iron_bars", "W"), ("wall_compat", "W"), ("squarepillar", "|")]


def glyph(s, x, y, z):
    b = s.get(x, y, z)
    if b is None:
        return "." if walkcheck.solid(s, x, y - 1, z) or (y == 1) else " "
    n = parse_state(b)[0]
    for k, g in KEY:
        if k in n:
            return g
    return "#"


def plan(s, y):
    (x0, _, z0), (x1, _, z1) = s.bounds()
    marks = {}
    for (x, yy, z), names in s.tags.items():
        if yy == y and any(n in ("sitter", "photographer", "studio", "scope", "camera", "darkroom") for n in names):
            marks[(x, z)] = "^"
    ents = {(x, z): "@" for (x, yy, z), eid, *_ in s.entities if yy == y and "camera" in eid}
    frames = {(x, z): "f" for (x, yy, z), eid, *_ in s.entities if yy == y and "frame" in eid}
    lines = [f"y={y}  x from {x0} to {x1}, north (z={z0}) at the top"]
    for z in range(z0, z1 + 1):
        row = ""
        for x in range(x0, x1 + 1):
            g = glyph(s, x, y, z)
            if (x, z) in ents:
                g = "@"
            elif (x, z) in frames and g == ".":
                g = "f"
            elif (x, z) in marks and g == ".":
                g = "^"
            row += g
        lines.append(f"{z:4d} {row}")
    return "\n".join(lines)


if __name__ == "__main__":
    import importlib
    mod = importlib.import_module(sys.argv[1])
    name = sys.argv[2]
    look, lv = name.rstrip("12345"), int(name[-1])
    st = mod.build(look, lv)
    for y in (int(a) for a in sys.argv[3:]):
        print(plan(st, y))
        print()
