"""The trophies' textures and models: the souvenir (an item icon, and the keepsake on its stand as a
placed block) and the hand-drawn map of the End.

All of it is drawn here, pixel by pixel, from letter grids in an End palette of our own picking -
end stone's pale yellow, purpur's lilac, a chorus-violet crystal and the void's near-black - and no
pixel is taken from Minecraft or any other mod. Light comes from the top left, as on vanilla's
icons; outlines are a darker shade of the thing outlined, never black.

Writes:
  resources/assets/voyager/textures/item/{souvenir,end_map}.png      the inventory icons
  resources/assets/voyager/textures/block/souvenir_{stand,keepsake}.png  the placed block (UV atlases)
  resources/assets/voyager/models/item/{souvenir,end_map}.json
  resources/assets/voyager/models/block/souvenir.json
  resources/assets/voyager/blockstates/souvenir.json
and look-check previews (8x, nearest neighbour; the block in a three-quarter view) into
tools/out/shots/ - git-ignored scratch.

    python tools/gen_souvenir_textures.py
"""
import datetime
import json
import math
import os

from PIL import Image, ImageDraw

import paths

PALETTE = {
    ".": (0, 0, 0, 0),
    # end stone
    "A": (246, 245, 204, 255),
    "B": (232, 230, 176, 255),
    "C": (214, 211, 152, 255),
    "D": (188, 181, 124, 255),
    "E": (148, 139, 94, 255),
    # purpur
    "p": (216, 184, 216, 255),
    "q": (194, 152, 196, 255),
    "r": (168, 120, 172, 255),
    "s": (134, 90, 140, 255),
    "t": (98, 62, 106, 255),
    # the keepsake: a chorus-violet crystal
    "w": (252, 246, 255, 255),
    "x": (228, 208, 246, 255),
    "y": (194, 158, 226, 255),
    "z": (150, 108, 192, 255),
    "Z": (104, 66, 144, 255),
    # the chart: paper, ink and the void between the islands
    "P": (242, 234, 200, 255),
    "Q": (226, 216, 170, 255),
    "R": (200, 188, 140, 255),
    "S": (158, 142, 100, 255),
    "i": (120, 78, 140, 255),
    "v": (40, 28, 58, 255),
    "V": (58, 42, 82, 255),
}

# The inventory icon: a crystal keepsake on a little pedestal - an end-stone plate on a purpur foot.
SOUVENIR_ICON = [
    "................",
    "........w.......",
    ".......wxy......",
    ".......wxyz.....",
    "......xwxyzZ....",
    "......xwxyzZ....",
    "......xwxyzZ....",
    "..w...xwxyzZ....",
    ".wxy..xwxyzZ.w..",
    ".wxyZ.xwxyzZwyz.",
    ".xyzZZyxxyzZxzZ.",
    "..BAAAAAABBBBC..",
    "..DDDDDDDDDDDE..",
    ".....qrrrrs.....",
    "...pqqqqqqqqs...",
    "...srrrrrrrrst..",
]

# The map icon: a chart of the outer islands - a paper sheet with the void inked dark, the big
# central island and two outer ones in end stone, an End city's tower in purpur and the dotted way
# out to it. The bottom right corner is turned over.
END_MAP_ICON = [
    "................",
    ".PPPPPPPPPPPPPP.",
    ".PvvvvvvvvvvVvR.",
    ".PvVvvvvvvvBCvR.",
    ".PvvvvvvvvBBDvR.",
    ".PvvvBBCvvvvvvR.",
    ".PvvBAABBCvvvvR.",
    ".PvBAABBBCDvvvR.",
    ".PvvBCCCDDvvvvR.",
    ".PvvvvDvvxvvVvR.",
    ".PvVvvvvvvxvvvR.",
    ".PvvBCvvvvvxpvR.",
    ".PvvvDvvVvvqrvR.",
    ".PvvvvvvvvvvvQS.",
    ".QRRRRRRRRRRQS..",
    "................",
]

