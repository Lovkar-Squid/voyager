"""Paint the astronomer's coat textures (128x64) and citizen icons.

The astronomer works outdoors, at night, in winter, and dresses like it: a hood, a long cream
scarf, a heavy indigo coat with brass buttons, leather gloves and boots, and a satchel of glass
plates on the hip. Nothing about it should read as the Voyager's suit - you should be able to tell
at fifty blocks which of the two is standing on the roof.

The head (face, hair, skin tone) is taken from MineColonies' own Researcher textures so the four
skin-tone variants (_a, _b, _d, _w) and both genders keep their faces; everything from the neck
down is ours. Layout matches client/AstronomerModel.java:

  head (0,0) 8x8x8      hood (32,0) 8x8x8 inflate 0.55
  body (16,16) 8x12x4   right arm (40,16) 4x12x4   right leg (0,16) 4x12x4
  left leg (16,48)      left arm (32,48)
  cloak (64,0) 8x14x1   scarf (64,16) 10x3x6   satchel (64,26) 6x6x3

Outputs go to resources/assets/minecolonies/textures/entity/citizen/default/ and
resources/assets/minecolonies/textures/entity_icon/citizen/<style>/.
"""
import os
import zipfile

from PIL import Image, ImageDraw

import paths

MC_JAR = paths.lib("minecolonies-*.jar")
RES = paths.res("assets", "minecolonies", "textures")
STYLES = ("default", "eastasian", "hellenic", "medieval", "modern", "nether", "nordic", "undead")
SUFFIXES = ("_a", "_b", "_d", "_w")

# The faces come from the Student: MineColonies has no researcher skin of its own, and the
# University's own people are the closest thing the colony has to an astronomer. Fall back down
# the list if a pack ever ships without them.
HEAD_SOURCES = ("student{gender}1{sfx}", "teacher{gender}1{sfx}", "citizen{gender}1{sfx}")

# palette - a night watch: indigo wool, brass, amethyst and lamp-lit cream
NIGHT = (32, 36, 66, 255)          # coat
NIGHT2 = (46, 52, 90, 255)         # hood shell, panels
SEAM = (66, 74, 122, 255)
DEEP = (20, 22, 44, 255)           # coat shadow, lining
BLACK = (18, 16, 24, 255)          # gloves, boots
BRASS = (168, 124, 54, 255)
BRASS2 = (224, 182, 96, 255)       # buttons, buckles, the lamp's light on metal
CREAM = (228, 218, 194, 255)       # scarf
CREAM2 = (198, 186, 160, 255)      # scarf shadow
AMETHYST = (146, 94, 208, 255)
AMETHYST2 = (198, 156, 246, 255)   # the lens, the badge
LEATHER = (96, 64, 42, 255)
LEATHER2 = (132, 92, 58, 255)
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
    x0, y0, x1, y1 = rect
    ImageDraw.Draw(im).rectangle([x0 + inset, y0 + row, x1 - 1 - inset, y0 + row], fill=color)


def vline(im, rect, col, color, inset=0):
    x0, y0, x1, y1 = rect
    ImageDraw.Draw(im).rectangle([x0 + col, y0 + inset, x0 + col, y1 - 1 - inset], fill=color)


def limb(im, u, v, cuff_rows=3, glove=BLACK, cuff=BRASS):
    """Arm or leg: indigo sleeve, a brass cuff ring, black leather glove or boot."""
    f = box_faces(u, v, 4, 12, 4)
    fill(im, f["top"], NIGHT2)
    fill(im, f["bottom"], BLACK)
    for side in ("right", "front", "left", "back"):
        r = f[side]
        fill(im, r, NIGHT)
        hline(im, r, 0, NIGHT2)
        hline(im, r, 4, DEEP)                              # a fold in the wool
        for row in range(12 - cuff_rows, 12):
            hline(im, r, row, glove)
        hline(im, r, 12 - cuff_rows, cuff)
        hline(im, r, 11, DEEP)


