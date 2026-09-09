"""The Photo Booth hut block model - the photographer's bench: a developing tray, a stack of
prints, and a plate camera on a tripod, all from vanilla textures.

Writes resources/assets/voyager/models/block/blockhutphotobooth.json, its blockstate and its item
model. tools/check_models.py verifies every texture actually exists.
"""
import json
import os

import paths
from gen_hut_model import element

TEXTURES = {
    "plinth_top": "minecraft:block/polished_deepslate",
    "plinth_side": "minecraft:block/deepslate_bricks",
    "top": "minecraft:block/dark_oak_planks",
    "leg": "minecraft:block/dark_oak_log",
    "tray": "minecraft:block/cauldron_side",
    "fluid": "minecraft:block/redstone_block",
    "print": "minecraft:block/white_concrete",
    "print2": "minecraft:block/light_gray_concrete",
    "body": "minecraft:block/deepslate_tiles",
    "brass": "minecraft:block/exposed_copper",
    "lens": "minecraft:block/tinted_glass",
    "lamp": "minecraft:block/redstone_lamp",
    "particle": "minecraft:block/dark_oak_planks",
}


def build():
    els = []
    els.append(element((0, 0, 0), (16, 2, 16),
                       {"up": "plinth_top", "down": "plinth_top", "north": "plinth_side",
                        "south": "plinth_side", "east": "plinth_side", "west": "plinth_side"},
                       name="plinth"))
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        els.append(element((x, 2, z), (x + 2, 9, z + 2), "leg", name="leg"))
    els.append(element((1, 9, 1), (15, 11, 15),
                       {"up": "top", "down": "top", "north": "plinth_side", "south": "plinth_side",
                        "east": "plinth_side", "west": "plinth_side"}, name="bench"))

    # the developing tray, with something red in it
    els.append(element((2, 11, 9), (9, 12.5, 14), "tray", name="tray"))
    els.append(element((2.5, 11.6, 9.5), (8.5, 12.2, 13.5), "fluid", name="developer", glow=True))
    # a stack of prints drying beside it
    els.append(element((10, 11, 10), (14, 11.6, 14), "print", name="prints"))
    els.append(element((10.4, 11.6, 10.4), (14.4, 12.1, 14.4), "print2", name="prints2"))

    # the tripod: three legs meeting under the camera
    for dx, dz, ax, ang in ((0, 0, "x", 22.5), (4, 0, "x", -22.5), (2, 4, "z", 22.5)):
        els.append(element((5 + dx, 11, 2 + dz), (6 + dx, 15, 3 + dz), "brass",
                           rotation=(ax, ang, (5.5 + dx, 13, 2.5 + dz)), name="tripod"))
    # the camera body, facing out, with a brass lens on the front
    els.append(element((5, 14.5, 1), (11, 19.5, 6), "body", name="camera"))
    els.append(element((6.5, 16, 0), (9.5, 19, 1.2), "brass", name="barrel"))
    els.append(element((7, 16.5, -0.2), (9, 18.5, 0.2), "lens", name="lens"))
    # the darkroom's safelight, on the far corner
    els.append(element((12, 11, 2), (15, 14, 5), "lamp", name="safelight", glow=True))
    return {
        "credit": "Voyager - Lovkar & Claude",
        "parent": "block/block",
        "ambientocclusion": False,
        "textures": TEXTURES,
        "elements": els,
    }


DISPLAY = {
    "thirdperson_righthand": {"rotation": [45, -45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "thirdperson_lefthand": {"rotation": [45, -45, 0], "translation": [0, 2.5, 0], "scale": [0.3, 0.3, 0.3]},
    "firstperson_righthand": {"rotation": [0, -70, 0], "scale": [0.36, 0.36, 0.36]},
    "firstperson_lefthand": {"rotation": [0, -70, 0], "scale": [0.36, 0.36, 0.36]},
    "ground": {"translation": [0, 3, 0], "scale": [0.25, 0.25, 0.25]},
    "gui": {"rotation": [20, -45, 0], "translation": [0, -3.0, 0], "scale": [0.40, 0.40, 0.40]},
    "head": {"rotation": [0, 180, 0], "scale": [0.91, 0.91, 0.91]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -4, -4], "scale": [0.5, 0.5, 0.5]},
}


def main():
    model = paths.res("assets", "voyager", "models", "block", "blockhutphotobooth.json")
    os.makedirs(os.path.dirname(model), exist_ok=True)
    with open(model, "w") as f:
        json.dump(build(), f, indent=2)
    state = paths.res("assets", "voyager", "blockstates", "blockhutphotobooth.json")
    with open(state, "w") as f:
        json.dump({"variants": {
            f"facing={d}": {"model": "voyager:block/blockhutphotobooth", "y": y}
            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}, f, indent=2)
    item = paths.res("assets", "voyager", "models", "item", "blockhutphotobooth.json")
    with open(item, "w") as f:
        json.dump({"parent": "voyager:block/blockhutphotobooth", "display": DISPLAY}, f, indent=2)
    print("wrote blockhutphotobooth model, blockstate and item model",
          f"({len(build()['elements'])} elements)")


if __name__ == "__main__":
    main()
