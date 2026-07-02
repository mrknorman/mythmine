"""Regression net: every mythstack:block/* texture referenced by any of our models must exist.
(The smithing-table-top bug: a generator skip left model refs dangling as missing textures.)"""
import json, glob, os, sys
ROOT = os.path.join(os.path.dirname(__file__), "..", "src/main/resources")
missing = set()
for f in glob.glob(f"{ROOT}/assets/mythstack/models/**/*.json", recursive=True):
    m = json.load(open(f))
    for tex in (m.get("textures") or {}).values():
        if tex.startswith("mythstack:"):
            path = f"{ROOT}/assets/mythstack/textures/{tex.split(':', 1)[1]}.png"
            if not os.path.exists(path):
                missing.add((os.path.basename(f), tex))
for x in sorted(missing):
    print("MISSING:", *x)
print(f"{len(missing)} missing texture refs")
sys.exit(1 if missing else 0)
