package com.mythstack.interaction;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPile.Entry;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Double-click / drag pile separation (plan Phase 5). Distributes a pile's contents back into the
 * surrounding storage as plain vanilla stacks, written directly to slots (not via {@code Inventory.add},
 * so auto-pickup grouping isn't triggered).
 *
 * <p>Expand placement, per wood: walk the pile's own container nearest-first (its row radiating out from
 * its column, then the rows below, then above), filling an empty slot or topping up a matching stack
 * <em>as reached</em>, until the wood is placed — so a like-stack is only merged into if it sits in the
 * area the pile actually expands into, not just anywhere in the container. Overflow spills to the other
 * container (empties first, matching stacks only as a last resort). Leftover stays a smaller pile on the
 * cursor; never dropped.
 */
public final class PileSeparation {
	private PileSeparation() {
	}

	/**
	 * Expand the pile on {@code menu}'s cursor into the storage around slot {@code originSlot}. Returns
	 * the leftover that didn't fit, as a (possibly empty) carrier to leave on the cursor.
	 */
	public static ItemStack expand(AbstractContainerMenu menu, int originSlot) {
		ItemStack pile = menu.getCarried();
		VariantPile data = pile.get(ModComponents.VARIANT_PILE);
		if (data == null) {
			return pile;
		}
		Slot origin = (originSlot >= 0 && originSlot < menu.slots.size()) ? menu.slots.get(originSlot) : null;
		List<Slot> sameContainer = new ArrayList<>();
		List<Slot> otherContainer = new ArrayList<>();
		splitTargets(menu, origin, sameContainer, otherContainer);

		List<ItemStack> leftovers = new ArrayList<>();
		// Place the active wood first so it lands in the origin slot (where the mouse is) — radiatingPass
		// fills the origin first. Contracting that slot again then re-seeds the same active wood, so the
		// selection round-trips through expand/contract (QoL).
		for (Entry entry : orderedEntries(data)) {
			ItemStack stack = new ItemStack(entry.item(), entry.count());
			// Expansion area: fill empties / top up matching stacks in radiating order until this wood is
			// placed, so only the slots actually reached (where the pile expands to) get used — distant
			// like-stacks in the same container are left alone.
			radiatingPass(stack, sameContainer);
			// Overflow to the other container: empties first, matching stacks only as a last resort.
			fillEmpty(stack, otherContainer);
			topUp(stack, otherContainer);
			if (!stack.isEmpty()) {
				leftovers.add(stack);
			}
		}
		if (leftovers.isEmpty()) {
			return ItemStack.EMPTY;
		}
		// Keep the minimal unplaceable amount as a pile (the original was <= 64, so this packs to one stack).
		VariantGroup group = VariantGroups.of(pile.getItem());
		List<ItemStack> packed = VariantPiles.makeStacks(group, VariantPiles.pool(group, leftovers));
		if (packed.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack leftover = packed.get(0);
		data.selected().ifPresent(active -> VariantPiles.seed(leftover, active));
		VariantPiles.markManual(leftover, true); // a deliberate spread is curated — protect the leftover too
		return leftover;
	}

	/**
	 * Double-click a plain wood stack to contract: first gather every bit of <em>that</em> wood — pure
	 * stacks and the wood locked inside other piles — into the cursor, up to a full 64; then, if there's
	 * still room, pull in whole pure stacks of <em>other</em> woods that fit the gap, forming a pile,
	 * until full or none fit. The double-clicked wood is recorded as the pile's seed/active variant.
	 * Returns the resulting cursor stack.
	 */
	public static ItemStack contract(AbstractContainerMenu menu, Player player) {
		ItemStack carried = menu.getCarried();
		Item seed = carried.getItem();
		VariantGroup group = VariantGroups.of(seed);
		if (group == null) {
			return carried;
		}
		int cap = VariantPiles.CAP;
		List<ItemStack> gathered = new ArrayList<>();
		gathered.add(carried.copy());
		int total = carried.getCount();

		// Step 1: gather all of `seed` — pure seed stacks and seed locked inside other piles.
		for (Slot slot : menu.slots) {
			if (total >= cap) {
				break;
			}
			ItemStack s = slot.getItem();
			if (s.isEmpty() || !slot.mayPickup(player)) {
				continue;
			}
			if (VariantPiles.isPile(s)) {
				int take = Math.min(VariantPiles.countOf(s, seed), cap - total);
				if (take > 0) {
					gathered.add(VariantPiles.splitPilePreferring(s, take, seed));
					slot.set(s);
					total += take;
				}
			} else if (s.getItem() == seed) {
				int take = Math.min(s.getCount(), cap - total);
				gathered.add(new ItemStack(seed, take));
				s.shrink(take);
				slot.set(s);
				total += take;
			}
		}

		// Step 2: fill the remaining gap with whole stacks/piles of other group woods that fit. Pulling
		// whole piles too consolidates the many small piles dragging leaves behind into this one.
		for (Slot slot : menu.slots) {
			if (total >= cap) {
				break;
			}
			ItemStack s = slot.getItem();
			if (s.isEmpty() || !slot.mayPickup(player) || s.getCount() > cap - total) {
				continue;
			}
			if (VariantPiles.isPile(s)) {
				// Same-group piles only — a different-group pile (e.g. a logs pile while contracting
				// planks) must be left alone. Its seed was already drained in step 1; pool() then
				// dissolves the absorbed pile into its woods.
				if (VariantGroups.of(s.getItem()) == group) {
					gathered.add(s.copy());
					total += s.getCount();
					slot.set(ItemStack.EMPTY);
				}
			} else if (s.getItem() != seed && group.contains(s.getItem())) {
				gathered.add(s.copy());
				total += s.getCount();
				slot.set(ItemStack.EMPTY);
			}
		}

		List<ItemStack> made = VariantPiles.makeStacks(group, VariantPiles.pool(group, gathered));
		ItemStack result = made.isEmpty() ? ItemStack.EMPTY : made.get(0);
		VariantPiles.seed(result, seed); // record the active/seed wood (display/placement effects later)
		return result;
	}

	/**
	 * Split {@code menu}'s slots into the origin's container (sorted nearest-first: origin row, then rows
	 * below, then above, each radiating out from the origin column) and every other slot.
	 */
	private static void splitTargets(AbstractContainerMenu menu, Slot origin, List<Slot> same, List<Slot> other) {
		Container container = origin != null ? origin.container : null;
		for (Slot slot : menu.slots) {
			if (container != null && slot.container == container) {
				same.add(slot);
			} else {
				other.add(slot);
			}
		}
		int originX = origin != null ? origin.x : 0;
		int originY = origin != null ? origin.y : 0;
		same.sort(Comparator
				.comparingInt((Slot s) -> rowRank(s.y, originY)) // own row, then below, then above
				.thenComparingInt(s -> Math.abs(s.y - originY))  // nearest row first
				.thenComparingInt(s -> Math.abs(s.x - originX))  // radiate out from the origin column
				.thenComparingInt(s -> s.x));
	}

	private static int rowRank(int y, int originY) {
		if (y == originY) {
			return 0;
		}
		return y > originY ? 1 : 2; // rows below before rows above
	}

	/**
	 * One pass over {@code slots} in order: fill an empty slot, or top up a matching stack, until
	 * {@code stack} is placed. Stopping when placed means only the slots the pile actually reaches (the
	 * area it expands into) are touched — matching stacks beyond that point aren't disturbed.
	 */
	private static void radiatingPass(ItemStack stack, List<Slot> slots) {
		for (Slot slot : slots) {
			if (stack.isEmpty()) {
				return;
			}
			if (!slot.mayPlace(stack)) {
				continue;
			}
			ItemStack existing = slot.getItem();
			if (existing.isEmpty()) {
				slot.set(stack.split(Math.min(slot.getMaxStackSize(stack), stack.getCount())));
			} else if (ItemStack.isSameItemSameComponents(existing, stack)) {
				int move = Math.min(slot.getMaxStackSize(existing) - existing.getCount(), stack.getCount());
				if (move > 0) {
					existing.grow(move);
					slot.set(existing);
					stack.shrink(move);
				}
			}
		}
	}

	/** Fill empty slots in order with {@code stack}; mutates it down to the remainder. */
	private static void fillEmpty(ItemStack stack, List<Slot> slots) {
		for (Slot slot : slots) {
			if (stack.isEmpty()) {
				return;
			}
			if (!slot.getItem().isEmpty() || !slot.mayPlace(stack)) {
				continue;
			}
			slot.set(stack.split(Math.min(slot.getMaxStackSize(stack), stack.getCount())));
		}
	}

	/** Top up matching plain stacks in order with {@code stack}; mutates it down to the remainder. */
	private static void topUp(ItemStack stack, List<Slot> slots) {
		for (Slot slot : slots) {
			if (stack.isEmpty()) {
				return;
			}
			ItemStack existing = slot.getItem();
			if (existing.isEmpty() || !ItemStack.isSameItemSameComponents(existing, stack) || !slot.mayPlace(stack)) {
				continue;
			}
			int move = Math.min(slot.getMaxStackSize(existing) - existing.getCount(), stack.getCount());
			if (move > 0) {
				existing.grow(move);
				slot.set(existing);
				stack.shrink(move);
			}
		}
	}

	/**
	 * Drag-distribute the pile on {@code menu}'s cursor as evenly as possible across the empty
	 * {@code draggedSlots} — one pile/stack per slot. Slot totals differ by at most one (equality is the
	 * primary goal); as a tie-break the active wood is packed into the earliest slots first, so it lands
	 * earlier rather than being spread thin. Slots are ordered by position so the client and server agree
	 * on which slot gets which share. The whole pile is spread (the cursor ends empty); if no empty slot
	 * was dragged over, nothing happens.
	 */
	public static void dragDistribute(AbstractContainerMenu menu, Set<Slot> draggedSlots) {
		ItemStack pile = menu.getCarried();
		VariantPile data = pile.get(ModComponents.VARIANT_PILE);
		if (data == null) {
			return;
		}
		List<Slot> targets = new ArrayList<>();
		for (Slot slot : draggedSlots) {
			if (slot.getItem().isEmpty() && slot.mayPlace(pile)) {
				targets.add(slot);
			}
		}
		if (targets.isEmpty()) {
			return;
		}
		targets.sort(Comparator.comparingInt((Slot s) -> s.y).thenComparingInt(s -> s.x));

		int n = targets.size();
		List<Map<Item, Integer>> pools = distributePools(data, n);
		VariantGroup group = VariantGroups.of(pile.getItem());
		for (int i = 0; i < n; i++) {
			List<ItemStack> parts = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : pools.get(i).entrySet()) {
				parts.add(new ItemStack(e.getKey(), e.getValue()));
			}
			List<ItemStack> made = VariantPiles.makeStacks(group, VariantPiles.pool(group, parts));
			if (!made.isEmpty()) {
				ItemStack share = made.get(0);
				// makeStacks builds fresh piles with no selection; carry the active wood onto each share so
				// dragging a pile apart doesn't reset its active variant (same fix as the split path).
				data.selected().ifPresent(active -> VariantPiles.seed(share, active));
				VariantPiles.markManual(share, true); // a quick-craft spread is a deliberate arrangement → curated
				targets.get(i).set(share);
			}
		}
		menu.setCarried(ItemStack.EMPTY);
	}

