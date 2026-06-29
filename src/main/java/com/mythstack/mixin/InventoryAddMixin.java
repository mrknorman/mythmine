package com.mythstack.mixin;

import com.mythstack.interaction.Pickup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Auto-grouping on pickup (spec §5): before vanilla distributes a picked-up stack, route family
 * members into existing piles / same-family stacks via {@link Pickup#consolidate}. Anything left over
 * falls through to vanilla (empty-slot placement). Tag lookup only — no per-tick scanning.
 */
@Mixin(Inventory.class)
public class InventoryAddMixin {

	@Shadow
	@Final
	private NonNullList<ItemStack> items;

	@Inject(method = "add(Lnet/minecraft/world/item/ItemStack;)Z", at = @At("HEAD"), cancellable = true)
	private void mythstack$autoConsolidate(ItemStack stack, CallbackInfoReturnable<Boolean> cir) {
		if (Pickup.consolidate(this.items, stack)) {
			cir.setReturnValue(true);
		}
	}
}
