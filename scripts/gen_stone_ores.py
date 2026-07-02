"""Per-material ore variants: textures (ore-overlay transfer), assets, loot, smelting/blasting,
tags (tool tiers + vanilla ore families), ore-feature target overrides, blue ice vein, and the
shale rename cascade."""
import json, os, sys, zipfile
ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
from pnglib import *  # png codec + shared helpers
CJ = zipfile.ZipFile(os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar"))
SJ = zipfile.ZipFile(os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-extracted_server.jar"))

def write(path, obj):
    if isinstance(obj, dict) and isinstance(obj.get("values"), list):
        obj = {**obj, "values": list(dict.fromkeys(obj["values"]))}  # tags stay idempotent
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        json.dump(obj, f, indent="\t"); f.write("\n")

def vtex(name):
    _, _, px = png_decode(CJ.read(f"assets/minecraft/textures/block/{name}.png"))
    return px

MATERIALS = ["granite", "diorite", "andesite", "calcite", "tuff"]
ORES = ["coal_ore", "iron_ore", "copper_ore", "gold_ore", "redstone_ore",
        "emerald_ore", "lapis_ore", "diamond_ore"]
NEEDS_IRON = {"gold_ore", "redstone_ore", "emerald_ore", "diamond_ore"}
NEEDS_STONE = {"iron_ore", "copper_ore", "lapis_ore"}

# --- textures: the ore OVERLAY is the pixel-diff between <ore> and stone -----------------------
stone = vtex("stone")
made_tex = 0
lang = json.load(open(os.path.join(ROOT, "assets/mythstack/lang/en_us.json")))
pickaxe = json.load(open(os.path.join(ROOT, "data/minecraft/tags/block/mineable/pickaxe.json")))
pickaxe["values"] = list(dict.fromkeys(pickaxe["values"]))
tool_tags = {"needs_stone_tool": [], "needs_iron_tool": []}
family_appends = {}
for ore in ORES:
    overlay = vtex(ore)
    mask = [sum(abs(a - b) for a, b in zip(p[:3], q[:3])) > 30 for p, q in zip(overlay, stone)]
    loot_tpl = SJ.read(f"data/minecraft/loot_table/blocks/{ore}.json").decode()
    for m in MATERIALS:
        name = f"{m}_{ore}"
        base = vtex(m)
        out = [overlay[i] if mask[i] else base[i] for i in range(len(base))]
        dest = os.path.join(ROOT, f"assets/mythstack/textures/block/{name}.png")
        open(dest, "wb").write(png_encode(16, 16, out))
        made_tex += 1
        write(f"assets/mythstack/blockstates/{name}.json",
              {"variants": {"": {"model": f"mythstack:block/{name}"}}})
        write(f"assets/mythstack/models/block/{name}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"mythstack:block/{name}"}})
        write(f"assets/mythstack/items/{name}.json",
              {"model": {"type": "minecraft:model", "model": f"mythstack:block/{name}"}})
        write(f"data/mythstack/loot_table/blocks/{name}.json",
              json.loads(loot_tpl.replace(f"minecraft:{ore}", f"mythstack:{name}")))
        lang[f"block.mythstack.{name}"] = " ".join(w.capitalize() for w in name.split("_"))
        pickaxe["values"].append(f"mythstack:{name}")
        if ore in NEEDS_IRON:
            tool_tags["needs_iron_tool"].append(f"mythstack:{name}")
        if ore in NEEDS_STONE:
            tool_tags["needs_stone_tool"].append(f"mythstack:{name}")
        family_appends.setdefault(f"{ore}s", []).append(f"mythstack:{name}")

# smelting + blasting per variant (copy the vanilla recipe for THAT ore, swap the ingredient)
recipes = 0
for n in SJ.namelist():
    base = n.split("/")[-1][:-5] if n.endswith(".json") else ""
    if not n.startswith("data/minecraft/recipe/") or "_from_" not in base:
        continue
    r = json.loads(SJ.read(n))
    if r.get("type") not in ("minecraft:smelting", "minecraft:blasting"):
        continue
    ing = r.get("ingredient")
    if not isinstance(ing, str) or not ing.startswith("minecraft:") or ing.removeprefix("minecraft:") not in ORES:
        continue
    ore = ing.removeprefix("minecraft:")
    for m in MATERIALS:
        rr = dict(r)
        rr["ingredient"] = f"mythstack:{m}_{ore}"
        write(f"data/mythstack/recipe/{base.replace(ore, m + '_' + ore)}.json", rr)
        recipes += 1

# tags: tool tiers + vanilla ore family tags (block + item — groups grow via these)
for tag, values in tool_tags.items():
    write(f"data/minecraft/tags/block/{tag}.json", {"values": values})
for fam, values in family_appends.items():
    for reg in ("block", "item"):
        write(f"data/minecraft/tags/{reg}/{fam}.json", {"values": values})
write("data/minecraft/tags/block/mineable/pickaxe.json", pickaxe)

# --- ore-feature targets: veins in a region place the region's ore variant ---------------------
features = 0
for n in SJ.namelist():
    if not n.startswith("data/minecraft/worldgen/configured_feature/") or not n.endswith(".json"):
        continue
    f = json.loads(SJ.read(n))
    if f.get("type") != "minecraft:ore":
        continue
    targets = f.get("config", {}).get("targets", [])
    ore = None
    for t in targets:
        state = t.get("state", {}).get("Name", "")
        if state.removeprefix("minecraft:") in ORES:
            ore = state.removeprefix("minecraft:")
    if not ore:
        continue
    new_targets = [{"target": {"predicate_type": "minecraft:block_match", "block": f"minecraft:{m}"},
                    "state": {"Name": f"mythstack:{m}_{ore}"}} for m in MATERIALS]
    f["config"]["targets"] = new_targets + targets
    write(f"data/minecraft/worldgen/configured_feature/{n.split('/')[-1]}", f)
    features += 1

# --- blue ice veins in the tundra ice band ------------------------------------------------------
write("data/mythstack/worldgen/configured_feature/blue_ice_vein.json",
      {"type": "minecraft:ore",
       "config": {"discard_chance_on_air_exposure": 0.0, "size": 10,
                  "targets": [{"target": {"predicate_type": "minecraft:block_match",
                                          "block": "minecraft:packed_ice"},
                               "state": {"Name": "minecraft:blue_ice"}}]}})
write("data/mythstack/worldgen/placed_feature/blue_ice_vein.json",
      {"feature": "mythstack:blue_ice_vein",
       "placement": [{"type": "minecraft:count", "count": 4},
                     {"type": "minecraft:in_square"},
                     {"type": "minecraft:height_range",
                      "height": {"type": "minecraft:uniform",
                                 "min_inclusive": {"absolute": 34}, "max_inclusive": {"absolute": 80}}},
                     {"type": "minecraft:biome"}]})

# --- the shale rename cascade (lang only; ids untouched) ---------------------------------------
mlp = os.path.join(ROOT, "assets/minecraft/lang/en_us.json")
mlang = json.load(open(mlp))
RENAMES = {
    "stone": "Shale", "stone_stairs": "Shale Stairs", "stone_slab": "Shale Slab",
    "cobblestone": "Cobbled Shale", "cobblestone_stairs": "Cobbled Shale Stairs",
    "cobblestone_slab": "Cobbled Shale Slab", "cobblestone_wall": "Cobbled Shale Wall",
    "mossy_cobblestone": "Mossy Cobbled Shale", "mossy_cobblestone_stairs": "Mossy Cobbled Shale Stairs",
    "mossy_cobblestone_slab": "Mossy Cobbled Shale Slab", "mossy_cobblestone_wall": "Mossy Cobbled Shale Wall",
    "smooth_stone": "Smooth Shale", "smooth_stone_slab": "Smooth Shale Slab",
    "stone_bricks": "Shale Bricks", "stone_brick_stairs": "Shale Brick Stairs",
    "stone_brick_slab": "Shale Brick Slab", "stone_brick_wall": "Shale Brick Wall",
    "cracked_stone_bricks": "Cracked Shale Bricks", "chiseled_stone_bricks": "Chiseled Shale Bricks",
    "mossy_stone_bricks": "Mossy Shale Bricks", "mossy_stone_brick_stairs": "Mossy Shale Brick Stairs",
    "mossy_stone_brick_slab": "Mossy Shale Brick Slab", "mossy_stone_brick_wall": "Mossy Shale Brick Wall",
    "stone_button": "Shale Button", "stone_pressure_plate": "Shale Pressure Plate",
    "infested_stone": "Infested Shale", "infested_cobblestone": "Infested Cobbled Shale",
    "infested_stone_bricks": "Infested Shale Bricks",
    "infested_mossy_stone_bricks": "Infested Mossy Shale Bricks",
    "infested_cracked_stone_bricks": "Infested Cracked Shale Bricks",
    "infested_chiseled_stone_bricks": "Infested Chiseled Shale Bricks",
}
for bid, label in RENAMES.items():
    mlang[f"block.minecraft.{bid}"] = label
json.dump(dict(sorted(mlang.items())), open(mlp, "w"), indent="\t"); open(mlp, "a").write("\n")
# our stone-material blocks follow the cascade
lang["block.mythstack.stone_wall"] = "Shale Wall"
lang["block.mythstack.stone_pillar"] = "Shale Pillar"
lang["block.mythstack.smooth_stone_stairs"] = "Smooth Shale Stairs"
lang["block.mythstack.smooth_stone_wall"] = "Smooth Shale Wall"
lp = os.path.join(ROOT, "assets/mythstack/lang/en_us.json")
json.dump(dict(sorted(lang.items())), open(lp, "w"), indent="\t"); open(lp, "a").write("\n")
print(f"ores: {made_tex} textures, {recipes} smelt/blast recipes, {features} feature overrides, "
      f"{len(RENAMES)} shale renames, blue ice vein done")