	/**
	 * The share a single dragged {@code target} slot would receive from {@link #dragDistribute} — used to
	 * draw the in-drag preview so it matches the eventual even split (the same position-sort + round-robin
	 * as the real distribution). Returns the slot's existing item for slots that won't receive anything.
	 */
	public static ItemStack dragPreviewShare(ItemStack pile, Set<Slot> draggedSlots, Slot target) {
		VariantPile data = pile.get(ModComponents.VARIANT_PILE);
		if (data == null) {
			return target.getItem();
		}
		List<Slot> targets = new ArrayList<>();
		for (Slot slot : draggedSlots) {
			if (slot.getItem().isEmpty() && slot.mayPlace(pile)) {
				targets.add(slot);
			}
		}
		targets.sort(Comparator.comparingInt((Slot s) -> s.y).thenComparingInt(s -> s.x));
		int index = targets.indexOf(target);
		if (index < 0) {
			return target.getItem(); // this slot won't receive anything — show it unchanged
		}
		Map<Item, Integer> pool = distributePools(data, targets.size()).get(index);
		if (pool.isEmpty()) {
			return ItemStack.EMPTY;
		}
		List<ItemStack> parts = new ArrayList<>();
		for (Map.Entry<Item, Integer> e : pool.entrySet()) {
			parts.add(new ItemStack(e.getKey(), e.getValue()));
		}
		VariantGroup group = VariantGroups.of(pile.getItem());
		List<ItemStack> made = VariantPiles.makeStacks(group, VariantPiles.pool(group, parts));
		if (made.isEmpty()) {
			return ItemStack.EMPTY;
		}
		ItemStack share = made.get(0);
		data.selected().ifPresent(active -> VariantPiles.seed(share, active)); // preview keeps the active wood too
		return share;
	}

