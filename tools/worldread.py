"""Read blocks back out of a Minecraft world (Anvil region files), to check what a paste left behind.

worldread.box(world_dir, (x0, y0, z0), (x1, y1, z1)) -> {(x, y, z): "ns:name[props]"} for every
non-air block in the box, in world coordinates. Chunks are cached, so reading fifty buildings is
quick. Entities come from the entities/ region files: worldread.entities(world_dir, box).
"""
import gzip
import io
import os
import struct
import zlib

import nbtlib

_chunk_cache = {}


def _read_region_chunk(path, cx, cz):
    key = (path, cx, cz)
    if key in _chunk_cache:
        return _chunk_cache[key]
    tag = None
    if os.path.exists(path):
        with open(path, "rb") as f:
            f.seek(4 * ((cx & 31) + (cz & 31) * 32))
            head = f.read(4)
            off = struct.unpack(">I", b"\0" + head[:3])[0] if len(head) == 4 else 0
            if off:
                f.seek(off * 4096)
                length = struct.unpack(">I", f.read(4))[0]
                kind = f.read(1)[0]
                data = f.read(length - 1)
                if kind == 1:
                    data = gzip.decompress(data)
                elif kind == 2:
                    data = zlib.decompress(data)
                tag = nbtlib.File.parse(io.BytesIO(data))
    _chunk_cache[key] = tag
    return tag


def _state(entry):
    name = str(entry["Name"])
    props = entry.get("Properties")
    if props:
        return name + "[" + ",".join(f"{k}={props[k]}" for k in sorted(props)) + "]"
    return name


def chunk_blocks(world, cx, cz):
    """{(x, y, z): state} for one chunk, air left out."""
    path = os.path.join(world, "region", f"r.{cx >> 5}.{cz >> 5}.mca")
    tag = _read_region_chunk(path, cx, cz)
    out = {}
    if tag is None:
        return out
    root = tag
    for sec in root.get("sections", []):
        sy = int(sec["Y"])
        bs = sec.get("block_states")
        if bs is None:
            continue
        palette = [_state(e) for e in bs["palette"]]
        if len(palette) == 1:
            if palette[0] != "minecraft:air":
                for y in range(16):
                    for z in range(16):
                        for x in range(16):
                            out[(cx * 16 + x, sy * 16 + y, cz * 16 + z)] = palette[0]
            continue
        data = bs.get("data")
        bits = max(4, (len(palette) - 1).bit_length())
        per_long = 64 // bits
        mask = (1 << bits) - 1
        longs = [int(v) & 0xFFFFFFFFFFFFFFFF for v in data]
        i = 0
        for y in range(16):
            for z in range(16):
                for x in range(16):
                    v = (longs[i // per_long] >> ((i % per_long) * bits)) & mask
                    i += 1
                    st = palette[v]
                    if st != "minecraft:air":
                        out[(cx * 16 + x, sy * 16 + y, cz * 16 + z)] = st
    return out


def box(world, lo, hi):
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    out = {}
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            for (x, y, z), st in chunk_blocks(world, cx, cz).items():
                if x0 <= x <= x1 and y0 <= y <= y1 and z0 <= z <= z1:
                    out[(x, y, z)] = st
    return out


def entities(world, lo, hi):
    """[(id, (x, y, z), tag)] for entities inside the box, from the entities/ region files."""
    (x0, y0, z0), (x1, y1, z1) = lo, hi
    found = []
    for cx in range(x0 >> 4, (x1 >> 4) + 1):
        for cz in range(z0 >> 4, (z1 >> 4) + 1):
            path = os.path.join(world, "entities", f"r.{cx >> 5}.{cz >> 5}.mca")
            tag = _read_region_chunk(path, cx, cz)
            if tag is None:
                continue
            for e in tag.get("Entities", []):
                px, py, pz = (float(v) for v in e["Pos"])
                if x0 <= px <= x1 + 1 and y0 <= py <= y1 + 1 and z0 <= pz <= z1 + 1:
                    found.append((str(e["id"]), (px, py, pz), e))
    return found
