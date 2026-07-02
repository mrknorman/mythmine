# Family Stacking (`mythstack`) — Implementation Plan

**Status:** approved for build · **Scope:** wood-only MVP · **Audience:** a developer
picking this up cold.

This document is self-contained: it tells you what to build, in what order, how the
pieces fit, and how to know each piece is done. The design *rationale* lives in
[`DESIGN_SPEC.md`](./DESIGN_SPEC.md) (referenced below as "§N"). Read this plan first,
then skim the spec.

---

## Table of contents

1. [What we're building (one screen)](#1-what-were-building-one-screen)
2. [Locked decisions](#2-locked-decisions)
3. [The one invariant that makes it all work](#3-the-one-invariant)
4. [Target & toolchain](#4-target--toolchain)
5. [Getting started (day one)](#5-getting-started-day-one)
6. [Architecture](#6-architecture)
   - 6.1 [The "ghost" model: a pile is a real stack + an overlay](#61-the-ghost-model)
   - 6.2 [Data model: the `VariantPile` component](#62-data-model-the-variantpile-component)
   - 6.3 [Canonical packing (the core algorithm)](#63-canonical-packing-the-core-algorithm)
   - 6.4 [Boundary operations & the sum==count invariant](#64-boundary-operations)
   - 6.5 [Interaction hooks: what's free, what needs a mixin](#65-interaction-hooks)
   - 6.6 [Package & class layout](#66-package--class-layout)
7. [The wood variant groups (MVP data)](#7-the-wood-variant-groups-mvp-data)
8. [Build phases](#8-build-phases)
9. [Testing strategy](#9-testing-strategy)
10. [Risk register](#10-risk-register)
11. [Out of scope / deferred](#11-out-of-scope--deferred)
12. [References](#12-references)
13. [Glossary](#13-glossary)

---

## 1. What we're building (one screen)

Cosmetic variant groups (the wood types) auto-combine into a single **pile** so they
stop eating inventory slots. A pile of mixed wood behaves *exactly* like an ordinary
stack for transport, comparators, crafting, and smelting — but you can open it
bundle-style (hover → scroll → right-click) to pull a specific variant back out.

The trick that makes this cheap: **a mixed pile is literally a vanilla stack of the
variant group's canonical item (e.g. `oak_log ×56`) wearing an invisible component that
records the true composition** (`{oak:6, birch:50}`). To the game it *is* 56 oak logs,
so comparators, hoppers, furnaces, and crafting tables all "just work" with zero special
code. Our mod only intervenes at the **boundaries** — when a pile is split, combined,
displayed, or picked up.

MVP is **wood only** and ends at phase 9 — **all phases complete**. Nether wood (crimson/warped)
and bamboo are **in** (D4 reversed — see §2); typed sticks are in (phase A — creation + acceptance;
propagation into fence/gate/sign outputs is the remaining §13 work). Stone (§12) is post-MVP.

### The guiding tenet — inventory tends toward order through use

> **Piles exist so the normal act of playing leaves the inventory _more_ organised, not less.**
> Picking things up, crafting, placing — each should passively reduce clutter as a *side effect* of
> what the player was already doing, never add to it. Auto-grouping a pickup, auto-expanding an
> overflowing wood back into clean full stacks, compressing scattered offcuts into a pile — these are
> not "sort" commands the player has to remember to run; they happen on their own, as a consequence of
> use. The inventory should drift toward tidiness by itself.

Every pile feature is judged against this: **does it reduce entropy on use?** A feature that lets
disorder accumulate, or that forces the player to manually tidy, is working against the grain. The one
thing the system must respect is the difference between what the player **curated on purpose** (leave it
alone) and what merely **accumulated** (free to reorganise) — which is exactly what the `manual` flag on
a pile records (§6.2).

---

## 2. Locked decisions

| # | Decision | Choice | Why |
|---|----------|--------|-----|
| Loader | Mod loader | **Fabric** | QoL ecosystem; mixins cover our hot-path hooks |
| MC | Minecraft version | **Java 26.2** ("Chaos Cubed", 2026-06-16) | Latest stable; calendar versioning replaced `1.x` |
| ID | Mod id / namespace | **`mythstack`** | Per spec (repo is `mythmine`; mod id may differ — that's fine) |
| D1 | Carrier representation | **Hosted-on-canonical** — a pile is a real stack of the canonical member + a `VariantPile` overlay component. No new item types. | "Behaves identically to a stack except at the boundary." Vanilla does comparator/craft/smelt/transport for free. |
| D2 | VariantGroup granularity | **One variant group per wood *form*** (logs, planks, stairs, slabs, fences, …), each keyed by wood type, canonical = the `oak` member. | Matches the natural product line; the engine is written once and forms are declared as data. |
| D3 | Crafting a mix | **REVERSED by the §7 crafting redesign.** Ratio-preserving transmute: pile-in → pile-out in the input woods' ratio (shift-click mass); single takes are per-slot, output = majority/first-placed *productive* wood. No mixed *items* ever — outputs are definite per-wood items. | The invariant in §3 (mixedness is stack-level only) still holds; only the "first craft canonicalizes" cost was removed. |
| D4 | Nether & bamboo | **REVERSED — they're in.** All 12 vanilla woods are family members; `bamboo_block` takes the log slot. | The gaps are handled per-layer: a wood with no product for a recipe (no crimson/warped boat) is *unproductive* — consumable in mixed crafts but never the output (attribution falls to the runner-up wood); bamboo-only forms (mosaic) stay pure-bamboo because no canonical/oak recipe exists to transmute against; nether sticks aren't furnace fuel; and furnaces are pile-aware per element (phase 8 — piles burn/smelt down to their ineligible remainders). |

**Rejected alternative (don't do this, but know why):** a *distinct* carrier item
(`mythstack:wood_pile`). It avoids mixing into vanilla `Item`, but then you must
re-implement comparator output, hopper transport, crafting acceptance, and smelting by
hand. Net more code. Only revisit if the `VariantPile`-on-vanilla-item approach proves too
leaky in practice (see §10).

---

## 3. The one invariant

> **Mixedness is a stack-level property only.** Every individual *item* is a definite
> variant. Only a multi-item *stack* (count ≥ 2 with ≥ 2 distinct variants) can be a mix.
> Crafting and smelting consume from the stack and always emit definite, **canonical**
> items — they never produce or preserve a mix. Mixing arises **solely** from inventory
> aggregation (pickup / merge / normalize), never from crafting.

Everything else follows from this:

- A pile crafts to the canonical product (§7 of spec) **for free**, because the host
  stack *is* canonical (`oak_log`) and vanilla already outputs `oak_planks`.
- A pile smelts to charcoal **for free** — vanilla smelts the host `oak_log`.
- The rule is **self-enforcing**: vanilla never writes our component, so vanilla can
  never create an illegal "mixed item." Only our combine/normalize code mints a mix.
- ~~The cost we accept: the first craft canonicalizes the rainbow permanently.~~ **Superseded
  by the §7 crafting redesign:** the transmute layer plans ratio-preserving per-wood outputs
  *before* vanilla sees the grid, so a mix crafts into a mix-of-definite-products instead of
  all-oak. The invariant above still holds — no mixed *item* is ever produced.

---

## 4. Target & toolchain

> ✅ **Verified** against Fabric's `26.2` example-mod branch and a green build (phases 1–2).

| Tool | Version |
|------|---------|
| Minecraft | `26.2` |
| Fabric Loom | `1.17-SNAPSHOT` |
| Gradle | `9.5.1` |
| Fabric Loader | `0.19.3` |
| Fabric API | `0.153.0+26.2` |
| JDK | **25** (26.2 requires Java 25 — `release = 25`, `depends.java >=25`) |

**Important context — 26.2 is unobfuscated.** Since 26.1, Minecraft ships with official
(non-obfuscated) names, so there is **no Yarn/Mojmap remapping** — you compile against the
real class/method names. The flip side: nothing from before 26.1 works without
recompilation, so old tutorials and StackOverflow answers may reference obfuscated or
older APIs. When in doubt, read the actual decompiled/official 26.2 source.

**26.2 API gotchas to expect** (confirm exact signatures against source):
- Registration is split into `BlockIds` / `BlockItemIds` / `ItemIds` storage.
- GUI helpers moved off `Minecraft` onto `Gui`/`Hud` (e.g. `Minecraft.getInstance().gui.setScreen(...)`).
- Data components are mature (26.2 even ships new ones) — our component approach is standard ground.

---

## 5. Getting started (day one)

1. Install **JDK 25** (26.2 requires it). On macOS with Homebrew: `brew install openjdk@25` (keg-only, so prefix gradle with `JAVA_HOME=/opt/homebrew/opt/openjdk@25/libexec/openjdk.jdk/Contents/Home`). Verify `java -version`.
2. Generate a Fabric mod skeleton — either the official
   [example-mod template](https://github.com/FabricMC/fabric-example-mod) on the 26.2
   branch, or the [template generator](https://fabricmc.net/develop/template/). Set:
   - mod id: `mythstack`
   - package: `com.mythstack` (adjust the Maven group if you prefer)
3. Pin versions in `gradle.properties` (table in §4).
4. Confirm the loop works: `./gradlew runClient` launches a dev client; `./gradlew build`
   produces a jar. **This is phase 1's acceptance test.**
5. Read [`DESIGN_SPEC.md`](./DESIGN_SPEC.md) §1–§11 and §15.

Branch off `main` for each phase; keep mixins isolated and tag-gated (see §6.5). The
working tree is empty today — there is no existing code to integrate with.

---

## 6. Architecture

### 6.1 The "ghost" model

A mixed pile is a normal `ItemStack` whose:
- **item** = the variant group's declared **canonical** member (e.g. `oak_log`),
- **count** = the total number of items in the pile (1–64),
- and which carries a single extra **data component**, `mythstack:variant_pile`, recording
  the true composition.

Because the stack *is* a real stack of `oak_log ×N`, all of vanilla's stack behavior is
correct by construction: the count badge shows N, a comparator reads N/64, a hopper moves
it, a furnace smelts it, a crafting table consumes it as oak. **We write none of that.**

The component only exists **while a mix exists**. Combining births it; splitting or
collapsing to a single variant kills it (the stack becomes a plain vanilla stack again,
§3 single-variant collapse). Very ghost-like.

**Host-vs-contents rule (important, subtle):**
- While mixed, the **host item is *always* the variant group's declared canonical** (`oak_log`),
  even if the contents contain no oak. This is what guarantees deterministic canonical
  output (§7 of spec): a `{birch:30, spruce:30}` pile still crafts to *oak* planks.
- On **single-variant collapse**, the host item changes to the surviving variant
  (`oak_log` pile drained to only birch → becomes real `birch_log`).

### 6.2 Data model: the `VariantPile` component

One component type, reused by every variant group. Register it once (`ModComponents`).

```
VariantPile {
  contents:  List<Entry>      // ordered canonical-first; Entry = { item: Item, count: int }
  selected:  Optional<Item>   // the active wood (scroll-select / deposit) — stored as the wood, not an
                              // index, so it survives reorder/peel; empty = none. Preserved across reconcile.
}
```

Notes:
- Store `contents` as an **ordered list** (not a map) so peel order is deterministic and
  serialization is stable. Canonical order = the variant group's declared variant order (§7).
- The **variant group is derivable** from the host item (which variant group-tag it belongs to), so it
  need not be stored — but you may cache a `variant group` ResourceLocation for clarity/cheap
  lookup. Each item belongs to exactly one form-variant group, so this is unambiguous.
- Provide both a persistent **`Codec`** (NBT/JSON) and a **`StreamCodec`** (network sync —
  the component must sync to the client for tooltips/rendering).
- **Invariants** (assert in dev builds):
  - `sum(contents[*].count) == stack.getCount()`
  - `contents.size() >= 2` (a single-variant pile must have collapsed — never persists)
  - host item == variant group canonical (while the component is present)

### 6.3 Canonical packing (the core algorithm)

This is the algorithmic heart and the **most testable** code in the mod. It's pure —
`Map<Variant,int> pool → List<ItemStack>` — so unit-test it exhaustively with no
Minecraft runtime.

```
normalize(pool, variant group) -> List<ItemStack>:
    out = []
    # Step A: peel full 64s as PURE stacks, per variant, in canonical order
    for v in variant group.canonicalOrder(pool.keys):
        while pool[v] >= 64:
            out.add(pureStack(v, 64))
            pool[v] -= 64
    # Step B: pack the remainder (each pool[v] now in 0..63) into carriers of <=64,
    #         filling canonical-first
    remaining = [(v, pool[v]) for v in variant group.canonicalOrder if pool[v] > 0]
    while totalCount(remaining) > 0:
        group = take up to 64 items from `remaining`, canonical-first   # an ordered {v->n}
        if group has exactly 1 variant:
            out.add(pureStack(theVariant, group.total))                 # single-variant collapse (§3)
        else:
            out.add(carrierStack(host = variant group.canonical, contents = group))
        remove `group` from `remaining`
    return out
```

**Worked examples (turn these into test cases):**

| Input pool | Output |
|------------|--------|
| `30 oak + 30 birch` | one **60 carrier** `{oak:30, birch:30}` |
| `64 oak + 64 birch` | **two pure stacks** (`oak ×64`, `birch ×64`), no carrier |
| `70 oak + 50 birch` | one pure `oak ×64` + one **56 carrier** `{oak:6, birch:50}` |
| `50 oak + 50 birch` | one **64 carrier** `{oak:50, birch:14}` + pure `birch ×36` |
| pool reduced to only oak | plain `oak` stack(s) — no carrier |
| empty pool | `[]` (the pile is destroyed — §3 empty collapse) |

> 📌 **Spec refinement.** The spec (§6) says the partial remainder "stays as a single
> carrier," which is only true when the summed remainder ≤ 64. The `50 oak + 50 birch`
> row shows the general case (summed remainder 100 > 64): Step B packs into as few
> ≤64 stacks as possible. This generalization is intentional and correct — keep it.

### 6.4 Boundary operations

The ghost model's correctness rests on one invariant — **`sum(contents) == count`** —
maintained across three boundary operations:

**1. Combine (pickup / drag-merge / carrier↔carrier / double-click contract).**
Pool everything, run `normalize`, place the results. This is the *only* code that
*creates* a mix. For a **merge into a target slot with a 64 cap**: take from the source
only enough to fill the target to 64; pool that with the target and normalize (yields one
≤64 stack → target slot); the source keeps the remainder. ("Overflow stays on the cursor,"
§6.)

**2. Split (the boundary that needs a mixin).** When vanilla splits `n` off a pile
(hopper extract, taking half a stack, `Slot.remove`, etc.), vanilla copies the stack and
sets counts — which would duplicate the *whole* component into both halves. **Mixin
`ItemStack#split(int)`** so the split-off stack gets a *peeled* sub-component (n items,
canonical-first) and the remainder keeps the rest; each half then normalizes (a
single-variant half collapses to a plain stack). Result: a hopper draining a pile
**slowly de-mixes it, canonical-first** — an accepted, deterministic behavior.

**3. Consume / shrink (handled by lazy reconciliation, no mixin).** Furnaces and crafting
call `stack.shrink(n)`, which lowers count without producing a second stack. After a
shrink, `sum(contents) > count`. **Reconcile lazily at every read** through a single
choke-point accessor:

```
VariantPiles.read(stack):
    pile = stack.get(VARIANT_PILE)
    if pile == null: return null
    if sum(pile.contents) > stack.count:           # vanilla consumed some
        peel (sum - count) canonical-first, discard # deterministic given (contents, count)
        write the reconciled component back
    if sum(pile.contents) <  stack.count:           # should never happen on vanilla paths
        log + repair (pad canonical) defensively
    if pile.contents.size() < 2: collapse stack to plain vanilla stack
    return reconciled pile
```

Because the reconcile function is **deterministic given `(contents, count)`**, value-copies
and network-synced copies all reconcile identically — no desync. **All mod code reads
composition exclusively via `VariantPiles.read()`**; never touch the raw component.

> **Spike this first in phase 4.** The `split` mixin + lazy reconcile is the #1 technical
> risk. Prototype it and write the unit/gametests before building UI on top. If lazy
> reconcile proves leaky in practice, the fallback is a `setCount`/`shrink` mixin that
> reconciles eagerly (hotter path; early-out instantly when the component is absent).

### 6.5 Interaction hooks

Keep the mixin surface small and **tag-gated**: every mixin's first line must early-out
for non-variant group stacks (a component-presence or tag check) so vanilla items hit the vanilla
path untouched. This is what preserves muscle memory (§6/§10 of spec).

| Behavior | Spec | Mechanism | Mixin? |
|----------|------|-----------|:------:|
| Comparator N/64, hopper/dropper transport | §9 | Free — host is a real `oak_log ×N` stack | — |
| Crafting → canonical output | §7 | Free — vanilla consumes the host as oak | — |
| Smelting → charcoal | §8 | Free — vanilla smelts the host oak log | — |
| Tooltip breakdown (text) | §10 | Fabric `ItemTooltipCallback` | — |
| Scroll-select highlight | §10 | Fabric `ScreenMouseEvents` (client) + C2S "select" packet | — |
| Drag-merge (cursor member → same-variant group slot) | §6 | Mixin `Item#overrideStackedOnOther` / `overrideOtherStackedOnMe`, gated to same variant group | **M1** |
| Right-click extract selected variant | §10 | same `Item` override hooks (right-click branch) | **M1** |
| Split / peel (the boundary) | §3/§6 | Mixin `ItemStack#split(int)` — partition contents | **M2** |
| Double-click expand / contract | §10 | Mixin `AbstractContainerMenu#doClick` (double-click branch), gated to variant group | **M3** |
| Auto-group on pickup | §5 | Mixin `Inventory#add` — route variant group members into a pile | **M4** |
| Consume/shrink reconcile | §6.4 | Lazy reconcile at read (no mixin) | — |

**Four mixin classes total.** Because we host on vanilla items, the drag-merge/extract
behavior (M1) must be a mixin into vanilla `Item` rather than a clean subclass override —
that's the one extra cost of the hosted model, and it's cheap and contained.

> ⚠️ These exact class/method names (`overrideStackedOnOther`, `AbstractContainerMenu#doClick`,
> `Inventory#add`, `ItemStack#split`) are stable from the 1.21.x line but **must be
> verified against 26.2 source** — the GUI/registration refactors noted in §4 may have
> moved or renamed things.

### 6.6 Package & class layout

```
com.mythstack
├── MythStack.java                  // ModInitializer: register component, tags, variant groups, server packet handler
├── client/
│   └── MythStackClient.java        // ClientModInitializer: tooltip callback, scroll handler, keybind, client packet
├── variant/
│   ├── VariantGroup.java                 // record: id, formTag (TagKey<Item>), canonical (Item), canonical variant order
│   ├── VariantGroups.java         // item -> VariantGroup (tag lookup, cached); canonical lookup; declares all wood forms
│   ├── VariantPile.java             // the data component: record + Codec + StreamCodec
│   ├── VariantPiles.java            // read()/reconcile() choke-point + helpers (the ONLY way to read composition)
│   └── CanonicalPacking.java       // pure normalize() + pool/peel helpers  <-- unit-tested
├── interaction/
│   ├── CombineLogic.java           // pool + normalize + place (drag-merge, contract, pickup share this)
│   ├── ExtractLogic.java           // right-click extract selected variant
│   └── ExpandContractLogic.java    // double-click expand/contract (§10)
├── network/
│   └── SelectVariantPacket.java    // C2S: set `selected` index in the hovered pile's component
└── mixin/
    ├── ItemMixin.java              // M1: overrideStackedOnOther / overrideOtherStackedOnMe
    ├── ItemStackSplitMixin.java    // M2: split(int)
    ├── AbstractContainerMenuMixin.java // M3: doClick double-click branch
    └── InventoryAddMixin.java      // M4: add()

src/test/java/com/mythstack/variant/CanonicalPackingTest.java   // pure JUnit
src/main/resources/
├── fabric.mod.json                 // entrypoints (main + client), mixin config refs
├── mythstack.mixins.json
└── data/mythstack/tags/item/wood/<form>.json         // one tag per form (NOTE: "tags/item/" singular)
```

---

## 7. The wood variant groups (MVP data)

**Woods (12 — all vanilla, D4 reversed; bamboo lacks the wood/hyphae form):**
`oak, spruce, birch, jungle, acacia, dark_oak, mangrove, cherry, pale_oak`. **Canonical =
`oak`.** Variant order for deterministic peel = that list (oak first).

**Forms (each is a variant group).** The engine was proven on **`logs` + `planks`** first (the smeltable
and non-smeltable paths), then fanned out as pure data. **STATUS: implemented — 17 groups** (every form below plus `leaves`). The pile machinery is group-agnostic,
so each form is just a `VariantGroup` (tag + canonical) in `VariantGroups` + a pile-icon item-model
override. Manufactured forms reuse vanilla `#wooden_*`/family item tags (modded woods tag in for free); the
raw log forms use custom `#mythstack:wood/*` tags (vanilla `#*_logs` conflate log/wood/stripped). The pile
**cap is per-group** (`VariantGroup#cap` = the canonical's max stack size), so `signs`/`hanging_signs` pile
fine capped at 16. **`boats`/`chest_boats` are dropped permanently:** they stack to 1, so a pile (which
needs ≥ 2 of an item in one stack) can never form.

| Form | VariantGroup tag | Canonical | Notes |
|------|-----------|-----------|-------|
| logs | `#mythstack:wood/logs` | `oak_log` | smelts → charcoal; fuel |
| planks | `#mythstack:wood/planks` | `oak_planks` | — |
| stripped_logs | `…/stripped_logs` | `stripped_oak_log` | |
| wood | `…/wood` | `oak_wood` | 6-face log block |
| stripped_wood | `…/stripped_wood` | `stripped_oak_wood` | |
| stairs | `…/stairs` | `oak_stairs` | |
| slabs | `…/slabs` | `oak_slab` | |
| fences | `…/fences` | `oak_fence` | |
| fence_gates | `…/fence_gates` | `oak_fence_gate` | |
| doors | `…/doors` | `oak_door` | |
| trapdoors | `…/trapdoors` | `oak_trapdoor` | |
| pressure_plates | `…/pressure_plates` | `oak_pressure_plate` | |
| buttons | `#wooden_buttons` | `oak_button` | |
| shelves | `#wooden_shelves` | `oak_shelf` | new in 26.2 |
| leaves | `#minecraft:leaves` | `oak_leaves` | also folds in azalea / flowering-azalea |
| signs | `#signs` | `oak_sign` | per-group cap 16 |
| hanging_signs | `#hanging_signs` | `oak_hanging_sign` | per-group cap 16 |
| ~~boats / chest_boats~~ | — | — | **dropped** — stack to 1, can't ever pile |

A **tag file** lists the members, e.g. `data/mythstack/tags/item/wood/logs.json`:

```json
{ "values": ["minecraft:oak_log","minecraft:spruce_log","minecraft:birch_log",
             "minecraft:jungle_log","minecraft:acacia_log","minecraft:dark_oak_log",
             "minecraft:mangrove_log","minecraft:cherry_log","minecraft:pale_oak_log"] }
```

Tags are the **single source of truth** (§4). Modded woods that tag in are picked up for
free. The canonical map (`form → canonical item`) and variant order live in
`VariantGroups` (code) or a small data file — declare each form **once**.

> **Recipe parity (§3/§7) is mostly already done.** Vanilla ships every form for all nine
> woods, so step 3 is largely a *verification* pass, not a generation pass. Fill any gap
> via `RecipeProvider` datagen if one turns up.

---

## 8. Build phases

Each phase ships before the next. "DoD" = definition of done / acceptance test.

### Phase 1 — Scaffold
- **Goal:** working build/run loop.
- **Do:** generate the Fabric 26.2 skeleton (§5), register one throwaway debug item into a
  creative tab purely to prove registration works. *(The real mod adds no items — it adds a
  component — so this item is scaffolding you can delete later.)*
- **DoD:** `./gradlew runClient` launches; the debug item appears in the creative menu;
  `./gradlew build` produces a jar.

### Phase 2 — Tags & variant groups (no behavior)
- **Goal:** the variant group model exists and resolves.
- **Do:** `VariantGroup`, `VariantGroups`, the `logs` and `planks` tag files, canonical map,
  variant order. `VariantGroups.of(item)` returns the right variant group via tag lookup.
- **DoD:** a unit/dev test maps `birch_log → wood/logs` (canonical `oak_log`) and
  `oak_planks → wood/planks`; unknown items return none.

### Phase 3 — Recipe parity (verify)
- **Goal:** any wood member can craft its full product line (so canonical collapse is
  always legal).
- **Do:** verify all nine woods craft logs→planks→(stairs/slabs/fences/…); generate any
  missing recipe via `RecipeProvider`. (Expect ~nothing missing.)
- **DoD:** a checklist (or datagen diff) confirms parity across the nine woods for the
  forms in §7.

### Phase 4 — `VariantPile` component + canonical packing ⭐ (the core)
- **Goal:** piles exist, pack correctly, and survive the split/consume boundaries.
- **Do:**
  - Register the `mythstack:variant_pile` component (Codec + StreamCodec) — §6.2.
  - Implement `CanonicalPacking.normalize()` — §6.3 — **pure, fully unit-tested** against
    the worked-example table.
  - Implement `VariantPiles.read()` lazy reconcile — §6.4.
  - **Mixin M2** (`ItemStack#split`) — peel/partition. **Spike this before anything else.**
  - **Mixin M1** (`Item#overrideStackedOnOther/overrideOtherStackedOnMe`) for **drag-merge**
    only (so you can build piles manually — no auto-pickup yet).
  - Single-variant collapse + empty collapse.
- **DoD:** unit tests green for every §6.3 example; in dev, drag a `birch_log` stack onto an
  `oak_log` stack → one mixed `oak_log`-hosted pile of the summed count; a comparator on a
  chest with that pile reads N/64; splitting one item off yields a plain `oak_log` and a
  shrunken pile with `sum==count`.

### Phase 5 — Separation UI
- **Goal:** open a pile bundle-style and pull a variant out.
- **Do:**
  - Tooltip breakdown (text) via `ItemTooltipCallback`: each variant + count + total.
  - Scroll-select: `ScreenMouseEvents` cycles a highlighted variant; send `SelectVariantPacket`
    (C2S) to persist `selected` server-side.
  - Right-click extract (M1 right-click branch): one unit of the selected variant to the
    cursor as a **plain vanilla stack**; pile reconciles/collapses.
  - **Mixin M3** (`AbstractContainerMenu#doClick`, double-click branch): expand (pile →
    plain stacks into inventory, overflow drops) / contract (sweep variant group members in the
    inventory → normalize → result lands on the **clicked slot**). No-op contract falls back
    to vanilla double-click-gather (§10).
- **DoD:** hover shows the breakdown; scroll moves the highlight; right-click pulls the
  selected variant; double-click a pure stack contracts, double-click a pile expands;
  round-trip is exact when variant group total ≤ 64 and the *minimal* set when > 64 (§10).

### Phase 6 — Auto-group on pickup
- **Goal:** picking up a variant group member merges into an existing/new pile.
- **Do:** **Mixin M4** (`Inventory#add`): if the picked-up item is a variant group member, route it
  into a matching pile (or create one), respecting the 64 cap; overflow spills to a second
  pile or a normal slot. Tag lookup only — keep it off the per-tick path.
- **DoD:** walking over mixed logs builds a single pile; a full pile (64) overflows to a
  second; non-variant group items are unaffected.

### Phase 7 — Crafting default (verify)
- **Goal:** confirm a mixed pile crafts to canonical.
- **Do:** mostly **verification** — the host is canonical, so vanilla already outputs oak.
  Confirm the crafting grid consuming a pile decrements it via the reconcile path and leaves
  a valid (re-normalized) remainder. Cover the **1.21 crafter block** too (§9).
- **DoD:** a mixed log pile in a crafting table yields oak planks; the remainder pile stays
  consistent; the crafter block does the same deterministically.

### Phase 8 — Smelting — DONE (pile-aware furnaces)
- **What shipped:** furnaces consume piles **per element** (`PileFurnace` +
  `AbstractFurnaceBlockEntityMixin`, covering smoker/blast via the shared base class):
  - **Fuel:** each burn eats one *burnable* element at that element's own fuel value; with none
    left the furnace goes cold on the remainder ("piles burn down to their unburnable elements").
  - **Smelt:** the recipe is resolved for the next *smeltable* element (smallest-count-first, the
    entropy tenet) — result, cook time, and the XP ledger (`setRecipeUsed`) follow the element, so
    materials whose variants smelt differently (stone, modded woods) extend without rework. A pile
    with no smeltable element resolves to no recipe → idles on the remainder (crimson/warped/bamboo
    for charcoal — bamboo blocks are fuel but not in `#logs_that_burn`).
  - **QOL:** hoppers may extract *unburnable* stacks from the fuel slot (vanilla's bucket escape
    hatch, widened) so burned-down nether remainders don't jam automation. Input-side remainders
    idle vanilla-style. The pile's selected/active wood is ignored (furnaces are automation).
- **Deferred to stone:** output-slot-aware element choice (avoid stalls on mixed outputs) and
  output-side pile consolidation — both slot into `PileFurnace` without touching the mixin.
- **DoD met:** FurnaceSelfTest drives a real block entity through `serverTick`: per-element order,
  smelt-down/burn-down remainders, never-ignites, extraction rules.

### Phase 9 — Automation pass — DONE
- **Crafter (the real work):** the crafter resolved recipes against the pile HOST — a mixed pile
  silently canonicalized (the very bug the table redesign removed) and mixed plain grids refused to
  craft. Now a pulse behaves exactly like one table single-take (`CrafterBlockMixin`, the furnace
  local-swap pattern): the resolved recipe is swapped for the transmuted element recipe, consumption
  is per element (a pile slot gives its ACTIVE wood — scroll a pile in the crafter to steer it), and
  the crafter UI's ghost result shows the transmuted product (`CrafterMenuMixin`). Non-transmutable
  grids take the vanilla path untouched.
- **Hoppers / droppers (verified, no code):** movers take one item at a time via `split(1)`, which
  peels piles canonical-first into plain stacks — piles deterministically **de-mix as they flow**
  through automation (entropy-reducing at the destination). Droppers share the same removeItem path.
- **Comparators (verified, no code):** a pile reads exactly like the plain stack it stands in for
  (count-based; sum==count invariant).
- **DoD met:** AutomationSelfTest pulses a real crafter through `dispenseFrom` (into a front-face
  container), drives a real hopper through `pushItemsTick`, and compares comparator signals.
  **← MVP complete.**

### Build-time backlog (QOL & edge cases — fold into the phases above)

Raised during implementation; captured here so they aren't lost.

- **Deposit top-up keeps pure stacks pure** *(deposit logic, near Phase 5/6).* When depositing a
  pile onto a **pure** stack of wood T (not itself a pile), exhaust the pile's T into that stack
  first — keeping it a plain T stack — before converting it into a mixed pile. Avoids polluting a
  deliberate pure stack.
- **`manual` flag on `VariantPile` — DONE.** A pile carries `manual` (boolean): true = the player curated
  it on purpose, false = it merely accumulated. Set true by every **deliberate spread/assemble** —
  **drag-merge**, **quick-craft drag-distribute**, and **expand**; inherited across **split / move**; left
  false (auto) only by **contract** (compression) and **pickup** auto-grouping. Preserved across reconcile /
  split / seed. `Pickup.consolidate` skips manual piles, so auto-grouping never disturbs a curated pile.
- **Auto-expand overflow to pure stacks — DONE.** `Pickup.autoExpandOverflow`: after a pickup, any wood
  with a full stack's worth (`>= 64`) across **auto** piles of the group is pulled into pure stacks (top up
  pure stacks, then empty slots), leaving the sub-stack remainder in piles; drained piles collapse to pure
  stacks. Manual piles are never drained or counted. Capped by available room so nothing is lost. Covered
  by SelfTest.
- **Selective extraction for crafting & recipe book — DONE.** The recipe book is pile-aware end to
  end: availability counting decomposes piles into their contents (`StackedItemContentsMixin` — a
  {oak,spruce} pile makes spruce recipes craftable and stops overcounting oak), the ingredient slot
  search matches piles CONTAINING the wanted wood (`InventoryRecipeBookMixin`), and auto-fill
  extracts the intended wood from the pile (`ServerPlaceRecipeMixin` — vanilla peeled canonical-first
  or dumped the whole pile into a grid slot). Manual piles are fair game: a recipe fill is an explicit
  crafting demand, and pulling one wood out of a mix reduces entropy (§1). The crafter block got its
  own transmute in phase 9; JEI/REI click-fill routes through the same ServerPlaceRecipe path.
- **Pile-on-pile unmix — DONE.** Left-clicking a carried pile onto a same-group pile repacks BOTH
  piles' contents canonical-first (`DragMerge.unmix`, hooked at `clicked` — the cursor's host item can
  change): the slot keeps the purest possible stack (often a plain pure 64), the cursor the remainder.
  Two half-mixed piles → one pure oak + one pure birch stack instead of the vanilla swap (§1 tenet).
  Selections survive where their wood does; the slot result is curated (manual); already-optimal pairs
  fall through to the vanilla swap. Right-click drip and plain-stack deposits unchanged. Covered by
  MenuSelfTest via real clicks.
- **Non-group crafting outputs — DONE.** The transmuter no longer requires the output to belong to a
  variant group: per-wood outputs outside any group (**boats**) transmute by substitution like everything
  else (mixed grid → majority wood's boat), and wood-agnostic outputs (chest, sticks) run through the same
  path — same result as vanilla, but piles are consumed by their active wood. Mass (shift-click) for
  unstackable/non-group outputs stays the vanilla serial loop over our single-takes. Boats stay OUT of the
  variant groups: a group's contract is "stackable family that piles" (drives pickup, merge, split, pack),
  and max-stack-1 boats would need special-casing in every interaction hook — the crafting layer is the
  right home for wood-typed outputs, groups are not.
- **Crafted output consolidates like a pickup — DONE.** Shift-crafted results route through
  `Pickup.consolidate` before the vanilla slot placement (§1 tenet). The transmuted ratio mass path
  already did (it lands via `Inventory.add`); the redirect on the result-branch `moveItemStackTo`
  covers the vanilla serial loop too — so multi-group grids (gates, signs) mass-crafted one take at a
  time merge into piles / same-family stacks instead of opening new slots. Single takes to the cursor
  are untouched (deposits already consolidate on click).
- **Middle-click (pick-block) snaps to a matching pile — DONE.** `PickItemFromBlockMixin` hooks the
  server-side `tryPickItem`: when no loose stack of the clicked wood exists (and not creative), it hands
  over a pile containing that wood, set to place it (`seed`), via `pickSlot`/`setSelectedSlot`. So
  pick-block on jungle gives you the pile already pointed at jungle (matches the icon + placement path).
- **Contract piles are space-saving, not "keep" piles** *(for the auto-sort + `manual` flag).* Piles built
  by double-click contract exist to free slots, so they must NOT get the manual/protected flag — auto-sort
  is free to break them back up.
- **Active wood (`VariantPile.selected`) — DONE except block placement.** `selected` is the active
  **wood** (`Optional<Item>`), preserved across reconcile. Set by: contract (seeds the double-clicked wood),
  depositing a plain wood into a pile (that wood becomes active), and **scrolling** over a pile slot
  (`SelectVariantPayload` to the server, plus an immediate client-side update for a live icon — mirrors
  `BundleMouseActions`). Shown by the grid-tooltip highlight **and the icon** (active wood drawn on top,
  mirroring `BundleSelectedItemSpecialRenderer`). Drag-distribute uses it as a tie-break (active wood packed
  into the earliest slots, totals still equal). **Bundle-parity inventory gestures (done):** right-click a
  pile in a slot with an empty cursor → extract the active wood's stack to the cursor; right-click a carried
  pile onto an empty slot → drip the active wood out. Both mirror `BundleItem.overrideOther/StackedOn*` and
  run in `doClick` on both sides (no packet). **Still deferred:** placing a block from a pile uses the active
  wood (not canonical oak) — the world-placement path, not an inventory gesture.

### Post-MVP (outline only)
- **Fan-out:** declare the remaining forms from §7 as data; smoke-test each.
- **Typed sticks (§13) — phase A DONE.** 8 `mythstack:<wood>_stick` items (vanilla stick = the
  oak/canonical member — no duplicate; vanilla stick texture for now), STICKS variant group
  (`#mythstack:wood/sticks`), per-wood planks→stick recipes with `minecraft:stick` restricted to
  oak planks only (deterministic per-wood resolution; D4 woods have their own typed sticks, and raw
  bamboo items craft the bamboo stick), creative tab next to the
  stick, fuel 100. **109 vanilla recipes** (all tool tiers incl. copper/spears, torch/soul/redstone
  torch, rails, fences, gates, signs, banners, ladder, bow/crossbow, campfires, grindstone, item
  frame, painting, armor stand, brush, fishing rod, lever, tripwire hook) overridden mechanically to
  accept `#mythstack:wood/sticks` — typed sticks never dead-end. Transmuter handles mixed/pile stick
  CREATION for free (per-wood recipes + substitution). **Phase B — DONE: propagation.** A cross-group wood
  identity (`VariantGroups.woodKey`/`member` — name-matched longest-first, canonical members key as
  "oak", unknown/modded woods get per-item keys valid within their own group) lets the transmuter
  substitute per SLOT per GROUP: a planks slot gets the wood's planks, a stick slot its stick. Gates
  and signs propagate their wood (contributions tally by identity — 4 spruce sticks + 2 birch planks
  → spruce gate), piles feed multi-group grids by their active wood, and the crafter inherits it all.
  **Fences are pure sticks now** (6 same-wood sticks → 3 fences, per user decision) — single-group,
  so mixed sticks / stick piles / ratio mass work with zero extra code. Gates/signs keep their
  vanilla shape with tag-accepted sticks. The pooled ratio mass plan stays single-group: shift-click
  on a multi-group grid runs the vanilla loop over per-slot single-takes (serial, correct
  propagation, no pooled rebalancing).
- **Stone (§12):** subgroup split + content to close smelting gaps.

---

## 9. Testing strategy

- **Pure unit tests (JUnit), no MC runtime:** `CanonicalPacking` — every §6.3 example, plus
  edge cases (empty pool, single variant, exactly 64, summed-remainder > 64, > 2 variants).
  This is the cheapest, highest-value coverage; write it in phase 4 and keep it green.
- **Fabric gametests** (`fabric-gametest-api`) for in-world behavior: drag-merge, split/peel,
  extract, double-click round-trip (≤64 exact, >64 minimal), pickup grouping, smelt drain,
  comparator/hopper. Each gametest is a small structure + scripted assertions.
- **The reconcile invariant** (`sum(contents)==count`) should be asserted in dev builds at
  the `VariantPiles.read()` choke-point so violations surface immediately.

---

## 10. Risk register

| Risk | Severity | Mitigation |
|------|----------|------------|
| Lazy reconcile leaks (some code reads the raw component, or a path mutates count without us noticing) | **High** | Single `VariantPiles.read()` choke-point; dev-build assertions; spike in phase 4 before building up. Fallback: eager `setCount`/`shrink` mixin. |
| `split` partition mixin gets the canonical-first peel wrong (de-mix corrupts counts) | High | Unit + gametest the split path explicitly; assert `sum==count` after every split. |
| 26.2 API names moved (GUI/registration refactors, §4) | Medium | Verify each targeted class/method against 26.2 source before writing the mixin; the named targets are 1.21.x-stable but unconfirmed for 26.2. |
| 26.2 is brand-new; Fabric API 0.152.0 churns | Medium | Pin versions; re-verify §4 against the live Fabric page; isolate version-sensitive code. |
| Vanilla click overrides (drag-merge, double-click) fight muscle memory | Medium | Gate strictly to *same-variant group* interactions; everything else stays vanilla. Playtest; fallback is a modifier key (§14 of spec). |
| Hosted-on-canonical silently reverts a mix to oak if a foreign mod strips the component | Low | Degrades safely (no crash, just loses the mix). Document; if it bites, revisit the distinct-item alternative (§2). |

---

## 11. Out of scope / deferred

- **Typed sticks & recipe reverts** (§13 of spec) — first post-MVP phase.
- **Stone variant group** (§12) — content-addition work; post-MVP.
- **Icon-grid (bundle-style) tooltip rendering** — MVP uses text lines via
  `ItemTooltipCallback`; the fancy `getTooltipImage` grid is a later polish item.
- **Neutral "default wood" output texture** (§7 future option) — start with real oak.

---

## 12. References

- Design spec: [`DESIGN_SPEC.md`](./DESIGN_SPEC.md)
- [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html) — toolchain versions
- [Fabric docs](https://docs.fabricmc.net/) — components, mixins, networking, screen events, gametests
- [Fabric example mod](https://github.com/FabricMC/fabric-example-mod)
- [Java Edition 26.2 — Minecraft Wiki](https://minecraft.wiki/w/Java_Edition_26.2)
- Vanilla reference: the `BundleItem` / `BundleContents` / `BundleTooltip` source — our UI
  mirrors its hover/scroll/right-click interaction closely.

---

## 13. Glossary

- **VariantGroup** — a set of variants that behave identically under bulk operations; here, one
  wood *form* across the nine wood types (e.g. the `logs` variant group).
- **Form** — a kind of wood product (logs, planks, stairs, …). One variant group per form.
- **Variant / member** — a specific item in a variant group (e.g. `birch_log`).
- **Canonical** — the variant group's default member (`oak_*`); the host item of any mixed pile
  and the deterministic crafting output.
- **Pile / carrier** — a mixed stack: a real vanilla stack of the canonical item carrying a
  `VariantPile` component. ("Carrier" in the spec; "pile" here — same thing.)
- **Host** — the actual `Item` a pile's `ItemStack` is (always canonical while mixed).
- **Contents** — the `VariantPile` component's true composition (`{variant → count}`).
- **Normalize / canonical packing** — the §6.3 algorithm: pool → minimal set of pure stacks
  + carriers.
- **Collapse** — a pile reduced to one variant becomes a plain vanilla stack (single-variant
  collapse); reduced to zero is destroyed (empty collapse).
- **Boundary operation** — split, combine, or consume; the only points where the mod
  intervenes.
