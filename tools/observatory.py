"""The Observatory - five looks, five levels each, as voxel structures.

Same conventions as the Departure Point (see designs.py): front is SOUTH (+z), y=0 is the
foundation course, the floor surface is y=1 and people walk on y=2. The hut block is the
astronomer's desk and it stands against the north wall of the ground floor, facing into the room.

Five looks, the way the Departure Point ships a Launchpad and an End Gate: one building, one
profession, pick the one that suits your town in the build tool.

  observatory  the Copper Dome   - pale stone, a verdigris dome, a refractor on a fork
  keep         Stargazer's Keep  - a medieval stone hall with a round tower and an armillary
  sandcourt    the Sand Court    - a desert court with a great stone gnomon, no roof at all
  station      Skyward Station   - a white dome on a modern block, with a radio dish
  array        Aperture Array    - a great open ring on two pylons, on a dark seamed block

Each look keeps its own five levels inside the same box, aligned on its own hut block, so an
upgrade never appears to move the building - the requirement Marko set for the Departure Point.

THE PLAN EVERY LOOK SHARES (tools/access.py is the judge, and every design passes it):

  the study      the ground-floor room with the desk. Work row against the north wall (racks,
                 chart table, analyser, the hut block, the architect's cutter, bookshelf), a
                 free row in front of it, the astronomer's bed in the south-east corner, the
                 door in the middle of the south wall. From level 3 a ladder up the inside of
                 the east wall goes through the roof.
  the darkroom   from level 2, a windowless wing built AGAINST the study's west wall and entered
                 from inside the study - the first version stood apart with a door into thin
                 air. Lightrooms along its outer wall, each with its own printing lamp.
  the roof room  from level 3 the dome (or the Keep's tower) stands on the study's flat roof,
                 with the sky floor, the instrument and the console inside it, and its door on
                 the east side, where the ladder comes up. The first version dropped the drum
                 through the study and cut the room into pockets nobody could walk between.

Exposure's own blocks do the work inside every look. The Observatory is only registered when
Exposure and Exposure: Space are installed (docs/OBSERVATORY.md section 5), so the blueprints are
free to depend on them: exposure_space:analyzer is literally the astronomer's desk - its own model
file says so - night_analyzer is the console under the dome, exposure:lightroom is what the dark
room is *for*, and the photograph frames on the wall are Exposure's hanging entities (not blocks:
exposure:photograph_frame_* exist only as entity models, a blueprint naming them as blocks places
nothing).

Domum Ornamentum does the stonework. It is a hard dependency of MineColonies, so its blocks are in
every colony already, and its mix-and-match shapes (shingles, the vanilla stairs/slab/wall shapes,
square columns, timber frames) let one shape wear any material. Those keep their material in a
block entity, which voxel.py writes - see Structure.mixed. A material outside Domum Ornamentum's
tag for that slot renders as missing and nothing warns you, so every material here is one
MineColonies' own blueprints use.
"""
import math

from voxel import Structure
import walkcheck
from designs import stairs, slab, door, ladder, rod, lantern, hollow_disc, railing

HUT = "voyager:blockhutobservatory[facing=north]"
RACK = "minecolonies:blockminecoloniesrack"
FOUNDATION = "structurize:blocksolidsubstitution"

# The shared box. Level 5 of the tallest look sets the height and its terrace the width; every
# other level and look sits inside it, in the same place.
BOX = dict(half_x=11, z0=-12, z1=12, height=26)

# Direction -> Minecraft's 3D data value, what a hanging entity's "Facing" byte holds.
FACING3D = {"down": 0, "up": 1, "north": 2, "south": 3, "west": 4, "east": 5}


def lightroom(facing):
    return f"exposure:lightroom[facing={facing},printing=false]"


def r_int(r):
    return int(math.floor(r + 0.5))


# ---------------------------------------------------------------- the palettes

# What every look shares: the mod blocks it is built around and the Domum Ornamentum shape ids.
COMMON = {
    "stone_st": "domum_ornamentum:vanilla_stairs_compat",
    "stone_sl": "domum_ornamentum:vanilla_slab_compat",
    "stone_wl": "domum_ornamentum:vanilla_wall_compat",
    "colonne":  "domum_ornamentum:squarepillar",
    "shingle":  "domum_ornamentum:shingle",
    "window":   "domum_ornamentum:framed",
    "barrel":   "domum_ornamentum:blockbarreldeco_standing[facing=north]",
    "cutter":   "domum_ornamentum:architectscutter[facing=south]",
    "dark":     "minecraft:tinted_glass",
    "lens":     "minecraft:tinted_glass",
    "mount":    "minecraft:lodestone",
    "chart":    "minecraft:cartography_table",
    "books":    "minecraft:bookshelf",
    "desk":     "minecraft:lectern[facing=south,has_book=true,powered=false]",
    "spark":    "minecraft:end_rod",
    "analyzer": "exposure_space:analyzer[lit=false]",
    "console":  "exposure_space:night_analyzer",
    # The astronomer lives here (colony/ObservatoryModules.java): the study has their bed. Each
    # look overrides the colour to something its palette agrees with.
    "bed":      "minecraft:blue_bed",
}


def _pal(**kw):
    p = dict(COMMON)
    p.update(kw)
    return p


# The Copper Dome. Pale warm stone so the verdigris reads, deepslate underfoot, copper as an
# accent and nothing more - the first render came back almost entirely orange.
COPPER = _pal(
    floor="minecraft:polished_deepslate", floor2="minecraft:deepslate_tiles",
    wall="domum_ornamentum:beige_stone_bricks", wall2="domum_ornamentum:cream_stone_bricks",
    base="domum_ornamentum:black_brick_extra", sky="domum_ornamentum:blue_brick_extra",
    drum="domum_ornamentum:beige_stone_bricks",
    trim="minecraft:waxed_weathered_cut_copper", ring="minecraft:waxed_oxidized_cut_copper",
    trim_sl="minecraft:waxed_weathered_cut_copper_slab",
    chis="minecraft:waxed_chiseled_copper", grate="minecraft:waxed_oxidized_copper_grate",
    dome="minecraft:waxed_oxidized_copper",
    glass="minecraft:glass_pane", light="minecraft:soul_lantern", lamp="minecraft:amethyst_block",
    bulb="minecraft:waxed_copper_bulb[lit=true,powered=false]",
    door="minecraft:dark_oak_door", step="minecraft:deepslate_brick_stairs",
    slabd="minecraft:deepslate_brick_slab", rail="minecraft:deepslate_brick_wall",
    pillar="minecraft:deepslate_brick_wall",
    scope="minecraft:waxed_weathered_copper", scope2="minecraft:waxed_weathered_cut_copper",
    bed="minecraft:blue_bed",
    m_body="domum_ornamentum:beige_stone_bricks", m_trim="domum_ornamentum:cream_stone_bricks",
    m_dark="minecraft:polished_deepslate", m_roof="minecraft:deepslate_tiles",
    m_pale="minecraft:calcite", m_sup="minecraft:polished_deepslate",
)

# Stargazer's Keep. Grey-brown stone, red tile, spruce beams: a tower a town would have built
# three hundred years before anybody ground a lens. Its instrument is an armillary sphere.
KEEP = _pal(
    floor="minecraft:cobblestone", floor2="minecraft:stone_bricks",
    wall="minecraft:stone_bricks", wall2="minecraft:chiseled_stone_bricks",
    base="minecraft:polished_blackstone_bricks", sky="minecraft:blue_terracotta",
    drum="minecraft:stone_bricks",
    trim="minecraft:polished_blackstone", ring="minecraft:mossy_stone_bricks",
    trim_sl="minecraft:stone_brick_slab",
    chis="minecraft:chiseled_stone_bricks", grate="minecraft:iron_bars",
    dome="minecraft:bricks",
    glass="minecraft:glass_pane", light="minecraft:lantern", lamp="minecraft:glowstone",
    bulb="minecraft:glowstone",
    door="minecraft:spruce_door", step="minecraft:stone_brick_stairs",
    slabd="minecraft:stone_brick_slab", rail="minecraft:cobblestone_wall",
    pillar="minecraft:cobblestone_wall",
    scope="minecraft:waxed_cut_copper", scope2="minecraft:waxed_exposed_cut_copper",
    bed="minecraft:red_bed",
    m_body="domum_ornamentum:brown_stone_bricks", m_trim="domum_ornamentum:brown_bricks",
    m_dark="minecraft:polished_blackstone", m_roof="minecraft:bricks",
    m_pale="minecraft:calcite", m_sup="minecraft:spruce_planks",
)

