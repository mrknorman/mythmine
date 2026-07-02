package com.mythstack.mixin;

import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeMap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read access to the loaded recipe map — the sawmill set is built from all {@code sawing} recipes. */
@Mixin(RecipeManager.class)
public interface RecipeManagerAccessor {

	@Accessor("recipes")
	RecipeMap mythstack$recipes();
}
