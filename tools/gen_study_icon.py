"""The Observatory's study tab icon: 20x20, the size MineColonies uses for a module tab.

A small dark-blue plate with a star field and a brass rule across it - the astronomer's book of
studies, small enough to read at 20 pixels.
"""
import os

from PIL import Image, ImageDraw

import paths

OUT = paths.res("assets", "voyager", "textures", "gui", "study.png")

NIGHT = (28, 32, 62, 255)
NIGHT2 = (44, 50, 90, 255)
EDGE = (18, 20, 40, 255)
BRASS = (176, 132, 58, 255)
BRASS2 = (226, 186, 100, 255)
STAR = (232, 236, 250, 255)
FAINT = (150, 162, 200, 255)
AMETHYST = (188, 148, 240, 255)


def main():
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    im = Image.new("RGBA", (20, 20), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([1, 1, 18, 18], radius=2, fill=NIGHT, outline=EDGE)
    d.rounded_rectangle([2, 2, 17, 17], radius=2, outline=NIGHT2)
    # the sky
    for x, y, c in ((5, 5, STAR), (9, 4, FAINT), (13, 6, STAR), (6, 9, FAINT),
                    (15, 10, FAINT), (4, 13, FAINT), (11, 9, AMETHYST)):
        d.point((x, y), fill=c)
    d.point((11, 8), fill=FAINT)
    d.point((12, 9), fill=FAINT)
    # the rule the astronomer measures with
    d.line([3, 15, 16, 15], fill=BRASS)
    d.line([3, 14, 16, 14], fill=BRASS2)
    for x in range(4, 17, 3):
        d.point((x, 13), fill=BRASS)
    im.save(OUT)
    print("wrote", OUT, im.size)


if __name__ == "__main__":
    main()
