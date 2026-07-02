package com.mythstack.interaction;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * Pile-aware recipe book (backlog: selective extraction). Piles are hosted on their canonical item,
 * so the vanilla recipe book counted a pile as its HOST — a {oak,spruce} pile read as all-oak: spruce
 * recipes looked uncraftable, oak overcounted — and auto-fill pulled the host wood (or the whole
 * pile) into the grid. These helpers decompose piles for availability counting and extract the
 * <b>intended</b> wood on grid fill. Manual (curated) piles are deliberately fair game here: a
 * recipe-book fill is an explicit crafting demand, not background auto-sorting — and pulling a wood
 * out of a mix reduces entropy (§1 tenet).
 */
public final class PileRecipeBook {
	private PileRecipeBook() {
	}

	/** Account a pile as its CONTENTS — each wood separately; false (untouched) if not a pile. */
	public static boolean accountPile(StackedItemContents contents, ItemStack stack) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return false;
		}
		for (VariantPile.Entry entry : pile.contents()) {
			contents.accountStack(new ItemStack(entry.item(), entry.count()), entry.count());
		}
		return true;
	}

	/**
	 * The pile-aware ingredient match: the vanilla plain-stack rule, or a pile that CONTAINS the
	 * wanted wood. The grid sample only constrains plain stacks — what leaves a pile is always a
	 * plain stack of the wanted wood, so it merges with any sample.
	 */
	public static boolean matches(ItemStack stack, Holder<Item> wanted, ItemStack sample) {
		if (stack.isEmpty()) {
			return false;
		}
		if (VariantPiles.isPile(stack)) {
			return VariantPiles.countOf(stack, wanted.value()) > 0;
		}
		return stack.is(wanted) && Inventory.isUsableForCrafting(stack)
				&& (sample.isEmpty() || ItemStack.isSameItemSameComponents(sample, stack));
	}

	/**
	 * Remove up to {@code amount} of {@code wanted} from inventory slot {@code slot}: the wanted wood
	 * out of a pile (which collapses when a single variant remains), vanilla removal otherwise —
	 * {@code takeAll} mirrors the {@code removeItemNoUpdate} whole-stack branch of the caller.
	 */
	public static ItemStack extract(Inventory inventory, int slot, Holder<Item> wanted, int amount, boolean takeAll) {
		ItemStack inSlot = inventory.getItem(slot);
		if (!VariantPiles.isPile(inSlot)) {
			return takeAll ? inventory.removeItemNoUpdate(slot) : inventory.removeItem(slot, amount);
		}
		int take = Math.min(amount, VariantPiles.countOf(inSlot, wanted.value()));
		if (take <= 0) {
			return ItemStack.EMPTY;
		}
		ItemStack removed = VariantPiles.removeWood(inSlot, wanted.value(), take);
		inventory.setItem(slot, inSlot.getCount() <= 0 ? ItemStack.EMPTY : VariantPiles.collapseToReal(inSlot));
		return removed;
	}
}
