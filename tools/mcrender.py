"""Minecraft-style voxel renderer for Blender (runs INSIDE Blender, Python 3.11+).

Builds one mesh per block texture from a JSON list of blocks
([x, y, z, "ns:name[props]"]), textures straight out of the vanilla client jar,
frames the camera and renders a PNG.  Used only for previews - the .blueprint
files are generated from the same voxel data elsewhere.
"""
import base64
import json
import math
import os
import tempfile
import zipfile
import zlib

import bpy
from mathutils import Vector

# The vanilla 1.21.1 client jar (block textures are read straight out of it) and a cache folder
# for the extracted textures. Override with the MC_JAR / MC_TEX_DIR environment variables.
JAR = os.environ.get("MC_JAR", os.path.expanduser("~/.minecraft/versions/1.21.1/1.21.1.jar"))
TEX_DIR = os.environ.get("MC_TEX_DIR", os.path.join(tempfile.gettempdir(), "voyager_tex"))

# block name -> (top, side, bottom) texture names; None = same as side
SPECIAL = {
    "quartz_block": ("quartz_block_top", "quartz_block_side", "quartz_block_bottom"),
    "quartz_pillar": ("quartz_pillar_top", "quartz_pillar", None),
    "smooth_quartz": ("quartz_block_bottom", "quartz_block_bottom", None),
    "chiseled_quartz_block": ("chiseled_quartz_block_top", "chiseled_quartz_block", None),
    "purpur_pillar": ("purpur_pillar_top", "purpur_pillar", None),
    "blackstone": ("blackstone_top", "blackstone", None),
    "grass_block": ("grass_block_top", "grass_block_side", "dirt"),
    "sandstone": ("sandstone_top", "sandstone", "sandstone_bottom"),
    "smooth_sandstone": ("sandstone_top", "sandstone_top", None),
    "cut_sandstone": ("sandstone_top", "cut_sandstone", None),
    "bone_block": ("bone_block_top", "bone_block_side", None),
    "hay_block": ("hay_block_top", "hay_block_side", None),
    "crafting_table": ("crafting_table_top", "crafting_table_side", "oak_planks"),
    "furnace": ("furnace_top", "furnace_front", None),
    "blast_furnace": ("blast_furnace_top", "blast_furnace_front", None),
    "tnt": ("tnt_top", "tnt_side", "tnt_bottom"),
    "lodestone": ("lodestone_top", "lodestone_side", None),
    "ancient_debris": ("ancient_debris_top", "ancient_debris_side", None),
    "basalt": ("basalt_top", "basalt_side", None),
    "polished_basalt": ("polished_basalt_top", "polished_basalt_side", None),
    "deepslate": ("deepslate_top", "deepslate", None),
    "cut_copper": ("cut_copper", "cut_copper", None),
    "barrel": ("barrel_top", "barrel_side", "barrel_bottom"),
    "beacon": ("beacon", "beacon", None),
    "dried_kelp_block": ("dried_kelp_top", "dried_kelp_side", None),
    "melon": ("melon_top", "melon_side", None),
    "pumpkin": ("pumpkin_top", "pumpkin_side", None),
    "jack_o_lantern": ("pumpkin_top", "jack_o_lantern", None),
    "cauldron": ("cauldron_top", "cauldron_side", "cauldron_bottom"),
    "composter": ("composter_top", "composter_side", "composter_bottom"),
    "smoker": ("smoker_top", "smoker_front", None),
    "loom": ("loom_top", "loom_side", "loom_bottom"),
    "target": ("target_top", "target_side", None),
    "respawn_anchor": ("respawn_anchor_top_off", "respawn_anchor_side0", "respawn_anchor_bottom"),
    "enchanting_table": ("enchanting_table_top", "enchanting_table_side", "enchanting_table_bottom"),
    "end_portal_frame": ("end_portal_frame_top", "end_portal_frame_side", "end_stone"),
    "chiseled_bookshelf": ("chiseled_bookshelf_top", "chiseled_bookshelf_side", None),
    "sculk_catalyst": ("sculk_catalyst_top", "sculk_catalyst_side", "sculk_catalyst_bottom"),
    "redstone_lamp": ("redstone_lamp_on", "redstone_lamp_on", None),
    "note_block": ("note_block", "note_block", None),
    "mycelium": ("mycelium_top", "mycelium_side", "dirt"),
    "podzol": ("podzol_top", "podzol_side", "dirt"),
    "dirt_path": ("dirt_path_top", "dirt_path_side", "dirt"),
    "snow_block": ("snow", "snow", None),
    "chain": ("chain", "chain", None),
    "iron_bars": ("iron_bars", "iron_bars", None),
    "end_rod": ("end_rod", "end_rod", None),
    "lantern": ("lantern", "lantern", None),
    "soul_lantern": ("soul_lantern", "soul_lantern", None),
    "torch": ("torch", "torch", None),
    "soul_torch": ("soul_torch", "soul_torch", None),
    "ladder": ("ladder", "ladder", None),
    "scaffolding": ("scaffolding_top", "scaffolding_side", "scaffolding_bottom"),
    "chorus_plant": ("chorus_plant", "chorus_plant", None),
    "chorus_flower": ("chorus_flower", "chorus_flower", None),
    "anvil": ("anvil_top", "anvil", None),
    "shroomlight": ("shroomlight", "shroomlight", None),
    "copper_bulb": ("copper_bulb_lit", "copper_bulb_lit", None),
    "waxed_copper_bulb": ("copper_bulb_lit", "copper_bulb_lit", None),
    "spruce_log": ("spruce_log_top", "spruce_log", None),
    "dark_oak_log": ("dark_oak_log_top", "dark_oak_log", None),
    "stripped_dark_oak_log": ("stripped_dark_oak_log_top", "stripped_dark_oak_log", None),
    "oak_log": ("oak_log_top", "oak_log", None),
    "bookshelf": ("oak_planks", "bookshelf", None),
    "lectern": ("lectern_top", "lectern_sides", "lectern_base"),
    "cartography_table": ("cartography_table_top", "cartography_table_side3", "dark_oak_planks"),
    "smithing_table": ("smithing_table_top", "smithing_table_side", "smithing_table_bottom"),
    "observer": ("observer_top", "observer_side", None),
    "dispenser": ("furnace_top", "dispenser_front", None),
    "piston": ("piston_top", "piston_side", "piston_bottom"),
    "sticky_piston": ("piston_top_sticky", "piston_side", "piston_bottom"),
    "daylight_detector": ("daylight_detector_top", "daylight_detector_side", None),
    "jukebox": ("jukebox_top", "jukebox_side", None),
    "sea_lantern": ("sea_lantern", "sea_lantern", None),
    "magma_block": ("magma", "magma", None),
    "prismarine": ("prismarine", "prismarine", None),
    "crying_obsidian": ("crying_obsidian", "crying_obsidian", None),
    "sculk": ("sculk", "sculk", None),
    "stonecutter": ("stonecutter_top", "stonecutter_side", "stonecutter_bottom"),
    "grindstone": ("grindstone_round", "grindstone_side", None),
    "bell": ("bell_top", "bell_side", "bell_bottom"),
    "minecolonies_rack": ("stone_bricks", "stone_bricks", None),
}
# blocks from other mods: fallback texture from the vanilla jar
MOD_FALLBACK = {
    "voyager:blockhutvoyager": ("smithing_table_top", "smithing_table_side", "smithing_table_bottom"),
    "minecolonies:blockminecoloniesrack": ("barrel_top", "barrel_side", "barrel_bottom"),
    "structurize:blocksolidsubstitution": ("dirt", "dirt", None),
    "structurize:blocksubstitution": None,  # air - do not draw
    "minecolonies:blockconstructiontape": ("yellow_concrete", "yellow_concrete", None),
}
TINT = {"grass_block_top": (0.50, 0.75, 0.30), "grass_block_side": None}
EMISSIVE = {"sea_lantern", "glowstone", "end_rod", "lantern", "soul_lantern", "magma", "redstone_lamp_on",
            "shroomlight", "torch", "soul_torch", "copper_bulb_lit", "crying_obsidian", "beacon", "ochre_froglight_top",
            "pearlescent_froglight_top", "verdant_froglight_top", "ochre_froglight_side", "pearlescent_froglight_side",
            "verdant_froglight_side", "amethyst_block", "end_portal_frame_top"}
