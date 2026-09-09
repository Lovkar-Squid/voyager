"""Generate the Voyager research tree (University -> Technology, under "Reach for the Stars").

Rules MineColonies enforces: a child's researchLevel is exactly the parent's + 1; the University
must be at least that level (a level-5 University also allows level 6). Effect ids are
voyager:effects/<name>; the code reads them through colony/VoyagerResearch.java.

Files: data/voyager/researches/technology/<name>.json and researches/effects/<name>.json.
Lang keys (assets/voyager/lang/en_us.json):
  com.voyager.research.technology.<name>.name / .subtitle
  com.voyager.research.effects.<name>.description   (%3$s = this level's gain x100)
"""
import json
import os

import paths

DATA = paths.res("data", "voyager", "researches")
LANG = paths.res("assets", "voyager", "lang", "en_us.json")
ROOT = "voyager:technology/voyager"           # Reach for the Stars, level 3
HUT = "voyager:voyager"

EFFECTS = {
    "void_insurance": ([0.25, 0.5], "Voyagers take %3$s%% less damage in the End"),
    "starlight_navigation": ([1.0], "One more expedition per launch window (the Observatory's Launch Window study adds another)"),
    "rapid_refit": ([0.25], "Expeditions take %3$s%% less time"),
    "shulker_whisperer": ([1.0], "Every shulker a Voyager beats leaves an extra shell"),
    "ender_harvest": ([1.0], "Every enderman a Voyager beats leaves an extra ender pearl"),
    "dragon_hunt": ([1.0], "Level 5 Voyagers may run into the dragon - and bring home her head"),
    "long_range_comms": ([1.0], "Voyagers report to the colony chat while they are away"),
    "return_to_sender": ([1.0], "A lost Voyager's gear is brought back to the Departure Point"),
    "buddy_system": ([1.0], "One more Voyager per Departure Point (a Launchpad's crews take turns with the rocket)"),
}

# name, parent, level, sortOrder, hut level required, costs, effect (id, level), icon, title, subtitle
RESEARCHES = [
    ("void_insurance", ROOT, 4, 1, 1,
     [("minecraft:end_stone", 32), ("minecraft:ender_pearl", 4)],
     ("void_insurance", 1), "minecraft:shield",
     "Void Insurance", "Terms and conditions apply beyond the edge of the world"),
    ("void_insurance2", "voyager:technology/void_insurance", 5, 1, 3,
     [("minecraft:end_stone_bricks", 16), ("minecraft:ender_pearl", 8)],
     ("void_insurance", 2), "minecraft:totem_of_undying",
     "Void Insurance II", "Now also covering shulker fire and acts of dragon"),
    ("starlight_navigation", ROOT, 4, 2, 2,
     [("minecraft:end_rod", 4), ("minecraft:ender_eye", 8)],
     ("starlight_navigation", 1), "minecraft:ender_eye",
     "Starlight Navigation", "Second star to the right, and straight on till morning"),
    ("rapid_refit", "voyager:technology/starlight_navigation", 5, 1, 4,
     [("minecraft:purpur_block", 16), ("minecraft:iron_block", 4)],
     ("rapid_refit", 1), "minecraft:firework_rocket",
     "Rapid Refit", "A pit stop at the edge of the void"),
    ("buddy_system", "voyager:technology/starlight_navigation", 5, 2, 3,
     [("minecraft:end_stone_bricks", 32), ("minecraft:ender_pearl", 8)],
     ("buddy_system", 1), "minecraft:armor_stand",
     "Buddy System", "Nobody goes into the void alone"),
    ("ender_harvest", ROOT, 4, 3, 2,
     [("minecraft:chorus_fruit", 32), ("minecraft:ender_pearl", 4)],
     ("ender_harvest", 1), "minecraft:ender_pearl",
     "Ender Harvest", "Pearls of the void, picked fresh"),
    ("shulker_whisperer", ROOT, 4, 4, 3,
     [("minecraft:shulker_shell", 2), ("minecraft:chorus_fruit", 32)],
     ("shulker_whisperer", 1), "minecraft:shulker_shell",
     "Shulker Whisperer", "They open up if you ask nicely"),
    ("dragon_hunt", "voyager:technology/shulker_whisperer", 5, 1, 5,
     [("minecraft:dragon_breath", 4), ("minecraft:end_stone_bricks", 16)],
     ("dragon_hunt", 1), "minecraft:dragon_head",
     "Dragon Hunt", "There is always a bigger fish"),
    ("long_range_comms", ROOT, 4, 5, 1,
     [("minecraft:end_rod", 4), ("minecraft:redstone", 32)],
     ("long_range_comms", 1), "minecraft:lightning_rod",
     "Long-range Comms", "Houston, we have a shulker"),
    ("return_to_sender", "voyager:technology/long_range_comms", 5, 1, 3,
     [("minecraft:ender_chest", 1), ("minecraft:ender_pearl", 8)],
     ("return_to_sender", 1), "minecraft:ender_chest",
     "Return to Sender", "The pack always comes home"),
]


def main():
    os.makedirs(f"{DATA}/technology", exist_ok=True)
    os.makedirs(f"{DATA}/effects", exist_ok=True)
    with open(LANG) as f:
        lang = json.load(f)
    for name, (levels, desc) in EFFECTS.items():
        with open(f"{DATA}/effects/{name}.json", "w") as f:
            json.dump({"effect": True, "levels": levels}, f, indent=2)
        lang[f"com.voyager.research.effects.{name}.description"] = desc
    for name, parent, level, order, hut_level, costs, (effect, effect_level), icon, title, subtitle in RESEARCHES:
        research = {
            "branch": "minecolonies:technology",
            "parentResearch": parent,
            "researchLevel": level,
            "sortOrder": order,
            "subtitle": f"com.voyager.research.technology.{name}.subtitle",
            "requirements": [{"type": "minecolonies:building", "building": HUT, "level": hut_level}],
            "costs": [{"count": count, "item": item} for item, count in costs],
            "effects": [{"id": f"voyager:effects/{effect}", "level": effect_level}],
            "icon": icon,
        }
        with open(f"{DATA}/technology/{name}.json", "w") as f:
            json.dump(research, f, indent=2)
        lang[f"com.voyager.research.technology.{name}.name"] = title
        lang[f"com.voyager.research.technology.{name}.subtitle"] = subtitle
    # chat lines for Long-range Comms
    lang.update({
        "com.voyager.comms.departed": "%1$s has left for the End - %2$s stops on the itinerary",
        "com.voyager.comms.fight": "%1$s beat a %2$s out there (%3$s/%4$s hp left)",
        "com.voyager.comms.lost": "%1$s was lost in the End fighting a %2$s",
        "com.voyager.comms.dragon": "%1$s: it's the dragon! Engaging!",
        "com.voyager.comms.returning": "%1$s is heading home with %2$s items in the pack",
        "com.voyager.comms.landed": "%1$s is back at the %2$s",
    })
    with open(LANG, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"{len(RESEARCHES)} researches, {len(EFFECTS)} effects, lang keys: {len(lang)}")


if __name__ == "__main__":
    main()
