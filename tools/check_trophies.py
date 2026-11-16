"""Checks the trophies' resources against the code that names them (0.4.0's souvenir and map of the End).

  python -I tools/check_trophies.py      (from the repository root; prints OK, or FAIL lines and exits 1)

- every texture of tools/gen_souvenir_textures.py exists, is a 16x16 RGBA PNG, is not empty and is
  used by a model;
- every texture a trophy model names exists; the blockstate names an existing model for all four
  facings; the item models of the registered trophy items exist;
- the souvenir's loot table drops the souvenir and copies the trip story component back from the
  block entity - the component id the code registers;
- every lang key the trophy code names exists in en_us.json, the keys it builds from a prefix
  (places, words, mobs, looks) too, the block and item names, and no key appears twice;
- the sixteen places and their words are the approved ones (docs/PLAN-0.4.0.md), spelled as approved.

Run with -I, so it imports nothing from the folder it is in (nor from the user's site-packages, so
not Pillow either: the PNGs are read with the standard library). It finds the repository from its own
path.
"""
import json
import os
import re
import struct
import sys
import zlib

ROOT = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
SRC = os.path.join(ROOT, "src", "me", "lovkar", "voyager")
ASSETS = os.path.join(ROOT, "resources", "assets", "voyager")
DATA = os.path.join(ROOT, "resources", "data", "voyager")

# What the code is about to name, file by file: the trophy package and the classes around it.
TROPHY_SOURCES = [
    os.path.join(SRC, "trophy", "EndPlace.java"),
    os.path.join(SRC, "trophy", "TripStory.java"),
    os.path.join(SRC, "trophy", "TripTally.java"),
    os.path.join(SRC, "trophy", "Trophies.java"),
    os.path.join(SRC, "item", "SouvenirItem.java"),
    os.path.join(SRC, "item", "EndMapItem.java"),
    os.path.join(SRC, "block", "SouvenirBlock.java"),
    os.path.join(SRC, "block", "SouvenirBlockEntity.java"),
]
TEXTURES = ["item/souvenir", "item/end_map", "block/souvenir_stand", "block/souvenir_keepsake"]

# The approved names (owner decision of 16 Nov 2026), as a sentence reads them.
APPROVED = {
    "hollow_spires": ("the Hollow Spires", "Shard"),
    "chorus_reach": ("Chorus Reach", "Sprig"),
    "pale_shelf": ("the Pale Shelf", "Pebble"),
    "purpur_steps": ("Purpur Steps", "Fragment"),
    "shulker_narrows": ("Shulker Narrows", "Shell"),
    "quiet_rim": ("the Quiet Rim", "Keepsake"),
    "gatewatch_isle": ("Gatewatch Isle", "Stone"),
    "listening_pillars": ("the Listening Pillars", "Shard"),
    "long_drop": ("the Long Drop", "Pebble"),
    "obsidian_hook": ("Obsidian Hook", "Splinter"),
    "dragons_wake": ("the Dragon's Wake", "Scale"),
    "elytra_point": ("Elytra Point", "Feather"),
    "broken_arches": ("the Broken Arches", "Fragment"),
    "lanternless_bay": ("Lanternless Bay", "Stone"),
    "far_isles": ("the Far Isles", "Keepsake"),
    "drifting_keel": ("the Drifting Keel", "Splinter"),
}

failures = []


def fail(what):
    failures.append(what)
    print("FAIL", what)


def read(path):
    with open(path, encoding="utf-8") as f:
        return f.read()


def load_json(path):
    def no_duplicates(pairs):
        seen = {}
        for k, v in pairs:
            if k in seen:
                fail(f"{os.path.relpath(path, ROOT)}: key {k} appears twice")
            seen[k] = v
        return seen
    with open(path, encoding="utf-8") as f:
        return json.load(f, object_pairs_hook=no_duplicates)


def texture_file(ref):
    """'voyager:block/x' -> the PNG's path; None for another namespace (vanilla's, checked by the game)."""
    ns, _, path = ref.partition(":")
    if not path:
        ns, path = "minecraft", ns
    return os.path.join(ASSETS, "textures", *path.split("/")) + ".png" if ns == "voyager" else None


def model_file(ref):
    ns, _, path = ref.partition(":")
    return os.path.join(ASSETS, "models", *path.split("/")) + ".json" if ns == "voyager" else None


