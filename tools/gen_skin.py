"""Paint the Voyager suit textures (128x64) and citizen icons.

The head (face, hair, skin tone) is taken from MineColonies' Nether Miner textures so the
four skin-tone variants (_a, _b, _d, _w) and both genders keep their faces; everything from
the neck down is the suit. Layout matches client/VoyagerModel.java:

  head (0,0) 8x8x8      helmet (32,0) 8x8x8 inflate 0.6
  body (16,16) 8x12x4   right arm (40,16) 4x12x4   right leg (0,16) 4x12x4
  left leg (16,48)      left arm (32,48)
  chest panel (64,16) 6x4x1   pack (64,24) 8x10x4   tank (88,24) 2x8x2   antenna (96,24) 1x6x1

Outputs go to resources/assets/minecolonies/textures/entity/citizen/default/ and
resources/assets/minecolonies/textures/entity_icon/citizen/<style>/.
"""
import os
import zipfile

from PIL import Image, ImageDraw

import paths

MC_JAR = paths.lib("minecolonies-*.jar")       # the Nether Miner heads come from here
RES = paths.res("assets", "minecolonies", "textures")
STYLES = ("default", "eastasian", "hellenic", "medieval", "modern", "nether", "nordic", "undead")
SUFFIXES = ("_a", "_b", "_d", "_w")

# palette - End style: obsidian-dark suit, purple energy trim, ender-pearl teal, end-rod white
WHITE = (236, 230, 244, 255)       # end rod / highlights
BASE = (36, 27, 50, 255)           # obsidian purple-black suit
BASE2 = (52, 40, 74, 255)          # lighter panels / helmet shell
SEAM = (74, 58, 102, 255)
BLACK = (18, 14, 26, 255)          # gloves, boots, belt
PURPLE = (118, 44, 176, 255)
PURPLE2 = (184, 98, 244, 255)      # bright energy lines
MAGENTA = (236, 130, 255, 255)
PEARL = (24, 124, 108, 255)        # ender pearl
PEARL2 = (92, 220, 178, 255)
ENDSTONE = (222, 224, 164, 255)
CLEAR = (0, 0, 0, 0)

def box_faces(u, v, w, h, d):
    """Minecraft box UV: face -> (x0, y0, x1, y1) pixel rectangles (exclusive max)."""
    return {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }


def fill(im, rect, color):
    x0, y0, x1, y1 = rect
    ImageDraw.Draw(im).rectangle([x0, y0, x1 - 1, y1 - 1], fill=color)


def hline(im, rect, row, color, inset=0):
    """Paint one row (0 = top) of a face rectangle."""
    x0, y0, x1, y1 = rect
    ImageDraw.Draw(im).rectangle([x0 + inset, y0 + row, x1 - 1 - inset, y0 + row], fill=color)


def vline(im, rect, col, color):
    x0, y0, x1, y1 = rect
    ImageDraw.Draw(im).rectangle([x0 + col, y0, x0 + col, y1 - 1], fill=color)


def limb(im, u, v, glove_rows=4, band_row=5, sleeve=BASE):
    """Arm or leg: dark sleeve, purple energy band, black glove/boot with a bright edge."""
    f = box_faces(u, v, 4, 12, 4)
    fill(im, f["top"], BASE2)
    fill(im, f["bottom"], BLACK)
    for side in ("right", "front", "left", "back"):
        r = f[side]
        fill(im, r, sleeve)
        hline(im, r, 0, BASE2)
        hline(im, r, band_row, PURPLE)
        hline(im, r, band_row + 1, PURPLE2)
        for row in range(12 - glove_rows, 12):
            hline(im, r, row, BLACK)
        hline(im, r, 12 - glove_rows, PURPLE2)
        hline(im, r, 11, SEAM)


