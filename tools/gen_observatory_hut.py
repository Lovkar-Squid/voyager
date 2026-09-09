"""The Observatory hut block model - the astronomer's desk: a deepslate table with a chart
spread on it, a brass telescope on a tripod and a lamp, built from vanilla textures.

Writes resources/assets/voyager/models/block/blockhutobservatory.json, its blockstate and its
item model. Same Blockbench-style JSON as gen_hut_model.py, and the same helpers.
"""
import json
import os

import paths
from gen_hut_model import auto_uv, element

TEXTURES = {
    "plinth_top": "minecraft:block/polished_deepslate",
    "plinth_side": "minecraft:block/deepslate_bricks",
    "top": "minecraft:block/deepslate_tiles",
    # Two families of vanilla blocks have no texture of their own and a model that names one gets
    # the magenta-and-black chequer with no warning: a wall is drawn with the texture of the block
    # it is cut from, and every waxed copper reuses the unwaxed texture. tools/check_models.py
    # exists because this block shipped with four of them.
    "leg": "minecraft:block/polished_deepslate",
    "chart": "minecraft:block/cartography_table_top",
    "book": "minecraft:block/bookshelf",
    "brass": "minecraft:block/exposed_copper",
    "brass_dark": "minecraft:block/weathered_copper",
    "lens": "minecraft:block/tinted_glass",
    "dome": "minecraft:block/oxidized_copper",
    "lamp": "minecraft:block/amethyst_block",
    "particle": "minecraft:block/deepslate_bricks",
}


def build():
    els = []
    # the plinth the desk stands on
    els.append(element((0, 0, 0), (16, 2, 16),
                       {"up": "plinth_top", "down": "plinth_top", "north": "plinth_side",
                        "south": "plinth_side", "east": "plinth_side", "west": "plinth_side"},
                       name="plinth"))
    # four legs and the table top
    for x, z in ((2, 2), (12, 2), (2, 12), (12, 12)):
        els.append(element((x, 2, z), (x + 2, 9, z + 2), "leg", name="leg"))
    els.append(element((1, 9, 1), (15, 11, 15),
                       {"up": "top", "down": "top", "north": "plinth_side", "south": "plinth_side",
                        "east": "plinth_side", "west": "plinth_side"}, name="table"))
    # the chart, spread flat, and a book beside it
    els.append(element((2, 11, 6), (10, 11.4, 14), "chart", name="chart",
                       uv={"up": [0, 0, 16, 16], "down": [0, 0, 16, 16]}))
    els.append(element((11, 11, 10), (14, 13, 14), "book", name="book"))
    # the tripod: three legs meeting under the tube
    for dx, dz, ax, ang in ((0, 0, "x", 22.5), (4, 0, "x", -22.5), (2, 4, "z", 22.5)):
        els.append(element((5 + dx, 11, 3 + dz), (6 + dx, 15, 4 + dz), "brass_dark",
                           rotation=(ax, ang, (5.5 + dx, 13, 3.5 + dz)), name="tripod"))
    # the tube, leaning back and up, with its objective open to the sky
    els.append(element((5, 14, 1), (11, 17, 7), "brass",
                       rotation=("x", 22.5, (8, 15.5, 4)), name="tube"))
    els.append(element((5.5, 14.5, 0), (10.5, 16.5, 1), "lens",
                       rotation=("x", 22.5, (8, 15.5, 4)), name="objective"))
    els.append(element((7, 13, 6.5), (9, 15, 8.5), "brass_dark",
                       rotation=("x", 22.5, (8, 15.5, 4)), name="eyepiece"))
    # a small lamp on the far corner, so the block reads at night
    els.append(element((12, 11, 2), (15, 14, 5), "lamp", name="lamp", glow=True))
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
    "gui": {"rotation": [20, -45, 0], "translation": [0, -2.5, 0], "scale": [0.42, 0.42, 0.42]},
    "head": {"rotation": [0, 180, 0], "scale": [0.91, 0.91, 0.91]},
    "fixed": {"rotation": [0, 180, 0], "translation": [0, -4, -4], "scale": [0.5, 0.5, 0.5]},
}


def main():
    model = paths.res("assets", "voyager", "models", "block", "blockhutobservatory.json")
    os.makedirs(os.path.dirname(model), exist_ok=True)
    with open(model, "w") as f:
        json.dump(build(), f, indent=2)
    state = paths.res("assets", "voyager", "blockstates", "blockhutobservatory.json")
    with open(state, "w") as f:
        json.dump({"variants": {
            f"facing={d}": {"model": "voyager:block/blockhutobservatory", "y": y}
            for d, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}, f, indent=2)
    item = paths.res("assets", "voyager", "models", "item", "blockhutobservatory.json")
    with open(item, "w") as f:
        json.dump({"parent": "voyager:block/blockhutobservatory", "display": DISPLAY}, f, indent=2)
    print("wrote", os.path.basename(model), os.path.basename(state), "and its item model",
          f"({len(build()['elements'])} elements)")


if __name__ == "__main__":
    main()
