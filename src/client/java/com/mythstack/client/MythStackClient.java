package com.mythstack.client;

import com.mythstack.MythStack;
import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.special.SpecialModelRenderers;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class MythStackClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// Register the dynamic pile-icon renderer (mythstack:pile) — a SpecialModelRenderer that draws up
		// to 3 contained woods, fanned. We use a special renderer (not a declarative composite/select)
		// because the latter trips a vanilla render-state layer-pool bug; see PileSpecialRenderer.
		SpecialModelRenderers.ID_MAPPER.put(MythStack.id("pile"), PileSpecialRenderer.Unbaked.MAP_CODEC);

		// Minimal pile tooltip (phase 5 start): show the true composition under the (canonical) item name.
		// Text-only, no assets — the bundle-style icon grid is a later upgrade.
		ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
			VariantPile pile = stack.get(ModComponents.VARIANT_PILE);
			if (pile == null) {
				return;
			}
			lines.add(Component.literal("Mixed pile (" + pile.total() + ")").withStyle(ChatFormatting.AQUA));
			for (VariantPile.Entry entry : pile.contents()) {
				lines.add(Component.literal("  " + entry.count() + "× ")
						.append(new ItemStack(entry.item()).getHoverName())
						.withStyle(ChatFormatting.GRAY));
			}
		});
	}
}