# The placed block's two atlases. Each face of the model takes a small region of these (see MODEL).
#   stand:    plinth top 8x8 at (0,0); plinth sides 8x1 at (8,0) lit and (8,1) shaded;
#             plate top 7x7 at (0,8); plate side 7x1 at (8,3)
#   keepsake: the crystal cluster, 10x10 at (0,0), drawn on a cut-out ground: it is shown on two
#             crossed planes, so it looks like the icon's crystal from every side
STAND = [
    "srrrrrrsqpqqpqqp",
    "rqpqqpqssrssrsrs",
    "rqqqqqqs........",
    "rpqqrqqsDDDDDDE.",
    "rqqqqpqs........",
    "rqpqqqqs........",
    "rqqqpqqs........",
    "ssssssst........",
    "BAABBBC.........",
    "ABBBBBC.........",
    "ABBCBBD.........",
    "BBBBBCD.........",
    "BBCBBBD.........",
    "BBBBCBD.........",
    "CCDDDDE.........",
    "................",
]

KEEPSAKE = [
    ".....w..........",
    "....wxy.........",
    "....wxyz........",
    "...xwxyzZ.......",
    "...xwxyzZ.......",
    ".w.xwxyzZ.......",
    "wxyxwxyzZx......",
    "wxyxwxyzyz......",
    "xyzxwxyzyz......",
    "yzZyxxyzzZ......",
    "................",
    "................",
    "................",
    "................",
    "................",
    "................",
]


def from_grid(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), "a texture is 16 rows of 16"
    im = Image.new("RGBA", (16, 16))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            im.putpixel((x, y), PALETTE[ch])
    return im


def box(frm, to, faces, rotation=None):
    e = {"from": frm, "to": to, "faces": faces}
    if rotation:
        e["rotation"] = rotation
    return e


def face(tex, uv, cull=None):
    f = {"texture": "#" + tex, "uv": uv}
    if cull:
        f["cullface"] = cull
    return f


def sides(tex_a, uv_a, tex_b, uv_b):
    """North and south take the first region, east and west the second."""
    return {"north": face(tex_a, uv_a), "south": face(tex_a, uv_a),
            "west": face(tex_b, uv_b), "east": face(tex_b, uv_b)}


# The keepsake on its stand, in sixteenths of a block: a purpur plinth with an end-stone plate on it,
# and the crystal cluster standing on the plate. The crystal is two crossed planes of a cut-out
# texture (the way vanilla draws its own crystals and plants), not boxes: a box crystal reads as a
# bottle, a crossed picture reads as the crystal on the icon from every side.
MODEL = {
    "parent": "minecraft:block/block",
    "ambientocclusion": False,
    "render_type": "minecraft:cutout",
    "textures": {
        "particle": "voyager:block/souvenir_stand",
        "stand": "voyager:block/souvenir_stand",
        "keepsake": "voyager:block/souvenir_keepsake",
    },
    "elements": [
        box([4, 0, 4], [12, 1, 12], dict(sides("stand", [8, 0, 16, 1], "stand", [8, 1, 16, 2]),
                                         up=face("stand", [0, 0, 8, 8]),
                                         down=face("stand", [0, 0, 8, 8], "down"))),
        box([4.5, 1, 4.5], [11.5, 2, 11.5], dict(sides("stand", [8, 3, 15, 4], "stand", [8, 3, 15, 4]),
                                                 up=face("stand", [0, 8, 7, 15]))),
        dict(box([3, 2, 8], [13, 12, 8], {"north": face("keepsake", [10, 0, 0, 10]),
                                          "south": face("keepsake", [0, 0, 10, 10])},
                 {"origin": [8, 2, 8], "axis": "y", "angle": 45}), shade=False),
        dict(box([8, 2, 3], [8, 12, 13], {"west": face("keepsake", [0, 0, 10, 10]),
                                          "east": face("keepsake", [10, 0, 0, 10])},
                 {"origin": [8, 2, 8], "axis": "y", "angle": 45}), shade=False),
    ],
}

