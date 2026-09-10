"""Flat front/back paper-doll previews of a citizen skin, plus the raw sheet.

Not a render - it pastes the box faces where the model puts them, at 10x, so a skin can be looked
at before it goes into the game. It caught more than one thing that read fine as pixels and wrong
as a person. Usage: python3 preview_skin.py astronomer [voyager ...]
"""
import os
import sys

from PIL import Image

import paths

RES = paths.res("assets", "minecolonies", "textures", "entity", "citizen", "default")
OUT = os.path.join(os.path.dirname(__file__), "out")
S = 10                       # pixels per model pixel
BG = (58, 62, 78, 255)       # a night sky, since that is where these two work


def faces(u, v, w, h, d):
    return {"top": (u + d, v, u + d + w, v + d),
            "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "right": (u, v + d, u + d, v + d + h),
            "front": (u + d, v + d, u + d + w, v + d + h),
            "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
            "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


# (name, box uv+size, dest x0,y0 in model pixels) - dest is the 16x32 doll frame
HEAD = (0, 0, 8, 8, 8)
BODY = (16, 16, 8, 12, 4)
R_ARM, L_ARM = (40, 16, 4, 12, 4), (32, 48, 4, 12, 4)
R_LEG, L_LEG = (0, 16, 4, 12, 4), (16, 48, 4, 12, 4)

COMMON = [(R_LEG, 4, 20), (L_LEG, 8, 20), (BODY, 4, 8), (R_ARM, 0, 8), (L_ARM, 12, 8)]

EXTRA = {
    # the overlay boxes each model adds, in draw order after the body
    "astronomer": {"front": [((64, 26, 6, 6, 3), 11, 13),      # satchel on the left hip
                             ((64, 16, 10, 3, 6), 3, 7)],      # scarf
                   "back": [((64, 0, 8, 14, 1), 4, 8),         # cloak
                            ((64, 16, 10, 3, 6), 3, 7)]},
    "photographer": {"front": [((64, 26, 6, 6, 3), 11, 13)],  # camera bag
                     "back": []},
    "voyager": {"front": [((64, 16, 6, 4, 1), 5, 10)],         # chest panel
                "back": [((64, 24, 8, 10, 4), 4, 9),           # life-support pack
                         ((88, 24, 2, 8, 2), 5, 10), ((88, 24, 2, 8, 2), 9, 10)]},
}
HAT = {"astronomer": (32, 0, 8, 8, 8), "voyager": (32, 0, 8, 8, 8), "photographer": (32, 0, 8, 8, 8)}


def doll(tex, who, side):
    im = Image.new("RGBA", (16 * S, 32 * S), BG)
    parts = list(COMMON) + EXTRA.get(who, {}).get(side, [])
    parts += [(HEAD, 4, 0), (HAT[who], 4, 0)]
    for box, dx, dy in parts:
        rect = faces(*box)[side]
        piece = tex.crop(rect)
        if side == "back":
            piece = piece.transpose(Image.FLIP_LEFT_RIGHT)
        piece = piece.resize((piece.width * S, piece.height * S), Image.NEAREST)
        im.alpha_composite(piece, (dx * S, dy * S))
    return im.transpose(Image.FLIP_LEFT_RIGHT) if side == "back" else im


def main(names):
    os.makedirs(OUT, exist_ok=True)
    for who in names:
        for gender in ("male", "female"):
            path = os.path.join(RES, f"{who}{gender}1_b.png")
            if not os.path.exists(path):
                print("missing", path)
                continue
            tex = Image.open(path).convert("RGBA")
            sheet = tex.resize((tex.width * 4, tex.height * 4), Image.NEAREST)
            front, back = doll(tex, who, "front"), doll(tex, who, "back")
            card = Image.new("RGBA", (front.width * 2 + 24, max(front.height, sheet.height + 16) + 16), BG)
            card.alpha_composite(front, (8, 8))
            card.alpha_composite(back, (front.width + 16, 8))
            out = os.path.join(OUT, f"skin_{who}_{gender}.png")
            card.convert("RGB").save(out)
            sheet.convert("RGBA").save(os.path.join(OUT, f"sheet_{who}_{gender}.png"))
            print(out)


if __name__ == "__main__":
    main(sys.argv[1:] or ["astronomer"])
