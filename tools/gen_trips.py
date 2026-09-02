"""Generate the five expedition plans (crafter recipes) and their End loot tables."""
import json, os

import paths

OUT = paths.res("data", "voyager")
os.makedirs(f"{OUT}/crafterrecipes/voyager", exist_ok=True)
os.makedirs(f"{OUT}/loot_table/recipes/voyager", exist_ok=True)


def item(name, lo, hi, weight, quality=0):
    e = {"type": "minecraft:item", "name": name, "weight": weight,
         "functions": [{"function": "minecraft:set_count", "add": False,
                        "count": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)}}]}
    if quality:
        e["quality"] = quality
    return e


def token(entity, damage, xp, weight, quality=0):
    e = {"type": "minecraft:item", "name": "minecolonies:adventure_token", "weight": weight,
         "functions": [{"function": "minecraft:set_components",
                        "components": {"minecolonies:adventure": {"damage": float(damage), "entity": entity, "xp": xp}}}]}
    if quality:
        e["quality"] = quality
    return e


def empty(weight):
    return {"type": "minecraft:empty", "weight": weight}


def pool(rolls, entries, bonus=0.0):
    lo, hi = rolls
    return {"rolls": {"type": "minecraft:uniform", "min": float(lo), "max": float(hi)},
            "bonus_rolls": {"type": "minecraft:uniform", "min": bonus, "max": bonus},
            "entries": entries}


def level(lv):
    finds = [
        item("minecraft:end_stone", 4 + 2 * lv, 12 + 6 * lv, 20),
        item("minecraft:chorus_fruit", 1, 3 + lv, 14),
        item("minecraft:ender_pearl", 1, 1 + lv // 2, 8, quality=1),
    ]
    if lv >= 2:
        finds += [item("minecraft:chorus_flower", 1, 2, 6), item("minecraft:purpur_block", 2, 4 + 2 * lv, 10)]
    if lv >= 3:
        finds += [item("minecraft:end_stone_bricks", 2, 6, 8), item("minecraft:end_rod", 1, 3, 5),
                  item("minecraft:shulker_shell", 1, 1 if lv < 5 else 2, 3, quality=2)]
    if lv >= 4:
        finds += [item("minecraft:obsidian", 1, 2 + lv - 3, 6), item("minecraft:purpur_pillar", 1, 4, 5),
                  item("minecraft:dragon_breath", 1, 2, 2, quality=2)]
    fights = [
        token("minecraft:endermite", 2, 3, 40, quality=-4),
        token("minecraft:enderman", 7, 5, 30, quality=-2),
    ]
    if lv >= 3:
        fights.append(token("minecraft:shulker", 4, 5, 20, quality=-1))
    rare = [empty(100)]
    if lv >= 4:
        rare.append(item("minecraft:shulker_shell", 1, 2, 4, quality=3))
    if lv >= 5:
        rare.append(item("minecraft:elytra", 1, 1, 2, quality=1))
    pools = [
        pool((1 + lv, 3 + lv * 2 // 1), finds, 0.3),
        pool((1, 1 + lv // 2), fights, 0.1),
        pool((1, 1), rare),
    ]
    table = {"pools": pools}
    outputs = sorted({e["name"] for e in finds + rare if e.get("type") == "minecraft:item" and e["name"] != "minecolonies:adventure_token"})
    # mob drops the Voyager may bring home as well
    outputs = sorted(set(outputs) | {"minecraft:ender_pearl", "minecraft:shulker_shell"} if lv >= 3 else set(outputs) | {"minecraft:ender_pearl"})
    recipe = {
        "type": "recipe",
        "crafter": "voyager_custom",
        "inputs": [
            {"count": 64, "id": "minecraft:cobblestone"},
            {"count": 4, "id": "minecraft:ender_pearl"},
            {"count": 16, "id": "minecraft:torch"},
        ],
        "intermediate": "minecraft:air",
        "loot-table": f"voyager:recipes/voyager/trip{lv}",
        "additional-output": [{"id": o} for o in outputs],
        "min-building-level": lv,
        "max-building-level": lv,
    }
    with open(f"{OUT}/crafterrecipes/voyager/trip{lv}.json", "w") as f:
        json.dump(recipe, f, indent=2)
    with open(f"{OUT}/loot_table/recipes/voyager/trip{lv}.json", "w") as f:
        json.dump(table, f, indent=2)
    print(f"trip{lv}: {len(finds)} finds, {len(fights)} mobs, rolls {pools[0]['rolls']['min']:.0f}-{pools[0]['rolls']['max']:.0f}")


for lv in range(1, 6):
    level(lv)
