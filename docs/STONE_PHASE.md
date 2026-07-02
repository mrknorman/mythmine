# Stone Phase — Normalization + Families

**Status: S1 COMPLETE (2026-07-02) — all normalization shipped: S1a core kit (143) + S1b mossy
(56) + S1c tier-2 (40) + cinnabar/sulfur (12) = 251 blocks, 102 generated base textures, 562
recipes, loot normalization on. Next: S2 families (27 form-groups + stoneKey).**

S2 design caution: `stoneKey` name-matching must handle materials that are substrings of others —
"stone" is a suffix of sandstone/end_stone/cobblestone/blackstone/dripstone; match longest-first
on the material list like `woodKey` does, and treat cobbled/mossy/polished prefixes before the
material match.

Implementation notes: gap set is DERIVED, not hardcoded — `StoneKit.java` registers a form
only when vanilla lacks it (guarded to exactly 251) and `scripts/stone_naming.py` mirrors the
naming for the generators (`gen_stone_textures.py`, `gen_stone_kit.py`). Recipe chains follow the
deepslate/tuff convention: cobbled →(smelt)→ raw →(2×2)→ polished →(2×2)→ bricks →(smelt)→
cracked; chiseled = 2 raw slabs; pillar = 2 raw. Blackstone/basalt keep vanilla drops and get a
raw→cobbled stonecutter cut instead (documented quirk).

Goal: bring the pile/family system to stone — and unlike wood, **normalize vanilla first**.
Vanilla stone coverage is gap-ridden and inconsistent (granite has polished but no bricks; calcite
has *nothing*; deepslate has cobbled stairs but no raw stairs; only stone has mossy; only
basalt/quartz/purpur have pillars). We fill every material to the same form kit so the family
layer needs no exception table, then group by form across materials.

All counts are exact, probed against the 26.2 client jar.

## The two axes

Everything in this phase is a point on a **material × form** grid — the stone analog of wood's
*species × form* (spruce × bookshelf). A **material** is a stone identity (granite, calcite,
sandstone…). A **form** is a shape/finish of that material (cobbled, brick stairs, pillar…).

- **Normalization** fills the grid: every tier-1 material gets every core form.
- **Families (groups)** run along the material axis, one group per *fine-grained* form: all
  materials' `raw_stairs` are one group; all `brick_stairs` are another; `mossy_brick_stairs` a
  third. **Brick stairs never group with plain stairs.** Canonical member of every group = stone's
  version of that form. `stoneKey` (the `woodKey` analog) identifies the material across groups so
  transmute voting, furnaces, and the stonecutter treat "granite" as one identity in any form.

### The form axis

**Core kit — 19 forms** (every tier-1 material):

```
raw          raw_stairs        raw_slab        raw_wall
cobbled      cobbled_stairs    cobbled_slab    cobbled_wall
polished     polished_stairs   polished_slab   polished_wall
bricks       brick_stairs      brick_slab      brick_wall
cracked_bricks         chiseled         pillar
```

- **chiseled is ONE form per material** (decided): each material keeps its vanilla chiseled
  wherever vanilla put it (stone → chiseled stone bricks, deepslate → chiseled deepslate,
  blackstone → chiseled polished blackstone); new ones are raw-line `chiseled_<material>`.
  **Chiseled tuff bricks stays a unique tuff quirk** — we do not replicate the double-chiseled
  anywhere else.
- **pillar**: the quartz/purpur-style axis column, for every material. Basalt's raw block is
  already columnar and counts as its own pillar.
- **polished mappings**: smooth_stone, smooth_sandstone, smooth_red_sandstone, smooth_quartz, and
  dark_prismarine each serve as their material's *polished*.

**Mossy axis — +8 forms** (wet-overworld materials only):

```
mossy_cobbled  mossy_cobbled_stairs  mossy_cobbled_slab  mossy_cobbled_wall
mossy_bricks   mossy_brick_stairs    mossy_brick_slab    mossy_brick_wall
```

Conversion recipes follow vanilla (block + vine / moss block). Nether, end, ocean, and dry
materials don't moss.

### The material axis

**Tier 1 — full core kit** (13 materials). Includes the sandstones (decided): they get cobbled
variants like every other hard stone.

| Material | Core missing | Mossy missing | Notes |
|---|---|---|---|
| stone | 4 | 0 | has all mossy already |
| deepslate | 4 | 8 | tiles stay a deepslate one-off |
| granite | 12 | 8 | |
| diorite | 12 | 8 | |
| andesite | 12 | 8 | |
| tuff | 6 | 8 | keeps chiseled tuff bricks as its quirk |
| calcite | 18 | 8 | vanilla gave it nothing |
| blackstone | 5 | — | nether: no mossy |
| basalt | 16 | — | nether: no mossy; raw is its pillar |
| end_stone | 14 | — | end: no mossy |
| dripstone | 18 | 8 | wet caves: mossy applies |
| sandstone | 11 | — | dry: no mossy |
| red_sandstone | 11 | — | dry: no mossy |
| cinnabar | 6 | — | 26.2 stone; volcanic-dry: no mossy; near-complete vanilla kit |
| sulfur | 6 | — | 26.2 stone; volcanic-dry; potent sulfur + sulfur spike stay one-offs |
| **Subtotal** | **155** | **56** | |

**Tier 2 — reduced kit, 15 forms** (no cobbled ×4, no mossy): crafted/dimensional masonry whose
raw drops itself.

