"""Geology overhaul (worldgen phase): regional base stones via surface-rule noise bands.
Overrides minecraft:overworld noise settings — the sequence becomes:
  bedrock | vanilla surface bands | sulfur caves | ICE band | SAND bands | TUFF regions |
  deepslate | STONE REGIONS (granite/diorite/andesite/calcite; shale = fallthrough default)
Region rules sit AFTER deepslate so they only claim what would have been plain stone."""
import json, os, sys, zipfile
ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
SJ = zipfile.ZipFile(os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-extracted_server.jar"))

def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        json.dump(obj, f, indent="\t"); f.write("\n")

# region noises: low frequency = few-hundred-block regions
write("data/mythstack/worldgen/noise/stone_regions.json", {"firstOctave": -10, "amplitudes": [1.0, 0.5]})
write("data/mythstack/worldgen/noise/deep_regions.json", {"firstOctave": -9, "amplitudes": [1.0, 0.5]})

def block(name):
    return {"type": "minecraft:block", "result_state": {"Name": name}}
def cond(if_true, then_run):
    return {"type": "minecraft:condition", "if_true": if_true, "then_run": then_run}
def seq(*rules):
    return {"type": "minecraft:sequence", "sequence": list(rules)}
def biome(*biomes):
    return {"type": "minecraft:biome", "biome_is": list(biomes)}
def noise(nid, lo, hi):
    return {"type": "minecraft:noise_threshold", "noise": nid, "min_threshold": lo, "max_threshold": hi}
def y_above(y):
    return {"type": "minecraft:y_above", "anchor": {"absolute": y},
            "surface_depth_multiplier": 0, "add_stone_depth": False}
def gradient(name, lo, hi):
    return {"type": "minecraft:vertical_gradient", "random_name": name,
            "true_at_and_below": {"absolute": lo}, "false_at_and_above": {"absolute": hi}}
def stone_depth(offset, add_surface, secondary, surface):
    return {"type": "minecraft:stone_depth", "offset": offset, "add_surface_depth": add_surface,
            "secondary_depth_range": secondary, "surface_type": surface}

HUGE = 1.7976931348623157e+308

# ICE band: tundra proper — permafrost under the active soil layer, packed ice to ~y32.
ice = cond(biome("minecraft:snowy_plains", "minecraft:ice_spikes", "minecraft:snowy_taiga"),
           seq(cond(stone_depth(8, True, 0, "floor"), block("mythstack:permafrost")),
               cond(y_above(32), block("minecraft:packed_ice"))))
# SAND bands: sandstone caves under deserts, red under badlands — down to ~y32, no deeper.
sand = cond(biome("minecraft:desert"),
            cond(y_above(32), block("minecraft:sandstone")))
red_sand = cond(biome("minecraft:badlands", "minecraft:eroded_badlands", "minecraft:wooded_badlands"),
                cond(y_above(32), block("minecraft:red_sandstone")))
# TUFF regions share the deepslate band (before the deepslate rule, own dithered gradient).
tuff = cond(noise("mythstack:deep_regions", 0.25, HUGE),
            cond(gradient("mythstack:tuff", 0, 8), block("minecraft:tuff")))
# STONE regions: equal-ish noise bands; shale (stone) is the fallthrough default_block.
regions = seq(
    cond(noise("mythstack:stone_regions", -HUGE, -0.30), block("minecraft:granite")),
    cond(noise("mythstack:stone_regions", -0.30, -0.10), block("minecraft:diorite")),
    cond(noise("mythstack:stone_regions", 0.10, 0.30), block("minecraft:andesite")),
    cond(noise("mythstack:stone_regions", 0.30, HUGE), block("minecraft:calcite")))

ns = json.loads(SJ.read("data/minecraft/worldgen/noise_settings/overworld.json"))
sr = ns["surface_rule"]["sequence"]
assert len(sr) == 4, f"vanilla surface rule shape changed: {len(sr)} rules"
# [bedrock, surface, sulfur] + ours + [deepslate] + regions
ns["surface_rule"]["sequence"] = sr[:3] + [ice, sand, red_sand, tuff] + [sr[3]] + [regions]
write("resourcepacks/geology/data/minecraft/worldgen/noise_settings/overworld.json", ns)
print("noise settings overridden; region noises registered")