	/**
	 * Split {@code data}'s woods across {@code n} position-ordered slots: each slot's total differs by at
	 * most one (the remainder lands in the earliest slots), and within that the active wood is packed into
	 * the earliest slots first — equality is the primary goal, active-earlier is only the tie-break. Used
	 * by both {@link #dragDistribute} and {@link #dragPreviewShare} so the preview matches the result.
	 */
	private static List<Map<Item, Integer>> distributePools(VariantPile data, int n) {
		int total = 0;
		for (Entry entry : data.contents()) {
			total += entry.count();
		}
		int base = total / n;
		int rem = total % n;
		List<Map<Item, Integer>> pools = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			pools.add(new HashMap<>());
		}
		// Fill the earliest slots up to their quota, drawing the active wood before the rest, so the active
		// wood concentrates early while every slot still ends up with base (or base+1, for the first `rem`).
		int slot = 0;
		int placed = 0;
		for (Entry entry : orderedEntries(data)) {
			for (int k = 0; k < entry.count(); k++) {
				int quota = base + (slot < rem ? 1 : 0);
				while (slot < n && placed >= quota) {
					slot++;
					placed = 0;
					quota = base + (slot < rem ? 1 : 0);
				}
				if (slot >= n) {
					return pools; // defensive — sum of quotas equals total, so this shouldn't be reached
				}
				pools.get(slot).merge(entry.item(), 1, Integer::sum);
				placed++;
			}
		}
		return pools;
	}

	/** The pile's woods with the active one moved to the front (else contents order unchanged). */
	private static List<Entry> orderedEntries(VariantPile data) {
		if (data.selected().isEmpty()) {
			return data.contents();
		}
		Item active = data.selected().get();
		List<Entry> ordered = new ArrayList<>(data.contents().size());
		for (Entry entry : data.contents()) {
			if (entry.item() == active) {
				ordered.add(entry);
			}
		}
		for (Entry entry : data.contents()) {
			if (entry.item() != active) {
				ordered.add(entry);
			}
		}
		return ordered;
	}
}