def coat(base):
    im = base.copy()
    fill(im, (32, 0, 128, 16), CLEAR)
    fill(im, (0, 16, 128, 64), CLEAR)

    # ---- hood: indigo shell, open at the face, a lighter lining round the opening
    f = box_faces(32, 0, 8, 8, 8)
    for side in f:
        fill(im, f[side], NIGHT2)
    fill(im, f["bottom"], CLEAR)                            # open underneath: the scarf shows
    t = f["top"]
    fill(im, (t[0] + 1, t[1] + 1, t[0] + 7, t[3] - 1), NIGHT)
    hline(im, t, 1, SEAM, inset=1)
    for side in ("right", "left"):
        r = f[side]
        fill(im, r, NIGHT2)
        vline(im, r, 0, SEAM)                               # the hood's edge, catching the lamp
        hline(im, r, 7, DEEP)
        fill(im, (r[0] + 5, r[1] + 2, r[0] + 8, r[1] + 5), NIGHT)
    b = f["back"]
    fill(im, b, NIGHT2)
    hline(im, b, 0, SEAM)
    fill(im, (b[0] + 2, b[1] + 2, b[0] + 6, b[1] + 7), NIGHT)   # the fold of the cowl
    hline(im, b, 7, DEEP)
    fr = f["front"]
    fill(im, fr, CLEAR)                                     # the face
    hline(im, fr, 0, NIGHT2)                                # brow of the hood
    hline(im, fr, 1, SEAM, inset=1)                         # its lining
    vline(im, fr, 0, NIGHT2)
    vline(im, fr, 7, NIGHT2)
    # a flip-up lens on the side of the hood - the astronomer's one gadget, parked on the temple
    # so it never sits over an eye whichever face the citizen was born with
    fill(im, (fr[0], fr[1] + 1, fr[0] + 1, fr[1] + 4), BRASS)
    fill(im, (fr[0], fr[1] + 2, fr[0] + 1, fr[1] + 3), AMETHYST2)

    # ---- body: a heavy coat, buttoned up the front, with a deep lining
    f = box_faces(16, 16, 8, 12, 4)
    fill(im, f["top"], NIGHT2)
    fill(im, f["bottom"], DEEP)
    for side in ("right", "left"):
        r = f[side]
        fill(im, r, NIGHT)
        vline(im, r, 3, DEEP)                               # the coat's side seam
        hline(im, r, 11, DEEP)
    b = f["back"]
    fill(im, b, NIGHT)
    hline(im, b, 0, SEAM)
    vline(im, b, 3, NIGHT2)
    vline(im, b, 4, NIGHT2)                                 # the centre seam of the back
    hline(im, b, 11, DEEP)
    fr = f["front"]
    fill(im, fr, NIGHT)
    hline(im, fr, 0, SEAM)
    fill(im, (fr[0] + 2, fr[1] + 1, fr[0] + 4, fr[1] + 12), NIGHT2)   # the button placket
    for row in (2, 4, 6, 8):
        fill(im, (fr[0] + 2, fr[1] + row, fr[0] + 3, fr[1] + row + 1), BRASS2)
    fill(im, (fr[0] + 5, fr[1] + 2, fr[0] + 7, fr[1] + 4), DEEP)      # breast pocket
    fill(im, (fr[0] + 5, fr[1] + 2, fr[0] + 7, fr[1] + 3), BRASS)     # its brass edge
    fill(im, (fr[0] + 5, fr[1] + 8, fr[0] + 7, fr[1] + 10), AMETHYST) # the colony's star badge
    fill(im, (fr[0] + 5, fr[1] + 8, fr[0] + 6, fr[1] + 9), AMETHYST2)
    hline(im, fr, 11, DEEP)

    limb(im, 40, 16)                                        # right arm
    limb(im, 32, 48)                                        # left arm
    limb(im, 0, 16, cuff_rows=4, cuff=LEATHER2)             # right leg (boot)
    limb(im, 16, 48, cuff_rows=4, cuff=LEATHER2)            # left leg (boot)

    # ---- cloak: a shade lighter than the coat so the two read as separate garments, with deep
    # folds, a brass clasp bar at the shoulders and a worn hem
    f = box_faces(64, 0, 8, 14, 1)
    for side in f:
        fill(im, f[side], NIGHT2)
    b = f["back"]                                           # the side the world sees
    fill(im, b, NIGHT2)
    for col in (1, 4, 6):
        vline(im, b, col, DEEP, inset=2)                    # folds
    for col in (2, 5):
        vline(im, b, col, SEAM, inset=3)                    # the light down their ridges
    hline(im, b, 0, BRASS)                                  # the clasp bar across the shoulders
    fill(im, (b[0] + 3, b[1], b[0] + 5, b[1] + 2), BRASS2)  # its buckle
    hline(im, b, 12, DEEP)
    hline(im, b, 13, SEAM)                                  # the hem, catching the lamp
    fr = f["front"]                                         # the lining, seen when it swings
    fill(im, fr, DEEP)
    hline(im, fr, 0, BRASS)
    hline(im, fr, 13, NIGHT2)

    # ---- scarf: cream wool, wound twice, one end hanging down the front
    f = box_faces(64, 16, 10, 3, 6)
    for side in f:
        fill(im, f[side], CREAM)
    fill(im, f["top"], CREAM2)
    fill(im, f["bottom"], CREAM2)
    for side in ("right", "front", "left", "back"):
        r = f[side]
        hline(im, r, 1, CREAM2)                             # the second turn of the wind
    fr = f["front"]
    fill(im, (fr[0] + 3, fr[1], fr[0] + 5, fr[1] + 3), CREAM)
    vline(im, fr, 5, CREAM2)                                # where the loose end starts

    # ---- satchel: leather box, brass buckle, a plate corner showing under the flap
    f = box_faces(64, 26, 6, 6, 3)
    for side in f:
        fill(im, f[side], LEATHER)
    fill(im, f["top"], LEATHER2)
    for side in ("right", "front", "left", "back"):
        r = f[side]
        hline(im, r, 0, LEATHER2)                           # the flap
        hline(im, r, 1, LEATHER2)
        hline(im, r, 2, DEEP)                               # its shadow
    fr = f["front"]
    fill(im, (fr[0] + 2, fr[1] + 1, fr[0] + 4, fr[1] + 3), BRASS2)     # buckle
    fill(im, (fr[0] + 4, fr[1] + 3, fr[0] + 6, fr[1] + 4), AMETHYST2)  # a plate, edge-on
    return im