BLOCKSTATE = {"variants": {f"facing={f}": {"model": "voyager:block/souvenir", "y": y}
                           for f, y in (("north", 0), ("east", 90), ("south", 180), ("west", 270))}}


def item_model(name):
    return {"parent": "minecraft:item/generated", "textures": {"layer0": "voyager:item/" + name}}


# ---------------------------------------------------------------------------- the look-check preview

def rot(p, origin, axis, angle):
    a = math.radians(angle)
    c, s = math.cos(a), math.sin(a)
    x, y, z = (p[i] - origin[i] for i in range(3))
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return [x + origin[0], y + origin[1], z + origin[2]]


def render_block(textures, size=320):
    """A three-quarter view of MODEL from the south-east, flat shaded by face: a look-check, not a renderer."""
    scale = size / 28.0
    yaw, pitch = math.radians(225), math.radians(30)
    shade = {"up": 1.0, "north": 0.8, "south": 0.8, "west": 0.6, "east": 0.6, "down": 0.5}

    def project(p):
        x, y, z = p[0] - 8, p[1] - 8, p[2] - 8
        x, z = x * math.cos(yaw) + z * math.sin(yaw), -x * math.sin(yaw) + z * math.cos(yaw)
        y, z = y * math.cos(pitch) - z * math.sin(pitch), y * math.sin(pitch) + z * math.cos(pitch)
        return (size / 2 + x * scale, size / 2 - y * scale + 20, z)

    quads = []
    for el in MODEL["elements"]:
        (x0, y0, z0), (x1, y1, z1) = el["from"], el["to"]
        corners = {
            "north": [(x1, y1, z0), (x0, y1, z0), (x0, y0, z0), (x1, y0, z0)],
            "south": [(x0, y1, z1), (x1, y1, z1), (x1, y0, z1), (x0, y0, z1)],
            "west": [(x0, y1, z0), (x0, y1, z1), (x0, y0, z1), (x0, y0, z0)],
            "east": [(x1, y1, z1), (x1, y1, z0), (x1, y0, z0), (x1, y0, z1)],
            "up": [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)],
            "down": [(x0, y0, z1), (x1, y0, z1), (x1, y0, z0), (x0, y0, z0)],
        }
        for name, f in el["faces"].items():
            pts = corners[name]
            if "rotation" in el:
                r = el["rotation"]
                pts = [rot(p, r["origin"], r["axis"], r["angle"]) for p in pts]
            pr = [project(p) for p in pts]
            # back faces turn clockwise on screen
            (ax, ay, _), (bx, by, _), (cx, cy, _) = pr[0], pr[1], pr[2]
            if (bx - ax) * (cy - ay) - (by - ay) * (cx - ax) <= 0:
                continue
            depth = sum(p[2] for p in pr) / 4
            quads.append((depth, name, dict(f, shade=el.get("shade", True)), pr))
    canvas = Image.new("RGBA", (size, size), (0, 0, 0, 0))
    for depth, name, f, pr in sorted(quads, key=lambda q: q[0]):  # far to near
        tex = textures[f["texture"][1:]]
        u0, v0, u1, v1 = f["uv"]
        lo, hi = min(u0, u1), max(u0, u1)
        src = tex.crop((int(lo), int(v0), max(int(lo) + 1, int(math.ceil(hi))), max(int(v0) + 1, int(math.ceil(v1)))))
        if u0 > u1:
            src = src.transpose(Image.FLIP_LEFT_RIGHT)
        # affine map from screen to the source region: corner 0 = (u0,v0), 1 = (u1,v0), 3 = (u0,v1)
        (px, py, _), (qx, qy, _), _, (sx, sy, _) = pr
        w, h = src.size
        det = (qx - px) * (sy - py) - (sx - px) * (qy - py)
        if abs(det) < 1e-6:
            continue
        a = w * (sy - py) / det
        b = -w * (sx - px) / det
        d = -h * (qy - py) / det
        e = h * (qx - px) / det
        coeffs = (a, b, -a * px - b * py, d, e, -d * px - e * py)
        warped = src.transform((size, size), Image.AFFINE, coeffs, resample=Image.NEAREST)
        k = shade[name] if f.get("shade", True) else 1.0
        warped = Image.merge("RGBA", [ch.point(lambda v, k=k: int(v * k)) if i < 3 else ch
                                      for i, ch in enumerate(warped.split())])
        mask = Image.new("L", (size, size), 0)
        ImageDraw.Draw(mask).polygon([(p[0], p[1]) for p in pr], fill=255)
        alpha = Image.composite(warped.split()[3], Image.new("L", (size, size), 0), mask)
        warped.putalpha(alpha)
        canvas = Image.alpha_composite(canvas, warped)
    return canvas


