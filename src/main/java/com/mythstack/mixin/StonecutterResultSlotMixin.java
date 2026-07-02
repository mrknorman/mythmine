package com.mythstack.mixin;

import com.mythstack.variant.VariantPiles;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Taking a cut from a stonecutter/sawmill consumes the pile's ACTIVE wood — vanilla's blanket
 * {@code remove(1)} would peel the canonical host instead (the wrong wood, and the wrong recipe
 * list on the next refresh). The collapsed remainder is written back, refreshing the recipe list.
 */
@Mixin(targets = "net.minecraft.world.inventory.StonecutterMenu$2")
public abstract class StonecutterResultSlotMixin {

	@Redirect(method = "onTake", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/inventory/Slot;remove(I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack mythstack$cutActiveWood(Slot inputSlot, int amount) {
		ItemStack stack = inputSlot.getItem();
		if (VariantPiles.isPile(stack)) {
			Item active = VariantPiles.activeWood(stack);
			if (active != null) {
				ItemStack removed = VariantPiles.removeWood(stack, active, amount);
				inputSlot.set(stack.getCount() <= 0 ? ItemStack.EMPTY : VariantPiles.collapseToReal(stack));
				return removed;
			}
		}
		return inputSlot.remove(amount);
	}
}