# The Sand Court. No dome and no roof over the instrument at all: the great gnomon stands in an
# open court, the way Jaipur and Samarkand did it. Sandstone, and one cool blue for the tilework.
SAND = _pal(
    floor="minecraft:smooth_red_sandstone", floor2="minecraft:cut_red_sandstone",
    wall="minecraft:smooth_sandstone", wall2="minecraft:chiseled_sandstone",
    base="minecraft:cut_sandstone", sky="minecraft:cyan_terracotta",
    drum="minecraft:smooth_sandstone",
    trim="minecraft:cyan_terracotta", ring="minecraft:light_blue_terracotta",
    tile="minecraft:light_blue_glazed_terracotta[facing=north]",
    trim_sl="minecraft:cut_sandstone_slab",
    chis="minecraft:chiseled_sandstone", grate="minecraft:iron_bars",
    dome="minecraft:smooth_sandstone",
    glass="minecraft:glass_pane", light="minecraft:lantern", lamp="minecraft:glowstone",
    bulb="minecraft:glowstone",
    door="minecraft:acacia_door", step="minecraft:sandstone_stairs",
    slabd="minecraft:smooth_red_sandstone_slab", rail="minecraft:sandstone_wall",
    pillar="minecraft:sandstone_wall",
    scope="minecraft:waxed_exposed_copper", scope2="minecraft:waxed_oxidized_copper",
    bed="minecraft:cyan_bed",
    m_body="domum_ornamentum:sand_stone_bricks", m_trim="domum_ornamentum:beige_bricks",
    m_dark="minecraft:smooth_sandstone", m_roof="minecraft:cut_sandstone",
    m_pale="minecraft:calcite", m_sup="minecraft:smooth_sandstone",
)

# Skyward Station. Quartz, glass and a white dome, with a dish on the roof - the look that sits
# next to the Launchpad's purpur rather than next to the town's timber.
STATION = _pal(
    floor="minecraft:light_gray_concrete", floor2="minecraft:smooth_stone",
    wall="minecraft:smooth_quartz", wall2="minecraft:quartz_bricks",
    base="minecraft:polished_deepslate", sky="minecraft:blue_concrete",
    drum="minecraft:quartz_block",
    trim="minecraft:waxed_exposed_cut_copper", ring="minecraft:waxed_exposed_copper",
    trim_sl="minecraft:smooth_quartz_slab",
    chis="minecraft:chiseled_quartz_block", grate="minecraft:waxed_exposed_copper_grate",
    dome="minecraft:quartz_block",
    glass="minecraft:glass_pane", light="minecraft:soul_lantern", lamp="minecraft:sea_lantern",
    bulb="minecraft:waxed_copper_bulb[lit=true,powered=false]",
    door="minecraft:warped_door", step="minecraft:smooth_quartz_stairs",
    slabd="minecraft:smooth_quartz_slab", rail="minecraft:iron_bars",
    pillar="minecraft:quartz_pillar",
    scope="minecraft:smooth_quartz", scope2="minecraft:waxed_exposed_copper",
    bed="minecraft:light_gray_bed",
    m_body="minecraft:calcite", m_trim="minecraft:smooth_stone",
    m_dark="minecraft:polished_deepslate", m_roof="minecraft:deepslate_tiles",
    m_pale="minecraft:calcite", m_sup="minecraft:polished_deepslate",
)


# Aperture Array. No dome, no tube: a great open ring standing on two pylons with a tinted lens
# in it, on a dark block seamed with light. Purpur ties it to the Launchpad and to the End the
# Voyager comes home from, and it is the one look that could not have been built by hand.
VOID = _pal(
    floor="minecraft:polished_deepslate", floor2="minecraft:deepslate_tiles",
    wall="minecraft:deepslate_tiles", wall2="minecraft:purpur_block",
    base="minecraft:polished_blackstone", sky="minecraft:blue_concrete",
    drum="minecraft:purpur_pillar",
    trim="minecraft:purpur_block", ring="minecraft:amethyst_block",
    trim_sl="minecraft:purpur_slab",
    chis="minecraft:chiseled_deepslate", grate="minecraft:waxed_oxidized_copper_grate",
    dome="minecraft:tinted_glass",
    glass="minecraft:gray_stained_glass_pane", light="minecraft:soul_lantern",
    lamp="minecraft:sea_lantern", bulb="minecraft:sea_lantern",
    door="minecraft:mangrove_door", step="minecraft:deepslate_tile_stairs",
    slabd="minecraft:deepslate_tile_slab", rail="minecraft:iron_bars",
    pillar="minecraft:purpur_pillar",
    scope="minecraft:purpur_block", scope2="minecraft:amethyst_block",
    bed="minecraft:purple_bed",
    m_body="domum_ornamentum:gray_brick_extra", m_trim="domum_ornamentum:light_blue_brick_extra",
    m_dark="minecraft:polished_blackstone", m_roof="minecraft:deepslate",
    m_pale="minecraft:quartz_block", m_sup="minecraft:polished_blackstone",
)


# ---------------------------------------------------------------- Domum Ornamentum helpers

def bed(block, facing, part):
    """One half of a bed. Both halves have to agree on facing or Minecraft drops them."""
    return f"{block}[facing={facing},occupied=false,part={part}]"


def do_stairs(s, p, x, y, z, facing, material=None, half="bottom"):
    s.mixed(x, y, z, stairs(p["stone_st"], facing, half), material or p["m_trim"])


def do_slab(s, p, x, y, z, material=None, kind="bottom"):
    s.mixed(x, y, z, slab(p["stone_sl"], kind), material or p["m_trim"])


def column(s, p, x, z, y, height, material=None):
    """A square column: base, shaft, capital - a real column, in any stone."""
    material = material or p["m_dark"]
    s.mixed(x, y, z, p["colonne"] + "[column=pillar_base]", material)
    for yy in range(y + 1, y + height):
        s.mixed(x, yy, z, p["colonne"] + "[column=pillar_column]", material)
    s.mixed(x, y + height, z, p["colonne"] + "[column=pillar_capital]", material)


def frame(s, x, y, z, facing, size=0):
    """A photograph frame on a wall: Exposure's hanging entity, in the block in front of the wall.

    Not a block. exposure:photograph_frame_small/medium/large are entity models only, and the
    first blueprints named them as blocks - Structurize logs "invalid block" and places air, so
    every gallery wall was bare. `facing` is the way the picture looks, away from the wall it
    hangs on; `size` 0 is one block square, 1 two, 2 three. The builder asks the colony for one
    photograph frame per entity, the way it asks for an item frame in MineColonies' own designs.
    """
    import nbtlib.tag as T
    yaw = {"south": 0.0, "west": 90.0, "north": 180.0, "east": 270.0}[facing]
    s.entity(x, y, z, "exposure:photograph_frame", yaw=yaw,
             extra={"Facing": T.Byte(FACING3D[facing]), "Size": T.Byte(size), "__tile__": True})


# ---------------------------------------------------------------- shared pieces