| Material | Missing | Notes |
|---|---|---|
| netherrack | 8 | bricks chain via the nether-brick item |
| quartz | 6 | |
| prismarine | 5 | dark prismarine = its polished |
| purpur | 11 | |
| packed_mud | 10 | mud_bricks = its bricks |
| **Subtotal** | **40** | |

**Excluded** (reasons): obsidian (tool-tier/portal semantics) · amethyst (budding mechanics) ·
bone block (organic) · terracotta (the 16-color axis is its own future project) · sculk, magma,
glowstone, coral, ices (mechanics/function blocks).

**Vanilla one-offs stay one-offs**: deepslate tiles (+forms), cut sandstone (+slab), smooth
basalt, chiseled tuff bricks, red nether bricks, gilded blackstone, reinforced deepslate, mossy
one-offs already counted. They keep working, join no group, generate no counterparts.

## Totals

| Scope | New blocks |
|---|---|
| Tier-1 core kit (15 materials) | 155 |
| + mossy axis | 211 |
| + tier-2 | **251** |

## Resolved decisions

1. **Loot normalization: YES** (decided). Mining any tier-1 raw stone drops its **cobbled** form;
   silk touch restores raw — exactly like stone/cobblestone and deepslate. Applies to granite,
   diorite, andesite, tuff, calcite, dripstone, end stone, **and both sandstones**. Cobbled→raw
   smelting closes the loop.
2. **Chiseled: single form**, tuff's double is a quirk (decided — see form axis).
3. **Sandstones: tier 1** with cobbled variants (decided).
4. **Textures: ship everything programmatic** (decided). No block waits on art — see below.

## Textures: default-first, art-debt for the rest

**Every one of the 239 blocks ships with generated textures.** Only **~80 base textures** are new
(stairs/slabs/walls/pillar-body reuse their base block's art); the rest is model reuse. Two new
generator modes in `scripts/`:

- **Structure transfer**: a vanilla pattern donor's luminance structure (`cobblestone.png`,
  `stone_bricks.png`, `cracked_stone_bricks.png`, `chiseled_…`, pillar side/top) re-rendered
  through the target material's palette by luminance rank. This is how cobbled calcite, granite
  bricks, etc. are made.
- **Moss-mask transfer**: the pixel-diff between `mossy_cobblestone` and `cobblestone` is the moss
  overlay — composited onto each material's cobbled and bricks textures.

The ~80 generated bases are logged in `ART_DEBT.md` as priority-3 debt (replace opportunistically,
worst offenders first). Nothing in this phase creates priority-1 art debt.

## Work breakdown

### S1 — Normalization (ships in three stages)

**S1a: tier-1 core kit (143)** → **S1b: mossy (+56)** → **S1c: tier-2 (+40)**. Each stage is
independently useful; families (S2) can land after S1a and grow as members appear.

- **Registration**: one `stoneKit()` helper (mirrors `station()`); `StairBlock`/`SlabBlock`/
  `WallBlock`/`RotatedPillarBlock` (AW for ctors as needed).
- **Assets**: standard model parents, all generated (stairs=3 models, slab=2, wall=3+item,
  pillar=axis) — ~600 model files full sweep; blockstates, item defs, ~80 base textures.
- **Recipes** per material: bricks 4→4, polished 4→4, chiseled (slabs), pillar (2 vertical),
  stairs 6→4, slabs 3→6, walls 6→6, cracked (smelt bricks), cobbled→raw smelting, mossy
  conversions; plus **full stonecutter parity** (~30 cuts/material) ≈ ~700 stonecutting recipes at
  full sweep. Total ≈ 1,100+ generated data files.
- **Loot/tags**: the normalization drops (decision 1) + self-drops for shaped forms;
  `mineable/pickaxe`; walls MUST join `#minecraft:walls` to connect, stairs/slabs likewise.
- **Creative tabs**: anchor-following insertion after each material's raw block (mechanism
  exists).
- **Tests**: kit-completeness registry probe (forms × materials), zero-data-load-error scan,
  stonecutter cut counts per input, wall connectivity, mossy conversions, silk-touch loot pairs.

### S2 — Stone families (the payoff)

- 27 groups (19 core + 8 mossy) + `stoneKey` + tags; canonical = stone's form.
- Everything else is **already built and generic**: pickup consolidation, transmute crafting,
  per-element furnaces (each cobbled smelts by its own recipe), **pile-aware stonecutter came
  free with the sawmill work**, comparator/hopper/crafter, recipe book, trades (canonical costs —
  mason trades take piles automatically), bundles.
- Tests mirror the wood suite: mixed-pile mining pickup, transmute grid, furnace smelt-down,
  stonecutter pile cut, mass-craft ratios.

### S3 — Extensions (optional)

Mason trade audit / stone-carpenter villager flavor, one-offs as single-member groups if pile
icons are wanted, terracotta color-axis project.

## Estimate (full sweep)

| | |
|---|---|
| New blocks | **239** (143 minimum viable = S1a) |
| New base textures | ~80, all generated (see ART_DEBT.md) |
| Generated asset/data files | ~2,000 |
| New variant groups | 27 (→ ~69 total) |
| New code | `stoneKit()`, `stoneKey`, two texture-generator modes, loot overrides — **no new mixins expected** (every interaction hook is already form-agnostic) |
