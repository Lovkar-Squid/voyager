"""The Departure Point hut block model - a control console with a glowing screen,
antenna and hazard stripes, built from vanilla textures (Blockbench-style JSON).

Writes resources/assets/voyager/models/block/blockhutvoyager.json and a copy for
the Blender preview.
"""
import json
import os

TEXTURES = {
    "plinth_top": "minecraft:block/end_stone",
    "plinth_side": "minecraft:block/end_stone_bricks",
    "rim": "minecraft:block/obsidian",
    "post": "minecraft:block/purpur_pillar",
    "post_top": "minecraft:block/purpur_pillar_top",
    "console_side": "minecraft:block/purpur_block",
    "console_top": "minecraft:block/obsidian",
    "screen": "minecraft:block/crying_obsidian",
    "btn_magenta": "minecraft:block/magenta_concrete",
    "btn_purple": "minecraft:block/purple_concrete",
    "btn_yellow": "minecraft:block/yellow_concrete",
    "pad_top": "minecraft:block/end_portal_frame_top",
    "pad_side": "minecraft:block/end_portal_frame_side",
    "egg": "minecraft:block/dragon_egg",
    "chorus": "minecraft:block/chorus_plant",
    "flower": "minecraft:block/chorus_flower",
    "particle": "minecraft:block/end_stone_bricks",
}


def auto_uv(face, a, b):
    (x1, y1, z1), (x2, y2, z2) = a, b
    if face in ("north", "south"):
        return [x1, 16 - y2, x2, 16 - y1]
    if face in ("east", "west"):
        return [z1, 16 - y2, z2, 16 - y1]
    return [x1, z1, x2, z2]


def element(a, b, faces, rotation=None, name=None, glow=False, uv=None):
    """faces: texture key for all faces, or dict face -> key (missing faces omitted).
    uv: optional dict face -> explicit uv (for textures that should be shown whole)."""
    if isinstance(faces, str):
        faces = {f: faces for f in ("north", "south", "east", "west", "up", "down")}
    el = {"from": list(a), "to": list(b),
          "faces": {f: {"uv": (uv or {}).get(f, auto_uv(f, a, b)), "texture": "#" + key} for f, key in faces.items()}}
    if name:
        el["name"] = name
    if rotation:
        axis, angle, origin = rotation
        el["rotation"] = {"angle": angle, "axis": axis, "origin": list(origin)}
    if glow:
        el["neoforge_data"] = {"block_light": 15, "sky_light": 15}
    return el


def post(x, z, top):
    return element((x, 0, z), (x + 2, top, z + 2), {"up": "post_top", "down": "post_top", "north": "post",
                                                    "south": "post", "east": "post", "west": "post"}, name="post")


TILT = ("x", 22.5, (8, 9, 6))   # positive x-rotation lowers the south edge: the console faces the player
ALL = ("north", "south", "east", "west", "up", "down")

elements = [
    # obsidian rim, end-stone-brick plinth with an end-stone top
    element((0.5, 0, 0.5), (15.5, 1.5, 15.5), "rim", name="rim"),
    element((1, 1.5, 1), (15, 3.5, 15), {"up": "plinth_top", "down": "plinth_top", "north": "plinth_side",
                                        "south": "plinth_side", "east": "plinth_side", "west": "plinth_side"}, name="plinth"),
    # four purpur-pillar corner posts
    post(0.5, 0.5, 5.5), post(13.5, 0.5, 5.5), post(0.5, 13.5, 5.5), post(13.5, 13.5, 5.5),
    # end-portal-frame pad in front of the console where the Voyager reports for duty
    element((5, 3.5, 9.5), (11, 4.25, 15), {"up": "pad_top", "north": "pad_side", "south": "pad_side",
                                            "east": "pad_side", "west": "pad_side"}, name="portal pad",
            uv={"up": [0, 0, 16, 16]}),
    # purpur pillar column carrying the console
    element((5, 3.5, 3), (11, 9, 9), {"up": "post_top", "down": "post_top", "north": "post",
                                     "south": "post", "east": "post", "west": "post"}, name="column"),
    # console: purpur sides, obsidian desk, crying-obsidian screen (glows in game), three buttons and a lever
    element((2, 9, 2), (14, 12, 10), {"up": "console_top", "down": "console_top", "north": "console_side",
                                     "south": "console_side", "east": "console_side", "west": "console_side"},
            TILT, name="console"),
    element((3, 12, 3), (13, 12.5, 7), {"up": "screen", "north": "screen", "south": "screen",
                                       "east": "screen", "west": "screen"}, TILT, name="screen", glow=True),
    element((3.5, 12, 7.75), (5, 13, 9.25), "btn_magenta", TILT, name="button magenta"),
    element((6.5, 12, 7.75), (8, 13, 9.25), "btn_purple", TILT, name="button purple"),
    element((9.5, 12, 7.75), (11, 13, 9.25), "btn_yellow", TILT, name="button yellow"),
    element((12, 12, 8), (12.75, 14, 8.75), "rim", TILT, name="lever"),
    # comms mast on the back-right post with a glowing crying-obsidian beacon
    element((14, 5.5, 1), (15, 14, 2), "rim", name="mast"),
    element((13.5, 14, 0.5), (15.5, 15.5, 2.5), "screen", name="beacon", glow=True),
    # a dragon egg on the front-left post
    element((0.5, 5.5, 13.5), (2.5, 6, 15.5), "rim", name="egg stand"),
    element((0.5, 6, 13.5), (2.5, 8.5, 15.5), "egg", name="dragon egg", uv={f: [0, 0, 16, 16] for f in ALL}),
    # a chorus stalk growing off the back-left post
    element((1, 5.5, 1), (2, 10, 2), "chorus", name="chorus stalk"),
    element((0.5, 10, 0.5), (2.5, 12, 2.5), "flower", name="chorus flower", uv={f: [0, 0, 16, 16] for f in ALL}),
]

model = {
    "credit": "Voyager - Lovkar & Claude",
    "parent": "block/block",
    "ambientocclusion": False,
    "textures": TEXTURES,
    "elements": elements,
}

import paths

out = paths.res("assets", "voyager", "models", "block", "blockhutvoyager.json")
os.makedirs(os.path.dirname(out), exist_ok=True)
with open(out, "w") as f:
    json.dump(model, f, indent=2)
with open(paths.out("hutmodel.json"), "w") as f:   # copy for the Blender preview
    json.dump(model, f)
print(out, len(elements), "elements")