def preview(images, block):
    shots = os.path.join(paths.OUT, "shots")
    os.makedirs(shots, exist_ok=True)
    day = datetime.date.today().isoformat()
    cell = 16 * 8
    sheet = Image.new("RGBA", (len(images) * (cell + 16) + 16 + block.width, max(cell, block.height) + 32), (139, 139, 139, 255))
    x = 16
    for name, im in images:
        sheet.paste(im.resize((cell, cell), Image.NEAREST), (x, 16), im.resize((cell, cell), Image.NEAREST))
        x += cell + 16
    sheet.paste(block, (x, 16), block)
    sheet.save(os.path.join(shots, f"{day}-trophy-textures.png"))
    # the two icons at their real size in an inventory slot, the way a player meets them
    slot = Image.new("RGBA", (18 * 2 + 4, 20), (139, 139, 139, 255))
    d = ImageDraw.Draw(slot)
    for i, (_, im) in enumerate(images[:2]):
        d.rectangle([1 + i * 20, 1, 18 + i * 20, 18], fill=(55, 55, 55, 255))
        d.rectangle([2 + i * 20, 2, 18 + i * 20, 18], fill=(255, 255, 255, 255))
        d.rectangle([2 + i * 20, 2, 17 + i * 20, 17], fill=(139, 139, 139, 255))
        slot.paste(im, (2 + i * 20, 2), im)
    slot.resize((slot.width * 4, slot.height * 4), Image.NEAREST).save(os.path.join(shots, f"{day}-trophy-slots.png"))
    print("previews in", shots)


def main():
    tex_item = paths.res("assets", "voyager", "textures", "item")
    tex_block = paths.res("assets", "voyager", "textures", "block")
    mod_item = paths.res("assets", "voyager", "models", "item")
    mod_block = paths.res("assets", "voyager", "models", "block")
    states = paths.res("assets", "voyager", "blockstates")
    for d in (tex_item, tex_block, mod_item, mod_block, states):
        os.makedirs(d, exist_ok=True)

    icons = {"souvenir": from_grid(SOUVENIR_ICON), "end_map": from_grid(END_MAP_ICON)}
    atlases = {"souvenir_stand": from_grid(STAND), "souvenir_keepsake": from_grid(KEEPSAKE)}
    for name, im in icons.items():
        im.save(os.path.join(tex_item, name + ".png"))
        write_json(os.path.join(mod_item, name + ".json"), item_model(name))
    for name, im in atlases.items():
        im.save(os.path.join(tex_block, name + ".png"))
    write_json(os.path.join(mod_block, "souvenir.json"), MODEL)
    write_json(os.path.join(states, "souvenir.json"), BLOCKSTATE)
    print("wrote the souvenir and end_map icons, the souvenir block's atlases, models and blockstate")

    block = render_block({"stand": atlases["souvenir_stand"], "keepsake": atlases["souvenir_keepsake"]})
    preview(list(icons.items()) + list(atlases.items()), block)


def write_json(path, data):
    with open(path, "w", encoding="utf-8", newline="\n") as f:
        json.dump(data, f, indent=2)
        f.write("\n")


if __name__ == "__main__":
    main()
