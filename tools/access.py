"""Can a colonist actually use the building? The audit every Observatory and Photo Booth passes.

What it checks, for one voxel Structure (before pad_ground):

  reach     Starting from the ground outside the building, a two-block-tall citizen walks
            (step up 1, drop down 3, through doors, up and down ladders) and must be able to
            stand next to every block that is worked at: the hut block, every rack, the
            analyser, the console, the cutter, every lightroom, the lectern, the chart table,
            the barrel, the enchanting table, the bed, the instrument (``scope``) - and on
            every floor mark (``sitter``, ``photographer``, the camera stand's square).
  rooms     Every pocket of standable floor that is walled off from the outside is reported:
            a room with no way in, or a room whose door opens into a wall.
  doors     Every door must have a standable square on both sides, at the door's own height.
  light     Exposure's lightroom refuses to print below light level 13 (its default
            ``lightroom_light_requirement``), measured at the block ABOVE the lightroom. Block
            light only - a darkroom has no sky - propagated from the design's own lamps.
  beds      Both halves present, nothing on top of them, roofed.

Everything is reported, nothing is fixed here: the designs are fixed in observatory.py and
photobooth.py, and this tells you whether they are.
"""
from collections import deque

from voxel import parse_state
import walkcheck

# blocks a citizen works at from an adjacent square -> the name of the check
WORK_BLOCKS = {
    "minecolonies:blockminecoloniesrack": "rack",
    "exposure_space:analyzer": "analyzer",
    "exposure_space:night_analyzer": "console",
    "exposure:lightroom": "lightroom",
    "domum_ornamentum:architectscutter": "cutter",
    "minecraft:lectern": "lectern",
    "minecraft:cartography_table": "chart table",
    "minecraft:enchanting_table": "enchanting table",
    "domum_ornamentum:blockbarreldeco_standing": "barrel",
}
# tagged squares the AI walks to and stands ON
STAND_TAGS = ("sitter", "photographer", "studio", "camera")
# tagged blocks the AI walks NEXT TO
NEAR_TAGS = ("darkroom", "scope")

LIGHT = {
    "minecraft:lantern": 15, "minecraft:soul_lantern": 10, "minecraft:sea_lantern": 15,
    "minecraft:glowstone": 15, "minecraft:end_rod": 14, "minecraft:torch": 14,
    "minecraft:wall_torch": 14, "minecraft:shroomlight": 15, "minecraft:jack_o_lantern": 15,
    "minecraft:redstone_lamp": 15, "minecraft:waxed_copper_bulb": 15, "minecraft:copper_bulb": 15,
    "minecraft:light": 15, "minecraft:ochre_froglight": 15, "minecraft:verdant_froglight": 15,
    "minecraft:pearlescent_froglight": 15, "minecraft:campfire": 15, "minecraft:soul_campfire": 10,
    "minecraft:crying_obsidian": 10, "minecraft:magma_block": 3, "minecraft:amethyst_block": 0,
    "minecraft:beacon": 15, "minecraft:conduit": 15, "domum_ornamentum:framed_light": 15,
    "domum_ornamentum:center_light": 15,
}
LIGHT_REQUIRED = 13

# blocks light passes through (with the usual one-per-block loss); everything else is opaque
TRANSPARENT_WORDS = ("glass_pane", "stained_glass_pane", "iron_bars", "chain", "_door", "lantern",
                     "end_rod", "torch", "_slab", "_stairs", "_wall", "_fence", "ladder",
                     "photograph_frame", "camera", "bed", "lightroom", "analyzer", "lectern",
                     "cartography", "enchanting", "barrel", "chorus", "rack", "light", "bulb",
                     "squarepillar", "vanilla_slab_compat", "vanilla_stairs_compat", "vanilla_wall_compat")
OPAQUE_EXACT = ("minecraft:tinted_glass",)          # the one glass that stops light dead


def _name(s, x, y, z):
    b = s.get(x, y, z)
    return None if b is None else parse_state(b)[0]


def light_source(s, x, y, z):
    b = s.get(x, y, z)
    if b is None:
        return 0
    name, props = parse_state(b)
    if name in ("minecraft:waxed_copper_bulb", "minecraft:copper_bulb") and props.get("lit", "false") != "true":
        return 0
    if name == "minecraft:light":
        return int(props.get("level", "15"))
    return LIGHT.get(name, 0)