TRANSPARENT_WORDS = ("glass", "ice", "iron_bars", "chain", "slime", "honey", "leaves", "end_rod", "lantern", "torch",
                     "chorus_flower", "scaffolding", "ladder", "_door", "trapdoor", "candle", "sapling", "grass", "fern",
                     "flower", "dandelion", "poppy", "allium", "cornflower", "lily", "azure", "oxeye", "vine", "amethyst_cluster",
                     "cobweb", "bell", "anvil", "rail", "lever")
FULL_CUBE_EXCEPT = ("stairs", "slab", "fence", "wall", "pane", "bars", "chain", "rod", "torch", "lantern", "carpet",
                    "pressure_plate", "trapdoor", "door", "ladder", "button", "lever", "candle", "chorus", "sign",
                    "banner", "bed", "rail", "lightning_rod", "scaffolding", "flower", "grass", "fern", "sapling",
                    "vine", "anvil", "bell", "campfire", "cauldron", "hopper", "path", "end_portal_frame", "daylight",
                    "enchanting", "lectern", "stonecutter", "grindstone", "brewing", "composter", "conduit", "snow")

_tex_cache = {}
_mat_cache = {}


def texture_path(name):
    if name in _tex_cache:
        return _tex_cache[name]
    os.makedirs(TEX_DIR, exist_ok=True)
    path = os.path.join(TEX_DIR, name + ".png")
    if not os.path.exists(path):
        with zipfile.ZipFile(JAR) as z:
            entry = f"assets/minecraft/textures/block/{name}.png"
            if entry not in z.namelist():
                _tex_cache[name] = None
                return None
            with open(path, "wb") as f:
                f.write(z.read(entry))
    _tex_cache[name] = path
    return path


def textures_for(block_id):
    """block id (with namespace, no props) -> (top, side, bottom) texture names, or None for air."""
    if block_id in MOD_FALLBACK:
        fb = MOD_FALLBACK[block_id]
        return None if fb is None else (fb[0], fb[1], fb[2] or fb[1])
    if not block_id.startswith("minecraft:"):
        return ("stone", "stone", "stone")
    name = block_id.split(":", 1)[1]
    if name.startswith("waxed_"):
        name = name[6:]
    if name in SPECIAL:
        t, s, b = SPECIAL[name]
        return (t, s, b or s)
    # stairs/slabs/walls/fences derive from their base block
    for suffix in ("_stairs", "_slab", "_wall", "_fence_gate", "_fence", "_pane", "_trapdoor", "_door", "_button",
                   "_pressure_plate", "_carpet"):
        if name.endswith(suffix):
            base = name[: -len(suffix)]
            fixes = {"stone_brick": "stone_bricks", "end_stone_brick": "end_stone_bricks", "nether_brick": "nether_bricks",
                     "red_nether_brick": "red_nether_bricks", "deepslate_brick": "deepslate_bricks",
                     "deepslate_tile": "deepslate_tiles", "polished_blackstone_brick": "polished_blackstone_bricks",
                     "purpur": "purpur_block", "quartz": "quartz_block", "smooth_quartz": "quartz_block_bottom",
                     "prismarine_brick": "prismarine_bricks", "mud_brick": "mud_bricks", "brick": "bricks",
                     "cut_copper": "cut_copper", "smooth_stone": "smooth_stone", "cobbled_deepslate": "cobbled_deepslate",
                     "glass": "glass", "iron": "iron_block", "oak": "oak_planks", "spruce": "spruce_planks",
                     "dark_oak": "dark_oak_planks", "birch": "birch_planks", "crimson": "crimson_planks",
                     "warped": "warped_planks", "bamboo": "bamboo_planks", "cherry": "cherry_planks",
                     "tuff_brick": "tuff_bricks", "polished_tuff": "polished_tuff", "sandstone": "sandstone_top",
                     "red_sandstone": "red_sandstone_top", "smooth_sandstone": "sandstone_top"}
            base = fixes.get(base, base)
            if base in SPECIAL:
                t, s, b = SPECIAL[base]
                return (t, s, b or s)
            if base.endswith("_stained_glass_pane"):
                base = base[: -5]
            return (base, base, base)
    if name.endswith("_wool") or name.endswith("_concrete") or name.endswith("_terracotta") or name.endswith("_stained_glass"):
        return (name, name, name)
    return (name, name, name)


