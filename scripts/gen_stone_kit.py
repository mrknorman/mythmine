"""S1a assets + data for the stone kit (STONE_PHASE.md). Derives the same gap set as
StoneKit.java (via stone_naming.py) and generates blockstates/models/items/loot/recipes/tags/lang.
Recipe chains follow vanilla's deepslate/tuff convention: cobbled -> raw (smelt), raw -> polished
(2x2), polished -> bricks (2x2), bricks -> cracked (smelt); chiseled = 2 raw slabs; pillar = 2 raw.
Stonecutting gets full parity minus cuts vanilla already has."""
import json, os, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from stone_naming import MATERIALS, lines, stem, is_mossy

ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
CJ = zipfile.ZipFile(os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar"))
SJ = zipfile.ZipFile(os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-extracted_server.jar"))
VANILLA = {n.split("/")[-1][:-5] for n in CJ.namelist()
           if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")}
VTEX = {n.split("/")[-1][:-4] for n in CJ.namelist()
        if n.startswith("assets/minecraft/textures/block/") and n.endswith(".png")}

def write(path, obj):
    full = os.path.join(ROOT, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w") as f:
        json.dump(obj, f, indent="\t"); f.write("\n")

def vjson(path):
    return json.loads(CJ.read(path))

# vanilla full-cube base -> its representative texture, where the name differs
TEXMAP = {"basalt": "basalt_side", "polished_basalt": "polished_basalt_side",
          "smooth_sandstone": "sandstone_top", "smooth_red_sandstone": "red_sandstone_top",
          "smooth_stone": "smooth_stone", "sandstone": "sandstone", "red_sandstone": "red_sandstone"}

def tex_ref(base):
    if base not in VANILLA:  # ours — generated texture shares the block name
        return f"mythstack:block/{base}"
    t = TEXMAP.get(base, base)
    assert t in VTEX, f"missing vanilla texture for {base} -> {t}"
    return f"minecraft:block/{t}"

def model_ref(base):
    return (f"mythstack:block/{base}" if base not in VANILLA else f"minecraft:block/{base}")

def item_id(name):
    return f"minecraft:{name}" if name in VANILLA else f"mythstack:{name}"

# templates
STAIRS_BS = CJ.read("assets/minecraft/blockstates/granite_stairs.json").decode()
SLAB_BS = CJ.read("assets/minecraft/blockstates/granite_slab.json").decode()
WALL_BS = CJ.read("assets/minecraft/blockstates/granite_wall.json").decode()
PILLAR_BS = CJ.read("assets/minecraft/blockstates/quartz_pillar.json").decode()
LOOT_SIMPLE = SJ.read("data/minecraft/loot_table/blocks/granite.json").decode()
LOOT_SLAB = SJ.read("data/minecraft/loot_table/blocks/granite_slab.json").decode()
LOOT_SILK = SJ.read("data/minecraft/loot_table/blocks/stone.json").decode()

# vanilla stonecutting (input,result) pairs to avoid duplicating
vanilla_cuts = set()
for n in SJ.namelist():
    if n.startswith("data/minecraft/recipe/") and "stonecutting" in n:
        r = json.loads(SJ.read(n))
        if isinstance(r.get("ingredient"), str) and isinstance(r.get("result"), dict):
            vanilla_cuts.add((r["ingredient"], r["result"]["id"]))

counts = {"bs": 0, "model": 0, "item": 0, "loot": 0, "recipe": 0}
lang = json.load(open(os.path.join(ROOT, "assets/mythstack/lang/en_us.json")))
axe_path = "data/minecraft/tags/block/mineable/pickaxe.json"
try:
    pickaxe = json.load(open(os.path.join(ROOT, axe_path)))
except FileNotFoundError:
    pickaxe = {"values": []}
walls_tag, stairs_tag, slabs_tag = [], [], []

def emit_block(name, kind, base=None, pillar_top=None):
    """blockstate + models + item def + loot + lang for one added block."""
    tex = tex_ref(base) if base else f"mythstack:block/{name}"
    if kind == "cube":
        write(f"assets/mythstack/blockstates/{name}.json",
              {"variants": {"": {"model": f"mythstack:block/{name}"}}})
        write(f"assets/mythstack/models/block/{name}.json",
              {"parent": "minecraft:block/cube_all", "textures": {"all": f"mythstack:block/{name}"}})
        item_model = f"mythstack:block/{name}"
        counts["model"] += 1
    elif kind == "stairs":
        bs = STAIRS_BS.replace("minecraft:block/granite_stairs", f"mythstack:block/{name}")
        write(f"assets/mythstack/blockstates/{name}.json", json.loads(bs))
        for suffix, parent in [("", "stairs"), ("_inner", "inner_stairs"), ("_outer", "outer_stairs")]:
            write(f"assets/mythstack/models/block/{name}{suffix}.json",
                  {"parent": f"minecraft:block/{parent}",
                   "textures": {"bottom": tex, "top": tex, "side": tex}})
            counts["model"] += 1
        item_model = f"mythstack:block/{name}"
    elif kind == "slab":
        bs = SLAB_BS.replace("minecraft:block/granite_slab", f"mythstack:block/{name}")
        bs = bs.replace("minecraft:block/granite", model_ref(base))
        write(f"assets/mythstack/blockstates/{name}.json", json.loads(bs))
        for suffix, parent in [("", "slab"), ("_top", "slab_top")]:
            write(f"assets/mythstack/models/block/{name}{suffix}.json",
                  {"parent": f"minecraft:block/{parent}",
                   "textures": {"bottom": tex, "top": tex, "side": tex}})
            counts["model"] += 1
        item_model = f"mythstack:block/{name}"
    elif kind == "wall":
        bs = WALL_BS.replace("minecraft:block/granite_wall", f"mythstack:block/{name}")
        write(f"assets/mythstack/blockstates/{name}.json", json.loads(bs))
        for suffix, parent in [("_post", "template_wall_post"), ("_side", "template_wall_side"),
                               ("_side_tall", "template_wall_side_tall"), ("_inventory", "wall_inventory")]:
            write(f"assets/mythstack/models/block/{name}{suffix}.json",
                  {"parent": f"minecraft:block/{parent}", "textures": {"wall": tex}})
            counts["model"] += 1
        item_model = f"mythstack:block/{name}_inventory"
    elif kind == "pillar":
        bs = PILLAR_BS.replace("minecraft:block/quartz_pillar", f"mythstack:block/{name}")
        write(f"assets/mythstack/blockstates/{name}.json", json.loads(bs))
        side, end = f"mythstack:block/{name}", f"mythstack:block/{name}_top"
        write(f"assets/mythstack/models/block/{name}.json",
              {"parent": "minecraft:block/cube_column", "textures": {"side": side, "end": end}})
        write(f"assets/mythstack/models/block/{name}_horizontal.json",
              {"parent": "minecraft:block/cube_column_horizontal", "textures": {"side": side, "end": end}})
        counts["model"] += 2
        item_model = f"mythstack:block/{name}"
    counts["bs"] += 1
    write(f"assets/mythstack/items/{name}.json",
          {"model": {"type": "minecraft:model", "model": item_model}})
    counts["item"] += 1
    loot = (LOOT_SLAB if kind == "slab" else LOOT_SIMPLE).replace(
        "minecraft:granite_slab" if kind == "slab" else "minecraft:granite", f"mythstack:{name}")
    write(f"data/mythstack/loot_table/blocks/{name}.json", json.loads(loot))
    counts["loot"] += 1
    lang[f"block.mythstack.{name}"] = " ".join(w.capitalize() for w in name.split("_"))
    pickaxe["values"].append(f"mythstack:{name}")

def shaped(name, pattern, key, result, count):
    write(f"data/mythstack/recipe/{name}.json",
          {"type": "minecraft:crafting_shaped", "key": key, "pattern": pattern,
           "result": {"id": result, "count": count} if count > 1 else {"id": result}})
    counts["recipe"] += 1

def smelt(name, inp, result, xp=0.1):
    write(f"data/mythstack/recipe/{name}.json",
          {"type": "minecraft:smelting", "ingredient": inp, "result": {"id": result},
           "experience": xp, "cookingtime": 200})
    counts["recipe"] += 1

def cut(name, inp, result, count=1):
    if (inp, result) in vanilla_cuts:
        return
    write(f"data/mythstack/recipe/stonecutting/{name}.json",
          {"type": "minecraft:stonecutting", "ingredient": inp,
           "result": {"id": result, "count": count} if count > 1 else {"id": result}})
    counts["recipe"] += 1

total_new = 0
for m in MATERIALS:
    name, raw, cob, pol, br, chis, pillar, raw_is_pillar, loot_norm = m
    raw_line = "dripstone" if name == "dripstone" else raw

    # (block name, kind, base full-cube) in kit order — mirrors StoneKit.lines()
    entries = []
    def add_line(base):
        s = stem(base, br) if not base.startswith("mossy_") else (base[:-1] if base.endswith("s") else base)
        entries.append((base, "cube", None))
        entries.append((f"{s}_stairs", "stairs", base))
        entries.append((f"{s}_slab", "slab", base))
        entries.append((f"{s}_wall", "wall", base))
    entries.append((raw, "cube", None))
    entries.append((f"{raw_line}_stairs", "stairs", raw))
    entries.append((f"{raw_line}_slab", "slab", raw))
    entries.append((f"{raw_line}_wall", "wall", raw))
    for base in (cob, pol, br):
        add_line(base)
    entries.append((f"cracked_{br}", "cube", None))
    entries.append((chis, "cube", None))
    if not raw_is_pillar:
        entries.append((pillar, "pillar", None))
    if is_mossy(name):
        add_line(f"mossy_{cob}")
        add_line(f"mossy_{br}")

    new = [(n, k, b) for n, k, b in entries if n not in VANILLA]
    total_new += len(new)
    for n, kind, base in new:
        emit_block(n, kind, base)
        if kind == "stairs":
            shaped(n, ["#  ", "## ", "###"], {"#": item_id(base)}, f"mythstack:{n}", 4)
            stairs_tag.append(f"mythstack:{n}")
        elif kind == "slab":
            shaped(n, ["###"], {"#": item_id(base)}, f"mythstack:{n}", 6)
            slabs_tag.append(f"mythstack:{n}")
        elif kind == "wall":
            shaped(n, ["###", "###"], {"#": item_id(base)}, f"mythstack:{n}", 6)
            walls_tag.append(f"mythstack:{n}")
        elif kind == "pillar":
            shaped(n, ["#", "#"], {"#": item_id(raw)}, f"mythstack:{n}", 2)
        elif n == pol:
            shaped(n, ["##", "##"], {"#": item_id(raw)}, f"mythstack:{n}", 4)
        elif n == br:
            shaped(n, ["##", "##"], {"#": item_id(pol)}, f"mythstack:{n}", 4)
        elif n == chis:
            shaped(n, ["#", "#"], {"#": item_id(f"{raw_line}_slab")}, f"mythstack:{n}", 1)
        elif n == f"cracked_{br}":
            smelt(n, item_id(br), f"mythstack:{n}")
        elif n == cob:
            smelt(f"{raw}_from_smelting_{cob}", f"mythstack:{cob}", item_id(raw))
        elif n.startswith("mossy_"):
            plain = n.removeprefix("mossy_")
            for greenery in ("minecraft:vine", "minecraft:moss_block"):
                write(f"data/mythstack/recipe/{n}_from_{greenery.split(':')[1]}.json",
                      {"type": "minecraft:crafting_shapeless",
                       "ingredients": [item_id(plain), greenery],
                       "result": {"id": f"mythstack:{n}"}})
                counts["recipe"] += 1

    # loot normalization: mining raw drops cobbled, silk touch restores raw
    if loot_norm:
        silk = LOOT_SILK.replace("minecraft:cobblestone", item_id(cob)).replace("minecraft:stone", item_id(raw))
        write(f"data/minecraft/loot_table/blocks/{raw}.json", json.loads(silk))
        counts["loot"] += 1
    # nether stones keep vanilla drops: cobbled comes from a stonecutter cut instead
    if name in ("blackstone", "basalt"):
        cut(f"{cob}_from_{raw}_stonecutting", item_id(raw), item_id(cob))

    # stonecutting parity: raw reaches everything; each line base reaches its own shapes
    line_stems = {raw: raw_line, cob: stem(cob, br), pol: stem(pol, br), br: stem(br, br)}
    raw_targets = []
    for b in (pol, br):
        raw_targets.append(b)
    for b in (raw, pol, br):
        s2 = line_stems[b]
        raw_targets += [f"{s2}_stairs", f"{s2}_slab", f"{s2}_wall"]
    raw_targets += [chis] + ([] if raw_is_pillar else [pillar])
    for t in raw_targets:
        cut(f"{t}_from_{raw}_stonecutting", item_id(raw), item_id(t), 2 if t.endswith("_slab") else 1)
    mossy_bases = [f"mossy_{cob}", f"mossy_{br}"] if is_mossy(name) else []
    for b in (cob, pol, br, *mossy_bases):
        s2 = line_stems.get(b) or (b[:-1] if b.endswith("s") else b)
        for t in (f"{s2}_stairs", f"{s2}_slab", f"{s2}_wall"):
            cut(f"{t}_from_{b}_stonecutting", item_id(b), item_id(t), 2 if t.endswith("_slab") else 1)

write("data/minecraft/tags/block/mineable/pickaxe.json", pickaxe)
for reg in ("block", "item"):
    write(f"data/minecraft/tags/{reg}/walls.json", {"values": walls_tag})
    write(f"data/minecraft/tags/{reg}/stairs.json", {"values": stairs_tag})
    write(f"data/minecraft/tags/{reg}/slabs.json", {"values": slabs_tag})
lp = os.path.join(ROOT, "assets/mythstack/lang/en_us.json")
json.dump(dict(sorted(lang.items())), open(lp, "w"), indent="\t"); open(lp, "a").write("\n")
print(f"stone kit: {total_new} blocks | {counts}")