def suit(base):
    im = base.copy()
    # wipe everything except the head region, then paint
    fill(im, (32, 0, 128, 16), CLEAR)
    fill(im, (0, 16, 128, 64), CLEAR)

    # helmet: obsidian shell, open visor, magenta crest, pearl-lit ear modules
    f = box_faces(32, 0, 8, 8, 8)
    for side in ("top", "bottom", "right", "left", "back", "front"):
        fill(im, f[side], BASE2)
    fill(im, f["bottom"], CLEAR)                       # open underneath so the neck shows
    t = f["top"]
    fill(im, (t[0] + 3, t[1], t[0] + 5, t[3]), PURPLE)  # crest stripe front to back
    fill(im, (t[0] + 3, t[1] + 2, t[0] + 5, t[1] + 6), PURPLE2)
    fill(im, (t[0] + 3, t[1] + 3, t[0] + 5, t[1] + 5), MAGENTA)
    for side in ("right", "left"):
        r = f[side]
        hline(im, r, 0, SEAM)
        fill(im, (r[0] + 2, r[1] + 3, r[0] + 6, r[1] + 6), BLACK)     # ear module
        fill(im, (r[0] + 3, r[1] + 4, r[0] + 5, r[1] + 5), PEARL2)    # its light
        hline(im, r, 7, BLACK)
    b = f["back"]
    hline(im, b, 0, SEAM)
    fill(im, (b[0] + 2, b[1] + 2, b[0] + 6, b[1] + 6), BLACK)         # hinge plate
    fill(im, (b[0] + 3, b[1] + 3, b[0] + 5, b[1] + 5), PURPLE2)
    hline(im, b, 7, BLACK)
    fr = f["front"]
    fill(im, fr, CLEAR)
    hline(im, fr, 0, BASE2)                                            # visor rim
    hline(im, fr, 7, BLACK)
    vline(im, fr, 0, BASE2)
    vline(im, fr, 7, BASE2)
    hline(im, fr, 1, PURPLE2, inset=1)                                 # visor glass edge glows
    fill(im, (fr[0] + 1, fr[1] + 6, fr[0] + 7, fr[1] + 7), BLACK)      # chin guard
    fill(im, (fr[0] + 3, fr[1] + 6, fr[0] + 5, fr[1] + 7), PURPLE)     # mic

    # body: dark suit, purple energy lines down the sides, black belt with a glowing buckle
    f = box_faces(16, 16, 8, 12, 4)
    fill(im, f["top"], BASE2)
    fill(im, f["bottom"], BLACK)
    for side in ("right", "left"):
        r = f[side]
        fill(im, r, BASE)
        vline(im, r, 1, PURPLE)
        vline(im, r, 2, PURPLE2)
        hline(im, r, 10, BLACK)
        hline(im, r, 11, BLACK)
    for side in ("front", "back"):
        r = f[side]
        fill(im, r, BASE)
        vline(im, r, 0, BASE2)
        vline(im, r, 7, BASE2)
        hline(im, r, 10, BLACK)
        hline(im, r, 11, BLACK)
        fill(im, (r[0] + 3, r[1] + 10, r[0] + 5, r[1] + 12), PURPLE2)  # buckle
    fr = f["front"]
    hline(im, fr, 0, SEAM)                                             # collar
    # chevron of purple energy below the chest panel
    for i in range(3):
        fill(im, (fr[0] + 1 + i, fr[1] + 7 + i, fr[0] + 2 + i, fr[1] + 8 + i), PURPLE2)
        fill(im, (fr[0] + 6 - i, fr[1] + 7 + i, fr[0] + 7 - i, fr[1] + 8 + i), PURPLE2)
    fill(im, (fr[0] + 3, fr[1] + 9, fr[0] + 5, fr[1] + 10), MAGENTA)

    limb(im, 40, 16)          # right arm
    limb(im, 32, 48)          # left arm
    limb(im, 0, 16, band_row=6)    # right leg
    limb(im, 16, 48, band_row=6)   # left leg

    # chest panel: black plate with an eye-of-ender emblem and an energy bar
    f = box_faces(64, 16, 6, 4, 1)
    for side in f:
        fill(im, f[side], BLACK)
    fr = f["front"]
    fill(im, (fr[0] + 1, fr[1] + 1, fr[0] + 3, fr[1] + 3), PEARL2)     # eye
    fill(im, (fr[0] + 2, fr[1] + 2, fr[0] + 3, fr[1] + 3), BLACK)      # pupil
    fill(im, (fr[0] + 4, fr[1] + 1, fr[0] + 5, fr[1] + 2), PURPLE2)
    fill(im, (fr[0] + 4, fr[1] + 2, fr[0] + 5, fr[1] + 3), MAGENTA)

    # life-support pack: obsidian box, purple energy stripe, pearl valve
    f = box_faces(64, 24, 8, 10, 4)
    fill(im, f["top"], BASE2)
    fill(im, f["bottom"], BLACK)
    for side in ("right", "left", "front"):
        fill(im, f[side], BASE2)
        vline(im, f[side], 0, BLACK)
    b = f["back"]
    fill(im, b, BASE)
    hline(im, b, 0, SEAM)
    hline(im, b, 9, BLACK)
    fill(im, (b[0] + 1, b[1] + 2, b[0] + 7, b[1] + 4), PURPLE)         # pack stripe
    fill(im, (b[0] + 1, b[1] + 3, b[0] + 7, b[1] + 4), PURPLE2)
    fill(im, (b[0] + 3, b[1] + 6, b[0] + 5, b[1] + 8), BLACK)          # valve
    fill(im, (b[0] + 3, b[1] + 6, b[0] + 4, b[1] + 7), PEARL2)

    # tanks: ender-pearl capsules, teal with a bright band and a purple cap
    f = box_faces(88, 24, 2, 8, 2)
    fill(im, f["top"], PURPLE2)
    fill(im, f["bottom"], BLACK)
    for side in ("right", "front", "left", "back"):
        r = f[side]
        fill(im, r, PEARL)
        hline(im, r, 0, PURPLE)
        hline(im, r, 3, PEARL2)
        hline(im, r, 4, PEARL2)
        hline(im, r, 7, BLACK)

    # antenna: an end rod with a magenta tip
    f = box_faces(96, 24, 1, 6, 1)
    for side in f:
        fill(im, f[side], WHITE)
    for side in ("right", "front", "left", "back"):
        hline(im, f[side], 0, MAGENTA)
        hline(im, f[side], 5, BASE2)
    fill(im, f["top"], MAGENTA)
    return im


