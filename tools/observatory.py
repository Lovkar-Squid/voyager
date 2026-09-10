"""The Observatory - four looks, five levels each, as voxel structures.

Same conventions as the Departure Point (see designs.py): front is SOUTH (+z), y=0 is the
foundation course, the floor surface is y=1 and people walk on y=2. The hut block is the
astronomer's desk and it stands against the north wall of the ground floor, facing into the room.

Four looks, the way the Departure Point ships a Launchpad and an End Gate: one building, one
profession, pick the one that suits your town in the build tool.

  observatory  the Copper Dome   - pale stone, a verdigris dome, a refractor on a fork
  keep         Stargazer's Keep  - a medieval stone tower with a tiled spire and an armillary
  sandcourt    the Sand Court    - a desert court with a great stone gnomon, no roof at all
  station      Skyward Station   - a white dome on a modern block, with a radio dish

Each look keeps its own five levels inside the same box, aligned on its own hut block, so an
upgrade never appears to move the building - the requirement Marko set for the Departure Point.

Exposure's own blocks do the work inside every look. The Observatory is only registered when
Exposure and Exposure: Space are installed (docs/OBSERVATORY.md section 5), so the blueprints are
free to depend on them: exposure_space:analyzer is literally the astronomer's desk - its own model
file says so - night_analyzer is the console under the dome, exposure:lightroom is what the dark
room is *for*, and the photograph frames on the wall are where the colony's own plates end up.

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
    "frame":    "exposure:photograph_frame_medium",
    "frame_s":  "exposure:photograph_frame_small",
    "frame_l":  "exposure:photograph_frame_large",
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


# ---------------------------------------------------------------- shared pieces

def terrace(s, p, half, y, gate=True):
    """The apron the whole building stands on.

    A low kerb of slabs rather than a wall all the way round: a wall on a small platform reads as
    a pen, a kerb with columns at the corners reads as a terrace you are meant to walk on. The
    columns carry the lights, which is how you find the place at night - the only time anyone
    here is working.
    """
    s.box(-half, 0, -half, half, 0, half, FOUNDATION)
    for x in range(-half, half + 1):
        for z in range(-half, half + 1):
            s.set(x, y, z, p["floor"] if (x + z) % 2 == 0 else p["floor2"])
    edge = [(x, -half) for x in range(-half, half + 1)]
    edge += [(x, half) for x in range(-half, half + 1) if not (gate and abs(x) <= 1)]
    edge += [(-half, z) for z in range(-half + 1, half)]
    edge += [(half, z) for z in range(-half + 1, half)]
    for x, z in edge:
        s.set(x, y + 1, z, slab(p["slabd"]))
        if abs(x) == half and abs(z) == half:
            column(s, p, x, z, y + 1, 2)
            s.set(x, y + 4, z, lantern(p["light"]))
    if gate:
        for x in (-1, 0, 1):
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


def study(s, p, x0, z0, x1, z1, y, height, level, roofer=flat_roof, windows=True):
    """The working room: the desk, the plates, the charts and the analyser.

    exposure_space:analyzer goes on the desk from level 1 - it is the block whose own model file
    calls it the astronomer's desk - so the first thing the colony builds already does the work.
    """
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
        for zz in ((z0 + z1) // 2, (z0 + z1) // 2 + 1):
            window(x0, zz)
            window(x1, zz)

    s.set_anchor(cx, y + 1, z0 + 1, HUT)
    s.set(cx - 1, y + 1, z0 + 1, p["analyzer"])
    s.set(cx + 1, y + 1, z0 + 1, p["cutter"])
    s.set(x0 + 1, y + 1, z0 + 1, RACK)
    s.set(x1 - 1, y + 1, z0 + 1, RACK)
    s.set(cx, y + height - 1, (z0 + z1) // 2, lantern(p["light"], hanging=True))
    if level >= 2:
        s.set(cx + 2, y + 1, z0 + 1, p["books"])
        s.set(cx - 2, y + 1, z0 + 1, p["chart"])
        s.set(x1 - 1, y + 1, z1 - 1, p["barrel"])
    if level >= 3:
        s.set(cx + 2, y + 1, z1 - 1, p["desk"])
        for dx in (-2, 2):
            s.set(cx + dx, y + 2, z0 + 1, p["frame"])
    if level >= 4:
        s.set(x1 - 1, y + 1, z0 + 2, "minecraft:enchanting_table")
        s.set(cx, y + 2, z0 + 1, p["frame_l"])
    if level >= 5:
        for dx in (-4, 4):
            s.set(cx + dx, y + 2, z0 + 1, p["frame"])
        for dz in (-3, 3):
            s.set(x0 + 1, y + 2, (z0 + z1) // 2 + dz, p["frame_s"])
            s.set(x1 - 1, y + 2, (z0 + z1) // 2 + dz, p["frame_s"])


def darkroom(s, p, x0, z0, x1, z1, y, height, rooms=1):
    """A room with no windows and one door: where plates become photographs.

    exposure:lightroom is the whole point of it. Tinted glass in the ceiling rather than nothing
    at all, so the room reads as deliberate from outside and still lets no daylight onto a plate.
    """
    s.box(x0, y, z0, x1, y, z1, p["floor2"])
    for yy in range(y + 1, y + height):
        s.walls(x0, yy, z0, x1, yy, z1, p["base"])
    band(s, p, x0, z0, x1, z1, y + 1, p["wall2"])
    s.box(x0, y + height, z0, x1, y + height, z1, p["dark"])
    band(s, p, x0, z0, x1, z1, y + height, slab(p["trim_sl"]))
    s.set(x1, y + 1, z1 - 1, door(p["door"], "east", "lower"))
    s.set(x1, y + 2, z1 - 1, door(p["door"], "east", "upper"))
    s.set((x0 + x1) // 2, y + height - 1, (z0 + z1) // 2, lantern(p["light"], hanging=True))
    s.tag(x0 + 1, y + 1, z0 + 1, "darkroom")                      # where the plates are developed
    for i in range(rooms):
        s.set(x0 + 1, y + 1, z0 + 1 + i, lightroom("east"))
    s.set(x1 - 1, y + 1, z0 + 1, p["barrel"])
    s.set(x0 + 1, y + 1, z1 - 1, RACK)


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
    """A ladder up the inside of the east wall, and the hole it comes out of."""
    for i in range(height):
        s.set(x, y + 1 + i, z, ladder(facing))
    s.set(x, y + height, z, None)


def telescope(s, p, cx, cz, y, length, lean=1):
    """The instrument: a pier, a yoke and a tube lying along the sky.

    Two upright versions were rendered before this one and both came back as a copper chimney: a
    one-block-wide vertical stack has no telescope in its silhouette. What reads instantly is the
    long, near-horizontal tube of a refractor on a fork - a cannon on a mount.
    """
    s.tag(cx, y, cz, "scope")
    s.set(cx, y, cz, p["mount"])
    s.set(cx, y + 1, cz, p["m_dark"])
    for dx in (-1, 1):
        column(s, p, cx + dx, cz, y, 1)
    tube = y + 2
    s.set(cx, tube, cz + 2, p["scope2"])                          # the eyepiece and counterweight
    s.set(cx, tube, cz + 1, p["scope"])
    s.set(cx, tube, cz, p["scope"])
    zz = cz
    for _ in range(length):
        zz -= 1
        s.set(cx, tube, zz, p["scope"])
    if lean:
        s.set(cx, tube + 1, zz, p["scope2"])
        s.set(cx, tube + 1, zz - 1, p["lens"])
        return tube + 1, zz - 1
    s.set(cx, tube, zz - 1, p["lens"])
    return tube, zz - 1


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
    """A round drum with a dome on it, a door south and a slit cut through both, north."""
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
            for yy in range(y + 1, min(top - 3, cut)):
                for dx in (0, 1):
                    s.set(cx + dx, yy, zz, None)
    zd = cz + int(r)
    s.set(cx, y + 1, zd, door(p["door"], "north", "lower"))
    s.set(cx, y + 2, zd, door(p["door"], "north", "upper"))
    s.set(cx, y + 3, zd, p["chis"])
    for dx in (-1, 1):
        s.set(cx + dx, y + 2, zd, p["bulb"])
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
    """A round stone tower with a battlemented parapet and a tiled spire.

    The shuttered opening faces north, the same job the dome's slit does: the astronomer opens the
    shutters and the sky is there. The tower is what the town sees from the fields.
    """
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
    # the shutters: two courses of the north wall taken out and framed in timber
    for zz in range(cz - r_int(r), cz):
        for yy in range(y + 2, y + height):
            for dx in (0, 1):
                s.set(cx + dx, yy, zz, None)
    for dx in (-1, 2):
        for yy in range(y + 2, y + height):
            s.set(cx + dx, yy, cz - r_int(r) + 1, p["chis"])
    zd = cz + int(r)
    s.set(cx, y + 1, zd, door(p["door"], "north", "lower"))
    s.set(cx, y + 2, zd, door(p["door"], "north", "upper"))
    s.set(cx, y + 3, zd, p["chis"])
    for dx in (-1, 1):
        s.set(cx + dx, y + 2, zd, lantern(p["light"]))
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

def gnomon(s, p, cx, cz, y, size, width=3):
    """The great sundial: a stone wedge whose sloping edge points at the pole.

    A Samrat Yantra, and the reason this look has no dome. It is the biggest single shape in any
    of the four, and from across the valley it is unmistakable.
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
        for z in range(cz + 1, cz + 4):
            s.set(x, y, z, p["trim"])
    return y + size


