"""The two Voyager hut looks, levels 1-5, as voxel structures.

Front of every building is SOUTH (+z): the control room with the hut block
(voyager:blockhutvoyager, the 'departure console') stands at the south edge,
the pad / gate extends north.  The worker leaves from the block tagged
'departure'.  y=0 is the foundation layer (solid substitution), the pad
surface is y=1 and people walk on y=2.
"""
import math

from voxel import Structure

HUT = "voyager:blockhutvoyager[facing=north]"  # the console faces south, into the room
RACK = "minecolonies:blockminecoloniesrack"
FOUNDATION = "structurize:blocksolidsubstitution"


# ---------------------------------------------------------------- helpers
def stairs(block_id, facing, half="bottom"):
    return f"{block_id}[facing={facing},half={half},shape=straight,waterlogged=false]"


def slab(block_id, kind="bottom"):
    return f"{block_id}[type={kind},waterlogged=false]"


def connected(block_id, *dirs):
    props = {"north": "false", "south": "false", "east": "false", "west": "false", "waterlogged": "false"}
    for d in dirs:
        props[d] = "true"
    return block_id + "[" + ",".join(f"{k}={v}" for k, v in props.items()) + "]"


def wall_post(block_id):
    return f"{block_id}[north=none,south=none,east=none,west=none,up=true,waterlogged=false]"


def door(block_id, facing, half, hinge="left"):
    return f"{block_id}[facing={facing},half={half},hinge={hinge},open=false,powered=false]"


def ladder(facing):
    return f"minecraft:ladder[facing={facing},waterlogged=false]"


def rod(facing="up"):
    return f"minecraft:end_rod[facing={facing}]"


def lantern(kind="minecraft:lantern", hanging=False):
    return f"{kind}[hanging={'true' if hanging else 'false'},waterlogged=false]"


def cone(s, cx, cz, y, r, block, tip_block, stair_block, layers_per_step=2):
    """Tapering nose: solid discs shrinking by one block every `layers_per_step` layers,
    rim of each step dressed with stairs facing the axis. Returns the y above the tip."""
    rr = r
    while rr > 1.0:
        for i in range(layers_per_step):
            s.disc(cx, cz, y, rr, block)
            if i == layers_per_step - 1:
                # stairs on the rim of the next (smaller) step
                nxt = rr - 1.0
                for x in range(int(cx - rr) - 1, int(cx + rr) + 2):
                    for z in range(int(cz - rr) - 1, int(cz + rr) + 2):
                        d = math.hypot(x - cx, z - cz)
                        if nxt < d <= rr:
                            dx, dz = x - cx, z - cz
                            facing = ("west" if dx > 0 else "east") if abs(dx) >= abs(dz) else ("north" if dz > 0 else "south")
                            s.set(x, y + 1, z, stairs(stair_block, facing))
            y += 1
        rr -= 1.0
    s.disc(cx, cz, y, 1.0, block)
    s.set(cx, y + 1, cz, tip_block)
    s.set(cx, y + 2, cz, tip_block)
    return y + 3


def hollow_disc(s, cx, cz, y, r, block, inner=None):
    """Ring of a disc in the xz plane: blocks with inner < d <= r."""
    lo = r - 1.0 if inner is None else inner
    s.disc(cx, cz, y, r, block, ring=True, inner=lo)


def bars_line(s, x0, z0, x1, z1, y):
    """Straight run of iron bars (pipe/railing) with the right connections."""
    if x0 == x1:
        za, zb = sorted((z0, z1))
        for z in range(za, zb + 1):
            dirs = []
            if z > za: dirs.append("north")
            if z < zb: dirs.append("south")
            s.set(x0, y, z, connected("minecraft:iron_bars", *dirs))
    else:
        xa, xb = sorted((x0, x1))
        for x in range(xa, xb + 1):
            dirs = []
            if x > xa: dirs.append("west")
            if x < xb: dirs.append("east")
            s.set(x, y, z0, connected("minecraft:iron_bars", *dirs))


def railing(s, x0, z0, x1, z1, y, block="minecraft:iron_bars", gaps=()):
    """Rectangle of bars/panes on the given y (edge of a platform)."""
    for x in range(x0, x1 + 1):
        for z in (z0, z1):
            if (x, z) in gaps:
                continue
            dirs = []
            if x > x0: dirs.append("west")
            if x < x1: dirs.append("east")
            s.set(x, y, z, connected(block, *dirs))
    for z in range(z0 + 1, z1):
        for x in (x0, x1):
            if (x, z) in gaps:
                continue
            s.set(x, y, z, connected(block, "north", "south"))


# ================================================================ LAUNCHPAD
LP = {
    "pad": "minecraft:light_gray_concrete",
    "pad2": "minecraft:smooth_stone",
    "rim": "minecraft:polished_andesite",
    "stripe": "minecraft:yellow_concrete",
    "stripe2": "minecraft:black_concrete",
    "mount": "minecraft:polished_blackstone_bricks",
    "mount_stairs": "minecraft:polished_blackstone_brick_stairs",
    "trench": "minecraft:magma_block",
    "body": "minecraft:white_concrete",
    "band": "minecraft:light_gray_concrete",
    "stripe_r": "minecraft:red_concrete",
    "nose": "minecraft:red_concrete",
    "window": "minecraft:light_blue_stained_glass",
    "skirt": "minecraft:polished_blackstone",
    "skirt2": "minecraft:blackstone",
    "nozzle": "minecraft:iron_block",
    "frame": "minecraft:stone_bricks",
    "frame2": "minecraft:polished_andesite",
    "lattice": "minecraft:iron_bars",
    "deck": "minecraft:smooth_stone_slab",
    "wall": "minecraft:stone_bricks",
    "trim": "minecraft:polished_andesite",
    "corner": "minecraft:iron_block",
    "roof": "minecraft:smooth_stone_slab",
    "roof_edge": "minecraft:smooth_stone",
    "glass": "minecraft:glass",
    "floor": "minecraft:polished_andesite",
    "door": "minecraft:dark_oak_door",
    "tank": "minecraft:light_gray_concrete",
    "tank_cap": "minecraft:iron_block",
    "tank_band": "minecraft:waxed_cut_copper",
    "booster": "minecraft:white_concrete",
    "booster_top": "minecraft:light_gray_concrete",
}


HATCH = "minecraft:warped_door"


def tag_rocket(s, before):
    """Tag every block the rocket builder added as 'rocket' - the building hides exactly these
    while the Voyager is away and puts them back on landing."""
    for pos in s.blocks:
        if pos not in before:
            s.tag(*pos, "rocket")


