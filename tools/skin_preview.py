"""Flat (paper-doll) preview of the Voyager suit textures: front and back views."""
import sys
from PIL import Image

import paths

TEX = paths.res("assets", "minecolonies", "textures", "entity", "citizen", "default")
S = 12  # pixels per model unit


def faces(u, v, w, h, d):
    return {"top": (u + d, v, u + d + w, v + d), "bottom": (u + d + w, v, u + d + 2 * w, v + d),
            "right": (u, v + d, u + d, v + d + h), "front": (u + d, v + d, u + d + w, v + d + h),
            "left": (u + d + w, v + d, u + 2 * d + w, v + d + h), "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h)}


def paste(canvas, tex, rect, x0, y0, x1, y1, mirror=False):
    """Paste a texture face onto model-space rectangle [x0,x1]x[y0,y1] (units)."""
    face = tex.crop(rect)
    if mirror:
        face = face.transpose(Image.FLIP_LEFT_RIGHT)
    w = max(1, round((x1 - x0) * S)); h = max(1, round((y1 - y0) * S))
    face = face.resize((w, h), Image.NEAREST)
    canvas.alpha_composite(face, (round((x0 + 10) * S), round((y0 + 10) * S)))


def figure(tex, back=False):
    canvas = Image.new("RGBA", (20 * S, 36 * S), (0, 0, 0, 0))
    f = "back" if back else "front"
    m = -1 if back else 1  # mirror x for the back view

    def rect(x0, y0, x1, y1):
        if back:
            x0, x1 = -x1, -x0
        return x0, y0, x1, y1

    def part(u, v, w, h, d, x0, y0, z_order=None, inflate=0.0, mirror_tex=False):
        fc = faces(u, v, w, h, d)
        paste(canvas, tex, fc[f], *rect(x0 - inflate, y0 - inflate, x0 + w + inflate, y0 + h + inflate), mirror=mirror_tex)

    if back:
        # far parts first: arms/legs, body, pack, tanks, head, helmet
        part(40, 16, 4, 12, 4, -8, 0); part(32, 48, 4, 12, 4, 4, 0)
        part(0, 16, 4, 12, 4, -3.9, 12); part(16, 48, 4, 12, 4, -0.1, 12)
        part(16, 16, 8, 12, 4, -4, 0)
        part(64, 24, 8, 10, 4, -4, 1)
        part(88, 24, 2, 8, 2, -3, 2); part(88, 24, 2, 8, 2, 1, 2, mirror_tex=True)
        part(96, 24, 1, 6, 1, 2.5, -4)
        part(0, 0, 8, 8, 8, -4, -8); part(32, 0, 8, 8, 8, -4, -8, inflate=0.6)
    else:
        part(0, 16, 4, 12, 4, -3.9, 12); part(16, 48, 4, 12, 4, -0.1, 12)
        part(40, 16, 4, 12, 4, -8, 0); part(32, 48, 4, 12, 4, 4, 0)
        part(16, 16, 8, 12, 4, -4, 0)
        part(64, 16, 6, 4, 1, -3, 2)
        part(0, 0, 8, 8, 8, -4, -8); part(32, 0, 8, 8, 8, -4, -8, inflate=0.6)
    return canvas.crop((0, 0, 20 * S, 36 * S))


names = sys.argv[1:] or ["voyagermale1_d", "voyagerfemale1_a", "voyagermale1_w", "voyagerfemale1_b"]
figs = []
for n in names:
    tex = Image.open(f"{TEX}/{n}.png").convert("RGBA")
    figs.append(figure(tex)); figs.append(figure(tex, back=True))
W = sum(f.width for f in figs) + 10 * (len(figs) + 1)
out = Image.new("RGBA", (W, figs[0].height + 20), (44, 46, 58, 255))
x = 10
for f in figs:
    out.alpha_composite(f, (x, 10)); x += f.width + 10
out.convert("RGB").save(paths.out("voyager_suit_preview.png"))
print(out.size)