def arc(s, p, cx, cz, y, r):
    """A graduated quadrant arc beside the gnomon: the scale the shadow is read against."""
    prev = None
    for i in range(r + 1):
        h = int(round(math.sqrt(max(0.0, r * r - i * i))))
        if prev is not None:
            for yy in range(h, prev + 1):
                s.set(cx, y + yy, cz - i, p["wall2"])
        s.set(cx, y + h, cz - i, p["wall2"])
        if i % 3 == 0:
            s.set(cx, y + h, cz - i, p["ring"])                   # a mark every third degree
        prev = h
    for yy in range(y, y + r + 1):
        s.set(cx, yy, cz, p["wall2"])
    return y + r


def dial(s, p, cx, cz, y, r):
    """The graduated dial the gnomon's shadow is read against, laid into the paving.

    The first Sand Court flanked the gnomon with two tall quadrant walls and they rendered as a
    pair of archways - they competed with the wedge instead of serving it. On the ground they do
    the same job and leave the silhouette to the one shape that matters.
    """
    for x in range(cx - r, cx + r + 1):
        for z in range(cz - r, cz + r + 1):
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
    """A row of columns with arches between them, along the south side of the court."""
    for x in range(x0, x1 + 1, 3):
        column(s, p, x, z, y, height - 1)
        if x + 3 <= x1:
            s.mixed(x + 1, y + height, z, stairs(p["stone_st"], "west", "top"), p["m_body"])
            s.set(x + 2, y + height, z, p["wall2"])
            s.mixed(x + 3, y + height, z, stairs(p["stone_st"], "east", "top"), p["m_body"])
            if "tile" in p:
                s.set(x + 2, y + height + 1, z, p["tile"])


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


