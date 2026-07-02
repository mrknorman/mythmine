# Stone Phase — Normalization + Families

**GEOLOGY OVERHAUL (worldgen) — core SHIPPED (2026-07-02).** Regional base stones via a
surface-rule override of `noise_settings/overworld.json` (this makes mythstack a geology overhaul —
incompatible with other terrain mods; new chunks only). The rule sequence: vanilla bedrock/surface/
sulfur-caves | ICE band (snowy_plains/ice_spikes/snowy_taiga: permafrost under the topsoil, packed
ice to y32) | SAND bands (sandstone under deserts, red under badlands, to y32) | TUFF regions
(deepslate band, own dithered gradient) | vanilla deepslate | STONE REGIONS — noise-banded
granite/diorite/andesite/calcite with shale (stone) as the fallthrough default. Region noise
`mythstack:stone_regions` (firstOctave -10) ≈ few-hundred-block regions. Vanilla granite/diorite/
andesite/tuff blob features removed (BiomeModifications). New block: PERMAFROST (frozen ground,
shovel, dirt/ice blend texture). Verified headlessly: 24 far chunks sampled at y=40 show all five
region stones and nothing outside the expected base-terrain set.
PHASE COMPLETE (2026-07-02): 40 per-material ore variants (granite/diorite/andesite/calcite/tuff
x 8 ores) with ore-overlay textures, per-variant loot/smelting/blasting, tool-tier tags, and
block-match targets prepended to all 16 vanilla ore features — veins in a region place the
region's ore, and families grow via the vanilla ore tags (piles/smelting inherit). Blue ice veins
seed the tundra ice band. The SHALE rename cascade is in (31 vanilla blocks incl. infested
variants + our four stone-material blocks; ids untouched; generic items like Stone Pickaxe and
Stonecutter keep their names). Region thresholds tuned (shale band narrowed to [-0.10, 0.10]).
Pickup-merge design note: AUTO piles absorb family pickups; MANUAL (drag-merged) piles are
curated and deliberately untouched — covered by tests now.

**Functional forms (2026-07-02, post-S2): +78 blocks — buttons + pressure plates for every
material (vanilla placements kept: stone's and blackstone's polished pair stay canonical members),
and furnaces / pistons / sticky pistons for every cobbled-bearing material (their bodies are
cobbled). Nether furnaces (blackstone, basalt) burn SOUL-BLUE. Typed furnaces share the vanilla
furnace block entity (widened validBlocks; BE-backed menus have no block trap). The S2
button/plate/piston acceptance overrides reverted — those outputs are per-material families now,
so the transmuter owns mixed grids (a mixed-cobbled piston grid crafts the majority material's
piston); the furnace recipe pins to exact cobblestone so per-material recipes don't double-match.
Cross-family transmute fix: substitution keeps other-family slots as-is (planks in a piston grid)
instead of failing — same-family gaps still fail (bamboo can't fake hyphae). 329 stone blocks,
74 groups, 929 items.**

**Status: PHASE COMPLETE (2026-07-02) — S1 normalization (251 blocks) AND S2 families shipped.
844 items across 69 groups; all interactions live for stone: piles on pickup, transmute crafting
(mixed polished 2x2 -> majority-material bricks), per-element furnaces, the pile-aware
stonecutter, and family recipe ACCEPTANCE — any cobbled works in every cobbled material-cost
recipe (piston/lever/dispenser/stone tools/furnaces...), any raw in every stone one
(repeater/comparator/stonecutter/buttons...), same for polished/chiseled/slab forms and the
armor-trim template duplications. Protected material-identity one-offs (never family-craftable):
potent sulfur, cut sandstones, deepslate tiles, chiseled tuff bricks, nether brick fence.**

S2 implementation notes: stone identity is an EXACT item->material map from the kit naming tables
(no substring matching — "sandstone" can never collide with "stone"), hooked into
`VariantGroups.keyFor` after the canonical branch; canonicals key as "oak" like wood's, so the
transmuter's canonical normalization needed ZERO changes. Stone's own chain exceptions are
completed by two added recipes (4 smooth stone -> stone bricks, 2 stone slabs -> chiseled stone
bricks) so mixed-material grids normalize. Group tags live at `tags/item/stone/<form>`;
`stone_tool_materials`/`stone_crafting_materials` gained the 13 new cobbled forms.

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

**Resin — a PARTIAL material** (2026-07-02): its brick line + chiseled join the form groups
(piles, transmute identity "resin", stonecutter via groups) but the kit never gap-fills it — no
cobbled/cracked/pillar/polished resin, and resin_block deliberately stays OUT of the raw group so
it can't satisfy any-raw-stone recipes (repeaters from amber would be silly). The partial-material
pattern is now established for future semi-stones.

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
