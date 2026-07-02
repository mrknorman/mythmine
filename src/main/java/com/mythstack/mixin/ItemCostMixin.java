package com.mythstack.mixin;

import com.mythstack.variant.VariantGroup;
import com.mythstack.variant.VariantGroups;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.ItemCost;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Trades that ask for a family CANONICAL accept any member of the family: the plain stick is the
 * family's generic form, so fletchers buy spruce sticks too. Costs naming a NON-canonical member stay
 * exact (a trade wanting spruce planks specifically is not satisfied by birch), and the cost's
 * component predicate still applies. Piles pass through untouched: vanilla already accepts a
 * canonical-hosted pile and consumes it canonical-first — exactly right for a generic cost.
 */
@Mixin(ItemCost.class)
public abstract class ItemCostMixin {

	@Inject(method = "test", at = @At("HEAD"), cancellable = true)
	private void mythstack$familyCanonicalCost(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		ItemCost self = (ItemCost) (Object) this;
		Item costItem = self.item().value();
		if (stack.getItem() == costItem || VariantPiles.isPile(stack)) {
			return; // exact item -> vanilla; piles are never payment
		}
		VariantGroup group = VariantGroups.of(costItem);
		if (group != null && group.canonical() == costItem
				&& VariantGroups.of(stack.getItem()) == group
				&& self.components().test(stack)) {
			cir.setReturnValue(true);
		}
	}
}
