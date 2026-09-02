"""Voxel structure DSL for the Voyager hut blueprints.

A Structure is a sparse dict of block positions -> block state strings
("minecraft:oak_stairs[facing=north,half=bottom]"). The same data feeds the
Blender preview (to_render_json) and the Structurize .blueprint export
(to_blueprint), so what Lovkar approves in the render is exactly what the
MineColonies builder builds.

Coordinates: x east, y up, z south (Minecraft). Origin is free; export
normalises to the bounding box.
"""
import gzip
import io
import json
import math
import re

import nbtlib
from nbtlib import tag as T

AIR = "minecraft:air"
DATA_VERSION = 3955  # 1.21.1


def parse_state(s):
    """'ns:name[a=b,c=d]' -> ('ns:name', {'a': 'b', 'c': 'd'})"""
    m = re.match(r"^([a-z0-9_.-]+:[a-z0-9_./-]+)(?:\[(.*)\])?$", s.strip())
    if not m:
        raise ValueError("bad block state: " + s)
    name, props = m.group(1), {}
    if m.group(2):
        for kv in m.group(2).split(","):
            k, v = kv.split("=")
            props[k.strip()] = v.strip()
    return name, props


class Structure:
    def __init__(self, name):
        self.name = name
        self.blocks = {}
        self.tags = {}
        self.anchor = None

    # ---------------------------------------------------------------- editing
    def set(self, x, y, z, block):
        if block is None or block == AIR:
            self.blocks.pop((x, y, z), None)
        else:
            self.blocks[(x, y, z)] = block

    def get(self, x, y, z):
        return self.blocks.get((x, y, z))

    def box(self, x0, y0, z0, x1, y1, z1, block, hollow=False):
        """Inclusive box. hollow=True keeps only the shell."""
        xa, xb = sorted((x0, x1)); ya, yb = sorted((y0, y1)); za, zb = sorted((z0, z1))
        for x in range(xa, xb + 1):
            for y in range(ya, yb + 1):
                for z in range(za, zb + 1):
                    if hollow and xa < x < xb and ya < y < yb and za < z < zb:
                        continue
                    self.set(x, y, z, block)

    def floor(self, x0, z0, x1, z1, y, block):
        self.box(x0, y, z0, x1, y, z1, block)

    def walls(self, x0, y0, z0, x1, y1, z1, block):
        """Four vertical walls of the box (no floor/ceiling)."""
        xa, xb = sorted((x0, x1)); ya, yb = sorted((y0, y1)); za, zb = sorted((z0, z1))
        for x in range(xa, xb + 1):
            for y in range(ya, yb + 1):
                for z in range(za, zb + 1):
                    if x in (xa, xb) or z in (za, zb):
                        self.set(x, y, z, block)

    def column(self, x, z, y0, y1, block):
        for y in range(min(y0, y1), max(y0, y1) + 1):
            self.set(x, y, z, block)

    def disc(self, cx, cz, y, r, block, ring=False, inner=None):
        """Filled or ring disc (Bresenham-ish), r may be fractional."""
        for x in range(int(math.floor(cx - r)) - 1, int(math.ceil(cx + r)) + 2):
            for z in range(int(math.floor(cz - r)) - 1, int(math.ceil(cz + r)) + 2):
                d = math.hypot(x - cx, z - cz)
                if ring:
                    lo = inner if inner is not None else r - 1
                    if lo < d <= r:
                        self.set(x, y, z, block)
                elif d <= r:
                    self.set(x, y, z, block)

    def tag(self, x, y, z, *names):
        self.tags.setdefault((x, y, z), []).extend(names)

    def set_anchor(self, x, y, z, block):
        self.anchor = (x, y, z)
        self.set(x, y, z, block)

    def translated(self, dx, dy, dz):
        """A copy of this structure moved by (dx, dy, dz), tags and anchor included."""
        t = Structure(self.name)
        t.merge(self, dx, dy, dz)
        if self.anchor is not None:
            t.anchor = (self.anchor[0] + dx, self.anchor[1] + dy, self.anchor[2] + dz)
        return t

    def merge(self, other, dx=0, dy=0, dz=0):
        for (x, y, z), b in other.blocks.items():
            self.set(x + dx, y + dy, z + dz, b)
        for (x, y, z), t in other.tags.items():
            self.tag(x + dx, y + dy, z + dz, *t)

    # ---------------------------------------------------------------- queries
    def bounds(self):
        xs = [p[0] for p in self.blocks]; ys = [p[1] for p in self.blocks]; zs = [p[2] for p in self.blocks]
        return (min(xs), min(ys), min(zs)), (max(xs), max(ys), max(zs))

    def size(self):
        (x0, y0, z0), (x1, y1, z1) = self.bounds()
        return x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1

    def count(self):
        from collections import Counter
        return Counter(parse_state(b)[0] for b in self.blocks.values())

    # ---------------------------------------------------------------- exports
    def to_render_json(self):
        (x0, y0, z0), _ = self.bounds()
        blocks = [[x - x0, y - y0, z - z0, b] for (x, y, z), b in sorted(self.blocks.items())]
        return json.dumps({"name": self.name, "blocks": blocks, "size": list(self.size())}, separators=(",", ":"))

    def to_blueprint(self, path, file_name, pack_name, pack_path, building_type,
                     required_mods=("minecolonies", "voyager"), be_type="minecolonies:colonybuilding",
                     box=None):
        """Write a Structurize v1 .blueprint (gzip NBT).

        file_name: e.g. 'launchpad1.blueprint'; pack_path: path inside the pack,
        e.g. 'expedition/launchpad1.blueprint'; building_type: 'voyager:voyager'.
        box: optional ((x0, y0, z0), (x1, y1, z1)) forcing the blueprint size (filled with
        air, which the builder clears) so every level shares one footprint.
        """
        if self.anchor is None:
            raise ValueError("structure has no anchor (hut block)")
        (x0, y0, z0), (x1, y1, z1) = self.bounds()
        if box is not None:
            (bx0, by0, bz0), (bx1, by1, bz1) = box
            if bx0 > x0 or by0 > y0 or bz0 > z0 or bx1 < x1 or by1 < y1 or bz1 < z1:
                raise ValueError(f"{self.name}: blocks {self.bounds()} exceed the box {box}")
            (x0, y0, z0), (x1, y1, z1) = (bx0, by0, bz0), (bx1, by1, bz1)
        sx, sy, sz = x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1
        palette = [AIR]
        index = {AIR: 0}
        arr = [0] * (sx * sy * sz)
        for (x, y, z), b in self.blocks.items():
            if b not in index:
                index[b] = len(palette)
                palette.append(b)
            arr[(y - y0) * sz * sx + (z - z0) * sx + (x - x0)] = index[b]
        packed = []
        for i in range(0, len(arr), 2):
            hi = arr[i]
            lo = arr[i + 1] if i + 1 < len(arr) else 0
            v = (hi << 16) | lo
            if v >= 1 << 31:
                v -= 1 << 32
            packed.append(v)
        pal = T.List[T.Compound]()
        for b in palette:
            name, props = parse_state(b)
            c = T.Compound({"Name": T.String(name)})
            if props:
                c["Properties"] = T.Compound({k: T.String(v) for k, v in props.items()})
            pal.append(c)
        ax, ay, az = self.anchor
        tag_map = T.List[T.Compound]()
        for (tx, ty, tz), names in sorted(self.tags.items()):
            tag_map.append(T.Compound({
                "tagPos": T.Compound({"x": T.Int(tx - ax), "y": T.Int(ty - ay), "z": T.Int(tz - az)}),
                "tagNameList": T.List[T.Compound]([T.Compound({"tagName": T.String(n)}) for n in names]),
            }))
        schematic_name = file_name.replace(".blueprint", "")
        hut = T.Compound({
            "id": T.String(be_type),
            "x": T.Short(ax - x0), "y": T.Short(ay - y0), "z": T.Short(az - z0),
            "type": T.String(building_type),
            "version": T.Int(2),
            "pack": T.String(pack_name),
            "path": T.String(pack_path),
            "blueprintDataProvider": T.Compound({
                "corner1": T.Compound({"x": T.Int(x0 - ax), "y": T.Int(y0 - ay), "z": T.Int(z0 - az)}),
                "corner2": T.Compound({"x": T.Int(x1 - ax), "y": T.Int(y1 - ay), "z": T.Int(z1 - az)}),
                "path": T.String(pack_path),
                "schematicName": T.String(schematic_name),
                "pack": T.String(pack_name),
                "posTagMap": tag_map,
            }),
        })
        root = T.Compound({
            "version": T.Byte(1),
            "mcversion": T.Int(DATA_VERSION),
            "name": T.String(file_name),
            "size_x": T.Short(sx), "size_y": T.Short(sy), "size_z": T.Short(sz),
            "required_mods": T.List[T.String]([T.String(m) for m in required_mods]),
            "palette": pal,
            "blocks": T.IntArray(packed),
            "tile_entities": T.List[T.Compound]([hut]),
            "entities": T.List[T.Compound](),
            "optional_data": T.Compound({"structurize": T.Compound({
                "primary_offset": T.Compound({"x": T.Int(ax - x0), "y": T.Int(ay - y0), "z": T.Int(az - z0)})})}),
        })
        f = nbtlib.File(root, gzipped=True)
        f.save(path)
        return path


def load_blueprint(path):
    """Read a .blueprint back into a Structure (for verification)."""
    f = nbtlib.load(path)
    sx, sy, sz = int(f["size_x"]), int(f["size_y"]), int(f["size_z"])
    raw = [int(v) & 0xFFFFFFFF for v in f["blocks"]]
    arr = []
    for v in raw:
        arr += [(v >> 16) & 0xFFFF, v & 0xFFFF]
    pal = []
    for p in f["palette"]:
        name = str(p["Name"])
        if "Properties" in p:
            props = ",".join(f"{k}={p['Properties'][k]}" for k in sorted(p["Properties"].keys()))
            name += "[" + props + "]"
        pal.append(name)
    s = Structure(str(f["name"]))
    for y in range(sy):
        for z in range(sz):
            for x in range(sx):
                b = pal[arr[y * sz * sx + z * sx + x]]
                if b != AIR:
                    s.set(x, y, z, b)
    po = f["optional_data"]["structurize"]["primary_offset"]
    s.anchor = (int(po["x"]), int(po["y"]), int(po["z"]))
    return s
