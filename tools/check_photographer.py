"""The Photographer may only learn photography - and must still be able to learn all of it.

The Photo Booth's crafting module is MineColonies' *general* crafter, which accepts any recipe a
player teaches it; left open, the photographer crafts planks and stone bricks and quietly does the
Sawmill's and the Stonemason's job. BuildingPhotoBooth narrows it to one item tag, and that is the
whole gate - so the tag and the shipped recipes have to agree, in both directions:

  1. every recipe we ship for the photographer makes something the tag allows, or he silently
     cannot craft his own hut's recipe;
  2. the tag lets nothing wooden or stone through, or the gate is no gate at all;
  3. the module really does test the tag, and really does test the *product*, not the ingredients
     (a photograph frame is sticks and glass - judged by inputs it goes back to the Sawmill).

Run from tools/.
"""
import json
import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TAG = ROOT / "resources/data/voyager/tags/item/photographer_product.json"
RECIPES = ROOT / "resources/resourcepacks/exposure/data/voyager/crafterrecipes/photographer"
MODULE = ROOT / "src/me/lovkar/voyager/colony/BuildingPhotoBooth.java"

# a short list of things the Sawmill and the Stonemason own; if any of them passes the tag the
# gate is pointless
NOT_HIS = [
    "minecraft:oak_planks", "minecraft:stick", "minecraft:stone_bricks",
    "minecraft:cobblestone_wall", "minecraft:stone_brick_wall", "minecraft:oak_stairs",
    "minecraft:glass", "minecraft:chest",
]

fails = []


def tag_entries():
    data = json.loads(TAG.read_text(encoding="utf-8"))
    out = []
    for v in data["values"]:
        out.append(v["id"] if isinstance(v, dict) else v)
    return out


def main():
    if not TAG.exists():
        fails.append(f"the tag is missing: {TAG.relative_to(ROOT)}")
        return
    allowed = tag_entries()

    # every entry optional: Exposure is not a hard dependency, and a required entry that is absent
    # makes the whole tag fail to load - which would leave the photographer unable to craft at all
    data = json.loads(TAG.read_text(encoding="utf-8"))
    for v in data["values"]:
        if not isinstance(v, dict) or v.get("required") is not False:
            fails.append(f"tag entry {v!r} is not optional - without Exposure the tag breaks")

    # 1. every shipped recipe's product is allowed
    if not RECIPES.is_dir():
        fails.append(f"no photographer recipes at {RECIPES.relative_to(ROOT)}")
    else:
        seen = 0
        for f in sorted(RECIPES.glob("*.json")):
            made = json.loads(f.read_text(encoding="utf-8"))["result"]["id"]
            seen += 1
            if made not in allowed:
                fails.append(f"{f.name} makes {made}, which the tag does not allow - "
                             f"the photographer could not learn his own recipe")
        if seen == 0:
            fails.append("no recipe files found to check")

    # 2. nothing that belongs to another crafter is in the tag
    for item in NOT_HIS:
        if item in allowed:
            fails.append(f"{item} is in the tag - that belongs to the Sawmill or the Stonemason")

    # 3. the module actually gates on the tag, on the product
    src = MODULE.read_text(encoding="utf-8")
    body = re.search(r"public boolean isRecipeCompatible\((.|\n)*?\n        \}", src)
    if body is None:
        fails.append("BuildingPhotoBooth.CraftingModule does not override isRecipeCompatible - "
                     "the photographer is a general crafter again")
    else:
        text = body.group(0)
        if "PHOTOGRAPHER_PRODUCT" not in text:
            fails.append("isRecipeCompatible does not test PHOTOGRAPHER_PRODUCT")
        if "getPrimaryOutput" not in text:
            fails.append("isRecipeCompatible does not test the product - "
                         "judged by its inputs a photograph frame belongs to the Sawmill")
        if "super.isRecipeCompatible" not in text:
            fails.append("isRecipeCompatible skips super - the base checks are still needed")


main()
if fails:
    print("check_photographer: FAIL")
    for f in fails:
        print("  -", f)
    sys.exit(1)
print("check_photographer: ok - every shipped recipe is allowed, and nothing else is")
