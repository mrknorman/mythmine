package com.mythstack.interaction;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.inventory.AbstractContainerMenu;
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
		int space = slotStack.getMaxStackSize() - slotStack.getCount(); // per-group cap = the host's max stack
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

	/**
	 * Left-clicking a carried pile onto another pile of the same group repacks BOTH piles' contents
	 * canonical-first (<b>unmix</b>): the slot keeps the purest possible stack, the cursor the remainder.
	 * Two half-mixed piles become a pure full stack plus the rest, where the vanilla swap would just
	 * shuffle the mix — §1 tenet, order through use. Needs menu-level cursor access (the cursor's host
	 * item can change, and an ItemStack's item is final), hence the {@code clicked}-level hook rather
	 * than the {@code overrideStackedOnOther} path. Returns false — nothing touched — when the repack
	 * would change nothing or the slot refuses the stack, so the click falls back to vanilla.
	 */
	public static boolean unmix(AbstractContainerMenu menu, Slot slot, ItemStack carried) {
		ItemStack slotStack = slot.getItem();
		if (!VariantPiles.isPile(slotStack) || !VariantPiles.isPile(carried)) {
			return false;
		}
		VariantGroup group = VariantGroups.of(slotStack.getItem());
		if (group == null || VariantGroups.of(carried.getItem()) != group) {
			return false;
		}
		List<ItemStack> repacked = VariantPiles.makeStacks(group,
				VariantPiles.pool(group, List.of(slotStack, carried)));
		if (repacked.isEmpty() || repacked.size() > 2) {
			return false; // two piles always repack into at most two stacks; never risk dropping wood
		}
		ItemStack intoSlot = repacked.get(0);
		ItemStack ontoCursor = repacked.size() == 2 ? repacked.get(1) : ItemStack.EMPTY;
		if (!slot.mayPlace(intoSlot)) {
			return false;
		}
		if (ItemStack.matches(intoSlot, slotStack) && ItemStack.matches(ontoCursor, carried)) {
			return false; // already optimally packed — fall through to the vanilla swap
		}
		if (VariantPiles.isPile(intoSlot)) {
			VariantPiles.markManual(intoSlot, true); // hand-assembled = curated, same rule as a merge
		}
		keepSelection(slotStack, intoSlot);
		keepSelection(carried, ontoCursor);
		slot.set(intoSlot);
		menu.setCarried(ontoCursor);
		return true;
	}

	/** Carry a stack's active selection over to its repacked successor if that wood is still in it. */
	private static void keepSelection(ItemStack before, ItemStack after) {
		if (!VariantPiles.isPile(before) || !VariantPiles.isPile(after)) {
			return;
		}
		VariantPile pile = before.get(ModComponents.VARIANT_PILE);
		if (pile != null && pile.selected().isPresent()
				&& VariantPiles.countOf(after, pile.selected().get()) > 0) {
			VariantPiles.seed(after, pile.selected().get());
		}
	}
}
