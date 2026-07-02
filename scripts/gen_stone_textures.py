"""S1a base textures: structure transfer — a vanilla pattern donor's luminance structure
re-rendered through the target material's palette by luminance rank (STONE_PHASE.md)."""
import os, sys, zipfile
sys.path.insert(0, os.path.dirname(__file__))
from stone_naming import MATERIALS, lines

# borrow the PNG codec from the stick generator
_src = open(os.path.join(os.path.dirname(__file__), "gen_stick_textures.py")).read()
exec(_src.split("BASES = ")[0])

JAR2 = os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar")
OUT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources/assets/mythstack/textures/block")

def luminance(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]

def structure_transfer(donor, palette):
    """Map each donor pixel to the palette color at the same luminance rank."""
    ranked = sorted((p for p in palette if p[3] > 0), key=luminance)
    lums = sorted(luminance(p) for p in donor if p[3] > 0)
    if not ranked or not lums:
        return donor
    lo, hi = lums[0], lums[-1]
    span = (hi - lo) or 1.0
    out = []
    for p in donor:
        if p[3] == 0:
            out.append(p)
            continue
        frac = (luminance(p) - lo) / span
        c = ranked[min(len(ranked) - 1, int(frac * (len(ranked) - 1)))]
        out.append((c[0], c[1], c[2], p[3]))
    return out

# raw-texture name per material (palette source)
PALETTE_TEX = {"basalt": "basalt_side", "dripstone": "dripstone_block"}

DONORS = {  # form -> pattern donor texture
    "cobbled": "cobblestone", "polished": "polished_andesite", "bricks": "stone_bricks",
    "cracked": "cracked_stone_bricks", "chiseled": "chiseled_stone_bricks",
    "pillar": "quartz_pillar_side", "pillar_top": "quartz_pillar_top",
}

with zipfile.ZipFile(JAR2) as z:
    tex_names = {n.split("/")[-1][:-4] for n in z.namelist()
                 if n.startswith("assets/minecraft/textures/block/") and n.endswith(".png")}
    def vtex(name):
        _, _, px = png_decode(z.read(f"assets/minecraft/textures/block/{name}.png"))
        return px
    states = {n.split("/")[-1][:-5] for n in z.namelist()
              if n.startswith("assets/minecraft/blockstates/") and n.endswith(".json")}
    made = 0
    for m in MATERIALS:
        name, raw, cob, pol, br, chis, pillar, raw_is_pillar, _ = m
        palette = vtex(PALETTE_TEX.get(name, raw))
        jobs = []
        if cob not in states:
            jobs.append((cob, "cobbled"))
        if pol not in states:
            jobs.append((pol, "polished"))
        if br not in states:
            jobs.append((br, "bricks"))
        if f"cracked_{br}" not in states:
            jobs.append((f"cracked_{br}", "cracked"))
        if chis not in states:
            jobs.append((chis, "chiseled"))
        if not raw_is_pillar and pillar not in states:
            jobs.append((pillar, "pillar"))
            jobs.append((f"{pillar}_top", "pillar_top"))
        for out_name, form in jobs:
            donor = vtex(DONORS[form])
            w = h = int(len(donor) ** 0.5)
            px = structure_transfer(donor, palette)
            open(os.path.join(OUT, f"{out_name}.png"), "wb").write(png_encode(w, h, px))
            made += 1
print(f"generated {made} stone base textures")