def is_transparent(tex):
    return any(w in tex for w in TRANSPARENT_WORDS)


def get_material(tex):
    if tex in _mat_cache:
        return _mat_cache[tex]
    mat = bpy.data.materials.new("mc_" + tex)
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    bsdf.inputs["Roughness"].default_value = 0.85
    bsdf.inputs["Specular IOR Level"].default_value = 0.2 if "Specular IOR Level" in bsdf.inputs else 0.2
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    path = texture_path(tex)
    if path:
        img = bpy.data.images.load(path, check_existing=True)
        img.colorspace_settings.name = "sRGB"
        tex_node = nt.nodes.new("ShaderNodeTexImage")
        tex_node.image = img
        tex_node.interpolation = "Closest"
        tex_node.extension = "REPEAT"
        # animated textures are vertical strips: show only the top 16x16 frame
        w, h = img.size
        if h > w and w > 0:
            mapping = nt.nodes.new("ShaderNodeMapping")
            coord = nt.nodes.new("ShaderNodeTexCoord")
            mapping.inputs["Scale"].default_value = (1.0, w / h, 1.0)
            mapping.inputs["Location"].default_value = (0.0, 1.0 - w / h, 0.0)
            nt.links.new(coord.outputs["UV"], mapping.inputs["Vector"])
            nt.links.new(mapping.outputs["Vector"], tex_node.inputs["Vector"])
        color_out = tex_node.outputs["Color"]
        if TINT.get(tex):
            mix = nt.nodes.new("ShaderNodeMixRGB")
            mix.blend_type = "MULTIPLY"
            mix.inputs["Fac"].default_value = 1.0
            mix.inputs["Color2"].default_value = (*TINT[tex], 1.0)
            nt.links.new(color_out, mix.inputs["Color1"])
            color_out = mix.outputs["Color"]
        nt.links.new(color_out, bsdf.inputs["Base Color"])
        if tex in EMISSIVE:
            nt.links.new(color_out, bsdf.inputs["Emission Color"])
            bsdf.inputs["Emission Strength"].default_value = 3.0
        if is_transparent(tex):
            nt.links.new(tex_node.outputs["Alpha"], bsdf.inputs["Alpha"])
            try:
                mat.surface_render_method = "BLENDED"
            except Exception:
                mat.blend_method = "BLEND"
            mat.use_backface_culling = False
    else:
        bsdf.inputs["Base Color"].default_value = (0.8, 0.2, 0.8, 1.0)  # missing texture magenta
    _mat_cache[tex] = mat
    return mat


