"""Generate the Observatory's own book of studies.

Not MineColonies research: these are the Observatory's, paid for in nights of watching rather
than in hours of a timer, read by colony/SkyStudyModule.java out of
data/voyager/sky_study/<name>.json. See docs/OBSERVATORY-PLAN.md 14.

Fields: branch (which of the four columns of work it belongs to), sortOrder, parent, tier (the
Observatory level it needs), nights (what it costs), costs (items, out of the player's pockets),
effect + level (what the rest of the mod then reads through ObservatoryResearch), icon.
"""
import json
import os

import paths

DATA = paths.res("data", "voyager", "sky_study")
LANG = paths.res("assets", "voyager", "lang", "en_us.json")

BRANCHES = {
    "optics": "Optics",
    "almanac": "The Almanac",
    "darkroom": "The Darkroom",
    "watch": "The Watch",
}

# name, branch, sortOrder, parent, tier, nights, costs, (effect, level), icon, title, subtitle
STUDIES = [
    # ---- Optics: how deep the colony can see
    ("lens_grinding", "optics", 1, None, 2, 4,
     [("exposure_space:normal_telescopic_lens", 1), ("minecraft:amethyst_block", 4)],
     ("lens_grinding", 1.0), "minecraft:spyglass",
     "Lens Grinding", "Patience, sand, and a very steady hand"),
    ("lens_grinding_ii", "optics", 2, "lens_grinding", 4, 8,
     [("exposure_space:good_telescopic_lens", 1), ("minecraft:amethyst_block", 12)],
     ("lens_grinding", 2.0), "minecraft:tinted_glass",
     "Lens Grinding II", "The sky gets deeper the longer you polish"),
    ("deep_field", "optics", 3, "lens_grinding_ii", 5, 12,
     [("exposure_space:sculk_telescopic_lens", 1), ("minecraft:echo_shard", 8),
      ("minecraft:amethyst_block", 8)],
     ("deep_field", 1.0), "minecraft:echo_shard",
     "Deep Field", "Leave the plate open long enough and the dark fills in"),

    # ---- The Almanac: knowing what the sky will do
    ("star_party", "almanac", 1, None, 3, 5,
     [("minecraft:glowstone", 16), ("minecraft:gold_ingot", 8), ("minecraft:cake", 2)],
     ("star_party", 1.0), "minecraft:firework_star",
     "Star Party", "The whole town stays up, and nobody minds in the morning"),
    ("star_charts", "almanac", 2, "star_party", 3, 6,
     [("minecraft:paper", 64), ("minecraft:ink_sac", 16), ("minecraft:map", 1)],
     ("star_charts", 0.2), "minecraft:map",
     "Star Charts", "The shortest way across is drawn, not guessed"),
    ("ephemeris", "almanac", 3, "star_party", 4, 7,
     [("minecraft:ender_eye", 4), ("minecraft:paper", 32), ("minecraft:clock", 1)],
     ("ephemeris", 1.0), "minecraft:clock",
     "Ephemeris", "The sky, printed a day in advance"),
    ("launch_window", "almanac", 4, "ephemeris", 5, 10,
     [("minecraft:end_rod", 8), ("minecraft:ender_eye", 8), ("minecraft:clock", 2)],
     ("launch_window", 1.0), "minecraft:compass",
     "Launch Window", "The almanac says go tonight"),

    # ---- The Darkroom: what the colony does with a plate
    ("darkroom_discipline", "darkroom", 1, None, 3, 4,
     [("minecraft:redstone", 32), ("minecraft:copper_block", 4), ("minecraft:tinted_glass", 8)],
     ("darkroom_discipline", 0.35), "minecraft:tinted_glass",
     "Darkroom Discipline", "Mind the light, and the light minds you"),
    ("second_exposure", "darkroom", 2, "darkroom_discipline", 4, 6,
     [("minecraft:amethyst_shard", 16), ("minecraft:glass_pane", 32)],
     ("second_exposure", 1.0), "minecraft:glass_pane",
     "Second Exposure", "The same star, twice, is still worth looking at"),
    ("comparative_astronomy", "darkroom", 3, "second_exposure", 5, 10,
     [("exposure_space:excellent_telescopic_lens", 1), ("minecraft:amethyst_block", 16)],
     ("comparative_astronomy", 1.0), "minecraft:amethyst_cluster",
     "Comparative Astronomy", "Two nights of the same sky say more than one"),

    # ---- The Watch: who keeps it
    ("apprentice", "watch", 1, None, 4, 6,
     [("minecraft:book", 16), ("minecraft:lantern", 8)],
     ("apprentice", 1.0), "minecraft:lantern",
     "Apprentice Astronomer", "Somebody has to take the second watch"),
]

