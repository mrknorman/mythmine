# Modules — the vanilla-feel dials

Phase 1 of the submod plan (see the bottom): one jar, four content modules + three built-in packs.
**Registration always happens** (existing worlds keep their blocks; tags never dangle) — a module
being off means UNOBTAINABLE: no recipes, no trades, no worldgen, no creative-tab entries, and the
item is excluded from family membership so piles/transmuter never produce it.

## config/mythstack.json

| Flag | Contents | Off means |
|---|---|---|
| `blocks` | typed stations/ladders/chests/etc., the stone kit + functional forms | craft/see none of it; family recipes still accept vanilla members |
| `sawmill` | the sawmill block + carpenter villager/trades | stonecutter-for-wood and the profession never appear |
| `terrain` | the geology overhaul: regions, bands, permafrost gen, ore variants, loot normalization | vanilla worldgen and drops |
| `typed_sticks` | the 11 typed sticks | vanilla stick everywhere (transmuter falls back too) |

PILES — the machinery, vanilla families, pickup/merge/scroll, pile-aware furnaces/stonecutter —
is the mod itself and has no flag.

## Built-in packs (vanilla UI toggles)

- **mythstack:geology** (data) — the vanilla-file OVERRIDES for terrain: noise settings, ore
  features, raw-stone loot. Follows the `terrain` flag for default activation; also controllable
  per-world via `/datapack`. Overrides must live in packs: a resource CONDITION on an override
  leaves the registry entry unbound instead of falling back to vanilla (learned the hard way).
- **mythstack:sticks** (data) — leaves-drop-typed-sticks loot overrides; follows `typed_sticks`.
- **mythstack:renames** (client resources) — Shale / Cobbled Shale / Oak Stick / Oak ... display
  names. Toggle in the resource-pack screen; ids never change.

## Verified profiles (scripts/run_selftest.sh + config)

| Profile | Checks | Notes |
|---|---|---|
| all on (default) | 192 | canonical |
| terrain off | 187 | vanilla worldgen, geology checks skipped |
| piles only (all off) | 129 | the original MVP standalone, zero data errors |

## Phase 2 (planned): physical submods

The config seams become jar boundaries: `mythstack-piles` <- `mythstack-blocks` <-
`mythstack-terrain`, with `mythstack-sawmill` depending on piles (soft on blocks — cross-content
already carries dual conditions). Gradle multi-project, one fabric.mod.json per jar; the module
guards above become `FabricLoader.isModLoaded` checks. Separate mod-page listings let piles-only
players never see the overhaul.
