"""Shared S1a naming rules — MUST mirror StoneKit.java exactly."""
MATERIALS = [
    # name, raw, cobbled, polished, bricks, chiseled, pillar, raw_is_pillar, loot_normalized
    ("stone", "stone", "cobblestone", "smooth_stone", "stone_bricks", "chiseled_stone_bricks", "stone_pillar", False, False),
    ("deepslate", "deepslate", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks", "chiseled_deepslate", "deepslate_pillar", False, False),
    ("granite", "granite", "cobbled_granite", "polished_granite", "granite_bricks", "chiseled_granite", "granite_pillar", False, True),
    ("diorite", "diorite", "cobbled_diorite", "polished_diorite", "diorite_bricks", "chiseled_diorite", "diorite_pillar", False, True),
    ("andesite", "andesite", "cobbled_andesite", "polished_andesite", "andesite_bricks", "chiseled_andesite", "andesite_pillar", False, True),
    ("tuff", "tuff", "cobbled_tuff", "polished_tuff", "tuff_bricks", "chiseled_tuff", "tuff_pillar", False, True),
    ("calcite", "calcite", "cobbled_calcite", "polished_calcite", "calcite_bricks", "chiseled_calcite", "calcite_pillar", False, True),
    ("blackstone", "blackstone", "cobbled_blackstone", "polished_blackstone", "polished_blackstone_bricks", "chiseled_polished_blackstone", "blackstone_pillar", False, False),
    ("basalt", "basalt", "cobbled_basalt", "polished_basalt", "basalt_bricks", "chiseled_basalt", "basalt", True, False),
    ("end_stone", "end_stone", "cobbled_end_stone", "polished_end_stone", "end_stone_bricks", "chiseled_end_stone", "end_stone_pillar", False, True),
    ("dripstone", "dripstone_block", "cobbled_dripstone", "polished_dripstone", "dripstone_bricks", "chiseled_dripstone", "dripstone_pillar", False, True),
    ("sandstone", "sandstone", "cobbled_sandstone", "smooth_sandstone", "sandstone_bricks", "chiseled_sandstone", "sandstone_pillar", False, True),
    ("red_sandstone", "red_sandstone", "cobbled_red_sandstone", "smooth_red_sandstone", "red_sandstone_bricks", "chiseled_red_sandstone", "red_sandstone_pillar", False, True),
    # 26.2's newest stones — near-complete vanilla kits; volcanic-dry, so no mossy
    ("cinnabar", "cinnabar", "cobbled_cinnabar", "polished_cinnabar", "cinnabar_bricks", "chiseled_cinnabar", "cinnabar_pillar", False, True),
    ("sulfur", "sulfur", "cobbled_sulfur", "polished_sulfur", "sulfur_bricks", "chiseled_sulfur", "sulfur_pillar", False, True),
    # tier 2 (reduced kit: no cobbled line, no mossy, vanilla drops)
    ("netherrack", "netherrack", "", "polished_netherrack", "nether_bricks", "chiseled_nether_bricks", "netherrack_pillar", False, False),
    ("quartz", "quartz_block", "", "smooth_quartz", "quartz_bricks", "chiseled_quartz_block", "quartz_pillar", False, False),
    ("prismarine", "prismarine", "", "dark_prismarine", "prismarine_bricks", "chiseled_prismarine", "prismarine_pillar", False, False),
    ("purpur", "purpur_block", "", "polished_purpur", "purpur_bricks", "chiseled_purpur", "purpur_pillar", False, False),
    ("packed_mud", "packed_mud", "", "polished_packed_mud", "mud_bricks", "chiseled_packed_mud", "packed_mud_pillar", False, False),
]

def raw_line_of(name, raw):
    return {"dripstone": "dripstone", "quartz": "quartz", "purpur": "purpur"}.get(name, raw)

def is_tier2(name):
    return name in ("netherrack", "quartz", "prismarine", "purpur", "packed_mud")

def stem(base, bricks):
    return base[:-1] if base == bricks and base.endswith("s") else base

def forms(m):
    """Kit-order {form_key: block name or None} for one material — the S2 group axis."""
    name, raw, cob, pol, br, chis, pillar, raw_is_pillar, _ = m
    raw_line = raw_line_of(name, raw)
    out = {"raw": raw, "raw_stairs": f"{raw_line}_stairs", "raw_slab": f"{raw_line}_slab",
           "raw_wall": f"{raw_line}_wall"}
    tier2 = is_tier2(name)
    for form_key, shaped_key, base in (("cobbled", "cobbled", cob), ("polished", "polished", pol),
                                       ("bricks", "brick", br)):
        if form_key == "cobbled" and tier2:
            for k in ("cobbled", "cobbled_stairs", "cobbled_slab", "cobbled_wall"):
                out[k] = None
            continue
        s = stem(base, br)
        out[form_key] = base
        out[f"{shaped_key}_stairs"] = f"{s}_stairs"
        out[f"{shaped_key}_slab"] = f"{s}_slab"
        out[f"{shaped_key}_wall"] = f"{s}_wall"
    out["cracked_bricks"] = f"cracked_{br}"
    out["chiseled"] = chis
    out["pillar"] = None if raw_is_pillar else pillar
    for form_key, shaped_key, base in (("mossy_cobbled", "mossy_cobbled", f"mossy_{cob}"),
                                       ("mossy_bricks", "mossy_brick", f"mossy_{br}")):
        if not is_mossy(name):
            out[form_key] = None
            for k in ("_stairs", "_slab", "_wall"):
                out[f"{shaped_key}{k}"] = None
            continue
        s = base[:-1] if base.endswith("s") else base
        out[form_key] = base
        out[f"{shaped_key}_stairs"] = f"{s}_stairs"
        out[f"{shaped_key}_slab"] = f"{s}_slab"
        out[f"{shaped_key}_wall"] = f"{s}_wall"
    # functional forms: buttons/plates for every material (vanilla placements kept);
    # furnaces/pistons only where cobbled exists (their bodies are cobbled)
    if name == "stone":
        out["button"] = "stone_button"
        out["pressure_plate"] = "stone_pressure_plate"
        out["furnace"] = "furnace"
        out["piston"] = "piston"
        out["sticky_piston"] = "sticky_piston"
    elif name == "blackstone":
        out["button"] = "polished_blackstone_button"
        out["pressure_plate"] = "polished_blackstone_pressure_plate"
        out["furnace"] = f"{name}_furnace"
        out["piston"] = f"{name}_piston"
        out["sticky_piston"] = f"{name}_sticky_piston"
    else:
        out["button"] = f"{name}_button"
        out["pressure_plate"] = f"{name}_pressure_plate"
        out["furnace"] = None if tier2 else f"{name}_furnace"
        out["piston"] = None if tier2 else f"{name}_piston"
        out["sticky_piston"] = None if tier2 else f"{name}_sticky_piston"
    return out

def lines(m):
    """Kit-order names for one material tuple; same as StoneKit.lines()."""
    return [n for n in forms(m).values() if n]

def is_mossy(name):
    return name in ("stone", "deepslate", "granite", "diorite", "andesite", "tuff", "calcite", "dripstone")
