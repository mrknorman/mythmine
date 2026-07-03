"""Functional stone forms: buttons + pressure plates (every material), furnaces + pistons +
sticky pistons (cobbled-bearing materials). Textures: structure transfer with PROTECTED pixels —
furnace fire stays fire (soul-blue for nether stones), piston wood stays wood. Also reverts the
S2 acceptance overrides these families supersede (the transmuter takes over)."""
import json, os, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from stone_naming import MATERIALS, forms

ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
from pnglib import *  # png codec + shared helpers  # png codec
JARP = os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar")
CJ = zipfile.ZipFile(JARP)
SJ = zipfile.ZipFile(os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-extracted_server.jar"))
VANILLA = {n.split("/")[-1][:-5] for n in CJ.namelist()
           if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")}
OUT = os.path.join(ROOT, "assets/mythstack/textures/block")

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

def our_or_vanilla_tex(name):
    ours = os.path.join(OUT, f"{name}.png")
    if os.path.exists(ours):
        _, _, px = png_decode(open(ours, "rb").read())
        return px
    return vtex(name)

def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]

def is_fire(p):
    r, g, b = p[:3]
    return r > 150 and r > b * 1.6 and g > b  # saturated warm: flame + embers

def soul(p):
    r, g, b = p[:3]
    return (int(b * 0.35), int(g * 0.95), min(255, int(r * 1.1)), p[3])  # warm -> cyan-blue

def transfer(donor, palette, protect=None, soulfire=False):
    ranked = sorted((q for q in palette if q[3] > 0), key=luminance)
    lums = sorted(luminance(q) for q in donor if q[3] > 0 and not (protect and protect(q)))
    lo, hi = (lums[0], lums[-1]) if lums else (0, 255)
    span = (hi - lo) or 1.0
    out = []
    for p in donor:
        if p[3] == 0:
            out.append(p)
        elif protect and protect(p):
            out.append(soul(p) if soulfire else p)
        else:
            frac = max(0.0, min(1.0, (luminance(p) - lo) / span))
            c = ranked[min(len(ranked) - 1, int(frac * (len(ranked) - 1)))]
            out.append((c[0], c[1], c[2], p[3]))
    return out

def save(name, px):
    w = h = int(len(px) ** 0.5)
    save_png(os.path.join(OUT, f"{name}.png"), w, h, px)

# wood-protection for piston bodies: pixels near the oak-planks palette stay wooden
oak = vtex("oak_planks")
def near_wood(p):
    return any(sum(abs(a - b) for a, b in zip(p[:3], q[:3])) < 90 for q in oak[::7])

NETHER = {"blackstone", "basalt"}
tex_count = 0
made = {"blocks": 0, "recipes": 0}
lang = json.load(open(os.path.join(ROOT, "assets/mythstack/lang/en_us.json")))
pickaxe = json.load(open(os.path.join(ROOT, "data/minecraft/tags/block/mineable/pickaxe.json")))
pickaxe["values"] = list(dict.fromkeys(pickaxe["values"]))
buttons_tag, plates_tag = [], []

BTN_BS = CJ.read("assets/minecraft/blockstates/stone_button.json").decode()
PLATE_BS = CJ.read("assets/minecraft/blockstates/stone_pressure_plate.json").decode()
FURNACE_BS = CJ.read("assets/minecraft/blockstates/furnace.json").decode()
PISTON_BS = CJ.read("assets/minecraft/blockstates/piston.json").decode()
STICKY_BS = CJ.read("assets/minecraft/blockstates/sticky_piston.json").decode()
LOOT = SJ.read("data/minecraft/loot_table/blocks/granite.json").decode()

def emit(name, bs, item_model):
    write(f"assets/mythstack/blockstates/{name}.json", json.loads(bs))
    write(f"assets/mythstack/items/{name}.json",
          {"model": {"type": "minecraft:model", "model": item_model}})
    write(f"data/mythstack/loot_table/blocks/{name}.json",
          json.loads(LOOT.replace("minecraft:granite", f"mythstack:{name}")))
    lang[f"block.mythstack.{name}"] = " ".join(w.capitalize() for w in name.split("_"))
    pickaxe["values"].append(f"mythstack:{name}")
    made["blocks"] += 1

def shaped(rname, pattern, key, result, count=1):
    write(f"data/mythstack/recipe/{rname}.json",
          {"type": "minecraft:crafting_shaped", "key": key, "pattern": pattern,
           "result": {"id": result, "count": count} if count > 1 else {"id": result}})
    made["recipes"] += 1

for m in MATERIALS:
    name = m[0]
    f = forms(m)
    raw, cob = f["raw"], f["cobbled"]
    raw_id = f"minecraft:{raw}" if raw in VANILLA else f"mythstack:{raw}"
    cob_id = (f"minecraft:{cob}" if cob in VANILLA else f"mythstack:{cob}") if cob else None
    raw_tex = ("mythstack:block/" + raw) if raw not in VANILLA else "minecraft:block/" + \
        {"basalt": "basalt_side", "sandstone": "sandstone", "quartz_block": "quartz_block_side",
         "purpur_block": "purpur_block", "dripstone_block": "dripstone_block"}.get(raw, raw)

    # buttons + plates (models reuse the raw texture — no new art)
    btn, plate = f["button"], f["pressure_plate"]
    if btn and btn not in VANILLA:
        emit(btn, BTN_BS.replace("minecraft:block/stone_button", f"mythstack:block/{btn}"),
             f"mythstack:block/{btn}_inventory")
        for suffix, parent in [("", "button"), ("_pressed", "button_pressed"), ("_inventory", "button_inventory")]:
            write(f"assets/mythstack/models/block/{btn}{suffix}.json",
                  {"parent": f"minecraft:block/{parent}", "textures": {"texture": raw_tex}})
        shaped(btn, ["#"], {"#": raw_id}, f"mythstack:{btn}")
        buttons_tag.append(f"mythstack:{btn}")
    if plate and plate not in VANILLA:
        emit(plate, PLATE_BS.replace("minecraft:block/stone_pressure_plate", f"mythstack:block/{plate}"),
             f"mythstack:block/{plate}")
        for suffix, parent in [("", "pressure_plate_up"), ("_down", "pressure_plate_down")]:
            write(f"assets/mythstack/models/block/{plate}{suffix}.json",
                  {"parent": f"minecraft:block/{parent}", "textures": {"texture": raw_tex}})
        shaped(plate, ["##"], {"#": raw_id}, f"mythstack:{plate}")
        plates_tag.append(f"mythstack:{plate}")

    # furnaces + pistons (cobbled-bearing only)
    fur = f["furnace"]
    if fur and fur not in VANILLA:
        palette = our_or_vanilla_tex(cob)
        for donor_name in ("furnace_front", "furnace_front_on", "furnace_side", "furnace_top"):
            donor = vtex(donor_name)
            fire = "on" in donor_name
            px = transfer(donor, palette, protect=is_fire if fire else None,
                          soulfire=fire and name in NETHER)
            save(donor_name.replace("furnace", fur), px)
            tex_count += 1
        bs = FURNACE_BS.replace("minecraft:block/furnace", f"mythstack:block/{fur}")
        emit(fur, bs, f"mythstack:block/{fur}")
        t = lambda part: f"mythstack:block/{fur}_{part}"
        write(f"assets/mythstack/models/block/{fur}.json",
              {"parent": "minecraft:block/orientable",
               "textures": {"front": t("front"), "side": t("side"), "top": t("top")}})
        write(f"assets/mythstack/models/block/{fur}_on.json",
              {"parent": "minecraft:block/orientable",
               "textures": {"front": t("front_on"), "side": t("side"), "top": t("top")}})
        shaped(fur, ["###", "# #", "###"], {"#": cob_id}, f"mythstack:{fur}")

    pis, sticky = f["piston"], f["sticky_piston"]
    if pis and pis not in VANILLA:
        palette = our_or_vanilla_tex(cob)
        for donor_name in ("piston_bottom", "piston_side", "piston_inner"):
            donor = vtex(donor_name)
            px = transfer(donor, palette, protect=near_wood if donor_name != "piston_bottom" else None)
            save(donor_name.replace("piston", pis), px)
            tex_count += 1
        for bname, bs_tpl, top_tex in ((pis, PISTON_BS, "minecraft:block/piston_top"),
                                       (sticky, STICKY_BS, "minecraft:block/piston_top_sticky")):
            stem_from = "minecraft:block/piston" if bname == pis else "minecraft:block/sticky_piston"
            bs = bs_tpl.replace(stem_from, f"mythstack:block/{bname}")
            bs = bs.replace("minecraft:block/piston", f"mythstack:block/{pis}")  # sticky shares body models? no — own
            emit(bname, bs, f"mythstack:block/{bname}_inventory")
            t = lambda part: f"mythstack:block/{pis}_{part}"
            write(f"assets/mythstack/models/block/{bname}.json",
                  {"parent": "minecraft:block/template_piston",
                   "textures": {"platform": top_tex, "bottom": t("bottom"), "side": t("side")}})
            write(f"assets/mythstack/models/block/{bname}_base.json",
                  {"parent": "minecraft:block/piston_extended",
                   "textures": {"bottom": t("bottom"), "side": t("side"), "inside": t("inner")}})
            write(f"assets/mythstack/models/block/{bname}_inventory.json",
                  {"parent": "minecraft:block/cube_bottom_top",
                   "textures": {"top": top_tex, "bottom": t("bottom"), "side": t("side")}})
        shaped(pis, ["TTT", "#X#", "#R#"],
               {"T": "#minecraft:planks", "#": cob_id, "X": "minecraft:iron_ingot", "R": "minecraft:redstone"},
               f"mythstack:{pis}")
        shaped(sticky, ["S", "P"], {"S": "minecraft:slime_ball", "P": f"mythstack:{pis}"},
               f"mythstack:{sticky}")

# group tags for the 5 new forms + canonical pile wrappers
def member_id(n):
    return f"minecraft:{n}" if n in VANILLA else f"mythstack:{n}"
for form in ("button", "pressure_plate", "furnace", "piston", "sticky_piston"):
    members = [member_id(forms(m)[form]) for m in MATERIALS if forms(m)[form]]
    write(f"data/mythstack/tags/item/stone/{form}.json", {"values": members})
def pile_wrap(on_false):
    return {"model": {"type": "minecraft:condition", "property": "minecraft:has_component",
                      "component": "mythstack:variant_pile",
                      "on_true": {"type": "minecraft:special", "base": "mythstack:item/pile_base",
                                  "model": {"type": "mythstack:pile"}},
                      "on_false": on_false}}
for canon in ("stone_button", "stone_pressure_plate", "furnace", "piston", "sticky_piston"):
    vanilla = json.loads(CJ.read(f"assets/minecraft/items/{canon}.json"))
    write(f"assets/minecraft/items/{canon}.json", pile_wrap(vanilla["model"]))

# vanilla tag appends (walls-style): buttons + stone_buttons + pressure_plates
for reg in ("block", "item"):
    write(f"data/minecraft/tags/{reg}/buttons.json", {"values": buttons_tag})
    write(f"data/minecraft/tags/{reg}/stone_buttons.json", {"values": buttons_tag})
write("data/minecraft/tags/block/pressure_plates.json", {"values": plates_tag})

# REVERTS: these S2 acceptance overrides are superseded by per-material families (the transmuter
# handles mixed grids now); the furnace recipe pins to exact cobblestone so per-material recipes
# don't double-match the vanilla tag.
for stale in ("stone_button", "stone_pressure_plate", "piston",
              "polished_blackstone_button", "polished_blackstone_pressure_plate"):
    path = os.path.join(ROOT, f"data/minecraft/recipe/{stale}.json")
    if os.path.exists(path):
        os.remove(path)
furnace = json.loads(SJ.read("data/minecraft/recipe/furnace.json"))
furnace["key"] = {k: ("minecraft:cobblestone" if v == "#minecraft:stone_crafting_materials" else v)
                  for k, v in furnace["key"].items()}
write("data/minecraft/recipe/furnace.json", furnace)

write("data/minecraft/tags/block/mineable/pickaxe.json", pickaxe)
lp = os.path.join(ROOT, "assets/mythstack/lang/en_us.json")
json.dump(dict(sorted(lang.items())), open(lp, "w"), indent="\t"); open(lp, "a").write("\n")
print(f"functional: {made['blocks']} blocks, {made['recipes']} recipes, {tex_count} textures")
