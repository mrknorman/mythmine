package com.mythstack.interaction;

import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Manual drag-merge (spec §6): clicking a slot while holding a <em>different</em> member of the same
 * variant group combines them into a pile instead of the vanilla swap. Left-click deposits everything
 * that fits (up to the 64 cap); right-click deposits a single log at a time (top up / build a pile
 * gradually). Overflow stays on the cursor. Only ever intercepts same-group interactions — different
 * groups, non-members, or plain same-variant stacking fall through to vanilla.
 */
public final class DragMerge {
	private DragMerge() {
	}

	/**
	 * @param slot    the clicked slot (target)
	 * @param carried the cursor stack
	 * @param action  the click action — {@link ClickAction#PRIMARY} deposits all that fits,
	 *                {@link ClickAction#SECONDARY} deposits a single log
	 * @return true if the merge was handled (the caller should stop vanilla handling)
	 */
	public static boolean tryMerge(Slot slot, ItemStack carried, ClickAction action) {
		if (carried.isEmpty()) {
			return false;
		}
		ItemStack slotStack = slot.getItem();
		if (slotStack.isEmpty()) {
			return false;
		}
		VariantGroup group = VariantGroups.of(slotStack.getItem());
		if (group == null || VariantGroups.of(carried.getItem()) != group) {
			return false; // not both members of the same group
		}
		// A wood deposited into a pile becomes that pile's active variant (only meaningful for a plain
		// carried stack — depositing a whole pile leaves the existing selection alone).
		boolean pureDeposit = !VariantPiles.isPile(carried);
		Item depositedWood = carried.getItem();
		// Leave plain same-variant stacking to vanilla; only intervene when a mix would form.
		boolean anyPile = VariantPiles.isPile(slotStack) || VariantPiles.isPile(carried);
		if (slotStack.getItem() == carried.getItem() && !anyPile) {
			return false;
		}
		int space = VariantPiles.CAP - slotStack.getCount();
		if (space <= 0) {
			return false; // target already full — let vanilla swap
		}

		// Left-click deposits everything that fits; right-click drips in a single log.
		int wanted = action == ClickAction.SECONDARY ? 1 : carried.getCount();
		int take = Math.min(space, wanted);
		if (take <= 0) {
			return false;
		}
		// Depositing onto a PURE stack spends the carried pile's matching wood first, so the target
		// stays a plain stack until that wood is exhausted (QOL — don't pollute a deliberate stack).
		boolean topUpPure = !VariantPiles.isPile(slotStack);
		Item preferred = slotStack.getItem();

		// Preview the merged result without mutating, so we can bail on a bad slot.
		ItemStack preview = carried.copy();
		ItemStack peeled = topUpPure
				? VariantPiles.splitPilePreferring(preview, take, preferred)
				: preview.split(take);
		List<ItemStack> result = VariantPiles.makeStacks(group, VariantPiles.pool(group, List.of(slotStack, peeled)));
		ItemStack merged = result.isEmpty() ? ItemStack.EMPTY : result.get(0);
		if (merged.isEmpty() || !slot.mayPlace(merged)) {
			return false;
		}

		// Commit the same peel on the real cursor stack; the remainder stays on the cursor.
		if (topUpPure) {
			VariantPiles.splitPilePreferring(carried, take, preferred);
		} else {
			carried.split(take);
		}
		if (pureDeposit) {
			VariantPiles.seed(merged, depositedWood); // the wood you just deposited becomes the active one
		}
		VariantPiles.markManual(merged, true); // a pile you assembled by hand is curated — protect it from auto-sort
		slot.set(merged);
		return true;
	}
}
