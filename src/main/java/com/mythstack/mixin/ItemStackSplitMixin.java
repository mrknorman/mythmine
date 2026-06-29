package com.mythstack.mixin;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Intercepts {@link ItemStack#split(int)} for pile stacks so a split peels real variants
 * canonical-first (plan §6.4) instead of copying the whole component into both halves. Non-pile
 * stacks early-out and hit vanilla untouched.
 */
@Mixin(ItemStack.class)
public class ItemStackSplitMixin {

	@Inject(method = "split", at = @At("HEAD"), cancellable = true)
	private void mythstack$splitPile(int amount, CallbackInfoReturnable<ItemStack> cir) {
		ItemStack self = (ItemStack) (Object) this;
		if (self.has(ModComponents.VARIANT_PILE)) {
			cir.setReturnValue(VariantPiles.splitPile(self, amount));
		}
	}
}