def deck(s, p, x0, z0, x1, z1, y):
    """The station's paved court, railed along its open south edge.

    The first version railed all four sides and the render came back with an iron fence running
    straight through the middle of the yard.
    """
    s.box(x0, y, z0, x1, y, z1, slab(p["slabd"]))
    for x in range(x0, x1 + 1):
        if abs(x) > 1:                                            # leave the way in clear
            s.set(x, y + 1, z1, p["rail"])


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


# ---------------------------------------------------------------- look 1: the Copper Dome

def _copper(level):
    p, s = COPPER, Structure(f"observatory{level}")
    half = {1: 5, 2: 6, 3: 7, 4: 9, 5: 11}[level]
    y = 1
    terrace(s, p, half, y)

    if level == 1:
        study(s, p, -3, -4, 3, -1, y, 5, level, roofer=pitched)
        s.box(2, y, 0, 4, y, 5, p["floor2"])
        telescope(s, p, 3, 3, y + 2, 2, lean=1)
        rose(s, p, -2, 3, y, 3)

    elif level == 2:
        study(s, p, -4, -5, 4, -1, y, 6, level, roofer=pitched)
        darkroom(s, p, 5, -5, 7, -3, y, 4)
        s.box(2, y, 0, 4, y, 6, p["floor2"])
        s.box(2, y + 1, 3, 4, y + 1, 4, p["trim"])
        telescope(s, p, 3, 4, y + 3, 2, lean=1)
        rose(s, p, -2, 3, y, 3)

    elif level == 3:
        study(s, p, -5, -6, 5, -2, y, 5, level)
        darkroom(s, p, -7, -6, -6, -4, y, 4)
        top = drum_and_dome(s, p, 0, -4, y + 5, 3.5, 3)
        star_floor(s, p, 0, -4, y + 5, 3)
        telescope(s, p, 0, -4, y + 6, 1, lean=1)
        s.set(-2, y + 6, -5, p["console"])
        ladder_up(s, p, 4, -5, y, 5)
        s.set(0, top, -4, rod("up"))
        s.box(-3, y, 2, 3, y, 5, p["floor2"])
        rose(s, p, 0, 4, y, 2)
        railing(s, -3, 5, 3, 5, y + 1, p["rail"])

    elif level == 4:
        study(s, p, -6, -8, 6, -3, y, 6, level)
        darkroom(s, p, -8, -8, -7, -5, y, 4, rooms=2)
        top = drum_and_dome(s, p, 0, -5, y + 6, 4.5, 4)
        star_floor(s, p, 0, -5, y + 6, 4)
        telescope(s, p, 0, -5, y + 7, 2, lean=1)
        s.set(-3, y + 7, -6, p["console"])
        s.set(3, y + 7, -6, p["analyzer"])
        ladder_up(s, p, 5, -7, y, 6)
        s.set(0, top, -5, rod("up"))
        s.box(-5, y, 1, 5, y, 6, p["floor2"])
        for x in (-4, -3, 3, 4):
            s.set(x, y + 1, 2, RACK)
        s.set(0, y + 1, 2, p["analyzer"])
        s.set(-1, y + 1, 2, p["chart"])
        rose(s, p, 0, 5, y, 3)
        railing(s, -5, 6, 5, 6, y + 1, p["rail"])
        for x in (-6, 6):
            column(s, p, x, 6, y + 1, 2)
            s.set(x, y + 4, 6, lantern(p["light"]))

    else:
        study(s, p, -7, -9, 7, -4, y, 7, level)
        darkroom(s, p, -10, -9, -8, -6, y, 5, rooms=3)
        top = drum_and_dome(s, p, 0, -6, y + 7, 5.5, 5)
        star_floor(s, p, 0, -6, y + 7, 5)
        telescope(s, p, 0, -6, y + 8, 3, lean=1)
        s.set(-4, y + 8, -7, p["console"])
        s.set(4, y + 8, -7, p["console"])
        s.set(-4, y + 8, -4, p["analyzer"])
        s.set(4, y + 8, -4, p["analyzer"])
        ladder_up(s, p, 6, -8, y, 7)
        s.set(0, top, -6, rod("up"))
        meridian(s, p, half, y)
        s.box(-7, y, 0, 7, y, 8, p["floor2"])
        for x in (-6, -5, -4, 4, 5, 6):
            s.set(x, y + 1, 1, RACK)
        s.set(0, y + 1, 1, p["analyzer"])
        s.set(-1, y + 1, 1, p["chart"])
        s.set(1, y + 1, 1, p["desk"])
        rose(s, p, 0, 5, y, 4)
        railing(s, -7, 8, 7, 8, y + 1, p["rail"])
        for x in range(-6, 7, 3):
            s.set(x, y + 2, 8, lantern(p["light"]))
    return s