def check_model(ref, used):
    path = model_file(ref)
    if path is None:
        return
    if not os.path.isfile(path):
        fail(f"model {ref} is missing ({os.path.relpath(path, ROOT)})")
        return
    model = load_json(path)
    textures = model.get("textures", {})
    for key, tex in textures.items():
        if tex.startswith("#"):
            continue
        file = texture_file(tex)
        if file is None:
            continue
        if not os.path.isfile(file):
            fail(f"model {ref}: texture {key} = {tex} is missing")
        used.add(tex.split(":", 1)[1])
    for el in model.get("elements", []):
        for side, face in el.get("faces", {}).items():
            name = face.get("texture", "")
            if name.startswith("#") and name[1:] not in textures:
                fail(f"model {ref}: a {side} face names #{name[1:]}, which the model does not define")


def read_png(path):
    """(width, height, colour type, bit depth, rows of bytes) of a non-interlaced PNG; raises on anything else."""
    with open(path, "rb") as f:
        data = f.read()
    if data[:8] != bytes([137, 80, 78, 71, 13, 10, 26, 10]):
        raise ValueError("not a PNG")
    pos, ihdr, idat = 8, None, b""
    while pos < len(data):
        length, kind = struct.unpack(">I4s", data[pos:pos + 8])
        body = data[pos + 8:pos + 8 + length]
        if kind == b"IHDR":
            ihdr = struct.unpack(">IIBBBBB", body)
        elif kind == b"IDAT":
            idat += body
        elif kind == b"IEND":
            break
        pos += 12 + length
    width, height, depth, colour, _, _, interlace = ihdr
    if interlace:
        raise ValueError("interlaced")
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[colour]
    bpp = max(1, channels * depth // 8)
    stride = (width * channels * depth + 7) // 8
    raw = zlib.decompress(idat)
    rows, prev = [], bytearray(stride)
    for y in range(height):
        kind, line = raw[y * (stride + 1)], bytearray(raw[y * (stride + 1) + 1:(y + 1) * (stride + 1)])
        for i in range(stride):
            a = line[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if kind == 1:
                line[i] = (line[i] + a) & 255
            elif kind == 2:
                line[i] = (line[i] + b) & 255
            elif kind == 3:
                line[i] = (line[i] + (a + b) // 2) & 255
            elif kind == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        rows.append(bytes(line))
        prev = line
    return width, height, colour, depth, rows


def check_textures(used):
    for tex in TEXTURES:
        path = os.path.join(ASSETS, "textures", *tex.split("/")) + ".png"
        if not os.path.isfile(path):
            fail(f"texture {tex}.png is missing")
            continue
        try:
            width, height, colour, depth, rows = read_png(path)
        except (ValueError, KeyError, TypeError, zlib.error, struct.error) as e:
            fail(f"texture {tex}: not a readable PNG ({e})")
            continue
        if (width, height) != (16, 16):
            fail(f"texture {tex}: {width}x{height}, not 16x16")
        if (colour, depth) != (6, 8):
            fail(f"texture {tex}: PNG colour type {colour} at {depth} bits, not 8-bit RGBA")
        elif not any(row[i] for row in rows for i in range(3, len(row), 4)):
            fail(f"texture {tex}: empty (every pixel transparent)")
        if tex not in used:
            fail(f"texture {tex} is not used by any trophy model")


def registered(voyager_java):
    """The ids the code registers: blocks, items, block entities and data components."""
    blocks = set(re.findall(r'BLOCKS\.register\("([a-z0-9_]+)"', voyager_java))
    items = set(re.findall(r'ITEMS\.register\("([a-z0-9_]+)"', voyager_java))
    components = set(re.findall(r'registerComponentType\("([a-z0-9_]+)"', voyager_java))
    return blocks, items, components


def lang_keys_named():
    """Every full key literal in the trophy sources, and every key built from one of their prefixes."""
    keys, prefixes = set(), set()
    for path in TROPHY_SOURCES:
        if not os.path.isfile(path):
            fail(f"source {os.path.relpath(path, ROOT)} is missing")
            continue
        for literal in re.findall(r'"(com\.voyager\.[a-z0-9_.]+)"', read(path)):
            (prefixes if literal.endswith(".") else keys).add(literal)
    place_src = read(TROPHY_SOURCES[0])
    places = re.findall(r'^\s+[A-Z_]+\("([a-z_]+)", "([a-z]+)"\)[,;]', place_src, re.M)
    story_src = read(TROPHY_SOURCES[1])
    mobs_m = re.search(r'NAMED_MOBS = List\.of\(([^)]*)\)', story_src)
    mobs = re.findall(r'"([a-z_]+)"', mobs_m.group(1)) if mobs_m else []
    looks = re.findall(r'LOOK_[A-Z_]+ = "([a-z_]+)"', story_src)
    expand = {
        "com.voyager.place.": [p for p, _ in places],
        "com.voyager.souvenir.word.": sorted({w for _, w in places}),
        "com.voyager.story.mob.": mobs,
        "com.voyager.story.mobs.": mobs,
        "com.voyager.story.look.": looks,
    }
    for prefix in prefixes:
        if prefix not in expand:
            fail(f"the code builds lang keys from the prefix {prefix}, and this check does not know its ids")
            continue
        if not expand[prefix]:
            fail(f"found no ids for the prefix {prefix} in the sources")
        keys.update(prefix + i for i in expand[prefix])
    if not mobs:
        fail("TripStory.NAMED_MOBS not found")
    if len(looks) != 2:
        fail(f"expected the two looks in TripStory, found {looks}")
    return keys, places


def main():
    voyager_java = read(os.path.join(SRC, "Voyager.java"))
    blocks, items, components = registered(voyager_java)
    for name in ("souvenir",):
        if name not in blocks:
            fail(f"block voyager:{name} is not registered in Voyager.java")
    for name in ("souvenir", "end_map"):
        if name not in items:
            fail(f"item voyager:{name} is not registered in Voyager.java")
    if "trip_story" not in components:
        fail("data component voyager:trip_story is not registered in Voyager.java")

    # models, blockstate, textures
    used = set()
    for name in ("souvenir", "end_map"):
        check_model("voyager:item/" + name, used)
    state_path = os.path.join(ASSETS, "blockstates", "souvenir.json")
    if not os.path.isfile(state_path):
        fail("blockstate souvenir.json is missing")
    else:
        variants = load_json(state_path).get("variants", {})
        for facing in ("north", "east", "south", "west"):
            variant = variants.get("facing=" + facing)
            if not variant:
                fail(f"blockstate souvenir: no variant for facing={facing}")
                continue
            check_model(variant["model"], used)
        if set(variants) - {"facing=" + f for f in ("north", "east", "south", "west")}:
            fail(f"blockstate souvenir: unexpected variants {sorted(variants)}")
    check_textures(used)

    # the loot table keeps the story
    loot_path = os.path.join(DATA, "loot_table", "blocks", "souvenir.json")
    if not os.path.isfile(loot_path):
        fail("loot table blocks/souvenir.json is missing")
    else:
        loot = load_json(loot_path)
        entries = [e for pool in loot.get("pools", []) for e in pool.get("entries", [])]
        names = [e.get("name") for e in entries]
        if names != ["voyager:souvenir"]:
            fail(f"loot table souvenir drops {names}, not the souvenir")
        copies = [f for e in entries for f in e.get("functions", []) if f.get("function") == "minecraft:copy_components"]
        if not copies or copies[0].get("source") != "block_entity":
            fail("loot table souvenir does not copy components from the block entity")
        elif "voyager:trip_story" not in copies[0].get("include", []):
            fail("loot table souvenir does not copy voyager:trip_story")
        for comp in (c for f in copies for c in f.get("include", [])):
            ns, _, path = comp.partition(":")
            if ns == "voyager" and path not in components:
                fail(f"loot table souvenir copies {comp}, which the code does not register")

    # lang
    lang = load_json(os.path.join(ASSETS, "lang", "en_us.json"))
    keys, places = lang_keys_named()
    keys.update({"block.voyager.souvenir", "item.voyager.end_map"})
    for key in sorted(keys):
        if key not in lang:
            fail(f"lang key {key} is named by the code but missing from en_us.json")
        elif not lang[key].strip():
            fail(f"lang key {key} is empty")

    # the approved places
    if dict(places) != {p: w.lower() for p, (_, w) in APPROVED.items()}:
        fail(f"EndPlace does not hold the sixteen approved places and words: {places}")
    for place, (name, word) in APPROVED.items():
        if lang.get("com.voyager.place." + place) != name:
            fail(f"place {place} reads {lang.get('com.voyager.place.' + place)!r}, approved: {name!r}")
        if lang.get("com.voyager.souvenir.word." + word.lower()) != word:
            fail(f"word {word.lower()} reads {lang.get('com.voyager.souvenir.word.' + word.lower())!r}, approved: {word!r}")
    if lang.get("com.voyager.souvenir.name") != "%1$s from %2$s":
        fail("the souvenir name pattern is not '%1$s from %2$s'")

    if failures:
        print(f"{len(failures)} problem(s)")
        sys.exit(1)
    print(f"OK - {len(TEXTURES)} textures, {len(keys)} lang keys, {len(places)} places, models, blockstate and loot table")


if __name__ == "__main__":
    main()
