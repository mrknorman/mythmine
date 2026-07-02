"""Inject fabric:load_conditions (mythstack:module) into generated data so switched-off modules
leave no obtainability behind. Classification by path + content:
  sawing recipes -> sawmill (+blocks when the cut outputs a blocks-module item)
  sawmill recipe, carpenter trades/sets/tags -> sawmill (cross-module trades also +blocks)
  ore-variant smelting/blasting -> terrain; worldgen + noise override + loot normalization -> terrain
  leaves loot (typed sticks) -> typed_sticks
  the vanilla-furnace pin -> blocks (off => vanilla recipe returns)
  all other mythstack recipes (kit chains, stations, stonecutting, mossy) -> blocks
Registration is never gated (world safety) — tags/loot/blockstates stay unconditional. Idempotent."""
import json, glob, os, sys
ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
MATERIAL_ORES = ("granite_", "diorite_", "andesite_", "calcite_", "tuff_")

def condition(*modules):
    return [{"condition": "mythstack:module", "module": m} for m in modules]

def tag_file(path, *modules):
    obj = json.load(open(path))
    obj["fabric:load_conditions"] = condition(*modules)
    json.dump(obj, open(path, "w"), indent="\t"); open(path, "a").write("\n")

count = 0
for f in glob.glob(f"{ROOT}/data/**/*.json", recursive=True):
    if "/resourcepacks/" in f.replace("\\", "/"):
        continue
    rel = os.path.relpath(f, ROOT).replace("\\", "/")
    obj = json.load(open(f))
    obj.pop("fabric:load_conditions", None)
    modules = None
    name = os.path.basename(f)[:-5]

    if "/recipe/sawing/" in rel:
        result = obj.get("result", {}).get("id", "")
        cross = result.startswith("mythstack:") and result != "mythstack:sawmill"
        modules = ["sawmill", "blocks"] if cross else ["sawmill"]
    elif rel == "data/mythstack/recipe/sawmill.json":
        modules = ["sawmill"]
    elif "/villager_trade/" in rel or "/trade_set/" in rel:
        gives = json.dumps(obj.get("gives", {}))
        cross = "mythstack:" in gives
        modules = ["sawmill", "blocks"] if cross else ["sawmill"]
    elif "/worldgen/" in rel or "/noise_settings/" in rel:
        modules = ["terrain"]
    elif rel.startswith("data/mythstack/recipe/") and obj.get("type", "").startswith("minecraft:"):
        if any(m in name for m in MATERIAL_ORES) and name.endswith(("_smelting", "_blasting")) \
                or "_ore" in name and ("smelting" in name or "blasting" in name):
            modules = ["terrain"]
        else:
            modules = ["blocks"]

    if modules:
        obj["fabric:load_conditions"] = condition(*modules)
        json.dump(obj, open(f, "w"), indent="\t"); open(f, "a").write("\n")
        count += 1
print(f"conditions injected into {count} data files")