def icon(tex):
    """16x16 head icon: face with the helmet rim, like MineColonies' own icons."""
    face = tex.crop((8, 8, 16, 16)).convert("RGBA")
    rim = tex.crop((40, 8, 48, 16)).convert("RGBA")
    face.alpha_composite(rim)
    return face.resize((16, 16), Image.NEAREST).convert("RGB")


def main():
    body_dir = os.path.join(RES, "entity", "citizen", "default")
    os.makedirs(body_dir, exist_ok=True)
    for style in STYLES:
        os.makedirs(os.path.join(RES, "entity_icon", "citizen", style), exist_ok=True)
    with zipfile.ZipFile(MC_JAR) as jar:
        for gender in ("male", "female"):
            for sfx in SUFFIXES:
                src = f"assets/minecolonies/textures/entity/citizen/default/netherworker{gender}1{sfx}.png"
                with jar.open(src) as fh:
                    base = Image.open(fh).convert("RGBA")
                tex = suit(base)
                tex.save(os.path.join(body_dir, f"voyager{gender}1{sfx}.png"))
                ic = icon(tex)
                for style in STYLES:
                    ic.save(os.path.join(RES, "entity_icon", "citizen", style, f"voyager{gender}1{sfx}.png"))
    print("textures:", sorted(os.listdir(body_dir)))


if __name__ == "__main__":
    main()