# ------------------------------------------------------------------ geometry
def boxes_for(name, props):
    """Return list of (x0,y0,z0,x1,y1,z1) in block units [0,1] for a block."""
    def full():
        return [(0, 0, 0, 1, 1, 1)]
    if name.endswith("_slab"):
        t = props.get("type", "bottom")
        if t == "double":
            return full()
        return [(0, 0.5, 0, 1, 1, 1)] if t == "top" else [(0, 0, 0, 1, 0.5, 1)]
    if name.endswith("_stairs"):
        facing = props.get("facing", "north")
        half = props.get("half", "bottom")
        # bottom step + high part on the 'facing' side
        step = (0, 0, 0, 1, 0.5, 1) if half == "bottom" else (0, 0.5, 0, 1, 1, 1)
        hi = {"north": (0, 0, 0, 1, 1, 0.5), "south": (0, 0, 0.5, 1, 1, 1),
              "west": (0, 0, 0, 0.5, 1, 1), "east": (0.5, 0, 0, 1, 1, 1)}[facing]
        shape = props.get("shape", "straight")
        boxes = [step, hi]
        if shape in ("inner_left", "inner_right", "outer_left", "outer_right"):
            # approximate corners with the straight high part only (preview)
            pass
        return boxes
    if name.endswith("_fence") or name.endswith("_fence_gate"):
        boxes = [(6 / 16, 0, 6 / 16, 10 / 16, 1, 10 / 16)]
        for d, box in (("north", (7 / 16, 6 / 16, 0, 9 / 16, 15 / 16, 6 / 16)), ("south", (7 / 16, 6 / 16, 10 / 16, 9 / 16, 15 / 16, 1)),
                       ("west", (0, 6 / 16, 7 / 16, 6 / 16, 15 / 16, 9 / 16)), ("east", (10 / 16, 6 / 16, 7 / 16, 1, 15 / 16, 9 / 16))):
            if props.get(d) == "true":
                boxes.append(box)
        if name.endswith("_fence_gate"):
            return [(0, 5 / 16, 6 / 16, 1, 1, 10 / 16)] if props.get("facing") in ("north", "south") else [(6 / 16, 5 / 16, 0, 10 / 16, 1, 1)]
        return boxes
    if name.endswith("_wall"):
        boxes = [(4 / 16, 0, 4 / 16, 12 / 16, 1, 12 / 16)]
        for d, box in (("north", (5 / 16, 0, 0, 11 / 16, 14 / 16, 4 / 16)), ("south", (5 / 16, 0, 12 / 16, 11 / 16, 14 / 16, 1)),
                       ("west", (0, 0, 5 / 16, 4 / 16, 14 / 16, 11 / 16)), ("east", (12 / 16, 0, 5 / 16, 1, 14 / 16, 11 / 16))):
            if props.get(d, "none") not in ("none", "false"):
                boxes.append(box)
        return boxes
    if name.endswith("_pane") or name == "iron_bars":
        boxes = [(7 / 16, 0, 7 / 16, 9 / 16, 1, 9 / 16)]
        arms = {"north": (7 / 16, 0, 0, 9 / 16, 1, 7 / 16), "south": (7 / 16, 0, 9 / 16, 9 / 16, 1, 1),
                "west": (0, 0, 7 / 16, 7 / 16, 1, 9 / 16), "east": (9 / 16, 0, 7 / 16, 1, 1, 9 / 16)}
        any_arm = False
        for d, box in arms.items():
            if props.get(d) == "true":
                boxes.append(box); any_arm = True
        if not any_arm:  # lone pane: draw as a cross
            boxes += [arms["north"], arms["south"]]
        return boxes
    if name in ("end_rod",):
        f = props.get("facing", "up")
        if f in ("up", "down"):
            return [(6 / 16, 0, 6 / 16, 10 / 16, 1, 10 / 16)]
        if f in ("north", "south"):
            return [(6 / 16, 6 / 16, 0, 10 / 16, 10 / 16, 1)]
        return [(0, 6 / 16, 6 / 16, 1, 10 / 16, 10 / 16)]
    if name in ("chain", "lightning_rod"):
        return [(6.5 / 16, 0, 6.5 / 16, 9.5 / 16, 1, 9.5 / 16)]
    if name in ("torch", "soul_torch", "redstone_torch"):
        return [(7 / 16, 0, 7 / 16, 9 / 16, 10 / 16, 9 / 16)]
    if name.startswith("wall_torch") or name.endswith("wall_torch"):
        return [(7 / 16, 3 / 16, 7 / 16, 9 / 16, 13 / 16, 9 / 16)]
    if name in ("lantern", "soul_lantern"):
        hanging = props.get("hanging") == "true"
        y0 = 1 / 16 if not hanging else 1 / 16
        return [(5 / 16, y0, 5 / 16, 11 / 16, y0 + 7 / 16, 11 / 16), (6 / 16, y0 + 7 / 16, 6 / 16, 10 / 16, y0 + 9 / 16, 10 / 16)]
    if name.endswith("_carpet") or name.endswith("_pressure_plate") or name == "snow":
        return [(0, 0, 0, 1, 1 / 16, 1)]
    if name.endswith("_trapdoor"):
        if props.get("open") == "true":
            f = props.get("facing", "north")
            return {"north": [(0, 0, 13 / 16, 1, 1, 1)], "south": [(0, 0, 0, 1, 1, 3 / 16)],
                    "west": [(13 / 16, 0, 0, 1, 1, 1)], "east": [(0, 0, 0, 3 / 16, 1, 1)]}[f]
        return [(0, 13 / 16, 0, 1, 1, 1)] if props.get("half") == "top" else [(0, 0, 0, 1, 3 / 16, 1)]
    if name.endswith("_door"):
        f = props.get("facing", "north")
        return {"north": [(0, 0, 0, 1, 1, 3 / 16)], "south": [(0, 0, 13 / 16, 1, 1, 1)],
                "west": [(0, 0, 0, 3 / 16, 1, 1)], "east": [(13 / 16, 0, 0, 1, 1, 1)]}[f]
    if name == "ladder":
        f = props.get("facing", "north")
        return {"north": [(0, 0, 13 / 16, 1, 1, 1)], "south": [(0, 0, 0, 1, 1, 3 / 16)],
                "west": [(13 / 16, 0, 0, 1, 1, 1)], "east": [(0, 0, 0, 3 / 16, 1, 1)]}[f]
    if name.endswith("_button") or name == "lever":
        return [(5 / 16, 0, 6 / 16, 11 / 16, 2 / 16, 10 / 16)]
    if name.endswith("candle"):
        return [(7 / 16, 0, 7 / 16, 9 / 16, 6 / 16, 9 / 16)]
    if name == "chorus_plant":
        return [(4 / 16, 4 / 16, 4 / 16, 12 / 16, 12 / 16, 12 / 16), (6 / 16, 0, 6 / 16, 10 / 16, 1, 10 / 16)]
    if name == "chorus_flower":
        return [(2 / 16, 2 / 16, 2 / 16, 14 / 16, 14 / 16, 14 / 16)]
    if name == "end_portal_frame":
        return [(0, 0, 0, 1, 13 / 16, 1)]
    if name in ("daylight_detector", "enchanting_table", "stonecutter"):
        return [(0, 0, 0, 1, 12 / 16, 1)]
    if name in ("anvil", "chipped_anvil", "damaged_anvil"):
        return [(2 / 16, 0, 2 / 16, 14 / 16, 4 / 16, 14 / 16), (4 / 16, 4 / 16, 6 / 16, 12 / 16, 10 / 16, 10 / 16), (3 / 16, 10 / 16, 0, 13 / 16, 1, 1)]
    if name in ("bell",):
        return [(4 / 16, 4 / 16, 4 / 16, 12 / 16, 1, 12 / 16)]
    if name == "scaffolding":
        return [(0, 14 / 16, 0, 1, 1, 1), (0, 0, 0, 2 / 16, 1, 2 / 16), (14 / 16, 0, 0, 1, 1, 2 / 16), (0, 0, 14 / 16, 2 / 16, 1, 1), (14 / 16, 0, 14 / 16, 1, 1, 1)]
    if name in ("composter", "cauldron", "hopper"):
        return [(0, 0, 0, 1, 1, 1)]
    if name.endswith("_bed"):
        return [(0, 0, 0, 1, 9 / 16, 1)]
    if name in ("grass", "short_grass", "tall_grass", "fern") or name.endswith("_sapling") or name.endswith("_flower") or name in ("dandelion", "poppy", "allium", "azure_bluet", "oxeye_daisy", "cornflower", "lily_of_the_valley"):
        return [(4 / 16, 0, 4 / 16, 12 / 16, 12 / 16, 12 / 16)]
    if name in ("dirt_path", "farmland"):
        return [(0, 0, 0, 1, 15 / 16, 1)]
    return full()


