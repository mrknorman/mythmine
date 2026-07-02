package com.mythstack.mixin;

import com.mythstack.craft.SawmillRecipes;
import com.mythstack.variant.VariantPiles;
import com.mythstack.menu.SawmillMenu;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.inventory.StonecutterMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * A {@link SawmillMenu} reads the SAWING set everywhere the stonecutter menu reads its own — and a
 * PILE in the input selects recipes by its ACTIVE wood (scroll the pile in the slot to steer the
 * cut), not by the canonical host. Consumption follows in {@code StonecutterResultSlotMixin}.
 */
@Mixin(StonecutterMenu.class)
public abstract class StonecutterMenuMixin {

	@Redirect(method = "*", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/crafting/RecipeAccess;stonecutterRecipes()Lnet/minecraft/world/item/crafting/SelectableRecipe$SingleInputSet;"))
	private SelectableRecipe.SingleInputSet<StonecutterRecipe> mythstack$sawmillRecipes(RecipeAccess access) {
		if ((Object) this instanceof SawmillMenu) {
			return SawmillRecipes.forAccess(access);
		}
		return access.stonecutterRecipes();
	}

	@Redirect(method = "*", at = @At(value = "INVOKE",
			target = "Lnet/minecraft/world/item/crafting/SelectableRecipe$SingleInputSet;selectByInput(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/crafting/SelectableRecipe$SingleInputSet;"))
	private SelectableRecipe.SingleInputSet<StonecutterRecipe> mythstack$selectByActiveWood(
			SelectableRecipe.SingleInputSet<StonecutterRecipe> set, ItemStack input) {
		Item active = VariantPiles.isPile(input) ? VariantPiles.activeWood(input) : null;
		return set.selectByInput(active != null ? new ItemStack(active) : input);
	}
}
