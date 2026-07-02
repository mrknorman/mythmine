package com.mythstack.craft;

import com.mythstack.registry.ModRecipes;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * A sawmill cut (the stonecutter's shape, for wood): one input item → one product, chosen from a
 * list. Extends {@link StonecutterRecipe} so it slots straight into the stonecutter menu/screen
 * machinery — but with its OWN type, so sawing recipes never appear in actual stonecutters.
 */
public class SawmillRecipe extends StonecutterRecipe {

	public SawmillRecipe(Recipe.CommonInfo info, Ingredient input, ItemStackTemplate result) {
		super(info, input, result);
	}

	@Override
	public RecipeType<StonecutterRecipe> getType() {
		return ModRecipes.SAWING;
	}

	@Override
	public RecipeSerializer<StonecutterRecipe> getSerializer() {
		return ModRecipes.SAWING_SERIALIZER;
	}
}