# ---------------------------------------------------------------- look 2: Stargazer's Keep

def _keep(level):
    p, s = KEEP, Structure(f"keep{level}")
    half = {1: 5, 2: 6, 3: 7, 4: 9, 5: 11}[level]
    y = 1
    terrace(s, p, half, y)

    if level == 1:
        study(s, p, -3, -4, 3, -1, y, 5, level, roofer=pitched)
        quadrant(s, p, 3, 4, y + 1, 3)
        rose(s, p, -2, 3, y, 3)

    elif level == 2:
        study(s, p, -4, -5, 4, -1, y, 6, level, roofer=pitched)
        darkroom(s, p, 5, -5, 7, -3, y, 4)
        quadrant(s, p, 3, 5, y + 1, 4)
        rose(s, p, -2, 3, y, 3)

    elif level == 3:
        study(s, p, -5, -6, 5, -2, y, 5, level, roofer=pitched)
        darkroom(s, p, -7, -6, -6, -4, y, 4)
        tower(s, p, 0, -4, y, 3.5, 8, level)
        armillary(s, p, 3, 4, y + 1, 2)
        s.box(-3, y, 2, 3, y, 5, p["floor2"])
        rose(s, p, -2, 4, y, 2)
        railing(s, -3, 5, 3, 5, y + 1, p["rail"])

    elif level == 4:
        study(s, p, -6, -8, 6, -3, y, 6, level, roofer=pitched)
        darkroom(s, p, -8, -8, -7, -5, y, 4, rooms=2)
        tower(s, p, 0, -5, y, 4.5, 10, level)
        armillary(s, p, 5, 4, y + 1, 2)
        s.box(-5, y, 1, 5, y, 6, p["floor2"])
        for x in (-4, -3, 3, 4):
            s.set(x, y + 1, 2, RACK)
        s.set(0, y + 1, 2, p["analyzer"])
        s.set(-1, y + 1, 2, p["chart"])
        rose(s, p, -3, 4, y, 2)
        railing(s, -5, 6, 5, 6, y + 1, p["rail"])

    else:
        study(s, p, -7, -9, 7, -4, y, 7, level, roofer=pitched)
        darkroom(s, p, -10, -9, -8, -6, y, 5, rooms=3)
        tower(s, p, 0, -6, y, 5.5, 12, level)
        armillary(s, p, 6, 4, y + 1, 3)
        meridian(s, p, half, y, material=p["m_dark"])
        s.box(-7, y, 0, 7, y, 8, p["floor2"])
        for x in (-6, -5, -4, 4, 5, 6):
            s.set(x, y + 1, 1, RACK)
        s.set(0, y + 1, 1, p["analyzer"])
        s.set(-1, y + 1, 1, p["chart"])
        s.set(1, y + 1, 1, p["desk"])
        rose(s, p, -3, 5, y, 3)
        railing(s, -7, 8, 7, 8, y + 1, p["rail"])
        for x in range(-6, 7, 3):
            s.set(x, y + 2, 8, lantern(p["light"]))
    return s