# What each effect does, in the tooltip's words.
EFFECT_TEXT = {
    "lens_grinding": "The Observatory's lens is better than its level alone",
    "star_party": "The colony stays up on an event night - and nobody is the worse for it",
    "star_charts": "Voyager expeditions take less time, on top of Rapid Refit",
    "ephemeris": "Tomorrow night's event is announced a day early",
    "launch_window": "One more expedition per launch window",
    "darkroom_discipline": "Plates develop faster",
    "second_exposure": "A plate of an object the colony already has is still worth something",
    "comparative_astronomy": "Two plates of one object combine into a better print",
    "deep_field": "Every Voyager expedition brings back one find more",
    "apprentice": "One more astronomer per Observatory",
}


def main():
    os.makedirs(DATA, exist_ok=True)
    for stale in os.listdir(DATA):
        if stale.endswith(".json"):
            os.remove(os.path.join(DATA, stale))
    with open(LANG) as f:
        lang = json.load(f)

    for (name, branch, order, parent, tier, nights, costs,
         (effect, level), icon, title, subtitle) in STUDIES:
        study = {
            "branch": branch,
            "sortOrder": order,
            "tier": tier,
            "nights": nights,
            "costs": [{"item": item, "count": count} for item, count in costs],
            "effect": effect,
            "level": level,
            "icon": icon,
        }
        if parent:
            study["parent"] = f"voyager:{parent}"
        with open(f"{DATA}/{name}.json", "w") as f:
            json.dump(study, f, indent=2)
        lang[f"com.voyager.study.{name}.name"] = title
        lang[f"com.voyager.study.{name}.subtitle"] = subtitle

    for key, label in BRANCHES.items():
        lang[f"com.voyager.study.branch.{key}"] = label
    for effect, text in EFFECT_TEXT.items():
        lang[f"com.voyager.study.effect.{effect}"] = text

    lang.update({
        "com.voyager.gui.study.tab": "Sky Studies",
        "com.voyager.gui.study.title": "The Observatory's Studies",
        "com.voyager.gui.study.idle": "%1$s of %2$s studied - pick the next",
        "com.voyager.gui.study.working": "%1$s: %2$s of %3$s nights",
        "com.voyager.gui.study.nights": "%1$s of %2$s nights watched",
        "com.voyager.gui.study.cost": "%1$s nights",
        "com.voyager.gui.study.begun": "The astronomer takes up %1$s",
        "com.voyager.study.begun": "The Observatory has begun its study of %1$s",
        "com.voyager.gui.study.empty": "No studies in any datapack",
        "com.voyager.study.finished": "The Observatory has finished its study of %1$s",
        "com.voyager.study.status.done": "Studied",
        "com.voyager.study.status.in_progress": "Being studied",
        "com.voyager.study.status.available": "Ready to begin",
        "com.voyager.study.status.needs_level": "Needs an Observatory of level %1$s",
        "com.voyager.study.status.needs_parent": "Needs %1$s first",
        "com.voyager.study.status.needs_parent.unknown": "an earlier study",
        "com.voyager.study.status.busy": "The astronomer is already on something",
        "com.voyager.study.status.gathering": "Gathering what it costs",
        "com.voyager.gui.study.gathering": "Fetching what %1$s costs - the couriers are on it",
        "com.voyager.study.status.unaffordable": "The Observatory has not got what this study costs",
    })
    with open(LANG, "w") as f:
        json.dump(lang, f, indent=2, ensure_ascii=False)
        f.write("\n")
    print(f"{len(STUDIES)} studies, {len(BRANCHES)} branches, lang keys: {len(lang)}")


if __name__ == "__main__":
    main()
