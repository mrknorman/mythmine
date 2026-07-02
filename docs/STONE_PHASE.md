# Stone Phase — Normalization + Families (scope)

Goal: bring the pile/family system to stone — and unlike wood, **normalize vanilla first**. Vanilla
stone coverage is gap-ridden (granite has polished but no bricks; calcite has *nothing*; deepslate
has cobbled stairs but no raw stairs). We fill every material to the same form kit so the family
layer needs no exception table, then group by form across materials.

Counts below are exact — probed against the 26.2 client jar (2026-07-02).

## The form kit (18 forms per material)

```
raw          raw_stairs        raw_slab        raw_wall
cobbled      cobbled_stairs    cobbled_slab    cobbled_wall
polished     polished_stairs   polished_slab   polished_wall
bricks       brick_stairs      brick_slab      brick_wall
cracked_bricks               chiseled
```

**Grouping rule (decided):** groups are per *fine-grained form* across materials — all materials'
`raw_stairs` are one group, all `brick_stairs` are another; brick stairs never group with plain
stairs. 18 forms → **18 new variant groups**, members = the 11 materials' version of that form,
canonical = **stone's** version of the form (stone, cobblestone, stone bricks, stone brick
stairs, …).

Material identity (`stoneKey`, the analog of `woodKey`) spans groups so transmute output voting
works across forms, exactly like wood.

## Materials and their gaps

| Material | Missing forms | New blocks |
|---|---|---|
| stone | raw_wall, polished_stairs, polished_wall | 3 |
| deepslate | raw_stairs, raw_slab, raw_wall | 3 |
| granite | cobbled ×4, polished_wall, bricks ×4, cracked, chiseled | 11 |
| diorite | (same as granite) | 11 |
| andesite | (same as granite) | 11 |
| tuff | cobbled ×4, cracked_bricks | 5 |
| calcite | everything but raw | 17 |
| blackstone | cobbled ×4 | 4 |
| basalt | raw s/s/w, cobbled ×4, polished s/s/w, bricks ×4, cracked, chiseled | 16 |
| end_stone | raw s/s/w, cobbled ×4, polished ×4, cracked, chiseled | 13 |
| dripstone | everything but raw | 17 |
| **Total** | | **111** |

Mapping notes: `smooth_stone` serves as stone's *polished* (so only its stairs/wall are new);
`polished_blackstone_bricks` serve as blackstone's *bricks*; `chiseled_polished_blackstone` as its
*chiseled*; basalt's polished exists as a pillar block (its stairs/slab/wall are new).

### Decisions to settle before implementation

1. **Dripstone in or out?** (−17 blocks if out). It is a natural raw stone; thematically it's
   "drippy" rather than masonry. Recommendation: **in** — the whole point is no exceptions.
2. **Cobbled blackstone** (−4 if out): vanilla treats raw blackstone as its own cobble (it
   substitutes for cobblestone in recipes). Filling it anyway keeps the grid clean.
   Recommendation: **in**, with raw-drops-raw loot (see 3) as blackstone already behaves that way.
3. **Loot normalization — the real gameplay change.** Parity means mining raw granite/calcite/…
   drops the **cobbled** form (silk touch → raw), like stone→cobblestone and
   deepslate→cobbled deepslate. This changes established drops for 6+ materials. Alternative: keep
   vanilla drops and let cobbled forms come only from the stonecutter/crafting. Recommendation:
   **normalize the loot** — it is the coherent version and cobbled→raw smelting restores parity.
4. **Deferred materials:** sandstone/red sandstone (sand physics + decent vanilla parity already),
   netherrack/nether bricks (brick-item chain, not block-cut), prismarine, purpur, mud bricks.
   Phase S3 candidates.
5. **Vanilla one-offs stay one-offs:** deepslate tiles (+stairs/slab/wall/cracked), mossy
   cobblestone/stone bricks (+forms), smooth basalt, chiseled tuff bricks, gilded blackstone,
   reinforced deepslate. They keep working, join no group (or single-member groups where a pile
   icon is wanted), and generate no counterparts.

## Work breakdown

### S1 — Normalization (the 111 blocks, no families yet)

- **Registration**: one `stoneKit()` helper in ModBlocks (mirrors `station()`); stairs/slabs/walls
  via vanilla `StairBlock`/`SlabBlock`/`WallBlock` (AW for constructors as needed).
- **Textures (~33 new base textures only)** — stairs/slabs/walls reuse base textures; new
  generator mode *structure transfer*: pattern donor's luminance structure (vanilla
  `cobblestone`, `stone_bricks`, `cracked_stone_bricks`, `chiseled_stone_bricks`, `polished_*`)
  re-rendered through the target material's palette by luminance rank.
- **Models/blockstates**: standard parents; ~280 model files, all generated (stairs=3 models,
  slab=2, wall=3+item).
- **Recipes**: per material — bricks (4→4), polished (4→4), chiseled (slabs), stairs (6→4), slabs
  (3→6), walls (6→6), cracked (smelt bricks), cobbled→raw smelting; plus **full stonecutter
  parity** (every descendant form cuttable from raw/cobbled/bricks, ~25 cuts/material ≈ **~300
  stonecutting recipes**). Total ≈ 450–500 generated data files.
- **Loot/tags**: self-drops + the raw→cobbled mining change (decision 3); `mineable/pickaxe`,
  `walls`, `stairs`, `slabs` block+item tags (walls MUST be in `#minecraft:walls` to connect).
- **Creative tabs**: anchor-following insertion after each material's raw block (mechanism exists).
- **Tests**: kit-completeness check (18×11 registry probe), recipe/loot data-load zero-error scan,
  stonecutter cut counts per input, wall-connectivity tag check.

### S2 — Stone families (the payoff)

- 18 groups + `stoneKey` + tags; membership rebuild.
- Everything else is **already built and generic**: pickup consolidation, transmute crafting
  (mixed cobble grid → majority-material output), per-element furnaces (each cobbled smelts by its
  own recipe), **pile-aware stonecutter came free with the sawmill work** (piles cut by active
  material day one), comparators/hoppers/crafter, recipe book, trades (canonical costs), bundles.
- Tests: mirror the wood suite — mixed-pile pickup while mining, transmute grid, furnace
  smelt-down, stonecutter pile cut, mass-craft ratios.

### S3 — Extensions (optional, later)

Deferred materials (decision 4), mason villager trade audit (mason already buys/sells stone —
family costs make piles payable automatically), tiles/mossy as single-member groups if pile icons
are wanted.

## Estimate

| | |
|---|---|
| New blocks | **111** (94 if dripstone + cobbled blackstone are cut) |
| New base textures | ~33 (programmatic structure-transfer; see ART_DEBT.md) |
| Generated asset/data files | ~900–1000 |
| New variant groups | 18 (→ ~60 total) |
| New code | `stoneKit()` registration, `stoneKey`, texture-generator mode, loot overrides — **no new mixins expected** (every interaction hook is already form-agnostic) |

Sizing: bigger data batch than the 9-station batch (111 blocks vs 99, plus the recipe explosion),
but *less* code risk than the sawmill (no new menus/sync). The dominant cost is generation scripts
+ the loot-normalization decision.