def rocket(s, p, cx, cz, y0, r, height, level, fins=("north", "east", "west")):
    """Rocket standing on the mount: engine skirt at y0 (its top is the cabin floor), hull
    radius r (2.5 -> 5 wide), a wooden hatch on the south side at walking height with a step
    up from the mount ring, and the departure point inside the cabin. South stays free of fins."""
    ri = int(r)  # integer half-width
    # hold-down clamps: four iron posts on the mount the skirt edge rests on - they stay on the
    # pad when the rocket leaves, so they are placed before the rocket blocks get tagged
    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
        s.set(cx + dx * ri, y0 - 1, cz + dz * ri, p["nozzle"])
    before = set(s.blocks)
    # engine skirt (cabin floor) and nozzles in the trench below
    s.disc(cx, cz, y0, r, p["skirt2"])
    s.disc(cx, cz, y0 - 1, r - 1, p["nozzle"])
    if r >= 3.5:
        for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
            s.set(cx + dx, y0 - 1, cz + dz, p["nozzle"])
    # hull: hollow above the skirt
    top = y0 + height
    for y in range(y0 + 1, top + 1):
        hollow_disc(s, cx, cz, y, r, p["body"])
        if (y - y0) % 6 == 0:
            hollow_disc(s, cx, cz, y, r, p["band"])
    hollow_disc(s, cx, cz, y0 + 1, r, p["skirt"])   # dark ring around the cabin
    # red racing stripes east/west + a red band under the nose
    for y in range(y0 + 4, top - 3):
        s.set(cx + ri, y, cz, p["stripe_r"])
        s.set(cx - ri, y, cz, p["stripe_r"])
    hollow_disc(s, cx, cz, top, r, p["stripe_r"])
    # porthole rings (two heights) on the four sides
    for wy in (top - 4, top - 9):
        if wy > y0 + 4:
            for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
                s.set(cx + dx * ri, wy, cz + dz * ri, p["window"])
    # hatch on the south side at cabin-floor height, step up to it from the mount ring
    hz = cz + ri
    s.set(cx, y0 + 1, hz, door(HATCH, "north", "lower"))
    s.set(cx, y0 + 2, hz, door(HATCH, "north", "upper"))
    s.set(cx, y0, hz + 1, stairs(p["mount_stairs"], "north"))
    s.tag(cx, y0 + 1, cz, "departure")
    # nose cone: quartz-stair dressed taper, red tip, antenna on top
    y = cone(s, cx, cz, top + 1, r - 0.5, p["body"], p["nose"], "minecraft:quartz_stairs")
    s.set(cx, y, cz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]" if level < 3 else rod("up"))
    # fins: stepped triangles (never south - that is where the crew walks in)
    fh = 3 if r < 3.5 else 4 if r < 4.5 else 5
    dirs = {"east": (1, 0), "west": (-1, 0), "south": (0, 1), "north": (0, -1)}
    for dx, dz in (dirs[f] for f in fins):
        for i in range(fh):
            reach = fh - i
            for k in range(1, reach + 1):
                s.set(cx + dx * (ri + k), y0 + i, cz + dz * (ri + k), p["stripe_r"])
    tag_rocket(s, before)
    return top


def gantry(s, p, x, z, y0, height, arm_to_x, level):
    """Service tower 3x3: corner posts + iron-bar lattice, platforms every 5 blocks
    with a walkway arm toward the rocket (arm_to_x = x of the rocket hull edge)."""
    # corner posts
    for dx in (0, 2):
        for dz in (0, 2):
            s.column(x + dx, z + dz, y0, y0 + height, p["frame"] if (dx == dz) else p["frame2"])
    # lattice walls (bars) between posts
    for y in range(y0 + 1, y0 + height):
        s.set(x + 1, y, z, p["frame2"])
        s.set(x + 1, y, z + 2, connected(p["lattice"], "east", "west"))
        s.set(x, y, z + 1, connected(p["lattice"], "north", "south"))
        s.set(x + 2, y, z + 1, connected(p["lattice"], "north", "south"))
    # platforms + arms
    direction = 1 if arm_to_x > x else -1
    for y in range(y0 + 5, y0 + height, 5):
        s.box(x, y, z, x + 2, y, z + 2, slab(p["deck"], "top"))
        # ladder hole in the middle
        s.set(x + 1, y, z + 1, None)
        ax = x + 2 + 1 if direction > 0 else x - 1
        while (direction > 0 and ax < arm_to_x) or (direction < 0 and ax > arm_to_x):
            s.set(ax, y, z + 1, slab(p["deck"], "top"))
            s.set(ax, y + 1, z, connected(p["lattice"], "east", "west"))
            s.set(ax, y + 1, z + 2, connected(p["lattice"], "east", "west"))
            ax += direction
    # ladder up the middle
    for y in range(y0 + 1, y0 + height + 1):
        s.set(x + 1, y, z + 1, ladder("south"))
    # top deck with floodlights
    s.box(x, y0 + height, z, x + 2, y0 + height, z + 2, p["frame2"])
    s.set(x, y0 + height + 1, z, "minecraft:sea_lantern")
    s.set(x + 2, y0 + height + 1, z + 2, "minecraft:sea_lantern")
    s.set(x + 1, y0 + height + 1, z + 1, wall_post("minecraft:andesite_wall"))
    s.set(x + 1, y0 + height + 2, z + 1, "minecraft:red_concrete")


def fuel_tank(s, p, cx, cz, y0, height):
    r = 1.5
    s.disc(cx, cz, y0, r, p["tank_cap"])
    for y in range(y0 + 1, y0 + height):
        s.disc(cx, cz, y, r, p["tank_band"] if y in (y0 + 2, y0 + height - 2) else p["tank"])
    s.disc(cx, cz, y0 + height, r, p["tank_cap"])
    s.set(cx, y0 + height + 1, cz, p["tank_cap"])
    s.set(cx, y0 + height + 2, cz, wall_post("minecraft:andesite_wall"))


def place_racks(s, x0, z0, x1, z1, y, height, floors, hut_x, door_x):
    """Storage for the haul: racks along the south wall and the east wall of the ground floor
    (pairs form double racks), the north corners of every upper floor. Kept free: the cell inside
    each door and the one south of the north door, the console row, the ladder corner."""
    keep_free = {(x0 + 1, z0 + 1), (x0 + 1, z0 + 2), (door_x, z1 - 1),
                 (hut_x - 1, z0 + 1), (hut_x, z0 + 1), (hut_x + 1, z0 + 1)}
    if floors > 1:
        keep_free.add((x1 - 1, z1 - 1))
    cells = [(x, z1 - 1) for x in range(x0 + 1, x1)]                 # south wall
    cells += [(x1 - 1, z) for z in range(z0 + 1, z1 - 1)]             # east wall
    for x, z in cells:
        if (x, z) in keep_free or s.get(x, y + 1, z) is not None:
            continue
        s.set(x, y + 1, z, RACK)
    for f in range(1, floors):
        fy = y + f * (height + 1)
        for x, z in ((x0 + 1, z0 + 1), (x0 + 2, z0 + 1), (x1 - 1, z0 + 1), (x1 - 2, z0 + 1)):
            if x0 < x < x1 and (x, z) != (x1 - 1, z1 - 1):
                s.set(x, fy + 1, z, RACK)


