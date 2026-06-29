package com.mythstack.mixin;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.PileTooltip;
import com.mythstack.variant.VariantPile;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Gives a pile a bundle-style grid tooltip: when the hovered stack carries a {@link VariantPile}, return
 * a {@link PileTooltip} image component (mapped to the client renderer via {@code
 * ClientTooltipComponentCallback}). Non-pile stacks fall through to vanilla.
 */
@Mixin(Item.class)
public class ItemTooltipImageMixin {

	@Inject(method = "getTooltipImage", at = @At("HEAD"), cancellable = true)
	private void mythstack$pileTooltipImage(ItemStack stack, CallbackInfoReturnable<Optional<TooltipComponent>> cir) {
		VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
		if (pile == null) {
			return;
		}
		List<ItemStack> items = new ArrayList<>(pile.contents().size());
		int selectedIndex = -1;
		for (int i = 0; i < pile.contents().size(); i++) {
			VariantPile.Entry entry = pile.contents().get(i);
			items.add(new ItemStack(entry.item(), entry.count()));
			if (pile.selected().isPresent() && pile.selected().get() == entry.item()) {
				selectedIndex = i;
			}
		}
		cir.setReturnValue(Optional.of(new PileTooltip(items, selectedIndex)));
	}
}
