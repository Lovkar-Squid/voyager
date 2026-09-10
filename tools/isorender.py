"""A quick isometric preview of a voxel Structure, for eyeballing shapes without Blender.

Not textures - flat colours by block family, three shades for the three visible faces - but
enough to see that a dome sits on a roof, a wing touches the hall and a door has a floor in
front of it. python3 isorender.py observatory keep3 -> tools/out/iso_keep3.png
"""
import sys
from PIL import Image, ImageDraw

from voxel import parse_state

FAMILY = {
    "copper": (120, 170, 130), "oxidized": (90, 160, 140), "weathered": (110, 165, 135),
    "deepslate": (70, 72, 80), "blackstone": (40, 38, 42), "black_brick": (45, 45, 50),
    "beige": (215, 200, 170), "cream": (235, 225, 200), "brown": (150, 120, 90),
    "stone_brick": (150, 150, 150), "cobblestone": (125, 125, 125), "chiseled": (170, 170, 175),
    "sandstone": (225, 205, 150), "sand_stone": (225, 205, 150), "terracotta": (90, 160, 170),
    "quartz": (240, 238, 232), "calcite": (235, 235, 230), "purpur": (170, 120, 175),
    "amethyst": (150, 100, 200), "glass": (200, 230, 240), "tinted": (40, 40, 50),
    "lantern": (255, 220, 120), "sea_lantern": (190, 240, 230), "glowstone": (250, 220, 130),
    "bulb": (250, 200, 100), "end_rod": (250, 250, 250), "door": (110, 80, 50),
    "ladder": (160, 120, 70), "bed": (200, 60, 80), "lectern": (150, 110, 70),
    "bookshelf": (160, 120, 80), "rack": (120, 90, 60), "lightroom": (200, 60, 60),
    "analyzer": (80, 200, 220), "blockhut": (255, 120, 40), "cutter": (170, 150, 120),
    "barrel": (130, 100, 60), "cartography": (150, 120, 90), "enchanting": (90, 60, 120),
    "lodestone": (90, 90, 100), "blue_brick": (60, 80, 160), "blue_": (60, 80, 160),
    "gray_brick": (120, 120, 125), "light_blue": (120, 170, 220), "cyan": (60, 160, 170),
    "shingle": (150, 80, 70), "bricks": (160, 80, 70), "smooth_stone": (170, 170, 170),
    "concrete": (200, 200, 200), "iron_bars": (140, 140, 150), "spruce": (100, 75, 50),
    "mangrove": (110, 50, 50), "warped": (60, 140, 130), "acacia": (190, 100, 50),
    "dark_oak": (70, 50, 30), "cut_": (225, 205, 150), "solidsubstitution": (90, 130, 80),
    "substitution": (90, 130, 80), "squarepillar": (200, 200, 195), "vanilla": (200, 200, 195),
}


def colour(block):
    name = parse_state(block)[0].split(":")[1]
    for key, rgb in FAMILY.items():
        if key in name:
            return rgb
    return (180, 180, 180)


def render(s, path, cell=10, top=False):
    (x0, y0, z0), (x1, y1, z1) = s.bounds()
    ents = {(x, y, z): eid for (x, y, z), eid, *_ in s.entities}
    # iso: screen u = (x - z), v = (x + z)/2 - y
    def proj(x, y, z):
        u = (x - x0) - (z - z0)
        v = ((x - x0) + (z - z0)) * 0.5 - (y - y0)
        return u, v
    us, vs = [], []
    for c in ((x0, y0, z0), (x1, y0, z0), (x0, y0, z1), (x1, y0, z1), (x0, y1, z0), (x1, y1, z1), (x0, y1, z1), (x1, y1, z0)):
        u, v = proj(*c)
        us.append(u); vs.append(v)
    w = int((max(us) - min(us) + 3) * cell)
    h = int((max(vs) - min(vs) + 3) * cell)
    ou, ov = -min(us) + 1.5, -min(vs) + 1.5
    img = Image.new("RGB", (w, h), (24, 26, 32))
    d = ImageDraw.Draw(img)
    order = sorted(s.blocks.items(), key=lambda kv: (kv[0][0] + kv[0][2], kv[0][1]))
    for (x, y, z), b in order:
        rgb = colour(b)
        u, v = proj(x, y, z)
        px, py = (u + ou) * cell, (v + ov) * cell
        hx, hy = cell, cell * 0.5
        # top face
        d.polygon([(px, py - cell), (px + hx, py - cell + hy), (px, py - cell + 2 * hy), (px - hx, py - cell + hy)], fill=rgb)
        dark = tuple(int(c * 0.72) for c in rgb)
        darker = tuple(int(c * 0.5) for c in rgb)
        # left (south-west... x-) face and right (z+) face
        d.polygon([(px - hx, py - cell + hy), (px, py - cell + 2 * hy), (px, py + hy), (px - hx, py)], fill=dark)
        d.polygon([(px + hx, py - cell + hy), (px, py - cell + 2 * hy), (px, py + hy), (px + hx, py)], fill=darker)
    for (x, y, z), eid in ents.items():
        u, v = proj(x, y, z)
        px, py = (u + ou) * cell, (v + ov) * cell
        col = (255, 255, 0) if "camera" in eid else (0, 255, 255)
        d.ellipse([px - 4, py - cell - 4, px + 4, py - cell + 4], fill=col)
    img.save(path)
    return path


if __name__ == "__main__":
    import importlib
    import paths
    mod = importlib.import_module(sys.argv[1])
    names = sys.argv[2:]
    for name in names:
        look, lv = name.rstrip("12345"), int(name[-1])
        st = mod.build(look, lv)
        out = paths.out(f"iso_{sys.argv[1]}_{name}.png")
        render(st, out)
        print(out)
