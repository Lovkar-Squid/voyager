"""The Photo Booth, in the same five looks as the Observatory.

A studio the player can walk into and a darkroom the Photographer works in. Built out of the
Observatory's own pieces - the palettes, the terrace, the roofs, the darkroom - because the two
buildings belong to the same colony and should look like it.

  Copper Dome     -> a copper-trimmed studio with a glazed north wall
  Stargazer's Keep-> stone and spruce, a pitched roof
  Sand Court      -> sandstone and cool blue tile
  Skyward Station -> quartz and glass
  Aperture Array  -> deepslate and purpur

Level 1 is a studio and nothing else; the darkroom arrives at 2, a second enlarger at 4, and the
gallery wall fills up as the building grows.
"""
import walkcheck
from designs import door, lantern, slab, railing
from observatory import (COPPER, KEEP, SAND, STATION, VOID, RACK, HUT, band, flat_roof, pitched,
                         terrace, do_slab, bed)
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
BOX = ((-9, 0, -10), (9, 15, 10))


def frame(size="medium", facing="north"):
    return f"exposure:photograph_frame_{size}[facing={facing}]"


def studio(s, p, x0, z0, x1, z1, y, height, level, roofer=flat_roof):
    """The room the sitter walks into: a lit floor, a gallery wall and the colony's camera stand.

    The Photographer does not work here - a colonist cannot take a photograph, and pretending
    otherwise would be the one dishonest thing in this building. The studio is for the player: the
    camera stand is theirs to use, and the walls are where the colony's prints end up.
    """
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
    s.set(cx, y + 1, z1, door(p["door"], "south", "lower"))
    s.set(cx, y + 2, z1, door(p["door"], "south", "upper"))
    s.set(cx, y + 3, z1, p["chis"])
    # windows down the long sides, so a sitter is lit from both cheeks
    for zz in ((z0 + z1) // 2, (z0 + z1) // 2 + 1):
        for wx in (x0, x1):
            s.set(wx, y + 1, zz, p["wall2"])
            s.set(wx, y + 2, zz, p["glass"])

    s.set_anchor(cx, y + 1, z0 + 1, BOOTH_HUT)
    s.set(x0 + 1, y + 1, z0 + 1, RACK)
    s.set(x1 - 1, y + 1, z0 + 1, RACK)
    s.set(cx, y + height - 1, (z0 + z1) // 2, lantern(p["light"], hanging=True))

    # the gallery wall: the north face, filling up as the building grows
    frames = {1: (0,), 2: (-2, 2), 3: (-2, 0, 2), 4: (-3, -1, 1, 3), 5: (-4, -2, 0, 2, 4)}[level]
    for dx in frames:
        if x0 < cx + dx < x1 and (cx + dx) != cx:
            s.set(cx + dx, y + 2, z0 + 1, frame("medium"))
    if level >= 3:
        s.set(x0 + 1, y + 1, z1 - 1, p["desk"])        # the lectern the albums are read on
    if level >= 4:
        s.set(x1 - 1, y + 1, z1 - 1, p["books"])
    if level >= 5:
        for dz in (-1, 1):
            s.set(x0 + 1, y + 2, (z0 + z1) // 2 + dz, frame("small", "east"))
            s.set(x1 - 1, y + 2, (z0 + z1) // 2 + dz, frame("small", "west"))

    # The studio floor, where the camera stand goes. Tagged so the AI and the builder agree.
    s.tag(cx, y + 1, (z0 + z1) // 2 + 1, "studio")


def _booth(look, level):
    p = LOOKS_PAL[look]
    s = Structure(f"{'photobooth' if look == 'photobooth' else look}{level}")
    y = 1
    half = {1: 5, 2: 6, 3: 7, 4: 8, 5: 9}[level]
    terrace(s, p, half, y)
    roofer = pitched if look in ("keep", "sandcourt") else flat_roof

    if level == 1:
        studio(s, p, -3, -4, 3, 1, y, 5, level, roofer=roofer)
    elif level == 2:
        studio(s, p, -4, -5, 4, 1, y, 5, level, roofer=roofer)
        darkroom_at(s, p, 5, -5, 7, -3, y)
    elif level == 3:
        studio(s, p, -5, -6, 5, 1, y, 6, level, roofer=roofer)
        darkroom_at(s, p, 6, -6, 8, -3, y)
    elif level == 4:
        studio(s, p, -6, -7, 6, 2, y, 6, level, roofer=roofer)
        darkroom_at(s, p, -8, -7, -7, -4, y, rooms=2)
        darkroom_at(s, p, 7, -7, 8, -4, y)
    else:
        studio(s, p, -7, -8, 7, 2, y, 7, level, roofer=roofer)
        darkroom_at(s, p, -9, -8, -8, -4, y, rooms=2)
        darkroom_at(s, p, 8, -8, 9, -4, y, rooms=2)
        railing(s, -7, 3, 7, 3, y + 1, p["rail"])
    return s


def darkroom_at(s, p, x0, z0, x1, z1, y, rooms=1):
    """The Observatory's darkroom, unchanged - the same room does the same job in both buildings."""
    from observatory import darkroom
    darkroom(s, p, x0, z0, x1, z1, y, 4, rooms=rooms)


def place_camera(s):
    """The colony's camera stand, on the studio floor. A real tripod the player uses themselves."""
    spot = next((pos for pos, names in s.tags.items() if "studio" in names), None)
    if spot is None:
        return False
    x, y, z = spot
    if (x, y, z) in s.blocks or (x, y + 1, z) in s.blocks or not walkcheck.solid(s, x, y - 1, z):
        return False
    s.entity(x, y, z, "exposure:camera_stand")
    return True


def place_bed(s, p):
    """The photographer sleeps at the booth, the way the astronomer sleeps at the Observatory."""
    from observatory import place_bed as put
    return put(s, p)


LOOKS = {look: (lambda lv, _l=look: _booth(_l, lv)) for look in LOOKS_PAL}


def build(look, level):
    s = LOOKS[look](level)
    s.tag(*s.anchor, "name=" + LOOK_NAMES[look])
    place_camera(s)                 # the tripod claims its square before the bed goes looking
    place_bed(s, LOOKS_PAL[look])
    return s


if __name__ == "__main__":
    for look in LOOKS:
        for lv in range(1, 6):
            st = build(look, lv)
            print(f"{st.name:16s} {str(st.size()):16s} {len(st.blocks):5d} blocks  anchor {st.anchor}")