def terrace(s, p, half, y, gate=True, gate_w=1):
    """The apron the whole building stands on.

    A low kerb of slabs rather than a wall all the way round: a wall on a small platform reads as
    a pen, a kerb with columns at the corners reads as a terrace you are meant to walk on. The
    columns carry the lights, which is how you find the place at night - the only time anyone
    here is working. The kerb is the only way in that matters: a citizen steps up one block, and
    a slab on the edge is that block, so the gate (2*gate_w+1 wide, with a stair outside it) is
    the way in. The Sand Court opens its whole south side.
    """
    s.box(-half, 0, -half, half, 0, half, FOUNDATION)
    for x in range(-half, half + 1):
        for z in range(-half, half + 1):
            s.set(x, y, z, p["floor"] if (x + z) % 2 == 0 else p["floor2"])
    edge = [(x, -half) for x in range(-half, half + 1)]
    edge += [(x, half) for x in range(-half, half + 1) if not (gate and abs(x) <= gate_w)]
    edge += [(-half, z) for z in range(-half + 1, half)]
    edge += [(half, z) for z in range(-half + 1, half)]
    for x, z in edge:
        s.set(x, y + 1, z, slab(p["slabd"]))
        if abs(x) == half and abs(z) == half:
            column(s, p, x, z, y + 1, 2)
            s.set(x, y + 4, z, lantern(p["light"]))
    if gate:
        for x in range(-gate_w, gate_w + 1):
            s.set(x, y, half + 1, stairs(p["step"], "north"))


def rose(s, p, cx, cz, y, r):
    """A compass rose in the paving: the reason the terrace is as big as it is.

    A colony's observatory is where the town's bearings come from, and a big empty apron reads as
    unfinished. This costs nothing but paving that was going to be there anyway.
    """
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            dx, dz = x - cx, z - cz
            d = math.hypot(dx, dz)
            if d > r + 0.5:
                continue
            arm = (dx == 0 or dz == 0)
            diag = abs(dx) == abs(dz)
            if arm and d > 1:
                s.set(x, y, z, p["trim"])
            elif diag and 1 < d < r * 0.7:
                s.set(x, y, z, p["wall2"])
            elif r - 1 < d <= r + 0.5:
                s.set(x, y, z, p["wall2"])
    s.set(cx, y, cz, p["lamp"])
    for dx, dz in ((0, -r), (0, r), (-r, 0), (r, 0)):
        s.set(cx + dx, y, cz + dz, p["ring"])


def band(s, p, x0, z0, x1, z1, y, block):
    """One course all the way round a rectangle - a string course or a cornice."""
    s.walls(x0, y, z0, x1, y, z1, block)


def flat_roof(s, p, x0, z0, x1, z1, y):
    """A flat roof with a stone cornice and a one-block eave.

    Flat because a dome wants a platform, and because the roof is where the astronomer works
    before the colony can afford one. Slabs for the eave, not top-half stairs: a top stair leaves
    its lower half open and the first render had a black slot running right round the building.
    """
    s.box(x0, y, z0, x1, y, z1, slab(p["slabd"]))
    for x in range(x0 - 1, x1 + 2):
        do_slab(s, p, x, y, z0 - 1)
        do_slab(s, p, x, y, z1 + 1)
        do_slab(s, p, x, y, z0)
        do_slab(s, p, x, y, z1)
    for z in range(z0, z1 + 1):
        do_slab(s, p, x0 - 1, y, z)
        do_slab(s, p, x1 + 1, y, z)
        do_slab(s, p, x0, y, z)
        do_slab(s, p, x1, y, z)


def parapet(s, p, x0, z0, x1, z1, y):
    """Battlements round a flat roof: every other block of the cornice carried one higher."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x in (x0, x1) or z in (z0, z1)) and (x + z) % 2 == 0:
                s.set(x, y + 1, z, p["wall2"])


def pitched(s, p, x0, z0, x1, z1, y):
    """A tiled gable over a rectangle, overhanging a block on every side.

    Solid under the tiles rather than a shell of stairs: two courses of stairs on a 45-degree
    slope touch only at their corners, so a hollow one is a roof floating over a hole - the check
    found eighteen such blocks the first time, and a hanging lantern with nothing above it. The
    stairs are the surface; the course under them is the roof itself.
    """
    x0, x1 = x0 - 1, x1 + 1
    for i in range((z1 - z0) // 2 + 1):
        zn, zs, yy = z0 + i, z1 - i, y + i
        if zn > zs:
            break
        for x in range(x0, x1 + 1):
            for z in range(zn, zs + 1):
                s.set(x, yy, z, p["m_roof"])
            if zn == zs:
                do_slab(s, p, x, yy, zn, p["m_roof"])
            else:
                s.mixed(x, yy, zn, stairs(p["shingle"], "south"), p["m_roof"], p["m_sup"])
                s.mixed(x, yy, zs, stairs(p["shingle"], "north"), p["m_roof"], p["m_sup"])
    for x in range(x0, x1 + 1):
        do_slab(s, p, x, y, z0 - 1, p["m_roof"])
        do_slab(s, p, x, y, z1 + 1, p["m_roof"])


def room(s, x0, y0, z0, x1, y1, z1, kind):
    """Remember a room's box, so later pieces (the meridian posts) keep out of it."""
    if not hasattr(s, "rooms"):
        s.rooms = []
    s.rooms.append((min(x0, x1), y0, min(z0, z1), max(x0, x1), y1, max(z0, z1), kind))


def in_room(s, x, z, y=None):
    for (x0, y0, z0, x1, y1, z1, _kind) in getattr(s, "rooms", ()):
        if x0 <= x <= x1 and z0 <= z <= z1 and (y is None or y0 <= y <= y1):
            return True
    return False


def study(s, p, x0, z0, x1, z1, y, height, level, roofer=flat_roof, windows=True, annex=None,
          ladder_up_z=None):
    """The working room: the desk, the plates, the charts, the analyser - and the bed.

    Outer walls inclusive. The plan, row by row from the north wall (see the module docstring):
    the work row, then free floor, then the south row with the door in the middle, the lectern
    beside it and the bed in the east corner. exposure_space:analyzer goes on the desk from level
    1 - it is the block whose own model file calls it the astronomer's desk - so the first thing
    the colony builds already does the work.

    `annex` names the side ("west"/"east") the darkroom is built against: that wall gets no
    windows (a window into a darkroom is a window into a spoiled plate) and the square inside the
    darkroom's door stays free. `ladder_up_z` is the row of the ladder up the east wall - that
    wall gets no window there, a ladder does not hold on a pane.

    The bed is placed here, by plan, not found by search: the search put it outside under the
    eave at level 1 and across the door's inside square at level 2, and a bed in a doorway seals
    the room.
    """
    room(s, x0, y, z0, x1, y + height, z1, "study")
    s.box(x0, y, z0, x1, y, z1, p["floor"])
    for yy in range(y + 1, y + height):
        s.walls(x0, yy, z0, x1, yy, z1, p["wall"])
    band(s, p, x0, z0, x1, z1, y + 1, p["wall2"])                 # a plinth course
    for qx, qz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):       # and quoins at the corners
        for yy in range(y + 1, y + height):
            s.set(qx, yy, qz, p["wall2"])
    if roofer is not None:
        roofer(s, p, x0, z0, x1, z1, y + height)

    cx = (x0 + x1) // 2
    xi0, xi1, zi0, zi1 = x0 + 1, x1 - 1, z0 + 1, z1 - 1          # the interior
    zmid = (z0 + z1) // 2
    s.set(cx, y + 1, z1, door(p["door"], "south", "lower"))
    s.set(cx, y + 2, z1, door(p["door"], "south", "upper"))
    s.set(cx, y + 3, z1, p["chis"])
    if windows:
        def window(wx, wz):
            s.set(wx, y + 1, wz, p["wall2"])
            s.set(wx, y + 2, wz, p["glass"])
            if y + 3 < y + height - 1:
                s.set(wx, y + 3, wz, p["glass"])
        for dx in (-3, -2, 2, 3):
            if x0 < cx + dx < x1:
                window(cx + dx, z1)
        for zz in (zmid, zmid + 1):
            if annex != "west":
                window(x0, zz)
            if annex != "east" and zz != ladder_up_z:
                window(x1, zz)

    # the work row, against the north wall
    s.set_anchor(cx, y + 1, zi0, HUT)
    s.set(cx - 1, y + 1, zi0, p["analyzer"])
    s.set(cx + 1, y + 1, zi0, p["cutter"])
    s.set(xi0, y + 1, zi0, RACK)
    s.set(xi1, y + 1, zi0, RACK)
    s.set(cx, y + height - 1, zmid, lantern(p["light"], hanging=True))
    if level >= 2:
        s.set(cx + 2, y + 1, zi0, p["books"])
        s.set(cx - 2, y + 1, zi0, p["chart"])
    # the south row: the door's inside square (cx) stays free, the lectern beside it
    if level >= 3:
        s.set(cx - 2, y + 1, zi1, p["desk"])
    if level >= 4:
        s.set(xi0, y + 1, zi0 + 1, "minecraft:enchanting_table")
    # more storage as the building grows: racks down the side walls, off the door rows and off
    # the ladder's square
    if level >= 4:
        for zz in range(zi0 + 2, zi1 - 1):
            s.set(xi0, y + 1, zz, RACK)
            if zz != ladder_up_z:
                s.set(xi1, y + 1, zz, RACK)
    # the astronomer's bed, in the south-east corner, head to the east wall
    s.set(xi1 - 1, y + 1, zi1, bed(p["bed"], "east", "foot"))
    s.set(xi1, y + 1, zi1, bed(p["bed"], "east", "head"))
    s.tag(xi1 - 1, y + 1, zi1, "bed")
    # the gallery: photographs over the work row, facing into the room. One frame from the first
    # level - the astronomer hangs their prints in it (alpha.21) - then the wall fills as the
    # building grows: three at level 3, five at level 5.
    frame(s, cx, y + 2, zi0, "south")
    if level >= 3:
        for dx in (-2, 2):
            frame(s, cx + dx, y + 2, zi0, "south")
    if level >= 5:
        for dx in (-4, 4):
            frame(s, cx + dx, y + 2, zi0, "south")
    return (x0, z0, x1, z1)


