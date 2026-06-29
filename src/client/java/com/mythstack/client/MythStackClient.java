package com.mythstack.client;

import com.mythstack.MythStack;
import com.mythstack.variant.PileTooltip;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

public class MythStackClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register the dynamic pile-icon renderer (mythstack:pile) — see PileSpecialRenderer.
		SpecialModelRenderers.ID_MAPPER.put(MythStack.id("pile"), PileSpecialRenderer.Unbaked.MAP_CODEC);

		// Bundle-style grid tooltip for piles: map our PileTooltip data to the client grid renderer.
		ClientTooltipComponentCallback.EVENT.register(data ->
				data instanceof PileTooltip pile ? new ClientPileTooltip(pile.items()) : null);
	}
}
