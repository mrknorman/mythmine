package com.mythstack.interaction;

import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.item.ItemStack;

import java.util.List;

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
		// Pass 2: existing piles of the group.
		for (int i = 0; i < slots.size() && !incoming.isEmpty(); i++) {
			if (VariantPiles.isPile(slots.get(i)) && VariantGroups.of(slots.get(i).getItem()) == group) {
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
		return incoming.isEmpty();
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
