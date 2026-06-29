package com.mythstack.client;

import com.mythstack.registry.ModComponents;
import com.mythstack.variant.VariantPile;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

public class MythStackClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
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