def transparent(s, x, y, z):
    b = s.get(x, y, z)
    if b is None:
        return True
    name = parse_state(b)[0]
    if name in OPAQUE_EXACT:
        return False
    if name == "minecraft:glass" or name.endswith("_stained_glass"):
        return True
    return any(w in name for w in TRANSPARENT_WORDS)


def block_light(s, bounds):
    """Block light for every cell inside bounds (+1), from the design's own lamps, ignoring sky."""
    (x0, y0, z0), (x1, y1, z1) = bounds
    light = {}
    q = deque()
    for x in range(x0 - 1, x1 + 2):
        for y in range(y0 - 1, y1 + 2):
            for z in range(z0 - 1, z1 + 2):
                lv = light_source(s, x, y, z)
                if lv:
                    light[(x, y, z)] = lv
                    q.append((x, y, z))
    while q:
        x, y, z = q.popleft()
        lv = light[(x, y, z)] - 1
        if lv <= 0:
            continue
        for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            n = (x + dx, y + dy, z + dz)
            if not (x0 - 1 <= n[0] <= x1 + 1 and y0 - 1 <= n[1] <= y1 + 1 and z0 - 1 <= n[2] <= z1 + 1):
                continue
            if not transparent(s, *n):
                continue
            if light.get(n, 0) < lv:
                light[n] = lv
                q.append(n)
    return light


# ---------------------------------------------------------------- walking

def is_ladder(s, x, y, z):
    n = _name(s, x, y, z)
    return n is not None and n.endswith("ladder")


TALL_WORDS = ("_wall", "_fence", "iron_bars", "glass_pane", "vanilla_wall_compat", "vanilla_fence_compat")


def tall(s, x, y, z):
    """A wall, fence, bars or pane: a block and a half high - not a step, not a floor."""
    n = _name(s, x, y, z)
    return n is not None and any(w in n for w in TALL_WORDS) and "fence_gate" not in n


def ground_solid(s, x, y, z):
    """Solid enough to stand on. The pad's y=0 is terrain once built, so it counts even where the
    design leaves it empty."""
    if tall(s, x, y, z):
        return False
    if walkcheck.solid(s, x, y, z):
        return True
    return y == 0 and s.get(x, y, z) is None


def full_floor(s, x, y, z):
    """A whole block to stand on - what a room's floor is made of (slab and stair tops are not)."""
    n = _name(s, x, y, z)
    if n is None:
        return y == 0
    if tall(s, x, y, z):
        return False
    if any(w in n for w in ("_slab", "_stairs", "slab_compat", "stairs_compat", "shingle")):
        return False
    if any(w in n for w in ("bed", "blockhut", "bookshelf", "lodestone")):
        return False                                  # furniture: its top is not a floor
    return walkcheck.solid(s, x, y, z) and n not in WORK_BLOCKS


def standable(s, x, y, z):
    return (ground_solid(s, x, y - 1, z) and walkcheck.passable(s, x, y, z)
            and walkcheck.passable(s, x, y + 1, z) and not tall(s, x, y - 1, z))


def on_ladder(s, x, y, z):
    return is_ladder(s, x, y, z) and (walkcheck.passable(s, x, y + 1, z) or is_ladder(s, x, y + 1, z))


def node(s, x, y, z):
    return standable(s, x, y, z) or on_ladder(s, x, y, z)


def walk(s, bounds, starts):
    """Every square a citizen can reach from `starts`, inside bounds (+2 around)."""
    (x0, y0, z0), (x1, y1, z1) = bounds
    inside = lambda x, y, z: x0 - 2 <= x <= x1 + 2 and 0 <= y <= y1 + 2 and z0 - 2 <= z <= z1 + 2
    seen = set(starts)
    q = deque(starts)
    while q:
        x, y, z = q.popleft()
        # ladders: up and down
        if is_ladder(s, x, y, z) or is_ladder(s, x, y - 1, z):
            for ny in (y + 1, y - 1):
                n = (x, ny, z)
                if inside(*n) and n not in seen and node(s, *n):
                    seen.add(n)
                    q.append(n)
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            nx, nz = x + dx, z + dz
            for ny in (y + 1, y, y - 1, y - 2, y - 3):
                n = (nx, ny, nz)
                if not inside(*n) or n in seen:
                    continue
                if node(s, *n):
                    if ny == y + 1 and not walkcheck.passable(s, x, y + 2, z):
                        continue                      # no headroom to step up
                    if ny < y and not walkcheck.passable(s, nx, y, nz):
                        continue                      # can't drop through a block
                    seen.add(n)
                    q.append(n)
                    break
    return seen


