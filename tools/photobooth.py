"""The Photo Booth, in the same five looks as the Observatory.

A studio the player can walk into and a darkroom the Photographer works in. Built out of the
Observatory's own pieces - the palettes, the terrace, the roofs, the darkroom - because the two
buildings belong to the same colony and should look like it.

  Copper Dome     -> a copper-trimmed studio with a glazed north wall
  Stargazer's Keep-> stone and spruce, a pitched roof
  Sand Court      -> sandstone and cool blue tile
  Skyward Station -> quartz and glass
  Aperture Array  -> deepslate and purpur

Level 1 is a studio and nothing else; the darkroom arrives at 2 as a wing on the west side,
entered from inside the studio, a second wing on the east side at 4, and the gallery wall fills
up as the building grows. The first darkrooms stood a block apart from the studio with their
doors opening into the studio's outer wall - "the photographer has a wall in front of his
door" - and that is the thing this plan exists to make impossible: a wing shares the studio's
wall and its door is in that wall. tools/access.py checks every level.

The photographer lives here, the way the astronomer lives at the Observatory
(colony/PhotoBoothModules.java): the bed is in the studio, in the corner by the gallery wall,
out of the picture.
"""
import walkcheck
from designs import door, lantern
from observatory import (COPPER, KEEP, SAND, STATION, VOID, RACK, band, flat_roof, pitched,
                         terrace, bed, frame, annex, room, place_camera as _place_camera)
from voxel import Structure

# The Photo Booth's own hut block; everything else comes from the Observatory's palettes.
BOOTH_HUT = "voyager:blockhutphotobooth[facing=north]"

LOOKS_PAL = {
    "photobooth": COPPER,
    "keep": KEEP,
    "sandcourt": SAND,
    "station": STATION,
    "array": VOID,
}

LOOK_NAMES = {
    "photobooth": "Copper Dome",
    "keep": "Stargazer's Keep",
    "sandcourt": "Sand Court",
    "station": "Skyward Station",
    "array": "Aperture Array",
}

# One footprint for every level and every look, aligned on the hut block.
BOX = ((-9, 0, -11), (9, 15, 10))

# Per level: the terrace's half-width, the studio's box (outer walls, x0 z0 x1 z1), its height,
# and how many lightrooms the west and the east wing hold (0 = no wing).
LEVELS = {
    1: dict(half=6, box=(-5, -5, 5, 2), h=5, west=0, east=0),
    2: dict(half=8, box=(-5, -6, 5, 2), h=5, west=1, east=0),
    3: dict(half=9, box=(-6, -7, 6, 2), h=6, west=1, east=0),
    4: dict(half=9, box=(-6, -8, 6, 2), h=6, west=2, east=1),
    5: dict(half=9, box=(-6, -9, 6, 2), h=7, west=2, east=2),
}