def icon(tex):
    """16x16 head icon: face with the hood over it, like MineColonies' own icons."""
    face = tex.crop((8, 8, 16, 16)).convert("RGBA")
    hood = tex.crop((40, 8, 48, 16)).convert("RGBA")
    face.alpha_composite(hood)
    return face.resize((16, 16), Image.NEAREST).convert("RGB")


def head_texture(jar, names, gender, sfx):
    """The first of the source skins that this MineColonies build actually ships."""
    for name in names:
        src = ("assets/minecolonies/textures/entity/citizen/default/"
               + name.format(gender=gender, sfx=sfx) + ".png")
        try:
            with jar.open(src) as fh:
                return Image.open(fh).convert("RGBA"), src
        except KeyError:
            continue
    raise SystemExit(f"no head texture for {gender}{sfx}: tried {names}")


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
                tex = coat(base)
                tex.save(os.path.join(body_dir, f"astronomer{gender}1{sfx}.png"))
                ic = icon(tex)
                for style in STYLES:
                    ic.save(os.path.join(RES, "entity_icon", "citizen", style, f"astronomer{gender}1{sfx}.png"))
    print("heads from:", sorted(used))
    print("textures:", sorted(f for f in os.listdir(body_dir) if f.startswith("astronomer")))


if __name__ == "__main__":
    main()
