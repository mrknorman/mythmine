# Family Stacking — Design Specification

*Working draft v0.5 — a quality-of-life + content mod for Minecraft Java Edition*

> **MVP scope:** wood family only. Stone and all content-addition work are deferred
> (§12). UI is bundle-like with no expanded extraction screen (§10). Combining,
> manual drag-merge, and single-variant collapse are unified under canonical
> packing (§6). Double-click is an expand/contract toggle (§10). The deeper
> motivation — re-typing items vanilla flattened (typed sticks → revert fence
> recipe, etc.) — is §13.
>
> **Implementation note (added after v0.5):** the build targets **Fabric / Minecraft
> Java 26.2**, mod id **`mythstack`**. The carrier is implemented "hosted-on-canonical"
> (a mixed pile is a real vanilla stack of the canonical member + an overlay component),
> crafting always collapses to canonical (no mixed item can be crafted — "only stacks
> can be mixed"), there is one family **per wood form**, and nether wood + bamboo are
> deferred. See [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md) for how these
> decisions refine the sections below.

## 1. Goal

Cosmetic variant families (wood types, stone types, etc.) auto-combine into a
single **carrier** item so they stop bloating inventories and storage. The carrier
behaves like one stack for transport, comparators, and automation, but can be
opened bundle-style to pull out a specific variant. Crafting and smelting on a
mixed pile "just work" by resolving to deterministic results.

## 2. Core principle (the invariant)

> A family is a set of variants that **behave identically** under every bulk
> operation. Whenever a bulk operation could expose a difference, an **equivalent
> product must exist** for every member.

Closure policy: **closed by adding content.** Where a family is not naturally
closed (e.g. stone), we add the missing products rather than trimming the family.
This is a global policy, not a per-family choice.

Consequence: variant-specific properties only need to resolve at the moment a
**single item leaves** the pile (placement, use). Family-wide equivalence is only
required for operations applied to the carrier *as a whole* — smelting the stack,
consuming it in a recipe. That keeps the equivalence surface small.

## 3. The carrier item (data model)

A single item type per family, whose contents live in a data component:

```
FamilyPile {
  family:   ResourceLocation   // e.g. mythstack:wood, mythstack:stone
  contents: Map<VariantId, int> // multiset: variant -> count
}
```

- **Displayed count** = sum of all counts.
- **Hard cap** = 64 total across all variants (keeps this QoL, not storage
  compression — no balance impact).
- **Single-variant collapse:** if a carrier is reduced to exactly one variant, it
  degrades back to the plain vanilla `ItemStack` of that variant. Avoids carriers
  lingering as wrappers around a single item, and keeps interop with everything
  that already understands vanilla stacks. This is the degenerate case of canonical
  packing (§6), not a separate mechanism.
- **Empty collapse:** a carrier reduced to zero is destroyed.

## 4. Families & tags (single source of truth)

Everything keys off tags. One tag per family defines membership; the same tag
drives grouping-on-pickup *and* recipe acceptance, so they can never disagree.

- Do **not** hardcode variant lists anywhere.
- Modded variants that tag into a family are picked up for free.
- Canonical member per family is declared once (e.g. `oak` for wood,
  `cobblestone` for stone) and used as the crafting default (§7).

```
#mythstack:families/wood   -> oak, birch, spruce, ... (+ modded)
canonical: { wood: oak }
```

*Deferred:* the stone family (`#mythstack:families/stone`, canonical `cobblestone`)
is designed for but not built in the MVP — see §12.

## 5. Auto-grouping on pickup

On item pickup, if the picked-up item belongs to a known family, redirect it into
an existing matching carrier in the inventory (or create one) rather than taking a
fresh slot — subject to the 64 cap. Overflow spills to a second carrier or a normal
slot as usual.

This interception is the only "magic" hook on the hot path; keep it cheap (tag
lookup, no per-tick scanning).

## 6. Stacking rules & canonical packing

All combining is modeled as one operation. Think **family pool**, not individual
stacks: any combine dumps everything into a pool of `{variant → count}` for that
family, then **normalizes** (canonical packing).

**Normalize rule:**
1. For each variant, peel off every full **64** as a plain pure stack
   (`full = total // 64`).
2. Whatever partial remainder is left (`total % 64` summed across variants) stays
   as a single carrier...
3. ...**unless** the remainder is a single variant, in which case it collapses to a
   plain vanilla stack (the §3 single-variant collapse — same code path).

Math is `divmod(total, 64)` per variant; no bin-packing, because the cap is uniform
and everything is additive. Examples:
- 30 oak + 30 birch → one 60 carrier.
- 64 oak + 64 birch → two pure stacks, no carrier (slot-neutral tidiness).
- 70 oak + 50 birch → one pure 64-oak stack + a 56 carrier (6 oak + 50 birch).
- a pile dissolved down to only oak → plain oak stack.

> **Refinement (see plan §6.3):** rule 2 holds only when the summed remainder ≤ 64.
> When it exceeds 64 (e.g. 50 oak + 50 birch), the remainder packs into the minimal
> number of ≤64 stacks. The implementation generalizes step 2 accordingly.

**When normalize runs — player-initiated only, never continuous.** Background
reconciliation would teleport items between slots while the player watches, which
reads as broken even when correct. So packing runs only at moments the player
already initiated and expects to be disruptive:
- on pickup-overflow merge into an existing carrier;
- on **carrier-onto-carrier** merge (manual);
- on **manual drag-merge** (below);
- on an optional **sort/tidy keybind**;
- double-click dissolve (§10) is the maximal case.

**Manual drag-merge.** Holding a family member on the cursor and clicking a slot
holding a *different member of the same family* combines them into a carrier (feeds
the pool, then normalizes) instead of the vanilla swap. Respects the 64 cap;
overflow stays on the cursor.
- **Interaction subtlety / the one place we bend muscle memory:** this overrides
  vanilla's cursor↔slot swap. Rule that keeps it safe: only intercept when *both*
  sides are members of the *same family*. Different families, or a non-family item,
  swap exactly like vanilla. Flagged for playtest veto.

**Invariants:**
- Carriers of the same family merge up to the 64 cap.
- A plain vanilla stack of a family member merges into a carrier on pickup/move.
- Carriers never merge across families.

## 7. Crafting with a mixed pile

When a recipe consumes a family member and the supplied stack is a mixed carrier:

- Output is the **canonical** variant for that family (deterministic — never
  depends on insertion order). E.g. mixed wood → oak planks/stairs/etc.
- Accepted as a small, deliberate convenience tradeoff: crafting a birch-heavy
  pile into oak stairs is a free cosmetic conversion. Players who care separate
  first (§10), consistent with the separate-then-act model.
- *Future option:* introduce a neutral "default wood" texture/blockset so the
  canonical output reads as intentionally generic rather than "oak by surprise."
  Deferred; start with a real canonical variant.

Recipe parity work (the data half): every family member needs the full parallel
recipe set so any member *could* be the one consumed. Mostly recipe JSON /
data generation. Tedious but architecturally uncontroversial.

## 8. Smelting a mixed pile

- A furnace consumes one item per smelt tick and deposits into an **output
  carrier**, so the smelted mix preserves the input ratio **emergently** — no
  special enforcement needed.
- Graceful collapse: when all members smelt to the same thing (all logs → charcoal),
  the output is simply a homogeneous stack. Not a bug.
- **MVP (wood):** smelting is trivially closed — logs/planks already smelt to
  charcoal, so a mixed wood carrier collapses to a homogeneous charcoal output for
  free. No content additions needed.
- **Mixed-smeltability** is the real edge case *for other families* (deferred).
  Under the closure policy we would *homogenise smelting* so every member has a
  smelt result — the main content-addition obligation (e.g. smelted stone forms).
  Not relevant to the wood MVP. See §12.

## 9. Filtering & automation (no new block)

Deliberately reuse vanilla mechanics:

- A carrier flows through hoppers/droppers as **one item with one identity**.
- A comparator reads the carrier slot as N/64 like any stack, so existing
  redstone item-sorters keep working — they sort **piles as piles**.
- What you give up: pulling a *specific* variant out of a mix with redstone alone.
  That stays the UI's job (§10), consistent with separate-then-act.
- The 1.21 crafter block sees the canonical-default rule (§7), so auto-crafting
  from a mixed carrier yields canonical output deterministically.

No filter block is added. This keeps automation scope to "carriers are well-behaved
single-identity items" rather than "build a new filtering subsystem."

> **Note (see plan §6.4):** because automation pulls one item per operation (a
> *split*), a hopper draining a pile de-mixes it **canonical-first** over time. The
> pile still moves as a single identity; sub-item extraction is the boundary case.

## 10. The separation UI

Match the vanilla bundle interaction closely. No custom screen, no expanded
extraction.

**In-place, bundle-style:**
- Hovering the carrier shows a **tooltip breakdown**: each variant icon + count,
  plus the total (this is just the vanilla bundle tooltip with our contents).
- **Scroll** while hovering cycles a **selection highlight** through the variants.
- **Right-click** extracts the selected variant — one unit to the cursor, exactly
  as vanilla bundles remove the selected item.
- Extraction yields a **plain vanilla stack** of that variant. (Single-variant
  collapse in §3 keeps the leftover carrier clean.)

**Expand / contract (double-click toggle — the one addition beyond vanilla
bundle behavior):**

Double-click is a symmetric toggle between the spread-out and unified forms of a
family. Both directions are just canonical packing (§6) with a different trigger.

- **Contract** — double-click a *pure* (non-mixed) family member: sweep every
  member of that family in the inventory into a pool, run normalize (§6), and place
  the resulting carrier on the **clicked slot**. Full 64s settle as pure stacks
  (the peel rule); the partial remainder forms the carrier.
- **Expand** — double-click a *carrier*: dissolve the whole pile into its own
  vanilla stacks, distributed into available inventory slots, overflow dropping as
  items if inventory is full. (The maximal case of canonical packing.)

Behavior at the boundaries:
- **Perfect round-trip only when family total ≤ 64.** Below the cap, contract → one
  carrier, expand → the same pure stacks back. Above 64, contract yields the
  *minimal* set instead: full pure stacks + one remainder carrier (can't fit one
  slot).
- **Contract is a no-op when there's nothing to mix** (only a single variant has
  partials). In that case it falls back to vanilla double-click-gather for that
  item, so the gesture still feels responsive.