def is_full_opaque(name, props):
    if any(w in name for w in FULL_CUBE_EXCEPT):
        return False
    if any(w in name for w in TRANSPARENT_WORDS):
        return False
    return True


FACES = {  # face -> (normal, 4 corners as (x,y,z) selectors on box (0=min,1=max)), CCW seen from outside
    "up": ((0, 1, 0), [(0, 1, 1), (1, 1, 1), (1, 1, 0), (0, 1, 0)]),
    "down": ((0, -1, 0), [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)]),
    "north": ((0, 0, -1), [(1, 0, 0), (0, 0, 0), (0, 1, 0), (1, 1, 0)]),
    "south": ((0, 0, 1), [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]),
    "west": ((-1, 0, 0), [(0, 0, 0), (0, 0, 1), (0, 1, 1), (0, 1, 0)]),
    "east": ((1, 0, 0), [(1, 0, 1), (1, 0, 0), (1, 1, 0), (1, 1, 1)]),
}


def build_meshes(blocks):
    """blocks: list of [x,y,z,state]. Creates Blender objects; returns (objects, bounds)."""
    parsed = {}
    for x, y, z, state in blocks:
        if "[" in state:
            name, rest = state.split("[", 1)
            props = dict(kv.split("=") for kv in rest[:-1].split(",")) if rest[:-1] else {}
        else:
            name, props = state, {}
        parsed[(x, y, z)] = (name, props)
    opaque = {p for p, (n, pr) in parsed.items() if is_full_opaque(n.split(":", 1)[-1] if n.startswith("minecraft:") else n, pr) and textures_for(n) is not None}
    per_tex = {}  # tex -> (verts, faces, uvs)
    for (x, y, z), (name, props) in parsed.items():
        texs = textures_for(name)
        if texs is None:
            continue
        top, side, bottom = texs
        short = name.split(":", 1)[1] if name.startswith("minecraft:") else name
        full = (x, y, z) in opaque
        for (bx0, by0, bz0, bx1, by1, bz1) in boxes_for(short, props):
            for face, (nrm, corners) in FACES.items():
                if full:
                    nb = (x + nrm[0], y + nrm[1], z + nrm[2])
                    if nb in opaque:
                        continue
                tex = top if face == "up" else bottom if face == "down" else side
                verts, faces, uvs = per_tex.setdefault(tex, ([], [], []))
                base = len(verts)
                for (sx, sy, sz) in corners:
                    px = x + (bx1 if sx else bx0)
                    py = y + (by1 if sy else by0)
                    pz = z + (bz1 if sz else bz0)
                    # Minecraft (x east, y up, z south) -> Blender (x east, y north, z up)
                    verts.append((px, -pz, py))
                faces.append((base, base + 1, base + 2, base + 3))
                if face in ("up", "down"):
                    uvs.append([(bx0, 1 - bz1), (bx1, 1 - bz1), (bx1, 1 - bz0), (bx0, 1 - bz0)] if face == "up"
                               else [(bx0, bz0), (bx1, bz0), (bx1, bz1), (bx0, bz1)])
                else:
                    u0, u1 = (bx0, bx1) if face in ("north", "south") else (bz0, bz1)
                    uvs.append([(u0, by0), (u1, by0), (u1, by1), (u0, by1)])
    objects = []
    for tex, (verts, faces, uvs) in per_tex.items():
        mesh = bpy.data.meshes.new("mc_" + tex)
        mesh.from_pydata(verts, [], faces)
        mesh.update()
        uv_layer = mesh.uv_layers.new(name="UVMap")
        i = 0
        for f in uvs:
            for uv in f:
                uv_layer.data[i].uv = uv
                i += 1
        mesh.materials.append(get_material(tex))
        obj = bpy.data.objects.new("mc_" + tex, mesh)
        bpy.context.scene.collection.objects.link(obj)
        objects.append(obj)
    xs = [b[0] for b in blocks]; ys = [b[1] for b in blocks]; zs = [b[2] for b in blocks]
    bounds = ((min(xs), min(ys), min(zs)), (max(xs) + 1, max(ys) + 1, max(zs) + 1))
    return objects, bounds


def clear_scene():
    for obj in list(bpy.data.objects):
        bpy.data.objects.remove(obj, do_unlink=True)
    for coll in (bpy.data.meshes, bpy.data.materials, bpy.data.images, bpy.data.lights, bpy.data.cameras):
        for block in list(coll):
            if block.users == 0:
                coll.remove(block)
    _mat_cache.clear()


