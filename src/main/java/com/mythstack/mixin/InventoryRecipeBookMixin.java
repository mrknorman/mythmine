package com.mythstack.mixin;

import com.mythstack.interaction.PileRecipeBook;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Recipe-book auto-fill can source ingredients from inside piles: the slot search matches a pile
 * that CONTAINS the wanted wood (vanilla matched the host item only, so a pile's non-host woods were
 * invisible). Same slot order as vanilla — first match wins, plain stacks and piles alike.
 */
@Mixin(Inventory.class)
public abstract class InventoryRecipeBookMixin {

	@Inject(method = "findSlotMatchingCraftingIngredient", at = @At("HEAD"), cancellable = true)
	private void mythstack$pileAwareSearch(Holder<Item> wanted, ItemStack sample,
			CallbackInfoReturnable<Integer> cir) {
		Inventory self = (Inventory) (Object) this;
		for (int i = 0; i < self.getNonEquipmentItems().size(); i++) {
			if (PileRecipeBook.matches(self.getNonEquipmentItems().get(i), wanted, sample)) {
				cir.setReturnValue(i);
				return;
			}
		}
		cir.setReturnValue(-1);
	}
}
