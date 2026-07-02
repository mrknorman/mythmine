"""S2: the stone family layer (STONE_PHASE.md). Generates the 27 form-group item tags, pile-icon
wrappers for every group canonical, the recipe-acceptance overrides (material-cost recipes take
the whole form family; material-identity one-offs are protected), vanilla stone-material tag
appends (stone tools + furnaces take any cobbled), and stone's two chain-completing recipes."""
import json, os, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from stone_naming import MATERIALS, forms

ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
CJ = zipfile.ZipFile(os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar"))
SJ = zipfile.ZipFile(os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-extracted_server.jar"))
VANILLA = {n.split("/")[-1][:-5] for n in CJ.namelist()
           if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")}

def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        json.dump(obj, f, indent="\t"); f.write("\n")

def member_id(name):
    return f"minecraft:{name}" if name in VANILLA else f"mythstack:{name}"

# ---- 27 form-group tags; canonical = stone's form ------------------------------------------
FORM_KEYS = list(forms(MATERIALS[0]).keys())
canonicals = {}
for form in FORM_KEYS:
    members = []
    for m in MATERIALS:
        name = forms(m)[form]
        if name:
            members.append(member_id(name))
        if m[0] == "stone" and name:
            canonicals[form] = name
    write(f"data/mythstack/tags/item/stone/{form}.json", {"values": members})
print(f"{len(FORM_KEYS)} group tags; canonicals: {len(canonicals)}")

# ---- pile-icon wrappers for every canonical -------------------------------------------------
def pile_wrap(on_false):
    return {"model": {"type": "minecraft:condition", "property": "minecraft:has_component",
                      "component": "mythstack:variant_pile",
                      "on_true": {"type": "minecraft:special", "base": "mythstack:item/pile_base",
                                  "model": {"type": "mythstack:pile"}},
                      "on_false": on_false}}
wrapped = 0
for form, name in canonicals.items():
    if name in VANILLA:
        vanilla = json.loads(CJ.read(f"assets/minecraft/items/{name}.json"))
        write(f"assets/minecraft/items/{name}.json", pile_wrap(vanilla["model"]))
    else:
        ours = json.load(open(os.path.join(ROOT, f"assets/mythstack/items/{name}.json")))
        inner = ours["model"]
        if inner.get("type") == "minecraft:condition":  # idempotent re-runs
            inner = inner["on_false"]
        write(f"assets/mythstack/items/{name}.json", pile_wrap(inner))
    wrapped += 1
print(f"{wrapped} canonical pile wrappers")

# ---- recipe acceptance: material-cost recipes take the form family --------------------------
# recipe name -> {exact ingredient id: form tag}
TAG = lambda form: f"#mythstack:stone/{form}"
OVERRIDES = {
    "comparator": {"minecraft:stone": TAG("raw")},
    "repeater": {"minecraft:stone": TAG("raw")},
    "stonecutter": {"minecraft:stone": TAG("raw")},
    "dispenser": {"minecraft:cobblestone": TAG("cobbled")},
    "dropper": {"minecraft:cobblestone": TAG("cobbled")},
    "lever": {"minecraft:cobblestone": TAG("cobbled")},
    "observer": {"minecraft:cobblestone": TAG("cobbled")},
    "armor_stand": {"minecraft:smooth_stone_slab": TAG("polished_slab")},
    "blast_furnace": {"minecraft:smooth_stone": TAG("polished")},
    "grindstone": {"minecraft:stone_slab": TAG("raw_slab")},
    "lodestone": {"minecraft:chiseled_stone_bricks": TAG("chiseled")},
    # armor-trim template duplication: the material block is a cost, keyed by form
    "coast_armor_trim_smithing_template": {"minecraft:cobblestone": TAG("cobbled")},
    "sentry_armor_trim_smithing_template": {"minecraft:cobblestone": TAG("cobbled")},
    "vex_armor_trim_smithing_template": {"minecraft:cobblestone": TAG("cobbled")},
    "silence_armor_trim_smithing_template": {"minecraft:cobbled_deepslate": TAG("cobbled")},
    "ward_armor_trim_smithing_template": {"minecraft:cobbled_deepslate": TAG("cobbled")},
    "dune_armor_trim_smithing_template": {"minecraft:sandstone": TAG("raw")},
    "eye_armor_trim_smithing_template": {"minecraft:end_stone": TAG("raw")},
    "netherite_upgrade_smithing_template": {"minecraft:netherrack": TAG("raw")},
    "rib_armor_trim_smithing_template": {"minecraft:netherrack": TAG("raw")},
    "snout_armor_trim_smithing_template": {"minecraft:blackstone": TAG("raw")},
    "spire_armor_trim_smithing_template": {"minecraft:purpur_block": TAG("raw")},
    "tide_armor_trim_smithing_template": {"minecraft:prismarine": TAG("raw")},
    "wild_armor_trim_smithing_template": {"minecraft:mossy_cobblestone": TAG("mossy_cobbled")},
}
# protected material-identity one-offs (never family-craftable): potent_sulfur, cut_sandstone,
# cut_red_sandstone, deepslate_tiles, chiseled_tuff_bricks, nether_brick_fence — no overrides.
for name, swaps in OVERRIDES.items():
    local = os.path.join(ROOT, f"data/minecraft/recipe/{name}.json")
    if os.path.exists(local):  # already overridden (e.g. grindstone's typed sticks) — layer on top
        r = json.load(open(local))
    else:
        r = json.loads(SJ.read(f"data/minecraft/recipe/{name}.json"))
    def swap(v):
        return swaps.get(v, v) if isinstance(v, str) else v
    if "key" in r:
        r["key"] = {k: swap(v) for k, v in r["key"].items()}
    if "ingredients" in r:
        r["ingredients"] = [swap(v) for v in r["ingredients"]]
    write(f"data/minecraft/recipe/{name}.json", r)
print(f"{len(OVERRIDES)} acceptance overrides")

# ---- vanilla material tags: stone tools + furnaces take any cobbled -------------------------
new_cobbled = sorted(member_id(forms(m)["cobbled"]) for m in MATERIALS
                     if forms(m)["cobbled"] and forms(m)["cobbled"] not in VANILLA)
for tag in ("stone_crafting_materials", "stone_tool_materials"):
    write(f"data/minecraft/tags/item/{tag}.json", {"values": new_cobbled})
print(f"{len(new_cobbled)} cobbled into stone tool/crafting materials")

# ---- stone's chain-completing recipes (canonical normalization needs the full chain) ---------
write("data/mythstack/recipe/stone_bricks_from_smooth_stone.json",
      {"type": "minecraft:crafting_shaped", "key": {"#": "minecraft:smooth_stone"},
       "pattern": ["##", "##"], "result": {"id": "minecraft:stone_bricks", "count": 4}})
write("data/mythstack/recipe/chiseled_stone_bricks_from_stone_slab.json",
      {"type": "minecraft:crafting_shaped", "key": {"#": "minecraft:stone_slab"},
       "pattern": ["#", "#"], "result": {"id": "minecraft:chiseled_stone_bricks"}})
print("chain recipes done")