def setup_scene(bounds, ground=True, angle_deg=45.0, pitch_deg=32.0, ortho=True):
    scene = bpy.context.scene
    (x0, y0, z0), (x1, y1, z1) = bounds
    # Blender coords: (x, -z, y)
    cx, cy, cz = (x0 + x1) / 2, -(z0 + z1) / 2, (y0 + y1) / 2
    size = max(x1 - x0, z1 - z0, y1 - y0)
    if ground:
        mesh = bpy.data.meshes.new("ground")
        r = size * 3
        mesh.from_pydata([(cx - r, cy - r, y0), (cx + r, cy - r, y0), (cx + r, cy + r, y0), (cx - r, cy + r, y0)], [], [(0, 1, 2, 3)])
        obj = bpy.data.objects.new("ground", mesh)
        scene.collection.objects.link(obj)
        mat = bpy.data.materials.new("ground_mat")
        mat.use_nodes = True
        bsdf = mat.node_tree.nodes["Principled BSDF"]
        bsdf.inputs["Base Color"].default_value = (0.36, 0.52, 0.24, 1.0)
        bsdf.inputs["Roughness"].default_value = 1.0
        mesh.materials.append(mat)
    # sun
    sun_data = bpy.data.lights.new("sun", "SUN")
    sun_data.energy = 3.5
    sun_data.angle = math.radians(4)
    sun = bpy.data.objects.new("sun", sun_data)
    scene.collection.objects.link(sun)
    sun.rotation_euler = (math.radians(50), math.radians(10), math.radians(angle_deg + 40))
    # world
    world = bpy.data.worlds.get("World") or bpy.data.worlds.new("World")
    scene.world = world
    world.use_nodes = True
    bg = world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs["Color"].default_value = (0.62, 0.74, 0.90, 1.0)
        bg.inputs["Strength"].default_value = 0.9
    # camera
    cam_data = bpy.data.cameras.new("cam")
    cam = bpy.data.objects.new("cam", cam_data)
    scene.collection.objects.link(cam)
    scene.camera = cam
    a = math.radians(angle_deg)
    p = math.radians(pitch_deg)
    dist = size * 3.0
    direction = Vector((math.sin(a) * math.cos(p), -math.cos(a) * math.cos(p), math.sin(p)))
    rot = (-direction).to_track_quat("-Z", "Y")
    right = rot @ Vector((1, 0, 0))
    up = rot @ Vector((0, 1, 0))
    corners = [Vector((X, -Z, Y)) for X in (x0, x1) for Y in (y0, y1) for Z in (z0, z1)]
    rs = [c.dot(right) for c in corners]
    us = [c.dot(up) for c in corners]
    width, height = max(rs) - min(rs), max(us) - min(us)
    # centre of the projected extents, expressed in world space
    centre = Vector((cx, cy, cz))
    centre += right * ((max(rs) + min(rs)) / 2 - centre.dot(right))
    centre += up * ((max(us) + min(us)) / 2 - centre.dot(up))
    cam.location = centre + direction * dist
    cam.rotation_euler = rot.to_euler()
    aspect = 1400 / 1000
    if ortho:
        cam_data.type = "ORTHO"
        cam_data.ortho_scale = max(width, height * aspect) * 1.10 + 2
    else:
        cam_data.lens = 40
    scene.render.engine = "BLENDER_EEVEE_NEXT" if hasattr(bpy.types, "SceneEEVEE") and "BLENDER_EEVEE_NEXT" in [i.identifier for i in bpy.types.RenderSettings.bl_rna.properties["engine"].enum_items] else "BLENDER_EEVEE"
    scene.render.resolution_x = 1400
    scene.render.resolution_y = 1000
    scene.render.resolution_percentage = 100
    scene.render.film_transparent = False
    try:
        scene.eevee.taa_render_samples = 32
    except Exception:
        pass
    try:
        scene.view_settings.view_transform = "Standard"
    except Exception:
        pass


def render_blocks(blocks, out_path, title=None, angle_deg=45.0, pitch_deg=32.0):
    clear_scene()
    objects, bounds = build_meshes(blocks)
    setup_scene(bounds, angle_deg=angle_deg, pitch_deg=pitch_deg)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    scene = bpy.context.scene
    scene.render.filepath = out_path
    scene.render.image_settings.file_format = "PNG"
    bpy.ops.render.render(write_still=True)
    return {"out": out_path, "objects": len(objects), "blocks": len(blocks), "bounds": bounds,
            "textures_missing": sorted(k for k, v in _tex_cache.items() if v is None)}


def render_b64(payload_b64, out_path, **kw):
    data = json.loads(zlib.decompress(base64.b64decode(payload_b64)).decode("utf-8"))
    return render_blocks(data["blocks"], out_path, **kw)


# ------------------------------------------------------------------ block model preview
# Minecraft JSON block models (Blockbench style): boxes in 1/16 units with per-face
# textures and UVs, optional rotation about an origin. Rendered as one mesh per element.
MODEL_UV_CORNERS = {  # face -> corners for (u1,v1), (u2,v1), (u2,v2), (u1,v2) as (x,y,z) selectors
    "north": [(1, 1, 0), (0, 1, 0), (0, 0, 0), (1, 0, 0)],
    "south": [(0, 1, 1), (1, 1, 1), (1, 0, 1), (0, 0, 1)],
    "west": [(0, 1, 0), (0, 1, 1), (0, 0, 1), (0, 0, 0)],
    "east": [(1, 1, 1), (1, 1, 0), (1, 0, 0), (1, 0, 1)],
    "up": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
    "down": [(1, 0, 0), (0, 0, 0), (0, 0, 1), (1, 0, 1)],
}


def _rotate_point(p, rotation):
    if not rotation:
        return p
    axis, angle, origin = rotation["axis"], math.radians(rotation["angle"]), rotation["origin"]
    x, y, z = p[0] - origin[0], p[1] - origin[1], p[2] - origin[2]
    c, s = math.cos(angle), math.sin(angle)
    if axis == "x":
        y, z = y * c - z * s, y * s + z * c
    elif axis == "y":
        x, z = x * c + z * s, -x * s + z * c
    else:
        x, y = x * c - y * s, x * s + y * c
    return (x + origin[0], y + origin[1], z + origin[2])