def outside_starts(s, bounds):
    """Standable squares on the ground ring just outside the design's own blocks."""
    (x0, y0, z0), (x1, y1, z1) = bounds
    starts = []
    for x in range(x0 - 2, x1 + 3):
        for z in range(z0 - 2, z1 + 3):
            for y in (1, 2, 3):
                if standable(s, x, y, z) and (x in (x0 - 2, x1 + 2) or z in (z0 - 2, z1 + 2)):
                    starts.append((x, y, z))
    return starts


def near(s, seen, x, y, z, r):
    """A reached square within r blocks (and a floor either way) of (x, y, z)."""
    for dx in range(-r, r + 1):
        for dz in range(-r, r + 1):
            for dy in (0, -1, 1, -2, 2):
                if (dx or dz) and (x + dx, y + dy, z + dz) in seen:
                    return True
    return False


def next_to(s, seen, x, y, z):
    """A reached square from which a citizen can work at block (x, y, z)."""
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        for dy in (0, -1, 1):
            if (x + dx, y + dy, z + dz) in seen:
                return True
    return False


# ---------------------------------------------------------------- the audit

def audit(s):
    """Returns a list of problem strings; empty means the building passes."""
    problems = []
    bounds = s.bounds()
    starts = outside_starts(s, bounds)
    if not starts:
        return ["nowhere to start from outside the building"]
    seen = walk(s, bounds, starts)

    # reach: worked-at blocks
    ax, ay, az = s.anchor
    if not next_to(s, seen, ax, ay, az):
        problems.append(f"hut block {s.anchor} cannot be reached")
    for (x, y, z), b in sorted(s.blocks.items()):
        kind = WORK_BLOCKS.get(parse_state(b)[0])
        if kind and not next_to(s, seen, x, y, z):
            problems.append(f"{kind} at {(x, y, z)} cannot be reached")
    for (x, y, z), names in sorted(s.tags.items()):
        for t in names:
            if t in STAND_TAGS and (x, y, z) not in seen:
                problems.append(f"'{t}' mark {(x, y, z)} is not a square a citizen can stand on and reach")
            if t in NEAR_TAGS and not near(s, seen, x, y, z, 2 if t == "scope" else 1) and (x, y, z) not in seen:
                problems.append(f"'{t}' {(x, y, z)} cannot be reached")
    for (x, y, z), eid, _yaw, _pitch, extra in s.entities:
        if extra.get("__tile__"):
            # a frame hangs on a wall: it needs the wall behind it, not a floor under it
            fx, fz = {2: (0, 1), 3: (0, -1), 4: (1, 0), 5: (-1, 0)}[int(extra["Facing"])]
            if s.get(x + fx, y, z + fz) is None or transparent(s, x + fx, y, z + fz):
                problems.append(f"frame at {(x, y, z)} has no wall behind it")
            continue
        if (x, y, z) not in seen:
            problems.append(f"entity {eid} at {(x, y, z)} stands where nobody can walk")

    # rooms: pockets of floor walled off from the outside
    (x0, y0, z0), (x1, y1, z1) = bounds
    unreached = set()
    for x in range(x0, x1 + 1):
        for y in range(1, y1 + 1):
            for z in range(z0, z1 + 1):
                p = (x, y, z)
                if p not in seen and standable(s, *p) and full_floor(s, x, y - 1, z) and _enclosed(s, x, y, z):
                    unreached.add(p)
    for comp in _components(unreached):
        xs = [c[0] for c in comp]; ys = [c[1] for c in comp]; zs = [c[2] for c in comp]
        # A room is at least two squares wide both ways and closed in: the strip on top of a
        # wing's roof under the eave, or the top of the telescope's tube, is unreached too, but
        # it is one square wide and open at the sides - nobody is meant to be there.
        if len(comp) < 3 or max(xs) == min(xs) or max(zs) == min(zs) or not _sealed(s, comp):
            continue
        problems.append(f"room with no way in: {len(comp)} squares around x[{min(xs)},{max(xs)}] y{min(ys)}-{max(ys)} z[{min(zs)},{max(zs)}]")

    # doors: something to stand on on both sides
    for (x, y, z), b in sorted(s.blocks.items()):
        name, props = parse_state(b)
        if not name.endswith("_door") or props.get("half") != "lower":
            continue
        facing = props.get("facing", "north")
        dx, dz = {"north": (0, 1), "south": (0, -1), "east": (-1, 0), "west": (1, 0)}[facing]
        sides = [(x + dx, y, z + dz), (x - dx, y, z - dz)]
        for sx, sy, sz in sides:
            if not any(standable(s, sx, sy + dy, sz) for dy in (0, 1, -1)):
                problems.append(f"door at {(x, y, z)} opens into a wall/void on the {facing} side {(sx, sy, sz)}")
                break
        if (x, y, z) not in seen and not any(sd in seen for sd in sides):
            problems.append(f"door at {(x, y, z)} is not reachable from either side")

    # light at every lightroom
    light = block_light(s, bounds)
    for (x, y, z), b in sorted(s.blocks.items()):
        if parse_state(b)[0] == "exposure:lightroom":
            above = (x, y + 1, z)
            if s.get(*above) is not None:
                problems.append(f"lightroom at {(x, y, z)} has a block on top of it")
                continue
            lv = light.get(above, 0)
            if lv < LIGHT_REQUIRED:
                problems.append(f"lightroom at {(x, y, z)} has light {lv} above it, needs {LIGHT_REQUIRED}")

    # beds
    feet = [p for p, b in s.blocks.items() if "part=foot" in b]
    heads = [p for p, b in s.blocks.items() if "part=head" in b]
    if len(feet) != 1 or len(heads) != 1:
        problems.append(f"bed is not whole: feet {feet} heads {heads}")
    else:
        for (x, y, z) in feet + heads:
            if s.get(x, y + 1, z) is not None:
                problems.append(f"bed half {(x, y, z)} has a block on top of it")
            if not any((x, y + h, z) in s.blocks for h in range(2, 8)):
                problems.append(f"bed half {(x, y, z)} is not under a roof")
        if not (next_to(s, seen, *feet[0]) or next_to(s, seen, *heads[0])):
            problems.append(f"bed at {feet[0]} cannot be reached")
    return problems