# ---------------------------------------------------------------- look 3: the Sand Court

def _sand(level):
    p, s = SAND, Structure(f"sandcourt{level}")
    half = {1: 5, 2: 6, 3: 7, 4: 9, 5: 11}[level]
    y = 1
    terrace(s, p, half, y)

    if level == 1:
        study(s, p, -3, -4, 3, -1, y, 5, level)
        gnomon(s, p, 0, 3, y + 1, 3)
        dial(s, p, 0, 3, y, 2)
        rose(s, p, -4, 2, y, 1)

    elif level == 2:
        study(s, p, -4, -5, 4, -1, y, 5, level)
        darkroom(s, p, 5, -5, 7, -3, y, 4)
        gnomon(s, p, 0, 4, y + 1, 4)
        dial(s, p, 0, 4, y, 2)
        rose(s, p, -5, 2, y, 2)

    elif level == 3:
        study(s, p, -5, -6, 5, -2, y, 5, level)
        darkroom(s, p, -7, -6, -6, -4, y, 4)
        gnomon(s, p, 0, 4, y + 1, 5)
        dial(s, p, 0, 4, y, 3)
        arcade(s, p, -5, 5, 6, y + 1)
        rose(s, p, -5, 2, y, 2)

    elif level == 4:
        study(s, p, -6, -8, 6, -3, y, 6, level)
        darkroom(s, p, -8, -8, -7, -5, y, 4, rooms=2)
        gnomon(s, p, 0, 6, y + 1, 7, width=5)
        dial(s, p, 0, 6, y, 3)
        arcade(s, p, -6, 6, 8, y + 1)
        chhatri(s, p, -7, 3, y + 1)
        chhatri(s, p, 7, 3, y + 1)
        s.set(0, y + 1, 8, p["analyzer"])
        rose(s, p, 0, 1, y, 2)

    else:
        study(s, p, -7, -9, 7, -4, y, 7, level)
        darkroom(s, p, -10, -9, -8, -6, y, 5, rooms=3)
        gnomon(s, p, 0, 7, y + 1, 9, width=5)
        dial(s, p, 0, 7, y, 4)
        arcade(s, p, -8, 8, 10, y + 1)
        chhatri(s, p, -7, 2, y + 1, 2)
        chhatri(s, p, 7, 2, y + 1, 2)
        meridian(s, p, half, y, material=p["m_dark"])
        s.set(0, y + 1, 9, p["analyzer"])
        s.set(-1, y + 1, 9, p["chart"])
        s.set(1, y + 1, 9, p["desk"])
        for x in (-4, -3, 3, 4):
            s.set(x, y + 1, 9, RACK)
        rose(s, p, 0, -1, y, 2)
    return s


