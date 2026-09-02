"""Package the Voyager suit preview (model parts + textures) for the Blender renderer."""
import base64, json, os, zipfile, zlib

import paths

def box(tex, frm, size, inflate=0.0, mirror=False):
    return {"tex": list(tex), "from": list(frm), "size": list(size), "inflate": inflate, "mirror": mirror}

PARTS = [
    {"name": "head", "offset": (0, 0, 0), "boxes": [box((0, 0), (-4, -8, -4), (8, 8, 8)), box((32, 0), (-4, -8, -4), (8, 8, 8), 0.6)]},
    {"name": "body", "offset": (0, 0, 0), "boxes": [
        box((16, 16), (-4, 0, -2), (8, 12, 4)),
        box((64, 16), (-3, 2, -3), (6, 4, 1)),            # chest panel
        box((64, 24), (-4, 1, 2), (8, 10, 4)),            # pack
        box((88, 24), (-3, 2, 6), (2, 8, 2)),             # tank right
        box((88, 24), (1, 2, 6), (2, 8, 2), mirror=True),  # tank left
        box((96, 24), (2.5, -4, 4.75), (1, 6, 1)),        # antenna
    ]},
    {"name": "right_arm", "offset": (-5, 2, 0), "rot_x": -18, "boxes": [box((40, 16), (-3, -2, -2), (4, 12, 4))]},
    {"name": "left_arm", "offset": (5, 2, 0), "rot_x": 18, "boxes": [box((32, 48), (-1, -2, -2), (4, 12, 4))]},
    {"name": "right_leg", "offset": (-1.9, 12, 0), "boxes": [box((0, 16), (-2, 0, -2), (4, 12, 4))]},
    {"name": "left_leg", "offset": (1.9, 12, 0), "boxes": [box((16, 48), (-2, 0, -2), (4, 12, 4))]},
]

TEX = paths.res("assets", "minecolonies", "textures", "entity", "citizen", "default")
zpath = paths.out("voyager_suit.zip")
with zipfile.ZipFile(zpath, "w", zipfile.ZIP_DEFLATED) as z:
    z.writestr("parts.json", json.dumps(PARTS))
    for n in ("voyagermale1_d.png", "voyagerfemale1_a.png", "voyagermale1_w.png", "voyagerfemale1_b.png"):
        z.write(os.path.join(TEX, n), n)
    z.write(os.path.join(paths.TOOLS, "mcrender.py"), "mcrender.py")
print(zpath, os.path.getsize(zpath))
