# Stone Phase — Normalization + Families (scope)

Goal: bring the pile/family system to stone — and unlike wood, **normalize vanilla first**. Vanilla
stone coverage is gap-ridden and inconsistent (granite has polished but no bricks; calcite has
*nothing*; deepslate has cobbled stairs but no raw stairs; chiseled exists as *chiseled tuff* on
one material and *chiseled stone bricks* on another; only stone has mossy; only basalt/quartz/
purpur have pillars). We fill every material to the same form kit so the family layer needs no
exception table, then group by form across materials.

Counts are exact — probed against the 26.2 client jar (2026-07-02, second pass incl. pillars,
the chiseled split, mossy, and the tier-2 material sweep).

## The form kit

**Core kit — 20 forms** (every hard masonry stone):

```
raw          raw_stairs        raw_slab        raw_wall
cobbled      cobbled_stairs    cobbled_slab    cobbled_wall
polished     polished_stairs   polished_slab   polished_wall
bricks       brick_stairs      brick_slab      brick_wall
cracked_bricks    chiseled     chiseled_bricks    pillar
```

- **chiseled is two forms** (vanilla is inconsistent): `chiseled` (raw/polished line — chiseled
  tuff, chiseled deepslate) and `chiseled_bricks` (chiseled stone bricks, chiseled tuff bricks).
  Every material gets both.
- **pillar**: the quartz/purpur-style axis column, for every material. Basalt's raw block IS
  columnar and counts as its own pillar.

**Mossy axis — +8 forms** (wet-overworld materials only):

```
mossy_cobbled  mossy_cobbled_stairs  mossy_cobbled_slab  mossy_cobbled_wall
mossy_bricks   mossy_brick_stairs    mossy_brick_slab    mossy_brick_wall
```

Mossy conversion recipes follow vanilla (block + vine / moss block). Nether, end, ocean, and dry
materials don't moss.

**Grouping rule (decided):** groups are per *fine-grained form* across materials — all `raw_stairs`
are one group, all `brick_stairs` another, `mossy_brick_stairs` a third; brick stairs never group
with plain stairs. 28 forms → **28 new variant groups**, canonical = **stone's** version of each
form. `stoneKey` (the `woodKey` analog) spans groups for transmute voting.

## Material census

### Tier 1 — hard masonry stones (full core kit; mossy where wet-overworld)

| Material | Core missing | Mossy missing | Notes |
|---|---|---|---|
| stone | 5 | 0 | smooth_stone = its polished; has all mossy already |
| deepslate | 5 | 8 | tiles stay a deepslate one-off |
| granite | 13 | 8 | |
| diorite | 13 | 8 | |
| andesite | 13 | 8 | |
| tuff | 6 | 8 | |
| calcite | 19 | 8 | vanilla gave it nothing |
| blackstone | 6 | — | nether: no mossy; polished_blackstone_bricks = its bricks |
| basalt | 17 | — | nether: no mossy; raw is its own pillar |
| end_stone | 15 | — | end: no mossy |
| dripstone | 19 | 8 | wet caves: mossy applies |
| **Subtotal** | **131** | **56** | |

### Tier 2 — soft / self-dropping / dimensional masonry (reduced kit: no cobbled, no mossy)

These drop themselves when mined (or are crafted materials), so the cobbled axis doesn't apply —
that's the principled rule, not an exception list: *cobbled exists exactly where mining raw drops
rubble*.