# ---------------------------------------------------------------- look 4: Skyward Station

def _station(level):
    p, s = STATION, Structure(f"station{level}")
    half = {1: 5, 2: 6, 3: 7, 4: 9, 5: 11}[level]
    y = 1
    terrace(s, p, half, y)

    if level == 1:
        study(s, p, -3, -4, 3, -1, y, 5, level)
        s.box(2, y, 0, 4, y, 5, p["floor2"])
        telescope(s, p, 3, 3, y + 2, 2, lean=1)
        rose(s, p, -2, 3, y, 3)

    elif level == 2:
        study(s, p, -4, -5, 4, -1, y, 5, level)
        darkroom(s, p, 5, -5, 7, -3, y, 4)
        dish(s, p, -6, 3, y + 1, 2)
        s.box(2, y, 0, 4, y, 6, p["floor2"])
        telescope(s, p, 3, 4, y + 2, 2, lean=1)
        rose(s, p, 0, 4, y, 2)

    elif level == 3:
        study(s, p, -5, -6, 5, -2, y, 5, level)
        darkroom(s, p, -7, -6, -6, -4, y, 4)
        top = drum_and_dome(s, p, 0, -4, y + 5, 3.5, 3)
        star_floor(s, p, 0, -4, y + 5, 3)
        telescope(s, p, 0, -4, y + 6, 1, lean=1)
        s.set(-2, y + 6, -5, p["console"])
        ladder_up(s, p, 4, -5, y, 5)
        s.set(0, top, -4, rod("up"))
        dish(s, p, -6, 3, y + 1, 2)
        deck(s, p, -3, 2, 3, 5, y)
        rose(s, p, 0, 4, y, 2)

    elif level == 4:
        study(s, p, -6, -8, 6, -3, y, 6, level)
        darkroom(s, p, -8, -8, -7, -5, y, 4, rooms=2)
        top = drum_and_dome(s, p, 0, -5, y + 6, 4.5, 4)
        star_floor(s, p, 0, -5, y + 6, 4)
        telescope(s, p, 0, -5, y + 7, 2, lean=1)
        s.set(-3, y + 7, -6, p["console"])
        s.set(3, y + 7, -6, p["analyzer"])
        ladder_up(s, p, 5, -7, y, 6)
        s.set(0, top, -5, rod("up"))
        dish(s, p, -7, 3, y + 1, 3)
        deck(s, p, -5, 1, 5, 6, y)
        for x in (-4, -3, 3, 4):
            s.set(x, y + 1, 2, RACK)
        s.set(0, y + 1, 2, p["analyzer"])
        s.set(-1, y + 1, 2, p["chart"])
        rose(s, p, 0, 5, y, 3)

    else:
        study(s, p, -7, -9, 7, -4, y, 7, level)
        darkroom(s, p, -10, -9, -8, -6, y, 5, rooms=3)
        top = drum_and_dome(s, p, 0, -6, y + 7, 5.5, 5)
        star_floor(s, p, 0, -6, y + 7, 5)
        telescope(s, p, 0, -6, y + 8, 3, lean=1)
        s.set(-4, y + 8, -7, p["console"])
        s.set(4, y + 8, -7, p["console"])
        s.set(-4, y + 8, -4, p["analyzer"])
        s.set(4, y + 8, -4, p["analyzer"])
        ladder_up(s, p, 6, -8, y, 7)
        s.set(0, top, -6, rod("up"))
        dish(s, p, -7, 4, y + 1, 3)
        dish(s, p, 7, 4, y + 1, 3)
        meridian(s, p, half, y, material=p["m_dark"])
        deck(s, p, -7, 0, 7, 8, y)
        for x in (-6, -5, -4, 4, 5, 6):
            s.set(x, y + 1, 1, RACK)
        s.set(0, y + 1, 1, p["analyzer"])
        s.set(-1, y + 1, 1, p["chart"])
        s.set(1, y + 1, 1, p["desk"])
        rose(s, p, 0, 5, y, 4)
    return s


