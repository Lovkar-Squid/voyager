"""Everything the Photo Booth needs that is not code or blocks: its unlock research, its crafter
recipes and its lang keys.

The Photographer is the colony's crafter for Exposure. Every recipe here is one the player would
otherwise have to hand-craft, so teaching them to the booth is the whole point of the profession:
the colony makes its own film, frames, albums and cameras.
"""
import json
import os

import paths

DATA = paths.res("data", "voyager")
LANG = paths.res("assets", "voyager", "lang", "en_us.json")
CRAFTER = "photographer_crafting"

# The Exposure catalogue, in the order the colony grows into it.
# name, min building level, inputs [(id, count)], output (id, count)
RECIPES = [
    ("black_and_white_film", 1, [("minecraft:paper", 3), ("minecraft:iron_nugget", 2),
                                 ("minecraft:black_dye", 1)], ("exposure:black_and_white_film", 1)),
    ("photograph_frame", 1, [("minecraft:stick", 4), ("minecraft:oak_planks", 2)],
     ("exposure:photograph_frame", 2)),
    ("album", 2, [("minecraft:book", 1), ("minecraft:leather", 2), ("minecraft:paper", 3)],
     ("exposure:album", 1)),
    ("color_film", 2, [("minecraft:paper", 3), ("minecraft:iron_nugget", 2),
                       ("minecraft:cyan_dye", 1), ("minecraft:magenta_dye", 1),
                       ("minecraft:yellow_dye", 1)], ("exposure:color_film", 1)),
    ("glass_photograph_frame", 2, [("minecraft:glass_pane", 4), ("minecraft:stick", 2)],
     ("exposure:glass_photograph_frame", 2)),
    ("camera", 3, [("minecraft:copper_ingot", 4), ("minecraft:redstone", 2),
                   ("minecraft:glass_pane", 2), ("minecraft:leather", 2)], ("exposure:camera", 1)),
    ("camera_stand", 3, [("minecraft:copper_ingot", 3), ("minecraft:stick", 4)],
     ("exposure:camera_stand", 1)),
    ("lightroom", 3, [("minecraft:copper_ingot", 5), ("minecraft:redstone", 3),
                      ("minecraft:tinted_glass", 2), ("minecraft:oak_planks", 4)],
     ("exposure:lightroom", 1)),
    ("high_sensitivity_black_and_white_film", 4,
     [("exposure:black_and_white_film", 1), ("minecraft:glowstone_dust", 3)],
     ("exposure:high_sensitivity_black_and_white_film", 1)),
    ("high_sensitivity_color_film", 4,
     [("exposure:color_film", 1), ("minecraft:glowstone_dust", 3)],
     ("exposure:high_sensitivity_color_film", 1)),
    # no "flash" recipe: exposure:flash is a camera attachment model, not an item (alpha.18 crash)
    ("interplanar_projector", 5, [("minecraft:ender_eye", 2), ("minecraft:copper_block", 2),
                                  ("minecraft:tinted_glass", 3), ("minecraft:redstone_block", 1)],
     ("exposure:interplanar_projector", 1)),
]

UNLOCK = {
    "branch": "minecolonies:technology",
    "parentResearch": "minecolonies:technology/memoryaid",
    "researchLevel": 3,
    "sortOrder": 7,
    "subtitle": "com.voyager.research.technology.photobooth.subtitle",
    "costs": [
        {"count": 16, "item": "minecraft:paper"},
        {"count": 4, "item": "minecraft:copper_ingot"},
        {"count": 2, "item": "minecraft:tinted_glass"},
    ],
    "effects": [{"id": "voyager:effects/blockhutphotobooth", "level": 1}],
    "icon": "voyager:blockhutphotobooth",
    "requirements": [{"type": "minecolonies:building", "building": "minecolonies:library", "level": 2}],
}


