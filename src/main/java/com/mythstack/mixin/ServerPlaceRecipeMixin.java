package com.mythstack.mixin;

import com.mythstack.interaction.PileRecipeBook;
import net.minecraft.core.Holder;
import net.minecraft.recipebook.ServerPlaceRecipe;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Recipe-book auto-fill extracts the INTENDED wood from a matched pile — vanilla's removal would
 * peel the host canonical-first (wrong wood in the grid) or, on the whole-stack branch, drop the
 * entire pile into a grid slot. Plain stacks take the vanilla removal untouched.
 */
@Mixin(ServerPlaceRecipe.class)
public abstract class ServerPlaceRecipeMixin {

	@Redirect(method = "moveItemToGrid", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;removeItem(II)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack mythstack$extractPartial(Inventory inventory, int slot, int amount,
			Slot gridSlot, Holder<Item> wanted, int needed) {
		return PileRecipeBook.extract(inventory, slot, wanted, amount, false);
	}

	@Redirect(method = "moveItemToGrid", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/entity/player/Inventory;removeItemNoUpdate(I)Lnet/minecraft/world/item/ItemStack;"))
	private ItemStack mythstack$extractWholeStack(Inventory inventory, int slot,
			Slot gridSlot, Holder<Item> wanted, int needed) {
		return PileRecipeBook.extract(inventory, slot, wanted, needed, true);
	}
}
