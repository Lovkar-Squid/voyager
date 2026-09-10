"""Paint the photographer's textures (128x64) and citizen icons.

A flat tweed cap, a cream shirt with the sleeves rolled to the elbow, a brown leather vest with a
pocket, a black camera strap across the chest, dark trousers, brown boots, and a camera bag on the
hip. Nothing about it should read as the astronomer's coat or the Voyager's suit.

Heads come from MineColonies' Crafter skins - the photographer is a crafter and should look like
one of the town's makers. Layout matches client/PhotographerModel.java:

  head (0,0) 8x8x8      cap (32,0) 8x8x8 inflate 0.5     peak (64,0) 8x1x3
  body (16,16) 8x12x4   right arm (40,16) 4x12x4   right leg (0,16) 4x12x4
  left leg (16,48)      left arm (32,48)           bag (64,26) 6x6x3
"""
import os
import zipfile

from PIL import Image

import paths
from gen_astronomer_skin import box_faces, fill, hline, vline, icon, head_texture, RES, STYLES, SUFFIXES

MC_JAR = paths.lib("minecolonies-*.jar")
HEAD_SOURCES = ("crafter{gender}1{sfx}", "carpenter{gender}1{sfx}", "citizen{gender}1{sfx}")

# palette
TWEED = (72, 66, 60, 255)
TWEED2 = (92, 84, 74, 255)
CREAM = (232, 224, 204, 255)      # shirt
CREAM2 = (206, 196, 172, 255)     # shirt in shadow
SKIN = (196, 150, 110, 255)       # forearms below the rolled sleeve
LEATHER = (104, 70, 44, 255)      # vest
LEATHER2 = (136, 96, 60, 255)
STRAP = (26, 24, 26, 255)
BRASS = (214, 176, 92, 255)
TROUSER = (54, 58, 70, 255)
TROUSER2 = (42, 46, 56, 255)
BOOT = (76, 52, 34, 255)
BOOT2 = (58, 40, 26, 255)
CLEAR = (0, 0, 0, 0)


def arm(im, u, v):
    """Rolled sleeve to the elbow, then bare forearm, then the hand."""
    f = box_faces(u, v, 4, 12, 4)
    fill(im, f["top"], CREAM)
    fill(im, f["bottom"], SKIN)
    for side in ("right", "front", "left", "back"):
        r = f[side]
        fill(im, r, CREAM)
        hline(im, r, 4, CREAM2)                             # the roll
        hline(im, r, 5, CREAM2)
        for row in range(6, 12):
            hline(im, r, row, SKIN)


def leg(im, u, v):
    f = box_faces(u, v, 4, 12, 4)
    fill(im, f["top"], TROUSER)
    fill(im, f["bottom"], BOOT2)
    for side in ("right", "front", "left", "back"):
        r = f[side]
        fill(im, r, TROUSER)
        vline(im, r, 1, TROUSER2)                           # a crease
        for row in range(9, 12):
            hline(im, r, row, BOOT)
        hline(im, r, 9, BOOT2)


def vest(base):
    im = base.copy()
    fill(im, (32, 0, 128, 16), CLEAR)
    fill(im, (0, 16, 128, 64), CLEAR)

    # ---- cap: tweed shell, open at the face, a lighter band above the brow
    f = box_faces(32, 0, 8, 8, 8)
    for side in f:
        fill(im, f[side], CLEAR)
    fill(im, f["top"], TWEED)
    for side in ("right", "left", "back", "front"):
        r = f[side]
        fill(im, (r[0], r[1], r[2], r[1] + 4), TWEED)         # the cap covers the top half only
        hline(im, r, 3, TWEED2)
    fill(im, f["front"], CLEAR)
    fr = f["front"]
    fill(im, (fr[0], fr[1], fr[2], fr[1] + 3), TWEED)
    hline(im, fr, 2, TWEED2)
    # the peak
    f = box_faces(64, 0, 8, 1, 3)
    for side in f:
        fill(im, f[side], TWEED2)
    fill(im, f["bottom"], TWEED)

    # ---- body: shirt under a leather vest, a strap across it
    f = box_faces(16, 16, 8, 12, 4)
    fill(im, f["top"], CREAM)
    fill(im, f["bottom"], TROUSER2)
    for side in ("right", "left"):
        r = f[side]
        fill(im, r, LEATHER)
        hline(im, r, 0, CREAM)
        hline(im, r, 11, TROUSER2)
    b = f["back"]
    fill(im, b, LEATHER)
    hline(im, b, 0, CREAM)
    vline(im, b, 3, LEATHER2)
    vline(im, b, 4, LEATHER2)
    for i in range(8):                                       # the strap, corner to corner
        row = 1 + i
        if row < 11:
            fill(im, (b[0] + i, b[1] + row, b[0] + i + 1, b[1] + row + 1), STRAP)
    hline(im, b, 11, TROUSER2)
    fr = f["front"]
    fill(im, fr, LEATHER)
    fill(im, (fr[0] + 3, fr[1], fr[0] + 5, fr[1] + 6), CREAM)          # the shirt showing
    fill(im, (fr[0] + 3, fr[1] + 1, fr[0] + 5, fr[1] + 2), CREAM2)     # its collar
    hline(im, fr, 0, CREAM)
    for row in (3, 6):
        fill(im, (fr[0] + 5, fr[1] + row, fr[0] + 6, fr[1] + row + 1), BRASS)   # vest buttons
    fill(im, (fr[0] + 1, fr[1] + 7, fr[0] + 3, fr[1] + 10), LEATHER2)          # the pocket
    for i in range(8):                                       # the strap, over the shoulder
        row = 1 + i
        if row < 11:
            fill(im, (fr[0] + 7 - i, fr[1] + row, fr[0] + 8 - i, fr[1] + row + 1), STRAP)
    hline(im, fr, 11, TROUSER2)

    arm(im, 40, 16)
    arm(im, 32, 48)
    leg(im, 0, 16)
    leg(im, 16, 48)

    # ---- camera bag: leather, brass clasp
    f = box_faces(64, 26, 6, 6, 3)
    for side in f:
        fill(im, f[side], LEATHER)
    fill(im, f["top"], LEATHER2)
    for side in ("right", "front", "left", "back"):
        r = f[side]
        hline(im, r, 0, LEATHER2)
        hline(im, r, 1, LEATHER2)
        hline(im, r, 2, BOOT2)
    fr = f["front"]
    fill(im, (fr[0] + 2, fr[1] + 1, fr[0] + 4, fr[1] + 3), BRASS)
    return im


def main():
    body_dir = os.path.join(RES, "entity", "citizen", "default")
    os.makedirs(body_dir, exist_ok=True)
    for style in STYLES:
        os.makedirs(os.path.join(RES, "entity_icon", "citizen", style), exist_ok=True)
    used = set()
    with zipfile.ZipFile(MC_JAR) as jar:
        for gender in ("male", "female"):
            for sfx in SUFFIXES:
                base, src = head_texture(jar, HEAD_SOURCES, gender, sfx)
                used.add(os.path.basename(src))
                tex = vest(base)
                tex.save(os.path.join(body_dir, f"photographer{gender}1{sfx}.png"))
                ic = icon(tex)
                for style in STYLES:
                    ic.save(os.path.join(RES, "entity_icon", "citizen", style, f"photographer{gender}1{sfx}.png"))
    print("heads from:", sorted(used))
    print("textures:", sorted(f for f in os.listdir(body_dir) if f.startswith("photographer")))


if __name__ == "__main__":
    main()