- **Overrides vanilla double-click-to-collect**, same as drag-merge (§6): intercept
  only for family members, leave everything else vanilla. Note the result lands in
  the clicked *slot*, not on the cursor — a deliberate deviation from vanilla.

No multi-variant scroll-list screen and no shift-pull-stack in the MVP — both were
"expanded extraction" and are dropped.

## 11. Variant-specific property resolution

Properties that differ between members (flammability, placed-block behavior,
specific use actions) only resolve when a **single variant leaves** the pile via
extraction (§10) or canonical-default selection (§7). The mixed carrier itself
never has to answer variant-specific questions, which is what makes the whole
design tractable.

> **Strengthened (see plan §3):** mixedness is a *stack-level* property only. Every
> individual item is a definite variant; only a multi-item stack can be a mix.
> Crafting/smelting always emit definite canonical items and never preserve a mix.

## 12. Content the closure policy obliges us to add

"Closed by adding content" has concrete consequences *for families that aren't
naturally closed*. **The wood MVP needs almost none of this** — wood is naturally
near-closed (full recipe sets exist or are easy to generate; smelting collapses to
charcoal). This section is the forward-looking obligation for later families:

- **Recipe parity:** every member has the full parallel recipe set (§7). For wood,
  mostly already true; fill any small gaps via data generation.