| Material | Missing | Notes |
|---|---|---|
| sandstone | 8 | smooth = polished; cut sandstone stays a one-off |
| red_sandstone | 8 | same mapping |
| netherrack | 9 | bricks chain via nether-brick item (recipes differ, forms don't) |
| quartz | 7 | quartz_stairs/slab exist under short names; smooth = polished |
| prismarine | 6 | **dark prismarine = its polished** (incl. stairs/slab) |
| purpur | 12 | purpur_stairs/slab exist; pillar exists |
| packed_mud | 11 | mud_bricks = its bricks |
| **Subtotal** | **61** | |

### Excluded (with reasons)

obsidian (tool-tier/portal semantics, no masonry identity) · amethyst (budding mechanics) ·
bone block (organic pillar) · terracotta (the 16-color axis is its own project) · sculk (mechanics)
· magma/glowstone (function blocks) · coral (dies) · packed ice/snow (melting) · smooth basalt,
cut sandstone, dark prismarine*, red nether bricks, mossy one-offs, gilded blackstone, reinforced
deepslate (vanilla one-offs: keep working, join no group, generate no counterparts — *except dark
prismarine, which maps to polished).

## Totals

| Scope | New blocks |
|---|---|
| Tier 1 core kit | 131 |
| + mossy (wet overworld) | +56 → 187 |
| + tier 2 | +61 → **248** |

Staged shipping: **S1a** tier-1 core kit → **S1b** mossy → **S1c** tier-2 → **S2** families.
Each stage is independently useful; families (S2) can land after S1a and simply grow as members
appear.

## Work breakdown

### S1 — Normalization

- **Registration**: one `stoneKit()` helper (mirrors `station()`); `StairBlock`/`SlabBlock`/
  `WallBlock`/`RotatedPillarBlock` (AW for ctors as needed).
- **Textures (~85 new base textures)** — stairs/slabs/walls reuse base textures. New generator
  mode *structure transfer*: pattern donor's luminance structure (vanilla `cobblestone`,
  `stone_bricks`, `cracked_…`, `chiseled_…`, pillar side/top) re-rendered through the target
  material's palette by luminance rank. **Mossy overlay transfer**: the pixel-diff between
  `mossy_cobblestone` and `cobblestone` is the moss mask — composite it onto each material's
  cobbled/bricks.
- **Models/blockstates**: standard parents, all generated (stairs=3 models, slab=2, wall=3+item,
  pillar=axis rotations); ~600 model files across the full sweep.
- **Recipes** per material: bricks 4→4, polished 4→4, chiseled (slabs), pillar (2 vertical), stairs
  6→4, slabs 3→6, walls 6→6, cracked (smelt bricks), cobbled→raw smelting, mossy conversions;
  plus **full stonecutter parity** (~30 cuts/material) ≈ **~700 stonecutting recipes** at full
  sweep. Total ≈ 1,100+ generated data files.
- **Loot/tags**: self-drops; tier-1 raw drops cobbled + silk touch restores (the gameplay
  decision, see below); `mineable/pickaxe`, `walls`, `stairs`, `slabs` block+item tags (walls MUST
  join `#minecraft:walls` to connect).
- **Creative tabs**: anchor-following after each material's raw block (mechanism exists).
- **Tests**: kit-completeness registry probe (28×materials), zero-data-error scan, stonecutter cut
  counts, wall-connectivity, mossy conversion crafts.

### S2 — Stone families (the payoff)

- 28 groups + `stoneKey` + tags; membership rebuild; canonical = stone's form.
- Everything else is **already built and generic**: pickup consolidation, transmute crafting,
  per-element furnaces (each cobbled smelts by its own recipe), **pile-aware stonecutter came free
  with the sawmill work**, comparator/hopper/crafter, recipe book, trades (canonical costs —
  mason trades take piles automatically), bundles.
- Tests mirror the wood suite: mixed-pile mining pickup, transmute grid, furnace smelt-down,
  stonecutter pile cut, mass-craft ratios.

### S3 — Extensions (optional)

Mason trade audit / stone-mason villager flavor, tiles/mossy one-offs as single-member groups if
pile icons are wanted, terracotta as its own future color-axis project.

## Decisions to settle before implementation

1. **Loot normalization** (recommended: yes) — tier-1 raw stones drop their cobbled form, silk
   touch restores raw (parity with stone/deepslate). Changes established drops for granite,
   diorite, andesite, tuff, calcite, dripstone, end stone.
2. **Mossy scope** (recommended: wet-overworld only, +56) vs everywhere (+80) vs skip (0).
3. **Tier 2 in the first pass?** (recommended: ship S1a first, tier-2 as S1c follow-up).
4. **Blackstone cobbled** (recommended: in, +4 already counted) — vs treating raw as its cobble.

## Estimate (full sweep)

| | |
|---|---|
| New blocks | **248** (131 minimum viable) |
| New base textures | ~85 (programmatic; see ART_DEBT.md) |
| Generated asset/data files | ~2,000 |
| New variant groups | 28 (→ ~70 total) |
| New code | `stoneKit()`, `stoneKey`, structure-transfer + moss-mask generator modes, loot overrides — **no new mixins expected** (every interaction hook is already form-agnostic) |
