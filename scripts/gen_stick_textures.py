"""Generate per-wood stick AND ladder textures by palette-mapping the vanilla art through each wood's
planks palette (luminance-ranked), plus the stick model/item-definition JSON. Pure-python PNG codec
since Pillow isn't available."""
import json
import os
import struct
import zipfile
import zlib

JAR = os.path.expanduser("~/.gradle/caches/fabric-loom/26.2/minecraft-client.jar")
ROOT = "src/main/resources"
WOODS = ["spruce", "birch", "jungle", "acacia", "dark_oak", "mangrove", "cherry", "pale_oak",
         "bamboo", "crimson", "warped"]


def png_decode(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n"
    pos, w, h, ctype, plte, trns, idat = 8, 0, 0, 0, None, None, b""
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
            assert depth in (1, 2, 4, 8), f"unsupported bit depth {depth}"
        elif tag == b"PLTE":
            plte = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break
    raw = zlib.decompress(idat)
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    bits = channels * depth
    stride = (w * bits + 7) // 8
    bpp = max(1, bits // 8)  # filter byte-offset
    out, prev, p = [], bytearray(stride), 0
    for _ in range(h):
        f = raw[p]
        row = bytearray(raw[p + 1:p + 1 + stride])
        p += 1 + stride
        for i in range(stride):
            a = row[i - bpp] if i >= bpp else 0
            b = prev[i]
            c = prev[i - bpp] if i >= bpp else 0
            if f == 1:
                row[i] = (row[i] + a) & 0xFF
            elif f == 2:
                row[i] = (row[i] + b) & 0xFF
            elif f == 3:
                row[i] = (row[i] + (a + b) // 2) & 0xFF
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pred = a if pa <= pb and pa <= pc else (b if pb <= pc else c)
                row[i] = (row[i] + pred) & 0xFF
        prev = row
        if depth < 8:  # sub-byte samples (palette/grayscale): unpack MSB-first
            unpacked = []
            mask = (1 << depth) - 1
            scale = 255 // mask
            for x in range(w):
                bit = x * depth
                value = (row[bit // 8] >> (8 - depth - bit % 8)) & mask
                unpacked.append(value)
            for x in range(w):
                v = unpacked[x]
                if ctype == 3:
                    r, g, b2 = plte[v * 3:v * 3 + 3]
                    a2 = trns[v] if trns and v < len(trns) else 255
                    out.append((r, g, b2, a2))
                else:  # grayscale
                    out.append((v * scale, v * scale, v * scale, 255))
            continue
        for x in range(w):
            px = row[x * channels:(x + 1) * channels]
            if ctype == 6:
                out.append(tuple(px))
            elif ctype == 2:
                out.append((px[0], px[1], px[2], 255))
            elif ctype == 0:
                out.append((px[0], px[0], px[0], 255))
            elif ctype == 4:
                out.append((px[0], px[0], px[0], px[1]))
            elif ctype == 3:
                idx = px[0]
                r, g, b2 = plte[idx * 3:idx * 3 + 3]
                a2 = trns[idx] if trns and idx < len(trns) else 255
                out.append((r, g, b2, a2))
    return w, h, out


def png_encode(w, h, pixels):
    raw = bytearray()
    for y in range(h):
        raw.append(0)
        for x in range(w):
            raw.extend(pixels[y * w + x])

    def chunk(tag, body):
        return struct.pack(">I", len(body)) + tag + body + struct.pack(">I", zlib.crc32(tag + body))

    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def lum(p):
    return 0.299 * p[0] + 0.587 * p[1] + 0.114 * p[2]


BASES = [  # (vanilla texture to palette-map fully, per-wood output path template)
    ("assets/minecraft/textures/item/stick.png", "assets/mythstack/textures/item/{}_stick.png"),
    ("assets/minecraft/textures/block/ladder.png", "assets/mythstack/textures/block/{}_ladder.png"),
    ("assets/minecraft/textures/block/barrel_bottom.png", "assets/mythstack/textures/block/{}_barrel_bottom.png"),
    ("assets/minecraft/textures/block/barrel_side.png", "assets/mythstack/textures/block/{}_barrel_side.png"),
    ("assets/minecraft/textures/block/barrel_top.png", "assets/mythstack/textures/block/{}_barrel_top.png"),
    ("assets/minecraft/textures/block/barrel_top_open.png", "assets/mythstack/textures/block/{}_barrel_top_open.png"),
]
# Textures that mix wood with non-wood detail (books, tools, the crafting grid): only pixels close to
# the OAK planks palette are remapped; everything else is preserved verbatim.
SELECTIVE_PREFIXES = ["fletching_table", "cartography_table", "smithing_table", "loom", "lectern",
                      "composter", "note_block", "jukebox", "beehive"]
SELECTIVE = [
    ("assets/minecraft/textures/block/bookshelf.png", "assets/mythstack/textures/block/{}_bookshelf.png"),
    ("assets/minecraft/textures/block/chiseled_bookshelf_top.png", "assets/mythstack/textures/block/{}_chiseled_bookshelf_top.png"),
    ("assets/minecraft/textures/block/chiseled_bookshelf_side.png", "assets/mythstack/textures/block/{}_chiseled_bookshelf_side.png"),
    ("assets/minecraft/textures/block/chiseled_bookshelf_empty.png", "assets/mythstack/textures/block/{}_chiseled_bookshelf_empty.png"),
    ("assets/minecraft/textures/block/chiseled_bookshelf_occupied.png", "assets/mythstack/textures/block/{}_chiseled_bookshelf_occupied.png"),
    ("assets/minecraft/textures/block/crafting_table_top.png", "assets/mythstack/textures/block/{}_crafting_table_top.png"),
    ("assets/minecraft/textures/block/crafting_table_side.png", "assets/mythstack/textures/block/{}_crafting_table_side.png"),
    ("assets/minecraft/textures/block/crafting_table_front.png", "assets/mythstack/textures/block/{}_crafting_table_front.png"),
]
WOOD_DISTANCE = 42.0  # max RGB distance to the oak palette for a pixel to count as "wood"
def nearest_distance(p, palette):
    best = 1e9
    for q in palette:
        d = ((p[0]-q[0])**2 + (p[1]-q[1])**2 + (p[2]-q[2])**2) ** 0.5
        if d < best:
            best = d
    return best


with zipfile.ZipFile(JAR) as z:
    # Station families: every texture under the family prefix, selectively mapped.
    for prefix in SELECTIVE_PREFIXES:
        for n in z.namelist():
            if n.startswith(f"assets/minecraft/textures/block/{prefix}") and n.endswith(".png"):
                base = os.path.basename(n)[:-4]  # e.g. loom_side, note_block
                SELECTIVE.append((n, "assets/mythstack/textures/block/{}_" + base + ".png"))
    _, _, oak_planks = png_decode(z.read("assets/minecraft/textures/block/oak_planks.png"))
    oak_palette = sorted({p[:3] for p in oak_planks if p[3] > 0})
    for base_path, out_template in SELECTIVE:
        sw, sh, base = png_decode(z.read(base_path))
        wood_mask = [p[3] > 0 and nearest_distance(p, oak_palette) <= WOOD_DISTANCE for p in base]
        woody = [p for p, m in zip(base, wood_mask) if m]
        if not woody:
            continue
        lo, hi = min(map(lum, woody)), max(map(lum, woody))
        for wood in WOODS:
            pw, ph, planks = png_decode(z.read(f"assets/minecraft/textures/block/{wood}_planks.png"))
            palette = sorted((p for p in planks if p[3] > 0), key=lum)
            out = []
            for p, m in zip(base, wood_mask):
                if not m:
                    out.append(p)
                    continue
                t = 0.0 if hi == lo else (lum(p) - lo) / (hi - lo)
                r, g, b, _ = palette[round(t * (len(palette) - 1))]
                out.append((r, g, b, p[3]))
            dest = os.path.join(ROOT, out_template.format(wood))
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "wb") as f:
                f.write(png_encode(sw, sh, out))
    for base_path, out_template in BASES:
        sw, sh, base = png_decode(z.read(base_path))
        opaque = [p for p in base if p[3] > 0]
        lo, hi = min(map(lum, opaque)), max(map(lum, opaque))
        for wood in WOODS:
            pw, ph, planks = png_decode(z.read(f"assets/minecraft/textures/block/{wood}_planks.png"))
            palette = sorted((p for p in planks if p[3] > 0), key=lum)
            out = []
            for p in base:
                if p[3] == 0:
                    out.append((0, 0, 0, 0))
                    continue
                t = 0.0 if hi == lo else (lum(p) - lo) / (hi - lo)
                r, g, b, _ = palette[round(t * (len(palette) - 1))]
                out.append((r, g, b, p[3]))
            dest = os.path.join(ROOT, out_template.format(wood))
            os.makedirs(os.path.dirname(dest), exist_ok=True)
            with open(dest, "wb") as f:
                f.write(png_encode(sw, sh, out))

for wood in WOODS:
    model = os.path.join(ROOT, f"assets/mythstack/models/item/{wood}_stick.json")
    os.makedirs(os.path.dirname(model), exist_ok=True)
    with open(model, "w") as f:
        json.dump({"parent": "minecraft:item/handheld",
                   "textures": {"layer0": f"mythstack:item/{wood}_stick"}}, f, indent="\t")
        f.write("\n")
    with open(os.path.join(ROOT, f"assets/mythstack/items/{wood}_stick.json"), "w") as f:
        json.dump({"model": {"type": "minecraft:model", "model": f"mythstack:item/{wood}_stick"}}, f, indent="\t")
        f.write("\n")

print("generated", len(WOODS), "palette-mapped stick textures + models")
