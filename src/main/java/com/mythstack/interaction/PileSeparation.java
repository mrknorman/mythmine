package com.mythstack.interaction;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPile.Entry;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.Container;
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
		for (Entry entry : data.contents()) {
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
		return packed.isEmpty() ? ItemStack.EMPTY : packed.get(0);
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
	 * {@code draggedSlots} — one pile/stack per slot. Items are dealt round-robin in canonical order, so
	 * counts differ by at most one and every slot gets a representative mix. Slots are ordered by
	 * position so the client and server agree on which slot gets which share. The whole pile is spread
	 * (the cursor ends empty); if no empty slot was dragged over, nothing happens.
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
		List<Map<Item, Integer>> pools = new ArrayList<>(n);
		for (int i = 0; i < n; i++) {
			pools.add(new HashMap<>());
		}
		int next = 0;
		for (Entry entry : data.contents()) {
			for (int k = 0; k < entry.count(); k++) {
				pools.get(next).merge(entry.item(), 1, Integer::sum);
				next = (next + 1) % n;
			}
		}
		VariantGroup group = VariantGroups.of(pile.getItem());
		for (int i = 0; i < n; i++) {
			List<ItemStack> parts = new ArrayList<>();
			for (Map.Entry<Item, Integer> e : pools.get(i).entrySet()) {
				parts.add(new ItemStack(e.getKey(), e.getValue()));
			}
			List<ItemStack> made = VariantPiles.makeStacks(group, VariantPiles.pool(group, parts));
			if (!made.isEmpty()) {
				targets.get(i).set(made.get(0));
			}
		}
		menu.setCarried(ItemStack.EMPTY);
	}
}
