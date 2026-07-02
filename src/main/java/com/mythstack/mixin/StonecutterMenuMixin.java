package com.mythstack.mixin;

import com.mythstack.craft.SawmillRecipes;
import com.mythstack.menu.SawmillMenu;
import net.minecraft.world.item.crafting.RecipeAccess;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.inventory.StonecutterMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** A {@link SawmillMenu} reads the SAWING set everywhere the stonecutter menu reads its own. */
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
}
