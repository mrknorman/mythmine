package com.mythstack.interaction;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Auto-grouping on pickup (spec §5) — fully automatic consolidation.
 *
 * <p>When a family member is picked up it is routed into existing inventory slots by priority:
 * <ol>
 *   <li>top up a pure stack of the <em>same</em> variant (a deliberate same-wood stack wins);</li>
 *   <li>merge into an existing pile of the same family;</li>
 *   <li>combine with any other same-family member to form a pile (auto-consolidate).</li>
 * </ol>
 * Anything that doesn't fit is left on {@code incoming} for vanilla to drop into an empty slot.
 *
 * <p>The logic operates on a plain {@code List<ItemStack>} (the inventory's main slots) so it can be
 * unit/self-tested without a live player.
 */
public final class Pickup {
	private Pickup() {
	}

	/** Consolidate {@code incoming} into {@code slots}; returns true if it was fully absorbed. */
	public static boolean consolidate(List<ItemStack> slots, ItemStack incoming) {
		VariantGroup group = VariantGroups.of(incoming.getItem());
		if (group == null || incoming.isEmpty()) {
			return false;
		}

		// Pass 1: pure stacks of the same variant (only for a plain-member pickup).
		if (!VariantPiles.isPile(incoming)) {
			for (int i = 0; i < slots.size() && !incoming.isEmpty(); i++) {
				ItemStack slot = slots.get(i);
				if (!slot.isEmpty() && !VariantPiles.isPile(slot) && slot.getItem() == incoming.getItem()) {
					absorb(slots, i, incoming, group);
				}
			}
		}
		// Pass 2: existing AUTO piles of the group — a manual (curated) pile is never disturbed by pickup.
		for (int i = 0; i < slots.size() && !incoming.isEmpty(); i++) {
			ItemStack slot = slots.get(i);
			if (VariantPiles.isPile(slot) && !VariantPiles.isManual(slot) && VariantGroups.of(slot.getItem()) == group) {
				absorb(slots, i, incoming, group);
			}
		}
		// Pass 3: any other group member -> form a pile.
		for (int i = 0; i < slots.size() && !incoming.isEmpty(); i++) {
			ItemStack slot = slots.get(i);
			if (!slot.isEmpty() && !VariantPiles.isPile(slot) && VariantGroups.of(slot.getItem()) == group) {
				absorb(slots, i, incoming, group);
			}
		}
		// Tenet: the inventory tends toward order on use. A wood you've amassed a full stack of shouldn't
		// stay hoarded in mixed piles, so pull the full-stack portion out into clean pure stacks.
		autoExpandOverflow(slots, group);
		return incoming.isEmpty();
	}

	/**
	 * For each wood with a full stack's worth ({@code >= 64}) sitting in this group's <em>auto</em> piles,
	 * pull the full-stack portion out into pure stacks — topping up existing pure stacks of that wood, then
	 * filling empty slots — and leave the sub-stack remainder in the piles. Manual (curated) piles are never
	 * touched, and the pull is capped by available room so nothing is lost. Piles that lose a wood collapse
	 * naturally (a {@code {wood, other}} pile minus its {@code wood} becomes a pure {@code other} stack), so
	 * an abundant wood turns scattered mixed piles into clean pure stacks.
	 */
	public static void autoExpandOverflow(List<ItemStack> slots, VariantGroup group) {
		Set<Item> woods = new LinkedHashSet<>();
		for (ItemStack slot : slots) {
			if (isAutoPile(slot, group)) {
				for (VariantPile.Entry entry : slot.get(ModComponents.VARIANT_PILE).contents()) {
					woods.add(entry.item());
				}
			}
		}
		for (Item wood : woods) {
			int total = 0;
			for (ItemStack slot : slots) {
				if (isAutoPile(slot, group)) {
					total += VariantPiles.countOf(slot, wood);
				}
			}
			int fullStacks = (total / VariantPiles.CAP) * VariantPiles.CAP;
			if (fullStacks == 0) {
				continue;
			}
			int extract = Math.min(fullStacks, pureCapacity(slots, wood));
			if (extract <= 0) {
				continue;
			}
			addPure(slots, wood, pullFromAutoPiles(slots, group, wood, extract));
		}
	}

	private static boolean isAutoPile(ItemStack stack, VariantGroup group) {
		return VariantPiles.isPile(stack) && !VariantPiles.isManual(stack) && VariantGroups.of(stack.getItem()) == group;
	}

	/** Room to add pure {@code wood}: free space in existing pure stacks of it + empty slots. */
	private static int pureCapacity(List<ItemStack> slots, Item wood) {
		int room = 0;
		for (ItemStack slot : slots) {
			if (slot.isEmpty()) {
				room += VariantPiles.CAP;
			} else if (!VariantPiles.isPile(slot) && slot.getItem() == wood) {
				room += VariantPiles.CAP - slot.getCount();
			}
		}
		return room;
	}

	/** Remove up to {@code amount} of {@code wood} from the group's auto piles; returns how much was pulled. */
	private static int pullFromAutoPiles(List<ItemStack> slots, VariantGroup group, Item wood, int amount) {
		int removed = 0;
		for (int i = 0; i < slots.size() && removed < amount; i++) {
			ItemStack slot = slots.get(i);
			if (!isAutoPile(slot, group)) {
				continue;
			}
			removed += VariantPiles.removeWood(slot, wood, amount - removed).getCount();
			// The pile may have collapsed to a single variant or emptied — normalise the slot.
			slots.set(i, slot.getCount() <= 0 ? ItemStack.EMPTY : VariantPiles.collapseToReal(slot));
		}
		return removed;
	}

	/** Place {@code amount} of pure {@code wood}: top up existing pure stacks first, then fill empty slots. */
	private static void addPure(List<ItemStack> slots, Item wood, int amount) {
		for (int i = 0; i < slots.size() && amount > 0; i++) {
			ItemStack slot = slots.get(i);
			if (!slot.isEmpty() && !VariantPiles.isPile(slot) && slot.getItem() == wood && slot.getCount() < VariantPiles.CAP) {
				int add = Math.min(VariantPiles.CAP - slot.getCount(), amount);
				slot.grow(add);
				amount -= add;
			}
		}
		for (int i = 0; i < slots.size() && amount > 0; i++) {
			if (slots.get(i).isEmpty()) {
				int add = Math.min(VariantPiles.CAP, amount);
				slots.set(i, new ItemStack(wood, add));
				amount -= add;
			}
		}
	}

	/** Move up to the cap-remaining of {@code incoming} into slot {@code index}, re-normalizing it. */
	private static void absorb(List<ItemStack> slots, int index, ItemStack incoming, VariantGroup group) {
		ItemStack target = slots.get(index);
		int space = VariantPiles.CAP - target.getCount();
		if (space <= 0) {
			return;
		}
		ItemStack peeled = incoming.split(Math.min(space, incoming.getCount()));
		List<ItemStack> result = VariantPiles.makeStacks(group, VariantPiles.pool(group, List.of(target, peeled)));
		if (!result.isEmpty()) {
			slots.set(index, result.get(0));
		}
	}
}
