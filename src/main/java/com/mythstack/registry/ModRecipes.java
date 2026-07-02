package com.mythstack.registry;

import com.mojang.serialization.MapCodec;
import com.mythstack.MythStack;
import com.mythstack.craft.SawmillRecipe;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleItemRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/**
 * The sawing recipe type (plan: sawmill). Typed as {@code RecipeType<StonecutterRecipe>} on purpose:
 * {@link SawmillRecipe} extends it, and the stonecutter menu machinery is generic over exactly that.
 */
public final class ModRecipes {
	private ModRecipes() {
	}

	public static final RecipeType<StonecutterRecipe> SAWING = Registry.register(
			BuiltInRegistries.RECIPE_TYPE, MythStack.id("sawing"), new RecipeType<>() {
				@Override
				public String toString() {
					return "mythstack:sawing";
				}
			});

	@SuppressWarnings({"unchecked", "rawtypes"})
	public static final RecipeSerializer<StonecutterRecipe> SAWING_SERIALIZER = Registry.register(
			BuiltInRegistries.RECIPE_SERIALIZER, MythStack.id("sawing"),
			new RecipeSerializer<StonecutterRecipe>(
					(MapCodec) SingleItemRecipe.simpleMapCodec(SawmillRecipe::new),
					(StreamCodec) SingleItemRecipe.simpleStreamCodec(SawmillRecipe::new)));

	/** Called from {@link MythStack#onInitialize()} to force class-load so the statics register. */
	public static void initialize() {
	}
}