def _enclosed(s, x, y, z):
    """A floor square with a ceiling somewhere above it - indoors, not the open terrace."""
    return any((x, y + h, z) in s.blocks for h in range(1, 8))


def _sealed(s, comp):
    """No standable square outside the pocket touches it: walled in, not merely out of the way."""
    for (x, y, z) in comp:
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (0, 1, -1):
                n = (x + dx, y + dy, z + dz)
                if n in comp or not standable(s, *n):
                    continue
                if dy == 1 and not full_floor(s, n[0], n[1] - 1, n[2]):
                    continue                    # the top of a rack or a bed is not a way out
                return False
    return True


def _components(cells):
    cells = set(cells)
    comps = []
    while cells:
        start = cells.pop()
        comp = {start}
        q = deque([start])
        while q:
            x, y, z = q.popleft()
            for dx, dy, dz in ((1, 0, 0), (-1, 0, 0), (0, 0, 1), (0, 0, -1), (0, 1, 0), (0, -1, 0)):
                n = (x + dx, y + dy, z + dz)
                if n in cells:
                    cells.remove(n)
                    comp.add(n)
                    q.append(n)
        comps.append(comp)
    return comps


def report(name, problems):
    if not problems:
        print(f"{name:14s} ok")
        return True
    print(f"{name:14s} {len(problems)} problem(s)")
    for p in problems:
        print("   - " + p)
    return False


if __name__ == "__main__":
    import sys
    import observatory
    import photobooth
    ok = True
    which = sys.argv[1:] or ["observatory", "photobooth"]
    if "observatory" in which:
        for look in observatory.LOOKS:
            for lv in range(1, 6):
                ok = report(f"obs {look}{lv}", audit(observatory.build(look, lv))) and ok
    if "photobooth" in which:
        for look in photobooth.LOOKS:
            for lv in range(1, 6):
                ok = report(f"booth {look}{lv}", audit(photobooth.build(look, lv))) and ok
    print("ALL OK" if ok else "PROBLEMS")
    sys.exit(0 if ok else 1)