# ---------------------------------------------------------------- look 5: the Aperture Array

def _void(level):
    p, s = VOID, Structure(f"array{level}")
    half = {1: 5, 2: 6, 3: 7, 4: 9, 5: 11}[level]
    y = 1
    terrace(s, p, half, y)

    if level == 1:
        study(s, p, -3, -4, 3, -1, y, 5, level)
        aperture(s, p, 0, 3, y + 1, 2)
        seams(s, p, -4, 1, 4, 4, y)

    elif level == 2:
        study(s, p, -4, -5, 4, -1, y, 5, level)
        darkroom(s, p, 5, -5, 7, -3, y, 4)
        aperture(s, p, 0, 3, y + 1, 3)
        seams(s, p, -5, 0, 5, 5, y)

    elif level == 3:
        study(s, p, -5, -6, 5, -2, y, 5, level)
        darkroom(s, p, -7, -6, -6, -4, y, 4)
        aperture(s, p, 0, 3, y + 1, 4)
        s.set(-3, y + 6, -4, p["console"])                        # the roof console
        ladder_up(s, p, 4, -5, y, 5)
        deck(s, p, -5, -6, 5, -2, y + 5)
        seams(s, p, -6, 0, 6, 6, y)

    elif level == 4:
        study(s, p, -6, -8, 6, -3, y, 6, level)
        darkroom(s, p, -8, -8, -7, -5, y, 4, rooms=2)
        aperture(s, p, 0, 4, y + 1, 5)
        s.set(-4, y + 7, -5, p["console"])
        s.set(4, y + 7, -5, p["analyzer"])
        ladder_up(s, p, 5, -7, y, 6)
        deck(s, p, -6, -8, 6, -3, y + 6)
        for dx in (-8, 8):                                        # the masts
            for yy in range(y + 1, y + 6):
                s.set(dx, yy, 2, p["pillar"])
            s.set(dx, y + 6, 2, p["lamp"])
            s.set(dx, y + 7, 2, rod("up"))
        seams(s, p, -8, 0, 8, 8, y)

    else:
        study(s, p, -7, -9, 7, -4, y, 7, level)
        darkroom(s, p, -10, -9, -8, -6, y, 5, rooms=3)
        aperture(s, p, 0, 5, y + 1, 6)
        s.set(-5, y + 8, -6, p["console"])
        s.set(5, y + 8, -6, p["console"])
        s.set(-5, y + 8, -5, p["analyzer"])
        s.set(5, y + 8, -5, p["analyzer"])
        ladder_up(s, p, 6, -8, y, 7)
        deck(s, p, -7, -9, 7, -4, y + 7)
        for dx in (-9, 9):
            for dz in (0, 8):
                for yy in range(y + 1, y + 7):
                    s.set(dx, yy, dz, p["pillar"])
                s.set(dx, y + 7, dz, p["lamp"])
                s.set(dx, y + 8, dz, rod("up"))
        meridian(s, p, half, y, material=p["m_pale"])
        seams(s, p, -10, -1, 10, 10, y)
        for x in (-6, -5, -4, 4, 5, 6):
            s.set(x, y + 1, 1, RACK)
        s.set(0, y + 1, 1, p["analyzer"])
        s.set(-1, y + 1, 1, p["chart"])
        s.set(1, y + 1, 1, p["desk"])
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