def darkroom(s, p, x0, z0, x1, z1, y, height, rooms=1, door_side="east"):
    """A windowless wing with one door, opening from inside the study: where plates become photographs.

    Outer walls inclusive, exactly four wide: the outer wall, the lightrooms' column, a free
    column to work from, and the wall it shares with the study. That shared wall is the study's
    own and is not drawn here - only the door goes into it, at the front row. `door_side` is the
    side the study is on. The rack takes the back row, the lightrooms the column along the outer
    wall, and the barrel the front row when a row is left over.

    Exposure's lightroom will not print below light level 13, read at the block above it
    (Config.Server.LIGHTROOM_LIGHT_REQUIREMENT). A soul lantern gives 10 and the ceiling is tinted
    glass on purpose, so every lightroom gets its own lamp set into the outer wall right beside the
    square above it: a full-strength block one step away is 14, and a plate still develops in
    the dark because the lamp is the block's own printing light, not daylight.
    """
    assert x1 - x0 == 3, f"darkroom must be 4 wide: x{x0}..{x1}"
    assert z1 - z0 >= 4, f"darkroom too shallow: z{z0}..{z1}"
    assert door_side in ("east", "west")
    room(s, x0, y, z0, x1, y + height, z1, "darkroom")
    if door_side == "east":
        outer, lx, fx, inner = x0, x0 + 1, x0 + 2, x1
    else:
        outer, lx, fx, inner = x1, x1 - 1, x1 - 2, x0
    own = (outer, lx, fx)
    facing = "east" if door_side == "east" else "west"           # the lightroom's front faces the room
    for x in own:
        for z in range(z0, z1 + 1):
            s.set(x, y, z, p["floor2"])
            on_wall = (x == outer or z in (z0, z1))
            for yy in range(y + 1, y + height):
                if on_wall:
                    s.set(x, yy, z, p["wall2"] if yy == y + 1 else p["base"])
            s.set(x, y + height, z, slab(p["trim_sl"]) if on_wall else p["dark"])
    # the door, in the shared wall, at the front row - the square inside it stays free
    s.set(inner, y + 1, z1 - 1, door(p["door"], facing, "lower"))
    s.set(inner, y + 2, z1 - 1, door(p["door"], facing, "upper"))
    s.set(fx, y + height - 1, (z0 + z1) // 2, lantern(p["light"], hanging=True))
    s.set(lx, y + 1, z0 + 1, RACK)                                # back row
    depth = (z1 - 1) - (z0 + 2) + 1
    assert rooms <= depth, f"darkroom at z{z0}..{z1} holds {depth} lightrooms, not {rooms}"
    for i in range(rooms):
        zz = z0 + 2 + i
        s.set(lx, y + 1, zz, lightroom(facing))
        s.set(outer, y + 2, zz, p["bulb"])                        # the printing lamp, set into the wall
        if i == 0:
            s.tag(lx, y + 1, zz, "darkroom")                       # where the plates are developed
    if z0 + 2 + rooms <= z1 - 1:
        s.set(lx, y + 1, z1 - 1, p["barrel"])                     # front row, if a row is left


def annex(s, p, box, y, height, rooms=1, side="west"):
    """The darkroom built against the study's side wall, sharing it, entered from the study."""
    (sx0, sz0, sx1, sz1) = box
    if side == "west":
        darkroom(s, p, sx0 - 3, sz0, sx0, sz1, y, height, rooms=rooms, door_side="east")
    else:
        darkroom(s, p, sx1, sz0, sx1 + 3, sz1, y, height, rooms=rooms, door_side="west")


def star_floor(s, p, cx, cz, y, r):
    """The sky, laid into the floor under the dome: a compass rose on night-blue."""
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            if (x - cx) ** 2 + (z - cz) ** 2 > r * r:
                continue
            d = math.hypot(x - cx, z - cz)
            on_arm = (x == cx or z == cz or abs(x - cx) == abs(z - cz))
            s.set(x, y, z, p["trim"] if on_arm and d > 1 else p["sky"])
    s.set(cx, y, cz, p["lamp"])


def ladder_up(s, p, x, z, y, height, facing="west"):
    """A ladder up the inside of the east wall, and the hatch it comes out of.

    Rungs from the floor to the top course of the wall, and the roof slab above them taken out:
    the citizen climbs out of the hatch onto the roof. The rungs stop at the wall, never on the
    roof slab - a ladder does not hold on a slab's side.
    """
    for i in range(height - 1):
        s.set(x, y + 1 + i, z, ladder(facing))
    s.set(x, y + height, z, None)


def telescope(s, p, cx, cz, y, length, lean=1, toward=-1):
    """The instrument: a pier, a yoke and a tube lying along the sky.

    Two upright versions were rendered before this one and both came back as a copper chimney: a
    one-block-wide vertical stack has no telescope in its silhouette. What reads instantly is the
    long, near-horizontal tube of a refractor on a fork - a cannon on a mount. `toward` is the
    direction the tube points, -1 north (through the dome's slit), +1 south (out over the
    terrace, on the levels where it stands in the open).
    """
    s.tag(cx, y, cz, "scope")
    s.set(cx, y, cz, p["mount"])
    s.set(cx, y + 1, cz, p["m_dark"])
    for dx in (-1, 1):
        column(s, p, cx + dx, cz, y, 1)
    tube = y + 2
    s.set(cx, tube, cz - 2 * toward, p["scope2"])                 # the eyepiece and counterweight
    s.set(cx, tube, cz - toward, p["scope"])
    s.set(cx, tube, cz, p["scope"])
    zz = cz
    for _ in range(length):
        zz += toward
        s.set(cx, tube, zz, p["scope"])
    if lean:
        s.set(cx, tube + 1, zz, p["scope2"])
        s.set(cx, tube + 1, zz + toward, p["lens"])
        return tube + 1, zz + toward
    s.set(cx, tube, zz + toward, p["lens"])
    return tube, zz + toward


def dome(s, p, cx, cz, y, r):
    """A dome, not a cone, and not a lumpy one either.

    Every course is a clean circle of *whole* radius, and the radius never steps in by more than
    one block a course. Taking the radius straight off the circle gave courses whose radii
    differed by a tenth of a block, so single cells flicked in and out of the shell and the render
    came back looking chewed. The profile is a half-ellipse a third taller than a hemisphere,
    which is what an observatory dome looks like and gives the crown enough courses to turn over.
    """
    h = max(2, int(round(r * 1.35)))
    radii, j = [], 0
    while True:
        f = min(1.0, j / h)
        rad = max(0, int(r * math.sqrt(max(0.0, 1.0 - f * f)) + 0.5))
        if radii and radii[-1] - rad > 1:
            rad = radii[-1] - 1
        radii.append(rad)
        if rad <= 1:
            break
        j += 1
    radii = [x for x in radii if x >= 3] + [2]                    # a flat crown, not a spike
    for k, rad in enumerate(radii):
        yy = y + k
        if rad >= 4:
            s.disc(cx, cz, yy, rad, p["dome"], ring=True, inner=rad - 3)
        else:
            s.disc(cx, cz, yy, max(rad, 1), p["dome"])
    top = y + len(radii) - 1
    s.set(cx, top + 1, cz, p["lamp"])
    return top + 2


def drum_and_dome(s, p, cx, cz, y, r, height, slit=True):
    """A round drum with a dome on it, standing on the study's roof.

    `y` is the roof: the drum's first course sits in the roof's slab layer and the sky floor is
    laid inside it, so the observing floor is level with the roof outside. The door faces EAST,
    onto the strip of roof the ladder's hatch opens on - the first version opened south, straight
    off the eave into thin air, and the astronomer could never get in. The slit faces north and
    starts at head height, so it is a shutter for the tube and not a way off the roof.
    """
    room(s, cx - r_int(r), y, cz - r_int(r), cx + r_int(r), y + height, cz + r_int(r), "dome")
    star_floor(s, p, cx, cz, y, int(r))
    for yy in range(y, y + height):
        hollow_disc(s, cx, cz, yy, r, p["drum"])
    hollow_disc(s, cx, cz, y + 1, r, p["wall2"])
    # The turning ring projects half a block past the drum, so it must be a *band* and not a
    # one-block rim: the inner half sits on the drum and carries the outer half.
    hollow_disc(s, cx, cz, y + height, r + 0.5, p["ring"], inner=r - 1.0)
    top = dome(s, p, cx, cz, y + height + 1, r)
    for dx in (-r_int(r), r_int(r)):                              # vents: a dome has to breathe
        s.set(cx + dx, y + height + 1, cz, p["grate"])
    if slit:
        # The slit stops well below the crown. A shutter that ran clean over the apex would take
        # the dome's last course and its tip with it and leave the finial standing on air.
        cut = y + height + 1 + int(r * 0.45)
        for zz in range(cz - r_int(r), cz + 1):
            for yy in range(y + 2, min(top - 3, cut)):
                for dx in (0, 1):
                    s.set(cx + dx, yy, zz, None)
    xd = cx + int(r)
    s.set(xd, y + 1, cz, door(p["door"], "east", "lower"))
    s.set(xd, y + 2, cz, door(p["door"], "east", "upper"))
    s.set(xd, y + 3, cz, p["chis"])
    for dz in (-1, 1):
        s.set(xd, y + 2, cz + dz, p["bulb"])
    return top


def meridian(s, p, half, y, n=8, material=None):
    """The ring of little columns round a finished observatory.

    A meridian circle is a real instrument and also the cheapest way to make a building look like
    it belongs to a discipline: eight posts on a circle, each with a light on top.
    """
    r = half - 2
    for k in range(n):
        a = 2 * math.pi * k / n
        x = int(round(math.cos(a) * r))
        z = int(round(math.sin(a) * r))
        if abs(z - (half - 2)) < 2 and abs(x) < 3:
            continue                                              # leave the doorway clear
        if in_room(s, x, z) or any(in_room(s, x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1)):
            continue                                              # never inside, or against, a room
        column(s, p, x, z, y + 1, 2, material or p["m_pale"])
        s.set(x, y + 4, z, lantern(p["light"]))


def circle(s, cx, cy, cz, r, block, plane="xy"):
    """A one-block-thick circle, drawn column by column.

    A circle sampled by angle gives cells that only meet at their corners, which is a ring of
    floating blocks - the check found twelve of them in the first armillary. Stepping along one
    axis and filling the gap in the other gives a ring where every cell shares a face with the
    next. `plane` is "xy" for a ring standing up, "xz" for one lying flat.
    """
    def put(a, b):
        if plane == "xy":
            s.set(cx + a, cy + b, cz, block)
        else:
            s.set(cx + a, cy, cz + b, block)
    prev = None
    for i in range(-r, r + 1):
        h = int(round(math.sqrt(max(0.0, r * r - i * i))))
        if prev is not None:
            lo, hi = sorted((h, prev))
            for v in range(lo, hi + 1):
                put(i, v)
                put(i, -v)
        put(i, h)
        put(i, -h)
        prev = h


# ---------------------------------------------------------------- Stargazer's Keep

def spire(s, p, cx, cz, y, r):
    """A tiled cone on a round tower: the medieval answer to a dome.

    Solid, dressed with shingles on the rim, for the same reason the gable roof is solid - a cone
    of stairs on a 45-degree slope is a shell touching only at its corners.
    """
    rad, yy = int(r), y
    while rad >= 1:
        s.disc(cx, cz, yy, rad, p["m_roof"])
        for x in range(cx - rad, cx + rad + 1):
            for z in range(cz - rad, cz + rad + 1):
                d = math.hypot(x - cx, z - cz)
                if rad - 1 < d <= rad:
                    dx, dz = x - cx, z - cz
                    facing = ("west" if dx > 0 else "east") if abs(dx) >= abs(dz) else ("north" if dz > 0 else "south")
                    s.mixed(x, yy, z, stairs(p["shingle"], facing), p["m_roof"], p["m_sup"])
        rad -= 1
        yy += 1
    s.set(cx, yy, cz, p["lamp"])
    s.set(cx, yy + 1, cz, rod("up"))
    return yy + 2


def tower(s, p, cx, cz, y, r, height, level):
    """A round stone tower with a battlemented parapet and a tiled spire, on the hall's roof.

    The shuttered opening faces north, the same job the dome's slit does: the astronomer opens the
    shutters and the sky is there. The tower is what the town sees from the fields. `y` is the
    roof it stands on; the floor inside is laid level with it, and the door faces east onto the
    ladder's hatch, like the dome's. The first Keep dropped the tower through the hall from the
    ground and its round wall cut the room into pockets nobody could walk between.
    """
    room(s, cx - r_int(r), y, cz - r_int(r), cx + r_int(r), y + height, cz + r_int(r), "tower")
    s.disc(cx, cz, y, r - 1, p["floor2"])
    s.set(cx, y, cz, p["lamp"])
    for yy in range(y, y + height):
        hollow_disc(s, cx, cz, yy, r, p["wall"])
    hollow_disc(s, cx, cz, y + 1, r, p["wall2"])
    hollow_disc(s, cx, cz, y + height, r + 0.5, p["wall2"], inner=r - 1.0)   # the corbel
    s.disc(cx, cz, y + height, r - 1, p["floor2"])                # ... and the ceiling it rings
    # battlements: every other block of the corbel course carried one higher
    for x in range(cx - r_int(r) - 1, cx + r_int(r) + 2):
        for z in range(cz - r_int(r) - 1, cz + r_int(r) + 2):
            d = math.hypot(x - cx, z - cz)
            if r - 1.0 < d <= r + 0.5 and (x + z) % 2 == 0:
                s.set(x, y + height + 1, z, p["wall2"])
    top = spire(s, p, cx, cz, y + height + 1, r - 1)
    # the shutters: two courses of the north wall taken out and framed in stone, from head height
    for zz in range(cz - r_int(r), cz):
        for yy in range(y + 2, y + height):
            for dx in (0, 1):
                s.set(cx + dx, yy, zz, None)
    for dx in (-1, 2):
        for yy in range(y + 2, y + height):
            s.set(cx + dx, yy, cz - r_int(r) + 1, p["chis"])
    xd = cx + int(r)
    s.set(xd, y + 1, cz, door(p["door"], "east", "lower"))
    s.set(xd, y + 2, cz, door(p["door"], "east", "upper"))
    s.set(xd, y + 3, cz, p["chis"])
    for dz in (-1, 1):
        s.set(xd, y + 2, cz + dz, lantern(p["light"]))
    return top


def armillary(s, p, cx, cz, y, r):
    """An armillary sphere on a stone pedestal.

    Two rings and an axis: the meridian standing in the plane you look through, the equator lying
    flat, the earth in the middle. It is the instrument a colony would build before it could grind
    a lens, and it is the one astronomical object everybody recognises at a glance.
    """
    s.tag(cx, y, cz, "scope")
    for yy in range(y, y + 3):                                    # a plinth you can see
        s.disc(cx, cz, yy, 1.4, p["wall2"])
    base = y + 3
    cy = base + r
    for yy in range(base, cy + 1):                                # the axis, holding it all up
        s.set(cx, yy, cz, p["scope2"])
    circle(s, cx, cy, cz, r, p["scope"], "xy")                    # the meridian ring, standing
    circle(s, cx, cy, cz, r, p["scope2"], "xz")                   # the equator ring, lying flat
    s.set(cx, cy, cz, p["lamp"])                                  # the earth
    return cy + r


def quadrant(s, p, cx, cz, y, r):
    """A mural quadrant: a quarter circle of graduated stone standing on the terrace.

    The instrument level 1 and 2 can afford, and the thing that tells a player this yard belongs
    to somebody who measures the sky rather than somebody who keeps bees.
    """
    s.tag(cx, y, cz, "scope")
    for yy in (y, y + 1):
        s.box(cx - 1, yy, cz - 1, cx + 1, yy, cz + 1, p["wall2"])
    base = y + 2
    prev = None
    for i in range(r + 1):
        h = int(round(math.sqrt(max(0.0, r * r - i * i))))
        if prev is not None:
            for yy in range(h, prev + 1):                         # keep the arc unbroken
                s.set(cx, base + yy, cz - i, p["scope"])
        s.set(cx, base + h, cz - i, p["scope"])
        prev = h
    for i in range(r + 1):                                        # the two straight limbs
        s.set(cx, base, cz - i, p["scope2"])
    for h in range(r + 1):
        s.set(cx, base + h, cz, p["scope2"])
    s.set(cx, base, cz + 1, p["lamp"])
    return base + r


# ---------------------------------------------------------------- the Sand Court

def gnomon(s, p, cx, cz, y, size, width=3, plate=3):
    """The great sundial: a stone wedge whose sloping edge points at the pole.

    A Samrat Yantra, and the reason this look has no dome. It is the biggest single shape in any
    of the five, and from across the valley it is unmistakable. Its foot is at `cz` and it climbs
    northward to `size` high at cz-size; the shadow plate lies south of the foot, in the paving.
    """
    s.tag(cx, y, cz, "scope")
    half = width // 2
    for i in range(size + 1):
        for x in range(cx - half, cx + half + 1):
            for yy in range(y, y + i):
                s.set(x, yy, cz - i, p["wall"])
            if i:
                s.mixed(x, y + i - 1, cz - i, stairs(p["stone_st"], "south"), p["m_body"])
    for x in range(cx - half, cx + half + 1):                     # the shadow plate at its foot
        for z in range(cz + 1, cz + 1 + plate):
            s.set(x, y - 1, z, p["trim"])
    return y + size


def dial(s, p, cx, cz, y, r, zmax=None):
    """The graduated dial the gnomon's shadow is read against, laid into the paving.

    The first Sand Court flanked the gnomon with two tall quadrant walls and they rendered as a
    pair of archways - they competed with the wedge instead of serving it. On the ground they do
    the same job and leave the silhouette to the one shape that matters. `zmax` clips it to the
    terrace - paving past the kerb is paving in the air.
    """
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
            if zmax is not None and z > zmax:
                continue
            d = math.hypot(x - cx, z - cz)
            if r - 1 < d <= r:
                s.set(x, y, z, p["wall2"])
            elif r - 2 < d <= r - 1 and (x + z) % 3 == 0:
                s.set(x, y, z, p["ring"])                          # a mark every third degree
    for z in range(cz - r + 1, cz + 1):
        s.set(cx, y, z, p["trim"])                                 # the noon line


def chhatri(s, p, cx, cz, y, r=1):
    """A little domed pavilion on four columns - shade, and somewhere to sit and watch."""
    for dx in (-r - 1, r + 1):
        for dz in (-r - 1, r + 1):
            column(s, p, cx + dx, cz + dz, y, 2)
    top = y + 3
    s.box(cx - r - 1, top, cz - r - 1, cx + r + 1, top, cz + r + 1, p["wall2"])
    dome(s, p, cx, cz, top + 1, r + 1.0)
    s.set(cx, top - 1, cz, lantern(p["light"], hanging=True))
    return top


def arcade(s, p, x0, x1, z, y, height=3):
    """A row of columns with arches between them, along the south side of the court.

    The columns stand at +-2, +-5, +-8 ... so the three-wide way in through the middle is never
    blocked: the first arcade put a column at x=1, half in the way of everyone coming in.
    """
    cols = sorted(x for x in range(-2, x0 - 1, -3) if x >= x0) + sorted(x for x in range(2, x1 + 1, 3))
    for x in cols:
        column(s, p, x, z, y, height - 1)
        s.set(x, y + height, z, p["wall2"])                       # the impost the arches spring from
    for a, b in zip(cols, cols[1:]):
        gap = b - a - 1
        if gap <= 0:
            continue
        s.mixed(a + 1, y + height, z, stairs(p["stone_st"], "west", "top"), p["m_body"])
        s.mixed(b - 1, y + height, z, stairs(p["stone_st"], "east", "top"), p["m_body"])
        for x in range(a + 2, b - 1):
            s.set(x, y + height, z, p["wall2"])
            if "tile" in p:
                s.set(x, y + height + 1, z, p["tile"])


# ---------------------------------------------------------------- Skyward Station

def dish(s, p, cx, cz, y, r, height=4):
    """A radio dish on a mast: a bowl standing on edge, looking south at the sky.

    The first one was built lying flat with a scalloped stair rim and rendered as a white flower.
    A dish reads as a dish when you see its face, so this one stands up.
    """
    for yy in range(y, y + height):
        s.set(cx, yy, cz, p["pillar"])
    cy = y + height + r
    for i in range(-r, r + 1):                                    # the reflector, face on
        for v in range(-r, r + 1):
            if math.hypot(i, v) <= r:
                s.set(cx + i, cy + v, cz, p["dome"])
    circle(s, cx, cy, cz, r, p["scope2"], "xy")                   # the rim
    for yy in range(y + height, cy - r + 1):                      # the mast up to the bowl
        s.set(cx, yy, cz, p["pillar"])
    s.set(cx, cy, cz + 1, p["scope2"])                            # the boom
    s.set(cx, cy, cz + 2, p["lamp"])                              # ... and the feed horn
    return cy + r


def deck(s, p, x0, z0, x1, z1, y, rail=False):
    """A paved court of slabs. `rail` fences its whole edge - only for a deck up on a roof.

    On the ground the first version railed the south side with a gap and the render came back
    with an iron fence running straight through the middle of the yard, so a ground deck is
    paving and nothing else.
    """
    s.box(x0, y, z0, x1, y, z1, slab(p["slabd"]))
    if rail:
        railing(s, x0, z0, x1, z1, y + 1, p["rail"])


# ---------------------------------------------------------------- the Aperture Array

def aperture(s, p, cx, cz, y, r):
    """The great ring: a lens the size of a house, standing on two pylons and looking north-up.

    The one instrument in the five that is not a copy of something a person has actually built.
    It reads at any distance because it is a hole in the sky - and because nothing else in a
    colony is a circle standing on its edge.
    """
    s.tag(cx, y, cz, "scope")
    cy = y + 1 + r
    for yy in range(y, cy - r + 1):                               # the mast the ring stands on
        s.set(cx, yy, cz, p["pillar"])
    for dx in (-r - 1, r + 1):                                    # the two pylons
        for yy in range(y, cy + 1):
            s.set(cx + dx, yy, cz, p["pillar"])
        s.set(cx + dx, cy + 1, cz, p["ring"])
        s.set(cx + dx, cy + 2, cz, rod("up"))
    for i in range(-r + 1, r):                                    # the lens inside it
        for v in range(-r + 1, r):
            if math.hypot(i, v) <= r - 1:
                s.set(cx + i, cy + v, cz, p["dome"])
    circle(s, cx, cy, cz, r, p["trim"], "xy")                     # the ring itself
    circle(s, cx, cy, cz, r - 1, p["ring"], "xy")                 # ... and its glowing inner rim
    s.set(cx, cy, cz, p["lamp"])                                  # the core
    return cy + r


def seams(s, p, x0, z0, x1, z1, y):
    """Light let into the paving: the detail that says the floor is a machine, not a floor."""
    for x in range(x0, x1 + 1):
        for z in range(z0, z1 + 1):
            if (x % 4 == 0) != (z % 4 == 0):
                s.set(x, y, z, p["floor2"])
            elif x % 4 == 0 and z % 4 == 0:
                s.set(x, y, z, p["lamp"])


# ---------------------------------------------------------------- the frame every look shares

# Per level: the terrace's half-width, the study's box (outer walls, x0 z0 x1 z1), its height,
# how many lightrooms the darkroom holds, and the roof room's radius and drum height. The
# study's depth from level 3 is what the drum needs: its ring must sit on the roof and its eave,
# never past them, or a course hangs in the air over the door.
LEVELS = {
    1: dict(half=5,  box=(-3, -5, 3, -1),   h=5, rooms=0, r=None, dh=None),
    2: dict(half=7,  box=(-4, -6, 4, -1),   h=5, rooms=1, r=None, dh=None),
    3: dict(half=8,  box=(-5, -7, 5, -2),   h=5, rooms=1, r=3.5,  dh=3),
    4: dict(half=9,  box=(-6, -9, 6, -3),   h=6, rooms=2, r=4.5,  dh=4),
    5: dict(half=11, box=(-7, -10, 7, -2),  h=7, rooms=3, r=5.5,  dh=5),
}


def hall(s, p, level, roofer=flat_roof, windows=True):
    """The study, its darkroom wing (from level 2) and its ladder (from level 3): every look's core.

    Returns what the roof pieces need: the study box, the roof's y, the drum's centre and the
    hatch's position.
    """
    L = LEVELS[level]
    x0, z0, x1, z1 = L["box"]
    y = 1
    side = "west" if L["rooms"] else None
    # the roof room's centre row, and the ladder one row south of it: the hatch comes out beside
    # the roof room's door, never in front of it
    cz = (z0 + z1) // 2 if (z1 - z0) % 2 else (z0 + z1 + 1) // 2
    hatch_z = cz + 1 if level >= 3 else None
    box = study(s, p, x0, z0, x1, z1, y, L["h"], level, roofer=roofer, windows=windows,
                annex=side, ladder_up_z=hatch_z)
    if L["rooms"]:
        annex(s, p, box, y, 4, rooms=L["rooms"], side=side)
    roof_y = y + L["h"]
    hatch = None
    if level >= 3:
        ladder_up(s, p, x1 - 1, hatch_z, y, L["h"])
        hatch = (x1 - 1, roof_y, hatch_z)
    return box, roof_y, (0, cz), hatch


def roof_room(s, p, level, roof_y, centre, kind="dome"):
    """The observing room on the roof: the Copper Dome's drum, or the Keep's tower.

    Puts the telescope, the console and (from level 4) an analyser inside, all on the sky floor
    and all clear of the corridor from the door round to the console.
    """
    L = LEVELS[level]
    cx, cz = centre
    r, dh = L["r"], L["dh"]
    if kind == "tower":
        # The Keep's instrument is the armillary in the yard; the tower is the watch room, with
        # the console under the shutters and a lectern for the night's log.
        top = tower(s, p, cx, cz, roof_y, r, dh + 2, level)
        s.set(cx, roof_y + 1, cz - 2, p["console"])
        s.set(cx - 2, roof_y + 1, cz, p["desk"])
    else:
        top = drum_and_dome(s, p, cx, cz, roof_y, r, dh)
        s.set(cx, top, cz, rod("up"))
        telescope(s, p, cx, cz, roof_y + 1, max(1, level - 2), lean=1, toward=-1)
        s.set(cx - 2, roof_y + 1, cz - 1, p["console"])
    if level >= 4:
        s.set(cx + 2, roof_y + 1, cz - 1, p["analyzer"])
    if level >= 5:
        s.set(cx - 3, roof_y + 1, cz + 1, p["console"])
        s.set(cx + 3, roof_y + 1, cz + 1, p["analyzer"])
    return top


def yard_scope(s, p, level, cx, cz):
    """The refractor standing in the open on the terrace at levels 1 and 2, on a low plinth."""
    y = 1
    s.box(cx - 1, y + 1, cz, cx + 1, y + 1, cz + 1, p["trim"])
    telescope(s, p, cx, cz, y + 2, 1 if level == 1 else 2, lean=1, toward=+1)


# ---------------------------------------------------------------- look 1: the Copper Dome

def _copper(level):
    p, s = COPPER, Structure(f"observatory{level}")
    L = LEVELS[level]
    half, y = L["half"], 1
    terrace(s, p, half, y)
    box, roof_y, centre, hatch = hall(s, p, level, roofer=pitched if level <= 2 else flat_roof)

    if level == 1:
        yard_scope(s, p, level, 3, 2)
        rose(s, p, -2, 3, y, 2)
    elif level == 2:
        yard_scope(s, p, level, 3, 3)
        rose(s, p, -2, 3, y, 3)
    else:
        roof_room(s, p, level, roof_y, centre)
        rose(s, p, 0, 4 if level < 5 else 5, y, level - 1)
        if level >= 4:
            for x in (-box[2], box[2]):
                column(s, p, x, box[3] + 3, y + 1, 2)
                s.set(x, y + 4, box[3] + 3, lantern(p["light"]))
        if level == 5:
            meridian(s, p, half, y)
    return s


# ---------------------------------------------------------------- look 2: Stargazer's Keep

def _keep(level):
    p, s = KEEP, Structure(f"keep{level}")
    L = LEVELS[level]
    half, y = L["half"], 1
    terrace(s, p, half, y)
    box, roof_y, centre, hatch = hall(s, p, level, roofer=pitched if level <= 2 else flat_roof)

    if level == 1:
        quadrant(s, p, 3, 4, y + 1, 3)
        rose(s, p, -2, 3, y, 2)
    elif level == 2:
        quadrant(s, p, 3, 5, y + 1, 4)
        rose(s, p, -2, 3, y, 3)
    else:
        parapet(s, p, box[0], box[1], box[2], box[3], roof_y)
        roof_room(s, p, level, roof_y, centre, kind="tower")
        armillary(s, p, {3: 3, 4: 5, 5: 6}[level], 4, y + 1, 2 if level < 5 else 3)
        rose(s, p, -3, 4 if level < 5 else 5, y, level - 1)
        if level == 5:
            meridian(s, p, half, y, material=p["m_dark"])
    return s


# ---------------------------------------------------------------- look 3: the Sand Court

def _sand(level):
    p, s = SAND, Structure(f"sandcourt{level}")
    L = LEVELS[level]
    half, y = L["half"], 1
    terrace(s, p, half, y, gate_w=half - 1)                       # the whole south side is steps
    box, roof_y, centre, hatch = hall(s, p, level)
    # The gnomon's ridge climbs north and stops a row short of the door's own row - the first
    # one ran its tallest course straight into the door. Its foot is wherever that puts it.
    size = {1: 3, 2: 4, 3: 5, 4: 6, 5: 8}[level]
    foot = box[3] + (2 if level == 1 else 3) + size
    width = 3 if level <= 3 else 5
    gnomon(s, p, 0, foot, y + 1, size, width=width, plate=max(1, half - foot))
    dial(s, p, 0, foot, y, min(4, level + 1), zmax=half)
    if level >= 3:
        arcade(s, p, -(half - 3), half - 3, half - 1, y + 1)
        # the roof is the second observing floor: the console up there, in the open air
        s.set(centre[0] - 2, roof_y + 1, centre[1], p["console"])
        railing(s, box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1, roof_y + 1, p["rail"])
    if level >= 4:
        s.set(centre[0] + 2, roof_y + 1, centre[1], p["analyzer"])
        for x in (-(half - 3), half - 3):
            chhatri(s, p, x, 3, y + 1, 1 if level == 4 else 2)
    if level == 5:
        meridian(s, p, half, y, material=p["m_dark"])
    rose(s, p, {1: -4, 2: -5, 3: -6, 4: -6, 5: -7}[level], box[3] + 3, y, 1 if level == 1 else 2)
    return s


# ---------------------------------------------------------------- look 4: Skyward Station

def _station(level):
    p, s = STATION, Structure(f"station{level}")
    L = LEVELS[level]
    half, y = L["half"], 1
    terrace(s, p, half, y)
    box, roof_y, centre, hatch = hall(s, p, level)

    if level == 1:
        yard_scope(s, p, level, 3, 2)
        rose(s, p, -2, 3, y, 2)
    elif level == 2:
        yard_scope(s, p, level, 3, 3)
        dish(s, p, -5, 3, y + 1, 2)
        rose(s, p, 0, 4, y, 2)
    else:
        roof_room(s, p, level, roof_y, centre)
        deck(s, p, -3, box[3] + 3, 3, box[3] + 6, y)
        dish(s, p, -(half - 2) if level < 5 else -(half - 4), 3, y + 1, 2 if level == 3 else 3)
        if level == 5:
            dish(s, p, half - 4, 3, y + 1, 3)
            meridian(s, p, half, y, material=p["m_dark"])
        rose(s, p, 0, box[3] + 5, y, 2 if level < 5 else 3)
    return s


# ---------------------------------------------------------------- look 5: the Aperture Array

def _void(level):
    p, s = VOID, Structure(f"array{level}")
    L = LEVELS[level]
    half, y = L["half"], 1
    terrace(s, p, half, y)
    box, roof_y, centre, hatch = hall(s, p, level)
    z_front = box[3] + 1

    r = {1: 2, 2: 3, 3: 4, 4: 5, 5: 6}[level]
    aperture(s, p, 0, z_front + r + 1, y + 1, r)                  # clear of the door's approach
    seams(s, p, -(half - 1), z_front, half - 1, half - 1, y)
    if level >= 3:
        # the roof is an open observing deck, railed all round, with the console up there
        railing(s, box[0] - 1, box[1] - 1, box[2] + 1, box[3] + 1, roof_y + 1, p["rail"])
        s.set(centre[0] - 2, roof_y + 1, centre[1], p["console"])
    if level >= 4:
        s.set(centre[0] + 2, roof_y + 1, centre[1], p["analyzer"])
        for dx in (-(half - 1), half - 1):                        # the masts
            for yy in range(y + 1, y + 6):
                s.set(dx, yy, 2, p["pillar"])
            s.set(dx, y + 6, 2, p["lamp"])
            s.set(dx, y + 7, 2, rod("up"))
    if level == 5:
        s.set(centre[0] - 3, roof_y + 1, centre[1] + 2, p["console"])
        s.set(centre[0] + 3, roof_y + 1, centre[1] + 2, p["analyzer"])
        for dx in (-(half - 2), half - 2):
            for dz in (0, 8):
                for yy in range(y + 1, y + 7):
                    s.set(dx, yy, dz, p["pillar"])
                s.set(dx, y + 7, dz, p["lamp"])
                s.set(dx, y + 8, dz, rod("up"))
        meridian(s, p, half, y, material=p["m_pale"])
    return s


# ---------------------------------------------------------------- the looks

LOOKS = {
    "observatory": _copper,
    "keep": _keep,
    "sandcourt": _sand,
    "station": _station,
    "array": _void,
}


# What each look is called in the build tool. Structurize shows the five as alternatives of one
# "Observatory" entry and takes the label from the 'name=' tag on the anchor block - the same
# mechanism the Departure Point's Launchpad and End Gate use. One name for all five is what made
# the tool list twenty-five levels of one building instead of five looks of five levels.
LOOK_NAMES = {
    "observatory": "Copper Dome",
    "keep": "Stargazer's Keep",
    "sandcourt": "Sand Court",
    "station": "Skyward Station",
    "array": "Aperture Array",
}


def place_camera(s, near_tag="scope", radius=(2, 3, 4)):
    """Exposure's camera stand, on a free square near the instrument that nobody needs to walk through.

    A real tripod, not decoration: the player walks up, puts their own camera on it, and shoots
    the sky the astronomer has been writing about. A stand has a body, and a citizen does not
    path round entities, so its square must be one the building can spare: every square a
    citizen can reach is found first (tools/access.py), and the stand goes on the nearest reached
    square to the instrument whose loss cuts nothing else off - a pocket beside the telescope,
    never the corridor from the door. Always on a whole block of the design: the first Keep put
    its stand on the ground past the kerb, where a real build has no ground at that height, and
    the stand fell out of the world.
    """
    import access
    goal = next((pos for pos, names in s.tags.items() if near_tag in names), None)
    if goal is None:
        return False
    gx, gy, gz = goal
    bounds = s.bounds()
    starts = access.outside_starts(s, bounds)
    seen = access.walk(s, bounds, starts)
    doors = {(x, z) for (x, y, z), b in s.blocks.items() if "_door" in b}
    cands = []
    for (x, y, z) in seen:
        d = max(abs(x - gx), abs(z - gz))
        if d not in radius or abs(y - gy) > 2 or not access.standable(s, x, y, z):
            continue
        if s.get(x, y - 1, z) is None or not access.full_floor(s, x, y - 1, z):
            continue                                              # a whole block of the design under it:
                                                                  # not a kerb slab, not the ground outside
        if any(s.get(x + ex, y - 1, z + ez) is not None and not access.full_floor(s, x + ex, y - 1, z + ez)
               for ex, ez in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            continue                                              # not beside the kerb, the gate or an eave
        if any((x + dx, z + dz) in doors for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
            continue                                              # never the square inside a door
        cands.append(((d, -(z - gz), abs(x - gx)), (x, y, z)))
    for _key, (x, y, z) in sorted(cands):
        s.set(x, y, z, "minecraft:barrier")                       # would the stand cut anything off?
        s.set(x, y + 1, z, "minecraft:barrier")
        still = access.walk(s, bounds, starts)
        s.set(x, y, z, None)
        s.set(x, y + 1, z, None)
        if len(still) >= len(seen) - 1:
            s.entity(x, y, z, "exposure:camera_stand")
            s.tag(x, y, z, "camera")
            return True
    return False


def build(look, level):
    s = LOOKS[look](level)
    s.tag(*s.anchor, "name=" + LOOK_NAMES[look])
    place_camera(s)
    return s


def observatory(level):
    return build("observatory", level)


if __name__ == "__main__":
    for look in LOOKS:
        for lv in range(1, 6):
            st = build(look, lv)
            print(f"{st.name:16s} {str(st.size()):16s} {len(st.blocks):5d} blocks  anchor {st.anchor}")