def _resolve_texture(model, ref):
    seen = 0
    while ref.startswith("#") and seen < 10:
        ref = model["textures"].get(ref[1:], "")
        seen += 1
    return ref.split("/")[-1]  # minecraft:block/iron_block -> iron_block


def build_model_meshes(model):
    scene = bpy.context.scene
    objects = []
    for i, el in enumerate(model["elements"]):
        a, b = el["from"], el["to"]
        rot = el.get("rotation")
        verts, faces, mats, uv_data = [], [], [], []
        mat_index = {}
        for face, spec in el["faces"].items():
            tex = _resolve_texture(model, spec["texture"])
            mat = get_material(tex)
            if tex not in mat_index:
                mat_index[tex] = len(mats)
                mats.append(mat)
            u1, v1, u2, v2 = spec.get("uv", [0, 0, 16, 16])
            uvs = [(u1 / 16, 1 - v1 / 16), (u2 / 16, 1 - v1 / 16), (u2 / 16, 1 - v2 / 16), (u1 / 16, 1 - v2 / 16)]
            r = spec.get("rotation", 0) // 90
            uvs = uvs[r:] + uvs[:r]
            base = len(verts)
            for sel in MODEL_UV_CORNERS[face]:
                p = (a[0] if sel[0] == 0 else b[0], a[1] if sel[1] == 0 else b[1], a[2] if sel[2] == 0 else b[2])
                x, y, z = _rotate_point(p, rot)
                verts.append((x / 16, -z / 16, y / 16))
            # reverse winding so the normal points outward (corners above run clockwise seen from outside)
            faces.append((base + 3, base + 2, base + 1, base))
            uv_data.append([uvs[3], uvs[2], uvs[1], uvs[0]])
        mesh = bpy.data.meshes.new(f"el{i}")
        mesh.from_pydata(verts, [], faces)
        for m in mats:
            mesh.materials.append(m)
        for poly, (face, spec) in zip(mesh.polygons, el["faces"].items()):
            poly.material_index = mat_index[_resolve_texture(model, spec["texture"])]
        uv_layer = mesh.uv_layers.new(name="UVMap")
        for poly, quad_uv in zip(mesh.polygons, uv_data):
            for li, uv in zip(poly.loop_indices, quad_uv):
                uv_layer.data[li].uv = uv
        mesh.update()
        obj = bpy.data.objects.new(f"el{i}", mesh)
        scene.collection.objects.link(obj)
        objects.append(obj)
    return objects


def render_model(model, out_path, angle_deg=45.0, pitch_deg=30.0):
    clear_scene()
    objects = build_model_meshes(model)
    setup_scene(((0, 0, 0), (1, 1, 1)), ground=False, angle_deg=angle_deg, pitch_deg=pitch_deg)
    cam = bpy.context.scene.camera
    cam.data.ortho_scale = 1.9
    bg = bpy.context.scene.world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs["Color"].default_value = (0.16, 0.17, 0.22, 1.0)
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    scene = bpy.context.scene
    scene.render.resolution_x = 1000
    scene.render.resolution_y = 1000
    scene.render.filepath = out_path
    scene.render.image_settings.file_format = "PNG"
    bpy.ops.render.render(write_still=True)
    return {"out": out_path, "elements": len(objects),
            "textures_missing": sorted(k for k, v in _tex_cache.items() if v is None)}


def render_model_b64(payload_b64, out_path, **kw):
    model = json.loads(zlib.decompress(base64.b64decode(payload_b64)).decode("utf-8"))
    return render_model(model, out_path, **kw)


# ------------------------------------------------------------------ entity model preview
# Minecraft entity models: boxes in 1/16 units with Minecraft's box UV layout (texOffs),
# y pointing DOWN (0 = shoulders, 24 = feet). Used to preview the citizen suit textures.
def _box_uv(tex_off, size, tex_w, tex_h, mirror=False):
    u, v = tex_off
    w, h, d = size
    rects = {
        "top": (u + d, v, u + d + w, v + d),
        "bottom": (u + d + w, v, u + d + 2 * w, v + d),
        "right": (u, v + d, u + d, v + d + h),
        "front": (u + d, v + d, u + d + w, v + d + h),
        "left": (u + d + w, v + d, u + 2 * d + w, v + d + h),
        "back": (u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h),
    }
    out = {}
    for face, (x0, y0, x1, y1) in rects.items():
        # UV corners in texture pixels for (u1,v1) top-left, (u2,v1) top-right, (u2,v2), (u1,v2)
        a, b = (x1, x0) if mirror else (x0, x1)
        out[face] = [(a / tex_w, 1 - y0 / tex_h), (b / tex_w, 1 - y0 / tex_h), (b / tex_w, 1 - y1 / tex_h), (a / tex_w, 1 - y1 / tex_h)]
    if mirror:
        out["right"], out["left"] = out["left"], out["right"]
    return out


# which model corner each UV corner maps to, per face; corners as (x, y, z) selectors, y DOWN
ENTITY_UV_CORNERS = {
    "front": [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)],    # -z side: u ~ +x, v ~ +y(down)
    "back": [(1, 0, 1), (0, 0, 1), (0, 1, 1), (1, 1, 1)],     # +z side: u ~ -x
    "right": [(0, 0, 1), (0, 0, 0), (0, 1, 0), (0, 1, 1)],    # -x side: u ~ -z
    "left": [(1, 0, 0), (1, 0, 1), (1, 1, 1), (1, 1, 0)],     # +x side: u ~ +z
    "top": [(0, 0, 1), (1, 0, 1), (1, 0, 0), (0, 0, 0)],      # y min: u ~ +x, v ~ -z
    "bottom": [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],   # y max: u ~ +x, v ~ +z
}