def place_bed(s, p, reserved=()):
    """The astronomer's bed, indoors, near their desk.

    They live at the Observatory - a job whose whole point is the dark should not be walking home
    across a sleeping town at dawn, and should not be holding a bed in a house it is never in at
    night. Placed by searching rather than by coordinates because the looks build over each other:
    the Keep's tower cuts straight through the study's back wall at level 3 and up, and a bed with
    a wall where its head should be is a bed that drops on placement.

    Wanted: two free squares side by side, floor under both, air above both, and a ceiling within
    a few blocks - which is what tells an indoor room from the open terrace.
    """
    ax, ay, az = s.anchor

    def roofed(x, z):
        return any((x, ay + h, z) in s.blocks for h in range(2, 7))

    taken = {(ex, ez) for (ex, ey, ez), _eid, _yaw, _pitch, _extra in s.entities if ey == ay}
    # squares the AI stands on - the sitter's mark, the photographer's, the instrument - stay clear,
    # and so does whatever the caller reserves (the Photo Booth keeps its whole studio line free)
    taken |= {(tx, tz) for (tx, ty, tz) in s.tags if ty == ay}
    taken |= set(reserved)

    def free(x, z):
        return ((x, ay, z) not in s.blocks and (x, ay + 1, z) not in s.blocks
                and (x, z) not in taken          # a camera stand is an entity, not a block
                and walkcheck.solid(s, x, ay - 1, z) and roofed(x, z))

    # away from the hut block's own row first, then nearest to it
    for dz in (2, 3, 1, 4, 5):
        for dx in sorted(range(-8, 8), key=lambda d: (abs(d), d)):
            x, z = ax + dx, az + dz
            if free(x, z) and free(x + 1, z) and (x + 1) != ax:
                s.set(x, ay, z, bed(p["bed"], "east", "foot"))
                s.set(x + 1, ay, z, bed(p["bed"], "east", "head"))
                s.tag(x, ay, z, "bed")
                return True
    return False


def place_camera(s):
    """Exposure's camera stand, standing on the observing floor beside the instrument.

    A real tripod, not decoration: the player walks up, puts their own camera on it, and shoots
    the sky the astronomer has been writing about. Placed by looking for a free square with
    something solid under it near the tagged instrument, so it lands somewhere sensible in all
    five looks and at every level without five hand-picked coordinates going stale.
    """
    scope = next((pos for pos, names in s.tags.items() if "scope" in names), None)
    if scope is None:
        return
    sx, sy, sz = scope
    for radius in (2, 3, 4):
        # nearest first, and south of the instrument before north, so it never blocks the slit
        ring = sorted(
            ((dx, dz) for dx in range(-radius, radius + 1) for dz in range(-radius, radius + 1)
             if max(abs(dx), abs(dz)) == radius),
            key=lambda d: (abs(d[0]), -d[1]))
        for dx, dz in ring:
            x, y, z = sx + dx, sy, sz + dz
            if (x, y, z) in s.blocks or (x, y + 1, z) in s.blocks:
                continue
            if not walkcheck.solid(s, x, y - 1, z):
                continue
            s.entity(x, y, z, "exposure:camera_stand")
            s.tag(x, y, z, "camera")
            return


# The palette each look is drawn from, for the pieces that are placed after it is built.
PALETTES = {
    "observatory": COPPER,
    "keep": KEEP,
    "sandcourt": SAND,
    "station": STATION,
    "array": VOID,
}


def build(look, level):
    s = LOOKS[look](level)
    s.tag(*s.anchor, "name=" + LOOK_NAMES[look])
    if not place_bed(s, PALETTES[look]):
        raise ValueError(f"{look}{level}: nowhere indoors to put the astronomer's bed")
    place_camera(s)
    return s


def observatory(level):
    return build("observatory", level)


if __name__ == "__main__":
    for look in LOOKS:
        for lv in range(1, 6):
            st = build(look, lv)
            print(f"{st.name:16s} {str(st.size()):16s} {len(st.blocks):5d} blocks  anchor {st.anchor}")
