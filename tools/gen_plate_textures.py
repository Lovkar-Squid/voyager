"""The two sky-plate item textures: an exposed plate and a developed one.

A photographic plate is a sheet of glass in a frame, so both are the same frame with a different
sheet in it - the developed one has the sky on it, the exposed one is still blank. Sixteen pixels
is not much, so the frame is what carries the shape and the sheet carries the difference.

Writes resources/assets/voyager/textures/item/{exposed_plate,star_plate}.png and their models.
"""
import json
import os
import random

from PIL import Image, ImageDraw

import paths

FRAME = (122, 92, 66, 255)          # weathered copper frame
FRAME_HI = (168, 130, 96, 255)
FRAME_LO = (78, 58, 42, 255)
BLANK = (108, 112, 104, 255)        # an unexposed emulsion: flat, dull, nothing to see
BLANK_HI = (128, 132, 124, 255)
NIGHT = (14, 18, 40, 255)           # a developed sky
NIGHT_2 = (24, 30, 62, 255)
STAR = (236, 240, 255, 255)
STAR_DIM = (150, 168, 210, 255)


def plate(developed, seed=7):
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # the frame: a plate is held at its edges, and the corners are the tabs that hold it
    d.rectangle([1, 1, 14, 14], fill=FRAME)
    d.rectangle([1, 1, 14, 2], fill=FRAME_HI)
    d.rectangle([1, 13, 14, 14], fill=FRAME_LO)
    # the sheet
    d.rectangle([3, 3, 12, 12], fill=NIGHT if developed else BLANK)
    if developed:
        rng = random.Random(seed)
        for x in range(3, 13):
            for y in range(3, 13):
                if rng.random() < 0.18:
                    im.putpixel((x, y), NIGHT_2)
        # a handful of stars, one of them bright enough to be the thing the plate is of
        for _ in range(7):
            im.putpixel((rng.randint(4, 11), rng.randint(4, 11)), STAR_DIM)
        for _ in range(3):
            im.putpixel((rng.randint(4, 11), rng.randint(4, 11)), STAR)
        im.putpixel((7, 7), STAR)
        im.putpixel((8, 7), STAR_DIM)
        im.putpixel((7, 8), STAR_DIM)
    else:
        for x in range(3, 13):
            im.putpixel((x, 3), BLANK_HI)
    # the corner tabs
    for x, y in ((1, 1), (14, 1), (1, 14), (14, 14)):
        im.putpixel((x, y), FRAME_HI if y == 1 else FRAME_LO)
    return im


def main():
    tex = paths.res("assets", "voyager", "textures", "item")
    mod = paths.res("assets", "voyager", "models", "item")
    os.makedirs(tex, exist_ok=True)
    os.makedirs(mod, exist_ok=True)
    for name, developed in (("exposed_plate", False), ("star_plate", True)):
        plate(developed).save(os.path.join(tex, name + ".png"))
        with open(os.path.join(mod, name + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated",
                       "textures": {"layer0": "voyager:item/" + name}}, f, indent=2)
        print("wrote", name + ".png and its model")


if __name__ == "__main__":
    main()