def studio(s, p, x0, z0, x1, z1, y, height, level, roofer=flat_roof, wings=()):
    """The room the sitter walks into: a lit floor, a gallery wall and the colony's camera stand.

    Outer walls inclusive. From the north wall: the gallery row (racks in the corners, the hut
    block in the middle, the photographs above), the sitter's row with the bed in its east corner,
    two rows of open floor, the tripod's square, the photographer's square, and the south row
    with the door in the middle. Three marks on the floor, tagged so the AI and the builder
    agree: ``sitter`` is where a visitor stands for their portrait, ``studio`` the tripod's square,
    ``photographer`` the square behind the tripod the photographer shoots from. `wings` names the
    sides a darkroom is built against: no windows there, and the square inside its door is free.
    """
    room(s, x0, y, z0, x1, y + height, z1, "studio")
    s.box(x0, y, z0, x1, y, z1, p["floor"])
    for yy in range(y + 1, y + height):
        s.walls(x0, yy, z0, x1, yy, z1, p["wall"])
    band(s, p, x0, z0, x1, z1, y + 1, p["wall2"])
    for qx, qz in ((x0, z0), (x1, z0), (x0, z1), (x1, z1)):
        for yy in range(y + 1, y + height):
            s.set(qx, yy, qz, p["wall2"])
    if roofer is not None:
        roofer(s, p, x0, z0, x1, z1, y + height)

    cx = (x0 + x1) // 2
    xi0, xi1, zi0, zi1 = x0 + 1, x1 - 1, z0 + 1, z1 - 1
    zmid = (z0 + z1) // 2
    s.set(cx, y + 1, z1, door(p["door"], "south", "lower"))
    s.set(cx, y + 2, z1, door(p["door"], "south", "upper"))
    s.set(cx, y + 3, z1, p["chis"])

    def window(wx, wz):
        s.set(wx, y + 1, wz, p["wall2"])
        s.set(wx, y + 2, wz, p["glass"])
    for dx in (-3, -2, 2, 3):                                     # either side of the door
        if x0 < cx + dx < x1:
            window(cx + dx, z1)
    for zz in (zmid, zmid + 1):                                   # down the long sides, so a
        if "west" not in wings:                                   # sitter is lit from both cheeks
            window(x0, zz)
        if "east" not in wings:
            window(x1, zz)

    # the gallery row
    s.set_anchor(cx, y + 1, zi0, BOOTH_HUT)
    s.set(xi0, y + 1, zi0, RACK)
    s.set(xi1, y + 1, zi0, RACK)
    s.set(cx, y + height - 1, zmid, lantern(p["light"], hanging=True))
    frames = {1: (-2, 2), 2: (-2, 2), 3: (-3, -1, 1, 3), 4: (-3, -1, 1, 3), 5: (-4, -2, 0, 2, 4)}[level]
    for dx in frames:
        frame(s, cx + dx, y + 2, zi0, "south")
    # the sitter's row: the photographer's bed in its east corner, head to the wall
    s.set(xi1 - 1, y + 1, zi0 + 1, bed(p["bed"], "east", "foot"))
    s.set(xi1, y + 1, zi0 + 1, bed(p["bed"], "east", "head"))
    s.tag(xi1 - 1, y + 1, zi0 + 1, "bed")
    if level >= 3:
        s.set(xi0, y + 1, zi0 + 1, p["desk"])                     # the lectern the albums are read on
    if level >= 4:
        s.set(xi0, y + 1, zi0 + 2, p["books"])
        for zz in range(zi0 + 3, zi1 - 1):                        # more storage down the walls
            s.set(xi0, y + 1, zz, RACK)
        for zz in range(zi0 + 3, zi1 - 1):
            s.set(xi1, y + 1, zz, RACK)
    if level >= 5:
        for zz in (zi0 + 1, zi0 + 2):
            frame(s, xi1, y + 2, zz, "west")                      # over the bed, and beside it
            frame(s, xi0, y + 2, zz + 2, "east")

    # The studio floor: the sitter before the gallery wall, the tripod two squares south of them,
    # the photographer behind the tripod. Nothing stands between the three.
    s.tag(cx, y + 1, zi0 + 1, "sitter")
    s.tag(cx, y + 1, zi0 + 3, "studio")
    s.tag(cx, y + 1, zi0 + 4, "photographer")
    return (x0, z0, x1, z1)


def _booth(look, level):
    p = LOOKS_PAL[look]
    s = Structure(f"{'photobooth' if look == 'photobooth' else look}{level}")
    L = LEVELS[level]
    y = 1
    terrace(s, p, L["half"], y)
    roofer = pitched if look in ("keep", "sandcourt") else flat_roof
    wings = tuple(side for side in ("west", "east") if L[side])
    box = studio(s, p, *L["box"], y, L["h"], level, roofer=roofer, wings=wings)
    for side in wings:
        annex(s, p, box, y, 4, rooms=L[side], side=side)
    return s


def place_camera(s):
    """The colony's camera stand, on the tripod's square. A real tripod the player uses themselves."""
    spot = next((pos for pos, names in s.tags.items() if "studio" in names), None)
    if spot is None:
        return False
    x, y, z = spot
    if (x, y, z) in s.blocks or (x, y + 1, z) in s.blocks or not walkcheck.solid(s, x, y - 1, z):
        return False
    s.entity(x, y, z, "exposure:camera_stand")
    return True


LOOKS = {look: (lambda lv, _l=look: _booth(_l, lv)) for look in LOOKS_PAL}


def build(look, level):
    s = LOOKS[look](level)
    s.tag(*s.anchor, "name=" + LOOK_NAMES[look])
    place_camera(s)
    return s


if __name__ == "__main__":
    for look in LOOKS:
        for lv in range(1, 6):
            st = build(look, lv)
            print(f"{st.name:16s} {str(st.size()):16s} {len(st.blocks):5d} blocks  anchor {st.anchor}")