def control_room(s, p, x0, z0, x1, z1, y, height, level, floors=1):
    """Mission control: stone brick bunker with a glass band, flat roof, antenna, console inside.
    Door on the south wall (z1). Returns the console position."""
    s.box(x0, y, z0, x1, y, z1, p["floor"])
    for f in range(floors):
        fy = y + f * (height + 1)
        s.walls(x0, fy + 1, z0, x1, fy + height, z1, p["wall"])
        # glass band at eye level on all sides (keep corners)
        for x in range(x0 + 1, x1):
            s.set(x, fy + 2, z0, p["glass"]); s.set(x, fy + 2, z1, p["glass"])
            if height >= 4:
                s.set(x, fy + 3, z0, p["glass"]); s.set(x, fy + 3, z1, p["glass"])
        for z in range(z0 + 1, z1):
            s.set(x0, fy + 2, z, p["glass"]); s.set(x1, fy + 2, z, p["glass"])
            if height >= 4:
                s.set(x0, fy + 3, z, p["glass"]); s.set(x1, fy + 3, z, p["glass"])
        for cx in (x0, x1):
            for cz in (z0, z1):
                s.column(cx, cz, fy + 1, fy + height, p["corner"] if level >= 3 else p["trim"])
        if f < floors - 1:
            s.box(x0, fy + height + 1, z0, x1, fy + height + 1, z1, p["floor"])
            s.set(x1 - 1, fy + height + 1, z1 - 1, None)  # stair hole
            for i in range(height):
                s.set(x1 - 1, fy + 1 + i, z1 - 1, ladder("west"))
    top = y + floors * (height + 1)
    # roof: slab with a raised edge
    s.box(x0, top, z0, x1, top, z1, slab(p["roof"], "bottom"))
    s.walls(x0, top, z0, x1, top, z1, p["roof_edge"])
    # doors: centre of the south wall (the front door) and the west end of the north wall
    # (out onto the pad, toward the rocket) so the crew never has to climb the railing
    dx = (x0 + x1) // 2
    s.set(dx, y + 1, z1, door(p["door"], "south", "lower"))
    s.set(dx, y + 2, z1, door(p["door"], "south", "upper"))
    s.set(dx - 1, y + 2, z1, p["wall"]); s.set(dx + 1, y + 2, z1, p["wall"])
    s.set(dx - 2, y + 1, z1 + 1, lantern()); s.set(dx + 2, y + 1, z1 + 1, lantern())
    s.set(x0 + 1, y + 1, z0, door(p["door"], "north", "lower"))
    s.set(x0 + 1, y + 2, z0, door(p["door"], "north", "upper"))
    s.set(x0 + 2, y + 2, z0, p["wall"])
    # console (the hut block) against the north wall, racks in the corners, ceiling lamp
    hut_x, hut_z = (x0 + x1) // 2, z0 + 1
    s.set_anchor(hut_x, y + 1, hut_z, HUT)
    if hut_x - 1 > x0 + 1:   # keep the cell behind the north door free in the small rooms
        s.set(hut_x - 1, y + 1, hut_z, "minecraft:lectern[facing=south,has_book=false,powered=false]")
    s.set(hut_x + 1, y + 1, hut_z, "minecraft:crafting_table" if level < 3 else "minecraft:cartography_table")
    place_racks(s, x0, z0, x1, z1, y, height, floors, hut_x, dx)
    for f in range(floors):
        fy = y + f * (height + 1)
        s.set(hut_x, fy + height, (z0 + z1) // 2, "minecraft:sea_lantern")
    # antenna mast on the roof (north-east corner)
    mx, mz = x1 - 1, z0 + 1
    mh = 3 + level
    s.column(mx, mz, top + 1, top + mh, connected(p["lattice"]))
    s.set(mx, top + mh + 1, mz, rod("up") if level >= 3 else "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    if level >= 3:  # radar dish: ring of slabs on a post, kept inside the roof edge
        px, pz = x0 + 2, z0 + 2
        s.column(px, pz, top + 1, top + 2, wall_post("minecraft:andesite_wall"))
        s.disc(px, pz, top + 3, 1.5, slab("minecraft:polished_andesite_slab", "bottom"))
        s.set(px, top + 3, pz, "minecraft:polished_andesite")
        s.set(px, top + 4, pz, "minecraft:lightning_rod[facing=up,powered=false,waterlogged=false]")
    return hut_x, hut_z


def _launchpad(level):
    if level == 5:
        return launchpad_5()
    p = LP
    s = Structure(f"launchpad{level}")
    half = {1: 6, 2: 7, 3: 8, 4: 9, 5: 10}[level]           # pad 13/15/17/19/21 wide
    r = {1: 2.5, 2: 2.5, 3: 3.5, 4: 3.5, 5: 4.5}[level]       # hull radius
    height = {1: 11, 2: 14, 3: 17, 4: 21, 5: 25}[level]       # hull height above skirt
    ri = int(r)
    # the pad is a little deeper than wide: the rocket and its fins at the north end, the
    # control room at the south end (levels 1-2 need the extra rows for a proper room)
    x0, x1, z0, z1 = -half, half, -(half + 4 if level <= 2 else half + 2), half
    # foundation and pad
    s.box(x0, 0, z0, x1, 0, z1, FOUNDATION)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, 1, z, p["pad"] if (x // 2 + z // 2) % 2 == 0 else p["pad2"])
    s.walls(x0, 1, z0, x1, 1, z1, p["rim"])
    # rocket sits toward the north edge (fins need ri + fh blocks of clearance)
    fh = 3 if r < 3.5 else 4 if r < 4.5 else 5
    rcx, rcz = 0, z0 + ri + fh + 1
    m = ri + 3
    # launch mount: dark square, hazard stripes, raised ring, flame trench
    s.box(rcx - m, 1, rcz - m, rcx + m, 1, rcz + m, "minecraft:gray_concrete")
    for i, x in enumerate(range(rcx - m, rcx + m + 1)):
        for z in (rcz - m, rcz + m):
            s.set(x, 1, z, p["stripe"] if i % 2 == 0 else p["stripe2"])
    for i, z in enumerate(range(rcz - m, rcz + m + 1)):
        for x in (rcx - m, rcx + m):
            s.set(x, 1, z, p["stripe"] if i % 2 == 0 else p["stripe2"])
    hollow_disc(s, rcx, rcz, 2, r + 1.5, p["mount"], inner=r)
    s.disc(rcx, rcz, 1, r + 0.4, p["trench"])
    # stairs onto the mount ring from the south
    s.set(rcx, 2, rcz + ri + 2, stairs(p["mount_stairs"], "north"))
    # fins: three at level 1; from level 2 the gantry stands east, so no east fin; from level 4
    # the side boosters take the east/west places
    fins = ("north", "east", "west") if level == 1 else ("north", "west") if level < 4 else ("north",)
    top = rocket(s, p, rcx, rcz, 3, r, height, level, fins=fins)
    # control building at the south edge
    cw = {1: 3, 2: 3, 3: 3, 4: 3, 5: 4}[level]
    cd = {1: 4, 2: 4, 3: 3, 4: 4, 5: 4}[level]
    ccx = 0 if level <= 2 else x0 + cw + 2
    cz1 = z1 - 1
    cz0 = cz1 - cd
    floors = 1 if level <= 3 else 2
    control_room(s, p, ccx - cw, cz0, ccx + cw, cz1, 1, 3 if level <= 2 else 4, level, floors=floors)
    # walkway from the door to the mount
    for z in range(rcz + ri + 3, cz0):
        s.set(ccx, 1, z, "minecraft:gray_concrete")
    if ccx != rcx:
        for x in range(min(ccx, rcx), max(ccx, rcx) + 1):
            s.set(x, 1, rcz + ri + 3, "minecraft:gray_concrete")
    # gantry tower(s)
    if level >= 2:
        gx = rcx + ri + 3
        gantry(s, p, gx, rcz - 1, 2, height - 1, rcx + ri, level)
    if level >= 4:
        gx = rcx - ri - 5
        gantry(s, p, gx, rcz - 1, 2, height - 5, rcx - ri, level)
    # fuel tanks east of the control room, with a pipe to the mount
    if level >= 3:
        tx, tz = x1 - 2, cz0 - 3
        fuel_tank(s, p, tx, tz, 2, 5 if level == 3 else 7)
        bars_line(s, tx - 2, tz, rcx + m + 1, tz, 2)
        if level >= 4:
            fuel_tank(s, p, tx, tz - 5, 2, 6)
            bars_line(s, tx - 2, tz - 5, tx - 2, tz, 2)
    # side boosters (part of the rocket: they leave with it)
    if level >= 4:
        before = set(s.blocks)
        for bx in (rcx - ri - 1, rcx + ri + 1):
            bz = rcz
            s.disc(bx, bz, 3, 1.5, p["skirt"])
            for y in range(4, 4 + height // 2):
                s.disc(bx, bz, y, 1.5, p["booster"])
            s.disc(bx, bz, 4 + height // 2, 1.5, p["booster_top"])
            s.set(bx, 5 + height // 2, bz, p["booster_top"])
            s.set(bx, 6 + height // 2, bz, p["nose"])
        tag_rocket(s, before)
    # perimeter railing with a gap at the front door, pad lights
    if level >= 2:
        gaps = {(x, z1) for x in range(ccx - 1, ccx + 2)}
        railing(s, x0, z0, x1, z1, 2, gaps=gaps)
    for cx in (x0 + 1, x1 - 1):
        for cz in (z0 + 1, z1 - 1):
            if level >= 3:
                s.column(cx, cz, 2, 4, wall_post("minecraft:andesite_wall"))
                s.set(cx, 5, cz, "minecraft:sea_lantern")
                if level >= 4:
                    s.set(cx, 6, cz, rod("up"))
            else:
                s.set(cx, 2, cz, lantern())
    # runway lights along the west edge
    if level >= 3:
        for z in range(z0 + 3, z1 - 2, 3):
            s.set(x0 + 1, 1, z, "minecraft:sea_lantern")
            s.set(x1 - 1, 1, z, "minecraft:sea_lantern")
    return s


# ================================================================ END GATE
EG_LOW = {  # before the first expedition: overworld stand-ins in End colours
    "pad": "minecraft:smooth_sandstone",
    "pad2": "minecraft:sandstone",
    "rim": "minecraft:magenta_terracotta",
    "ring": "minecraft:magenta_terracotta",
    "ring2": "minecraft:obsidian",
    "ring_accent": "minecraft:amethyst_block",
    "portal": "minecraft:purple_stained_glass",
    "portal2": "minecraft:magenta_stained_glass",
    "portal3": "minecraft:black_stained_glass",
    "pillar": "minecraft:smooth_sandstone",
    "pillar_cap": "minecraft:amethyst_block",
    "light": "minecraft:lantern",
    "wall": "minecraft:smooth_sandstone",
    "wall2": "minecraft:magenta_terracotta",
    "trim": "minecraft:magenta_terracotta",
    "roof": "minecraft:deepslate_tiles",
    "roof_block": "minecraft:deepslate_tiles",
    "roof_stairs": "minecraft:deepslate_tile_stairs",
    "glass": "minecraft:purple_stained_glass",
    "floor": "minecraft:smooth_sandstone",
    "door": "minecraft:dark_oak_door",
    "stairs": "minecraft:smooth_sandstone_stairs",
    "dais": "minecraft:magenta_terracotta",
    "lamp": "minecraft:sea_lantern",
}
EG_HI = dict(EG_LOW, **{  # built from what the Voyager brings home
    "pad": "minecraft:end_stone_bricks",
    "pad2": "minecraft:end_stone",
    "rim": "minecraft:purpur_block",
    "ring": "minecraft:purpur_block",
    "ring2": "minecraft:obsidian",
    "ring_accent": "minecraft:crying_obsidian",
    "portal3": "minecraft:black_stained_glass",
    "pillar": "minecraft:purpur_pillar[axis=y]",
    "pillar_cap": "minecraft:amethyst_block",
    "light": "minecraft:soul_lantern",
    "wall": "minecraft:end_stone_bricks",
    "wall2": "minecraft:purpur_block",
    "trim": "minecraft:purpur_pillar[axis=y]",
    "roof": "minecraft:purpur_block",
    "roof_block": "minecraft:purpur_block",
    "roof_stairs": "minecraft:purpur_stairs",
    "floor": "minecraft:end_stone_bricks",
    "stairs": "minecraft:end_stone_brick_stairs",
    "dais": "minecraft:purpur_block",
})


def vertical_ring(s, block, cx, cy, cz, R, thickness=1.0):
    """Ring in the x/y plane at depth z=cz."""
    for x in range(int(cx - R) - 1, int(cx + R) + 2):
        for y in range(int(cy - R) - 1, int(cy + R) + 2):
            d = math.hypot(x - cx, y - cy)
            if R - thickness < d <= R:
                s.set(x, y, cz, block)


def vertical_disc(s, block, cx, cy, cz, R, inner=-1):
    for x in range(int(cx - R) - 1, int(cx + R) + 2):
        for y in range(int(cy - R) - 1, int(cy + R) + 2):
            d = math.hypot(x - cx, y - cy)
            if inner < d <= R:
                s.set(x, y, cz, block)


def tag_portal(s, cx, cy, gz, R):
    """Tag every pane of the portal film: the building puts an invisible light in front of or
    behind each one, so the gate glows at night without a single visible lamp."""
    for x in range(int(cx - R) - 1, int(cx + R) + 2):
        for y in range(int(cy - R) - 1, int(cy + R) + 2):
            b = s.get(x, y, gz)
            if b and "stained_glass" in b and math.hypot(x - cx, y - cy) <= R:
                s.tag(x, y, gz, "portal")


def spike(s, cx, cy, gz, ox, oy, length, skip_below=None):
    """End-rod spike radiating from the ring along (ox, oy): walks a 4-connected
    path outward from the centre, skips the ring blocks and places `length` rods
    right after the last ring block so nothing floats."""
    if abs(ox) < 1e-9 and abs(oy) < 1e-9:
        return
    facing = ("east" if ox > 0 else "west") if abs(ox) >= abs(oy) else ("up" if oy > 0 else "down")
    if facing == "down" and skip_below is not None:
        return
    x, y = 0, 0
    path = [(0, 0)]
    for _ in range(60):
        # move one cell along the axis that keeps us closest to the ideal ray
        nx, ny = x + (1 if ox > 0 else -1 if ox < 0 else 0), y + (1 if oy > 0 else -1 if oy < 0 else 0)
        cand = []
        if nx != x:
            cand.append((nx, y))
        if ny != y:
            cand.append((x, ny))
        # distance of the candidate from the ray direction (cross product)
        best = min(cand, key=lambda c: abs(c[0] * oy - c[1] * ox))
        x, y = best
        path.append((x, y))
    placed, seen_ring = 0, False
    for dx, dy in path:
        px, py = cx + dx, round(cy) + dy
        if s.get(px, py, gz) is not None:
            seen_ring = True
            continue
        if not seen_ring:
            continue
        if skip_below is not None and py < skip_below:
            return
        s.set(px, py, gz, rod(facing))
        placed += 1
        if placed >= length:
            return


def gate(s, p, cx, gz, y0, R, level):
    """The ring gate standing at z=gz, resting on the pad (y0 = walking level)."""
    cy = y0 + R - 0.5
    thick = 1.0 if level <= 2 else 1.5 if level <= 4 else 2.0
    vertical_ring(s, p["ring"], cx, cy, gz, R + 0.5, thick)
    if level >= 3:  # obsidian outer rim + inner purpur
        vertical_ring(s, p["ring2"], cx, cy, gz, R + 0.5, 0.6)
    # glowing accents at the eight compass points of the ring
    for k in range(8):
        a = k * math.pi / 4
        x = cx + round(math.cos(a) * R); y = round(cy + math.sin(a) * R)
        s.set(x, y, gz, p["ring_accent"])
    if level >= 3:  # end-rod spikes at the four compass points (not below the pad)
        for k in range(0, 8, 2):
            a = k * math.pi / 4
            spike(s, cx, cy, gz, math.cos(a), math.sin(a), 1, skip_below=y0)
    # portal: concentric glass rings just inside the ring
    inner_r = R + 0.5 - thick
    vertical_disc(s, p["portal"], cx, cy, gz, inner_r)
    if inner_r >= 3.5:
        vertical_disc(s, p["portal2"], cx, cy, gz, inner_r - 2.0)
    if inner_r >= 5.0:
        vertical_disc(s, p["portal3"], cx, cy, gz, inner_r - 4.0)
    tag_portal(s, cx, cy, gz, inner_r)
    # feet
    for sx in (-1, 1):
        fx = cx + sx * round(R)
        s.box(fx - 1, y0, gz - 1, fx + 1, y0, gz + 1, p["ring"] if level <= 2 else p["ring2"])
        s.box(fx - 1, y0 + 1, gz - 1, fx + 1, y0 + 1, gz + 1, p["ring"])
    # bottom of the ring buried in the dais: dais 2 wide in front, 1 step up
    return cy


def observatory(s, p, x0, z0, x1, z1, y, height, level):
    """The control house: deepslate/purpur hut with a pitched slab roof, console inside."""
    s.box(x0, y, z0, x1, y, z1, p["floor"])
    s.walls(x0, y + 1, z0, x1, y + height, z1, p["wall"])
    for cx in (x0, x1):
        for cz in (z0, z1):
            s.column(cx, cz, y + 1, y + height, p["trim"])
    for x in range(x0 + 2, x1 - 1, 2):
        s.set(x, y + 2, z0, p["glass"]); s.set(x, y + 2, z1, p["glass"])
    for z in range(z0 + 2, z1 - 1, 2):
        s.set(x0, y + 2, z, p["glass"]); s.set(x1, y + 2, z, p["glass"])
    # pyramid roof of stairs (one block overhang), block core, spire
    ry = y + height + 1
    lvl = 0
    while x0 - 1 + lvl <= x1 + 1 - lvl and z0 - 1 + lvl <= z1 + 1 - lvl:
        ax0, az0, ax1, az1 = x0 - 1 + lvl, z0 - 1 + lvl, x1 + 1 - lvl, z1 + 1 - lvl
        s.box(ax0, ry + lvl, az0, ax1, ry + lvl, az1, p["roof_block"])
        for x in range(ax0, ax1 + 1):
            s.set(x, ry + lvl, az0, stairs(p["roof_stairs"], "south"))
            s.set(x, ry + lvl, az1, stairs(p["roof_stairs"], "north"))
        for z in range(az0 + 1, az1):
            s.set(ax0, ry + lvl, z, stairs(p["roof_stairs"], "east"))
            s.set(ax1, ry + lvl, z, stairs(p["roof_stairs"], "west"))
        lvl += 1
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    s.set(mx, ry + lvl - 1, mz, p["roof_block"])
    s.set(mx, ry + lvl, mz, p["pillar_cap"])
    s.set(mx, ry + lvl + 1, mz, rod("up") if level >= 3 else lantern())
    # door south, lamps
    dx = mx
    s.set(dx, y + 1, z1, door(p["door"], "south", "lower"))
    s.set(dx, y + 2, z1, door(p["door"], "south", "upper"))
    s.set(dx - 2, y + 1, z1 + 1, lantern(p["light"]))
    s.set(dx + 2, y + 1, z1 + 1, lantern(p["light"]))
    # console, racks, decor
    hut_x, hut_z = mx, z0 + 1
    s.set_anchor(hut_x, y + 1, hut_z, HUT)
    s.set(hut_x - 1, y + 1, hut_z, "minecraft:enchanting_table" if level >= 2 else "minecraft:lectern[facing=south,has_book=false,powered=false]")
    s.set(hut_x + 1, y + 1, hut_z, "minecraft:bookshelf")
    s.set(x0 + 1, y + 1, z1 - 1, RACK)
    s.set(x1 - 1, y + 1, z0 + 1, RACK)
    if level >= 3:
        s.set(x0 + 1, y + 1, z0 + 1, RACK)
    s.set(mx, y + height, mz, p["lamp"])
    return hut_x, hut_z


def _endgate(level):
    if level == 5:
        return endgate_5()
    p = EG_LOW if level <= 2 else EG_HI
    s = Structure(f"endgate{level}")
    half = {1: 6, 2: 7, 3: 8, 4: 9, 5: 10}[level]
    R = {1: 4.5, 2: 5.5, 3: 6.5, 4: 7.5, 5: 8.5}[level]
    x0, x1, z0, z1 = -half, half, -(half + 1), half
    s.box(x0, 0, z0, x1, 0, z1, FOUNDATION)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, 1, z, p["pad"] if (x // 2 + z // 2) % 2 == 0 else p["pad2"])
    s.walls(x0, 1, z0, x1, 1, z1, p["rim"])
    # the gate across the north third
    gz = z0 + 4
    cy = gate(s, p, 0, gz, 2, R, level)
    s.tag(0, int(cy), gz, "gate")   # heart of the ring, where the vortex spins
    # dais in front of the gate: raised platform with steps on the south side
    dw = round(R)
    s.box(-dw, 1, gz - 1, dw, 1, gz + 2, p["dais"])
    s.box(-dw + 1, 2, gz + 1, dw - 1, 2, gz + 2, p["dais"])
    for x in range(-dw + 1, dw):
        s.set(x, 2, gz + 3, stairs(p["stairs"], "north"))
    s.tag(0, 3, gz + 2, "departure")
    # flanking pillars with lights (and rods at high levels)
    ph = {1: 5, 2: 6, 3: 8, 4: 9, 5: 11}[level]
    for sx in (-1, 1):
        px = sx * (half - 1)
        pz = gz + 2
        s.column(px, pz, 2, 2 + ph, p["pillar"])
        s.set(px, 3 + ph, pz, p["pillar_cap"])
        s.set(px, 4 + ph, pz, rod("up") if level >= 3 else lantern(p["light"]))
        if level >= 4:
            s.column(px, pz + 5, 2, 2 + ph - 3, p["pillar"])
            s.set(px, ph, pz + 5, p["pillar_cap"])
            s.set(px, ph + 1, pz + 5, rod("up"))
    # observatory at the south edge
    cw = {1: 3, 2: 3, 3: 3, 4: 3, 5: 4}[level]
    cd = {1: 4, 2: 4, 3: 3, 4: 4, 5: 4}[level]
    ccx = 0 if level <= 2 else x0 + cw + 2
    cz1 = z1 - 1
    cz0 = cz1 - cd
    observatory(s, p, ccx - cw, cz0, ccx + cw, cz1, 1, 3 if level <= 2 else 4, level)
    # path from the door to the steps
    for z in range(gz + 4, cz0):
        s.set(ccx, 1, z, p["dais"])
    if ccx != 0:
        for x in range(min(ccx, 0), max(ccx, 0) + 1):
            s.set(x, 1, gz + 4, p["dais"])
    # chorus garden and end-stone islets from level 3
    if level >= 3:
        gx0 = ccx + cw + 2
        i = 0
        for gx in range(gx0, x1, 2):
            gzz = cz0 + (i % 3) - 1
            s.set(gx, 1, gzz, "minecraft:end_stone")
            h = 2 + (i % 3)
            s.column(gx, gzz, 2, 1 + h, CHORUS)
            s.set(gx, 2 + h, gzz, CHORUS_FLOWER)
            i += 1
    if level >= 5:  # floating islets beside the gate
        for sx in (-1, 1):
            ix, iz, iy = sx * (round(R) + 1), gz - 3, int(cy) + 3
            s.disc(ix, iz, iy, 1.5, "minecraft:end_stone")
            s.disc(ix, iz, iy - 1, 0.8, "minecraft:end_stone")
            s.column(ix, iz, iy + 1, iy + 2, CHORUS)
            s.set(ix, iy + 3, iz, CHORUS_FLOWER)
    # pad lights
    for cx in (x0 + 1, x1 - 1):
        for cz in (z0 + 1, z1 - 1):
            if level >= 3:
                s.column(cx, cz, 2, 3, p["pillar"])
                s.set(cx, 4, cz, p["pillar_cap"])
                s.set(cx, 5, cz, rod("up"))
            else:
                s.set(cx, 1, cz, p["lamp"])
    if level >= 2:
        for x in range(x0 + 3, x1 - 2, 3):
            s.set(x, 1, z0 + 1, p["lamp"])
            s.set(x, 1, z1 - 1, p["lamp"])
    return s



# ================================================================ LEVEL 5 SHOWPIECES
def floodlight_tower(s, p, x, z, y0, height):
    s.box(x, y0, z, x + 1, y0 + height, z + 1, p["frame"])
    for cx in (x, x + 1):
        for cz in (z, z + 1):
            s.set(cx, y0 + height + 1, cz, "minecraft:sea_lantern")
    s.set(x, y0 + height + 2, z, rod("up"))
    s.set(x + 1, y0 + height + 2, z + 1, rod("up"))


def water_tower(s, p, cx, cz, y0):
    for dx in (-1, 1):
        for dz in (-1, 1):
            s.column(cx + dx, cz + dz, y0, y0 + 6, wall_post("minecraft:andesite_wall"))
    s.disc(cx, cz, y0 + 7, 2.5, p["tank_cap"])
    for y in range(y0 + 8, y0 + 12):
        s.disc(cx, cz, y, 2.5, p["body"])
    s.disc(cx, cz, y0 + 12, 2.5, p["tank_cap"])
    s.disc(cx, cz, y0 + 13, 1.5, p["tank_cap"])
    s.set(cx, y0 + 14, cz, "minecraft:sea_lantern")


def big_tower(s, p, x, z, y0, height, arm_to_x, crane_len):
    """Full-height service tower with arms every 5 and a crane boom on top."""
    gantry(s, p, x, z, y0, height, arm_to_x, 5)
    direction = -1 if arm_to_x < x else 1
    # crane boom over the rocket
    by = y0 + height + 2
    s.column(x + 1, z + 1, y0 + height + 1, by, p["corner"])
    bx = x + 1
    for k in range(crane_len):
        s.set(bx + direction * k, by, z + 1, p["corner"] if k % 2 == 0 else p["frame2"])
    end_x = bx + direction * (crane_len - 1)
    s.set(bx - direction, by, z + 1, p["corner"])  # counterweight
    s.set(bx - direction * 2, by, z + 1, "minecraft:red_concrete")
    for k in range(1, 4):
        s.set(end_x, by - k, z + 1, "minecraft:chain[axis=y,waterlogged=false]")
    s.set(end_x, by - 4, z + 1, p["corner"])
    s.set(bx, by + 1, z + 1, "minecraft:red_concrete")
    s.set(bx, by + 2, z + 1, rod("up"))


def mission_control(s, p, x0, z0, x1, z1, y, level):
    """Three floors, glass bands, a glass dome, big dish and antenna array. Console on the ground floor."""
    hut_x, hut_z = control_room(s, p, x0, z0, x1, z1, y, 4, level, floors=3)
    top = y + 3 * 5
    mx, mz = (x0 + x1) // 2, (z0 + z1) // 2
    # glass dome on the roof centre
    for i, rr in enumerate((3.5, 3.0, 2.5, 1.5)):
        hollow_disc(s, mx, mz, top + 1 + i, rr, p["glass"], inner=rr - 1.2)
    s.disc(mx, mz, top + 5, 0.9, p["glass"])
    s.set(mx, top + 6, mz, rod("up"))
    # big dish east of the dome
    dx_, dz_ = x1 - 3, z0 + 3
    s.column(dx_, dz_, top + 1, top + 3, wall_post("minecraft:andesite_wall"))
    s.disc(dx_, dz_, top + 4, 2.5, slab("minecraft:polished_andesite_slab", "bottom"))
    s.disc(dx_, dz_, top + 4, 1.2, "minecraft:polished_andesite")
    s.set(dx_, top + 5, dz_, p["corner"])
    s.set(dx_, top + 6, dz_, rod("up"))
    # antenna array west
    for i, ax in enumerate((x0 + 1, x0 + 3)):
        h = 6 + i * 2
        s.column(ax, z1 - 1, top + 1, top + h, connected(p["lattice"]))
        s.set(ax, top + h + 1, z1 - 1, rod("up"))
    return hut_x, hut_z


def launchpad_5():
    p = LP
    s = Structure("launchpad5")
    x0, x1, z0, z1 = -12, 12, -15, 11
    s.box(x0, 0, z0, x1, 0, z1, FOUNDATION)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, 1, z, p["pad"] if (x // 3 + z // 3) % 2 == 0 else p["pad2"])
    s.walls(x0, 1, z0, x1, 1, z1, p["rim"])
    # heavy-lift rocket: first stage r 4.5, second stage r 3.5, capsule r 2.5
    r1, r2, r3 = 4.5, 3.5, 2.5
    ri = 4
    rcx, rcz = 0, z0 + 10
    m = ri + 5
    s.box(rcx - m, 1, rcz - m, rcx + m, 1, rcz + m, "minecraft:gray_concrete")
    for i, x in enumerate(range(rcx - m, rcx + m + 1)):
        for z in (rcz - m, rcz + m):
            s.set(x, 1, z, p["stripe"] if i % 2 == 0 else p["stripe2"])
    for i, z in enumerate(range(rcz - m, rcz + m + 1)):
        for x in (rcx - m, rcx + m):
            s.set(x, 1, z, p["stripe"] if i % 2 == 0 else p["stripe2"])
    # flame trench cross + raised mount ring
    s.box(rcx - 1, 1, rcz - ri - 3, rcx + 1, 1, rcz + ri + 3, p["trench"])
    s.box(rcx - ri - 3, 1, rcz - 1, rcx + ri + 3, 1, rcz + 1, p["trench"])
    s.disc(rcx, rcz, 1, r1 + 0.4, p["trench"])
    hollow_disc(s, rcx, rcz, 2, r1 + 2.5, p["mount"], inner=r1 + 0.4)
    s.set(rcx, 2, rcz + ri + 3, stairs(p["mount_stairs"], "north"))
    s.set(rcx, 2, rcz + ri + 2, p["mount"])
    y0 = 3
    h1, h2, h3 = 14, 13, 5
    # booster base plates belong to the pad (they stay when the rocket leaves)
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        s.disc(rcx + dx * (ri + 1), rcz + dz * (ri + 1), y0 - 1, 1.5, p["mount"])
    rocket_before = set(s.blocks)
    # first stage: skirt = cabin floor, hull above
    s.disc(rcx, rcz, y0, r1, p["skirt2"])
    s.disc(rcx, rcz, y0 - 1, r1 - 1, p["nozzle"])
    for y in range(y0 + 1, y0 + h1 + 1):
        hollow_disc(s, rcx, rcz, y, r1, p["band"] if (y - y0) % 6 == 0 else p["body"])
    hollow_disc(s, rcx, rcz, y0 + 1, r1, p["skirt"])
    # Saturn-style roll pattern on the lower first stage: black quadrants
    for y in range(y0 + 2, y0 + 6):
        for (x, yy, z), b in list(s.blocks.items()):
            if yy == y and b == p["body"] and math.hypot(x - rcx, z - rcz) > r1 - 1.0 and abs(x - rcx) <= ri and abs(z - rcz) <= ri:
                if ((x - rcx) > 0) != ((z - rcz) > 0):
                    s.set(x, yy, z, p["stripe2"])
    for y in range(y0 + 7, y0 + h1 - 1):
        s.set(rcx + ri, y, rcz, p["stripe_r"]); s.set(rcx - ri, y, rcz, p["stripe_r"])
    hollow_disc(s, rcx, rcz, y0 + h1, r1, p["stripe_r"])
    # interstage taper and second stage
    y = y0 + h1 + 1
    s.disc(rcx, rcz, y, r1 - 0.5, p["band"]); y += 1
    for yy in range(y, y + h2):
        hollow_disc(s, rcx, rcz, yy, r2, p["band"] if (yy - y) % 5 == 4 else p["body"])
    for yy in range(y + 2, y + h2 - 2):
        s.set(rcx + 3, yy, rcz, p["stripe_r"]); s.set(rcx - 3, yy, rcz, p["stripe_r"])
    for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        s.set(rcx + dx * 3, y + h2 - 3, rcz + dz * 3, p["window"])
    y += h2
    s.disc(rcx, rcz, y, r2, p["stripe_r"]); y += 1      # red bulkhead: the capsule stands on it
    # capsule
    for yy in range(y, y + h3):
        hollow_disc(s, rcx, rcz, yy, r3, p["body"])
    for dx, dz in ((0, -1), (0, 1), (-1, 0), (1, 0)):
        s.set(rcx + dx * 2, y + 1, rcz + dz * 2, p["window"])
        s.set(rcx + dx * 2, y + 2, rcz + dz * 2, p["window"])
    y += h3
    y = cone(s, rcx, rcz, y, r3 - 0.5, p["body"], p["nose"], "minecraft:quartz_stairs")
    s.set(rcx, y, rcz, rod("up"))
    top_y = y
    # hatch at cabin-floor height, step up from the mount ring, departure inside the cabin
    s.set(rcx, y0 + 1, rcz + ri, door(HATCH, "north", "lower"))
    s.set(rcx, y0 + 2, rcz + ri, door(HATCH, "north", "upper"))
    s.set(rcx, y0, rcz + ri + 1, stairs(p["mount_stairs"], "north"))
    s.tag(rcx, y0 + 1, rcz, "departure")
    # four strap-on boosters on the diagonals (south stays free for the crew)
    bh = h1 - 4
    for dx, dz in ((1, 1), (-1, 1), (1, -1), (-1, -1)):
        bx, bz = rcx + dx * (ri + 1), rcz + dz * (ri + 1)
        s.disc(bx, bz, y0, 1.5, p["skirt2"]); s.disc(bx, bz, y0 + 1, 1.5, p["skirt"])
        for yy in range(y0 + 2, y0 + bh):
            s.disc(bx, bz, yy, 1.5, p["band"] if (yy - y0) % 6 == 0 else p["booster"])
        s.disc(bx, bz, y0 + bh, 1.5, p["stripe_r"])
        s.disc(bx, bz, y0 + bh + 1, 1.0, p["booster_top"])
        s.set(bx, y0 + bh + 2, bz, p["booster_top"])
        s.set(bx, y0 + bh + 3, bz, p["nose"])
    tag_rocket(s, rocket_before)
    # towers: east full height with crane, west two thirds
    # crane boom stops two blocks short of the capsule (r3), so the hook never hangs on the rocket
    big_tower(s, p, rcx + ri + 5, rcz - 1, 2, y0 + h1 + h2 + 2, rcx + ri + 2, ri + 3)
    gantry(s, p, rcx - ri - 7, rcz - 1, 2, y0 + h1 + 2, rcx - ri - 2, 5)
    # mission control south-west
    cw, cd = 4, 4
    ccx = x0 + cw + 1
    cz1 = z1 - 1
    cz0 = cz1 - cd
    mission_control(s, p, ccx - cw, cz0, ccx + cw, cz1, 1, 5)
    # roads: door -> mount, yellow centre line
    for z in range(rcz + ri + 4, cz0):
        s.set(ccx, 1, z, "minecraft:gray_concrete")
    for x in range(ccx, rcx + 1):
        s.set(x, 1, rcz + ri + 4, "minecraft:gray_concrete")
        if x % 2 == 0:
            s.set(x, 1, rcz + ri + 5, p["stripe"])
    # fuel farm east with pipes
    tx = x1 - 3
    for i, tz in enumerate((cz1 - 2, cz1 - 7, cz1 - 12)):
        fuel_tank(s, p, tx, tz, 2, 7)
        if i < 2:
            bars_line(s, tx, tz - 2, tx, tz - 3, 2)
    bars_line(s, tx - 2, cz1 - 7, rcx + m + 1, cz1 - 7, 2)
    bars_line(s, rcx + m + 1, cz1 - 7, rcx + m + 1, rcz + m + 1, 2)
    # water tower north-west, floodlight towers at the corners
    water_tower(s, p, x0 + 3, z0 + 3, 2)
    for cx in (x0 + 1, x1 - 2):
        for cz in (z0 + 1, z1 - 2):
            if (cx, cz) == (x0 + 1, z0 + 1):
                continue
            floodlight_tower(s, p, cx, cz, 2, 7)
    # railing and runway lights
    gaps = {(x, z1) for x in range(ccx - 1, ccx + 2)}
    railing(s, x0, z0, x1, z1, 2, gaps=gaps)
    for z in range(z0 + 4, z1 - 3, 3):
        s.set(x0 + 1, 1, z, "minecraft:sea_lantern"); s.set(x1 - 1, 1, z, "minecraft:sea_lantern")
    for x in range(x0 + 4, x1 - 3, 3):
        s.set(x, 1, z0 + 1, "minecraft:sea_lantern")
    return s


CHORUS = "minecraft:chorus_plant"
CHORUS_FLOWER = "minecraft:chorus_flower[age=5]"   # age 5 = grown out: never ticks, so the trees keep their shape


def chorus_tree(s, x, y, z, height, seed=0, dirs=((1, 0), (-1, 0), (0, 1), (0, -1))):
    """A chorus plant that survives Minecraft's rules: the stem stands on end stone, branches
    grow only from the TOP block of the stem (a plant block with plant above and below may not
    have side branches), one branch keeps climbing. `dirs` limits the branch directions so
    neighbouring plants never touch."""
    top = y + height
    s.column(x, z, y, top, CHORUS)
    dirs = list(dirs)
    count = min(len(dirs), 2 + (seed % 2))
    for k in range(count):
        dx, dz = dirs[(seed + k) % len(dirs)]
        bx, bz = x + dx, z + dz
        s.set(bx, top, bz, CHORUS)
        if k == 0 and height >= 3:
            s.set(bx, top + 1, bz, CHORUS)
            s.set(bx, top + 2, bz, CHORUS_FLOWER)
        else:
            s.set(bx, top + 1, bz, CHORUS_FLOWER)


def connect_chorus(s):
    """Give every chorus plant its connection state so it renders joined up the moment it is placed."""
    plants = [p for p, b in s.blocks.items() if b.startswith(CHORUS)]
    for (x, y, z) in plants:
        props = []
        for name, (dx, dy, dz) in (("down", (0, -1, 0)), ("up", (0, 1, 0)), ("north", (0, 0, -1)),
                                   ("south", (0, 0, 1)), ("west", (-1, 0, 0)), ("east", (1, 0, 0))):
            n = s.get(x + dx, y + dy, z + dz) or ""
            joined = n.startswith("minecraft:chorus") or (name == "down" and n == "minecraft:end_stone")
            props.append(f"{name}={'true' if joined else 'false'}")
        s.set(x, y, z, CHORUS + "[" + ",".join(props) + "]")


def islet(s, cx, cz, y, r):
    s.disc(cx, cz, y, r, "minecraft:end_stone")
    s.disc(cx, cz, y - 1, r - 1.0, "minecraft:end_stone")
    if r >= 2.5:
        s.disc(cx, cz, y - 2, max(r - 2.0, 0.5), "minecraft:end_stone")
    chorus_tree(s, cx, y + 1, cz, 3 if r < 2.5 else 4, seed=abs(cx * 7 + cz))


def spire_tower(s, p, x, z, y0, height):
    s.box(x, y0, z, x + 1, y0 + 2, z + 1, p["wall"])
    for cx in (x, x + 1):
        for cz in (z, z + 1):
            s.column(cx, cz, y0 + 3, y0 + height, p["pillar"])
    s.box(x, y0 + height + 1, z, x + 1, y0 + height + 1, z + 1, p["pillar_cap"])
    s.set(x, y0 + height + 2, z, rod("up")); s.set(x + 1, y0 + height + 2, z + 1, rod("up"))


def round_observatory(s, p, cx, cz, y, level):
    """Round two-floor tower with a stair-dressed dome and an amethyst spire."""
    r = 3.5
    s.disc(cx, cz, y, r, p["floor"])
    for yy in range(y + 1, y + 9):
        hollow_disc(s, cx, cz, yy, r, p["wall"])
    for yy in (y + 2, y + 6):
        for dx, dz in ((0, -1), (-1, 0), (1, 0)):
            s.set(cx + dx * 3, yy, cz + dz * 3, p["glass"])
    s.disc(cx, cz, y + 5, r, p["floor"])  # first floor
    s.set(cx + 2, y + 5, cz + 1, None)     # ladder hole, ladder leans on the wall block east of it
    for i in range(4):
        s.set(cx + 2, y + 1 + i, cz + 1, ladder("west"))
    s.disc(cx, cz, y + 9, r + 0.5, p["wall2"])  # eave disc = ceiling of the top floor
    yy = cone(s, cx, cz, y + 10, r, p["roof_block"], p["pillar_cap"], p["roof_stairs"], layers_per_step=1)
    s.set(cx, yy, cz, rod("up"))
    # door south + lamps
    s.set(cx, y + 1, cz + 3, door(p["door"], "south", "lower"))
    s.set(cx, y + 2, cz + 3, door(p["door"], "south", "upper"))
    s.set(cx - 2, y + 1, cz + 4, lantern(p["light"])); s.set(cx + 2, y + 1, cz + 4, lantern(p["light"]))
    # console against the north wall, racks, lore
    s.set_anchor(cx, y + 1, cz - 2, HUT)
    s.set(cx - 1, y + 1, cz - 2, "minecraft:enchanting_table")
    s.set(cx + 1, y + 1, cz - 2, "minecraft:bookshelf")
    s.set(cx - 2, y + 1, cz, RACK); s.set(cx + 2, y + 1, cz - 1, RACK); s.set(cx - 2, y + 1, cz + 1, RACK)
    s.set(cx, y + 4, cz, p["lamp"]); s.set(cx, y + 8, cz, p["lamp"])
    s.set(cx - 1, y + 6, cz - 1, "minecraft:bookshelf"); s.set(cx + 1, y + 6, cz - 1, "minecraft:bookshelf")
    s.set(cx, y + 6, cz - 2, "minecraft:lectern[facing=south,has_book=true,powered=false]")


def endgate_5():
    p = EG_HI
    s = Structure("endgate5")
    x0, x1, z0, z1 = -14, 14, -16, 11
    s.box(x0, 0, z0, x1, 0, z1, FOUNDATION)
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            s.set(x, 1, z, p["pad"] if (x // 3 + z // 3) % 2 == 0 else p["pad2"])
    s.walls(x0, 1, z0, x1, 1, z1, p["rim"])
    R = 10.5
    gz = z0 + 5
    y0 = 2
    cy = y0 + R - 0.5
    # void pool under the ring line
    s.box(x0 + 1, 1, gz - 2, x1 - 1, 1, gz + 2, "minecraft:black_stained_glass")
    # the great ring: purpur core, obsidian rim, crying-obsidian chevrons with end-rod spikes
    vertical_ring(s, p["ring"], 0, cy, gz, R + 0.5, 2.0)
    s.tag(0, int(cy), gz, "gate")
    vertical_ring(s, p["ring2"], 0, cy, gz, R + 0.5, 0.7)
    for zz in (gz - 1, gz + 1):  # ring depth: purpur behind and in front of the rim
        vertical_ring(s, p["ring"], 0, cy, zz, R + 0.5, 1.2)
    for k in range(8):
        a = k * math.pi / 4
        x = round(math.cos(a) * R); y = round(cy + math.sin(a) * R)
        s.box(x - (1 if math.cos(a) < 0 else 0), y - (1 if math.sin(a) < 0 else 0), gz - 1, x + (0 if math.cos(a) < 0 else 1), y + (0 if math.sin(a) < 0 else 1), gz + 1, p["ring_accent"])
    for k in range(8):
        a = k * math.pi / 4
        spike(s, 0, cy, gz, math.cos(a), math.sin(a), 2, skip_below=y0)
    inner_r = R + 0.5 - 2.0
    vertical_disc(s, p["portal"], 0, cy, gz, inner_r)
    vertical_disc(s, p["portal2"], 0, cy, gz, inner_r - 2.5)
    vertical_disc(s, "minecraft:purple_stained_glass", 0, cy, gz, inner_r - 4.5)
    vertical_disc(s, p["portal3"], 0, cy, gz, inner_r - 6.0)
    tag_portal(s, 0, cy, gz, inner_r)
    # feet
    for sx in (-1, 1):
        fx = sx * 10
        s.box(fx - 2, y0, gz - 2, fx + 2, y0 + 1, gz + 2, p["ring2"])
        s.box(fx - 1, y0 + 2, gz - 1, fx + 1, y0 + 2, gz + 1, p["ring"])
    # three-tier dais with steps, departure on top
    s.box(-8, 1, gz + 1, 8, 2, gz + 5, p["dais"])
    s.box(-6, 3, gz + 1, 6, 3, gz + 4, p["dais"])
    s.box(-4, 4, gz + 1, 4, 4, gz + 3, p["dais"])
    for x in range(-8, 9):
        s.set(x, 2, gz + 6, stairs(p["stairs"], "north"))
    for x in range(-6, 7):
        s.set(x, 3, gz + 5, stairs(p["stairs"], "north"))
    for x in range(-4, 5):
        s.set(x, 4, gz + 4, stairs(p["stairs"], "north"))
    s.tag(0, 5, gz + 2, "departure")
    for x in (-4, 4):
        s.set(x, 5, gz + 3, rod("up"))
    # floating islets with chorus trees
    islet(s, -10, gz - 3, 16, 2.5)
    islet(s, 10, gz - 2, 21, 2.5)
    islet(s, -9, gz + 8, 13, 1.5)
    islet(s, 9, gz + 9, 24, 1.5)
    # corner spire towers
    for cx in (x0 + 1, x1 - 2):
        for cz in (z0 + 1, z1 - 2):
            spire_tower(s, p, cx, cz, 2, 10)
    # round observatory south-west, chorus garden south-east
    round_observatory(s, p, x0 + 6, z1 - 5, 1, 5)
    for i, gx in enumerate(range(3, x1 - 2, 2)):
        gzz = z1 - 3 - (i % 3)
        s.set(gx, 1, gzz, "minecraft:end_stone")
        chorus_tree(s, gx, 2, gzz, 2 + (i % 3), seed=i, dirs=((0, 1), (0, -1)))
    # path and rim lights
    for z in range(gz + 7, z1 - 5):
        s.set(x0 + 6, 1, z, p["dais"])
    for x in range(x0 + 6, 1):
        s.set(x, 1, gz + 7, p["dais"])
    for x in range(x0 + 4, x1 - 3, 4):
        s.set(x, 2, z0 + 1, p["pillar"]); s.set(x, 3, z0 + 1, rod("up"))
    return s


def _named(s, name):
    """Structurize shows the two looks as alternatives of one 'Departure Point' entry in the
    build tool and labels each alternative with the 'name=' tag on the anchor block."""
    s.tag(*s.anchor, "name=" + name)
    connect_chorus(s)
    return s


def launchpad(level):
    return _named(_launchpad(level), "Launchpad")


def endgate(level):
    return _named(_endgate(level), "End Gate")


if __name__ == "__main__":
    for lv in range(1, 6):
        for fn in (launchpad, endgate):
            st = fn(lv)
            print(st.name, st.size(), len(st.blocks), "anchor", st.anchor, "tags", st.tags)
