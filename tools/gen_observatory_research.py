"""The one MineColonies research the Observatory still needs: the unlock.

Everything else the Observatory can learn is in its own book now - see gen_sky_studies.py and
docs/OBSERVATORY-PLAN.md 14. A hut still has to be unlocked somewhere the player already looks,
though, and that is the University's Technology tree, so this single research stays there.

Files: data/voyager/researches/technology/observatory.json and researches/effects/blockhutobservatory.json.
"""
import glob
import json
import os

import paths

DATA = paths.res("data", "voyager", "researches")
LANG = paths.res("assets", "voyager", "lang", "en_us.json")

# Researches that used to live in the University's tree and are now sky studies. Removed here so
# an old checkout does not leave them behind in a jar.
MOVED = ("lens_grinding", "lens_grinding2", "star_party", "ephemeris", "darkroom_discipline",
         "second_exposure", "comparative_astronomy", "apprentice", "star_charts", "deep_field",
         "launch_window")

UNLOCK = {
    "branch": "minecolonies:technology",
    "parentResearch": "minecolonies:technology/memoryaid",
    "researchLevel": 3,
    "sortOrder": 6,
    "subtitle": "com.voyager.research.technology.observatory.subtitle",
    "costs": [
        {"count": 32, "item": "minecraft:glass"},
        {"count": 8, "item": "minecraft:amethyst_shard"},
        {"count": 1, "item": "minecraft:spyglass"},
    ],
    "effects": [{"id": "voyager:effects/blockhutobservatory", "level": 1}],
    "icon": "voyager:blockhutobservatory",
    "requirements": [{"type": "minecolonies:building", "building": "minecolonies:library", "level": 3}],
}


def main():
    os.makedirs(f"{DATA}/technology", exist_ok=True)
    os.makedirs(f"{DATA}/effects", exist_ok=True)
    for name in MOVED:
        for stale in (f"{DATA}/technology/{name}.json", f"{DATA}/effects/{name}.json"):
            if os.path.exists(stale):
                os.remove(stale)
                print("removed", os.path.relpath(stale, DATA))

    with open(f"{DATA}/effects/blockhutobservatory.json", "w") as f:
        json.dump({"effect": True, "levels": [1.0]}, f, indent=2)
    with open(f"{DATA}/technology/observatory.json", "w") as f:
        json.dump(UNLOCK, f, indent=2)

    with open(LANG) as f:
        lang = json.load(f)
    for name in MOVED:
        lang.pop(f"com.voyager.research.technology.{name}.name", None)
        lang.pop(f"com.voyager.research.technology.{name}.subtitle", None)
        lang.pop(f"com.voyager.research.effects.{name}.description", None)
    lang["com.voyager.research.technology.observatory.name"] = "The Long Night"
    lang["com.voyager.research.technology.observatory.subtitle"] = "Somebody has to stay up and write it down"
    lang["com.voyager.research.effects.blockhutobservatory.description"] = "Unlocks the Observatory"
    lang.update({
        "com.voyager.sky.event_tonight": "The astronomer says tonight is %1$s",
        "com.voyager.sky.event_tomorrow": "The astronomer says tomorrow night is %1$s",
        "com.voyager.sky.caught": "%1$s caught %2$s tonight (%3$s)",
        "com.voyager.sky.blank": "%1$s could not resolve anything tonight - the colony needs a better lens",
        "com.voyager.sky.combined": "%1$s put two nights of %2$s together - %3$s",
        "com.voyager.sky.reward": "Studying %1$s paid the colony %2$s",
        "com.voyager.plate.mark.first": "First of its kind in this colony",
        "com.voyager.plate.mark.duplicate": "Another of the same",
        "com.voyager.plate.mark.composite": "Composite of two nights",
        "com.voyager.observatory.shut": "No cosmic objects in any datapack - the astronomer keeps the watch, but every plate will come home blank",
    })
    with open(LANG, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print("1 research (the unlock), 1 effect; the rest are sky studies now")


if __name__ == "__main__":
    main()