def get_texture_material(path):
    key = "entity:" + path
    if key in _mat_cache:
        return _mat_cache[key]
    mat = bpy.data.materials.new("entity_tex")
    mat.use_nodes = True
    nt = mat.node_tree
    for n in list(nt.nodes):
        nt.nodes.remove(n)
    out = nt.nodes.new("ShaderNodeOutputMaterial")
    bsdf = nt.nodes.new("ShaderNodeBsdfPrincipled")
    bsdf.inputs["Roughness"].default_value = 0.8
    img = bpy.data.images.load(path, check_existing=True)
    img.colorspace_settings.name = "sRGB"
    tex = nt.nodes.new("ShaderNodeTexImage")
    tex.image = img
    tex.interpolation = "Closest"
    nt.links.new(tex.outputs["Color"], bsdf.inputs["Base Color"])
    nt.links.new(tex.outputs["Alpha"], bsdf.inputs["Alpha"])
    nt.links.new(bsdf.outputs["BSDF"], out.inputs["Surface"])
    try:
        mat.surface_render_method = "DITHERED"
    except Exception:
        mat.blend_method = "CLIP"
    mat.use_backface_culling = False
    _mat_cache[key] = mat
    return mat


def build_entity_meshes(parts, texture_path, tex_w=128, tex_h=64, dx=0.0):
    """parts: list of dicts {name, offset:(x,y,z), rot_x (deg, optional), boxes:[{tex:(u,v), from:(x,y,z), size:(w,h,d), inflate, mirror}]}."""
    scene = bpy.context.scene
    mat = get_texture_material(texture_path)
    objects = []
    for part in parts:
        ox, oy, oz = part["offset"]
        rx = math.radians(part.get("rot_x", 0.0))
        for bi, box in enumerate(part["boxes"]):
            x, y, z = box["from"]
            w, h, d = box["size"]
            g = box.get("inflate", 0.0)
            lo = (x - g, y - g, z - g)
            hi = (x + w + g, y + h + g, z + d + g)
            uvs = _box_uv(box["tex"], (w, h, d), tex_w, tex_h, box.get("mirror", False))
            verts, faces, uv_data = [], [], []
            centre = Vector((0.0, 0.0, 0.0))
            corner_pts = []
            for face, sel in ENTITY_UV_CORNERS.items():
                base = len(verts)
                pts = []
                for s in sel:
                    px = lo[0] if s[0] == 0 else hi[0]
                    py = lo[1] if s[1] == 0 else hi[1]
                    pz = lo[2] if s[2] == 0 else hi[2]
                    # rotate about the part pivot (x axis), then translate by the part offset
                    ry = py * math.cos(rx) - pz * math.sin(rx)
                    rz = py * math.sin(rx) + pz * math.cos(rx)
                    wx, wy, wz = px + ox, ry + oy, rz + oz
                    # model space (y down, front = -z) -> Blender (x, z, up); the front faces -Y
                    pts.append(Vector(((wx + dx) / 16, wz / 16, (24 - wy) / 16)))
                corner_pts.append(pts)
                verts.extend(tuple(p) for p in pts)
                faces.append((base, base + 1, base + 2, base + 3))
                uv_data.append(list(uvs[face]))
            # make every face wind outward: flip those whose normal points at the box centre
            centre = sum((p for pts in corner_pts for p in pts), Vector()) / (6 * 4)
            for fi, pts in enumerate(corner_pts):
                normal = (pts[1] - pts[0]).cross(pts[2] - pts[0])
                if normal.dot((pts[0] + pts[2]) / 2 - centre) < 0:
                    b = faces[fi][0]
                    faces[fi] = (b + 3, b + 2, b + 1, b)
                    q = uv_data[fi]
                    uv_data[fi] = [q[3], q[2], q[1], q[0]]
            mesh = bpy.data.meshes.new(f"{part['name']}_{bi}")
            mesh.from_pydata(verts, [], faces)
            mesh.materials.append(mat)
            uv_layer = mesh.uv_layers.new(name="UVMap")
            for poly, quad in zip(mesh.polygons, uv_data):
                for li, uv in zip(poly.loop_indices, quad):
                    uv_layer.data[li].uv = uv
            mesh.update()
            obj = bpy.data.objects.new(mesh.name, mesh)
            scene.collection.objects.link(obj)
            objects.append(obj)
    return objects


def render_entity(parts, texture_paths, out_path, angle_deg=35.0, pitch_deg=12.0, spacing=1.4):
    """Renders the same model once per texture side by side."""
    clear_scene()
    for i, tp in enumerate(texture_paths):
        build_entity_meshes(parts, tp, dx=i * spacing * 16)
    n = len(texture_paths)
    bounds = ((-0.6, 0.0, -0.6), (0.6 + (n - 1) * spacing, 2.0, 0.6))
    setup_scene(bounds, ground=False, angle_deg=angle_deg, pitch_deg=pitch_deg)
    cam = bpy.context.scene.camera
    cam.data.ortho_scale = max(2.6, 1.6 + (n - 1) * spacing * 1.1)
    bg = bpy.context.scene.world.node_tree.nodes.get("Background")
    if bg:
        bg.inputs["Color"].default_value = (0.16, 0.17, 0.22, 1.0)
    scene = bpy.context.scene
    scene.render.resolution_x = 1400
    scene.render.resolution_y = 1000
    os.makedirs(os.path.dirname(out_path), exist_ok=True)
    scene.render.filepath = out_path
    scene.render.image_settings.file_format = "PNG"
    bpy.ops.render.render(write_still=True)
    return {"out": out_path, "textures": len(texture_paths)}
