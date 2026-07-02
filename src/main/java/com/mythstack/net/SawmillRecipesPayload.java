package com.mythstack.net;

import com.mythstack.MythStack;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.item.crafting.SelectableRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;

/** S2C: the sawmill's selectable recipes, display-only (the client never needs the recipe bodies). */
public record SawmillRecipesPayload(SelectableRecipe.SingleInputSet<StonecutterRecipe> recipes)
		implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SawmillRecipesPayload> TYPE =
			new CustomPacketPayload.Type<>(MythStack.id("sawmill_recipes"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SawmillRecipesPayload> STREAM_CODEC =
			SelectableRecipe.SingleInputSet.<StonecutterRecipe>noRecipeCodec()
					.map(SawmillRecipesPayload::new, SawmillRecipesPayload::recipes);

	@Override
	public CustomPacketPayload.Type<SawmillRecipesPayload> type() {
		return TYPE;
	}
}
