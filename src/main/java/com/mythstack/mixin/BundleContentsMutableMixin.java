package com.mythstack.mixin;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import com.mythstack.variant.VariantPiles;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.BundleContents;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Piles auto-UNPACK into bundles (QOL): a bundle gives no slot saving over a pile, and a pile inside
 * a bundle obfuscates the contents. Inserting a pile inserts its constituent plain stacks instead,
 * draining the pile by exactly what fit.
 */
@Mixin(BundleContents.Mutable.class)
public abstract class BundleContentsMutableMixin {

	@Inject(method = "tryInsert", at = @At("HEAD"), cancellable = true)
	private void mythstack$unpackPiles(ItemStack stack, CallbackInfoReturnable<Integer> cir) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return;
		}
		BundleContents.Mutable self = (BundleContents.Mutable) (Object) this;
		int inserted = 0;
		for (VariantPile.Entry entry : List.copyOf(pile.contents())) {
			ItemStack plain = new ItemStack(entry.item(), entry.count());
			int fit = self.tryInsert(plain); // plain stacks take the vanilla path
			if (fit > 0) {
				VariantPiles.removeWood(stack, entry.item(), fit);
				inserted += fit;
			}
		}
		cir.setReturnValue(inserted);
	}
}