- **Smelt parity:** every member has a smelt result (§8). Trivial for wood.
- **Stone (deferred):** this is where the policy bites — inventing smelted forms
  vanilla lacks (*what is smelted granite?*), turning stone into a content target.
  Recommended when tackled: split "stone" into genuinely-closed **subfamilies**
  (the cobble/stone building line vs. ingredient stones) and only force closure
  with new content where a subfamily is worth completing.

> **Closure caveat that bites in wood too (see plan D4):** crimson/warped are not
> flammable, not fuel, and don't smelt to charcoal, so they cannot share a family
> with overworld wood for any fuel-able form. They become their own families.
> Bamboo has forms with no analog (`mosaic`) and irregular naming (`raft`, `block`).
> Both are **deferred** out of the MVP, which runs on the 9 regular overworld woods.

## 13. Typed item families (the deeper motivation)

The carrier is what makes it safe to **re-type items vanilla deliberately
flattened.** Vanilla keeps sticks, ladders, and similar items generic to avoid
inventory bloat; once bloat is solved (§3, §6), the lost type information can be
restored "for free." This reframes the mod: not just inventory QoL, but undoing the
compromises that existed only because inventories couldn't afford the variants.

**Worked example — the fence recipe.** Until Java 1.8 a fence was 6 sticks. In 1.8
the recipe became 4 planks + 2 sticks *specifically* so the fence could carry a wood
type, because type-less sticks couldn't express it. Typed sticks remove that root
cause, so the fence — and the fence gate, which uses the identical sticks+planks
pattern — can revert to a stick-only recipe whose type comes from the sticks. The
plank was a workaround for a missing type; supply the type and the workaround drops
out. Any recipe using the sticks+planks pattern to fake a type is a revert
candidate.