def main():
    # The recipes are a built-in datapack of their own, offered only when Exposure is loaded
    # (Voyager.addExposurePack); in the main datapack they would break every world without it.
    pack = paths.res("resourcepacks", "exposure")
    os.makedirs(pack, exist_ok=True)
    with open(os.path.join(pack, "pack.mcmeta"), "w") as f:
        json.dump({"pack": {"description": "Voyager - the Photo Booth's recipes (needs Exposure)", "pack_format": 48}}, f, indent=2)
    recipes = os.path.join(pack, "data", "voyager", "crafterrecipes", "photographer")
    os.makedirs(recipes, exist_ok=True)
    for stale in os.listdir(recipes):
        if stale.endswith(".json"):
            os.remove(os.path.join(recipes, stale))
    os.makedirs(f"{DATA}/researches/technology", exist_ok=True)
    os.makedirs(f"{DATA}/researches/effects", exist_ok=True)

    for name, level, inputs, (out_id, out_count) in RECIPES:
        recipe = {
            "type": "recipe",
            "crafter": CRAFTER,
            # MineColonies' own files spell it "id" - "item" is silently not a recipe
            "inputs": [{"id": item, "count": count} for item, count in inputs],
            "intermediate": "minecraft:air",
            "result": {"id": out_id, "count": out_count},
            "min-building-level": level,
            "max-building-level": 5,
        }
        with open(f"{recipes}/{name}.json", "w") as f:
            json.dump(recipe, f, indent=2)

    with open(f"{DATA}/researches/effects/blockhutphotobooth.json", "w") as f:
        json.dump({"effect": True, "levels": [1.0]}, f, indent=2)
    with open(f"{DATA}/researches/technology/photobooth.json", "w") as f:
        json.dump(UNLOCK, f, indent=2)

    with open(LANG) as f:
        lang = json.load(f)
    lang.update({
        "block.minecolonies.blockhutphotobooth": "Photo Booth",
        "block.voyager.blockhutphotobooth": "Photo Booth",
        "block.voyager.blockhutphotobooth.name": "Photo Booth",
        "item.voyager.blockhutphotobooth": "Photo Booth",
        "com.minecolonies.building.photobooth": "Photo Booth",
        "com.voyager.building.photobooth": "Photo Booth",
        "com.voyager.building.photobooth.desc": "The colony's studio, darkroom and print shop. The Photographer crafts every camera, film, frame and album the colony needs, develops the film you bring home - and takes photographs: of colonists, of every building the builder finishes (kept in the colony chronicle, an album on the shelf), and from level 2 of visitors, who come in for a portrait and pay for it in Trade Post coins. Build it as a Copper Dome, a Stargazer's Keep, a Sand Court, a Skyward Station or an Aperture Array. Needs Exposure.",
        "com.voyager.job.photographer": "Photographer",
        "com.minecolonies.coremod.jei.photographer": "Develops, prints and frames the colony's photographs - and takes a few of their own.",
        "voyager:photographer.job.desc": "Runs the colony's studio and darkroom: crafts everything Exposure needs, develops and prints film, photographs the colonists and every new building for the colony chronicle, and takes paid portraits of visitors.",
        "voyager:photographer.skills.desc": "Creativity decides what they can be taught, Dexterity how steady the hand is at the enlarger.",
        "com.voyager.research.technology.photobooth.name": "A Moment Held",
        "com.voyager.research.technology.photobooth.subtitle": "Somebody has to develop it",
        "com.voyager.research.effects.blockhutphotobooth.description": "Unlocks the Photo Booth",
        "com.voyager.photo.taken": "%1$s took a photograph",
        "com.voyager.photo.portrait": "Portrait of %1$s",
        "com.voyager.photo.view": "%1$s",
        "com.voyager.photo.no_camera": "The Photo Booth has no camera - the photographer has asked for one",
        # visitors who come in for a portrait, and what they pay
        "com.voyager.photo.sold": "%1$s bought their portrait from %3$s for %2$s",
        "com.voyager.photo.sat": "%1$s sat for a portrait at the Photo Booth",
        # the colony chronicle: a photograph of every building the builder finishes, kept in an album
        "com.voyager.photo.chronicle": "%1$s, level %2$s (day %3$s)",
        "com.voyager.photo.chronicled": "%1$s photographed the new %2$s for the colony chronicle",
        "com.voyager.photo.chronicle_halfway": "%1$s, level %2$s - halfway up (day %3$s)",
        "com.voyager.photo.chronicled_halfway": "%1$s photographed the %2$s going up, for the colony chronicle",
        "com.voyager.photo.volume": "The chronicle's album is full: %2$s signed \"%1$s\"",
        "com.minecolonies.coremod.gui.townhall.stats.portraits_sold": "Portraits sold: %d",
        "com.minecolonies.coremod.gui.townhall.stats.chronicle_photographs": "Chronicle photographs: %d",
    })
    with open(LANG, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"{len(RECIPES)} recipes, 1 unlock research, lang keys: {len(lang)}")


if __name__ == "__main__":
    main()
