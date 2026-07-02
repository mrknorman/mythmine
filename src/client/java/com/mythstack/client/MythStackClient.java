package com.mythstack.client;

import com.mythstack.MythStack;
import com.mythstack.craft.SawmillRecipes;
import com.mythstack.net.SawmillRecipesPayload;
import com.mythstack.registry.ModMenus;
import com.mythstack.variant.PileTooltip;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.ClientTooltipComponentCallback;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.client.gui.screens.inventory.StonecutterScreen;
import net.minecraft.client.renderer.special.SpecialModelRenderers;

public class MythStackClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register the dynamic pile-icon renderer (mythstack:pile) — see PileSpecialRenderer.
		SpecialModelRenderers.ID_MAPPER.put(MythStack.id("pile"), PileSpecialRenderer.Unbaked.MAP_CODEC);

		// The sawmill reuses the stonecutter's screen; its recipe set arrives via our payload.
		MenuScreens.register(ModMenus.SAWMILL, StonecutterScreen::new);
		ClientPlayNetworking.registerGlobalReceiver(SawmillRecipesPayload.TYPE,
				(payload, context) -> SawmillRecipes.setClientSet(payload.recipes()));

		// Bundle-style grid tooltip for piles: map our PileTooltip data to the client grid renderer.
		ClientTooltipComponentCallback.EVENT.register(data ->
				data instanceof PileTooltip pile ? new ClientPileTooltip(pile.items(), pile.selectedIndex()) : null);
	}
}