**Propagate-vs-discard — the one genuinely new design decision.** Sticks feed dozens
of recipes, and most must NOT become typed (an "oak pickaxe" or "birch torch" is
nonsense — there the stick is an invisible handle/shaft). So each stick-consuming
recipe needs a policy:
- **Propagate type** → output is a typed family member: fences, fence gates,
  ladders, signs (already typed), boats (already typed). Placed/aesthetic wood where
  the grain is the point.
- **Discard type** → output is generic: tools, torches, arrows, rails, item frames,
  brushes, armor stands. The stick is an internal component; its type is consumed
  and dropped.

Default principle: **propagate only where wood grain is visible on the placed
result; discard everywhere the stick is a hidden component.** Confirm the per-item
list in playtest.

This rides on the canonical-default rule (§7): a mixed/typed stick carrier into a
*discard* recipe yields the generic item; into a *propagate* recipe it yields the
canonical type (separate first for a specific type). Mechanically, typed sticks are
just another family with their own carrier (§3–§6), derived from the wood family.

*Sequencing note:* this is the first **post-core** phase — built after the wood
carrier MVP works, since it depends on all of §3–§10 being in place. See §15.

## 14. Open questions (deferred decisions)

- Drag-merge and double-click-contract overrides of vanilla swap / collect (§6,
  §10) — confirm they feel right in playtest; fallback is requiring a modifier key.
- Whether a neutral "default" textured blockset replaces canonical-variant output
  (§7) — deferred well past MVP.
- Interaction with shulker boxes / storage mods (likely fine — carrier is just an
  item — but verify).
- *Post-MVP:* the per-item propagate-vs-discard list for typed sticks (§13).
- *Post-MVP:* stone subfamily boundaries and which gaps justify new content (§12).

## 15. Suggested build sequencing

Ordered by "prove the pipeline, then add ambition." Each step ships before the next.
**MVP is wood-only and ends at step 9.** (See [`IMPLEMENTATION_PLAN.md`](./IMPLEMENTATION_PLAN.md)
§8 for the elaborated, Fabric/26.2-specific version of this list with acceptance criteria.)

1. **Scaffold:** dev environment, one custom item into the creative menu, fast
   build/run loop. (Loader + MC version locked.)
2. **Tags & families:** declare the wood family tag + canonical `oak`; no behavior yet.
3. **Recipe parity (wood):** generate any missing parallel recipes; verify any wood
   member crafts the full product line. Pure data — good early win.
4. **Carrier item + canonical packing:** data model (§3), display, 64 cap, the
   normalize rule (§6) and single-variant collapse. No auto-pickup yet — fill it
   manually via drag-merge.
5. **Separation UI:** bundle-style tooltip + scroll-select + right-click extract;
   then the double-click expand/contract toggle.
6. **Auto-grouping on pickup (§5).**
7. **Crafting default (§7)** against the carrier — mixed wood → oak output.
8. **Smelting (§8)** — mixed wood carrier → charcoal, ratio-preserving path verified.
9. **Automation pass (§9):** verify hopper/comparator/crafter behavior.

*Post-MVP, phase order:*
- **Typed sticks (§13):** new stick family + carrier; propagate-vs-discard policy;
  revert fence & fence-gate recipes to stick-only. The motivating payoff.
- **Typed ladders & other reverts (§13):** extend propagation to remaining
  visible-grain items.
- **Stone as a content family (§12):** subfamily split + fill gaps.

The flagship feature (mixed carrier) lands around steps 4–8 — intentionally *after*
the boring scaffolding and the easy recipe-parity win, never as the first thing.
