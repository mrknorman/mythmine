"""Generate per-wood stick textures by palette-mapping the vanilla stick through each wood's planks
palette (luminance-ranked), plus the model/item-definition JSON to use them. Pure-python PNG codec
(bit depth 8; color types 0/2/3/4/6) since Pillow isn't available."""
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


with zipfile.ZipFile(JAR) as z:
    sw, sh, stick = png_decode(z.read("assets/minecraft/textures/item/stick.png"))
    opaque = [p for p in stick if p[3] > 0]
    lo, hi = min(map(lum, opaque)), max(map(lum, opaque))
    for wood in WOODS:
        pw, ph, planks = png_decode(z.read(f"assets/minecraft/textures/block/{wood}_planks.png"))
        palette = sorted((p for p in planks if p[3] > 0), key=lum)
        out = []
        for p in stick:
            if p[3] == 0:
                out.append((0, 0, 0, 0))
                continue
            t = 0.0 if hi == lo else (lum(p) - lo) / (hi - lo)
            r, g, b, _ = palette[round(t * (len(palette) - 1))]
            out.append((r, g, b, p[3]))
        dest = os.path.join(ROOT, f"assets/mythstack/textures/item/{wood}_stick.png")
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
